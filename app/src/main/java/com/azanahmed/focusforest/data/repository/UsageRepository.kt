package com.azanahmed.focusforest.data.repository

import com.azanahmed.focusforest.core.time.ClockProvider
import com.azanahmed.focusforest.data.local.datastore.UserPreferencesDataStore
import com.azanahmed.focusforest.data.local.db.FocusForestDatabase
import com.azanahmed.focusforest.data.local.db.entities.AppUsageItemEntity
import com.azanahmed.focusforest.data.local.db.entities.AppUsageSnapshotEntity
import com.azanahmed.focusforest.domain.engine.UsageAnalyticsEngine
import com.azanahmed.focusforest.domain.model.DailyUsageSummary
import com.azanahmed.focusforest.domain.model.LaunchableAppInfo
import com.azanahmed.focusforest.domain.model.SnapshotCompleteness
import com.azanahmed.focusforest.domain.model.UsageDashboardState
import com.azanahmed.focusforest.domain.model.UsagePermissionStatus
import com.azanahmed.focusforest.domain.model.UsageQueryResult
import com.azanahmed.focusforest.platform.usage.UsageStatsGateway
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate

class UsageRepository(
    private val db: FocusForestDatabase,
    private val prefs: UserPreferencesDataStore,
    private val gateway: UsageStatsGateway,
    private val clockProvider: ClockProvider
) {
    private val usageDao = db.usageDao()
    private val refreshMutex = Mutex()
    private var inFlightRefresh: CompletableDeferred<UsageDashboardState>? = null
    private var lastCacheWallMs: Long = -1L

    private val _dashboardState = MutableStateFlow(UsageDashboardState())
    val dashboardState: StateFlow<UsageDashboardState> = _dashboardState.asStateFlow()

    companion object {
        private const val CACHE_TTL_MS = 30_000L
    }

    suspend fun refreshUsageData(forceRefresh: Boolean = false): UsageDashboardState {
        val nowCheckMs = clockProvider.nowWallClockMs()
        val cached = _dashboardState.value
        if (!forceRefresh &&
            lastCacheWallMs > 0L &&
            (nowCheckMs - lastCacheWallMs) in 0L..CACHE_TTL_MS &&
            cached.lastRefreshedMs != null
        ) {
            return cached
        }

        var existingDeferred: CompletableDeferred<UsageDashboardState>? = null
        var ownerDeferred: CompletableDeferred<UsageDashboardState>? = null

        refreshMutex.withLock {
            val active = inFlightRefresh
            if (active != null && active.isActive) {
                existingDeferred = active
            } else {
                val created = CompletableDeferred<UsageDashboardState>()
                inFlightRefresh = created
                ownerDeferred = created
            }
        }

        existingDeferred?.let { return it.await() }
        val deferred = ownerDeferred!!

        return try {
            _dashboardState.value = _dashboardState.value.copy(isRefreshing = true)
            val result = performRefreshInternal()
            lastCacheWallMs = clockProvider.nowWallClockMs()
            _dashboardState.value = result
            deferred.complete(result)
            result
        } catch (c: CancellationException) {
            // Cancellation is control flow, not a usage failure. Propagate it to the caller and
            // cancel joined waiters so they do not receive a phantom fallback dashboard.
            _dashboardState.value = _dashboardState.value.copy(isRefreshing = false)
            deferred.cancel(c)
            throw c
        } catch (t: Throwable) {
            val fallback = _dashboardState.value.copy(
                isRefreshing = false,
                queryErrorMessage = t.message ?: "Unable to refresh usage history."
            )
            _dashboardState.value = fallback
            deferred.complete(fallback)
            fallback
        } finally {
            refreshMutex.withLock {
                if (inFlightRefresh === deferred) {
                    inFlightRefresh = null
                }
            }
        }
    }

    private suspend fun performRefreshInternal(): UsageDashboardState = withContext(Dispatchers.IO) {
        val permissionStatus = gateway.checkUsagePermission()
        val trackingEnabled = prefs.usageTrackingEnabled.first()
        val monitoredPackages = prefs.monitoredPackages.first()
        val dailyBudgetMinutes = prefs.dailyUsageBudgetMinutes.first()
        val today = clockProvider.todayLocalDate()
        val nowMs = clockProvider.nowWallClockMs()

        if (permissionStatus != UsagePermissionStatus.GRANTED) {
            return@withContext UsageDashboardState(
                permissionStatus = UsagePermissionStatus.DENIED,
                isTrackingEnabled = trackingEnabled,
                isDataUnavailable = true,
                todayCompleteness = SnapshotCompleteness.UNAVAILABLE,
                monitoredPackages = monitoredPackages,
                availableLaunchableApps = emptyList(),
                dailyUsageBudgetMinutes = dailyBudgetMinutes,
                isRefreshing = false,
                lastRefreshedMs = nowMs
            )
        }

        val effectiveTrackingEnabled = if (prefs.usageDisclosureAccepted.first()) {
            if (!trackingEnabled) {
                prefs.setUsageTrackingEnabled(true)
            }
            true
        } else {
            trackingEnabled
        }

        if (!effectiveTrackingEnabled) {
            return@withContext UsageDashboardState(
                permissionStatus = UsagePermissionStatus.GRANTED,
                isTrackingEnabled = false,
                isDataUnavailable = true,
                todayCompleteness = SnapshotCompleteness.UNAVAILABLE,
                monitoredPackages = monitoredPackages,
                availableLaunchableApps = emptyList(),
                dailyUsageBudgetMinutes = dailyBudgetMinutes,
                isRefreshing = false,
                lastRefreshedMs = nowMs
            )
        }

        val launchableApps: List<LaunchableAppInfo> = runCatching {
            gateway.getLaunchableApps()
        }.getOrDefault(emptyList())
        val iconByPkg = launchableApps.associate { it.packageName to it.icon }

        val summariesByDate = linkedMapOf<LocalDate, DailyUsageSummary>()
        var latestErrorMessage: String? = null

        // Query today and up to 14 prior days so 7v7 completed-week baselines work right away when Android retains data
        for (offset in 0L..14L) {
            val date = today.minusDays(offset)
            val queryResult = gateway.queryDailyForegroundUsageResult(date, monitoredPackages, launchableApps)
            if (queryResult is UsageQueryResult.Failure && latestErrorMessage == null) {
                latestErrorMessage = queryResult.message
            }
            val dailySummary = UsageAnalyticsEngine.buildDailySummary(date, queryResult, monitoredPackages)
            summariesByDate[date] = dailySummary

            if (dailySummary.hasUsableData) {
                val snapshotEntity = AppUsageSnapshotEntity(
                    localDate = date.toString(),
                    totalObservedMinutes = dailySummary.totalObservedMinutes,
                    monitoredAppsMinutes = dailySummary.monitoredAppsMinutes,
                    hasUsableData = true,
                    capturedAtWallMs = nowMs,
                    completeness = dailySummary.completeness.name,
                    totalObservedMillis = dailySummary.totalObservedMillis,
                    monitoredAppsMillis = dailySummary.monitoredAppsMillis
                )
                val itemEntities = dailySummary.topApps.map { app ->
                    AppUsageItemEntity(
                        snapshotDate = date.toString(),
                        packageName = app.packageName,
                        appName = app.appName,
                        foregroundMillis = app.foregroundMillis,
                        foregroundMinutes = app.foregroundMinutes,
                        isMonitored = app.isMonitored
                    )
                }
                usageDao.replaceSnapshotAndItems(snapshotEntity, itemEntities)
            }
        }

        // Merge with stored Room snapshots (for historical baseline beyond Android's query retention)
        val storedSnapshots = usageDao.getSnapshotsOnce()
        for (snap in storedSnapshots) {
            val parsedDate = runCatching { LocalDate.parse(snap.localDate) }.getOrNull() ?: continue
            val existing = summariesByDate[parsedDate]
            if ((existing == null || !existing.hasUsableData) && snap.hasUsableData) {
                val storedCompleteness = runCatching {
                    SnapshotCompleteness.valueOf(snap.completeness)
                }.getOrDefault(
                    if (parsedDate == today) SnapshotCompleteness.PARTIAL_TODAY else SnapshotCompleteness.COMPLETE
                )
                summariesByDate[parsedDate] = DailyUsageSummary(
                    localDate = snap.localDate,
                    totalObservedMinutes = snap.totalObservedMinutes,
                    monitoredAppsMinutes = snap.monitoredAppsMinutes,
                    hasUsableData = true,
                    topApps = emptyList(),
                    totalObservedMillis = if (snap.totalObservedMillis > 0L) snap.totalObservedMillis else snap.totalObservedMinutes * 60_000L,
                    monitoredAppsMillis = if (snap.monitoredAppsMillis > 0L) snap.monitoredAppsMillis else snap.monitoredAppsMinutes * 60_000L,
                    completeness = storedCompleteness
                )
            }
        }

        val todaySummary = summariesByDate[today]?.let { summary ->
            summary.copy(
                topApps = summary.topApps.map { app ->
                    app.copy(icon = app.icon ?: iconByPkg[app.packageName])
                }
            )
        }
        val sevenDayHistory = (6L downTo 0L).map { offset ->
            val d = today.minusDays(offset)
            summariesByDate[d] ?: DailyUsageSummary(
                localDate = d.toString(),
                totalObservedMinutes = 0,
                monitoredAppsMinutes = 0,
                hasUsableData = false,
                completeness = SnapshotCompleteness.UNAVAILABLE
            )
        }

        val baselineStatus = UsageAnalyticsEngine.computeBaselineStatus(
            todayDate = today,
            summariesByDate = summariesByDate,
            useMonitoredSubset = monitoredPackages.isNotEmpty(),
            dailyBudgetMinutes = dailyBudgetMinutes
        )

        val todayCompleteness = todaySummary?.completeness ?: SnapshotCompleteness.UNAVAILABLE
        val isUnavailable = todaySummary == null || !todaySummary.hasUsableData

        UsageDashboardState(
            permissionStatus = UsagePermissionStatus.GRANTED,
            isTrackingEnabled = true,
            isDataUnavailable = isUnavailable,
            todayCompleteness = todayCompleteness,
            todaySummary = todaySummary,
            sevenDayHistory = sevenDayHistory,
            baselineStatus = baselineStatus,
            monitoredPackages = monitoredPackages,
            availableLaunchableApps = launchableApps,
            dailyUsageBudgetMinutes = dailyBudgetMinutes,
            queryErrorMessage = latestErrorMessage,
            isRefreshing = false,
            lastRefreshedMs = nowMs
        )
    }

    suspend fun acceptDisclosure() {
        prefs.setUsageDisclosureAccepted(true)
        prefs.setUsageTrackingEnabled(true)
        resetInMemoryCache()
    }

    suspend fun toggleMonitoredPackage(packageName: String) {
        val current = prefs.monitoredPackages.first().toMutableSet()
        if (packageName in current) {
            current.remove(packageName)
        } else {
            current.add(packageName)
        }
        prefs.setMonitoredPackages(current)
        refreshUsageData(forceRefresh = true)
    }

    suspend fun setDailyUsageBudget(minutes: Int) {
        prefs.setDailyUsageBudgetMinutes(minutes)
        refreshUsageData(forceRefresh = true)
    }

    suspend fun disableAndDeleteUsageData() {
        prefs.setUsageTrackingEnabled(false)
        prefs.setUsageDisclosureAccepted(false)
        prefs.setMonitoredPackages(emptySet())
        usageDao.clearAllUsageData()
        resetInMemoryCache()
        refreshUsageData(forceRefresh = true)
    }

    fun resetInMemoryCache() {
        lastCacheWallMs = -1L
        _dashboardState.value = UsageDashboardState()
    }
}
