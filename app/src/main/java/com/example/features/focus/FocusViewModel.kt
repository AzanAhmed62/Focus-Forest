package com.example.features.focus

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.di.AppContainer
import com.example.data.repository.SessionCompletionOutcome
import com.example.domain.engine.FocusTimerEngine
import com.example.domain.engine.TimerRecoveryResult
import com.example.domain.model.ActiveTimerState
import com.example.domain.model.SessionStatus
import com.example.domain.model.SessionType
import com.example.notifications.SessionCompletionWorker
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FocusUiState(
    val selectedType: SessionType = SessionType.POMODORO,
    val customMinutes: Int = 35,
    val intentionLabel: String = "",
    val activeTimer: ActiveTimerState? = null,
    val showAbortConfirmDialog: Boolean = false,
    val completionOutcome: SessionCompletionOutcome? = null,
    val interruptedAfterRebootNotice: Boolean = false,
    val currentStreakDays: Int = 0,
    val forestLevel: Int = 1
) {
    val plannedMinutesForSelection: Int
        get() = if (selectedType == SessionType.CUSTOM) customMinutes else selectedType.defaultMinutes
}

class FocusViewModel(
    private val container: AppContainer,
    private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(FocusUiState())
    val uiState: StateFlow<FocusUiState> = _uiState.asStateFlow()

    private var tickerJob: Job? = null
    private var completingSessionId: String? = null

    init {
        viewModelScope.launch {
            val saved = container.focusRepository.activeTimerState.first()
            if (saved != null) {
                recoverPersistedTimer(saved)
            }
        }
        viewModelScope.launch {
            container.focusRepository.personalRecords.collect { records ->
                _uiState.update { it.copy(currentStreakDays = records.currentStreakDays) }
            }
        }
        viewModelScope.launch {
            container.shopAndForestRepository.forestState.collect { forest ->
                _uiState.update { it.copy(forestLevel = forest.forestLevel) }
            }
        }
    }

    fun selectSessionType(type: SessionType) {
        if (_uiState.value.activeTimer != null) return
        _uiState.update { it.copy(selectedType = type, completionOutcome = null, interruptedAfterRebootNotice = false) }
    }

    fun updateCustomMinutes(minutes: Int) {
        _uiState.update { it.copy(customMinutes = minutes.coerceIn(5, 180)) }
    }

    fun updateIntentionLabel(label: String) {
        _uiState.update { it.copy(intentionLabel = label.take(60)) }
    }

    fun startSession() {
        if (_uiState.value.activeTimer != null) return
        val current = _uiState.value
        val nowWall = container.clockProvider.nowWallClockMs()
        val nowElapsed = container.clockProvider.elapsedRealtimeMs()
        val bootCount = container.clockProvider.bootCount()
        val plannedMinutes = current.plannedMinutesForSelection

        val started = FocusTimerEngine.startSession(
            sessionType = current.selectedType,
            plannedMinutes = plannedMinutes,
            intentionLabel = current.intentionLabel,
            nowWallClockMs = nowWall,
            nowElapsedMs = nowElapsed,
            bootCount = bootCount
        )
        _uiState.update {
            it.copy(
                activeTimer = started,
                completionOutcome = null,
                interruptedAfterRebootNotice = false
            )
        }
        viewModelScope.launch {
            container.focusRepository.saveActiveTimerState(started)
            SessionCompletionWorker.schedule(
                context = appContext,
                delayMs = started.plannedDurationMs,
                sessionName = started.sessionType.displayName,
                plannedMinutes = plannedMinutes
            )
        }
        startTickerLoop()
    }

    fun pauseSession() {
        val active = _uiState.value.activeTimer ?: return
        if (active.status != SessionStatus.RUNNING) return
        val paused = FocusTimerEngine.pause(
            state = active,
            nowWallClockMs = container.clockProvider.nowWallClockMs(),
            nowElapsedMs = container.clockProvider.elapsedRealtimeMs(),
            currentBootCount = container.clockProvider.bootCount()
        )
        tickerJob?.cancel()
        SessionCompletionWorker.cancel(appContext)
        _uiState.update { it.copy(activeTimer = paused) }
        viewModelScope.launch {
            container.focusRepository.saveActiveTimerState(paused)
        }
    }

    fun resumeSession() {
        val active = _uiState.value.activeTimer ?: return
        if (active.status != SessionStatus.PAUSED) return
        val resumed = FocusTimerEngine.resume(
            state = active,
            nowWallClockMs = container.clockProvider.nowWallClockMs(),
            nowElapsedMs = container.clockProvider.elapsedRealtimeMs(),
            currentBootCount = container.clockProvider.bootCount()
        )
        _uiState.update { it.copy(activeTimer = resumed) }
        viewModelScope.launch {
            container.focusRepository.saveActiveTimerState(resumed)
            SessionCompletionWorker.schedule(
                context = appContext,
                delayMs = resumed.remainingMs,
                sessionName = resumed.sessionType.displayName,
                plannedMinutes = (resumed.plannedDurationMs / 60_000L).toInt()
            )
        }
        startTickerLoop()
    }

    fun requestAbortConfirmation() {
        _uiState.update { it.copy(showAbortConfirmDialog = true) }
    }

    fun dismissAbortConfirmation() {
        _uiState.update { it.copy(showAbortConfirmDialog = false) }
    }

    fun confirmAbortSession() {
        val active = _uiState.value.activeTimer ?: return
        tickerJob?.cancel()
        SessionCompletionWorker.cancel(appContext)
        _uiState.update { it.copy(activeTimer = null, showAbortConfirmDialog = false) }
        viewModelScope.launch {
            container.focusRepository.recordAbortedSession(active, SessionStatus.ABORTED)
        }
    }

    fun onForegroundSync() {
        val active = _uiState.value.activeTimer ?: return
        viewModelScope.launch {
            recoverPersistedTimer(active)
        }
    }

    fun dismissCompletionOutcome() {
        _uiState.update { it.copy(completionOutcome = null) }
    }

    fun dismissRebootNotice() {
        _uiState.update { it.copy(interruptedAfterRebootNotice = false) }
    }

    private suspend fun recoverPersistedTimer(saved: ActiveTimerState) {
        val eval = FocusTimerEngine.evaluateState(
            state = saved,
            nowWallClockMs = container.clockProvider.nowWallClockMs(),
            nowElapsedMs = container.clockProvider.elapsedRealtimeMs(),
            currentBootCount = container.clockProvider.bootCount()
        )
        when (eval) {
            is TimerRecoveryResult.Active -> {
                _uiState.update { it.copy(activeTimer = eval.state) }
                container.focusRepository.saveActiveTimerState(eval.state)
                if (eval.state.status == SessionStatus.RUNNING) {
                    startTickerLoop()
                }
            }
            is TimerRecoveryResult.ReadyToComplete -> {
                completeSession(eval.state)
            }
            is TimerRecoveryResult.InterruptedAfterReboot -> {
                tickerJob?.cancel()
                SessionCompletionWorker.cancel(appContext)
                container.focusRepository.recordAbortedSession(eval.state, SessionStatus.INTERRUPTED)
                _uiState.update {
                    it.copy(
                        activeTimer = null,
                        interruptedAfterRebootNotice = true
                    )
                }
            }
        }
    }

    private fun startTickerLoop() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (true) {
                val active = _uiState.value.activeTimer ?: break
                if (active.status != SessionStatus.RUNNING) break
                val eval = FocusTimerEngine.evaluateState(
                    state = active,
                    nowWallClockMs = container.clockProvider.nowWallClockMs(),
                    nowElapsedMs = container.clockProvider.elapsedRealtimeMs(),
                    currentBootCount = container.clockProvider.bootCount()
                )
                when (eval) {
                    is TimerRecoveryResult.Active -> {
                        _uiState.update { it.copy(activeTimer = eval.state) }
                    }
                    is TimerRecoveryResult.ReadyToComplete -> {
                        completeSession(eval.state)
                        break
                    }
                    is TimerRecoveryResult.InterruptedAfterReboot -> {
                        container.focusRepository.recordAbortedSession(eval.state, SessionStatus.INTERRUPTED)
                        _uiState.update {
                            it.copy(activeTimer = null, interruptedAfterRebootNotice = true)
                        }
                        break
                    }
                }
                delay(250L)
            }
        }
    }

    private suspend fun completeSession(state: ActiveTimerState) {
        if (completingSessionId == state.sessionId) return
        completingSessionId = state.sessionId
        tickerJob?.cancel()
        SessionCompletionWorker.cancel(appContext)

        val baseline = container.usageRepository.dashboardState.value.baselineStatus
        val outcome = container.focusRepository.recordCompletedSession(state, baseline)
        _uiState.update {
            it.copy(
                activeTimer = null,
                completionOutcome = outcome,
                intentionLabel = ""
            )
        }
        completingSessionId = null
    }

    override fun onCleared() {
        super.onCleared()
        tickerJob?.cancel()
    }

    class Factory(
        private val container: AppContainer,
        private val appContext: Context
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return FocusViewModel(container, appContext) as T
        }
    }
}
