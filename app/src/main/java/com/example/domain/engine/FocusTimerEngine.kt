package com.example.domain.engine

import com.example.domain.model.ActiveTimerState
import com.example.domain.model.FocusSession
import com.example.domain.model.SessionStatus
import com.example.domain.model.SessionType
import java.time.LocalDate
import java.util.UUID

sealed class TimerRecoveryResult {
    data class Active(val state: ActiveTimerState) : TimerRecoveryResult()
    data class ReadyToComplete(val state: ActiveTimerState) : TimerRecoveryResult()
    data class InterruptedAfterReboot(val state: ActiveTimerState) : TimerRecoveryResult()
}

object FocusTimerEngine {

    fun startSession(
        sessionType: SessionType,
        plannedMinutes: Int,
        intentionLabel: String,
        nowWallClockMs: Long,
        nowElapsedMs: Long,
        bootCount: Int,
        sessionId: String = UUID.randomUUID().toString()
    ): ActiveTimerState {
        val safeMinutes = plannedMinutes.coerceIn(1, 180)
        val durationMs = safeMinutes * 60_000L
        return ActiveTimerState(
            sessionId = sessionId,
            sessionType = sessionType,
            plannedDurationMs = durationMs,
            remainingMs = durationMs,
            status = SessionStatus.RUNNING,
            startedAtWallMs = nowWallClockMs,
            endElapsedRealtimeMs = nowElapsedMs + durationMs,
            expectedEndWallClockMs = nowWallClockMs + durationMs,
            bootCount = bootCount,
            intentionLabel = intentionLabel.trim()
        )
    }

    fun evaluateState(
        state: ActiveTimerState,
        nowWallClockMs: Long,
        nowElapsedMs: Long,
        currentBootCount: Int
    ): TimerRecoveryResult {
        if (state.status == SessionStatus.PAUSED) {
            return TimerRecoveryResult.Active(state)
        }
        if (state.status != SessionStatus.RUNNING) {
            return TimerRecoveryResult.Active(state)
        }

        // Same boot: monotonic elapsedRealtime is authoritative
        if (currentBootCount == state.bootCount) {
            val remaining = (state.endElapsedRealtimeMs - nowElapsedMs).coerceAtLeast(0L)
            val updated = state.copy(remainingMs = remaining)
            return if (remaining == 0L) {
                TimerRecoveryResult.ReadyToComplete(updated)
            } else {
                TimerRecoveryResult.Active(updated)
            }
        }

        // Device restarted while session was running: recover safely using expected wall clock
        val remainingAfterBoot = state.expectedEndWallClockMs - nowWallClockMs
        return if (remainingAfterBoot > 0L && remainingAfterBoot <= state.plannedDurationMs) {
            val recovered = state.copy(
                remainingMs = remainingAfterBoot,
                endElapsedRealtimeMs = nowElapsedMs + remainingAfterBoot,
                bootCount = currentBootCount
            )
            TimerRecoveryResult.Active(recovered)
        } else {
            // Timer expired while device was off/rebooted — do not fabricate unverified completion
            val interrupted = state.copy(
                remainingMs = 0L,
                status = SessionStatus.INTERRUPTED
            )
            TimerRecoveryResult.InterruptedAfterReboot(interrupted)
        }
    }

    fun pause(
        state: ActiveTimerState,
        nowWallClockMs: Long,
        nowElapsedMs: Long,
        currentBootCount: Int
    ): ActiveTimerState {
        if (state.status != SessionStatus.RUNNING) return state
        val evaluated = when (val res = evaluateState(state, nowWallClockMs, nowElapsedMs, currentBootCount)) {
            is TimerRecoveryResult.Active -> res.state
            is TimerRecoveryResult.ReadyToComplete -> res.state
            is TimerRecoveryResult.InterruptedAfterReboot -> return res.state
        }
        return evaluated.copy(status = SessionStatus.PAUSED)
    }

    fun resume(
        state: ActiveTimerState,
        nowWallClockMs: Long,
        nowElapsedMs: Long,
        currentBootCount: Int
    ): ActiveTimerState {
        if (state.status != SessionStatus.PAUSED) return state
        val remaining = state.remainingMs.coerceAtLeast(0L)
        return state.copy(
            status = SessionStatus.RUNNING,
            endElapsedRealtimeMs = nowElapsedMs + remaining,
            expectedEndWallClockMs = nowWallClockMs + remaining,
            bootCount = currentBootCount
        )
    }

    fun buildCompletedSession(
        state: ActiveTimerState,
        nowWallClockMs: Long,
        localDate: LocalDate,
        alreadyRewardedSessionIds: Set<String>
    ): FocusSession {
        val plannedMinutes = (state.plannedDurationMs / 60_000L).toInt().coerceAtLeast(1)
        val duplicateGuard = state.sessionId in alreadyRewardedSessionIds
        val reward = if (duplicateGuard) {
            SessionReward(0, 0)
        } else {
            XpLevelEngine.calculateSessionReward(state.sessionType, plannedMinutes)
        }
        return FocusSession(
            id = state.sessionId,
            sessionType = state.sessionType,
            plannedMinutes = plannedMinutes,
            completedMinutes = plannedMinutes,
            status = SessionStatus.COMPLETED,
            startedAtWallMs = state.startedAtWallMs,
            endedAtWallMs = nowWallClockMs,
            localDate = localDate.toString(),
            xpAwarded = reward.xp,
            seedsAwarded = reward.seeds,
            intentionLabel = state.intentionLabel,
            rewardGranted = !duplicateGuard
        )
    }

    fun buildAbortedSession(
        state: ActiveTimerState,
        nowWallClockMs: Long,
        localDate: LocalDate,
        status: SessionStatus = SessionStatus.ABORTED
    ): FocusSession {
        val plannedMinutes = (state.plannedDurationMs / 60_000L).toInt().coerceAtLeast(1)
        val elapsedMinutes = ((state.plannedDurationMs - state.remainingMs).coerceAtLeast(0L) / 60_000L).toInt()
        return FocusSession(
            id = state.sessionId,
            sessionType = state.sessionType,
            plannedMinutes = plannedMinutes,
            completedMinutes = elapsedMinutes,
            status = status,
            startedAtWallMs = state.startedAtWallMs,
            endedAtWallMs = nowWallClockMs,
            localDate = localDate.toString(),
            xpAwarded = 0,
            seedsAwarded = 0,
            intentionLabel = state.intentionLabel,
            rewardGranted = false
        )
    }
}
