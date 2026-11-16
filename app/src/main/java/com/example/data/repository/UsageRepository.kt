package com.example.data.repository

import com.example.core.time.ClockProvider
import com.example.data.local.datastore.UserPreferencesDataStore
import com.example.data.local.db.FocusForestDatabase
import com.example.data.local.db.entities.AppUsageItemEntity
import com.example.data.local.db.entities.AppUsageSnapshotEntity
import com.example.domain.engine.UsageAnalyticsEngine
import com.example.domain.model.AppUsageSummary
import com.example.domain.model.DailyUsageSummary
import com.example.domain.model.LaunchableAppInfo
import com.example.domain.model.UsageDashboardState
import com.example.domain.model.UsagePermissionStatus
import com.example.platform.usage.UsageStatsGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate

class UsageRepository(
    private val db: FocusForestDatabase,
    private val prefs: UserPreferencesDataStore,
    private val gateway: UsageStatsGateway,
    private val clockProvider: ClockProvider
) {
    private val usageDao = db.usageDao()

    private val _dashboardState = MutableStateFlow(UsageDashboardState())
    val dashboardState: StateFlow<UsageDashboardState> = _dashboardState.asStateFlow()

    suspend fun refreshUsageData(): UsageDashboardState = withContext(Dispatchers.IO) {
        val permissionStatus = gateway.checkUsagePermission()
        val trackingEnabled = prefs.usageTrackingEnabled.first()
        val monitoredPackages = prefs.monitoredPackages.first()
        val today = clockProvider.todayLocalDate()
        val nowMs = clockProvider.nowWallClockMs()

        if (permissionStatus != UsagePermissionStatus.GRANTED) {
            val state = UsageDashboardState(
                permissionStatus = UsagePermissionStatus.DENIED,
                isTrackingEnabled = trackingEnabled,
                isDataUnavailable = true,
                monitoredPackages = monitoredPackages,
                availableLaunchableApps = emptyList(),
                lastRefreshedMs = nowMs
            )
            _dashboardState.value = state
            return@withContext state
        }

        // If user granted permission in system settings, ensure tracking flag reflects consent if accepted
        val effectiveTrackingEnabled = if (prefs.usageDisclosureAccepted.first()) {
            if (!trackingEnabled) {
                prefs.setUsageTrackingEnabled(true)
            }
            true
        } else {
            trackingEnabled
        }

        if (!effectiveTrackingEnabled) {
            val state = UsageDashboardState(
                permissionStatus = UsagePermissionStatus.GRANTED,
                isTrackingEnabled = false,
                isDataUnavailable = true,
                monitoredPackages = monitoredPackages,
                availableLaunchableApps = emptyList(),
                lastRefreshedMs = nowMs
            )
            _dashboardState.value = state
            return@withContext state
        }

        val launchableApps: List<LaunchableAppInfo> = runCatching {
            gateway.getLaunchableApps()
        }.getOrDefault(emptyList())
        val iconByPkg = launchableApps.associate { it.packageName to it.icon }

        // Query today and up to 7 prior days from Android UsageStatsManager
        val summariesByDate = linkedMapOf<LocalDate, DailyUsageSummary>()
        for (offset in 0L..7L) {
            val date = today.minusDays(offset)
            val rawItems = gateway.queryDailyForegroundUsage(date, monitoredPackages, launchableApps)
            val dailySummary = UsageAnalyticsEngine.buildDailySummary(date, rawItems, monitoredPackages)
            summariesByDate[date] = dailySummary

            // Persist local snapshot to Room
            if (dailySummary.hasUsableData) {
                val snapshotEntity = AppUsageSnapshotEntity(
                    localDate = date.toString(),
                    totalObservedMinutes = dailySummary.totalObservedMinutes,
                    monitoredAppsMinutes = dailySummary.monitoredAppsMinutes,
                    hasUsableData = true,
                    capturedAtWallMs = nowMs
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

        // Merge with any previously stored Room snapshots (for historical baseline beyond query retention)
        val storedSnapshots = usageDao.getSnapshotsOnce()
        for (snap in storedSnapshots) {
            val parsedDate = runCatching { LocalDate.parse(snap.localDate) }.getOrNull() ?: continue
            val existing = summariesByDate[parsedDate]
            if ((existing == null || !existing.hasUsableData) && snap.hasUsableData) {
                summariesByDate[parsedDate] = DailyUsageSummary(
                    localDate = snap.localDate,
                    totalObservedMinutes = snap.totalObservedMinutes,
                    monitoredAppsMinutes = snap.monitoredAppsMinutes,
                    hasUsableData = true,
                    topApps = emptyList()
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
                hasUsableData = false
            )
        }

        val baselineStatus = UsageAnalyticsEngine.computeBaselineStatus(
            todayDate = today,
            summariesByDate = summariesByDate,
            useMonitoredSubset = monitoredPackages.isNotEmpty()
        )

        val state = UsageDashboardState(
            permissionStatus = UsagePermissionStatus.GRANTED,
            isTrackingEnabled = true,
            isDataUnavailable = todaySummary == null || !todaySummary.hasUsableData,
            todaySummary = todaySummary,
            sevenDayHistory = sevenDayHistory,
            baselineStatus = baselineStatus,
            monitoredPackages = monitoredPackages,
            availableLaunchableApps = launchableApps,
            lastRefreshedMs = nowMs
        )
        _dashboardState.value = state
        state
    }

    suspend fun acceptDisclosure() {
        prefs.setUsageDisclosureAccepted(true)
        prefs.setUsageTrackingEnabled(true)
    }

    suspend fun toggleMonitoredPackage(packageName: String) {
        val current = prefs.monitoredPackages.first().toMutableSet()
        if (packageName in current) {
            current.remove(packageName)
        } else {
            current.add(packageName)
        }
        prefs.setMonitoredPackages(current)
        refreshUsageData()
    }

    suspend fun disableAndDeleteUsageData() {
        prefs.setUsageTrackingEnabled(false)
        prefs.setUsageDisclosureAccepted(false)
        prefs.setMonitoredPackages(emptySet())
        usageDao.clearAllUsageData()
        refreshUsageData()
    }
}
