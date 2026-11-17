package com.example.domain.model

enum class ChallengePeriod {
    DAILY,
    WEEKLY,
    CUSTOM
}

enum class ChallengeMetricType {
    FOCUS_MINUTES,
    COMPLETED_SESSIONS,
    DEEP_WORK_SESSIONS,
    POMODORO_SESSIONS,
    PLANNED_BREAKS,
    USAGE_REDUCTION_PERCENT,
    DAILY_USAGE_BUDGET,
    WEEKLY_FOCUS_GOAL
}

enum class ChallengeStatus {
    ACTIVE,
    COMPLETED,
    EXPIRED
}

enum class ChallengeIconType {
    TIMER,
    PINE_TREE,
    SPARK,
    SUNRISE,
    SHIELD_LEAF,
    CUP_TEA
}

data class ChallengeItem(
    val id: String,
    val title: String,
    val description: String,
    val iconType: ChallengeIconType,
    val period: ChallengePeriod,
    val metricType: ChallengeMetricType,
    val targetValue: Int,
    val currentProgress: Int,
    val rewardXp: Int,
    val rewardSeeds: Int,
    val status: ChallengeStatus,
    val periodKey: String,
    val createdAtMs: Long,
    val completedAtMs: Long?,
    val rewardClaimed: Boolean,
    val targetPackageName: String? = null,
    val isArchived: Boolean = false
) {
    val progressFraction: Float
        get() = if (targetValue <= 0) 0f else (currentProgress.toFloat() / targetValue.toFloat()).coerceIn(0f, 1f)

    val isCustom: Boolean
        get() = period == ChallengePeriod.CUSTOM
}
