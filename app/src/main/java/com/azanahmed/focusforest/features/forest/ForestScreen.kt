package com.azanahmed.focusforest.features.forest

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Forest
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.LocalFlorist
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Park
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Water
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.azanahmed.focusforest.domain.model.AchievementItem
import com.azanahmed.focusforest.domain.model.ForestTree
import com.azanahmed.focusforest.domain.model.GrowthStage
import com.azanahmed.focusforest.domain.model.ShopCatalogItem
import com.azanahmed.focusforest.domain.model.TreeSpecies
import com.azanahmed.focusforest.ui.components.BotanicalCard
import com.azanahmed.focusforest.ui.components.EmptyStateCard
import com.azanahmed.focusforest.ui.components.ForestEnvironmentThumbnail
import com.azanahmed.focusforest.ui.components.ForestIllustrationCanvas
import com.azanahmed.focusforest.ui.components.SectionHeader
import com.azanahmed.focusforest.ui.components.StatPill
import com.azanahmed.focusforest.ui.components.TreeSpeciesThumbnail
import com.azanahmed.focusforest.ui.theme.ForestRadii
import com.azanahmed.focusforest.ui.theme.ForestSizes
import com.azanahmed.focusforest.ui.theme.ForestSpacing
import com.azanahmed.focusforest.ui.theme.LocalForestSemanticColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ForestScreen(
    viewModel: ForestViewModel,
    onOpenShop: () -> Unit,
    onOpenHistory: () -> Unit,
    onStartFocus: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val forest = state.forestState
    val semanticColors = LocalForestSemanticColors.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showAllAchievements by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { message -> snackbarHostState.showSnackbar(message) }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 620.dp)
                .testTag("forest_lazy_column"),
            contentPadding = PaddingValues(
                horizontal = ForestSpacing.screenHorizontal,
                vertical = ForestSpacing.screenVertical
            ),
            verticalArrangement = Arrangement.spacedBy(ForestSpacing.sectionGap)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Your Forest",
                            style = MaterialTheme.typography.headlineLarge,
                            modifier = Modifier.semantics { heading() }
                        )
                        Spacer(modifier = Modifier.height(ForestSpacing.xxs))
                        Text(
                            text = "A living record of the time you have protected.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(ForestSpacing.sm))
                    Button(
                        onClick = onOpenShop,
                        modifier = Modifier.testTag("open_virtual_shop_button"),
                        shape = RoundedCornerShape(ForestRadii.control)
                    ) {
                        Icon(
                            Icons.Rounded.Storefront,
                            contentDescription = null,
                            modifier = Modifier.size(ForestSizes.iconSmall)
                        )
                        Spacer(modifier = Modifier.width(ForestSpacing.xs))
                        Text("Shop")
                    }
                }
            }

            item {
                ForestHeroCard(
                    state = state,
                    semanticColors = semanticColors,
                    onStartFocus = onStartFocus
                )
            }

            if (forest.trees.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Rounded.Forest,
                        title = "Your grove is waiting for its first seed",
                        subtitle = "Complete any focus session and the tree it plants will appear here. " +
                            "Breaks restore your attention but never plant a tree.",
                        actionLabel = "Start First Focus",
                        onAction = onStartFocus,
                        actionTestTag = "forest_start_first_focus"
                    )
                }
            }

            item {
                SectionHeader(
                    title = "Grove collection",
                    subtitle = "Pomodoro, Deep Work, and Custom Focus each plant a different tree."
                )
            }
            item {
                PlantableSpeciesRow(speciesCounts = state.speciesCounts)
            }
            item {
                Text(
                    text = "Fern and willow are the grove's rest-day flora. " +
                        "They appear in the wider artwork, but a break never plants a tree.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                SectionHeader(
                    title = "Grove setting",
                    subtitle = "Change the clearing around your trees. Progress is never reset."
                )
            }
            item {
                EnvironmentRow(
                    items = state.environments,
                    onEquip = viewModel::equipEnvironment,
                    onOpenShop = onOpenShop
                )
            }

            item {
                SectionHeader(
                    title = "Grove details",
                    subtitle = "Optional touches you own. Locked details open in the Shop."
                )
            }
            item {
                DecorationFlow(
                    items = state.decorations,
                    onToggle = viewModel::toggleDecoration,
                    onOpenShop = onOpenShop
                )
            }

            item {
                SectionHeader(
                    title = "Planting timeline",
                    subtitle = "Each completed focus session becomes one tree.",
                    actionLabel = "History",
                    onAction = onOpenHistory
                )
            }
            if (forest.trees.isEmpty()) {
                item {
                    BotanicalCard {
                        Text(
                            text = "No plantings yet. Your first focus session will start the timeline.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                item {
                    BotanicalCard(modifier = Modifier.testTag("forest_timeline")) {
                        forest.trees.take(5).forEachIndexed { index, tree ->
                            TimelineRow(tree = tree, stage = forest.currentStage)
                            if (index < minOf(forest.trees.size, 5) - 1) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = ForestSpacing.xs),
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                                )
                            }
                        }
                        if (forest.trees.size > 5) {
                            Spacer(modifier = Modifier.height(ForestSpacing.xs))
                            Text(
                                text = "Showing 5 of ${forest.trees.size} plantings.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(
                            onClick = onOpenHistory,
                            modifier = Modifier.testTag("forest_open_history_button")
                        ) {
                            Text("Revisit the work behind these trees")
                        }
                    }
                }
            }

            item {
                val unlocked = state.achievements.count { it.unlocked }
                SectionHeader(
                    title = "Milestones",
                    subtitle = "$unlocked of ${state.achievements.size} earned through steady focus."
                )
            }
            item {
                AchievementsCard(
                    achievements = state.achievements,
                    showAll = showAllAchievements,
                    onToggleShowAll = { showAllAchievements = !showAllAchievements }
                )
            }

            item {
                Text(
                    text = "Your forest is a record of focused time, not a score. " +
                        "Rest days are part of a sustainable rhythm.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = ForestSpacing.xs)
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(ForestSpacing.md)
        )
    }
}

@Composable
private fun ForestHeroCard(
    state: ForestUiState,
    semanticColors: com.azanahmed.focusforest.ui.theme.ForestSemanticColors,
    onStartFocus: () -> Unit
) {
    val forest = state.forestState
    val nextStage = GrowthStage.entries.firstOrNull { it.minSeeds > forest.totalSeeds }
    val stageProgress = if (nextStage == null) {
        1f
    } else {
        val floor = forest.currentStage.minSeeds
        val span = (nextStage.minSeeds - floor).coerceAtLeast(1)
        ((forest.totalSeeds - floor).toFloat() / span.toFloat()).coerceIn(0.02f, 1f)
    }
    val equippedEnvironment = state.environments.firstOrNull { it.equipped }?.name
        ?: "Emerald Clearing"
    val summary = buildString {
        append("Level ${forest.forestLevel}, ${forest.currentStage.label}. ")
        append("${forest.totalTreesPlanted} trees planted. ")
        append("${forest.totalSeeds} seeds grown. ")
        append("${forest.spendableLeafPoints} Leaf Points available. ")
        if (nextStage == null) {
            append("Your grove has reached its final growth stage.")
        } else {
            append("${nextStage.minSeeds - forest.totalSeeds} more seeds to reach ${nextStage.label}.")
        }
    }

    BotanicalCard {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ForestSizes.forestHeroHeight)
                .clip(RoundedCornerShape(ForestRadii.card))
        ) {
            ForestIllustrationCanvas(
                forestState = forest,
                contentDescription = summary,
                modifier = Modifier.testTag("forest_canvas")
            )
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(ForestSpacing.sm),
                shape = RoundedCornerShape(ForestRadii.pill),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = ForestSpacing.sm, vertical = ForestSpacing.xxs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Landscape,
                        contentDescription = null,
                        modifier = Modifier.size(ForestSizes.iconSmall),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(ForestSpacing.xxs))
                    Text(equippedEnvironment, style = MaterialTheme.typography.labelMedium)
                }
            }
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(ForestSpacing.sm),
                shape = RoundedCornerShape(ForestRadii.pill),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.90f),
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Text(
                    text = "Lv.${forest.forestLevel} · ${forest.currentStage.label}",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = ForestSpacing.sm, vertical = ForestSpacing.xxs)
                )
            }
        }

        Spacer(modifier = Modifier.height(ForestSpacing.md))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ForestSpacing.sm)
        ) {
            StatPill(
                label = "Trees planted",
                value = "${forest.totalTreesPlanted}",
                icon = Icons.Rounded.Park,
                modifier = Modifier.weight(1f)
            )
            StatPill(
                label = "Leaf Points",
                value = "${forest.spendableLeafPoints}",
                icon = Icons.Rounded.Eco,
                containerColor = semanticColors.leafContainer,
                contentColor = semanticColors.onLeafContainer,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(ForestSpacing.md))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (nextStage == null) {
                    "Ancient grove · ${forest.totalSeeds} seeds"
                } else {
                    "${nextStage.minSeeds - forest.totalSeeds} seeds to ${nextStage.label}"
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "${forest.totalSeeds} seeds grown",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(ForestSpacing.xs))
        LinearProgressIndicator(
            progress = { stageProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(ForestSizes.progressTrack)
                .clip(RoundedCornerShape(ForestSizes.progressTrack / 2))
                .testTag("forest_stage_progress")
        )
        Spacer(modifier = Modifier.height(ForestSpacing.sm))
        Text(
            text = summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("forest_accessibility_summary")
        )

        Spacer(modifier = Modifier.height(ForestSpacing.md))
        Button(
            onClick = onStartFocus,
            modifier = Modifier
                .fillMaxWidth()
                .height(ForestSizes.minTouchTarget)
                .testTag("forest_start_focus_button"),
            shape = RoundedCornerShape(ForestRadii.control)
        ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(ForestSizes.iconSmall))
            Spacer(modifier = Modifier.width(ForestSpacing.xs))
            Text(if (forest.trees.isEmpty()) "Plant Your First Tree" else "Grow Your Forest")
        }
    }
}

@Composable
private fun PlantableSpeciesRow(speciesCounts: Map<TreeSpecies, Int>) {
    val plantable = listOf(TreeSpecies.PINE, TreeSpecies.OAK, TreeSpecies.BIRCH)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ForestSpacing.sm)
    ) {
        plantable.forEach { species ->
            val count = speciesCounts[species] ?: 0
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .testTag("forest_species_${species.name}"),
                shape = RoundedCornerShape(ForestRadii.card),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(ForestSpacing.sm),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TreeSpeciesThumbnail(
                        species = species,
                        stage = GrowthStage.MATURE_TREE,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(74.dp)
                    )
                    Spacer(modifier = Modifier.height(ForestSpacing.xs))
                    Text(
                        text = species.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    Text(
                        text = speciesSourceLabel(species),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(ForestSpacing.xxs))
                    Text(
                        text = if (count > 0) "$count planted" else "Not planted yet",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (count > 0) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun EnvironmentRow(
    items: List<ShopCatalogItem>,
    onEquip: (String) -> Unit,
    onOpenShop: () -> Unit
) {
    if (items.isEmpty()) {
        BotanicalCard {
            Text(
                text = "The grove catalog is preparing. Reopen this screen in a moment.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(ForestSpacing.sm),
        contentPadding = PaddingValues(horizontal = 1.dp)
    ) {
        items(items, key = { it.id }) { item ->
            val actionLabel = when {
                item.equipped -> "Equipped"
                item.owned -> "Tap to equip"
                else -> "${item.costLeafPoints} Leaf Points · Unlock in Shop"
            }
            Surface(
                modifier = Modifier
                    .width(184.dp)
                    .testTag("forest_environment_${item.id}")
                    .clickable {
                        if (item.owned) onEquip(item.id) else onOpenShop()
                    },
                shape = RoundedCornerShape(ForestRadii.card),
                color = if (item.equipped) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                } else {
                    MaterialTheme.colorScheme.surface
                },
                border = BorderStroke(
                    width = if (item.equipped) 2.dp else 1.dp,
                    color = if (item.equipped) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                    }
                )
            ) {
                Column(modifier = Modifier.padding(ForestSpacing.sm)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(84.dp)
                            .clip(RoundedCornerShape(ForestRadii.control))
                    ) {
                        ForestEnvironmentThumbnail(environmentId = item.id)
                    }
                    Spacer(modifier = Modifier.height(ForestSpacing.xs))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                            maxLines = 1
                        )
                        if (item.equipped) {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = "Equipped",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(ForestSizes.iconSmall)
                            )
                        } else if (!item.owned) {
                            Icon(
                                Icons.Rounded.Lock,
                                contentDescription = "Locked",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(ForestSizes.iconSmall)
                            )
                        }
                    }
                    Text(
                        text = actionLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
            }
        }
    }
}

@Composable
private fun DecorationFlow(
    items: List<ShopCatalogItem>,
    onToggle: (String, String, Boolean) -> Unit,
    onOpenShop: () -> Unit
) {
    if (items.isEmpty()) {
        Text(
            text = "The grove catalog is preparing. Reopen this screen in a moment.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(ForestSpacing.xs)) {
        items.forEach { item ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("forest_decoration_${item.id}")
                    .clickable {
                        if (item.owned) {
                            onToggle(item.id, item.name, !item.equipped)
                        } else {
                            onOpenShop()
                        }
                    },
                shape = RoundedCornerShape(ForestRadii.control),
                color = if (item.equipped) {
                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.60f)
                } else {
                    MaterialTheme.colorScheme.surface
                },
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.40f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(
                        horizontal = ForestSpacing.sm,
                        vertical = ForestSpacing.sm
                    ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = decorationIcon(item.id),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(ForestSizes.iconMedium)
                    )
                    Spacer(modifier = Modifier.width(ForestSpacing.sm))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.name, style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = when {
                                item.equipped -> "Placed in your grove · tap to remove"
                                item.owned -> "Owned · tap to place"
                                else -> "${item.costLeafPoints} Leaf Points · unlock in Shop"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (item.equipped) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = "Placed",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(ForestSizes.iconSmall)
                        )
                    } else if (!item.owned) {
                        Icon(
                            Icons.Rounded.Lock,
                            contentDescription = "Locked",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(ForestSizes.iconSmall)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineRow(tree: ForestTree, stage: GrowthStage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TreeSpeciesThumbnail(
            species = tree.species,
            stage = stage,
            modifier = Modifier
                .size(48.dp)
        )
        Spacer(modifier = Modifier.width(ForestSpacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(tree.species.displayName, style = MaterialTheme.typography.titleSmall)
            Text(
                text = "${formatPlantedDate(tree.plantedDate)} · ${tree.focusMinutes} min focus",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = speciesSourceLabel(tree.species),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun AchievementsCard(
    achievements: List<AchievementItem>,
    showAll: Boolean,
    onToggleShowAll: () -> Unit
) {
    if (achievements.isEmpty()) {
        BotanicalCard {
            Text(
                text = "Milestones appear as your focus record grows.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }
    val ordered = achievements.sortedWith(
        compareByDescending<AchievementItem> { it.unlocked }
            .thenBy { it.type.ordinal }
    )
    val visible = if (showAll) ordered else ordered.take(4)
    BotanicalCard {
        visible.forEachIndexed { index, achievement ->
            AchievementRow(achievement)
            if (index < visible.size - 1) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = ForestSpacing.xs),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                )
            }
        }
        if (achievements.size > 4) {
            TextButton(onClick = onToggleShowAll) {
                Text(if (showAll) "Show fewer milestones" else "Show all ${achievements.size} milestones")
            }
        }
    }
}

@Composable
private fun AchievementRow(achievement: AchievementItem) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (achievement.unlocked) Icons.Rounded.EmojiEvents else Icons.Rounded.Lock,
            contentDescription = null,
            tint = if (achievement.unlocked) {
                LocalForestSemanticColors.current.leaf
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(ForestSizes.iconMedium)
        )
        Spacer(modifier = Modifier.width(ForestSpacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = achievement.type.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = if (achievement.unlocked) {
                    "Unlocked · +${achievement.type.bonusXp} XP"
                } else {
                    achievement.progressLabel
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (achievement.unlocked) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
        if (achievement.unlocked) {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = "Unlocked",
                tint = LocalForestSemanticColors.current.leaf,
                modifier = Modifier.size(ForestSizes.iconSmall)
            )
        }
    }
}

private fun speciesSourceLabel(species: TreeSpecies): String = when (species) {
    TreeSpecies.PINE -> "Pomodoro"
    TreeSpecies.OAK -> "Deep Work"
    TreeSpecies.BIRCH -> "Custom Focus"
    TreeSpecies.WILLOW -> "Rest flora"
    TreeSpecies.FERN -> "Rest flora"
}

private fun decorationIcon(itemId: String) = when (itemId) {
    "deco_stream_brook" -> Icons.Rounded.Water
    "deco_wildflowers" -> Icons.Rounded.LocalFlorist
    "deco_fireflies" -> Icons.Rounded.LightMode
    else -> Icons.Rounded.Landscape
}

private fun formatPlantedDate(raw: String): String = runCatching {
    LocalDate.parse(raw).format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))
}.getOrDefault(raw)
