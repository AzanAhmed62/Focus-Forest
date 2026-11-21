package com.azanahmed.focusforest.features.settings

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.azanahmed.focusforest.core.di.AppContainer
import com.azanahmed.focusforest.domain.model.NotificationPreferences
import com.azanahmed.focusforest.domain.model.ThemePreference
import com.azanahmed.focusforest.domain.model.UsagePermissionStatus
import com.azanahmed.focusforest.notifications.FocusWorkers
import com.azanahmed.focusforest.notifications.NotificationHelper
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val displayName: String = "",
    val dailyGoalMinutes: Int = 50,
    val weeklyGoalMinutes: Int = 350,
    val dailyUsageBudgetMinutes: Int = 120,
    val themePreference: ThemePreference = ThemePreference.SYSTEM,
    val notificationPreferences: NotificationPreferences = NotificationPreferences(),
    val usagePermissionStatus: UsagePermissionStatus = UsagePermissionStatus.DENIED,
    val usageTrackingEnabled: Boolean = false,
    val notificationPermissionGranted: Boolean = false,
    val showDeleteAllDialog: Boolean = false,
    val showUsageDisclosureDialog: Boolean = false
)

class SettingsViewModel(
    private val container: AppContainer,
    private val appContext: Context
) : ViewModel() {

    private val localDialogs = MutableStateFlow(SettingsUiState())
    private val _shareIntentEvents = MutableSharedFlow<Intent>()
    val shareIntentEvents: SharedFlow<Intent> = _shareIntentEvents.asSharedFlow()

    private val _toastEvents = MutableSharedFlow<String>()
    val toastEvents: SharedFlow<String> = _toastEvents.asSharedFlow()

    val uiState: StateFlow<SettingsUiState> = combine(
        container.focusRepository.userProfile,
        container.preferencesDataStore.dailyGoalMinutes,
        container.preferencesDataStore.dailyUsageBudgetMinutes,
        container.preferencesDataStore.themePreference,
        container.preferencesDataStore.notificationPreferences,
        container.preferencesDataStore.usageTrackingEnabled,
        container.preferencesDataStore.weeklyGoalMinutes,
        localDialogs
    ) { values ->
        val profile = values[0] as com.azanahmed.focusforest.domain.model.UserProfile?
        val goal = values[1] as Int
        val usageBudget = values[2] as Int
        val theme = values[3] as ThemePreference
        val notif = values[4] as NotificationPreferences
        val usageEnabled = values[5] as Boolean
        val weeklyGoal = values[6] as Int
        val dialogs = values[7] as SettingsUiState

        dialogs.copy(
            displayName = profile?.displayName ?: "",
            dailyGoalMinutes = goal,
            weeklyGoalMinutes = weeklyGoal,
            dailyUsageBudgetMinutes = usageBudget,
            themePreference = theme,
            notificationPreferences = notif,
            usagePermissionStatus = container.usageStatsGateway.checkUsagePermission(),
            usageTrackingEnabled = usageEnabled,
            notificationPermissionGranted = NotificationHelper.hasNotificationPermission(appContext)
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    fun refreshPermissions() {
        localDialogs.update {
            it.copy(
                usagePermissionStatus = container.usageStatsGateway.checkUsagePermission(),
                notificationPermissionGranted = NotificationHelper.hasNotificationPermission(appContext)
            )
        }
    }

    fun updateProfile(displayName: String, dailyGoalMinutes: Int) {
        viewModelScope.launch {
            container.focusRepository.updateProfileNameAndGoal(displayName, dailyGoalMinutes)
            _toastEvents.emit("Goals saved locally.")
        }
    }

    fun updateDailyUsageBudget(minutes: Int) {
        viewModelScope.launch {
            container.usageRepository.setDailyUsageBudget(minutes)
            _toastEvents.emit("Daily screen-time budget updated.")
        }
    }

    /** Weekly target is independent from the daily goal; 0 means no weekly target. */
    fun updateWeeklyGoal(minutes: Int) {
        viewModelScope.launch {
            container.preferencesDataStore.setWeeklyGoalMinutes(minutes)
            _toastEvents.emit(
                if (minutes <= 0) "Weekly target disabled." else "Weekly focus goal updated."
            )
        }
    }

    fun setThemePreference(preference: ThemePreference) {
        viewModelScope.launch {
            container.preferencesDataStore.setThemePreference(preference)
        }
    }

    fun updateNotificationPrefs(updated: NotificationPreferences) {
        viewModelScope.launch {
            container.preferencesDataStore.updateNotificationPreferences(updated)
            FocusWorkers.syncAllSchedules(appContext, updated)
        }
    }

    fun openUsageDisclosure() {
        localDialogs.update { it.copy(showUsageDisclosureDialog = true) }
    }

    fun dismissUsageDisclosure() {
        localDialogs.update { it.copy(showUsageDisclosureDialog = false) }
    }

    fun confirmUsageDisclosure(launchSettings: () -> Unit) {
        viewModelScope.launch {
            container.usageRepository.acceptDisclosure()
            localDialogs.update { it.copy(showUsageDisclosureDialog = false) }
            launchSettings()
        }
    }

    fun disableAndDeleteUsageRecords() {
        viewModelScope.launch {
            container.usageRepository.disableAndDeleteUsageData()
            _toastEvents.emit("Usage monitoring disabled and records deleted.")
        }
    }

    fun exportFocusSessionsCsv() {
        viewModelScope.launch {
            val intent = container.dataManagementRepository.exportFocusSessionsCsvIntent(appContext)
            _shareIntentEvents.emit(intent)
        }
    }

    fun exportDailyStatsCsv() {
        viewModelScope.launch {
            val intent = container.dataManagementRepository.exportDailyStatsCsvIntent(appContext)
            _shareIntentEvents.emit(intent)
        }
    }

    fun requestDeleteAllData() {
        localDialogs.update { it.copy(showDeleteAllDialog = true) }
    }

    fun dismissDeleteAllDialog() {
        localDialogs.update { it.copy(showDeleteAllDialog = false) }
    }

    fun confirmDeleteAllData(onResetComplete: () -> Unit) {
        viewModelScope.launch {
            container.dataManagementRepository.deleteAllLocalData()
            localDialogs.update { it.copy(showDeleteAllDialog = false) }
            onResetComplete()
        }
    }

    class Factory(
        private val container: AppContainer,
        private val appContext: Context
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(container, appContext) as T
        }
    }
}
