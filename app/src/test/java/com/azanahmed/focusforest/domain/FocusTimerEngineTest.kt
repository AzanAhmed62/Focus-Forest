package com.azanahmed.focusforest.domain

import com.azanahmed.focusforest.domain.engine.FocusTimerEngine
import com.azanahmed.focusforest.domain.engine.TimerRecoveryResult
import com.azanahmed.focusforest.domain.model.SessionStatus
import com.azanahmed.focusforest.domain.model.SessionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class FocusTimerEngineTest {

    @Test
    fun `start pause resume and complete follow monotonic elapsed time`() {
        val started = FocusTimerEngine.startSession(
            sessionType = SessionType.POMODORO,
            plannedMinutes = 25,
            intentionLabel = "Study",
            nowWallClockMs = 1_000_000L,
            nowElapsedMs = 50_000L,
            bootCount = 1,
            sessionId = "sess_1"
        )
        assertEquals(SessionStatus.RUNNING, started.status)
        assertEquals(25 * 60_000L, started.remainingMs)

        // 10 minutes pass
        val paused = FocusTimerEngine.pause(
            state = started,
            nowWallClockMs = 1_600_000L,
            nowElapsedMs = 650_000L,
            currentBootCount = 1
        )
        assertEquals(SessionStatus.PAUSED, paused.status)
        assertEquals(15 * 60_000L, paused.remainingMs)

        // Resume 5 minutes later in wall clock — remaining must still be 15 minutes
        val resumed = FocusTimerEngine.resume(
            state = paused,
            nowWallClockMs = 1_900_000L,
            nowElapsedMs = 950_000L,
            currentBootCount = 1
        )
        assertEquals(SessionStatus.RUNNING, resumed.status)
        assertEquals(15 * 60_000L, resumed.remainingMs)

        // Advance past remaining 15 minutes
        val eval = FocusTimerEngine.evaluateState(
            state = resumed,
            nowWallClockMs = 2_800_000L,
            nowElapsedMs = 1_850_000L,
            currentBootCount = 1
        )
        assertTrue(eval is TimerRecoveryResult.ReadyToComplete)

        // Duplicate reward guard
        val completedFirst = FocusTimerEngine.buildCompletedSession(
            state = resumed,
            nowWallClockMs = 2_800_000L,
            localDate = LocalDate.of(2026, 11, 16),
            alreadyRewardedSessionIds = emptySet()
        )
        assertTrue(completedFirst.rewardGranted)
        assertEquals(50, completedFirst.xpAwarded)

        val completedDuplicate = FocusTimerEngine.buildCompletedSession(
            state = resumed,
            nowWallClockMs = 2_800_000L,
            localDate = LocalDate.of(2026, 11, 16),
            alreadyRewardedSessionIds = setOf("sess_1")
        )
        assertFalse(completedDuplicate.rewardGranted)
        assertEquals(0, completedDuplicate.xpAwarded)
        assertEquals(0, completedDuplicate.seedsAwarded)
    }

    @Test
    fun `device reboot recovery restores remaining time or marks interrupted if expired`() {
        val started = FocusTimerEngine.startSession(
            sessionType = SessionType.POMODORO,
            plannedMinutes = 25,
            intentionLabel = "",
            nowWallClockMs = 1_000_000L,
            nowElapsedMs = 50_000L,
            bootCount = 1,
            sessionId = "sess_boot"
        )

        // Reboot occurred (bootCount 2), 10 minutes elapsed wall-clock -> 15 minutes remain
        val activeAfterBoot = FocusTimerEngine.evaluateState(
            state = started,
            nowWallClockMs = 1_600_000L,
            nowElapsedMs = 10_000L,
            currentBootCount = 2
        )
        assertTrue(activeAfterBoot is TimerRecoveryResult.Active)
        val recoveredState = (activeAfterBoot as TimerRecoveryResult.Active).state
        assertEquals(15 * 60_000L, recoveredState.remainingMs)
        assertEquals(2, recoveredState.bootCount)

        // Reboot occurred AFTER expected end time -> must be marked INTERRUPTED, never auto-completed
        val expiredDuringReboot = FocusTimerEngine.evaluateState(
            state = started,
            nowWallClockMs = 3_000_000L,
            nowElapsedMs = 10_000L,
            currentBootCount = 2
        )
        assertTrue(expiredDuringReboot is TimerRecoveryResult.InterruptedAfterReboot)
        assertEquals(
            SessionStatus.INTERRUPTED,
            (expiredDuringReboot as TimerRecoveryResult.InterruptedAfterReboot).state.status
        )
    }

    @Test
    fun `aborted session awards zero xp and zero seeds`() {
        val started = FocusTimerEngine.startSession(
            sessionType = SessionType.DEEP_WORK,
            plannedMinutes = 50,
            intentionLabel = "",
            nowWallClockMs = 1_000_000L,
            nowElapsedMs = 50_000L,
            bootCount = 1
        )
        val aborted = FocusTimerEngine.buildAbortedSession(
            state = started.copy(remainingMs = 30 * 60_000L),
            nowWallClockMs = 2_200_000L,
            localDate = LocalDate.of(2026, 11, 16)
        )
        assertEquals(SessionStatus.ABORTED, aborted.status)
        assertEquals(0, aborted.xpAwarded)
        assertEquals(0, aborted.seedsAwarded)
        assertFalse(aborted.rewardGranted)
    }
}
