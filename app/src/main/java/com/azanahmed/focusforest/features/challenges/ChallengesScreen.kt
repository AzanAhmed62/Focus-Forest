package com.azanahmed.focusforest.features.challenges

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.Park
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.azanahmed.focusforest.domain.engine.ChallengeEngine
import com.azanahmed.focusforest.domain.model.ChallengeIconType
import com.azanahmed.focusforest.domain.model.ChallengeItem
import com.azanahmed.focusforest.domain.model.ChallengeMetricType
import com.azanahmed.focusforest.domain.model.ChallengeStatus
import com.azanahmed.focusforest.ui.components.BotanicalCard
import com.azanahmed.focusforest.ui.components.EmptyStateCard
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChallengesScreen(
    viewModel: ChallengesViewModel
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.actionMessage) {
        val msg = state.actionMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearActionMessage()
    }

    if (state.showCreateOrEditDialog) {
        val isEditing = state.editingChallengeId != null
        val (minBound, maxBound, unitLabel) = ChallengeEngine.targetBoundsFor(state.newMetricType)
        AlertDialog(
            onDismissRequest = { viewModel.dismissDialog() },
            title = { Text(if (isEditing) "Edit Personal Challenge" else "Create Personal Challenge") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = state.newTitle,
                        onValueChange = viewModel::updateNewTitle,
                        label = { Text("Goal title") },
                        placeholder = { Text("e.g. Weekend Writing Sprint") },
                        singleLine = true,
                        isError = state.dialogValidationError != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_challenge_title_input")
                    )
                    state.dialogValidationError?.let { err ->
                        Text(
                            text = err,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag("custom_challenge_validation_error")
                        )
                    }
                    OutlinedTextField(
                        value = state.newDescription,
                        onValueChange = viewModel::updateNewDescription,
                        label = { Text("Short note (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (!isEditing) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(
                                ChallengeMetricType.FOCUS_MINUTES to "Minutes",
                                ChallengeMetricType.COMPLETED_SESSIONS to "Sessions",
                                ChallengeMetricType.DEEP_WORK_SESSIONS to "Deep Work",
                                ChallengeMetricType.DAILY_USAGE_BUDGET to "Usage Budget"
                            ).forEach { (metric, label) ->
                                FilterChip(
                                    selected = state.newMetricType == metric,
                                    onClick = { viewModel.selectNewMetricType(metric) },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }
                    Text(
                        text = "Target: ${state.newTargetValue} $unitLabel",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Slider(
                        value = state.newTargetValue.toFloat().coerceIn(minBound.toFloat(), maxBound.toFloat()),
                        onValueChange = { viewModel.updateNewTargetValue(it.toInt()) },
                        valueRange = minBound.toFloat()..maxBound.toFloat()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.savePersonalChallenge() },
                    modifier = Modifier.testTag("save_custom_challenge_button")
                ) {
                    Text(if (isEditing) "Save Changes" else "Create Goal")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDialog() }) {
                    Text("Cancel")
                }
            }
        )
    }

    state.pendingDeleteChallenge?.let { target ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeleteDialog() },
            title = {
                Text(if (target.rewardClaimed) "Archive completed challenge?" else "Delete personal challenge?")
            },
            text = {
                Text(
                    if (target.rewardClaimed) {
                        "\"${target.title}\" already granted rewards and will be archived from your active list while preserving your reward ledger."
                    } else {
                        "Delete \"${target.title}\" permanently? No XP or seeds have been claimed yet."
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmDeleteChallenge() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_challenge_button")
                ) {
                    Text(if (target.rewardClaimed) "Archive" else "Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDeleteDialog() }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.openCreateDialog() },
                modifier = Modifier.testTag("add_custom_challenge_fab")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Create personal challenge")
            }
        }
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
                    .testTag("challenges_lazy_column"),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Text("Challenges", style = MaterialTheme.typography.headlineLarge)
                    Text(
                        text = "Steady, achievable goals tied directly to your completed sessions and completed-day usage baseline.",
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
                        ChallengeCard(challenge = challenge)
                    }
                }

                item {
                    Spacer(Modifier.height(6.dp))
                    Text("Weekly Challenges", style = MaterialTheme.typography.titleLarge)
                }
                if (state.weeklyChallenges.isEmpty()) {
                    item {
                        EmptyStateCard(
                            title = "Preparing this week's challenges",
                            subtitle = "Weekly goals appear once your current week begins."
                        )
                    }
                } else {
                    items(state.weeklyChallenges, key = { it.id }) { challenge ->
                        ChallengeCard(challenge = challenge)
                    }
                }

                if (state.customChallenges.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(6.dp))
                        Text("Personal Goals", style = MaterialTheme.typography.titleLarge)
                    }
                    items(state.customChallenges, key = { it.id }) { challenge ->
                        ChallengeCard(
                            challenge = challenge,
                            onEdit = { viewModel.openEditDialog(challenge) },
                            onDelete = { viewModel.requestDeleteChallenge(challenge) }
                        )
                    }
                }

                if (state.historyChallenges.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(6.dp))
                        Text("Challenge History", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "Completed and expired goals stay on your device so you can see your follow-through over time.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    items(state.historyChallenges, key = { "history_${it.id}" }) { challenge ->
                        ChallengeCard(challenge = challenge)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChallengeCard(
    challenge: ChallengeItem,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    val isDone = challenge.status == ChallengeStatus.COMPLETED
    val isExpired = challenge.status == ChallengeStatus.EXPIRED
    val icon = when (challenge.iconType) {
        ChallengeIconType.TIMER -> Icons.Rounded.Timer
        ChallengeIconType.PINE_TREE -> Icons.Rounded.Park
        ChallengeIconType.SPARK -> Icons.Rounded.AutoAwesome
        ChallengeIconType.SUNRISE -> Icons.Rounded.WbSunny
        ChallengeIconType.SHIELD_LEAF -> Icons.Rounded.Shield
        ChallengeIconType.CUP_TEA -> Icons.Rounded.LocalCafe
    }
    val unitSuffix = when (challenge.metricType) {
        ChallengeMetricType.FOCUS_MINUTES,
        ChallengeMetricType.WEEKLY_FOCUS_GOAL -> "m"
        ChallengeMetricType.USAGE_REDUCTION_PERCENT -> "%"
        else -> ""
    }

    BotanicalCard(
        containerColor = when {
            isDone -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            isExpired -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            else -> MaterialTheme.colorScheme.surface
        },
        modifier = Modifier.testTag("challenge_card_${challenge.id}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(
                    imageVector = when {
                        isDone -> Icons.Rounded.CheckCircle
                        isExpired -> Icons.Rounded.History
                        else -> icon
                    },
                    contentDescription = when {
                        isDone -> "Completed challenge"
                        isExpired -> "Expired challenge"
                        else -> null
                    },
                    tint = when {
                        isDone -> MaterialTheme.colorScheme.primary
                        isExpired -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.secondary
                    },
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
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                if (challenge.isCustom && !challenge.rewardClaimed && onEdit != null) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.testTag("edit_challenge_${challenge.id}")
                    ) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Edit challenge", modifier = Modifier.size(20.dp))
                    }
                }
                if (challenge.isCustom && onDelete != null) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.testTag("delete_challenge_${challenge.id}")
                    ) {
                        Icon(
                            Icons.Rounded.DeleteOutline,
                            contentDescription = if (challenge.rewardClaimed) "Archive challenge" else "Delete challenge",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
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
                text = "${challenge.currentProgress}$unitSuffix / ${challenge.targetValue}$unitSuffix",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val completedTime = challenge.completedAtMs?.let {
                val fmt = DateTimeFormatter.ofPattern("MMM d, HH:mm")
                Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(fmt)
            }
            Text(
                text = when {
                    isDone && completedTime != null -> "Completed $completedTime"
                    isExpired -> "Expired • progress saved"
                    else -> "+${challenge.rewardSeeds} seeds"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (isDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
