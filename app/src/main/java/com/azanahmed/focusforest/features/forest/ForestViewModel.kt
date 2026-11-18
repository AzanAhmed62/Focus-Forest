package com.azanahmed.focusforest.features.forest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.azanahmed.focusforest.core.di.AppContainer
import com.azanahmed.focusforest.domain.engine.ForestGrowthEngine
import com.azanahmed.focusforest.domain.model.AchievementItem
import com.azanahmed.focusforest.domain.model.ForestState
import com.azanahmed.focusforest.domain.model.TreeSpecies
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ForestUiState(
    val forestState: ForestState = ForestState(),
    val seedsToNextStage: Int? = null,
    val speciesCounts: Map<TreeSpecies, Int> = emptyMap(),
    val achievements: List<AchievementItem> = emptyList()
)

class ForestViewModel(
    private val container: AppContainer
) : ViewModel() {

    val uiState: StateFlow<ForestUiState> = combine(
        container.shopAndForestRepository.forestState,
        container.focusRepository.achievements
    ) { forest, achievements ->
        val counts = TreeSpecies.entries.associateWith { sp ->
            forest.trees.count { it.species == sp }
        }
        ForestUiState(
            forestState = forest,
            seedsToNextStage = ForestGrowthEngine.seedsToNextStage(forest.totalSeeds),
            speciesCounts = counts,
            achievements = achievements
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ForestUiState()
    )

    init {
        viewModelScope.launch {
            container.shopAndForestRepository.ensureCatalogSeeded()
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ForestViewModel(container) as T
        }
    }
}
