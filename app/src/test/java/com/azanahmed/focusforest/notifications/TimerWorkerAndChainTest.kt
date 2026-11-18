package com.azanahmed.focusforest.notifications

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.azanahmed.focusforest.core.time.ClockProvider
import com.azanahmed.focusforest.data.local.datastore.UserPreferencesDataStore
import com.azanahmed.focusforest.data.local.db.FocusForestDatabase
import com.azanahmed.focusforest.data.repository.ChallengeRepository
import com.azanahmed.focusforest.data.repository.FocusRepository
import com.azanahmed.focusforest.domain.engine.FocusTimerEngine
import com.azanahmed.focusforest.domain.model.ActiveChainState
import com.azanahmed.focusforest.domain.model.AmbientSoundPreset
import com.azanahmed.focusforest.domain.model.ChainConfig
import com.azanahmed.focusforest.domain.model.NotificationPreferences
import com.azanahmed.focusforest.domain.model.SessionStatus
import com.azanahmed.focusforest.domain.model.SessionType
import com.azanahmed.focusforest.domain.model.UserIntention
import com.azanahmed.focusforest.platform.audio.AmbientAudioController
import com.azanahmed.focusforest.platform.audio.AmbientPlaybackState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.ZoneId

private class WorkerTestClock(
    var wallMs: Long = 1_763_300_000_000L, // Nov 16 2025 ~13:33 UTC
    var elapsedMs: Long = 100_000L,
    var boot: Int = 1
) : ClockProvider {
    override fun nowWallClockMs(): Long = wallMs
    override fun elapsedRealtimeMs(): Long = elapsedMs
    override fun bootCount(): Int = boot
    override fun zoneId(): ZoneId = ZoneId.of("UTC")
}

private class FakeAmbientAudioController : AmbientAudioController {
    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _activePreset = MutableStateFlow(AmbientSoundPreset.NONE)
    override val activePreset: StateFlow<AmbientSoundPreset> = _activePreset.asStateFlow()

    private val _playbackState = MutableStateFlow(AmbientPlaybackState.IDLE)
    override val playbackState: StateFlow<AmbientPlaybackState> = _playbackState.asStateFlow()

    var lastVolume: Float = 0.55f

    override fun startOrUpdate(preset: AmbientSoundPreset, volume: Float) {
        lastVolume = volume.coerceIn(0f, 1f)
        if (preset == AmbientSoundPreset.NONE) {
            stop()
        } else {
            _activePreset.value = preset
            _isPlaying.value = true
            _playbackState.value = AmbientPlaybackState.PLAYING
        }
    }

    override fun setVolume(volume: Float) {
        lastVolume = volume.coerceIn(0f, 1f)
    }

    override fun pause() {
        _isPlaying.value = false
        if (_activePreset.value != AmbientSoundPreset.NONE) {
            _playbackState.value = AmbientPlaybackState.PAUSED
        }
    }

    override fun stop() {
        _isPlaying.value = false
        _activePreset.value = AmbientSoundPreset.NONE
        _playbackState.value = AmbientPlaybackState.IDLE
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TimerWorkerAndChainTest {

    private lateinit var context: Context
    private lateinit var db: FocusForestDatabase
    private lateinit var prefs: UserPreferencesDataStore
    private lateinit var tempPrefFile: File
    private lateinit var clock: WorkerTestClock
    private lateinit var focusRepo: FocusRepository
    private lateinit var challengeRepo: ChallengeRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, FocusForestDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        tempPrefFile = File(context.filesDir, "worker_prefs_${System.nanoTime()}.preferences_pb")
        prefs = UserPreferencesDataStore(PreferenceDataStoreFactory.create(produceFile = { tempPrefFile }))
        clock = WorkerTestClock()
        focusRepo = FocusRepository(db, prefs, clock)
        challengeRepo = ChallengeRepository(db, clock)
    }

    @After
    fun tearDown() {
        db.close()
        tempPrefFile.delete()
    }

    @Test
    fun `session completion coordinator ignores stale, reschedules active, interrupts reboot-expired, and completes once`() = runTest {
        focusRepo.completeOnboarding("Sam", 50, UserIntention.WORK)
        challengeRepo.ensureChallengesGenerated(false)

        var rescheduledDelay: Long? = null
        val coordinator = SessionCompletionCoordinator(
            context = context,
            focusRepository = focusRepo,
            prefs = prefs,
            clockProvider = clock,
            scheduleReschedule = { delayMs, _, _, _ -> rescheduledDelay = delayMs }
        )

        // No active session -> Ignored
        assertEquals(
            WorkerCompletionAction.IgnoredStaleOrMissing,
            coordinator.execute("missing_id", "Pomodoro", 25)
        )

        // Start active session
        val active = FocusTimerEngine.startSession(
            sessionType = SessionType.POMODORO,
            plannedMinutes = 25,
            intentionLabel = "Drafting",
            nowWallClockMs = clock.nowWallClockMs(),
            nowElapsedMs = clock.elapsedRealtimeMs(),
            bootCount = clock.bootCount(),
            sessionId = "sess_worker_1"
        )
        focusRepo.saveActiveTimerState(active)

        // Stale worker ID -> Ignored
        assertEquals(
            WorkerCompletionAction.IgnoredStaleOrMissing,
            coordinator.execute("other_id", "Pomodoro", 25)
        )

        // Still running (10m elapsed, 15m left) -> Rescheduled
        clock.wallMs += 10 * 60_000L
        clock.elapsedMs += 10 * 60_000L
        val resched = coordinator.execute("sess_worker_1", "Pomodoro", 25)
        assertTrue(resched is WorkerCompletionAction.Rescheduled)
        assertEquals(15 * 60_000L, rescheduledDelay)

        // Reboot past expected end -> Interrupted with zero XP
        val rebootSession = FocusTimerEngine.startSession(
            sessionType = SessionType.POMODORO,
            plannedMinutes = 25,
            intentionLabel = "Rebooted",
            nowWallClockMs = clock.nowWallClockMs(),
            nowElapsedMs = clock.elapsedRealtimeMs(),
            bootCount = 1,
            sessionId = "sess_reboot"
        )
        focusRepo.saveActiveTimerState(rebootSession)
        clock.boot = 2
        clock.wallMs += 30 * 60_000L
        clock.elapsedMs = 5_000L
        val interrupted = coordinator.execute("sess_reboot", "Pomodoro", 25)
        assertEquals(WorkerCompletionAction.Interrupted("sess_reboot"), interrupted)
        assertNull(focusRepo.activeTimerState.first())
        assertEquals(0, focusRepo.userProfile.first()!!.lifetimeXp)

        // Valid completion -> Completed + rewards granted once + duplicate run ignored
        val fresh = FocusTimerEngine.startSession(
            sessionType = SessionType.POMODORO,
            plannedMinutes = 25,
            intentionLabel = "Finish",
            nowWallClockMs = clock.nowWallClockMs(),
            nowElapsedMs = clock.elapsedRealtimeMs(),
            bootCount = clock.bootCount(),
            sessionId = "sess_valid"
        )
        focusRepo.saveActiveTimerState(fresh)
        clock.wallMs += 25 * 60_000L
        clock.elapsedMs += 25 * 60_000L
        val completed = coordinator.execute("sess_valid", "Pomodoro", 25)
        assertTrue(completed is WorkerCompletionAction.Completed)
        val xpAfterComplete = focusRepo.userProfile.first()!!.lifetimeXp
        assertTrue(xpAfterComplete >= 50)

        // Re-running coordinator immediately after clears active state -> Ignored
        assertEquals(
            WorkerCompletionAction.IgnoredStaleOrMissing,
            coordinator.execute("sess_valid", "Pomodoro", 25)
        )
        assertEquals(xpAfterComplete, focusRepo.userProfile.first()!!.lifetimeXp)
    }

    @Test
    fun `pomodoro chain mode advances focus, short break, long break, and completion`() = runTest {
        focusRepo.completeOnboarding("Alex", 50, UserIntention.STUDYING)
        val config = ChainConfig(
            totalFocusSessions = 2,
            focusMinutes = 25,
            shortBreakMinutes = 5,
            longBreakMinutes = 15,
            longBreakFrequency = 2,
            autoTransition = false
        ).validated()
        val chain = ActiveChainState(
            chainId = "chain_1",
            config = config,
            intentionLabel = "Exam Prep"
        )
        focusRepo.saveActiveChainState(chain)

        // Step 1: First focus block finishes -> advances to SHORT_BREAK (since total=2 & freq=2, 1%2 != 0)
        val focus1 = FocusTimerEngine.startSession(
            sessionType = SessionType.POMODORO,
            plannedMinutes = 25,
            intentionLabel = "Exam Prep",
            nowWallClockMs = clock.nowWallClockMs(),
            nowElapsedMs = clock.elapsedRealtimeMs(),
            bootCount = 1,
            sessionId = "c_f1",
            chainId = "chain_1"
        )
        val out1 = focusRepo.recordCompletedSession(focus1)
        val afterFocus1 = focusRepo.advanceChainForCompletedSession("chain_1", out1.session)!!
        assertEquals(1, afterFocus1.completedFocusCount)
        assertEquals(SessionType.SHORT_BREAK, afterFocus1.currentStepType)
        assertTrue(afterFocus1.waitingForUserNextStep)
        assertFalse(afterFocus1.isCompleted)

        // Step 2: Break finishes (earns 0 XP) -> advances back to POMODORO
        val brk1 = FocusTimerEngine.startSession(
            sessionType = SessionType.SHORT_BREAK,
            plannedMinutes = 5,
            intentionLabel = "Break",
            nowWallClockMs = clock.nowWallClockMs() + 1_600_000L,
            nowElapsedMs = clock.elapsedRealtimeMs() + 1_600_000L,
            bootCount = 1,
            sessionId = "c_b1",
            chainId = "chain_1"
        )
        val outB1 = focusRepo.recordCompletedSession(brk1)
        assertEquals(0, outB1.session.xpAwarded)
        val afterBreak = focusRepo.advanceChainForCompletedSession("chain_1", outB1.session)!!
        assertEquals(1, afterBreak.completedBreakCount)
        assertEquals(SessionType.POMODORO, afterBreak.currentStepType)

        // Step 3: Second focus block finishes -> chain completes
        val focus2 = FocusTimerEngine.startSession(
            sessionType = SessionType.POMODORO,
            plannedMinutes = 25,
            intentionLabel = "Exam Prep",
            nowWallClockMs = clock.nowWallClockMs() + 2_000_000L,
            nowElapsedMs = clock.elapsedRealtimeMs() + 2_000_000L,
            bootCount = 1,
            sessionId = "c_f2",
            chainId = "chain_1"
        )
        val out2 = focusRepo.recordCompletedSession(focus2)
        val finished = focusRepo.advanceChainForCompletedSession("chain_1", out2.session)!!
        assertTrue(finished.isCompleted)
        assertEquals(2, finished.completedFocusCount)
        assertEquals(50, finished.totalFocusedMinutes)
    }

    @Test
    fun `auto chain recovery persists the next timer exactly once after process death`() = runTest {
        focusRepo.completeOnboarding("Recovery", 50, UserIntention.WORK)
        val config = ChainConfig(
            totalFocusSessions = 2,
            focusMinutes = 25,
            shortBreakMinutes = 5,
            longBreakMinutes = 15,
            longBreakFrequency = 2,
            autoTransition = true
        ).validated()
        val chain = ActiveChainState(chainId = "chain_auto_recovery", config = config)
        focusRepo.saveActiveChainState(chain)

        val focus1 = FocusTimerEngine.startSession(
            sessionType = SessionType.POMODORO,
            plannedMinutes = 25,
            intentionLabel = "Recovery",
            nowWallClockMs = clock.nowWallClockMs(),
            nowElapsedMs = clock.elapsedRealtimeMs(),
            bootCount = clock.bootCount(),
            sessionId = "auto_recovery_f1",
            chainId = chain.chainId
        )
        focusRepo.startChainStep(chain, focus1)
        assertEquals("auto_recovery_f1", prefs.activeChainState.first()!!.pendingStepSessionId)

        // Process death after completion but before any continuation was written.
        clock.wallMs += 25 * 60_000L
        clock.elapsedMs += 25 * 60_000L
        val outcome = focusRepo.recordCompletedSession(focus1)
        assertTrue(outcome.session.xpAwarded > 0)
        assertNull(prefs.activeTimerState.first())
        val xpAfterCompletion = focusRepo.userProfile.first()!!.lifetimeXp

        val recovered = focusRepo.recoverChainState()
        val nextTimer = recovered.nextTimer
        assertNotNull(nextTimer)
        assertEquals(SessionType.SHORT_BREAK, nextTimer!!.sessionType)
        assertNotEquals(focus1.sessionId, nextTimer.sessionId)
        assertTrue(nextTimer.expectedEndWallClockMs > nextTimer.startedAtWallMs)
        assertEquals(1, recovered.chain!!.completedFocusCount)
        assertEquals(focus1.sessionId, recovered.chain!!.lastAdvancedSessionId)
        assertEquals(nextTimer.sessionId, recovered.chain!!.pendingStepSessionId)

        // A second recovery must return the same persisted timer, not create/advance another step.
        val recoveredAgain = focusRepo.recoverChainState()
        assertEquals(nextTimer.sessionId, recoveredAgain.nextTimer!!.sessionId)
        assertEquals(1, recoveredAgain.chain!!.completedFocusCount)
        assertEquals(xpAfterCompletion, focusRepo.userProfile.first()!!.lifetimeXp)
    }

    @Test
    fun `worker advances chain when rewards were already recorded before continuation was persisted`() = runTest {
        focusRepo.completeOnboarding("CrashSafe", 50, UserIntention.WORK)
        challengeRepo.ensureChallengesGenerated(false)
        val config = ChainConfig(
            totalFocusSessions = 2,
            focusMinutes = 25,
            shortBreakMinutes = 5,
            longBreakMinutes = 15,
            longBreakFrequency = 2,
            autoTransition = true
        ).validated()
        val chain = ActiveChainState(chainId = "chain_crash_safe", config = config)
        focusRepo.saveActiveChainState(chain)

        val focus1 = FocusTimerEngine.startSession(
            sessionType = SessionType.POMODORO,
            plannedMinutes = 25,
            intentionLabel = "CrashSafe",
            nowWallClockMs = clock.nowWallClockMs(),
            nowElapsedMs = clock.elapsedRealtimeMs(),
            bootCount = clock.bootCount(),
            sessionId = "crash_safe_f1",
            chainId = chain.chainId
        )
        focusRepo.startChainStep(chain, focus1)
        clock.wallMs += 25 * 60_000L
        clock.elapsedMs += 25 * 60_000L

        // Simulate the UI committing rewards and then dying before chain continuation.
        val outcome = focusRepo.recordCompletedSession(focus1)
        assertTrue(outcome.session.xpAwarded > 0)
        assertNull(prefs.activeTimerState.first())
        assertEquals(0, prefs.activeChainState.first()!!.completedFocusCount)
        val xpAfterCompletion = focusRepo.userProfile.first()!!.lifetimeXp

        // The timer and pending step id were persisted before the crash.
        focusRepo.saveActiveTimerState(focus1)

        var scheduledSessionId: String? = null
        val coordinator = SessionCompletionCoordinator(
            context = context,
            focusRepository = focusRepo,
            prefs = prefs,
            clockProvider = clock,
            scheduleReschedule = { _, sessionId, _, _ -> scheduledSessionId = sessionId }
        )

        val result = coordinator.execute("crash_safe_f1", "Pomodoro", 25)
        assertTrue(result is WorkerCompletionAction.Continued)
        val breakTimer = prefs.activeTimerState.first()
        assertNotNull(breakTimer)
        assertEquals(SessionType.SHORT_BREAK, breakTimer!!.sessionType)
        assertEquals(breakTimer.sessionId, scheduledSessionId)
        assertEquals(1, prefs.activeChainState.first()!!.completedFocusCount)
        assertEquals(xpAfterCompletion, focusRepo.userProfile.first()!!.lifetimeXp)
    }

    @Test
    fun `worker completes auto focus and break steps without the app and ignores stale workers`() = runTest {
        focusRepo.completeOnboarding("Worker", 50, UserIntention.WORK)
        challengeRepo.ensureChallengesGenerated(false)
        val config = ChainConfig(
            totalFocusSessions = 2,
            focusMinutes = 25,
            shortBreakMinutes = 5,
            longBreakMinutes = 15,
            longBreakFrequency = 2,
            autoTransition = true
        ).validated()
        val chain = ActiveChainState(chainId = "chain_worker_auto", config = config)
        focusRepo.saveActiveChainState(chain)

        val scheduled = mutableListOf<String>()
        val coordinator = SessionCompletionCoordinator(
            context = context,
            focusRepository = focusRepo,
            prefs = prefs,
            clockProvider = clock,
            scheduleReschedule = { _, sessionId, _, _ -> scheduled.add(sessionId) }
        )

        val focus1 = FocusTimerEngine.startSession(
            sessionType = SessionType.POMODORO,
            plannedMinutes = 25,
            intentionLabel = "Worker",
            nowWallClockMs = clock.nowWallClockMs(),
            nowElapsedMs = clock.elapsedRealtimeMs(),
            bootCount = clock.bootCount(),
            sessionId = "worker_auto_f1",
            chainId = chain.chainId
        )
        focusRepo.startChainStep(chain, focus1)
        clock.wallMs += 25 * 60_000L
        clock.elapsedMs += 25 * 60_000L

        val focusResult = coordinator.execute("worker_auto_f1", "Pomodoro", 25)
        assertTrue(focusResult is WorkerCompletionAction.Completed)
        val breakTimer = prefs.activeTimerState.first()
        assertNotNull(breakTimer)
        assertEquals(SessionType.SHORT_BREAK, breakTimer!!.sessionType)
        assertTrue(scheduled.contains(breakTimer.sessionId))

        // Break finishes while no Activity/ViewModel exists.
        clock.wallMs += 5 * 60_000L
        clock.elapsedMs += 5 * 60_000L
        val breakResult = coordinator.execute(breakTimer.sessionId, "Short Break", 5)
        assertTrue(breakResult is WorkerCompletionAction.Completed)
        val nextFocus = prefs.activeTimerState.first()
        assertNotNull(nextFocus)
        assertEquals(SessionType.POMODORO, nextFocus!!.sessionType)
        assertNotEquals(focus1.sessionId, nextFocus.sessionId)
        assertEquals(1, prefs.activeChainState.first()!!.completedBreakCount)
        assertEquals(1, prefs.activeChainState.first()!!.completedFocusCount)

        // Stale worker from the first step must not complete or duplicate the running next step.
        val beforeStale = focusRepo.userProfile.first()!!.lifetimeXp
        assertEquals(
            WorkerCompletionAction.IgnoredStaleOrMissing,
            coordinator.execute("worker_auto_f1", "Pomodoro", 25)
        )
        assertEquals(nextFocus.sessionId, prefs.activeTimerState.first()!!.sessionId)
        assertEquals(beforeStale, focusRepo.userProfile.first()!!.lifetimeXp)
    }

    @Test
    fun `manual chain waits for user, reboot pauses the chain, and the final focus ends cleanly`() = runTest {
        focusRepo.completeOnboarding("Manual", 50, UserIntention.STUDYING)

        // Manual transition: persist the next step but never start a background timer.
        val manualConfig = ChainConfig(
            totalFocusSessions = 2,
            focusMinutes = 25,
            shortBreakMinutes = 5,
            longBreakMinutes = 15,
            longBreakFrequency = 2,
            autoTransition = false
        ).validated()
        val manualChain = ActiveChainState(chainId = "chain_manual", config = manualConfig)
        focusRepo.saveActiveChainState(manualChain)
        val manualFocus = FocusTimerEngine.startSession(
            sessionType = SessionType.POMODORO,
            plannedMinutes = 25,
            intentionLabel = "Manual",
            nowWallClockMs = clock.nowWallClockMs(),
            nowElapsedMs = clock.elapsedRealtimeMs(),
            bootCount = 1,
            sessionId = "manual_f1",
            chainId = manualChain.chainId
        )
        focusRepo.startChainStep(manualChain, manualFocus)
        clock.wallMs += 25 * 60_000L
        clock.elapsedMs += 25 * 60_000L
        val manualOutcome = focusRepo.recordCompletedSession(manualFocus)
        val manualContinuation = focusRepo.continueChainAfterCompletion(manualChain.chainId, manualOutcome.session)
        assertNull(manualContinuation.nextTimer)
        assertTrue(manualContinuation.chain!!.waitingForUserNextStep)
        assertEquals(SessionType.SHORT_BREAK, manualContinuation.chain!!.currentStepType)
        assertNull(prefs.activeTimerState.first())
        assertNull(focusRepo.recoverChainState().nextTimer)

        // Reboot during an auto chain pauses it for the user instead of auto-starting a duplicate.
        val rebootConfig = manualConfig.copy(autoTransition = true)
        val rebootChain = ActiveChainState(chainId = "chain_reboot", config = rebootConfig)
        focusRepo.saveActiveChainState(rebootChain)
        val rebootFocus = FocusTimerEngine.startSession(
            sessionType = SessionType.POMODORO,
            plannedMinutes = 25,
            intentionLabel = "Reboot",
            nowWallClockMs = clock.nowWallClockMs(),
            nowElapsedMs = clock.elapsedRealtimeMs(),
            bootCount = 1,
            sessionId = "reboot_f1",
            chainId = rebootChain.chainId
        )
        focusRepo.startChainStep(rebootChain, rebootFocus)
        clock.boot = 2
        clock.wallMs += 30 * 60_000L
        clock.elapsedMs = 1_000L
        val coordinator = SessionCompletionCoordinator(
            context = context,
            focusRepository = focusRepo,
            prefs = prefs,
            clockProvider = clock,
            scheduleReschedule = { _, _, _, _ -> }
        )
        assertEquals(
            WorkerCompletionAction.Interrupted("reboot_f1"),
            coordinator.execute("reboot_f1", "Pomodoro", 25)
        )
        assertNull(prefs.activeTimerState.first())
        assertTrue(prefs.activeChainState.first()!!.waitingForUserNextStep)
        assertNull(focusRepo.recoverChainState().nextTimer)

        // Final focus session completes the chain without creating another timer.
        val finalChain = ActiveChainState(
            chainId = "chain_final",
            config = rebootConfig.copy(totalFocusSessions = 1),
            intentionLabel = "Final"
        )
        focusRepo.saveActiveChainState(finalChain)
        val finalFocus = FocusTimerEngine.startSession(
            sessionType = SessionType.POMODORO,
            plannedMinutes = 25,
            intentionLabel = "Final",
            nowWallClockMs = clock.nowWallClockMs(),
            nowElapsedMs = clock.elapsedRealtimeMs(),
            bootCount = 2,
            sessionId = "final_f1",
            chainId = finalChain.chainId
        )
        focusRepo.startChainStep(finalChain, finalFocus)
        clock.wallMs += 25 * 60_000L
        clock.elapsedMs += 25 * 60_000L
        val finalOutcome = focusRepo.recordCompletedSession(finalFocus)
        val finalContinuation = focusRepo.continueChainAfterCompletion(finalChain.chainId, finalOutcome.session)
        assertTrue(finalContinuation.chain!!.isCompleted)
        assertNull(finalContinuation.nextTimer)
        assertNull(prefs.activeTimerState.first())
    }

    @Test
    fun `weekly summary coordinator respects notification toggle and quiet hours`() = runTest {
        prefs.updateNotificationPreferences(
            NotificationPreferences(
                notificationsEnabled = false,
                weeklySummaryEnabled = true
            )
        )
        val coordinator = WeeklySummaryCoordinator(context, db, prefs, clock)
        assertFalse(coordinator.execute())

        prefs.updateNotificationPreferences(
            NotificationPreferences(
                notificationsEnabled = true,
                weeklySummaryEnabled = true,
                quietHoursEnabled = true,
                quietHoursStartHour = 12,
                quietHoursEndHour = 16
            )
        )
        // WorkerTestClock wallMs is ~13:33 UTC -> inside 12..16 quiet hours -> suppressed
        assertFalse(coordinator.execute())
    }

    @Test
    fun `ambient audio controller state transitions work cleanly`() {
        val audio = FakeAmbientAudioController()
        assertFalse(audio.isPlaying.value)
        audio.startOrUpdate(AmbientSoundPreset.SOFT_RAIN, 0.7f)
        assertTrue(audio.isPlaying.value)
        assertEquals(AmbientSoundPreset.SOFT_RAIN, audio.activePreset.value)
        assertEquals(0.7f, audio.lastVolume, 0.01f)

        audio.pause()
        assertFalse(audio.isPlaying.value)

        audio.startOrUpdate(AmbientSoundPreset.NONE, 0.7f)
        assertFalse(audio.isPlaying.value)
        assertEquals(AmbientSoundPreset.NONE, audio.activePreset.value)
    }
}
