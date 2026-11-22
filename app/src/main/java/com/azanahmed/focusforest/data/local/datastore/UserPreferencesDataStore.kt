package com.azanahmed.focusforest.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.azanahmed.focusforest.domain.model.ActiveChainState
import com.azanahmed.focusforest.domain.model.ActiveTimerState
import com.azanahmed.focusforest.domain.model.AmbientSoundPreset
import com.azanahmed.focusforest.domain.model.ChainConfig
import com.azanahmed.focusforest.domain.model.NotificationPreferences
import com.azanahmed.focusforest.domain.model.SessionStatus
import com.azanahmed.focusforest.domain.model.SessionType
import com.azanahmed.focusforest.domain.model.ThemePreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.focusForestDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "focus_forest_prefs"
)

class UserPreferencesDataStore(private val dataStore: DataStore<Preferences>) {

    companion object {
        const val MAX_WEEKLY_GOAL_MINUTES = 3360
    }

    constructor(context: Context) : this(context.focusForestDataStore)

    private object Keys {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val THEME_PREFERENCE = stringPreferencesKey("theme_preference")
        val DAILY_GOAL_MINUTES = intPreferencesKey("daily_goal_minutes")
        val WEEKLY_GOAL_MINUTES = intPreferencesKey("weekly_goal_minutes")
        val USAGE_TRACKING_ENABLED = booleanPreferencesKey("usage_tracking_enabled")
        val USAGE_DISCLOSURE_ACCEPTED = booleanPreferencesKey("usage_disclosure_accepted")
        val MONITORED_PACKAGES = stringSetPreferencesKey("monitored_packages")
        val DAILY_USAGE_BUDGET_MINUTES = intPreferencesKey("daily_usage_budget_minutes")

        // Notifications
        val NOTIF_ENABLED = booleanPreferencesKey("notif_enabled")
        val NOTIF_SESSION_COMPLETE = booleanPreferencesKey("notif_session_complete")
        val NOTIF_DAILY_REMINDER = booleanPreferencesKey("notif_daily_reminder")
        val NOTIF_DAILY_HOUR = intPreferencesKey("notif_daily_hour")
        val NOTIF_DAILY_MINUTE = intPreferencesKey("notif_daily_minute")
        val NOTIF_INCOMPLETE_GOAL = booleanPreferencesKey("notif_incomplete_goal")
        val NOTIF_WEEKLY_SUMMARY = booleanPreferencesKey("notif_weekly_summary")
        val QUIET_HOURS_ENABLED = booleanPreferencesKey("quiet_hours_enabled")
        val QUIET_START_HOUR = intPreferencesKey("quiet_start_hour")
        val QUIET_END_HOUR = intPreferencesKey("quiet_end_hour")

        // Active focus timer persistence for process-death and reboot recovery
        val TIMER_SESSION_ID = stringPreferencesKey("timer_session_id")
        val TIMER_SESSION_TYPE = stringPreferencesKey("timer_session_type")
        val TIMER_PLANNED_MS = longPreferencesKey("timer_planned_ms")
        val TIMER_REMAINING_MS = longPreferencesKey("timer_remaining_ms")
        val TIMER_STATUS = stringPreferencesKey("timer_status")
        val TIMER_STARTED_WALL_MS = longPreferencesKey("timer_started_wall_ms")
        val TIMER_END_ELAPSED_MS = longPreferencesKey("timer_end_elapsed_ms")
        val TIMER_EXPECTED_END_WALL_MS = longPreferencesKey("timer_expected_end_wall_ms")
        val TIMER_BOOT_COUNT = intPreferencesKey("timer_boot_count")
        val TIMER_INTENTION_LABEL = stringPreferencesKey("timer_intention_label")
        val TIMER_CHAIN_ID = stringPreferencesKey("timer_chain_id")
        val TIMER_ROUTINE_ID = stringPreferencesKey("timer_routine_id")
        val TIMER_ROUTINE_NAME = stringPreferencesKey("timer_routine_name")
        val TIMER_ROUTINE_SNAPSHOT = stringPreferencesKey("timer_routine_snapshot")
        val TIMER_CATEGORY_ID = stringPreferencesKey("timer_category_id")
        val TIMER_CATEGORY_NAME = stringPreferencesKey("timer_category_name")

        // Pomodoro Chain Mode state
        val CHAIN_ID = stringPreferencesKey("chain_id")
        val CHAIN_TOTAL_FOCUS = intPreferencesKey("chain_total_focus")
        val CHAIN_FOCUS_MIN = intPreferencesKey("chain_focus_min")
        val CHAIN_SHORT_BREAK_MIN = intPreferencesKey("chain_short_break_min")
        val CHAIN_LONG_BREAK_MIN = intPreferencesKey("chain_long_break_min")
        val CHAIN_LONG_BREAK_FREQ = intPreferencesKey("chain_long_break_freq")
        val CHAIN_AUTO_TRANSITION = booleanPreferencesKey("chain_auto_transition")
        val CHAIN_COMPLETED_FOCUS = intPreferencesKey("chain_completed_focus")
        val CHAIN_COMPLETED_BREAKS = intPreferencesKey("chain_completed_breaks")
        val CHAIN_TOTAL_FOCUSED_MIN = intPreferencesKey("chain_total_focused_min")
        val CHAIN_EARNED_XP = intPreferencesKey("chain_earned_xp")
        val CHAIN_EARNED_SEEDS = intPreferencesKey("chain_earned_seeds")
        val CHAIN_CURRENT_STEP_TYPE = stringPreferencesKey("chain_current_step_type")
        val CHAIN_WAITING_NEXT = booleanPreferencesKey("chain_waiting_next")
        val CHAIN_IS_COMPLETED = booleanPreferencesKey("chain_is_completed")
        val CHAIN_INTENTION_LABEL = stringPreferencesKey("chain_intention_label")
        val CHAIN_PENDING_STEP_SESSION_ID = stringPreferencesKey("chain_pending_step_session_id")
        val CHAIN_LAST_ADVANCED_SESSION_ID = stringPreferencesKey("chain_last_advanced_session_id")
        val CHAIN_ROUTINE_ID = stringPreferencesKey("chain_routine_id")
        val CHAIN_ROUTINE_NAME = stringPreferencesKey("chain_routine_name")
        val CHAIN_ROUTINE_SNAPSHOT = stringPreferencesKey("chain_routine_snapshot")
        val CHAIN_CATEGORY_ID = stringPreferencesKey("chain_category_id")
        val CHAIN_CATEGORY_NAME = stringPreferencesKey("chain_category_name")

        // Ambient sound preferences
        val AMBIENT_PRESET = stringPreferencesKey("ambient_preset")
        val AMBIENT_VOLUME = floatPreferencesKey("ambient_volume")
        val AMBIENT_PLAY_WITH_SESSION = booleanPreferencesKey("ambient_play_with_session")

        // Default focus session shown when no timer is active
        val DEFAULT_SESSION_TYPE = stringPreferencesKey("default_session_type")
        val DEFAULT_SESSION_MINUTES = intPreferencesKey("default_session_minutes")
    }

    val onboardingCompleted: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.ONBOARDING_COMPLETED] ?: false
    }

    val themePreference: Flow<ThemePreference> = dataStore.data.map { prefs ->
        val raw = prefs[Keys.THEME_PREFERENCE] ?: ThemePreference.SYSTEM.name
        runCatching { ThemePreference.valueOf(raw) }.getOrDefault(ThemePreference.SYSTEM)
    }

    val dailyGoalMinutes: Flow<Int> = dataStore.data.map { prefs ->
        prefs[Keys.DAILY_GOAL_MINUTES] ?: 50
    }

    /**
     * Independent weekly target. Before the first explicit write, it falls back to the
     * previous effective weekly intention (daily × 7) so upgrading users keep their goal.
     * A value of 0 disables the weekly target.
     */
    val weeklyGoalMinutes: Flow<Int> = dataStore.data.map { prefs ->
        prefs[Keys.WEEKLY_GOAL_MINUTES]?.coerceIn(0, MAX_WEEKLY_GOAL_MINUTES)
            ?: (((prefs[Keys.DAILY_GOAL_MINUTES] ?: 50) * 7).coerceIn(0, MAX_WEEKLY_GOAL_MINUTES))
    }

    val usageTrackingEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.USAGE_TRACKING_ENABLED] ?: false
    }

    val usageDisclosureAccepted: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.USAGE_DISCLOSURE_ACCEPTED] ?: false
    }

    val monitoredPackages: Flow<Set<String>> = dataStore.data.map { prefs ->
        prefs[Keys.MONITORED_PACKAGES] ?: emptySet()
    }

    val dailyUsageBudgetMinutes: Flow<Int> = dataStore.data.map { prefs ->
        (prefs[Keys.DAILY_USAGE_BUDGET_MINUTES] ?: 120).coerceIn(15, 720)
    }

    val ambientPreset: Flow<AmbientSoundPreset> = dataStore.data.map { prefs ->
        val raw = prefs[Keys.AMBIENT_PRESET] ?: AmbientSoundPreset.NONE.name
        runCatching { AmbientSoundPreset.valueOf(raw) }.getOrDefault(AmbientSoundPreset.NONE)
    }

    val ambientVolume: Flow<Float> = dataStore.data.map { prefs ->
        (prefs[Keys.AMBIENT_VOLUME] ?: 0.55f).coerceIn(0f, 1f)
    }

    val ambientPlayWithSession: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.AMBIENT_PLAY_WITH_SESSION] ?: true
    }

    /**
     * The focus type preselected on the Focus screen when no timer is running.
     * Break types are never valid defaults because a break cannot plant a tree.
     */
    val defaultSessionType: Flow<SessionType> = dataStore.data.map { prefs ->
        val raw = prefs[Keys.DEFAULT_SESSION_TYPE] ?: SessionType.POMODORO.name
        runCatching { SessionType.valueOf(raw) }
            .getOrDefault(SessionType.POMODORO)
            .takeUnless { it.isBreak }
            ?: SessionType.POMODORO
    }

    val defaultSessionMinutes: Flow<Int> = dataStore.data.map { prefs ->
        (prefs[Keys.DEFAULT_SESSION_MINUTES] ?: SessionType.POMODORO.defaultMinutes)
            .coerceIn(5, 240)
    }

    val notificationPreferences: Flow<NotificationPreferences> = dataStore.data.map { prefs ->
        NotificationPreferences(
            notificationsEnabled = prefs[Keys.NOTIF_ENABLED] ?: false,
            sessionCompletionEnabled = prefs[Keys.NOTIF_SESSION_COMPLETE] ?: true,
            dailyReminderEnabled = prefs[Keys.NOTIF_DAILY_REMINDER] ?: false,
            dailyReminderHour = prefs[Keys.NOTIF_DAILY_HOUR] ?: 9,
            dailyReminderMinute = prefs[Keys.NOTIF_DAILY_MINUTE] ?: 0,
            incompleteGoalReminderEnabled = prefs[Keys.NOTIF_INCOMPLETE_GOAL] ?: false,
            weeklySummaryEnabled = prefs[Keys.NOTIF_WEEKLY_SUMMARY] ?: false,
            quietHoursEnabled = prefs[Keys.QUIET_HOURS_ENABLED] ?: true,
            quietHoursStartHour = prefs[Keys.QUIET_START_HOUR] ?: 22,
            quietHoursEndHour = prefs[Keys.QUIET_END_HOUR] ?: 7
        )
    }

    val activeTimerState: Flow<ActiveTimerState?> = dataStore.data.map { prefs ->
        val sessionId = prefs[Keys.TIMER_SESSION_ID] ?: return@map null
        val typeRaw = prefs[Keys.TIMER_SESSION_TYPE] ?: return@map null
        val statusRaw = prefs[Keys.TIMER_STATUS] ?: return@map null
        val sessionType = runCatching { SessionType.valueOf(typeRaw) }.getOrNull() ?: return@map null
        val status = runCatching { SessionStatus.valueOf(statusRaw) }.getOrNull() ?: return@map null
        ActiveTimerState(
            sessionId = sessionId,
            sessionType = sessionType,
            plannedDurationMs = prefs[Keys.TIMER_PLANNED_MS] ?: 0L,
            remainingMs = prefs[Keys.TIMER_REMAINING_MS] ?: 0L,
            status = status,
            startedAtWallMs = prefs[Keys.TIMER_STARTED_WALL_MS] ?: 0L,
            endElapsedRealtimeMs = prefs[Keys.TIMER_END_ELAPSED_MS] ?: 0L,
            expectedEndWallClockMs = prefs[Keys.TIMER_EXPECTED_END_WALL_MS] ?: 0L,
            bootCount = prefs[Keys.TIMER_BOOT_COUNT] ?: 0,
            intentionLabel = prefs[Keys.TIMER_INTENTION_LABEL] ?: "",
            chainId = prefs[Keys.TIMER_CHAIN_ID],
            routineId = prefs[Keys.TIMER_ROUTINE_ID],
            routineNameSnapshot = prefs[Keys.TIMER_ROUTINE_NAME] ?: "",
            routineSnapshot = prefs[Keys.TIMER_ROUTINE_SNAPSHOT] ?: "",
            categoryId = prefs[Keys.TIMER_CATEGORY_ID],
            categoryNameSnapshot = prefs[Keys.TIMER_CATEGORY_NAME] ?: ""
        )
    }

    val activeChainState: Flow<ActiveChainState?> = dataStore.data.map { prefs ->
        val chainId = prefs[Keys.CHAIN_ID] ?: return@map null
        val stepRaw = prefs[Keys.CHAIN_CURRENT_STEP_TYPE] ?: SessionType.POMODORO.name
        val stepType = runCatching { SessionType.valueOf(stepRaw) }.getOrDefault(SessionType.POMODORO)
        ActiveChainState(
            chainId = chainId,
            config = ChainConfig(
                totalFocusSessions = prefs[Keys.CHAIN_TOTAL_FOCUS] ?: 4,
                focusMinutes = prefs[Keys.CHAIN_FOCUS_MIN] ?: 25,
                shortBreakMinutes = prefs[Keys.CHAIN_SHORT_BREAK_MIN] ?: 5,
                longBreakMinutes = prefs[Keys.CHAIN_LONG_BREAK_MIN] ?: 15,
                longBreakFrequency = prefs[Keys.CHAIN_LONG_BREAK_FREQ] ?: 4,
                autoTransition = prefs[Keys.CHAIN_AUTO_TRANSITION] ?: false
            ).validated(),
            completedFocusCount = prefs[Keys.CHAIN_COMPLETED_FOCUS] ?: 0,
            completedBreakCount = prefs[Keys.CHAIN_COMPLETED_BREAKS] ?: 0,
            totalFocusedMinutes = prefs[Keys.CHAIN_TOTAL_FOCUSED_MIN] ?: 0,
            totalEarnedXp = prefs[Keys.CHAIN_EARNED_XP] ?: 0,
            totalEarnedSeeds = prefs[Keys.CHAIN_EARNED_SEEDS] ?: 0,
            currentStepType = stepType,
            waitingForUserNextStep = prefs[Keys.CHAIN_WAITING_NEXT] ?: false,
            isCompleted = prefs[Keys.CHAIN_IS_COMPLETED] ?: false,
            intentionLabel = prefs[Keys.CHAIN_INTENTION_LABEL] ?: "",
            pendingStepSessionId = prefs[Keys.CHAIN_PENDING_STEP_SESSION_ID],
            lastAdvancedSessionId = prefs[Keys.CHAIN_LAST_ADVANCED_SESSION_ID],
            routineId = prefs[Keys.CHAIN_ROUTINE_ID],
            routineNameSnapshot = prefs[Keys.CHAIN_ROUTINE_NAME] ?: "",
            routineSnapshot = prefs[Keys.CHAIN_ROUTINE_SNAPSHOT] ?: "",
            categoryId = prefs[Keys.CHAIN_CATEGORY_ID],
            categoryNameSnapshot = prefs[Keys.CHAIN_CATEGORY_NAME] ?: ""
        )
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { it[Keys.ONBOARDING_COMPLETED] = completed }
    }

    suspend fun setThemePreference(preference: ThemePreference) {
        dataStore.edit { it[Keys.THEME_PREFERENCE] = preference.name }
    }

    suspend fun setDailyGoalMinutes(minutes: Int) {
        dataStore.edit { it[Keys.DAILY_GOAL_MINUTES] = minutes.coerceIn(10, 480) }
    }

    suspend fun setWeeklyGoalMinutes(minutes: Int) {
        dataStore.edit { it[Keys.WEEKLY_GOAL_MINUTES] = minutes.coerceIn(0, MAX_WEEKLY_GOAL_MINUTES) }
    }

    /**
     * Persists the legacy effective weekly intention once. Subsequent daily-goal edits do not
     * silently rewrite an already-independent weekly goal.
     */
    suspend fun ensureWeeklyGoalInitialized() {
        dataStore.edit { prefs ->
            if (prefs[Keys.WEEKLY_GOAL_MINUTES] == null) {
                val daily = prefs[Keys.DAILY_GOAL_MINUTES] ?: 50
                prefs[Keys.WEEKLY_GOAL_MINUTES] = (daily * 7).coerceIn(0, MAX_WEEKLY_GOAL_MINUTES)
            }
        }
    }

    suspend fun setUsageTrackingEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.USAGE_TRACKING_ENABLED] = enabled }
    }

    suspend fun setUsageDisclosureAccepted(accepted: Boolean) {
        dataStore.edit { it[Keys.USAGE_DISCLOSURE_ACCEPTED] = accepted }
    }

    suspend fun setMonitoredPackages(packages: Set<String>) {
        dataStore.edit { it[Keys.MONITORED_PACKAGES] = packages }
    }

    suspend fun setDailyUsageBudgetMinutes(minutes: Int) {
        dataStore.edit { it[Keys.DAILY_USAGE_BUDGET_MINUTES] = minutes.coerceIn(15, 720) }
    }

    suspend fun setAmbientPreferences(preset: AmbientSoundPreset, volume: Float, playWithSession: Boolean) {
        dataStore.edit {
            it[Keys.AMBIENT_PRESET] = preset.name
            it[Keys.AMBIENT_VOLUME] = volume.coerceIn(0f, 1f)
            it[Keys.AMBIENT_PLAY_WITH_SESSION] = playWithSession
        }
    }

    /** Updates one ambient key without overwriting the other saved ambient choices. */
    suspend fun setAmbientPreset(preset: AmbientSoundPreset) {
        dataStore.edit { it[Keys.AMBIENT_PRESET] = preset.name }
    }

    suspend fun setAmbientVolume(volume: Float) {
        dataStore.edit { it[Keys.AMBIENT_VOLUME] = volume.coerceIn(0f, 1f) }
    }

    suspend fun setAmbientPlayWithSession(enabled: Boolean) {
        dataStore.edit { it[Keys.AMBIENT_PLAY_WITH_SESSION] = enabled }
    }

    /**
     * Persists the focus type and length used as the starting selection on Focus.
     * Only a single-session default is stored; routines and chains keep their own settings.
     */
    suspend fun setDefaultSession(type: SessionType, minutes: Int) {
        val safeType = type.takeUnless { it.isBreak } ?: SessionType.POMODORO
        dataStore.edit {
            it[Keys.DEFAULT_SESSION_TYPE] = safeType.name
            it[Keys.DEFAULT_SESSION_MINUTES] = minutes.coerceIn(5, 240)
        }
    }

    suspend fun updateNotificationPreferences(prefs: NotificationPreferences) {
        dataStore.edit {
            it[Keys.NOTIF_ENABLED] = prefs.notificationsEnabled
            it[Keys.NOTIF_SESSION_COMPLETE] = prefs.sessionCompletionEnabled
            it[Keys.NOTIF_DAILY_REMINDER] = prefs.dailyReminderEnabled
            it[Keys.NOTIF_DAILY_HOUR] = prefs.dailyReminderHour
            it[Keys.NOTIF_DAILY_MINUTE] = prefs.dailyReminderMinute
            it[Keys.NOTIF_INCOMPLETE_GOAL] = prefs.incompleteGoalReminderEnabled
            it[Keys.NOTIF_WEEKLY_SUMMARY] = prefs.weeklySummaryEnabled
            it[Keys.QUIET_HOURS_ENABLED] = prefs.quietHoursEnabled
            it[Keys.QUIET_START_HOUR] = prefs.quietHoursStartHour
            it[Keys.QUIET_END_HOUR] = prefs.quietHoursEndHour
        }
    }

    suspend fun saveActiveTimerState(state: ActiveTimerState?) {
        dataStore.edit { prefs -> writeTimer(prefs, state) }
    }

    suspend fun saveActiveChainState(state: ActiveChainState?) {
        dataStore.edit { prefs -> writeChain(prefs, state) }
    }

    /**
     * Writes the chain and its next timer in one DataStore transaction so a crash can never
     * persist an advanced chain without its timer (or vice versa).
     */
    suspend fun saveChainAndTimer(chain: ActiveChainState?, timer: ActiveTimerState?) {
        dataStore.edit { prefs ->
            writeChain(prefs, chain)
            writeTimer(prefs, timer)
        }
    }

    /**
     * Clears the active timer only when it still belongs to [sessionId]. Completion and abort
     * paths must use this so a late duplicate worker cannot delete a newer chain step.
     */
    suspend fun clearActiveTimerIfSession(sessionId: String) {
        dataStore.edit { prefs ->
            if (prefs[Keys.TIMER_SESSION_ID] == sessionId) {
                writeTimer(prefs, null)
            }
        }
    }

    private fun writeTimer(prefs: MutablePreferences, state: ActiveTimerState?) {
        if (state == null) {
            prefs.remove(Keys.TIMER_SESSION_ID)
            prefs.remove(Keys.TIMER_SESSION_TYPE)
            prefs.remove(Keys.TIMER_PLANNED_MS)
            prefs.remove(Keys.TIMER_REMAINING_MS)
            prefs.remove(Keys.TIMER_STATUS)
            prefs.remove(Keys.TIMER_STARTED_WALL_MS)
            prefs.remove(Keys.TIMER_END_ELAPSED_MS)
            prefs.remove(Keys.TIMER_EXPECTED_END_WALL_MS)
            prefs.remove(Keys.TIMER_BOOT_COUNT)
            prefs.remove(Keys.TIMER_INTENTION_LABEL)
            prefs.remove(Keys.TIMER_CHAIN_ID)
            prefs.remove(Keys.TIMER_ROUTINE_ID)
            prefs.remove(Keys.TIMER_ROUTINE_NAME)
            prefs.remove(Keys.TIMER_ROUTINE_SNAPSHOT)
            prefs.remove(Keys.TIMER_CATEGORY_ID)
            prefs.remove(Keys.TIMER_CATEGORY_NAME)
        } else {
            prefs[Keys.TIMER_SESSION_ID] = state.sessionId
            prefs[Keys.TIMER_SESSION_TYPE] = state.sessionType.name
            prefs[Keys.TIMER_PLANNED_MS] = state.plannedDurationMs
            prefs[Keys.TIMER_REMAINING_MS] = state.remainingMs
            prefs[Keys.TIMER_STATUS] = state.status.name
            prefs[Keys.TIMER_STARTED_WALL_MS] = state.startedAtWallMs
            prefs[Keys.TIMER_END_ELAPSED_MS] = state.endElapsedRealtimeMs
            prefs[Keys.TIMER_EXPECTED_END_WALL_MS] = state.expectedEndWallClockMs
            prefs[Keys.TIMER_BOOT_COUNT] = state.bootCount
            prefs[Keys.TIMER_INTENTION_LABEL] = state.intentionLabel
            if (state.chainId != null) {
                prefs[Keys.TIMER_CHAIN_ID] = state.chainId
            } else {
                prefs.remove(Keys.TIMER_CHAIN_ID)
            }
            if (state.routineId != null) {
                prefs[Keys.TIMER_ROUTINE_ID] = state.routineId
            } else {
                prefs.remove(Keys.TIMER_ROUTINE_ID)
            }
            prefs[Keys.TIMER_ROUTINE_NAME] = state.routineNameSnapshot
            prefs[Keys.TIMER_ROUTINE_SNAPSHOT] = state.routineSnapshot
            if (state.categoryId != null) {
                prefs[Keys.TIMER_CATEGORY_ID] = state.categoryId
            } else {
                prefs.remove(Keys.TIMER_CATEGORY_ID)
            }
            prefs[Keys.TIMER_CATEGORY_NAME] = state.categoryNameSnapshot
        }
    }

    private fun writeChain(prefs: MutablePreferences, state: ActiveChainState?) {
        if (state == null) {
            prefs.remove(Keys.CHAIN_ID)
            prefs.remove(Keys.CHAIN_TOTAL_FOCUS)
            prefs.remove(Keys.CHAIN_FOCUS_MIN)
            prefs.remove(Keys.CHAIN_SHORT_BREAK_MIN)
            prefs.remove(Keys.CHAIN_LONG_BREAK_MIN)
            prefs.remove(Keys.CHAIN_LONG_BREAK_FREQ)
            prefs.remove(Keys.CHAIN_AUTO_TRANSITION)
            prefs.remove(Keys.CHAIN_COMPLETED_FOCUS)
            prefs.remove(Keys.CHAIN_COMPLETED_BREAKS)
            prefs.remove(Keys.CHAIN_TOTAL_FOCUSED_MIN)
            prefs.remove(Keys.CHAIN_EARNED_XP)
            prefs.remove(Keys.CHAIN_EARNED_SEEDS)
            prefs.remove(Keys.CHAIN_CURRENT_STEP_TYPE)
            prefs.remove(Keys.CHAIN_WAITING_NEXT)
            prefs.remove(Keys.CHAIN_IS_COMPLETED)
            prefs.remove(Keys.CHAIN_INTENTION_LABEL)
            prefs.remove(Keys.CHAIN_PENDING_STEP_SESSION_ID)
            prefs.remove(Keys.CHAIN_LAST_ADVANCED_SESSION_ID)
            prefs.remove(Keys.CHAIN_ROUTINE_ID)
            prefs.remove(Keys.CHAIN_ROUTINE_NAME)
            prefs.remove(Keys.CHAIN_ROUTINE_SNAPSHOT)
            prefs.remove(Keys.CHAIN_CATEGORY_ID)
            prefs.remove(Keys.CHAIN_CATEGORY_NAME)
        } else {
            val cfg = state.config.validated()
            prefs[Keys.CHAIN_ID] = state.chainId
            prefs[Keys.CHAIN_TOTAL_FOCUS] = cfg.totalFocusSessions
            prefs[Keys.CHAIN_FOCUS_MIN] = cfg.focusMinutes
            prefs[Keys.CHAIN_SHORT_BREAK_MIN] = cfg.shortBreakMinutes
            prefs[Keys.CHAIN_LONG_BREAK_MIN] = cfg.longBreakMinutes
            prefs[Keys.CHAIN_LONG_BREAK_FREQ] = cfg.longBreakFrequency
            prefs[Keys.CHAIN_AUTO_TRANSITION] = cfg.autoTransition
            prefs[Keys.CHAIN_COMPLETED_FOCUS] = state.completedFocusCount
            prefs[Keys.CHAIN_COMPLETED_BREAKS] = state.completedBreakCount
            prefs[Keys.CHAIN_TOTAL_FOCUSED_MIN] = state.totalFocusedMinutes
            prefs[Keys.CHAIN_EARNED_XP] = state.totalEarnedXp
            prefs[Keys.CHAIN_EARNED_SEEDS] = state.totalEarnedSeeds
            prefs[Keys.CHAIN_CURRENT_STEP_TYPE] = state.currentStepType.name
            prefs[Keys.CHAIN_WAITING_NEXT] = state.waitingForUserNextStep
            prefs[Keys.CHAIN_IS_COMPLETED] = state.isCompleted
            prefs[Keys.CHAIN_INTENTION_LABEL] = state.intentionLabel
            if (state.pendingStepSessionId != null) {
                prefs[Keys.CHAIN_PENDING_STEP_SESSION_ID] = state.pendingStepSessionId
            } else {
                prefs.remove(Keys.CHAIN_PENDING_STEP_SESSION_ID)
            }
            if (state.lastAdvancedSessionId != null) {
                prefs[Keys.CHAIN_LAST_ADVANCED_SESSION_ID] = state.lastAdvancedSessionId
            } else {
                prefs.remove(Keys.CHAIN_LAST_ADVANCED_SESSION_ID)
            }
            if (state.routineId != null) {
                prefs[Keys.CHAIN_ROUTINE_ID] = state.routineId
            } else {
                prefs.remove(Keys.CHAIN_ROUTINE_ID)
            }
            prefs[Keys.CHAIN_ROUTINE_NAME] = state.routineNameSnapshot
            prefs[Keys.CHAIN_ROUTINE_SNAPSHOT] = state.routineSnapshot
            if (state.categoryId != null) {
                prefs[Keys.CHAIN_CATEGORY_ID] = state.categoryId
            } else {
                prefs.remove(Keys.CHAIN_CATEGORY_ID)
            }
            prefs[Keys.CHAIN_CATEGORY_NAME] = state.categoryNameSnapshot
        }
    }

    suspend fun clearAll() {
        dataStore.edit { it.clear() }
    }
}
