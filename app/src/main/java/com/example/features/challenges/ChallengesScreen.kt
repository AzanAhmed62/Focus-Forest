package com.example.features.challenges

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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.Park
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.ChallengeIconType
import com.example.domain.model.ChallengeItem
import com.example.domain.model.ChallengeMetricType
import com.example.domain.model.ChallengeStatus
import com.example.ui.components.BotanicalCard
import com.example.ui.components.EmptyStateCard
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ChallengesScreen(
    viewModel: ChallengesViewModel
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    if (state.showCreateDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissCreateDialog() },
            title = { Text("Create Personal Challenge") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = state.newTitle,
                        onValueChange = viewModel::updateNewTitle,
                        label = { Text("Goal title") },
                        placeholder = { Text("e.g. Weekend Writing Sprint") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_challenge_title_input")
                    )
                    OutlinedTextField(
                        value = state.newDescription,
                        onValueChange = viewModel::updateNewDescription,
                        label = { Text("Short note (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = state.newMetricType == ChallengeMetricType.FOCUS_MINUTES,
                            onClick = { viewModel.selectNewMetricType(ChallengeMetricType.FOCUS_MINUTES) },
                            label = { Text("Focus Minutes") }
                        )
                        FilterChip(
                            selected = state.newMetricType == ChallengeMetricType.COMPLETED_SESSIONS,
                            onClick = { viewModel.selectNewMetricType(ChallengeMetricType.COMPLETED_SESSIONS) },
                            label = { Text("Sessions") }
                        )
                    }
                    val maxRange = if (state.newMetricType == ChallengeMetricType.FOCUS_MINUTES) 300f else 15f
                    val minRange = if (state.newMetricType == ChallengeMetricType.FOCUS_MINUTES) 15f else 1f
                    Text(
                        text = "Target: ${state.newTargetValue} " +
                            if (state.newMetricType == ChallengeMetricType.FOCUS_MINUTES) "minutes" else "sessions",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Slider(
                        value = state.newTargetValue.toFloat().coerceIn(minRange, maxRange),
                        onValueChange = { viewModel.updateNewTargetValue(it.toInt()) },
                        valueRange = minRange..maxRange
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.createPersonalChallenge() },
                    modifier = Modifier.testTag("save_custom_challenge_button")
                ) {
                    Text("Create Goal")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissCreateDialog() }) {
                    Text("Cancel")
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 620.dp),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Text("Challenges", style = MaterialTheme.typography.headlineLarge)
                    Text(
                        text = "Steady, achievable goals tied directly to your completed sessions and usage baseline.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                item {
                    Text("Daily Challenges", style = MaterialTheme.typography.titleLarge)
                }
                if (state.dailyChallenges.isEmpty()) {
                    item {
                        EmptyStateCard(
                            title = "Preparing today's challenges",
                            subtitle = "Your three daily goals are generated once per calendar day."
                        )
                    }
                } else {
                    items(state.dailyChallenges, key = { it.id }) { challenge ->
                        ChallengeCard(challenge)
                    }
                }

                item {
                    Spacer(Modifier.height(6.dp))
                    Text("Weekly Challenges", style = MaterialTheme.typography.titleLarge)
                }
                items(state.weeklyChallenges, key = { it.id }) { challenge ->
                    ChallengeCard(challenge)
                }

                if (state.customChallenges.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(6.dp))
                        Text("Personal Goals", style = MaterialTheme.typography.titleLarge)
                    }
                    items(state.customChallenges, key = { it.id }) { challenge ->
                        ChallengeCard(challenge)
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { viewModel.openCreateDialog() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(18.dp)
                .testTag("add_custom_challenge_fab")
        ) {
            Icon(Icons.Rounded.Add, contentDescription = "Create personal challenge")
        }
    }
}

@Composable
private fun ChallengeCard(challenge: ChallengeItem) {
    val isDone = challenge.status == ChallengeStatus.COMPLETED
    val icon = when (challenge.iconType) {
        ChallengeIconType.TIMER -> Icons.Rounded.Timer
        ChallengeIconType.PINE_TREE -> Icons.Rounded.Park
        ChallengeIconType.SPARK -> Icons.Rounded.AutoAwesome
        ChallengeIconType.SUNRISE -> Icons.Rounded.WbSunny
        ChallengeIconType.SHIELD_LEAF -> Icons.Rounded.Shield
        ChallengeIconType.CUP_TEA -> Icons.Rounded.LocalCafe
    }

    BotanicalCard(
        containerColor = if (isDone) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        else MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(
                    imageVector = if (isDone) Icons.Rounded.CheckCircle else icon,
                    contentDescription = null,
                    tint = if (isDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = challenge.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = challenge.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer
            ) {
                Text(
                    text = "+${challenge.rewardXp} XP",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = { challenge.progressFraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
        )
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${challenge.currentProgress} / ${challenge.targetValue}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val completedTime = challenge.completedAtMs?.let {
                val fmt = DateTimeFormatter.ofPattern("MMM d, HH:mm")
                Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(fmt)
            }
            Text(
                text = if (isDone && completedTime != null) "Completed $completedTime" else "+${challenge.rewardSeeds} seeds",
                style = MaterialTheme.typography.labelSmall,
                color = if (isDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
