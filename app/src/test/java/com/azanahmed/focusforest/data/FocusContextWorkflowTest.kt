package com.azanahmed.focusforest.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.azanahmed.focusforest.core.di.AppContainer
import com.azanahmed.focusforest.core.time.ClockProvider
import com.azanahmed.focusforest.core.util.CsvExporter
import com.azanahmed.focusforest.data.local.datastore.UserPreferencesDataStore
import com.azanahmed.focusforest.data.local.db.FocusForestDatabase
import com.azanahmed.focusforest.data.repository.DataManagementRepository
import com.azanahmed.focusforest.data.repository.FocusContextRepository
import com.azanahmed.focusforest.data.repository.FocusRepository
import com.azanahmed.focusforest.domain.engine.FocusTimerEngine
import com.azanahmed.focusforest.domain.model.AmbientSoundPreset
import com.azanahmed.focusforest.domain.model.AppUsageSummary
import com.azanahmed.focusforest.domain.model.FocusSession
import com.azanahmed.focusforest.domain.model.LaunchableAppInfo
import com.azanahmed.focusforest.domain.model.SessionReflection
import com.azanahmed.focusforest.domain.model.SessionStatus
import com.azanahmed.focusforest.domain.model.SessionType
import com.azanahmed.focusforest.domain.model.ThemePreference
import com.azanahmed.focusforest.domain.model.UsagePermissionStatus
import com.azanahmed.focusforest.domain.model.UserIntention
import com.azanahmed.focusforest.features.focus.FocusViewModel
import com.azanahmed.focusforest.platform.audio.AmbientAudioController
import com.azanahmed.focusforest.platform.audio.AmbientPlaybackState
import com.azanahmed.focusforest.platform.usage.UsageStatsGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private class WorkflowClock(
    var wallMs: Long = 1_763_300_000_000L,
    var elapsedMs: Long = 100_000L,
    var boot: Int = 1
) : ClockProvider {
    override fun nowWallClockMs(): Long = wallMs
    override fun elapsedRealtimeMs(): Long = elapsedMs
    override fun bootCount(): Int = boot
    override fun zoneId(): ZoneId = ZoneId.of("UTC")
}

private class WorkflowUsageGateway : UsageStatsGateway {
    override fun checkUsagePermission(): UsagePermissionStatus = UsagePermissionStatus.DENIED
    override fun getLaunchableApps(): List<LaunchableAppInfo> = emptyList()
    override fun queryDailyForegroundUsage(
        date: LocalDate,
        monitoredPackages: Set<String>
    ): List<AppUsageSummary> = emptyList()
}

private class WorkflowAudioController : AmbientAudioController {
    override val isPlaying: StateFlow<Boolean> = MutableStateFlow(false).asStateFlow()
    override val activePreset: StateFlow<AmbientSoundPreset> =
        MutableStateFlow(AmbientSoundPreset.NONE).asStateFlow()
    override val playbackState: StateFlow<AmbientPlaybackState> =
        MutableStateFlow(AmbientPlaybackState.IDLE).asStateFlow()

    override fun startOrUpdate(preset: AmbientSoundPreset, volume: Float) = Unit
    override fun setVolume(volume: Float) = Unit
    override fun pause() = Unit
    override fun stop() = Unit
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FocusContextWorkflowTest {

    private lateinit var context: Context
    private lateinit var db: FocusForestDatabase
    private lateinit var prefs: UserPreferencesDataStore
    private lateinit var prefsFile: File
    private lateinit var clock: WorkflowClock
    private lateinit var focusRepo: FocusRepository
    private lateinit var contextRepo: FocusContextRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, FocusForestDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        prefsFile = File(context.filesDir, "workflow_prefs_${System.nanoTime()}.preferences_pb")
        prefs = UserPreferencesDataStore(PreferenceDataStoreFactory.create(produceFile = { prefsFile }))
        clock = WorkflowClock()
        focusRepo = FocusRepository(db, prefs, clock)
        contextRepo = FocusContextRepository(db, clock)
    }

    @After
    fun tearDown() {
        db.close()
        prefsFile.delete()
    }

    @Test
    fun `routine start copies snapshots and later edit rename delete cannot rewrite active or completed history`() = runBlocking {
        val dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        try {
            val container = AppContainer(
                context = context,
                clockProvider = clock,
                database = db,
                preferencesDataStore = prefs,
                usageStatsGateway = WorkflowUsageGateway(),
                ambientAudioController = WorkflowAudioController()
            )
            contextRepo.seedDefaultsIfEmpty()
            val original = contextRepo.getRoutineOnce("routine_study")!!
            val viewModel = FocusViewModel(container, context)
            await(dispatcher) { viewModel.uiState.value.isReady }

            viewModel.startRoutine(original.id)
            await(dispatcher) { viewModel.uiState.value.activeTimer != null }

            val state = viewModel.uiState.value
            val chain = state.activeChain
            val timer = state.activeTimer
            assertNotNull(chain)
            assertNotNull(timer)
            assertEquals("routine_study", chain!!.routineId)
            assertEquals("Study", chain.routineNameSnapshot)
            assertEquals(original.snapshotLabel(), chain.routineSnapshot)
            assertEquals("cat_study", chain.categoryId)
            assertEquals("Study", chain.categoryNameSnapshot)
            assertEquals(original.snapshotLabel(), timer!!.routineSnapshot)
            assertEquals("Study", timer.routineNameSnapshot)
            assertEquals("Study", timer.categoryNameSnapshot)
            assertEquals(25 * 60_000L, timer.plannedDurationMs)

            // Editing the template and renaming its category must not touch the running timer.
            val edited = original.copy(
                name = "Study Renamed",
                config = original.config.copy(focusMinutes = 40)
            )
            assertTrue(contextRepo.updateRoutine(edited).isSuccess)
            assertTrue(contextRepo.renameCategory("cat_study", "Study Renamed Category").isSuccess)
            val afterEdit = viewModel.uiState.value.activeTimer!!
            assertEquals("Study", afterEdit.routineNameSnapshot)
            assertEquals(original.snapshotLabel(), afterEdit.routineSnapshot)
            assertEquals("Study", afterEdit.categoryNameSnapshot)

            // Complete the session, then delete the template and archive the category.
            val completed = focusRepo.recordCompletedSession(afterEdit)
            assertTrue(completed.session.rewardGranted)
            assertEquals("Study", completed.session.routineNameSnapshot)
            assertEquals(original.snapshotLabel(), completed.session.routineSnapshot)
            assertEquals("Study", completed.session.categoryNameSnapshot)

            contextRepo.deleteRoutine(original.id)
            contextRepo.archiveCategory("cat_study", archived = true)
            val stored = focusRepo.getSessionOnce(completed.session.id)!!
            assertEquals("routine_study", stored.routineId)
            assertEquals("Study", stored.routineNameSnapshot)
            assertEquals(original.snapshotLabel(), stored.routineSnapshot)
            assertEquals("cat_study", stored.categoryId)
            assertEquals("Study", stored.categoryNameSnapshot)
            assertEquals(completed.session.xpAwarded, stored.xpAwarded)
            assertEquals(completed.session.seedsAwarded, stored.seedsAwarded)
            assertEquals(SessionStatus.COMPLETED, stored.status)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `session metadata edits never change rewards aggregates or active rows`() = runTest {
        focusRepo.completeOnboarding("Maya", 50, UserIntention.STUDYING)
        val timer = FocusTimerEngine.startSession(
            sessionType = SessionType.DEEP_WORK,
            plannedMinutes = 50,
            intentionLabel = "Thesis",
            nowWallClockMs = clock.nowWallClockMs(),
            nowElapsedMs = clock.elapsedRealtimeMs(),
            bootCount = 1,
            sessionId = "metadata_session",
            routineId = "routine_deep_work",
            routineNameSnapshot = "Deep Work",
            routineSnapshot = "1 round · 90m focus",
            categoryId = "cat_study",
            categoryNameSnapshot = "Study"
        )
        val outcome = focusRepo.recordCompletedSession(timer)
        val before = outcome.session
        val profileBefore = focusRepo.userProfile.first()!!
        val forestBefore = db.forestDao().getForestProgressOnce()!!
        val dailyBefore = db.dailyStatsDao().getDailyStatsForDateOnce(clock.todayLocalDate().toString())!!
        val ledgerBefore = db.rewardLedgerDao().getAllOnce()

        val result = focusRepo.updateSessionMetadata(
            sessionId = before.id,
            note = "Read, \"Chapter 4\"\nand took notes.",
            reflection = SessionReflection.FOCUSED
        )
        assertTrue(result.isSuccess)

        val after = focusRepo.getSessionOnce(before.id)!!
        assertEquals("Read, \"Chapter 4\"\nand took notes.", after.note)
        assertEquals(SessionReflection.FOCUSED, after.reflection)
        assertEquals(before.xpAwarded, after.xpAwarded)
        assertEquals(before.seedsAwarded, after.seedsAwarded)
        assertEquals(before.plannedMinutes, after.plannedMinutes)
        assertEquals(before.completedMinutes, after.completedMinutes)
        assertEquals(before.status, after.status)
        assertEquals(before.endedAtWallMs, after.endedAtWallMs)
        assertEquals(before.rewardGranted, after.rewardGranted)
        assertEquals(profileBefore.lifetimeXp, focusRepo.userProfile.first()!!.lifetimeXp)
        assertEquals(forestBefore.totalSeeds, db.forestDao().getForestProgressOnce()!!.totalSeeds)
        assertEquals(forestBefore.spendableLeafPoints, db.forestDao().getForestProgressOnce()!!.spendableLeafPoints)
        assertEquals(dailyBefore, db.dailyStatsDao().getDailyStatsForDateOnce(clock.todayLocalDate().toString()))
        assertEquals(ledgerBefore, db.rewardLedgerDao().getAllOnce())

        // Active sessions cannot be annotated and reward columns remain untouched.
        val activeEntity = db.focusSessionDao().getSessionById(before.id)!!.copy(
            id = "active_session",
            status = SessionStatus.RUNNING.name,
            note = "",
            reflection = ""
        )
        db.focusSessionDao().upsertSession(activeEntity)
        val activeResult = focusRepo.updateSessionMetadata("active_session", "too early", null)
        assertTrue(activeResult.isFailure)
        assertEquals("", db.focusSessionDao().getSessionById("active_session")!!.note)

        val tooLong = "x".repeat(FocusRepository.MAX_NOTE_LENGTH + 1)
        assertTrue(focusRepo.updateSessionMetadata(before.id, tooLong, null).isFailure)
        assertTrue(focusRepo.updateSessionMetadata("missing_session", "note", null).isFailure)
    }

    @Test
    fun `weekly goal is independent from daily goal and initializes exactly once`() = runTest {
        // Legacy fallback: daily × 7 before any explicit weekly write.
        assertEquals(50, prefs.dailyGoalMinutes.first())
        assertEquals(350, prefs.weeklyGoalMinutes.first())

        prefs.setDailyGoalMinutes(40)
        assertEquals(280, prefs.weeklyGoalMinutes.first())

        prefs.setWeeklyGoalMinutes(500)
        prefs.setDailyGoalMinutes(60)
        assertEquals(60, prefs.dailyGoalMinutes.first())
        assertEquals(500, prefs.weeklyGoalMinutes.first())

        prefs.setWeeklyGoalMinutes(0)
        assertEquals(0, prefs.weeklyGoalMinutes.first())
        prefs.setDailyGoalMinutes(70)
        assertEquals(0, prefs.weeklyGoalMinutes.first())

        // The one-time initializer must not overwrite an explicit zero or any later value.
        prefs.ensureWeeklyGoalInitialized()
        assertEquals(0, prefs.weeklyGoalMinutes.first())

        prefs.clearAll()
        prefs.ensureWeeklyGoalInitialized()
        assertEquals(350, prefs.weeklyGoalMinutes.first())
        prefs.setWeeklyGoalMinutes(200)
        prefs.setDailyGoalMinutes(30)
        prefs.ensureWeeklyGoalInitialized()
        assertEquals(200, prefs.weeklyGoalMinutes.first())
    }

    @Test
    fun `full wipe clears routines categories notes weekly goal and caches`() = runTest {
        focusRepo.completeOnboarding("Sam", 45, UserIntention.WORK)
        contextRepo.seedDefaultsIfEmpty()
        assertTrue(contextRepo.createRoutine(
            com.azanahmed.focusforest.domain.model.FocusRoutine(
                id = "",
                name = "Wipe Me",
                config = com.azanahmed.focusforest.domain.model.ChainConfig(focusMinutes = 20)
            )
        ).isSuccess)
        assertTrue(contextRepo.createCategory("Wipe Category").isSuccess)
        prefs.setWeeklyGoalMinutes(500)
        prefs.setThemePreference(ThemePreference.DARK)

        val timer = FocusTimerEngine.startSession(
            sessionType = SessionType.POMODORO,
            plannedMinutes = 25,
            intentionLabel = "Wipe",
            nowWallClockMs = clock.nowWallClockMs(),
            nowElapsedMs = clock.elapsedRealtimeMs(),
            bootCount = 1,
            sessionId = "wipe_session",
            categoryId = "cat_study",
            categoryNameSnapshot = "Study"
        )
        val outcome = focusRepo.recordCompletedSession(timer)
        assertTrue(focusRepo.updateSessionMetadata(outcome.session.id, "private note", SessionReflection.MIXED).isSuccess)
        assertTrue(db.focusRoutineDao().count() > 0)
        assertTrue(db.focusCategoryDao().count() > 0)

        DataManagementRepository(db, prefs, clock, context).deleteAllLocalData()

        assertTrue(db.focusSessionDao().getAllSessionsOnce().isEmpty())
        assertTrue(db.focusRoutineDao().getAllRoutinesOnce().isEmpty())
        assertTrue(db.focusCategoryDao().getAllCategoriesOnce().isEmpty())
        assertTrue(db.rewardLedgerDao().getAllOnce().isEmpty())
        assertNull(focusRepo.userProfile.first())
        assertFalse(prefs.onboardingCompleted.first())
        assertEquals(50, prefs.dailyGoalMinutes.first())
        assertEquals(350, prefs.weeklyGoalMinutes.first())
        assertEquals(ThemePreference.SYSTEM, prefs.themePreference.first())
        assertFalse(prefs.usageTrackingEnabled.first())
    }

    @Test
    fun `csv export keeps stable prefix and escapes unicode multiline context fields`() {
        val zone = ZoneId.of("Asia/Kolkata")
        val started = Instant.parse("2026-11-16T10:15:30Z")
        val session = FocusSession(
            id = "session,1",
            sessionType = SessionType.POMODORO,
            plannedMinutes = 25,
            completedMinutes = 25,
            status = SessionStatus.COMPLETED,
            startedAtWallMs = started.toEpochMilli(),
            endedAtWallMs = started.plusSeconds(1_500).toEpochMilli(),
            localDate = "2026-11-16",
            xpAwarded = 180,
            seedsAwarded = 6,
            intentionLabel = "Read, \"Chapter 4\"\nand notes",
            rewardGranted = true,
            routineId = "routine_study",
            routineNameSnapshot = "Étude, café ☕",
            routineSnapshot = "1 round · 25m focus",
            categoryId = "cat_study",
            categoryNameSnapshot = "Study, \"deep\"",
            note = "Line 1\nLine, 2 \"quoted\"",
            reflection = SessionReflection.MIXED
        )

        val previousLocale = java.util.Locale.getDefault()
        val csv = try {
            java.util.Locale.setDefault(java.util.Locale.FRANCE)
            CsvExporter.buildFocusSessionsCsv(listOf(session), zone)
        } finally {
            java.util.Locale.setDefault(previousLocale)
        }

        val header = csv.lineSequence().first()
        assertEquals(
            "id,date,session_type,planned_minutes,completed_minutes,status,xp_awarded," +
                "seeds_awarded,label,started_at,ended_at,routine_id,routine_name," +
                "routine_snapshot,category_id,category_name,note,reflection",
            header
        )

        val fields = parseCsvRecord(csv.substringAfter('\n').trimEnd('\n', '\r'))
        assertEquals(18, fields.size)
        assertEquals("session,1", fields[0])
        assertEquals("Read, \"Chapter 4\"\nand notes", fields[8])
        assertEquals("2026-11-16T15:45:30+05:30", fields[9])
        assertEquals("2026-11-16T16:10:30+05:30", fields[10])
        assertEquals("routine_study", fields[11])
        assertEquals("Étude, café ☕", fields[12])
        assertEquals("1 round · 25m focus", fields[13])
        assertEquals("cat_study", fields[14])
        assertEquals("Study, \"deep\"", fields[15])
        assertEquals("Line 1\nLine, 2 \"quoted\"", fields[16])
        assertEquals("MIXED", fields[17])
    }

    private fun parseCsvRecord(record: String): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var index = 0
        while (index < record.length) {
            val char = record[index]
            if (inQuotes) {
                if (char == '"') {
                    if (index + 1 < record.length && record[index + 1] == '"') {
                        current.append('"')
                        index++
                    } else {
                        inQuotes = false
                    }
                } else {
                    current.append(char)
                }
            } else {
                when (char) {
                    ',' -> {
                        fields.add(current.toString())
                        current.clear()
                    }
                    '"' -> inQuotes = true
                    else -> current.append(char)
                }
            }
            index++
        }
        fields.add(current.toString())
        return fields
    }

    private fun await(
        dispatcher: TestDispatcher,
        timeoutMs: Long = 10_000L,
        condition: () -> Boolean
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition() && System.currentTimeMillis() < deadline) {
            dispatcher.scheduler.runCurrent()
            Thread.sleep(5L)
        }
        assertTrue("Timed out waiting for asynchronous state", condition())
    }
}
