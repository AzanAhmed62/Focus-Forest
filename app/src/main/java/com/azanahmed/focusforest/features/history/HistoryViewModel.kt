package com.azanahmed.focusforest.features.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.azanahmed.focusforest.core.di.AppContainer
import com.azanahmed.focusforest.data.repository.FocusRepository
import com.azanahmed.focusforest.domain.model.FocusCategory
import com.azanahmed.focusforest.domain.model.FocusRoutine
import com.azanahmed.focusforest.domain.model.FocusSession
import com.azanahmed.focusforest.domain.model.SessionReflection
import com.azanahmed.focusforest.domain.model.SessionStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class HistoryRange(val label: String, val days: Int?) {
    ALL("All time", null),
    LAST_7("7 days", 7),
    LAST_30("30 days", 30),
    LAST_90("90 days", 90)
}

data class HistoryFilters(
    val range: HistoryRange = HistoryRange.LAST_30,
    val categoryId: String? = null,
    val uncategorized: Boolean = false,
    val routineId: String? = null,
    val status: SessionStatus? = null,
    val query: String = ""
)

data class HistoryDetailState(
    val session: FocusSession,
    val note: String,
    val reflection: SessionReflection?,
    val isSaving: Boolean = false,
    val error: String? = null
)

data class HistoryUiState(
    val isLoading: Boolean = true,
    val sessions: List<FocusSession> = emptyList(),
    val categories: List<FocusCategory> = emptyList(),
    val routines: List<FocusRoutine> = emptyList(),
    val filters: HistoryFilters = HistoryFilters(),
    val detail: HistoryDetailState? = null
) {
    val shownCount: Int get() = sessions.size
}

class HistoryViewModel(
    private val container: AppContainer
) : ViewModel() {

    private val filters = MutableStateFlow(HistoryFilters())
    private val detail = MutableStateFlow<HistoryDetailState?>(null)
    private val _events = MutableSharedFlow<String>()
    val events: SharedFlow<String> = _events.asSharedFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val filteredSessions = filters.flatMapLatest { current ->
        val cutoff = current.range.days?.let { days ->
            container.clockProvider.todayLocalDate().minusDays((days - 1).toLong()).toString()
        }
        container.focusRepository.observeFilteredSessions(
            cutoffDate = cutoff,
            categoryId = current.categoryId,
            uncategorized = current.uncategorized,
            routineId = current.routineId,
            status = current.status,
            query = current.query
        )
    }

    val uiState: StateFlow<HistoryUiState> = combine(
        filteredSessions,
        container.focusContextRepository.allCategories,
        container.focusContextRepository.allRoutines,
        filters,
        detail
    ) { sessions, categories, routines, currentFilters, currentDetail ->
        HistoryUiState(
            isLoading = false,
            sessions = sessions,
            categories = categories.sortedBy { it.name },
            routines = routines.sortedBy { it.name },
            filters = currentFilters,
            detail = currentDetail
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HistoryUiState()
    )

    fun setRange(range: HistoryRange) {
        filters.update { it.copy(range = range) }
    }

    fun setCategory(categoryId: String?) {
        filters.update { it.copy(categoryId = categoryId, uncategorized = false) }
    }

    fun setUncategorized(selected: Boolean) {
        filters.update {
            it.copy(
                uncategorized = selected,
                categoryId = if (selected) null else it.categoryId
            )
        }
    }

    fun setRoutine(routineId: String?) {
        filters.update { it.copy(routineId = routineId) }
    }

    fun setStatus(status: SessionStatus?) {
        filters.update { it.copy(status = status) }
    }

    fun setQuery(query: String) {
        filters.update { it.copy(query = query.take(120)) }
    }

    fun clearFilters() {
        filters.value = HistoryFilters(range = filters.value.range)
    }

    fun openSession(sessionId: String) {
        viewModelScope.launch {
            val session = container.focusRepository.getSessionOnce(sessionId) ?: return@launch
            detail.value = HistoryDetailState(
                session = session,
                note = session.note,
                reflection = session.reflection
            )
        }
    }

    fun dismissDetail() {
        detail.value = null
    }

    fun updateNote(note: String) {
        detail.update { it?.copy(note = note.take(FocusRepository.MAX_NOTE_LENGTH), error = null) }
    }

    fun updateReflection(reflection: SessionReflection?) {
        detail.update { it?.copy(reflection = reflection, error = null) }
    }

    fun saveDetail() {
        val current = detail.value ?: return
        if (current.isSaving) return
        detail.value = current.copy(isSaving = true, error = null)
        viewModelScope.launch {
            container.focusRepository
                .updateSessionMetadata(current.session.id, current.note, current.reflection)
                .fold(
                    onSuccess = {
                        val refreshed = container.focusRepository.getSessionOnce(current.session.id)
                        detail.value = refreshed?.let { session ->
                            HistoryDetailState(
                                session = session,
                                note = session.note,
                                reflection = session.reflection
                            )
                        }
                        _events.emit("Reflection saved. Focus rewards are unchanged.")
                    },
                    onFailure = { error ->
                        detail.update {
                            it?.copy(
                                isSaving = false,
                                error = error.message ?: "Could not save reflection."
                            )
                        }
                    }
                )
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HistoryViewModel(container) as T
        }
    }
}
