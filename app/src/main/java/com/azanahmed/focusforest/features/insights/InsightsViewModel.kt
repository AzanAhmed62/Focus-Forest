package com.azanahmed.focusforest.features.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.azanahmed.focusforest.core.di.AppContainer
import com.azanahmed.focusforest.domain.engine.AdvancedFocusInsights
import com.azanahmed.focusforest.domain.engine.CalendarDayCell
import com.azanahmed.focusforest.domain.engine.InsightsAnalyticsEngine
import com.azanahmed.focusforest.domain.model.DailyStats
import com.azanahmed.focusforest.domain.model.FocusSession
import com.azanahmed.focusforest.domain.model.PersonalRecords
import com.azanahmed.focusforest.domain.model.UsageDashboardState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

enum class InsightsDateFilter(val label: String, val days: Long?) {
    SEVEN_DAYS("7 Days", 7L),
    THIRTY_DAYS("30 Days", 30L),
    NINETY_DAYS("90 Days", 90L),
    ALL_TIME("All Time", null)
}

data class InsightsUiState(
    val selectedTab: Int = 0,
    val dateFilter: InsightsDateFilter = InsightsDateFilter.SEVEN_DAYS,
    val dailyGoalMinutes: Int = 50,
    val filteredSessions: List<FocusSession> = emptyList(),
    val trendFocusDays: List<DailyStats> = emptyList(),
    val records: PersonalRecords = PersonalRecords(),
    val advancedInsights: AdvancedFocusInsights = AdvancedFocusInsights(),
    val displayedMonth: YearMonth = YearMonth.now(),
    val calendarCells: List<CalendarDayCell> = emptyList(),
    val selectedCalendarDate: LocalDate? = null,
    val selectedDayStats: DailyStats? = null,
    val selectedDaySessions: List<FocusSession> = emptyList(),
    val usageState: UsageDashboardState = UsageDashboardState(),
    val showDisclosureDialog: Boolean = false,
    val showAppSelectorDialog: Boolean = false,
    val showDeleteUsageDialog: Boolean = false
)

class InsightsViewModel(
    private val container: AppContainer
) : ViewModel() {

    private val localControls = MutableStateFlow(
        InsightsUiState(
            displayedMonth = YearMonth.from(container.clockProvider.todayLocalDate()),
            selectedCalendarDate = container.clockProvider.todayLocalDate()
        )
    )

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
        val zoneId = container.clockProvider.zoneId()
        val cutoff = controls.dateFilter.days?.let { today.minusDays(it - 1) }

        val filteredSessions = if (cutoff == null) {
            sessions
        } else {
            sessions.filter { s ->
                val d = runCatching { LocalDate.parse(s.localDate) }.getOrNull()
                d != null && !d.isBefore(cutoff)
            }
        }

        val windowDays = controls.dateFilter.days ?: 30L
        val trendDays = InsightsAnalyticsEngine.buildTrendWindow(
            dailyStats = dailyStats,
            today = today,
            daysCount = windowDays,
            defaultGoalMinutes = goalMinutes
        )

        val advancedInsights = InsightsAnalyticsEngine.computeAdvancedInsights(
            sessions = sessions,
            dailyStats = dailyStats,
            today = today,
            zoneId = zoneId
        )

        val calendarCells = InsightsAnalyticsEngine.buildCalendarMonth(
            month = controls.displayedMonth,
            dailyStats = dailyStats,
            today = today,
            defaultGoalMinutes = goalMinutes
        )

        val selDateStr = controls.selectedCalendarDate?.toString()
        val selectedDayStats = selDateStr?.let { ds ->
            dailyStats.find { it.localDate == ds }
                ?: DailyStats(localDate = ds, goalMinutes = goalMinutes)
        }
        val selectedDaySessions = if (selDateStr == null) {
            emptyList()
        } else {
            sessions.filter { it.localDate == selDateStr }
        }

        controls.copy(
            dailyGoalMinutes = goalMinutes,
            filteredSessions = filteredSessions,
            trendFocusDays = trendDays,
            records = records,
            advancedInsights = advancedInsights,
            calendarCells = calendarCells,
            selectedDayStats = selectedDayStats,
            selectedDaySessions = selectedDaySessions,
            usageState = usage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = InsightsUiState(
            displayedMonth = YearMonth.from(container.clockProvider.todayLocalDate()),
            selectedCalendarDate = container.clockProvider.todayLocalDate()
        )
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

    fun previousMonth() {
        localControls.update { state ->
            val prev = state.displayedMonth.minusMonths(1)
            state.copy(displayedMonth = prev)
        }
    }

    fun nextMonth() {
        val currentMonth = YearMonth.from(container.clockProvider.todayLocalDate())
        localControls.update { state ->
            val next = state.displayedMonth.plusMonths(1)
            if (next.isAfter(currentMonth)) state else state.copy(displayedMonth = next)
        }
    }

    fun selectCalendarDate(date: LocalDate) {
        localControls.update { it.copy(selectedCalendarDate = date) }
    }

    fun refreshUsage(force: Boolean = false) {
        viewModelScope.launch {
            val usage = container.usageRepository.refreshUsageData(forceRefresh = force)
            container.challengeRepository.refreshProgress(usage.baselineStatus)
        }
    }

    fun updateDailyUsageBudget(minutes: Int) {
        viewModelScope.launch {
            container.usageRepository.setDailyUsageBudget(minutes)
            val baseline = container.usageRepository.dashboardState.value.baselineStatus
            container.challengeRepository.refreshProgress(baseline)
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
            val baseline = container.usageRepository.dashboardState.value.baselineStatus
            container.challengeRepository.refreshProgress(baseline)
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
