package com.example.features.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.di.AppContainer
import com.example.domain.model.DailyStats
import com.example.domain.model.FocusSession
import com.example.domain.model.PersonalRecords
import com.example.domain.model.UsageDashboardState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class InsightsDateFilter(val label: String, val days: Long?) {
    SEVEN_DAYS("7 Days", 7L),
    THIRTY_DAYS("30 Days", 30L),
    ALL_TIME("All Time", null)
}

data class InsightsUiState(
    val selectedTab: Int = 0,
    val dateFilter: InsightsDateFilter = InsightsDateFilter.SEVEN_DAYS,
    val dailyGoalMinutes: Int = 50,
    val filteredSessions: List<FocusSession> = emptyList(),
    val weeklyFocusDays: List<DailyStats> = emptyList(),
    val records: PersonalRecords = PersonalRecords(),
    val usageState: UsageDashboardState = UsageDashboardState(),
    val showDisclosureDialog: Boolean = false,
    val showAppSelectorDialog: Boolean = false,
    val showDeleteUsageDialog: Boolean = false
)

class InsightsViewModel(
    private val container: AppContainer
) : ViewModel() {

    private val localControls = MutableStateFlow(InsightsUiState())

    val uiState: StateFlow<InsightsUiState> = combine(
        container.focusRepository.allSessions,
        container.focusRepository.allDailyStats,
        container.focusRepository.personalRecords,
        container.usageRepository.dashboardState,
        container.preferencesDataStore.dailyGoalMinutes,
        localControls
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val sessions = values[0] as List<FocusSession>
        @Suppress("UNCHECKED_CAST")
        val dailyStats = values[1] as List<DailyStats>
        val records = values[2] as PersonalRecords
        val usage = values[3] as UsageDashboardState
        val goalMinutes = values[4] as Int
        val controls = values[5] as InsightsUiState

        val today = container.clockProvider.todayLocalDate()
        val cutoff = controls.dateFilter.days?.let { today.minusDays(it - 1) }

        val filteredSessions = if (cutoff == null) {
            sessions
        } else {
            sessions.filter { s ->
                val d = runCatching { LocalDate.parse(s.localDate) }.getOrNull()
                d != null && !d.isBefore(cutoff)
            }
        }

        val statsMap = dailyStats.associateBy { it.localDate }
        val weeklyDays = (6L downTo 0L).map { offset ->
            val d = today.minusDays(offset)
            statsMap[d.toString()] ?: DailyStats(localDate = d.toString(), goalMinutes = goalMinutes)
        }

        controls.copy(
            dailyGoalMinutes = goalMinutes,
            filteredSessions = filteredSessions,
            weeklyFocusDays = weeklyDays,
            records = records,
            usageState = usage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = InsightsUiState()
    )

    init {
        refreshUsage()
    }

    fun selectTab(tabIndex: Int) {
        localControls.update { it.copy(selectedTab = tabIndex.coerceIn(0, 1)) }
    }

    fun selectDateFilter(filter: InsightsDateFilter) {
        localControls.update { it.copy(dateFilter = filter) }
    }

    fun refreshUsage() {
        viewModelScope.launch {
            container.usageRepository.refreshUsageData()
        }
    }

    fun openDisclosureDialog() {
        localControls.update { it.copy(showDisclosureDialog = true) }
    }

    fun dismissDisclosureDialog() {
        localControls.update { it.copy(showDisclosureDialog = false) }
    }

    fun confirmUsageDisclosureAndLaunchSettings(launchSettings: () -> Unit) {
        viewModelScope.launch {
            container.usageRepository.acceptDisclosure()
            localControls.update { it.copy(showDisclosureDialog = false) }
            launchSettings()
        }
    }

    fun openAppSelectorDialog() {
        localControls.update { it.copy(showAppSelectorDialog = true) }
    }

    fun dismissAppSelectorDialog() {
        localControls.update { it.copy(showAppSelectorDialog = false) }
    }

    fun toggleMonitoredApp(packageName: String) {
        viewModelScope.launch {
            container.usageRepository.toggleMonitoredPackage(packageName)
        }
    }

    fun requestDeleteUsageData() {
        localControls.update { it.copy(showDeleteUsageDialog = true) }
    }

    fun dismissDeleteUsageDialog() {
        localControls.update { it.copy(showDeleteUsageDialog = false) }
    }

    fun confirmDeleteUsageData() {
        viewModelScope.launch {
            container.usageRepository.disableAndDeleteUsageData()
            localControls.update { it.copy(showDeleteUsageDialog = false) }
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return InsightsViewModel(container) as T
        }
    }
}
