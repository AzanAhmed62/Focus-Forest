package com.azanahmed.focusforest.features.onboarding

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.azanahmed.focusforest.core.di.AppContainer
import com.azanahmed.focusforest.domain.model.SessionType
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
    val defaultSessionType: SessionType = SessionType.POMODORO,
    val defaultSessionMinutes: Int = SessionType.POMODORO.defaultMinutes,
    val isSaving: Boolean = false
) {
    val plannedDefaultMinutes: Int
        get() = if (defaultSessionType == SessionType.CUSTOM) {
            defaultSessionMinutes
        } else {
            defaultSessionType.defaultMinutes
        }
}

class OnboardingViewModel(
    private val container: AppContainer,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle()
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        OnboardingUiState(
            currentPage = savedStateHandle[KEY_PAGE] ?: 0,
            displayName = savedStateHandle[KEY_NAME] ?: "",
            dailyGoalMinutes = savedStateHandle[KEY_DAILY_GOAL] ?: 50,
            selectedIntention = savedStateHandle.get<String>(KEY_INTENTION)
                ?.let { raw -> runCatching { UserIntention.valueOf(raw) }.getOrNull() }
                ?: UserIntention.CONCENTRATION,
            defaultSessionType = savedStateHandle.get<String>(KEY_SESSION_TYPE)
                ?.let { raw -> runCatching { SessionType.valueOf(raw) }.getOrNull() }
                ?.takeUnless { it.isBreak }
                ?: SessionType.POMODORO,
            defaultSessionMinutes = savedStateHandle[KEY_SESSION_MINUTES] ?: 25
        )
    )
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    private fun updateState(transform: (OnboardingUiState) -> OnboardingUiState) {
        _uiState.update { current ->
            val next = transform(current)
            savedStateHandle[KEY_PAGE] = next.currentPage
            savedStateHandle[KEY_NAME] = next.displayName
            savedStateHandle[KEY_DAILY_GOAL] = next.dailyGoalMinutes
            savedStateHandle[KEY_INTENTION] = next.selectedIntention.name
            savedStateHandle[KEY_SESSION_TYPE] = next.defaultSessionType.name
            savedStateHandle[KEY_SESSION_MINUTES] = next.defaultSessionMinutes
            next
        }
    }

    fun nextPage() {
        updateState { it.copy(currentPage = (it.currentPage + 1).coerceAtMost(LAST_PAGE)) }
    }

    fun previousPage() {
        updateState { it.copy(currentPage = (it.currentPage - 1).coerceAtLeast(0)) }
    }

    fun updateDisplayName(name: String) {
        updateState { it.copy(displayName = name.take(40)) }
    }

    fun updateDailyGoal(minutes: Int) {
        updateState { it.copy(dailyGoalMinutes = minutes.coerceIn(15, 240)) }
    }

    fun selectIntention(intention: UserIntention) {
        updateState { it.copy(selectedIntention = intention) }
    }

    fun selectDefaultSession(type: SessionType) {
        if (type.isBreak) return
        updateState {
            it.copy(
                defaultSessionType = type,
                defaultSessionMinutes = if (type == SessionType.CUSTOM) {
                    it.defaultSessionMinutes.coerceIn(5, 240)
                } else {
                    type.defaultMinutes
                }
            )
        }
    }

    fun updateDefaultSessionMinutes(minutes: Int) {
        updateState {
            it.copy(
                defaultSessionType = SessionType.CUSTOM,
                defaultSessionMinutes = minutes.coerceIn(5, 240)
            )
        }
    }

    /**
     * Saves the minimum useful setup and enters the app. Skipping never asks for a permission
     * and never blocks the first session.
     */
    fun skipOnboarding(onFinished: () -> Unit) {
        completeOnboarding(onFinished)
    }

    fun finishOnboarding(onFinished: () -> Unit) {
        completeOnboarding(onFinished)
    }

    private fun completeOnboarding(onFinished: () -> Unit) {
        if (_uiState.value.isSaving) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val current = _uiState.value
            container.shopAndForestRepository.ensureCatalogSeeded()
            // Seed and initialize before onboarding is marked complete. Writing the completion
            // flag first lets navigation dispose this ViewModel and cancel viewModelScope before
            // the starter templates/weekly goal are persisted.
            container.focusContextRepository.seedDefaultsIfEmpty()
            container.preferencesDataStore.setDailyGoalMinutes(current.dailyGoalMinutes)
            container.preferencesDataStore.ensureWeeklyGoalInitialized()
            container.preferencesDataStore.setDefaultSession(
                type = current.defaultSessionType,
                minutes = current.plannedDefaultMinutes
            )
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
        override fun <T : ViewModel> create(
            modelClass: Class<T>,
            extras: CreationExtras
        ): T {
            return OnboardingViewModel(container, extras.createSavedStateHandle()) as T
        }
    }

    private companion object {
        const val LAST_PAGE = 2
        const val KEY_PAGE = "onboarding_page"
        const val KEY_NAME = "onboarding_name"
        const val KEY_DAILY_GOAL = "onboarding_daily_goal"
        const val KEY_INTENTION = "onboarding_intention"
        const val KEY_SESSION_TYPE = "onboarding_session_type"
        const val KEY_SESSION_MINUTES = "onboarding_session_minutes"
    }
}
