package com.azanahmed.focusforest.features.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.azanahmed.focusforest.core.di.AppContainer
import com.azanahmed.focusforest.domain.model.UsagePermissionStatus
import com.azanahmed.focusforest.domain.model.UserIntention
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnboardingUiState(
    val currentPage: Int = 0,
    val displayName: String = "",
    val dailyGoalMinutes: Int = 50,
    val selectedIntention: UserIntention = UserIntention.CONCENTRATION,
    val usagePermissionStatus: UsagePermissionStatus = UsagePermissionStatus.DENIED,
    val showDisclosureDialog: Boolean = false,
    val isSaving: Boolean = false
)

class OnboardingViewModel(
    private val container: AppContainer
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        refreshUsagePermission()
    }

    fun refreshUsagePermission() {
        val status = container.usageStatsGateway.checkUsagePermission()
        _uiState.update { it.copy(usagePermissionStatus = status) }
    }

    fun setPage(page: Int) {
        _uiState.update { it.copy(currentPage = page.coerceIn(0, 3)) }
    }

    fun nextPage() {
        _uiState.update { it.copy(currentPage = (it.currentPage + 1).coerceAtMost(3)) }
    }

    fun previousPage() {
        _uiState.update { it.copy(currentPage = (it.currentPage - 1).coerceAtLeast(0)) }
    }

    fun updateDisplayName(name: String) {
        _uiState.update { it.copy(displayName = name.take(40)) }
    }

    fun updateDailyGoal(minutes: Int) {
        _uiState.update { it.copy(dailyGoalMinutes = minutes.coerceIn(15, 240)) }
    }

    fun selectIntention(intention: UserIntention) {
        _uiState.update { it.copy(selectedIntention = intention) }
    }

    fun openDisclosureDialog() {
        _uiState.update { it.copy(showDisclosureDialog = true) }
    }

    fun dismissDisclosureDialog() {
        _uiState.update { it.copy(showDisclosureDialog = false) }
    }

    fun confirmUsageDisclosureAndLaunchSettings(launchSettings: () -> Unit) {
        viewModelScope.launch {
            container.usageRepository.acceptDisclosure()
            _uiState.update { it.copy(showDisclosureDialog = false) }
            launchSettings()
        }
    }

    fun finishOnboarding(onFinished: () -> Unit) {
        if (_uiState.value.isSaving) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val current = _uiState.value
            container.shopAndForestRepository.ensureCatalogSeeded()
            container.focusRepository.completeOnboarding(
                displayName = current.displayName,
                dailyGoalMinutes = current.dailyGoalMinutes,
                intention = current.selectedIntention
            )
            val baselineReady = container.usageRepository.dashboardState.value.baselineStatus.isBaselineReady
            container.challengeRepository.ensureChallengesGenerated(baselineReady)
            _uiState.update { it.copy(isSaving = false) }
            onFinished()
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return OnboardingViewModel(container) as T
        }
    }
}
