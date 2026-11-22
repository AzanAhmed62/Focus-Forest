package com.azanahmed.focusforest.features.forest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.azanahmed.focusforest.core.di.AppContainer
import com.azanahmed.focusforest.data.repository.ShopActionResult
import com.azanahmed.focusforest.domain.engine.ForestGrowthEngine
import com.azanahmed.focusforest.domain.model.AchievementItem
import com.azanahmed.focusforest.domain.model.ForestState
import com.azanahmed.focusforest.domain.model.ShopCatalogItem
import com.azanahmed.focusforest.domain.model.ShopCategory
import com.azanahmed.focusforest.domain.model.TreeSpecies
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ForestUiState(
    val forestState: ForestState = ForestState(),
    val seedsToNextStage: Int? = null,
    val speciesCounts: Map<TreeSpecies, Int> = emptyMap(),
    val achievements: List<AchievementItem> = emptyList(),
    val environments: List<ShopCatalogItem> = emptyList(),
    val decorations: List<ShopCatalogItem> = emptyList()
)

class ForestViewModel(
    private val container: AppContainer
) : ViewModel() {

    private val _events = MutableSharedFlow<String>()
    val events: SharedFlow<String> = _events.asSharedFlow()

    val uiState: StateFlow<ForestUiState> = combine(
        container.shopAndForestRepository.forestState,
        container.focusRepository.achievements,
        container.shopAndForestRepository.shopItems
    ) { forest, achievements, shopItems ->
        val counts = TreeSpecies.entries.associateWith { sp ->
            forest.trees.count { it.species == sp }
        }
        ForestUiState(
            forestState = forest,
            seedsToNextStage = ForestGrowthEngine.seedsToNextStage(forest.totalSeeds),
            speciesCounts = counts,
            achievements = achievements,
            environments = shopItems.filter { it.category == ShopCategory.ENVIRONMENT },
            decorations = shopItems.filter { it.category == ShopCategory.DECORATION }
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

    fun equipEnvironment(itemId: String) {
        equipOwnedItem(itemId, "Grove setting updated.")
    }

    fun toggleDecoration(itemId: String, displayName: String, willEquip: Boolean) {
        equipOwnedItem(
            itemId = itemId,
            successMessage = if (willEquip) {
                "$displayName placed in your grove."
            } else {
                "$displayName removed from your grove."
            }
        )
    }

    private fun equipOwnedItem(itemId: String, successMessage: String) {
        viewModelScope.launch {
            when (container.shopAndForestRepository.equipOwnedItem(itemId)) {
                ShopActionResult.Success -> _events.emit(successMessage)
                ShopActionResult.NotOwned -> _events.emit("Unlock this item in the Shop first.")
                ShopActionResult.ItemNotFound -> Unit
                ShopActionResult.InsufficientPoints -> Unit
            }
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ForestViewModel(container) as T
        }
    }
}
