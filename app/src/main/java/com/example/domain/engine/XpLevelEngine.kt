package com.example.domain.engine

import com.example.domain.model.SessionType

data class SessionReward(
    val xp: Int,
    val seeds: Int
)

data class LevelProgressInfo(
    val level: Int,
    val lifetimeXp: Int,
    val currentLevelStartXp: Int,
    val nextLevelXp: Int,
    val progressFraction: Float
)

object XpLevelEngine {

    /**
     * Computes direct session rewards.
     * Restorative break sessions (SHORT_BREAK, LONG_BREAK) intentionally grant 0 XP and 0 seeds
     * so breaks cannot be farmed; planned breaks can still advance planned-break challenges.
     */
    fun calculateSessionReward(sessionType: SessionType, completedMinutes: Int): SessionReward {
        if (completedMinutes <= 0 || sessionType.isBreak) return SessionReward(xp = 0, seeds = 0)
        return when (sessionType) {
            SessionType.SHORT_BREAK,
            SessionType.LONG_BREAK -> SessionReward(xp = 0, seeds = 0)
            SessionType.POMODORO -> {
                val baseXp = completedMinutes * 2
                val seeds = (completedMinutes / 10).coerceAtLeast(1)
                SessionReward(xp = baseXp, seeds = seeds)
            }
            SessionType.DEEP_WORK -> {
                val baseXp = completedMinutes * 2 + 20
                val seeds = (completedMinutes / 10).coerceAtLeast(2)
                SessionReward(xp = baseXp, seeds = seeds)
            }
            SessionType.CUSTOM -> {
                val baseXp = completedMinutes * 2
                val seeds = (completedMinutes / 10).coerceAtLeast(1)
                SessionReward(xp = baseXp, seeds = seeds)
            }
        }
    }

    /**
     * Cumulative XP required to reach [level].
     * Level 1: 0 XP
     * Level 2: 150 XP
     * Level 3: 350 XP
     * Level 4: 600 XP
     * Level 5: 900 XP
     */
    fun xpRequiredForLevel(level: Int): Int {
        if (level <= 1) return 0
        var total = 0
        for (l in 1 until level) {
            total += 100 + l * 50
        }
        return total
    }

    fun levelForXp(lifetimeXp: Int): Int {
        val safeXp = lifetimeXp.coerceAtLeast(0)
        var level = 1
        while (safeXp >= xpRequiredForLevel(level + 1)) {
            level++
        }
        return level
    }

    fun levelProgress(lifetimeXp: Int): LevelProgressInfo {
        val safeXp = lifetimeXp.coerceAtLeast(0)
        val level = levelForXp(safeXp)
        val start = xpRequiredForLevel(level)
        val next = xpRequiredForLevel(level + 1)
        val span = (next - start).coerceAtLeast(1)
        val fraction = ((safeXp - start).toFloat() / span.toFloat()).coerceIn(0f, 1f)
        return LevelProgressInfo(
            level = level,
            lifetimeXp = safeXp,
            currentLevelStartXp = start,
            nextLevelXp = next,
            progressFraction = fraction
        )
    }

    fun streakMilestoneBonusXp(streakDays: Int): Int = when (streakDays) {
        3 -> 25
        7 -> 50
        14 -> 80
        30 -> 150
        60 -> 250
        100 -> 400
        else -> 0
    }
}
