package com.example.domain.engine

import com.example.domain.model.AchievementItem
import com.example.domain.model.AchievementType
import com.example.domain.model.PersonalRecords

object AchievementEngine {

    fun evaluate(
        existing: Map<AchievementType, Long?>,
        records: PersonalRecords,
        completedChallengesCount: Int,
        currentLevel: Int,
        nowWallClockMs: Long
    ): Pair<List<AchievementItem>, List<AchievementType>> {
        val result = mutableListOf<AchievementItem>()
        val newlyUnlocked = mutableListOf<AchievementType>()

        for (type in AchievementType.entries) {
            val existingUnlockMs = existing[type]
            val conditionMet = when (type) {
                AchievementType.FIRST_FOCUS -> records.totalCompletedSessions >= 1
                AchievementType.THREE_DAY_CONSISTENCY -> records.longestStreakDays >= 3
                AchievementType.SEVEN_DAY_CONSISTENCY -> records.longestStreakDays >= 7
                AchievementType.THIRTY_DAY_CONSISTENCY -> records.longestStreakDays >= 30
                AchievementType.TEN_SESSIONS -> records.totalCompletedSessions >= 10
                AchievementType.FIFTY_SESSIONS -> records.totalCompletedSessions >= 50
                AchievementType.TEN_FOCUS_HOURS -> records.totalFocusMinutes >= 600
                AchievementType.FIRST_FIVE_CHALLENGES -> completedChallengesCount >= 5
                AchievementType.LEVEL_FIVE -> currentLevel >= 5
            }

            val unlockedNow = existingUnlockMs != null || conditionMet
            if (existingUnlockMs == null && conditionMet) {
                newlyUnlocked.add(type)
            }
            val progressLabel = when (type) {
                AchievementType.FIRST_FOCUS -> "${records.totalCompletedSessions.coerceAtMost(1)} / 1 session"
                AchievementType.THREE_DAY_CONSISTENCY -> "${records.longestStreakDays.coerceAtMost(3)} / 3 days"
                AchievementType.SEVEN_DAY_CONSISTENCY -> "${records.longestStreakDays.coerceAtMost(7)} / 7 days"
                AchievementType.THIRTY_DAY_CONSISTENCY -> "${records.longestStreakDays.coerceAtMost(30)} / 30 days"
                AchievementType.TEN_SESSIONS -> "${records.totalCompletedSessions.coerceAtMost(10)} / 10 sessions"
                AchievementType.FIFTY_SESSIONS -> "${records.totalCompletedSessions.coerceAtMost(50)} / 50 sessions"
                AchievementType.TEN_FOCUS_HOURS -> "${records.totalFocusMinutes.coerceAtMost(600)} / 600 min"
                AchievementType.FIRST_FIVE_CHALLENGES -> "${completedChallengesCount.coerceAtMost(5)} / 5 challenges"
                AchievementType.LEVEL_FIVE -> "Level ${currentLevel.coerceAtMost(5)} / 5"
            }

            result.add(
                AchievementItem(
                    type = type,
                    unlocked = unlockedNow,
                    unlockedAtMs = existingUnlockMs ?: if (conditionMet) nowWallClockMs else null,
                    progressLabel = progressLabel
                )
            )
        }
        return result to newlyUnlocked
    }
}
