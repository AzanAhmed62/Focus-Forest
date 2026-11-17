package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.core.time.ClockProvider
import com.example.data.local.datastore.UserPreferencesDataStore
import com.example.data.local.db.FocusForestDatabase
import com.example.data.repository.ChallengeMutationResult
import com.example.data.repository.ChallengeRepository
import com.example.data.repository.FocusRepository
import com.example.data.repository.RewardAuthority
import com.example.domain.engine.FocusTimerEngine
import com.example.domain.model.ChallengeMetricType
import com.example.domain.model.ChallengeStatus
import com.example.domain.model.SessionType
import com.example.domain.model.SnapshotCompleteness
import com.example.domain.model.UsageBaselineStatus
import com.example.domain.model.UserIntention
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

private class LedgerTestClock(
    var wallMs: Long = 1_763_300_000_000L,
    var elapsedMs: Long = 100_000L,
    var zone: ZoneId = ZoneId.of("UTC")
) : ClockProvider {
    override fun nowWallClockMs(): Long = wallMs
    override fun elapsedRealtimeMs(): Long = elapsedMs
    override fun bootCount(): Int = 1
    override fun zoneId(): ZoneId = zone
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RoomMigrationAndLedgerTest {

    private lateinit var context: Context
    private lateinit var db: FocusForestDatabase
    private lateinit var prefs: UserPreferencesDataStore
    private lateinit var tempPrefFile: File
    private lateinit var clock: LedgerTestClock

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, FocusForestDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        tempPrefFile = File(context.filesDir, "ledger_prefs_${System.nanoTime()}.preferences_pb")
        prefs = UserPreferencesDataStore(PreferenceDataStoreFactory.create(produceFile = { tempPrefFile }))
        clock = LedgerTestClock()
    }

    @After
    fun tearDown() {
        db.close()
        tempPrefFile.delete()
    }

    @Test
    fun `room migration 1 to 2 preserves v1 rows, adds columns, and creates reward_ledger`() = runTest {
        val dbFile = File(context.cacheDir, "migration_test_${System.nanoTime()}.db")
        if (dbFile.exists()) dbFile.delete()

        val callback = object : SupportSQLiteOpenHelper.Callback(1) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS user_profile (
                        id INTEGER NOT NULL PRIMARY KEY,
                        displayName TEXT NOT NULL,
                        primaryIntention TEXT NOT NULL,
                        dailyFocusGoalMinutes INTEGER NOT NULL,
                        lifetimeXp INTEGER NOT NULL,
                        currentLevel INTEGER NOT NULL,
                        currentStreakDays INTEGER NOT NULL,
                        longestStreakDays INTEGER NOT NULL,
                        equippedAvatarId TEXT NOT NULL,
                        equippedThemeId TEXT NOT NULL,
                        createdAtMs INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS focus_sessions (
                        id TEXT NOT NULL PRIMARY KEY,
                        sessionType TEXT NOT NULL,
                        plannedMinutes INTEGER NOT NULL,
                        completedMinutes INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        startedAtWallMs INTEGER NOT NULL,
                        endedAtWallMs INTEGER,
                        localDate TEXT NOT NULL,
                        xpAwarded INTEGER NOT NULL,
                        seedsAwarded INTEGER NOT NULL,
                        intentionLabel TEXT NOT NULL,
                        rewardGranted INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_focus_sessions_localDate ON focus_sessions (localDate)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_focus_sessions_status ON focus_sessions (status)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS daily_stats (
                        localDate TEXT NOT NULL PRIMARY KEY,
                        focusMinutes INTEGER NOT NULL,
                        completedSessions INTEGER NOT NULL,
                        goalMinutes INTEGER NOT NULL,
                        goalMet INTEGER NOT NULL,
                        xpEarned INTEGER NOT NULL,
                        seedsEarned INTEGER NOT NULL,
                        isRestDay INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS app_usage_snapshots (
                        localDate TEXT NOT NULL PRIMARY KEY,
                        totalObservedMinutes INTEGER NOT NULL,
                        monitoredAppsMinutes INTEGER NOT NULL,
                        hasUsableData INTEGER NOT NULL,
                        capturedAtWallMs INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS app_usage_items (
                        snapshotDate TEXT NOT NULL,
                        packageName TEXT NOT NULL,
                        appName TEXT NOT NULL,
                        foregroundMillis INTEGER NOT NULL,
                        foregroundMinutes INTEGER NOT NULL,
                        isMonitored INTEGER NOT NULL,
                        PRIMARY KEY(snapshotDate, packageName)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_app_usage_items_snapshotDate ON app_usage_items (snapshotDate)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS challenges (
                        id TEXT NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        description TEXT NOT NULL,
                        iconType TEXT NOT NULL,
                        period TEXT NOT NULL,
                        metricType TEXT NOT NULL,
                        targetValue INTEGER NOT NULL,
                        rewardXp INTEGER NOT NULL,
                        rewardSeeds INTEGER NOT NULL,
                        periodKey TEXT NOT NULL,
                        createdAtMs INTEGER NOT NULL,
                        targetPackageName TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_challenges_periodKey ON challenges (periodKey)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_challenges_period ON challenges (period)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS challenge_progress (
                        challengeId TEXT NOT NULL PRIMARY KEY,
                        currentProgress INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        completedAtMs INTEGER,
                        rewardClaimed INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS achievements (
                        type TEXT NOT NULL PRIMARY KEY,
                        unlocked INTEGER NOT NULL,
                        unlockedAtMs INTEGER
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS forest_progress (
                        id INTEGER NOT NULL PRIMARY KEY,
                        totalSeeds INTEGER NOT NULL,
                        spendableLeafPoints INTEGER NOT NULL,
                        forestLevel INTEGER NOT NULL,
                        totalTreesPlanted INTEGER NOT NULL,
                        equippedEnvironmentId TEXT NOT NULL,
                        equippedDecorationsCsv TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS forest_trees (
                        id TEXT NOT NULL PRIMARY KEY,
                        species TEXT NOT NULL,
                        plantedDate TEXT NOT NULL,
                        focusMinutes INTEGER NOT NULL,
                        plantedAtWallMs INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS shop_items (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        description TEXT NOT NULL,
                        category TEXT NOT NULL,
                        costLeafPoints INTEGER NOT NULL,
                        previewColorHex INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS owned_items (
                        itemId TEXT NOT NULL PRIMARY KEY,
                        acquiredAtMs INTEGER NOT NULL,
                        isEquipped INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                // Seed v1 rows before migration
                db.execSQL(
                    "INSERT INTO user_profile VALUES (1, 'Azan', 'WORK', 50, 220, 2, 3, 5, 'avatar_seedling', 'theme_classic_sage', 1000)"
                )
                db.execSQL(
                    "INSERT INTO app_usage_snapshots VALUES ('2026-11-15', 45, 20, 1, 2000)"
                )
                db.execSQL(
                    "INSERT INTO challenges VALUES ('c1', 'Old Challenge', 'Desc', 'SPARK', 'CUSTOM', 'FOCUS_MINUTES', 30, 25, 1, 'custom', 1500, NULL)"
                )
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbFile.absolutePath)
                .callback(callback)
                .build()
        )
        helper.writableDatabase.close()
        helper.close()

        // Open with Room v2 and MIGRATION_1_2 (Room validates exact schema identity!)
        val migratedDb = Room.databaseBuilder(context, FocusForestDatabase::class.java, dbFile.absolutePath)
            .addMigrations(FocusForestDatabase.MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()

        try {
            val profile = migratedDb.userProfileDao().getProfileOnce()
            assertNotNull(profile)
            assertEquals("Azan", profile?.displayName)
            assertEquals(220, profile?.lifetimeXp)

            val snapshots = migratedDb.usageDao().getSnapshotsOnce()
            assertEquals(1, snapshots.size)
            assertEquals(SnapshotCompleteness.COMPLETE.name, snapshots.first().completeness)

            val challenge = migratedDb.challengeDao().getChallengeById("c1")
            assertNotNull(challenge)
            assertFalse(challenge!!.isArchived)

            // Verify reward_ledger table works post-migration
            val inserted = migratedDb.rewardLedgerDao().insertIfAbsent(
                com.example.data.local.db.entities.RewardLedgerEntity(
                    rewardKey = "test:1",
                    sourceType = "SESSION",
                    sourceId = "1",
                    xpDelta = 50,
                    seedsDelta = 2,
                    createdAtMs = 3000L
                )
            )
            assertTrue(inserted != -1L)
        } finally {
            migratedDb.close()
            dbFile.delete()
        }
    }

    @Test
    fun `break sessions earn zero xp, zero seeds, zero trees, and zero focus minutes`() = runTest {
        val focusRepo = FocusRepository(db, prefs, clock)
        focusRepo.completeOnboarding("Ava", 50, UserIntention.CONCENTRATION)

        val shortBreak = FocusTimerEngine.startSession(
            sessionType = SessionType.SHORT_BREAK,
            plannedMinutes = 5,
            intentionLabel = "Stretch",
            nowWallClockMs = clock.nowWallClockMs(),
            nowElapsedMs = clock.elapsedRealtimeMs(),
            bootCount = 1,
            sessionId = "break_short"
        )
        val outcome = focusRepo.recordCompletedSession(shortBreak)
        assertEquals(0, outcome.session.xpAwarded)
        assertEquals(0, outcome.session.seedsAwarded)
        assertFalse(outcome.session.rewardGranted)

        val profile = focusRepo.userProfile.first()!!
        assertEquals(0, profile.lifetimeXp)
        assertEquals(0, db.forestDao().getTreesOnce().size)
        assertTrue(db.dailyStatsDao().getAllDailyStatsOnce().isEmpty())
    }

    @Test
    fun `challenge repository credits usage challenges atomically, supports edit-archive, and enforces custom cap`() = runTest {
        val focusRepo = FocusRepository(db, prefs, clock)
        val challengeRepo = ChallengeRepository(db, clock)

        focusRepo.completeOnboarding("Maya", 50, UserIntention.REDUCE_SOCIAL)
        challengeRepo.ensureChallengesGenerated(baselineReady = true)

        // Complete a 50-min deep work session -> completes challenge & unlocks FIRST_FOCUS achievement
        val deepWork = FocusTimerEngine.startSession(
            sessionType = SessionType.DEEP_WORK,
            plannedMinutes = 50,
            intentionLabel = "Reading",
            nowWallClockMs = clock.nowWallClockMs(),
            nowElapsedMs = clock.elapsedRealtimeMs(),
            bootCount = 1,
            sessionId = "dw_1"
        )
        focusRepo.recordCompletedSession(deepWork)

        val xpBeforeUsageRefresh = focusRepo.userProfile.first()!!.lifetimeXp
        val seedsBeforeUsageRefresh = db.forestDao().getForestProgressOnce()!!.totalSeeds

        // Simulate baseline showing 20% usage improvement -> satisfies daily_..._usage_reduce_10 & weekly_..._usage_improve_15
        val baseline = UsageBaselineStatus(
            validBaselineDays = 7,
            requiredDays = 7,
            sevenDayAverageMinutes = 100,
            todayMinutes = 30,
            improvementPercent = 20,
            comparableCompletedDayMinutes = 80,
            comparableCompletedDayDate = clock.todayLocalDate().minusDays(1).toString(),
            dailyBudgetMinutes = 120,
            weeklyCompletedMinutes = 560,
            weeklyPriorBaselineMinutes = 700,
            weeklyReductionPercent = 20
        )
        val newlyCredited = challengeRepo.refreshProgress(baseline)
        assertTrue(newlyCredited.isNotEmpty())
        val xpAfterUsageRefresh = focusRepo.userProfile.first()!!.lifetimeXp
        val seedsAfterUsageRefresh = db.forestDao().getForestProgressOnce()!!.totalSeeds
        assertTrue(xpAfterUsageRefresh > xpBeforeUsageRefresh)
        assertTrue(seedsAfterUsageRefresh > seedsBeforeUsageRefresh)

        // Second refreshProgress with identical baseline must NOT double credit
        val secondCredited = challengeRepo.refreshProgress(baseline)
        assertTrue(secondCredited.isEmpty())
        assertEquals(xpAfterUsageRefresh, focusRepo.userProfile.first()!!.lifetimeXp)

        // Custom challenge validation, edit, and delete/archive
        val invalid = challengeRepo.createCustomPersonalChallenge("X", "Too short", ChallengeMetricType.FOCUS_MINUTES, 30)
        assertTrue(invalid is ChallengeMutationResult.ValidationError)

        assertTrue(
            challengeRepo.createCustomPersonalChallenge(
                "No Scroll", "Stay under budget", ChallengeMetricType.POMODORO_SESSIONS, 2
            ) is ChallengeMutationResult.Success
        )
        val created = challengeRepo.challenges.first().first { it.title == "No Scroll" }
        assertTrue(
            challengeRepo.editCustomPersonalChallenge(created.id, "Mindful Blocks", "Updated", 3)
                is ChallengeMutationResult.Success
        )
        val updated = challengeRepo.challenges.first().first { it.id == created.id }
        assertEquals("Mindful Blocks", updated.title)
        assertEquals(3, updated.targetValue)

        // Active unrewarded custom challenge is deleted outright
        assertTrue(challengeRepo.deleteOrArchiveCustomChallenge(updated.id) is ChallengeMutationResult.Success)
        assertTrue(challengeRepo.challenges.first().none { it.id == updated.id })

        // Anti-farm cap: at most MAX_REWARDED_CUSTOM_CHALLENGES_PER_DAY (3) custom challenges grant XP today
        repeat(4) { idx ->
            challengeRepo.createCustomPersonalChallenge(
                "Custom $idx",
                "Goal $idx",
                ChallengeMetricType.POMODORO_SESSIONS,
                1
            )
        }
        val pom = FocusTimerEngine.startSession(
            sessionType = SessionType.POMODORO,
            plannedMinutes = 25,
            intentionLabel = "Cap check",
            nowWallClockMs = clock.nowWallClockMs() + 5_000L,
            nowElapsedMs = clock.elapsedRealtimeMs() + 5_000L,
            bootCount = 1,
            sessionId = "pom_cap"
        )
        focusRepo.recordCompletedSession(pom)
        val customRewardedCount = db.rewardLedgerDao().countBySourceSince(
            RewardAuthority.SOURCE_CUSTOM_CHALLENGE,
            clock.todayLocalDate().atStartOfDay(clock.zoneId()).toInstant().toEpochMilli()
        )
        assertEquals(RewardAuthority.MAX_REWARDED_CUSTOM_CHALLENGES_PER_DAY, customRewardedCount)

        // Completed rewarded custom challenge is archived (not hard-deleted) when removed
        val rewardedCustom = challengeRepo.challenges.first()
            .first { it.isCustom && it.status == ChallengeStatus.COMPLETED && it.rewardClaimed }
        assertTrue(challengeRepo.deleteOrArchiveCustomChallenge(rewardedCustom.id) is ChallengeMutationResult.Success)
        assertTrue(challengeRepo.challenges.first().none { it.id == rewardedCustom.id })
        assertTrue(db.challengeDao().getChallengeById(rewardedCustom.id)?.isArchived == true)
    }
}
