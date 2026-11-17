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

enum class AmbientSoundPreset(
    val displayName: String,
    val subtitle: String
) {
    NONE("Silent", "Pure quiet without synthesized ambience"),
    SOFT_RAIN("Soft Rain", "Gentle procedural rainfall texture"),
    FOREST_WIND("Forest Wind", "Warm rustling canopy breeze"),
    OCEAN_WAVES("Ocean Waves", "Slow, rhythmic coastal surf"),
    HEARTH_FIRE("Hearth Ember", "Low-frequency warm cabin rumble"),
    BROWN_NOISE("Deep Focus Hum", "Smooth low-pass brown noise")
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
    val intentionLabel: String = "",
    val chainId: String? = null
) {
    val progressFraction: Float
        get() = if (plannedDurationMs <= 0L) 0f
        else ((plannedDurationMs - remainingMs).toFloat() / plannedDurationMs.toFloat()).coerceIn(0f, 1f)
}

data class ChainConfig(
    val totalFocusSessions: Int = 4,
    val focusMinutes: Int = 25,
    val shortBreakMinutes: Int = 5,
    val longBreakMinutes: Int = 15,
    val longBreakFrequency: Int = 4,
    val autoTransition: Boolean = false
) {
    fun validated(): ChainConfig = copy(
        totalFocusSessions = totalFocusSessions.coerceIn(1, 8),
        focusMinutes = focusMinutes.coerceIn(5, 90),
        shortBreakMinutes = shortBreakMinutes.coerceIn(1, 30),
        longBreakMinutes = longBreakMinutes.coerceIn(5, 45),
        longBreakFrequency = longBreakFrequency.coerceIn(2, 8)
    )
}

data class ActiveChainState(
    val chainId: String,
    val config: ChainConfig,
    val completedFocusCount: Int = 0,
    val completedBreakCount: Int = 0,
    val totalFocusedMinutes: Int = 0,
    val totalEarnedXp: Int = 0,
    val totalEarnedSeeds: Int = 0,
    val currentStepType: SessionType = SessionType.POMODORO,
    val waitingForUserNextStep: Boolean = false,
    val isCompleted: Boolean = false,
    val intentionLabel: String = ""
) {
    val upcomingStepType: SessionType?
        get() {
            if (isCompleted) return null
            if (!currentStepType.isBreak) {
                val nextFocusCompleted = completedFocusCount + 1
                if (nextFocusCompleted >= config.totalFocusSessions) return null
                return if (nextFocusCompleted % config.longBreakFrequency == 0) {
                    SessionType.LONG_BREAK
                } else {
                    SessionType.SHORT_BREAK
                }
            }
            return SessionType.POMODORO
        }

    val currentStepPlannedMinutes: Int
        get() = when (currentStepType) {
            SessionType.SHORT_BREAK -> config.shortBreakMinutes
            SessionType.LONG_BREAK -> config.longBreakMinutes
            else -> config.focusMinutes
        }

    val overallProgressFraction: Float
        get() {
            val totalFocus = config.totalFocusSessions.coerceAtLeast(1)
            return (completedFocusCount.toFloat() / totalFocus.toFloat()).coerceIn(0f, 1f)
        }
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
