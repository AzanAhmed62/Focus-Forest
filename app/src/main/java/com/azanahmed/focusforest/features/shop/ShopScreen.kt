package com.azanahmed.focusforest.features.shop

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.azanahmed.focusforest.domain.model.ShopCatalogItem
import com.azanahmed.focusforest.domain.model.ShopCategory
import com.azanahmed.focusforest.ui.components.BotanicalCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopScreen(
    viewModel: ShopViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Botanical Shop") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("shop_back_button")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier.padding(end = 14.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Rounded.Eco,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "${state.leafPointsBalance} pts",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.testTag("shop_leaf_points_balance")
                            )
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 620.dp),
                contentPadding = PaddingValues(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = "Spend Leaf Points earned from completed focus sessions and challenges. No real-money purchases are active in this version.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = state.selectedFilter == ShopFilterTab.ALL,
                            onClick = { viewModel.selectFilter(ShopFilterTab.ALL) },
                            label = { Text("All") },
                            modifier = Modifier.testTag("shop_filter_all")
                        )
                        FilterChip(
                            selected = state.selectedFilter == ShopFilterTab.OWNED,
                            onClick = { viewModel.selectFilter(ShopFilterTab.OWNED) },
                            label = { Text("Owned") },
                            modifier = Modifier.testTag("shop_filter_owned")
                        )
                        listOf(
                            ShopFilterTab.THEME to ShopCategory.THEME.label,
                            ShopFilterTab.AVATAR to ShopCategory.AVATAR.label,
                            ShopFilterTab.DECORATION to ShopCategory.DECORATION.label,
                            ShopFilterTab.ENVIRONMENT to ShopCategory.ENVIRONMENT.label
                        ).forEach { (tab, label) ->
                            FilterChip(
                                selected = state.selectedFilter == tab,
                                onClick = { viewModel.selectFilter(tab) },
                                label = { Text(label) }
                            )
                        }
                    }
                }

                items(state.items, key = { it.id }) { item ->
                    ShopItemCard(
                        item = item,
                        canAfford = state.leafPointsBalance >= item.costLeafPoints,
                        onAction = { viewModel.onItemAction(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ShopItemCard(
    item: ShopCatalogItem,
    canAfford: Boolean,
    onAction: () -> Unit
) {
    BotanicalCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(item.previewColorHex)),
                contentAlignment = Alignment.Center
            ) {
                if (item.equipped) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = "Equipped",
                        tint = Color.White
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = item.category.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(10.dp))
            when {
                item.equipped -> {
                    OutlinedButton(
                        onClick = onAction,
                        modifier = Modifier.testTag("shop_item_${item.id}")
                    ) {
                        Text(if (item.category == ShopCategory.DECORATION) "Remove" else "Equipped")
                    }
                }
                item.owned -> {
                    Button(
                        onClick = onAction,
                        modifier = Modifier.testTag("shop_item_${item.id}")
                    ) {
                        Text("Equip")
                    }
                }
                else -> {
                    Button(
                        onClick = onAction,
                        colors = if (canAfford) {
                            ButtonDefaults.buttonColors()
                        } else {
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        modifier = Modifier.testTag("shop_item_${item.id}")
                    ) {
                        Text("${item.costLeafPoints} pts")
                    }
                }
            }
        }
    }
}
