package com.azanahmed.focusforest.domain.engine

import com.azanahmed.focusforest.domain.model.ActiveChainState
import com.azanahmed.focusforest.domain.model.FocusSession
import com.azanahmed.focusforest.domain.model.SessionType

/**
 * Pure Pomodoro Chain progression rules.
 *
 * The state machine is intentionally side-effect free so worker completion, in-app completion
 * and cold-start recovery all apply exactly the same transition.
 */
object ChainStateMachine {

    fun advanceForCompletedSession(
        chain: ActiveChainState,
        session: FocusSession
    ): ActiveChainState {
        if (chain.isCompleted) return chain

        val updated = if (!session.sessionType.isBreak) {
            val newFocusCount = chain.completedFocusCount + 1
            val newFocusedMin = chain.totalFocusedMinutes + session.completedMinutes
            val newXp = chain.totalEarnedXp + session.xpAwarded
            val newSeeds = chain.totalEarnedSeeds + session.seedsAwarded
            if (newFocusCount >= chain.config.totalFocusSessions) {
                chain.copy(
                    completedFocusCount = newFocusCount,
                    totalFocusedMinutes = newFocusedMin,
                    totalEarnedXp = newXp,
                    totalEarnedSeeds = newSeeds,
                    waitingForUserNextStep = false,
                    isCompleted = true
                )
            } else {
                val nextBreak = if (newFocusCount % chain.config.longBreakFrequency == 0) {
                    SessionType.LONG_BREAK
                } else {
                    SessionType.SHORT_BREAK
                }
                chain.copy(
                    completedFocusCount = newFocusCount,
                    totalFocusedMinutes = newFocusedMin,
                    totalEarnedXp = newXp,
                    totalEarnedSeeds = newSeeds,
                    currentStepType = nextBreak,
                    waitingForUserNextStep = !chain.config.autoTransition,
                    isCompleted = false
                )
            }
        } else {
            chain.copy(
                completedBreakCount = chain.completedBreakCount + 1,
                currentStepType = SessionType.POMODORO,
                waitingForUserNextStep = !chain.config.autoTransition,
                isCompleted = false
            )
        }

        return updated.copy(
            lastAdvancedSessionId = session.id,
            pendingStepSessionId = null
        )
    }

    /**
     * When a step is interrupted (reboot or explicit abort of the step), the chain stays alive
     * but must wait for the user instead of silently auto-starting another block.
     */
    fun pauseForUserAfterInterruption(chain: ActiveChainState): ActiveChainState {
        if (chain.isCompleted) return chain
        return chain.copy(
            waitingForUserNextStep = true,
            pendingStepSessionId = null
        )
    }

    fun clearPendingStep(chain: ActiveChainState): ActiveChainState =
        chain.copy(pendingStepSessionId = null)
}
