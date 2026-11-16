package com.example.features.forest

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Park
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.AchievementItem
import com.example.domain.model.TreeSpecies
import com.example.ui.components.BotanicalCard
import com.example.ui.components.ForestIllustrationCanvas
import com.example.ui.components.StatPill

@Composable
fun ForestScreen(
    viewModel: ForestViewModel,
    onOpenShop: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val forest = state.forestState

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 620.dp),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Your Forest", style = MaterialTheme.typography.headlineLarge)
                        Text(
                            text = "Level ${forest.forestLevel} • ${forest.currentStage.label}",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Button(
                        onClick = onOpenShop,
                        modifier = Modifier.testTag("open_virtual_shop_button"),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Rounded.Storefront, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Shop")
                    }
                }
            }

            // Live Forest Canvas
            item {
                BotanicalCard {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(230.dp)
                            .clip(RoundedCornerShape(18.dp))
                    ) {
                        ForestIllustrationCanvas(forestState = forest)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StatPill(
                            label = "Trees Planted",
                            value = "${forest.totalTreesPlanted}",
                            icon = Icons.Rounded.Park,
                            modifier = Modifier.weight(1f)
                        )
                        StatPill(
                            label = "Leaf Points",
                            value = "${forest.spendableLeafPoints}",
                            icon = Icons.Rounded.Eco,
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    val nextDelta = state.seedsToNextStage
                    Text(
                        text = if (nextDelta != null) "$nextDelta more seeds to reach the next growth stage"
                        else "Maximum Ancient Grove stage reached (${forest.totalSeeds} seeds)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    val stageProgress = if (nextDelta == null) 1f
                    else (forest.totalSeeds % 12) / 12f
                    LinearProgressIndicator(
                        progress = { stageProgress.coerceIn(0.08f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                }
            }

            // Plant & Tree Species Collection
            item {
                Text("Plant Collection", style = MaterialTheme.typography.titleLarge)
            }
            item {
                BotanicalCard {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        TreeSpecies.entries.forEach { species ->
                            val count = state.speciesCounts[species] ?: 0
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(species.displayName, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        species.scientificHint,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (count > 0) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = "$count planted",
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Achievements
            item {
                Text("Achievements", style = MaterialTheme.typography.titleLarge)
            }
            items(state.achievements, key = { it.type.name }) { ach ->
                AchievementCard(ach)
            }
        }
    }
}

@Composable
private fun AchievementCard(achievement: AchievementItem) {
    BotanicalCard(
        containerColor = if (achievement.unlocked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        else MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (achievement.unlocked) Icons.Rounded.EmojiEvents else Icons.Rounded.Lock,
                contentDescription = null,
                tint = if (achievement.unlocked) MaterialTheme.colorScheme.tertiary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(26.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = achievement.type.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = achievement.type.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (achievement.unlocked) "Unlocked • +${achievement.type.bonusXp} XP"
                    else "Progress: ${achievement.progressLabel}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (achievement.unlocked) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (achievement.unlocked) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = "Unlocked",
                    tint = MaterialTheme.colorScheme.tertiary
                )
            }
        }
    }
}
