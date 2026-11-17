package com.example.features.focus

import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Park
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.util.ShareFormatter
import com.example.domain.model.SessionStatus
import com.example.domain.model.SessionType
import com.example.ui.components.AchievementCelebrationDialog
import com.example.ui.components.BotanicalCard
import com.example.ui.theme.JetBrainsMonoFontFamily

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FocusScreen(
    viewModel: FocusViewModel
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var showAchievementsDialog by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onForegroundSync()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (state.showAbortConfirmDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissAbortConfirmation() },
            title = { Text("End session early?") },
            text = {
                Text("Ending before the timer finishes saves the session as interrupted without completion XP or planted seeds.")
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmAbortSession() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_abort_button")
                ) {
                    Text("End Early")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.dismissAbortConfirmation() },
                    modifier = Modifier.testTag("cancel_abort_button")
                ) {
                    Text("Keep Focusing")
                }
            }
        )
    }

    if (state.showAbortChainConfirmDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissAbortChainConfirmation() },
            title = { Text("End Pomodoro Chain?") },
            text = {
                Text("Previously completed focus rounds remain saved in your history and forest. Only the current incomplete step is cancelled.")
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmAbortChain() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_abort_chain_button")
                ) {
                    Text("End Chain")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissAbortChainConfirmation() }) {
                    Text("Continue")
                }
            }
        )
    }

    val unlockedAchievements = state.completionOutcome?.newlyUnlockedAchievements.orEmpty()
    if (showAchievementsDialog && unlockedAchievements.isNotEmpty()) {
        AchievementCelebrationDialog(
            achievements = unlockedAchievements,
            onDismiss = { showAchievementsDialog = false }
        )
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .testTag("focus_lazy_column"),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "Focus Session",
                    style = MaterialTheme.typography.headlineLarge
                )
                Text(
                    text = "Protect an intentional block of time and grow your forest.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (state.interruptedAfterRebootNotice) {
                item {
                    BotanicalCard(containerColor = MaterialTheme.colorScheme.errorContainer) {
                        Text(
                            text = "Previous session interrupted by device restart",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Your earlier timer elapsed while the phone was powered off, so it was logged as interrupted rather than awarding unverified focus XP.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        TextButton(onClick = { viewModel.dismissRebootNotice() }) {
                            Text("Dismiss")
                        }
                    }
                }
            }

            state.completionErrorMessage?.let { errorMsg ->
                item {
                    BotanicalCard(containerColor = MaterialTheme.colorScheme.errorContainer) {
                        Text(
                            text = errorMsg,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { viewModel.retryPendingCompletion() },
                            modifier = Modifier.testTag("retry_session_completion_button")
                        ) {
                            Text("Retry Save")
                        }
                    }
                }
            }

            state.activeChain?.let { chain ->
                item {
                    ActiveChainBannerCard(
                        chain = chain,
                        hasActiveTimer = state.activeTimer != null,
                        onStartNextStep = { viewModel.startNextChainStep() },
                        onAbortChain = { viewModel.requestAbortChainConfirmation() },
                        onDismissCompleted = { viewModel.dismissCompletedChainSummary() }
                    )
                }
            }

            val outcome = state.completionOutcome
            if (outcome != null && state.activeTimer == null) {
                item {
                    val isBreak = outcome.session.sessionType.isBreak
                    BotanicalCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "${outcome.session.sessionType.displayName} Completed",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = if (isBreak) {
                                "Restorative ${outcome.session.completedMinutes}-minute break finished (+0 XP — breaks restore your focus without planting trees)."
                            } else {
                                "You planted a ${outcome.session.sessionType.treeSpecies.displayName} and earned +${outcome.session.xpAwarded} XP & +${outcome.session.seedsAwarded} seeds."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        if (outcome.newlyCompletedChallenges.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Challenges completed: " + outcome.newlyCompletedChallenges.joinToString { it.title },
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        if (outcome.newlyUnlockedAchievements.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            TextButton(onClick = { showAchievementsDialog = true }) {
                                Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("View ${outcome.newlyUnlockedAchievements.size} Unlocked Achievement(s)")
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (!isBreak) {
                                OutlinedButton(
                                    onClick = {
                                        val shareText = ShareFormatter.formatSessionShareText(
                                            session = outcome.session,
                                            currentStreakDays = state.currentStreakDays,
                                            forestLevel = state.forestLevel
                                        )
                                        val intent = Intent.createChooser(
                                            ShareFormatter.buildTextShareIntent(shareText),
                                            "Share Focus Summary"
                                        )
                                        context.startActivity(intent)
                                    },
                                    modifier = Modifier.testTag("share_session_button")
                                ) {
                                    Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Share Summary")
                                }
                            }
                            TextButton(onClick = { viewModel.dismissCompletionOutcome() }) {
                                Text("Done")
                            }
                        }
                    }
                }
            }

            // Central Countdown Ring Card
            item {
                val active = state.activeTimer
                val displayRemainingMs = active?.remainingMs
                    ?: (state.plannedMinutesForSelection * 60_000L)
                val targetProgress = active?.progressFraction ?: 0f
                val animatedProgress by animateFloatAsState(
                    targetValue = targetProgress,
                    label = "timer_ring_progress"
                )
                val ringTrack = MaterialTheme.colorScheme.surfaceVariant
                val ringFill = if (active?.status == SessionStatus.PAUSED) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.primary
                }
                val displayType = active?.sessionType
                    ?: state.activeChain?.takeIf { !it.isCompleted }?.currentStepType
                    ?: if (state.isChainMode) SessionType.POMODORO else state.selectedType

                BotanicalCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Park,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = displayType.displayName,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        Box(
                            modifier = Modifier.size(204.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val strokePx = 15.dp.toPx()
                                drawArc(
                                    color = ringTrack,
                                    startAngle = -90f,
                                    sweepAngle = 360f,
                                    useCenter = false,
                                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                                )
                                drawArc(
                                    color = ringFill,
                                    startAngle = -90f,
                                    sweepAngle = 360f * animatedProgress,
                                    useCenter = false,
                                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                                )
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = formatDurationMs(displayRemainingMs),
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 38.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.testTag("timer_countdown_text")
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = when {
                                        active?.status == SessionStatus.RUNNING && active.sessionType.isBreak ->
                                            "Resting mindfully (+0 XP)"
                                        active?.status == SessionStatus.RUNNING ->
                                            "Growing ${active.sessionType.treeSpecies.displayName}"
                                        active?.status == SessionStatus.PAUSED -> "Paused"
                                        displayType.isBreak -> "Restorative pause (no tree planted)"
                                        else -> "Ready to plant ${displayType.treeSpecies.displayName}"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (!active?.intentionLabel.isNullOrBlank()) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "\"${active?.intentionLabel}\"",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(Modifier.height(18.dp))

                        if (active == null) {
                            val showStartButton = state.activeChain == null ||
                                state.activeChain?.isCompleted == true
                            if (showStartButton) {
                                Button(
                                    onClick = { viewModel.startSession() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(54.dp)
                                        .testTag("start_focus_button"),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        if (state.isChainMode) {
                                            "Start ${state.chainConfig.totalFocusSessions}-Round Chain"
                                        } else {
                                            "Begin ${state.selectedType.displayName}"
                                        }
                                    )
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                if (active.status == SessionStatus.RUNNING) {
                                    OutlinedButton(
                                        onClick = { viewModel.pauseSession() },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(52.dp)
                                            .testTag("pause_focus_button"),
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Icon(Icons.Rounded.Pause, contentDescription = null)
                                        Spacer(Modifier.width(6.dp))
                                        Text("Pause")
                                    }
                                } else {
                                    Button(
                                        onClick = { viewModel.resumeSession() },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(52.dp)
                                            .testTag("resume_focus_button"),
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                                        Spacer(Modifier.width(6.dp))
                                        Text("Resume")
                                    }
                                }

                                Button(
                                    onClick = { viewModel.requestAbortConfirmation() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(52.dp)
                                        .testTag("abort_focus_button"),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Icon(Icons.Rounded.Stop, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("End")
                                }
                            }
                        }
                    }
                }
            }

            // Ambient Soundscape card (available before & during sessions)
            item {
                AmbientSoundscapeCard(
                    selectedPreset = state.ambientPreset,
                    volume = state.ambientVolume,
                    playWithSession = state.ambientPlayWithSession,
                    isPlaying = state.isAmbientPlaying,
                    onSelectPreset = viewModel::selectAmbientPreset,
                    onVolumeChange = viewModel::updateAmbientVolume,
                    onPlayWithSessionChange = viewModel::setAmbientPlayWithSession,
                    onTogglePlayback = viewModel::toggleAmbientPlayback
                )
            }

            // Mode & Session Configuration (when idle and no active chain)
            val chainIdle = state.activeChain == null || state.activeChain?.isCompleted == true
            if (state.activeTimer == null && chainIdle) {
                item {
                    BotanicalCard {
                        Text("Focus Mode", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = !state.isChainMode,
                                onClick = { viewModel.setChainMode(false) },
                                label = { Text("Single Session") },
                                modifier = Modifier.testTag("mode_single_session")
                            )
                            FilterChip(
                                selected = state.isChainMode,
                                onClick = { viewModel.setChainMode(true) },
                                label = { Text("Pomodoro Chain") },
                                modifier = Modifier.testTag("mode_pomodoro_chain")
                            )
                        }

                        if (!state.isChainMode) {
                            Spacer(Modifier.height(12.dp))
                            Text("Session Type", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(8.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                SessionType.entries.forEach { type ->
                                    FilterChip(
                                        selected = state.selectedType == type,
                                        onClick = { viewModel.selectSessionType(type) },
                                        label = {
                                            val mins = if (type == SessionType.CUSTOM) state.customMinutes else type.defaultMinutes
                                            Text("${type.displayName} (${mins}m)")
                                        },
                                        modifier = Modifier.testTag("session_type_${type.name}")
                                    )
                                }
                            }

                            if (state.selectedType == SessionType.CUSTOM) {
                                Spacer(Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Custom duration", style = MaterialTheme.typography.labelLarge)
                                    Text(
                                        "${state.customMinutes} min",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Slider(
                                    value = state.customMinutes.toFloat(),
                                    onValueChange = { viewModel.updateCustomMinutes((it / 5f).toInt() * 5) },
                                    valueRange = 5f..180f,
                                    modifier = Modifier.testTag("custom_duration_slider")
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = state.intentionLabel,
                            onValueChange = viewModel::updateIntentionLabel,
                            label = { Text("Focus intention (optional)") },
                            placeholder = { Text("e.g. Chapter 4 notes or Inbox zero") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("session_intention_input"),
                            shape = RoundedCornerShape(16.dp)
                        )
                    }
                }

                if (state.isChainMode) {
                    item {
                        ChainSetupCard(
                            config = state.chainConfig,
                            onUpdateConfig = viewModel::updateChainConfig
                        )
                    }
                }
            }
        }
    }
}

private fun formatDurationMs(ms: Long): String {
    val totalSeconds = ((ms + 999L) / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
