package com.azanahmed.focusforest.features.challenges

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.azanahmed.focusforest.core.di.AppContainer
import com.azanahmed.focusforest.data.repository.ChallengeMutationResult
import com.azanahmed.focusforest.domain.engine.ChallengeEngine
import com.azanahmed.focusforest.domain.model.ChallengeItem
import com.azanahmed.focusforest.domain.model.ChallengeMetricType
import com.azanahmed.focusforest.domain.model.ChallengePeriod
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChallengesUiState(
    val dailyChallenges: List<ChallengeItem> = emptyList(),
    val weeklyChallenges: List<ChallengeItem> = emptyList(),
    val customChallenges: List<ChallengeItem> = emptyList(),
    val showCreateOrEditDialog: Boolean = false,
    val editingChallengeId: String? = null,
    val newTitle: String = "",
    val newDescription: String = "",
    val newMetricType: ChallengeMetricType = ChallengeMetricType.FOCUS_MINUTES,
    val newTargetValue: Int = 60,
    val dialogValidationError: String? = null,
    val pendingDeleteChallenge: ChallengeItem? = null,
    val actionMessage: String? = null
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
        dialogState.update {
            it.copy(
                showCreateOrEditDialog = true,
                editingChallengeId = null,
                newTitle = "",
                newDescription = "",
                newMetricType = ChallengeMetricType.FOCUS_MINUTES,
                newTargetValue = 60,
                dialogValidationError = null
            )
        }
    }

    fun openEditDialog(challenge: ChallengeItem) {
        if (!challenge.isCustom || challenge.rewardClaimed) return
        dialogState.update {
            it.copy(
                showCreateOrEditDialog = true,
                editingChallengeId = challenge.id,
                newTitle = challenge.title,
                newDescription = challenge.description,
                newMetricType = challenge.metricType,
                newTargetValue = challenge.targetValue,
                dialogValidationError = null
            )
        }
    }

    fun dismissDialog() {
        dialogState.update {
            it.copy(
                showCreateOrEditDialog = false,
                editingChallengeId = null,
                dialogValidationError = null
            )
        }
    }

    fun updateNewTitle(value: String) {
        dialogState.update { it.copy(newTitle = value.take(40), dialogValidationError = null) }
    }

    fun updateNewDescription(value: String) {
        dialogState.update { it.copy(newDescription = value.take(100)) }
    }

    fun selectNewMetricType(type: ChallengeMetricType) {
        if (dialogState.value.editingChallengeId != null) return
        val defaultTarget = when (type) {
            ChallengeMetricType.FOCUS_MINUTES -> 60
            ChallengeMetricType.WEEKLY_FOCUS_GOAL -> 180
            ChallengeMetricType.DAILY_USAGE_BUDGET -> 3
            ChallengeMetricType.USAGE_REDUCTION_PERCENT -> 10
            else -> 3
        }
        dialogState.update {
            it.copy(
                newMetricType = type,
                newTargetValue = defaultTarget,
                dialogValidationError = null
            )
        }
    }

    fun updateNewTargetValue(value: Int) {
        dialogState.update { it.copy(newTargetValue = value, dialogValidationError = null) }
    }

    fun savePersonalChallenge() {
        val snap = dialogState.value
        val editingId = snap.editingChallengeId
        viewModelScope.launch {
            val result = if (editingId == null) {
                container.challengeRepository.createCustomPersonalChallenge(
                    title = snap.newTitle,
                    description = snap.newDescription,
                    metricType = snap.newMetricType,
                    targetValue = snap.newTargetValue
                )
            } else {
                container.challengeRepository.editCustomPersonalChallenge(
                    challengeId = editingId,
                    newTitle = snap.newTitle,
                    newDescription = snap.newDescription,
                    newTargetValue = snap.newTargetValue
                )
            }

            when (result) {
                is ChallengeMutationResult.ValidationError -> {
                    dialogState.update { it.copy(dialogValidationError = result.message) }
                }
                is ChallengeMutationResult.Rejected -> {
                    dialogState.update { it.copy(dialogValidationError = result.message) }
                }
                ChallengeMutationResult.Success -> {
                    val baseline = container.usageRepository.dashboardState.value.baselineStatus
                    container.challengeRepository.refreshProgress(baseline)
                    dialogState.update {
                        it.copy(
                            showCreateOrEditDialog = false,
                            editingChallengeId = null,
                            newTitle = "",
                            newDescription = "",
                            dialogValidationError = null
                        )
                    }
                }
            }
        }
    }

    fun requestDeleteChallenge(challenge: ChallengeItem) {
        dialogState.update { it.copy(pendingDeleteChallenge = challenge) }
    }

    fun dismissDeleteDialog() {
        dialogState.update { it.copy(pendingDeleteChallenge = null) }
    }

    fun confirmDeleteChallenge() {
        val target = dialogState.value.pendingDeleteChallenge ?: return
        viewModelScope.launch {
            val result = container.challengeRepository.deleteOrArchiveCustomChallenge(target.id)
            val msg = when (result) {
                ChallengeMutationResult.Success -> if (target.rewardClaimed) {
                    "Archived completed challenge."
                } else {
                    "Deleted personal challenge."
                }
                is ChallengeMutationResult.Rejected -> result.message
                is ChallengeMutationResult.ValidationError -> result.message
            }
            dialogState.update {
                it.copy(
                    pendingDeleteChallenge = null,
                    actionMessage = msg
                )
            }
        }
    }

    fun clearActionMessage() {
        dialogState.update { it.copy(actionMessage = null) }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ChallengesViewModel(container) as T
        }
    }
}
