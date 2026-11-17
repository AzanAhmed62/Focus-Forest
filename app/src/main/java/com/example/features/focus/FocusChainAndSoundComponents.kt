package com.example.features.focus

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.ActiveChainState
import com.example.domain.model.AmbientSoundPreset
import com.example.domain.model.ChainConfig
import com.example.ui.components.BotanicalCard

@Composable
fun ChainSetupCard(
    config: ChainConfig,
    onUpdateConfig: ((ChainConfig) -> ChainConfig) -> Unit
) {
    BotanicalCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Repeat, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text("Pomodoro Chain Blueprint", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(10.dp))

        IntStepperRow(
            label = "Focus rounds",
            valueText = "${config.totalFocusSessions} sessions",
            canDecrement = config.totalFocusSessions > 1,
            canIncrement = config.totalFocusSessions < 8,
            onDecrement = { onUpdateConfig { it.copy(totalFocusSessions = it.totalFocusSessions - 1) } },
            onIncrement = { onUpdateConfig { it.copy(totalFocusSessions = it.totalFocusSessions + 1) } },
            testTagPrefix = "chain_rounds"
        )
        IntStepperRow(
            label = "Focus duration",
            valueText = "${config.focusMinutes} min",
            canDecrement = config.focusMinutes > 5,
            canIncrement = config.focusMinutes < 90,
            onDecrement = { onUpdateConfig { it.copy(focusMinutes = it.focusMinutes - 5) } },
            onIncrement = { onUpdateConfig { it.copy(focusMinutes = it.focusMinutes + 5) } },
            testTagPrefix = "chain_focus_min"
        )
        IntStepperRow(
            label = "Short break",
            valueText = "${config.shortBreakMinutes} min",
            canDecrement = config.shortBreakMinutes > 1,
            canIncrement = config.shortBreakMinutes < 30,
            onDecrement = { onUpdateConfig { it.copy(shortBreakMinutes = it.shortBreakMinutes - 1) } },
            onIncrement = { onUpdateConfig { it.copy(shortBreakMinutes = it.shortBreakMinutes + 1) } },
            testTagPrefix = "chain_short_break"
        )
        IntStepperRow(
            label = "Long break",
            valueText = "${config.longBreakMinutes} min",
            canDecrement = config.longBreakMinutes > 5,
            canIncrement = config.longBreakMinutes < 45,
            onDecrement = { onUpdateConfig { it.copy(longBreakMinutes = it.longBreakMinutes - 5) } },
            onIncrement = { onUpdateConfig { it.copy(longBreakMinutes = it.longBreakMinutes + 5) } },
            testTagPrefix = "chain_long_break"
        )
        IntStepperRow(
            label = "Long break every",
            valueText = "${config.longBreakFrequency} rounds",
            canDecrement = config.longBreakFrequency > 2,
            canIncrement = config.longBreakFrequency < 8,
            onDecrement = { onUpdateConfig { it.copy(longBreakFrequency = it.longBreakFrequency - 1) } },
            onIncrement = { onUpdateConfig { it.copy(longBreakFrequency = it.longBreakFrequency + 1) } },
            testTagPrefix = "chain_long_freq"
        )

        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Auto-start next phase", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = "Automatically begin breaks and next focus rounds when a phase ends.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = config.autoTransition,
                onCheckedChange = { checked -> onUpdateConfig { it.copy(autoTransition = checked) } },
                modifier = Modifier.testTag("chain_auto_transition_switch")
            )
        }
    }
}

@Composable
fun ActiveChainBannerCard(
    chain: ActiveChainState,
    hasActiveTimer: Boolean,
    onStartNextStep: () -> Unit,
    onAbortChain: () -> Unit,
    onDismissCompleted: () -> Unit
) {
    if (chain.isCompleted) {
        BotanicalCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Pomodoro Chain Complete",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Completed ${chain.completedFocusCount} focus round(s) (${chain.totalFocusedMinutes}m) and ${chain.completedBreakCount} break(s). Earned +${chain.totalEarnedXp} XP & +${chain.totalEarnedSeeds} seeds.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onDismissCompleted,
                modifier = Modifier.testTag("dismiss_completed_chain_button")
            ) {
                Text("Finish Chain")
            }
        }
        return
    }

    BotanicalCard(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Pomodoro Chain • Round ${(chain.completedFocusCount + if (chain.currentStepType.isBreak) 0 else 1).coerceAtMost(chain.config.totalFocusSessions)} of ${chain.config.totalFocusSessions}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Current: ${chain.currentStepType.displayName} (${chain.currentStepPlannedMinutes}m)" +
                        (chain.upcomingStepType?.let { " • Next: ${it.displayName}" } ?: " • Final round"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Text(
                    text = "+${chain.totalEarnedXp} XP",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { chain.overallProgressFraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!hasActiveTimer && chain.waitingForUserNextStep) {
                Button(
                    onClick = onStartNextStep,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("start_next_chain_step_button")
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Start ${chain.currentStepType.displayName}")
                }
            }
            OutlinedButton(
                onClick = onAbortChain,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.testTag("abort_chain_button")
            ) {
                Text("End Chain")
            }
        }
    }
}

@Composable
fun AmbientSoundscapeCard(
    selectedPreset: AmbientSoundPreset,
    volume: Float,
    playWithSession: Boolean,
    isPlaying: Boolean,
    onSelectPreset: (AmbientSoundPreset) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onPlayWithSessionChange: (Boolean) -> Unit,
    onTogglePlayback: () -> Unit
) {
    BotanicalCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("Ambient Soundscape", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = if (isPlaying) "Playing ${selectedPreset.displayName} (100% offline DSP)"
                        else selectedPreset.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(
                onClick = onTogglePlayback,
                modifier = Modifier.testTag("ambient_play_toggle_button")
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Pause ambient sound" else "Preview ambient sound"
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AmbientSoundPreset.entries.forEach { preset ->
                FilterChip(
                    selected = selectedPreset == preset,
                    onClick = { onSelectPreset(preset) },
                    label = { Text(preset.displayName) },
                    modifier = Modifier.testTag("ambient_chip_${preset.name}")
                )
            }
        }

        if (selectedPreset != AmbientSoundPreset.NONE) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.VolumeUp,
                    contentDescription = "Ambient volume",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                Slider(
                    value = volume,
                    onValueChange = onVolumeChange,
                    valueRange = 0f..1f,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ambient_volume_slider")
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Play automatically during focus sessions", style = MaterialTheme.typography.bodySmall)
                Switch(
                    checked = playWithSession,
                    onCheckedChange = onPlayWithSessionChange,
                    modifier = Modifier.testTag("ambient_sync_switch")
                )
            }
        }
    }
}

@Composable
private fun IntStepperRow(
    label: String,
    valueText: String,
    canDecrement: Boolean,
    canIncrement: Boolean,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    testTagPrefix: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = onDecrement,
                enabled = canDecrement,
                modifier = Modifier.testTag("${testTagPrefix}_dec")
            ) {
                Text("−")
            }
            Text(
                text = valueText,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            TextButton(
                onClick = onIncrement,
                enabled = canIncrement,
                modifier = Modifier.testTag("${testTagPrefix}_inc")
            ) {
                Text("+")
            }
        }
    }
}
