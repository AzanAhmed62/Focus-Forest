package com.example.features.shop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.di.AppContainer
import com.example.data.repository.ShopActionResult
import com.example.domain.model.ShopCatalogItem
import com.example.domain.model.ShopCategory
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ShopFilterTab {
    ALL,
    OWNED,
    THEME,
    AVATAR,
    DECORATION,
    ENVIRONMENT
}

data class ShopUiState(
    val leafPointsBalance: Int = 0,
    val selectedFilter: ShopFilterTab = ShopFilterTab.ALL,
    val items: List<ShopCatalogItem> = emptyList()
)

class ShopViewModel(
    private val container: AppContainer
) : ViewModel() {

    private val selectedFilter = MutableStateFlow(ShopFilterTab.ALL)
    private val _events = MutableSharedFlow<String>()
    val events: SharedFlow<String> = _events.asSharedFlow()

    val uiState: StateFlow<ShopUiState> = combine(
        container.shopAndForestRepository.forestState,
        container.shopAndForestRepository.shopItems,
        selectedFilter
    ) { forest, items, filter ->
        val filtered = when (filter) {
            ShopFilterTab.ALL -> items
            ShopFilterTab.OWNED -> items.filter { it.owned }
            ShopFilterTab.THEME -> items.filter { it.category == ShopCategory.THEME }
            ShopFilterTab.AVATAR -> items.filter { it.category == ShopCategory.AVATAR }
            ShopFilterTab.DECORATION -> items.filter { it.category == ShopCategory.DECORATION }
            ShopFilterTab.ENVIRONMENT -> items.filter { it.category == ShopCategory.ENVIRONMENT }
        }
        ShopUiState(
            leafPointsBalance = forest.spendableLeafPoints,
            selectedFilter = filter,
            items = filtered
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ShopUiState()
    )

    init {
        viewModelScope.launch {
            container.shopAndForestRepository.ensureCatalogSeeded()
        }
    }

    fun selectFilter(filter: ShopFilterTab) {
        selectedFilter.value = filter
    }

    fun onItemAction(item: ShopCatalogItem) {
        viewModelScope.launch {
            when (container.shopAndForestRepository.purchaseOrEquip(item.id)) {
                ShopActionResult.Success -> {
                    val msg = if (item.owned) {
                        if (item.category == ShopCategory.DECORATION) {
                            if (item.equipped) "Removed ${item.name} from forest" else "Placed ${item.name} in forest"
                        } else {
                            "Equipped ${item.name}"
                        }
                    } else {
                        "Unlocked and equipped ${item.name}!"
                    }
                    _events.emit(msg)
                }
                ShopActionResult.InsufficientPoints -> {
                    val deficit = (item.costLeafPoints - uiState.value.leafPointsBalance).coerceAtLeast(1)
                    _events.emit("Insufficient Leaf Points — need $deficit more to unlock ${item.name}.")
                }
                ShopActionResult.ItemNotFound -> Unit
            }
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ShopViewModel(container) as T
        }
    }
}
