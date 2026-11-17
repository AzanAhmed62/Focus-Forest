package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.time.ClockProvider
import com.example.core.util.CsvExporter
import com.example.core.util.ShareFormatter
import com.example.data.local.datastore.UserPreferencesDataStore
import com.example.data.local.db.FocusForestDatabase
import com.example.data.repository.ChallengeRepository
import com.example.data.repository.DataManagementRepository
import com.example.data.repository.FocusRepository
import com.example.data.repository.ShopActionResult
import com.example.data.repository.ShopAndForestRepository
import com.example.data.repository.UsageRepository
import com.example.domain.engine.FocusTimerEngine
import com.example.domain.model.AppUsageSummary
import com.example.domain.model.LaunchableAppInfo
import com.example.domain.model.SessionType
import com.example.domain.model.SnapshotCompleteness
import com.example.domain.model.ThemePreference
import com.example.domain.model.UsagePermissionStatus
import com.example.domain.model.UsageQueryResult
import com.example.domain.model.UserIntention
import com.example.platform.usage.UsageStatsGateway
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

private class FakeClockProvider(
    var wallMs: Long = 1_763_300_000_000L,
    var elapsedMs: Long = 100_000L,
    var boot: Int = 1,
    var zone: ZoneId = ZoneId.of("UTC")
) : ClockProvider {
    override fun nowWallClockMs(): Long = wallMs
    override fun elapsedRealtimeMs(): Long = elapsedMs
    override fun bootCount(): Int = boot
    override fun zoneId(): ZoneId = zone
}

private class FakeUsageStatsGateway(
    var permission: UsagePermissionStatus = UsagePermissionStatus.DENIED,
    var dailyItemsByDate: Map<LocalDate, List<AppUsageSummary>> = emptyMap(),
    var zeroUsageDates: Set<LocalDate> = emptySet()
) : UsageStatsGateway {
    var launchableAppsCalls: Int = 0
    var queryCalls: Int = 0

    override fun checkUsagePermission(): UsagePermissionStatus = permission
    override fun getLaunchableApps(): List<LaunchableAppInfo> {
        launchableAppsCalls++
        return listOf(
            LaunchableAppInfo("com.social.app", "Social App"),
            LaunchableAppInfo("com.video.app", "Video App")
        )
    }
    override fun queryDailyForegroundUsage(date: LocalDate, monitoredPackages: Set<String>): List<AppUsageSummary> {
        queryCalls++
        return if (permission == UsagePermissionStatus.GRANTED) {
            dailyItemsByDate[date].orEmpty()
        } else {
            emptyList()
        }
    }

    override fun queryDailyForegroundUsageResult(
        date: LocalDate,
        monitoredPackages: Set<String>,
        preloadedLaunchableApps: List<LaunchableAppInfo>
    ): UsageQueryResult {
        if (permission != UsagePermissionStatus.GRANTED) return UsageQueryResult.PermissionDenied
        queryCalls++
        val items = dailyItemsByDate[date].orEmpty()
        return when {
            items.isNotEmpty() -> UsageQueryResult.SuccessWithData(items, isPartialDay = date == LocalDate.of(2025, 11, 16))
            date in zeroUsageDates -> UsageQueryResult.SuccessZeroUsage(isPartialDay = false)
            else -> UsageQueryResult.Unavailable("No data for $date")
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PersistenceAndExportTest {

    private lateinit var context: Context
    private lateinit var db: FocusForestDatabase
    private lateinit var prefs: UserPreferencesDataStore
    private lateinit var tempPrefFile: File
    private lateinit var clock: FakeClockProvider
    private lateinit var usageGateway: FakeUsageStatsGateway

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, FocusForestDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        tempPrefFile = File(context.filesDir, "test_prefs_${System.nanoTime()}.preferences_pb")
        val ds = PreferenceDataStoreFactory.create(produceFile = { tempPrefFile })
        prefs = UserPreferencesDataStore(ds)
        clock = FakeClockProvider()
        usageGateway = FakeUsageStatsGateway()
    }

    @After
    fun tearDown() {
        db.close()
        tempPrefFile.delete()
    }

    @Test
    fun `transactional session completion prevents duplicate XP and updates forest and shop`() = runTest {
        val focusRepo = FocusRepository(db, prefs, clock)
        val challengeRepo = ChallengeRepository(db, clock)
        val shopRepo = ShopAndForestRepository(db, clock)

        shopRepo.ensureCatalogSeeded()
        focusRepo.completeOnboarding("Azan", 50, UserIntention.WORK)
        challengeRepo.ensureChallengesGenerated(false)

        val timer = FocusTimerEngine.startSession(
            sessionType = SessionType.DEEP_WORK,
            plannedMinutes = 50,
            intentionLabel = "Architecture",
            nowWallClockMs = clock.nowWallClockMs(),
            nowElapsedMs = clock.elapsedRealtimeMs(),
            bootCount = 1,
            sessionId = "unique_session_1"
        )

        val outcome1 = focusRepo.recordCompletedSession(timer)
        assertTrue(outcome1.session.rewardGranted)
        assertFalse(outcome1.duplicateSuppressed)
        val xpAfterFirst = focusRepo.userProfile.first()!!.lifetimeXp
        assertTrue(xpAfterFirst >= 120)

        // Duplicate completion call with same session ID must NOT grant extra XP/seeds
        val outcome2 = focusRepo.recordCompletedSession(timer)
        assertTrue(outcome2.duplicateSuppressed)
        assertEquals(xpAfterFirst, focusRepo.userProfile.first()!!.lifetimeXp)
        assertEquals(1, shopRepo.forestState.first().totalTreesPlanted)

        // Purchase & equip shop item with earned Leaf Points
        val shopResult = shopRepo.purchaseOrEquip("deco_stone_lantern")
        assertTrue(shopResult is ShopActionResult.Success)
        assertTrue("deco_stone_lantern" in shopRepo.forestState.first().equippedDecorationIds)
    }

    @Test
    fun `usage repository respects denied permission, caches 30s, coalesces calls, and deletes records cleanly`() = runTest {
        val usageRepo = UsageRepository(db, prefs, usageGateway, clock)

        // Denied: must NOT scan launchable apps or query usage stats
        val deniedState = usageRepo.refreshUsageData(forceRefresh = true)
        assertEquals(UsagePermissionStatus.DENIED, deniedState.permissionStatus)
        assertTrue(deniedState.isDataUnavailable)
        assertEquals(0, usageGateway.launchableAppsCalls)
        assertEquals(0, usageGateway.queryCalls)

        // Granted but no data from Android: scans launchable apps once
        usageRepo.acceptDisclosure()
        usageGateway.permission = UsagePermissionStatus.GRANTED
        val emptyGranted = usageRepo.refreshUsageData(forceRefresh = true)
        assertEquals(UsagePermissionStatus.GRANTED, emptyGranted.permissionStatus)
        assertTrue(emptyGranted.isDataUnavailable)
        assertEquals(1, usageGateway.launchableAppsCalls)

        // Non-forced call within 30s TTL returns cached state without rescanning
        usageRepo.refreshUsageData(forceRefresh = false)
        assertEquals(1, usageGateway.launchableAppsCalls)

        // Advance past 30s TTL with valid usage data + zero-usage day
        clock.wallMs += 31_000L
        val today = clock.todayLocalDate()
        val yesterday = today.minusDays(1)
        usageGateway.dailyItemsByDate = mapOf(
            today to listOf(AppUsageSummary("com.social.app", "Social App", 35, 35 * 60_000L, false))
        )
        usageGateway.zeroUsageDates = setOf(yesterday)
        val populated = usageRepo.refreshUsageData()
        assertFalse(populated.isDataUnavailable)
        assertEquals(35, populated.todaySummary?.totalObservedMinutes)
        assertEquals(2, usageGateway.launchableAppsCalls)
        assertEquals(
            SnapshotCompleteness.ZERO_USAGE,
            populated.sevenDayHistory.first { it.localDate == yesterday.toString() }.completeness
        )

        // Concurrent forced refreshes coalesce safely
        val results = listOf(
            async { usageRepo.refreshUsageData(forceRefresh = true) },
            async { usageRepo.refreshUsageData(forceRefresh = true) }
        ).awaitAll()
        assertEquals(results[0].todaySummary?.totalObservedMinutes, results[1].todaySummary?.totalObservedMinutes)

        // Disable & delete
        usageRepo.disableAndDeleteUsageData()
        assertTrue(db.usageDao().getSnapshotsOnce().isEmpty())
        assertFalse(prefs.usageTrackingEnabled.first())
    }

    @Test
    fun `csv export, privacy-safe share formatter, datastore prefs, and full wipe work`() = runTest {
        val focusRepo = FocusRepository(db, prefs, clock)
        val usageRepo = UsageRepository(db, prefs, usageGateway, clock)
        val dataRepo = DataManagementRepository(db, prefs, clock, context, usageRepo)

        focusRepo.completeOnboarding("Sam", 50, UserIntention.STUDYING)
        prefs.setThemePreference(ThemePreference.DARK)
        assertEquals(ThemePreference.DARK, prefs.themePreference.first())

        val timer = FocusTimerEngine.startSession(
            sessionType = SessionType.POMODORO,
            plannedMinutes = 25,
            intentionLabel = "Thesis",
            nowWallClockMs = clock.nowWallClockMs(),
            nowElapsedMs = clock.elapsedRealtimeMs(),
            bootCount = 1,
            sessionId = "s_export"
        )
        val outcome = focusRepo.recordCompletedSession(timer)

        val csvSessions = CsvExporter.buildFocusSessionsCsv(focusRepo.allSessions.first(), clock.zoneId())
        assertTrue(csvSessions.contains("POMODORO"))
        assertTrue(csvSessions.contains("Thesis"))

        val csvStats = CsvExporter.buildDailyStatsCsv(focusRepo.allDailyStats.first())
        assertTrue(csvStats.contains("25"))

        val shareText = ShareFormatter.formatSessionShareText(outcome.session, 1, 1)
        assertTrue(shareText.contains("Pomodoro"))
        assertFalse(shareText.contains("com.social.app"))

        dataRepo.deleteAllLocalData()
        assertNull(focusRepo.userProfile.first())
        assertFalse(prefs.onboardingCompleted.first())
        assertTrue(db.rewardLedgerDao().countBySourceSince("SESSION", 0L) == 0)
    }
}

