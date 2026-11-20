package com.azanahmed.focusforest.domain.model

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

enum class SessionReflection(val label: String) {
    FOCUSED("Focused"),
    MIXED("Mixed"),
    INTERRUPTED("Interrupted")
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
    val rewardGranted: Boolean = false,
    /** Null for ad-hoc sessions; never a foreign key, so routine deletion cannot erase history. */
    val routineId: String? = null,
    val routineNameSnapshot: String = "",
    val routineSnapshot: String = "",
    val categoryId: String? = null,
    val categoryNameSnapshot: String = "",
    val note: String = "",
    val reflection: SessionReflection? = null
)

data class FocusCategory(
    val id: String,
    val name: String,
    val isArchived: Boolean = false,
    val sortOrder: Int = 0,
    val isBuiltIn: Boolean = false,
    val createdAtMs: Long = 0L
)

data class FocusRoutine(
    val id: String,
    val name: String,
    val description: String = "",
    val config: ChainConfig = ChainConfig(),
    val ambientPreset: AmbientSoundPreset = AmbientSoundPreset.NONE,
    val defaultCategoryId: String? = null,
    val isPinned: Boolean = false,
    val sortOrder: Int = 0,
    val isArchived: Boolean = false,
    val isBuiltIn: Boolean = false,
    val createdAtMs: Long = 0L,
    val lastUsedAtMs: Long? = null
) {
    /** Stable, human-readable configuration snapshot stored on completed sessions. */
    fun snapshotLabel(): String {
        val rounds = if (config.totalFocusSessions == 1) "1 round" else "${config.totalFocusSessions} rounds"
        val transition = if (config.autoTransition) "auto" else "manual"
        return "$rounds · ${config.focusMinutes}m focus · ${config.shortBreakMinutes}m short · " +
            "${config.longBreakMinutes}m long · every ${config.longBreakFrequency} rounds · $transition"
    }
}

data class RoutineValidation(
    val nameError: String? = null,
    val descriptionError: String? = null,
    val focusMinutesError: String? = null,
    val shortBreakMinutesError: String? = null,
    val longBreakMinutesError: String? = null,
    val totalFocusSessionsError: String? = null,
    val longBreakFrequencyError: String? = null
) {
    val isValid: Boolean
        get() = nameError == null && descriptionError == null && focusMinutesError == null &&
            shortBreakMinutesError == null && longBreakMinutesError == null &&
            totalFocusSessionsError == null && longBreakFrequencyError == null

    fun firstError(): String? = nameError ?: descriptionError ?: focusMinutesError
        ?: shortBreakMinutesError ?: longBreakMinutesError ?: totalFocusSessionsError
        ?: longBreakFrequencyError
}

fun FocusRoutine.validation(): RoutineValidation = RoutineValidation(
    nameError = when {
        name.isBlank() -> "Give the routine a name."
        name.trim().length > 40 -> "Routine names can be at most 40 characters."
        else -> null
    },
    descriptionError = if (description.trim().length > 200) {
        "Descriptions can be at most 200 characters."
    } else null,
    focusMinutesError = if (config.focusMinutes !in 5..180) "Focus must be 5–180 minutes." else null,
    shortBreakMinutesError = if (config.shortBreakMinutes !in 1..30) "Short breaks must be 1–30 minutes." else null,
    longBreakMinutesError = if (config.longBreakMinutes !in 5..45) "Long breaks must be 5–45 minutes." else null,
    totalFocusSessionsError = if (config.totalFocusSessions !in 1..8) "Rounds must be 1–8." else null,
    longBreakFrequencyError = if (config.longBreakFrequency !in 2..8) {
        "Long-break cadence must be 2–8 rounds."
    } else null
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
    val chainId: String? = null,
    val routineId: String? = null,
    val routineNameSnapshot: String = "",
    val routineSnapshot: String = "",
    val categoryId: String? = null,
    val categoryNameSnapshot: String = ""
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
    val intentionLabel: String = "",
    /**
     * Session id of the step that was persisted as the current chain timer. Used to recover
     * exactly one continuation after process death between session completion and chain advance.
     */
    val pendingStepSessionId: String? = null,
    /** Completed session id already applied to this chain, making advancement idempotent. */
    val lastAdvancedSessionId: String? = null,
    /** Routine/category snapshots copied at start so later edits cannot rewrite the chain. */
    val routineId: String? = null,
    val routineNameSnapshot: String = "",
    val routineSnapshot: String = "",
    val categoryId: String? = null,
    val categoryNameSnapshot: String = ""
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
