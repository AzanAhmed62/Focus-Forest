package com.example.features.challenges

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.di.AppContainer
import com.example.domain.engine.ChallengeEngine
import com.example.domain.model.ChallengeItem
import com.example.domain.model.ChallengeMetricType
import com.example.domain.model.ChallengePeriod
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChallengesUiState(
    val dailyChallenges: List<ChallengeItem> = emptyList(),
    val weeklyChallenges: List<ChallengeItem> = emptyList(),
    val customChallenges: List<ChallengeItem> = emptyList(),
    val showCreateDialog: Boolean = false,
    val newTitle: String = "",
    val newDescription: String = "",
    val newMetricType: ChallengeMetricType = ChallengeMetricType.FOCUS_MINUTES,
    val newTargetValue: Int = 60
)

class ChallengesViewModel(
    private val container: AppContainer
) : ViewModel() {

    private val dialogState = MutableStateFlow(ChallengesUiState())

    val uiState: StateFlow<ChallengesUiState> = combine(
        container.challengeRepository.challenges,
        dialogState
    ) { all, local ->
        val todayStr = container.clockProvider.todayLocalDate().toString()
        val weekKey = ChallengeEngine.weekKeyFor(container.clockProvider.todayLocalDate())

        local.copy(
            dailyChallenges = all.filter { it.period == ChallengePeriod.DAILY && it.periodKey == todayStr },
            weeklyChallenges = all.filter { it.period == ChallengePeriod.WEEKLY && it.periodKey == weekKey },
            customChallenges = all.filter { it.period == ChallengePeriod.CUSTOM }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ChallengesUiState()
    )

    init {
        viewModelScope.launch {
            val baseline = container.usageRepository.dashboardState.value.baselineStatus
            container.challengeRepository.ensureChallengesGenerated(baseline.isBaselineReady)
            container.challengeRepository.refreshProgress(baseline)
        }
    }

    fun openCreateDialog() {
        dialogState.update { it.copy(showCreateDialog = true) }
    }

    fun dismissCreateDialog() {
        dialogState.update { it.copy(showCreateDialog = false) }
    }

    fun updateNewTitle(value: String) {
        dialogState.update { it.copy(newTitle = value.take(40)) }
    }

    fun updateNewDescription(value: String) {
        dialogState.update { it.copy(newDescription = value.take(100)) }
    }

    fun selectNewMetricType(type: ChallengeMetricType) {
        val defaultTarget = if (type == ChallengeMetricType.FOCUS_MINUTES) 60 else 3
        dialogState.update { it.copy(newMetricType = type, newTargetValue = defaultTarget) }
    }

    fun updateNewTargetValue(value: Int) {
        dialogState.update { it.copy(newTargetValue = value) }
    }

    fun createPersonalChallenge() {
        val snap = dialogState.value
        viewModelScope.launch {
            container.challengeRepository.createCustomPersonalChallenge(
                title = snap.newTitle,
                description = snap.newDescription,
                metricType = snap.newMetricType,
                targetValue = snap.newTargetValue
            )
            val baseline = container.usageRepository.dashboardState.value.baselineStatus
            container.challengeRepository.refreshProgress(baseline)
            dialogState.update {
                it.copy(
                    showCreateDialog = false,
                    newTitle = "",
                    newDescription = ""
                )
            }
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ChallengesViewModel(container) as T
        }
    }
}
