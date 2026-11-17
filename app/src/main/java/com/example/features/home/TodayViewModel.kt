package com.example.features.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.di.AppContainer
import com.example.domain.engine.XpLevelEngine
import com.example.domain.model.ActiveChainState
import com.example.domain.model.ChallengeItem
import com.example.domain.model.ChallengePeriod
import com.example.domain.model.ChallengeStatus
import com.example.domain.model.DailyStats
import com.example.domain.model.ForestState
import com.example.domain.model.PersonalRecords
import com.example.domain.model.UsageDashboardState
import com.example.domain.model.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class TodayUiState(
    val isLoading: Boolean = true,
    val greeting: String = "Welcome back",
    val formattedDate: String = "",
    val profile: UserProfile? = null,
    val todayStats: DailyStats = DailyStats(localDate = ""),
    val weeklyStats: List<DailyStats> = emptyList(),
    val records: PersonalRecords = PersonalRecords(),
    val highlightChallenge: ChallengeItem? = null,
    val forestState: ForestState = ForestState(),
    val usageState: UsageDashboardState = UsageDashboardState(),
    val activeChain: ActiveChainState? = null,
    val levelProgressFraction: Float = 0f,
    val isFirstTimeEmpty: Boolean = false
)

class TodayViewModel(
    private val container: AppContainer
) : ViewModel() {

    private val showDisclosure = MutableStateFlow(false)
    val showUsageDisclosureDialog: StateFlow<Boolean> = showDisclosure

    val uiState: StateFlow<TodayUiState> = combine(
        container.focusRepository.userProfile,
        container.focusRepository.allDailyStats,
        container.focusRepository.personalRecords,
        container.challengeRepository.challenges,
        container.shopAndForestRepository.forestState,
        container.usageRepository.dashboardState,
        container.preferencesDataStore.dailyGoalMinutes,
        container.focusRepository.activeChainState
    ) { values ->
        val profile = values[0] as UserProfile?
        @Suppress("UNCHECKED_CAST")
        val allStats = values[1] as List<DailyStats>
        val records = values[2] as PersonalRecords
        @Suppress("UNCHECKED_CAST")
        val challenges = values[3] as List<ChallengeItem>
        val forest = values[4] as ForestState
        val usage = values[5] as UsageDashboardState
        val goalMinutes = values[6] as Int
        val activeChain = values[7] as ActiveChainState?

        val todayDate = container.clockProvider.todayLocalDate()
        val todayStr = todayDate.toString()
        val statsMap = allStats.associateBy { it.localDate }
        val todayStats = statsMap[todayStr] ?: DailyStats(localDate = todayStr, goalMinutes = goalMinutes)

        val last7Days = (6L downTo 0L).map { offset ->
            val d = todayDate.minusDays(offset)
            statsMap[d.toString()] ?: DailyStats(localDate = d.toString(), goalMinutes = goalMinutes)
        }

        val todayChallenges = challenges.filter { it.period == ChallengePeriod.DAILY && it.periodKey == todayStr }
        val highlight = todayChallenges.firstOrNull { it.status == ChallengeStatus.ACTIVE }
            ?: todayChallenges.firstOrNull()

        val hour = LocalTime.now(container.clockProvider.zoneId()).hour
        val baseGreeting = when {
            hour < 12 -> "Good morning"
            hour < 17 -> "Good afternoon"
            else -> "Good evening"
        }
        val namePart = profile?.displayName?.takeIf { it.isNotBlank() }?.let { ", $it" } ?: ""
        val formattedDate = todayDate.format(DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault()))
        val levelInfo = XpLevelEngine.levelProgress(profile?.lifetimeXp ?: 0)

        TodayUiState(
            isLoading = false,
            greeting = "$baseGreeting$namePart",
            formattedDate = formattedDate,
            profile = profile,
            todayStats = todayStats.copy(goalMinutes = goalMinutes),
            weeklyStats = last7Days,
            records = records,
            highlightChallenge = highlight,
            forestState = forest,
            usageState = usage,
            activeChain = activeChain,
            levelProgressFraction = levelInfo.progressFraction,
            isFirstTimeEmpty = records.totalCompletedSessions == 0 && todayStats.focusMinutes == 0
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TodayUiState(isLoading = true)
    )

    init {
        viewModelScope.launch {
            container.shopAndForestRepository.ensureCatalogSeeded()
            val usage = container.usageRepository.refreshUsageData()
            container.challengeRepository.ensureChallengesGenerated(usage.baselineStatus.isBaselineReady)
            container.challengeRepository.refreshProgress(usage.baselineStatus)
        }
    }

    fun onResumeRefresh() {
        viewModelScope.launch {
            val usage = container.usageRepository.refreshUsageData()
            container.challengeRepository.ensureChallengesGenerated(usage.baselineStatus.isBaselineReady)
            container.challengeRepository.refreshProgress(usage.baselineStatus)
        }
    }

    fun markRestDayToday() {
        viewModelScope.launch {
            container.focusRepository.markRestDay()
        }
    }

    fun openUsageDisclosure() {
        showDisclosure.value = true
    }

    fun dismissUsageDisclosure() {
        showDisclosure.value = false
    }

    fun acceptUsageDisclosureAndLaunchSettings(launchSettings: () -> Unit) {
        viewModelScope.launch {
            container.usageRepository.acceptDisclosure()
            showDisclosure.value = false
            launchSettings()
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return TodayViewModel(container) as T
        }
    }
}
