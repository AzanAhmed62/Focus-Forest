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
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.LocalFlorist
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Water
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.azanahmed.focusforest.domain.model.ShopCatalogItem
import com.azanahmed.focusforest.domain.model.ShopCategory
import com.azanahmed.focusforest.ui.components.AvatarBadge
import com.azanahmed.focusforest.ui.components.BotanicalCard
import com.azanahmed.focusforest.ui.components.EmptyStateCard
import com.azanahmed.focusforest.ui.components.ForestEnvironmentThumbnail
import com.azanahmed.focusforest.ui.theme.ForestRadii
import com.azanahmed.focusforest.ui.theme.ForestSizes
import com.azanahmed.focusforest.ui.theme.ForestSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopScreen(
    viewModel: ShopViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingPurchase by remember { mutableStateOf<ShopCatalogItem?>(null) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    pendingPurchase?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingPurchase = null },
            title = { Text("Unlock ${item.name}?") },
            text = {
                Text(
                    "This spends ${item.costLeafPoints} Leaf Points from your balance of " +
                        "${state.leafPointsBalance}. Leaf Points come from completed focus " +
                        "sessions and challenge rewards, never from money."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingPurchase = null
                        viewModel.onItemAction(item)
                    },
                    modifier = Modifier.testTag("confirm_shop_purchase_button")
                ) {
                    Text("Unlock")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingPurchase = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Botanical Shop") },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("shop_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Surface(
                        shape = RoundedCornerShape(ForestRadii.chip),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier.padding(end = 14.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(
                                horizontal = ForestSpacing.xs,
                                vertical = 6.dp
                            ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Rounded.Eco,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(ForestSpacing.xxs))
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
                    .widthIn(max = 620.dp)
                    .testTag("shop_lazy_column"),
                contentPadding = PaddingValues(
                    horizontal = ForestSpacing.screenHorizontal,
                    vertical = ForestSpacing.screenVertical
                ),
                verticalArrangement = Arrangement.spacedBy(ForestSpacing.sm)
            ) {
                item {
                    BotanicalCard(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(
                            alpha = 0.55f
                        )
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.Eco,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Spacer(Modifier.width(ForestSpacing.xs))
                            Text(
                                "Leaf Points",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                        Spacer(Modifier.height(ForestSpacing.xxs))
                        Text(
                            text = "Earned only by completing focus sessions and challenges. " +
                                "No real-money purchases, loot boxes, or random rewards.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(ForestSpacing.xs)
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

                if (state.isLoading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(ForestSpacing.xl),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.testTag("shop_loading_indicator")
                            )
                        }
                    }
                } else if (state.items.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Rounded.Storefront,
                            title = "Nothing in this filter yet",
                            subtitle = if (state.selectedFilter == ShopFilterTab.OWNED) {
                                "Complete focus sessions to earn Leaf Points, then unlock " +
                                    "your first theme, emblem, environment, or grove detail."
                            } else {
                                "This shelf is empty right now. Show all items to browse " +
                                    "the full botanical catalog."
                            },
                            actionLabel = "Show All Items",
                            onAction = { viewModel.selectFilter(ShopFilterTab.ALL) },
                            actionTestTag = "shop_empty_show_all"
                        )
                    }
                } else {
                    items(state.items, key = { it.id }) { item ->
                        ShopItemCard(
                            item = item,
                            balance = state.leafPointsBalance,
                            onAction = {
                                if (!item.owned && item.costLeafPoints > 0 &&
                                    state.leafPointsBalance >= item.costLeafPoints
                                ) {
                                    pendingPurchase = item
                                } else {
                                    viewModel.onItemAction(item)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShopItemCard(
    item: ShopCatalogItem,
    balance: Int,
    onAction: () -> Unit
) {
    val canAfford = balance >= item.costLeafPoints
    BotanicalCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ShopItemPreview(item)
            Spacer(Modifier.width(ForestSpacing.sm))
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
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!item.owned) {
                    Spacer(modifier = Modifier.height(ForestSpacing.xxs))
                    Text(
                        text = if (canAfford) {
                            "Unlock with ${item.costLeafPoints} Leaf Points"
                        } else {
                            "Need ${item.costLeafPoints - balance} more Leaf Points"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (canAfford) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
            Spacer(Modifier.width(ForestSpacing.xs))
            when {
                item.equipped -> {
                    val isDecoration = item.category == ShopCategory.DECORATION
                    OutlinedButton(
                        onClick = onAction,
                        modifier = Modifier
                            .testTag("shop_item_${item.id}")
                            .semantics {
                                contentDescription = if (isDecoration) {
                                    "Remove ${item.name} from forest"
                                } else {
                                    "${item.name} is equipped"
                                }
                            }
                    ) {
                        Text(if (isDecoration) "Remove" else "Equipped")
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
                        Icon(
                            Icons.Rounded.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(ForestSpacing.xxs))
                        Text("${item.costLeafPoints} pts")
                    }
                }
            }
        }
    }
}

@Composable
private fun ShopItemPreview(item: ShopCatalogItem) {
    when (item.category) {
        ShopCategory.ENVIRONMENT -> {
            Box(
                modifier = Modifier
                    .width(84.dp)
                    .height(60.dp)
                    .clip(RoundedCornerShape(ForestRadii.control))
            ) {
                ForestEnvironmentThumbnail(environmentId = item.id)
            }
        }
        ShopCategory.AVATAR -> {
            AvatarBadge(avatarId = item.id, size = 56.dp)
        }
        else -> {
            val icon = when {
                item.category == ShopCategory.THEME -> Icons.Rounded.Palette
                item.id == "deco_stream_brook" -> Icons.Rounded.Water
                item.id == "deco_wildflowers" -> Icons.Rounded.LocalFlorist
                item.id == "deco_fireflies" -> Icons.Rounded.LightMode
                item.category == ShopCategory.DECORATION -> Icons.Rounded.Landscape
                else -> Icons.Rounded.Eco
            }
            PreviewDisc(
                icon = icon,
                tint = Color(item.previewColorHex)
            )
        }
    }
}

@Composable
private fun PreviewDisc(icon: ImageVector, tint: Color) {
    Box(
        modifier = Modifier
            .size(ForestSizes.minTouchTarget)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(ForestSizes.iconMedium)
        )
    }
}
