package com.example.domain.model

enum class SessionType(
    val displayName: String,
    val defaultMinutes: Int,
    val isBreak: Boolean,
    val treeSpecies: TreeSpecies
) {
    POMODORO("Pomodoro", 25, false, TreeSpecies.PINE),
    DEEP_WORK("Deep Work", 50, false, TreeSpecies.OAK),
    SHORT_BREAK("Short Break", 5, true, TreeSpecies.FERN),
    LONG_BREAK("Long Break", 15, true, TreeSpecies.WILLOW),
    CUSTOM("Custom Focus", 35, false, TreeSpecies.BIRCH)
}

enum class SessionStatus {
    RUNNING,
    PAUSED,
    COMPLETED,
    ABORTED,
    INTERRUPTED
}

enum class UserIntention(val label: String, val subtitle: String) {
    STUDYING("Deep Studying", "Stay absorbed in readings, coursework, and exam prep"),
    WORK("Focused Work", "Protect uninterrupted blocks for meaningful output"),
    REDUCE_SOCIAL("Less Scrolling", "Notice distracting habits and reclaim spare minutes"),
    CONCENTRATION("Calm Concentration", "Build steady attention without burnout")
}

enum class ThemePreference(val label: String) {
    SYSTEM("Follow System"),
    LIGHT("Warm Cream Light"),
    DARK("Deep Canopy Dark")
}

enum class ForestThemeSkin(val id: String, val displayName: String) {
    CLASSIC_SAGE("theme_classic_sage", "Classic Forest Sage"),
    MORNING_MIST("theme_morning_mist", "Dawn Mist Meadow"),
    AUTUMN_CANOPY("theme_autumn_canopy", "Autumn Amber Canopy"),
    MIDNIGHT_PINE("theme_midnight_pine", "Midnight Pine Sanctuary")
}

enum class AvatarStyle(val id: String, val displayName: String) {
    SEEDLING("avatar_seedling", "Sprouting Seedling"),
    PINE_BADGE("avatar_pine", "Evergreen Pine"),
    ACORN("avatar_acorn", "Golden Acorn"),
    BOTANIST("avatar_botanist", "Forest Botanist"),
    OWL("avatar_owl", "Woodland Owl")
}

data class FocusSession(
    val id: String,
    val sessionType: SessionType,
    val plannedMinutes: Int,
    val completedMinutes: Int,
    val status: SessionStatus,
    val startedAtWallMs: Long,
    val endedAtWallMs: Long?,
    val localDate: String,
    val xpAwarded: Int,
    val seedsAwarded: Int,
    val intentionLabel: String = "",
    val rewardGranted: Boolean = false
)

data class ActiveTimerState(
    val sessionId: String,
    val sessionType: SessionType,
    val plannedDurationMs: Long,
    val remainingMs: Long,
    val status: SessionStatus,
    val startedAtWallMs: Long,
    val endElapsedRealtimeMs: Long,
    val expectedEndWallClockMs: Long,
    val bootCount: Int,
    val intentionLabel: String = ""
) {
    val progressFraction: Float
        get() = if (plannedDurationMs <= 0L) 0f
        else ((plannedDurationMs - remainingMs).toFloat() / plannedDurationMs.toFloat()).coerceIn(0f, 1f)
}

data class PersonalRecords(
    val totalFocusMinutes: Int = 0,
    val totalCompletedSessions: Int = 0,
    val longestSessionMinutes: Int = 0,
    val currentStreakDays: Int = 0,
    val longestStreakDays: Int = 0,
    val goalCompletionRatePercent: Int = 0,
    val totalActiveDays: Int = 0
)
