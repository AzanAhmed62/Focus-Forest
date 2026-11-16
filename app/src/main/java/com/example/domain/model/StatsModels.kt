package com.example.domain.model

data class DailyStats(
    val localDate: String,
    val focusMinutes: Int = 0,
    val completedSessions: Int = 0,
    val goalMinutes: Int = 50,
    val goalMet: Boolean = false,
    val xpEarned: Int = 0,
    val seedsEarned: Int = 0,
    val isRestDay: Boolean = false
) {
    val goalProgressFraction: Float
        get() = if (goalMinutes <= 0) 0f else (focusMinutes.toFloat() / goalMinutes.toFloat()).coerceIn(0f, 1f)
}

data class UserProfile(
    val id: Int = 1,
    val displayName: String = "",
    val primaryIntention: UserIntention = UserIntention.CONCENTRATION,
    val dailyFocusGoalMinutes: Int = 50,
    val lifetimeXp: Int = 0,
    val currentLevel: Int = 1,
    val currentStreakDays: Int = 0,
    val longestStreakDays: Int = 0,
    val equippedAvatarId: String = AvatarStyle.SEEDLING.id,
    val equippedThemeId: String = ForestThemeSkin.CLASSIC_SAGE.id,
    val createdAtMs: Long = 0L
)

data class NotificationPreferences(
    val notificationsEnabled: Boolean = false,
    val sessionCompletionEnabled: Boolean = true,
    val dailyReminderEnabled: Boolean = false,
    val dailyReminderHour: Int = 9,
    val dailyReminderMinute: Int = 0,
    val incompleteGoalReminderEnabled: Boolean = false,
    val weeklySummaryEnabled: Boolean = false,
    val quietHoursEnabled: Boolean = true,
    val quietHoursStartHour: Int = 22,
    val quietHoursEndHour: Int = 7
)
