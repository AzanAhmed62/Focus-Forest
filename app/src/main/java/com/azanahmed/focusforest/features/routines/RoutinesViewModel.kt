package com.azanahmed.focusforest.features.routines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.azanahmed.focusforest.core.di.AppContainer
import com.azanahmed.focusforest.domain.model.ChainConfig
import com.azanahmed.focusforest.domain.model.FocusCategory
import com.azanahmed.focusforest.domain.model.FocusRoutine
import com.azanahmed.focusforest.domain.model.validation
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RoutineDraft(
    val id: String? = null,
    val name: String = "",
    val description: String = "",
    val config: ChainConfig = ChainConfig(),
    val ambientPreset: com.azanahmed.focusforest.domain.model.AmbientSoundPreset =
        com.azanahmed.focusforest.domain.model.AmbientSoundPreset.NONE,
    val defaultCategoryId: String? = null,
    val isBuiltIn: Boolean = false
) {
    fun toRoutine(): FocusRoutine = FocusRoutine(
        id = id.orEmpty(),
        name = name,
        description = description,
        config = config,
        ambientPreset = ambientPreset,
        defaultCategoryId = defaultCategoryId,
        isBuiltIn = isBuiltIn
    )
}

data class CategoryDraft(
    val id: String? = null,
    val name: String = ""
)

data class RoutinesUiState(
    val isLoading: Boolean = true,
    val routines: List<FocusRoutine> = emptyList(),
    val archivedRoutines: List<FocusRoutine> = emptyList(),
    val categories: List<FocusCategory> = emptyList(),
    val archivedCategories: List<FocusCategory> = emptyList(),
    val routineDraft: RoutineDraft? = null,
    val routineError: String? = null,
    val categoryDraft: CategoryDraft? = null,
    val categoryError: String? = null,
    val confirmDeleteRoutineId: String? = null,
    val confirmArchiveCategoryId: String? = null
)

class RoutinesViewModel(
    private val container: AppContainer
) : ViewModel() {

    private val local = MutableStateFlow(RoutinesUiState())
    private val _events = MutableSharedFlow<String>()
    val events: SharedFlow<String> = _events.asSharedFlow()

    val uiState: StateFlow<RoutinesUiState> = combine(
        container.focusContextRepository.allRoutines,
        container.focusContextRepository.allCategories,
        local
    ) { routines, categories, controls ->
        controls.copy(
            isLoading = false,
            routines = routines
                .filterNot { it.isArchived }
                .sortedWith(compareByDescending<FocusRoutine> { it.isPinned }.thenBy { it.sortOrder }.thenBy { it.name }),
            archivedRoutines = routines.filter { it.isArchived }.sortedBy { it.name },
            categories = categories.filterNot { it.isArchived }.sortedBy { it.sortOrder },
            archivedCategories = categories.filter { it.isArchived }.sortedBy { it.name }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RoutinesUiState()
    )

    fun openCreateRoutine() {
        local.update {
            it.copy(
                routineDraft = RoutineDraft(),
                routineError = null
            )
        }
    }

    fun openEditRoutine(routineId: String) {
        val routine = uiState.value.routines.firstOrNull { it.id == routineId }
            ?: uiState.value.archivedRoutines.firstOrNull { it.id == routineId }
            ?: return
        local.update {
            it.copy(
                routineDraft = RoutineDraft(
                    id = routine.id,
                    name = routine.name,
                    description = routine.description,
                    config = routine.config,
                    ambientPreset = routine.ambientPreset,
                    defaultCategoryId = routine.defaultCategoryId,
                    isBuiltIn = routine.isBuiltIn
                ),
                routineError = null
            )
        }
    }

    fun dismissRoutineEditor() {
        local.update { it.copy(routineDraft = null, routineError = null) }
    }

    fun updateRoutineDraft(transform: (RoutineDraft) -> RoutineDraft) {
        local.update { state ->
            val draft = state.routineDraft ?: return@update state
            state.copy(routineDraft = transform(draft), routineError = null)
        }
    }

    fun saveRoutine() {
        val draft = local.value.routineDraft ?: return
        val candidate = draft.toRoutine()
        val validation = candidate.validation()
        if (!validation.isValid) {
            local.update { it.copy(routineError = validation.firstError()) }
            return
        }
        viewModelScope.launch {
            val result = if (draft.id == null) {
                container.focusContextRepository.createRoutine(candidate)
            } else {
                container.focusContextRepository.updateRoutine(candidate)
            }
            result.fold(
                onSuccess = {
                    local.update { it.copy(routineDraft = null, routineError = null) }
                    _events.emit(if (draft.id == null) "Routine created." else "Routine updated.")
                },
                onFailure = { error ->
                    local.update { it.copy(routineError = error.message ?: "Could not save routine.") }
                }
            )
        }
    }

    fun duplicateRoutine(routineId: String) {
        viewModelScope.launch {
            container.focusContextRepository.duplicateRoutine(routineId).fold(
                onSuccess = { _events.emit("Duplicated as \"${it.name}\".") },
                onFailure = { _events.emit(it.message ?: "Could not duplicate routine.") }
            )
        }
    }

    fun togglePinned(routineId: String) {
        val routine = uiState.value.routines.firstOrNull { it.id == routineId } ?: return
        viewModelScope.launch {
            container.focusContextRepository.setPinned(routineId, !routine.isPinned)
        }
    }

    fun moveRoutine(routineId: String, direction: Int) {
        viewModelScope.launch {
            container.focusContextRepository.moveRoutine(routineId, direction)
        }
    }

    fun setRoutineArchived(routineId: String, archived: Boolean) {
        viewModelScope.launch {
            container.focusContextRepository.archiveRoutine(routineId, archived)
            _events.emit(if (archived) "Routine archived." else "Routine restored.")
        }
    }

    fun requestDeleteRoutine(routineId: String) {
        local.update { it.copy(confirmDeleteRoutineId = routineId) }
    }

    fun dismissDeleteRoutine() {
        local.update { it.copy(confirmDeleteRoutineId = null) }
    }

    fun confirmDeleteRoutine() {
        val id = local.value.confirmDeleteRoutineId ?: return
        viewModelScope.launch {
            container.focusContextRepository.deleteRoutine(id)
            local.update { it.copy(confirmDeleteRoutineId = null) }
            _events.emit("Routine deleted. Past sessions keep their saved snapshots.")
        }
    }

    fun openCreateCategory() {
        local.update { it.copy(categoryDraft = CategoryDraft(), categoryError = null) }
    }

    fun openRenameCategory(categoryId: String) {
        val category = uiState.value.categories.firstOrNull { it.id == categoryId } ?: return
        local.update {
            it.copy(categoryDraft = CategoryDraft(id = category.id, name = category.name), categoryError = null)
        }
    }

    fun dismissCategoryEditor() {
        local.update { it.copy(categoryDraft = null, categoryError = null) }
    }

    fun updateCategoryName(name: String) {
        local.update { state ->
            state.copy(categoryDraft = state.categoryDraft?.copy(name = name.take(30)), categoryError = null)
        }
    }

    fun saveCategory() {
        val draft = local.value.categoryDraft ?: return
        if (draft.name.isBlank()) {
            local.update { it.copy(categoryError = "Give the category a name.") }
            return
        }
        viewModelScope.launch {
            val result = if (draft.id == null) {
                container.focusContextRepository.createCategory(draft.name)
            } else {
                container.focusContextRepository.renameCategory(draft.id, draft.name)
            }
            result.fold(
                onSuccess = {
                    local.update { it.copy(categoryDraft = null, categoryError = null) }
                    _events.emit(if (draft.id == null) "Category added." else "Category renamed.")
                },
                onFailure = { error ->
                    local.update { it.copy(categoryError = error.message ?: "Could not save category.") }
                }
            )
        }
    }

    fun requestArchiveCategory(categoryId: String) {
        local.update { it.copy(confirmArchiveCategoryId = categoryId) }
    }

    fun dismissArchiveCategory() {
        local.update { it.copy(confirmArchiveCategoryId = null) }
    }

    fun confirmArchiveCategory() {
        val id = local.value.confirmArchiveCategoryId ?: return
        viewModelScope.launch {
            container.focusContextRepository.archiveCategory(id, archived = true)
            local.update { it.copy(confirmArchiveCategoryId = null) }
            _events.emit("Category archived. Past sessions keep their saved name.")
        }
    }

    fun restoreCategory(categoryId: String) {
        viewModelScope.launch {
            container.focusContextRepository.archiveCategory(categoryId, archived = false)
            _events.emit("Category restored.")
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return RoutinesViewModel(container) as T
        }
    }
}
