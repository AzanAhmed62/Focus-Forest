package com.azanahmed.focusforest.features.focus

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.azanahmed.focusforest.core.di.AppContainer
import com.azanahmed.focusforest.data.repository.SessionCompletionOutcome
import com.azanahmed.focusforest.domain.engine.FocusTimerEngine
import com.azanahmed.focusforest.domain.engine.TimerRecoveryResult
import com.azanahmed.focusforest.domain.model.ActiveChainState
import com.azanahmed.focusforest.domain.model.ActiveTimerState
import com.azanahmed.focusforest.domain.model.AmbientSoundPreset
import com.azanahmed.focusforest.domain.model.ChainConfig
import com.azanahmed.focusforest.domain.model.FocusCategory
import com.azanahmed.focusforest.domain.model.FocusRoutine
import com.azanahmed.focusforest.domain.model.SessionStatus
import com.azanahmed.focusforest.domain.model.SessionType
import com.azanahmed.focusforest.domain.model.validation
import com.azanahmed.focusforest.notifications.SessionCompletionWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class FocusUiState(
    val selectedType: SessionType = SessionType.POMODORO,
    val customMinutes: Int = 35,
    val intentionLabel: String = "",
    val isChainMode: Boolean = false,
    val chainConfig: ChainConfig = ChainConfig(),
    val activeChain: ActiveChainState? = null,
    val activeTimer: ActiveTimerState? = null,
    val showAbortConfirmDialog: Boolean = false,
    val showAbortChainConfirmDialog: Boolean = false,
    val completionOutcome: SessionCompletionOutcome? = null,
    val pendingRetryState: ActiveTimerState? = null,
    val completionErrorMessage: String? = null,
    val interruptedAfterRebootNotice: Boolean = false,
    val currentStreakDays: Int = 0,
    val forestLevel: Int = 1,
    val ambientPreset: AmbientSoundPreset = AmbientSoundPreset.NONE,
    val ambientVolume: Float = 0.55f,
    val ambientPlayWithSession: Boolean = true,
    val isAmbientPlaying: Boolean = false,
    val routines: List<FocusRoutine> = emptyList(),
    val categories: List<FocusCategory> = emptyList(),
    val selectedCategoryId: String? = null,
    val isReady: Boolean = false
) {
    val plannedMinutesForSelection: Int
        get() = when {
            activeChain != null && !activeChain.isCompleted -> activeChain.currentStepPlannedMinutes
            isChainMode -> chainConfig.focusMinutes
            selectedType == SessionType.CUSTOM -> customMinutes
            else -> selectedType.defaultMinutes
        }

    val selectedCategory: FocusCategory?
        get() = categories.firstOrNull { it.id == selectedCategoryId }
}

class FocusViewModel(
    private val container: AppContainer,
    private val appContext: Context,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle()
) : ViewModel() {

    private val _uiState = MutableStateFlow(FocusUiState())
    val uiState: StateFlow<FocusUiState> = _uiState.asStateFlow()

    private var tickerJob: Job? = null
    private var completingSessionId: String? = null
    private var isAppForeground: Boolean = false
    private var timerStartPending: Boolean = false

    init {
        timerStartPending = true
        viewModelScope.launch {
            try {
                val savedTimer = container.focusRepository.activeTimerState.first()
                val savedChain = container.focusRepository.activeChainState.first()
                if (savedChain != null && !savedChain.isCompleted) {
                    _uiState.update { it.copy(activeChain = savedChain, isChainMode = true) }
                }
                if (savedTimer != null) {
                    recoverPersistedTimer(savedTimer)
                } else {
                    recoverChainContinuation()
                }
            } finally {
                timerStartPending = false
                _uiState.update { it.copy(isReady = true) }
            }
        }
        viewModelScope.launch {
            container.focusRepository.activeChainState.collect { chain ->
                _uiState.update { it.copy(activeChain = chain) }
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
        viewModelScope.launch {
            container.preferencesDataStore.ambientPreset.collect { preset ->
                _uiState.update { it.copy(ambientPreset = preset) }
            }
        }
        viewModelScope.launch {
            container.preferencesDataStore.ambientVolume.collect { vol ->
                _uiState.update { it.copy(ambientVolume = vol) }
                container.ambientAudioController.setVolume(vol)
            }
        }
        viewModelScope.launch {
            container.preferencesDataStore.ambientPlayWithSession.collect { syncSession ->
                _uiState.update { it.copy(ambientPlayWithSession = syncSession) }
            }
        }
        viewModelScope.launch {
            container.ambientAudioController.isPlaying.collect { playing ->
                _uiState.update { it.copy(isAmbientPlaying = playing) }
            }
        }
        viewModelScope.launch {
            container.focusContextRepository.activeRoutines.collect { routines ->
                _uiState.update { state ->
                    state.copy(
                        routines = routines,
                        selectedCategoryId = state.selectedCategoryId?.takeIf { id ->
                            state.categories.any { it.id == id }
                        }
                    )
                }
            }
        }
        viewModelScope.launch {
            container.focusContextRepository.activeCategories.collect { categories ->
                _uiState.update { state ->
                    val stillValid = state.selectedCategoryId == null ||
                        categories.any { it.id == state.selectedCategoryId }
                    state.copy(
                        categories = categories,
                        selectedCategoryId = if (stillValid) state.selectedCategoryId else null
                    )
                }
            }
        }
    }

    /**
     * Consumes a one-shot routine start request passed by navigation. The SavedStateHandle keys
     * are cleared immediately so rotation or process recreation cannot start a second timer.
     */
    fun consumeRoutineAutoStart() {
        val routineId = savedStateHandle.get<String>("routineId").orEmpty()
        if (routineId.isBlank() || !_uiState.value.isReady) return
        // Clear both keys before starting so rotation or process recreation cannot repeat it.
        savedStateHandle["routineId"] = ""
        savedStateHandle["requestId"] = ""
        startRoutine(routineId)
    }

    fun selectSessionType(type: SessionType) {
        if (_uiState.value.activeTimer != null || _uiState.value.activeChain != null) return
        _uiState.update {
            it.copy(
                selectedType = type,
                completionOutcome = null,
                interruptedAfterRebootNotice = false,
                completionErrorMessage = null
            )
        }
    }

    fun setChainMode(enabled: Boolean) {
        if (_uiState.value.activeTimer != null || _uiState.value.activeChain != null) return
        _uiState.update { it.copy(isChainMode = enabled, completionOutcome = null) }
    }

    fun updateChainConfig(transform: (ChainConfig) -> ChainConfig) {
        if (_uiState.value.activeTimer != null || _uiState.value.activeChain != null) return
        _uiState.update { it.copy(chainConfig = transform(it.chainConfig).validated()) }
    }

    fun updateCustomMinutes(minutes: Int) {
        _uiState.update { it.copy(customMinutes = minutes.coerceIn(5, 180)) }
    }

    fun updateIntentionLabel(label: String) {
        _uiState.update { it.copy(intentionLabel = label.take(60)) }
    }

    fun selectAmbientPreset(preset: AmbientSoundPreset) {
        val current = _uiState.value
        _uiState.update { it.copy(ambientPreset = preset) }
        viewModelScope.launch {
            container.preferencesDataStore.setAmbientPreferences(
                preset = preset,
                volume = current.ambientVolume,
                playWithSession = current.ambientPlayWithSession
            )
        }
        if (preset == AmbientSoundPreset.NONE) {
            container.ambientAudioController.stop()
        } else if (current.isAmbientPlaying || (current.ambientPlayWithSession && current.activeTimer?.status == SessionStatus.RUNNING)) {
            container.ambientAudioController.startOrUpdate(preset, current.ambientVolume)
        }
    }

    fun updateAmbientVolume(volume: Float) {
        val safeVol = volume.coerceIn(0f, 1f)
        val current = _uiState.value
        _uiState.update { it.copy(ambientVolume = safeVol) }
        container.ambientAudioController.setVolume(safeVol)
        viewModelScope.launch {
            container.preferencesDataStore.setAmbientPreferences(
                preset = current.ambientPreset,
                volume = safeVol,
                playWithSession = current.ambientPlayWithSession
            )
        }
    }

    fun setAmbientPlayWithSession(enabled: Boolean) {
        val current = _uiState.value
        _uiState.update { it.copy(ambientPlayWithSession = enabled) }
        viewModelScope.launch {
            container.preferencesDataStore.setAmbientPreferences(
                preset = current.ambientPreset,
                volume = current.ambientVolume,
                playWithSession = enabled
            )
        }
    }

    fun toggleAmbientPlayback() {
        val current = _uiState.value
        if (current.isAmbientPlaying) {
            container.ambientAudioController.pause()
        } else {
            val preset = if (current.ambientPreset == AmbientSoundPreset.NONE) {
                AmbientSoundPreset.SOFT_RAIN
            } else {
                current.ambientPreset
            }
            if (current.ambientPreset == AmbientSoundPreset.NONE) {
                selectAmbientPreset(preset)
            }
            container.ambientAudioController.startOrUpdate(preset, current.ambientVolume)
        }
    }

    fun selectCategory(categoryId: String?) {
        val current = _uiState.value
        if (current.activeTimer != null) return
        if (current.activeChain != null && !current.activeChain.isCompleted) return
        if (categoryId != null && current.categories.none { it.id == categoryId }) return
        _uiState.update { it.copy(selectedCategoryId = categoryId) }
    }

    /**
     * One-action routine start. The routine is only a template: its configuration and labels are
     * copied into the chain/timer snapshots so later edits or deletion cannot rewrite this session.
     */
    fun startRoutine(routineId: String) {
        if (timerStartPending || _uiState.value.activeTimer != null) return
        val current = _uiState.value
        if (current.activeChain != null && !current.activeChain.isCompleted) return
        launchTimerStart {
            val routine = container.focusContextRepository.getRoutineOnce(routineId)
                ?: return@launchTimerStart
            if (routine.isArchived) return@launchTimerStart
            val validated = routine.config.validated()
            val category = routine.defaultCategoryId
                ?.let { container.focusContextRepository.getCategoryOnce(it) }
                ?.takeIf { !it.isArchived }
            val chainId = "chain_${UUID.randomUUID()}"
            val newChain = ActiveChainState(
                chainId = chainId,
                config = validated,
                currentStepType = SessionType.POMODORO,
                intentionLabel = current.intentionLabel.trim(),
                routineId = routine.id,
                routineNameSnapshot = routine.name,
                routineSnapshot = routine.snapshotLabel(),
                categoryId = category?.id,
                categoryNameSnapshot = category?.name.orEmpty()
            )
            _uiState.update {
                it.copy(
                    activeChain = newChain,
                    isChainMode = true,
                    chainConfig = validated,
                    selectedCategoryId = category?.id,
                    ambientPreset = routine.ambientPreset,
                    completionOutcome = null,
                    completionErrorMessage = null,
                    interruptedAfterRebootNotice = false
                )
            }
            container.focusRepository.saveActiveChainState(newChain)
            container.focusContextRepository.markRoutineUsed(routine.id)
            container.preferencesDataStore.setAmbientPreferences(
                preset = routine.ambientPreset,
                volume = current.ambientVolume,
                playWithSession = current.ambientPlayWithSession
            )
            if (routine.ambientPreset == AmbientSoundPreset.NONE) {
                container.ambientAudioController.stop()
            }
            launchTimerStep(
                sessionType = SessionType.POMODORO,
                plannedMinutes = validated.focusMinutes,
                intentionLabel = newChain.intentionLabel,
                chainId = chainId,
                routineId = newChain.routineId,
                routineNameSnapshot = newChain.routineNameSnapshot,
                routineSnapshot = newChain.routineSnapshot,
                categoryId = newChain.categoryId,
                categoryNameSnapshot = newChain.categoryNameSnapshot
            )
        }
    }

    fun startSession() {
        if (timerStartPending || _uiState.value.activeTimer != null) return
        val current = _uiState.value
        val category = current.selectedCategory

        if (current.isChainMode && (current.activeChain == null || current.activeChain.isCompleted)) {
            val validatedCfg = current.chainConfig.validated()
            val chainId = "chain_${UUID.randomUUID()}"
            val newChain = ActiveChainState(
                chainId = chainId,
                config = validatedCfg,
                currentStepType = SessionType.POMODORO,
                intentionLabel = current.intentionLabel.trim(),
                categoryId = category?.id,
                categoryNameSnapshot = category?.name.orEmpty()
            )
            _uiState.update {
                it.copy(
                    activeChain = newChain,
                    completionOutcome = null,
                    completionErrorMessage = null,
                    interruptedAfterRebootNotice = false
                )
            }
            launchTimerStart {
                container.focusRepository.saveActiveChainState(newChain)
                launchTimerStep(
                    sessionType = SessionType.POMODORO,
                    plannedMinutes = validatedCfg.focusMinutes,
                    intentionLabel = newChain.intentionLabel,
                    chainId = chainId,
                    categoryId = newChain.categoryId,
                    categoryNameSnapshot = newChain.categoryNameSnapshot
                )
            }
            return
        }

        val activeChain = current.activeChain
        if (activeChain != null && !activeChain.isCompleted) {
            launchTimerStart {
                val clearedWait = activeChain.copy(waitingForUserNextStep = false)
                container.focusRepository.saveActiveChainState(clearedWait)
                launchTimerStep(
                    sessionType = activeChain.currentStepType,
                    plannedMinutes = activeChain.currentStepPlannedMinutes,
                    intentionLabel = activeChain.intentionLabel,
                    chainId = activeChain.chainId,
                    routineId = activeChain.routineId,
                    routineNameSnapshot = activeChain.routineNameSnapshot,
                    routineSnapshot = activeChain.routineSnapshot,
                    categoryId = activeChain.categoryId,
                    categoryNameSnapshot = activeChain.categoryNameSnapshot
                )
            }
            return
        }

        val plannedMinutes = current.plannedMinutesForSelection
        launchTimerStart {
            launchTimerStep(
                sessionType = current.selectedType,
                plannedMinutes = plannedMinutes,
                intentionLabel = current.intentionLabel,
                chainId = null,
                categoryId = category?.id,
                categoryNameSnapshot = category?.name.orEmpty()
            )
        }
    }

    /**
     * Sets the pending flag synchronously before launching, then clears it once the timer state
     * has been persisted. This closes the double-tap window where two coroutines could both
     * observe an empty `activeTimer` and create two sessions.
     */
    private fun launchTimerStart(block: suspend () -> Unit) {
        timerStartPending = true
        viewModelScope.launch {
            try {
                block()
            } finally {
                timerStartPending = false
            }
        }
    }

    fun startNextChainStep() {
        val activeChain = _uiState.value.activeChain ?: return
        if (activeChain.isCompleted || timerStartPending || _uiState.value.activeTimer != null) return
        launchTimerStart {
            val updated = activeChain.copy(waitingForUserNextStep = false)
            container.focusRepository.saveActiveChainState(updated)
            launchTimerStep(
                sessionType = updated.currentStepType,
                plannedMinutes = updated.currentStepPlannedMinutes,
                intentionLabel = updated.intentionLabel,
                chainId = updated.chainId,
                routineId = updated.routineId,
                routineNameSnapshot = updated.routineNameSnapshot,
                routineSnapshot = updated.routineSnapshot,
                categoryId = updated.categoryId,
                categoryNameSnapshot = updated.categoryNameSnapshot
            )
        }
    }

    fun dismissCompletedChainSummary() {
        _uiState.update { it.copy(activeChain = null, completionOutcome = null) }
        viewModelScope.launch {
            container.focusRepository.saveActiveChainState(null)
        }
    }

    private suspend fun launchTimerStep(
        sessionType: SessionType,
        plannedMinutes: Int,
        intentionLabel: String,
        chainId: String?,
        routineId: String? = null,
        routineNameSnapshot: String = "",
        routineSnapshot: String = "",
        categoryId: String? = null,
        categoryNameSnapshot: String = ""
    ) {
        val nowWall = container.clockProvider.nowWallClockMs()
        val nowElapsed = container.clockProvider.elapsedRealtimeMs()
        val bootCount = container.clockProvider.bootCount()

        val started = FocusTimerEngine.startSession(
            sessionType = sessionType,
            plannedMinutes = plannedMinutes,
            intentionLabel = intentionLabel,
            nowWallClockMs = nowWall,
            nowElapsedMs = nowElapsed,
            bootCount = bootCount,
            chainId = chainId,
            routineId = routineId,
            routineNameSnapshot = routineNameSnapshot,
            routineSnapshot = routineSnapshot,
            categoryId = categoryId,
            categoryNameSnapshot = categoryNameSnapshot
        )
        _uiState.update {
            it.copy(
                activeTimer = started,
                completionOutcome = null,
                interruptedAfterRebootNotice = false,
                completionErrorMessage = null,
                pendingRetryState = null
            )
        }
        val chain = if (chainId != null) {
            container.focusRepository.activeChainState.first()?.takeIf { it.chainId == chainId }
        } else {
            null
        }
        if (chain != null) {
            // Chain + timer are written atomically so process death cannot lose the pending step.
            container.focusRepository.startChainStep(chain, started)
        } else {
            container.focusRepository.saveActiveTimerState(started)
        }
        SessionCompletionWorker.schedule(
            context = appContext,
            delayMs = started.plannedDurationMs,
            sessionId = started.sessionId,
            sessionName = started.sessionType.displayName,
            plannedMinutes = plannedMinutes
        )

        val current = _uiState.value
        if (isAppForeground &&
            current.ambientPlayWithSession &&
            current.ambientPreset != AmbientSoundPreset.NONE
        ) {
            container.ambientAudioController.startOrUpdate(current.ambientPreset, current.ambientVolume)
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
        if (_uiState.value.ambientPlayWithSession) {
            container.ambientAudioController.pause()
        }
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
        val current = _uiState.value
        if (current.ambientPlayWithSession && current.ambientPreset != AmbientSoundPreset.NONE) {
            container.ambientAudioController.startOrUpdate(current.ambientPreset, current.ambientVolume)
        }
        viewModelScope.launch {
            container.focusRepository.saveActiveTimerState(resumed)
            SessionCompletionWorker.schedule(
                context = appContext,
                delayMs = resumed.remainingMs,
                sessionId = resumed.sessionId,
                sessionName = resumed.sessionType.displayName,
                plannedMinutes = (resumed.plannedDurationMs / 60_000L).toInt().coerceAtLeast(1)
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
        if (_uiState.value.ambientPlayWithSession) {
            container.ambientAudioController.stop()
        }
        _uiState.update { it.copy(activeTimer = null, showAbortConfirmDialog = false) }
        viewModelScope.launch {
            container.focusRepository.recordAbortedSession(active, SessionStatus.ABORTED)
            // If part of a chain, also mark chain stopped cleanly
            if (active.chainId != null) {
                container.focusRepository.saveActiveChainState(null)
            }
        }
    }

    fun requestAbortChainConfirmation() {
        _uiState.update { it.copy(showAbortChainConfirmDialog = true) }
    }

    fun dismissAbortChainConfirmation() {
        _uiState.update { it.copy(showAbortChainConfirmDialog = false) }
    }

    fun confirmAbortChain() {
        val active = _uiState.value.activeTimer
        tickerJob?.cancel()
        SessionCompletionWorker.cancel(appContext)
        if (_uiState.value.ambientPlayWithSession) {
            container.ambientAudioController.stop()
        }
        _uiState.update {
            it.copy(
                activeTimer = null,
                activeChain = null,
                showAbortChainConfirmDialog = false
            )
        }
        viewModelScope.launch {
            if (active != null) {
                container.focusRepository.recordAbortedSession(active, SessionStatus.ABORTED)
            }
            container.focusRepository.saveActiveChainState(null)
        }
    }

    fun onForegroundSync() {
        if (timerStartPending) return
        viewModelScope.launch {
            val savedTimer = container.focusRepository.activeTimerState.first()
            val savedChain = container.focusRepository.activeChainState.first()
            _uiState.update { it.copy(activeChain = savedChain, activeTimer = savedTimer) }
            if (savedTimer != null) {
                recoverPersistedTimer(savedTimer)
            } else {
                tickerJob?.cancel()
                recoverChainContinuation()
            }
        }
    }

    /** Called by the Compose screen from ON_START. Ambient audio is never started in background. */
    fun onAppForeground() {
        isAppForeground = true
        val current = _uiState.value
        if (current.ambientPlayWithSession &&
            current.activeTimer?.status == SessionStatus.RUNNING &&
            current.ambientPreset != AmbientSoundPreset.NONE &&
            !current.isAmbientPlaying
        ) {
            container.ambientAudioController.startOrUpdate(current.ambientPreset, current.ambientVolume)
        }
        onForegroundSync()
    }

    /** Called by the Compose screen from ON_STOP. */
    fun onAppBackground() {
        isAppForeground = false
        if (_uiState.value.isAmbientPlaying) {
            container.ambientAudioController.pause()
        }
    }

    fun retryPendingCompletion() {
        val pending = _uiState.value.pendingRetryState ?: return
        if (timerStartPending || _uiState.value.activeTimer != null || completingSessionId != null) return
        viewModelScope.launch {
            completeSession(pending)
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
                    // Re-arm the completion worker: force-stop or an OEM power manager may have
                    // cancelled the previously scheduled job while the timer kept running.
                    SessionCompletionWorker.schedule(
                        context = appContext,
                        delayMs = eval.state.remainingMs.coerceAtLeast(1_000L),
                        sessionId = eval.state.sessionId,
                        sessionName = eval.state.sessionType.displayName,
                        plannedMinutes = (eval.state.plannedDurationMs / 60_000L).toInt().coerceAtLeast(1)
                    )
                    startTickerLoop()
                }
            }
            is TimerRecoveryResult.ReadyToComplete -> {
                completeSession(eval.state)
            }
            is TimerRecoveryResult.InterruptedAfterReboot -> {
                tickerJob?.cancel()
                SessionCompletionWorker.cancel(appContext)
                if (_uiState.value.ambientPlayWithSession) {
                    container.ambientAudioController.stop()
                }
                container.focusRepository.recordAbortedSession(eval.state, SessionStatus.INTERRUPTED)
                container.focusRepository.markChainWaitingAfterInterruption(eval.state.chainId, eval.state.sessionId)
                _uiState.update {
                    it.copy(
                        activeTimer = null,
                        activeChain = container.focusRepository.activeChainState.first(),
                        interruptedAfterRebootNotice = true
                    )
                }
            }
        }
    }

    /**
     * Cold-start recovery for the case where WorkManager completed a chain step while the UI
     * process was dead. Auto chains start their persisted next timer; manual chains stay waiting.
     */
    private suspend fun recoverChainContinuation() {
        val recovered = container.focusRepository.recoverChainState()
        _uiState.update {
            it.copy(
                activeChain = recovered.chain,
                isChainMode = recovered.chain != null || it.isChainMode
            )
        }
        val next = recovered.nextTimer ?: return
        _uiState.update { it.copy(activeTimer = next) }
        // The recovered timer may already have been running before process death; evaluate it
        // instead of granting a fresh full block.
        recoverPersistedTimer(next)
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
                        container.focusRepository.markChainWaitingAfterInterruption(eval.state.chainId, eval.state.sessionId)
                        _uiState.update {
                            it.copy(
                                activeTimer = null,
                                activeChain = container.focusRepository.activeChainState.first(),
                                interruptedAfterRebootNotice = true
                            )
                        }
                        break
                    }
                }
                delay(1000L)
            }
        }
    }

    private suspend fun completeSession(state: ActiveTimerState) {
        if (completingSessionId == state.sessionId) return
        completingSessionId = state.sessionId
        // The ticker itself calls completeSession once the timer elapses. Cancelling the job we are
        // running inside would cancel the completion write and surface a bogus save failure, so only
        // cancel the ticker when completion was triggered from another coroutine (retry/recovery).
        if (currentCoroutineContext()[Job] !== tickerJob) {
            tickerJob?.cancel()
        }
        SessionCompletionWorker.cancel(appContext)

        try {
            val baseline = container.usageRepository.dashboardState.value.baselineStatus
            val outcome = container.focusRepository.recordCompletedSession(state, baseline)
            val continuation = container.focusRepository.continueChainAfterCompletion(
                chainId = state.chainId,
                session = outcome.session
            )
            val updatedChain = continuation.chain

            if (continuation.nextTimer != null) {
                val next = continuation.nextTimer
                _uiState.update {
                    it.copy(
                        activeTimer = next,
                        activeChain = updatedChain,
                        completionOutcome = if (outcome.duplicateSuppressed) it.completionOutcome else outcome,
                        completionErrorMessage = null,
                        pendingRetryState = null
                    )
                }
                SessionCompletionWorker.schedule(
                    context = appContext,
                    delayMs = next.plannedDurationMs,
                    sessionId = next.sessionId,
                    sessionName = next.sessionType.displayName,
                    plannedMinutes = (next.plannedDurationMs / 60_000L).toInt().coerceAtLeast(1)
                )
                if (isAppForeground &&
                    _uiState.value.ambientPlayWithSession &&
                    _uiState.value.ambientPreset != AmbientSoundPreset.NONE
                ) {
                    container.ambientAudioController.startOrUpdate(
                        _uiState.value.ambientPreset,
                        _uiState.value.ambientVolume
                    )
                }
                startTickerLoop()
            } else {
                if (_uiState.value.ambientPlayWithSession) {
                    container.ambientAudioController.stop()
                }
                _uiState.update {
                    it.copy(
                        activeTimer = null,
                        activeChain = updatedChain,
                        completionOutcome = if (outcome.duplicateSuppressed) it.completionOutcome else outcome,
                        intentionLabel = if (updatedChain == null || updatedChain.isCompleted) "" else it.intentionLabel,
                        completionErrorMessage = null,
                        pendingRetryState = null
                    )
                }
            }
        } catch (c: CancellationException) {
            // ViewModel/screen teardown: leave the persisted timer in place so startup recovery or the
            // scheduled worker can finish the session instead of showing a phantom storage error.
            throw c
        } catch (t: Throwable) {
            _uiState.update {
                it.copy(
                    activeTimer = null,
                    pendingRetryState = state,
                    completionErrorMessage = "Session finished, but saving failed (${t.message ?: "storage error"}). Tap Retry to save cleanly."
                )
            }
        } finally {
            completingSessionId = null
        }
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
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            return FocusViewModel(container, appContext, extras.createSavedStateHandle()) as T
        }
    }
}
