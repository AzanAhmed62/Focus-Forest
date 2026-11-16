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

data class ShopUiState(
    val leafPointsBalance: Int = 0,
    val selectedCategory: ShopCategory? = null,
    val items: List<ShopCatalogItem> = emptyList()
)

class ShopViewModel(
    private val container: AppContainer
) : ViewModel() {

    private val selectedCategory = MutableStateFlow<ShopCategory?>(null)
    private val _events = MutableSharedFlow<String>()
    val events: SharedFlow<String> = _events.asSharedFlow()

    val uiState: StateFlow<ShopUiState> = combine(
        container.shopAndForestRepository.forestState,
        container.shopAndForestRepository.shopItems,
        selectedCategory
    ) { forest, items, cat ->
        val filtered = if (cat == null) items else items.filter { it.category == cat }
        ShopUiState(
            leafPointsBalance = forest.spendableLeafPoints,
            selectedCategory = cat,
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

    fun selectCategory(category: ShopCategory?) {
        selectedCategory.value = category
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
                    _events.emit("Need ${item.costLeafPoints} Leaf Points — complete focus sessions or challenges to earn more.")
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
