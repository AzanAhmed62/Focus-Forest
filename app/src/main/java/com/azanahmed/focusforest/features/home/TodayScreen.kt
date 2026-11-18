package com.azanahmed.focusforest.features.home

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Forest
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.azanahmed.focusforest.domain.model.SnapshotCompleteness
import com.azanahmed.focusforest.domain.model.UsagePermissionStatus
import com.azanahmed.focusforest.platform.usage.AndroidUsageStatsGateway
import com.azanahmed.focusforest.ui.components.AvatarBadge
import com.azanahmed.focusforest.ui.components.BotanicalCard
import com.azanahmed.focusforest.ui.components.EmptyStateCard
import com.azanahmed.focusforest.ui.components.FocusTrendBarChart
import com.azanahmed.focusforest.ui.components.ForestIllustrationCanvas
import com.azanahmed.focusforest.ui.components.StatPill
import com.azanahmed.focusforest.ui.components.UsageAccessDisclosureDialog

@Composable
fun TodayScreen(
    viewModel: TodayViewModel,
    onQuickStartFocus: () -> Unit,
    onOpenChallenges: () -> Unit,
    onOpenInsights: () -> Unit,
    onOpenForest: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val showDisclosure by viewModel.showUsageDisclosureDialog.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onResumeRefresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (showDisclosure) {
        UsageAccessDisclosureDialog(
            onConfirmOpenSettings = {
                viewModel.acceptUsageDisclosureAndLaunchSettings {
                    runCatching {
                        context.startActivity(AndroidUsageStatsGateway.buildUsageAccessSettingsIntent())
                    }
                }
            },
            onDismiss = { viewModel.dismissUsageDisclosure() }
        )
    }

    if (state.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.testTag("today_loading_indicator"))
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 620.dp),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Top Greeting Bar
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AvatarBadge(avatarId = state.profile?.equippedAvatarId ?: "")
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = state.formattedDate,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = state.greeting,
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.testTag("today_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "Open Settings"
                        )
                    }
                }
            }

            // 2. Hero Daily Focus Progress Card
            item {
                val today = state.todayStats
                BotanicalCard(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Today's Focus Intention",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${today.focusMinutes} / ${today.goalMinutes} min",
                                style = MaterialTheme.typography.displayMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = when {
                                    today.goalMet -> "Daily goal reached — calm consistency."
                                    today.focusMinutes == 0 -> "Plant your first tree with a mindful session."
                                    else -> "${today.goalMinutes - today.focusMinutes} minutes remaining toward your goal."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(76.dp)) {
                            CircularProgressIndicator(
                                progress = { today.goalProgressFraction },
                                modifier = Modifier.fillMaxSize(),
                                strokeWidth = 8.dp,
                                trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "${(today.goalProgressFraction * 100).toInt()}%",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onQuickStartFocus,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("quick_start_focus_button"),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (state.activeChain != null && !state.activeChain!!.isCompleted) {
                                "Resume Pomodoro Chain"
                            } else {
                                "Start Focus Session"
                            }
                        )
                    }
                }
            }

            state.activeChain?.takeIf { !it.isCompleted }?.let { chain ->
                item {
                    BotanicalCard(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                        modifier = Modifier.clickable { onQuickStartFocus() }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Repeat, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Pomodoro Chain Active (${chain.completedFocusCount}/${chain.config.totalFocusSessions} rounds)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (chain.waitingForUserNextStep) {
                                "Waiting for you: ${chain.currentStepType.displayName} (${chain.currentStepPlannedMinutes}m). Open Focus to continue."
                            } else {
                                "Next step: ${chain.currentStepType.displayName} (${chain.currentStepPlannedMinutes}m)"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("chain_continuation_hint")
                        )
                    }
                }
            }

            // 3. First-time user welcoming tip
            if (state.isFirstTimeEmpty) {
                item {
                    EmptyStateCard(
                        icon = Icons.Rounded.Spa,
                        title = "Your forest awaits its first seed",
                        subtitle = "Complete one 25-minute Pomodoro or short custom session to plant your first tree and start your consistency streak.",
                        actionLabel = "Begin First Session",
                        onAction = onQuickStartFocus
                    )
                }
            }

            // 4. Consistency Streak & Level Pills
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatPill(
                        label = "Consistency Streak",
                        value = "${state.records.currentStreakDays} days",
                        icon = Icons.Rounded.LocalFireDepartment,
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    StatPill(
                        label = "Level ${state.profile?.currentLevel ?: 1}",
                        value = "${state.profile?.lifetimeXp ?: 0} XP",
                        icon = Icons.Rounded.AutoAwesome,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 5. Measured Android App Usage Card (Honest Permission & Data Handling)
            item {
                val usage = state.usageState
                BotanicalCard(
                    modifier = Modifier.clickable { onOpenInsights() }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.PhoneAndroid,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Android-Observed App Usage",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Text(
                            text = if (usage.permissionStatus == UsagePermissionStatus.GRANTED) "Enabled" else "Off",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (usage.permissionStatus == UsagePermissionStatus.GRANTED)
                                MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    when {
                        usage.permissionStatus != UsagePermissionStatus.GRANTED -> {
                            Text(
                                text = "Optional Usage Access is not enabled. Enable local-only permission to inspect today's foreground app habits.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = { viewModel.openUsageDisclosure() },
                                modifier = Modifier.testTag("today_enable_usage_button")
                            ) {
                                Text("Enable Usage Insights")
                            }
                        }
                        usage.queryErrorMessage != null -> {
                            Text(
                                text = usage.queryErrorMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        usage.todayCompleteness == SnapshotCompleteness.ZERO_USAGE -> {
                            Text(
                                text = "0 min observed foreground usage today (zero-usage day vs ${usage.dailyUsageBudgetMinutes}m budget).",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        usage.isDataUnavailable || usage.todaySummary == null -> {
                            Text(
                                text = "Usage Access is active, but Android has not reported usable foreground usage data yet for today.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        else -> {
                            val summary = usage.todaySummary
                            Text(
                                text = "${summary.totalObservedMinutes} min observed today (${usage.dailyUsageBudgetMinutes}m budget)",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (summary.topApps.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Top today: " + summary.topApps.take(2)
                                        .joinToString(" • ") { "${it.appName} (${it.displayDuration})" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // 6. Weekly Focus Progress Card
            item {
                BotanicalCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("This Week's Focus", style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = { viewModel.markRestDayToday() }) {
                            Icon(
                                imageVector = Icons.Rounded.SelfImprovement,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(if (state.todayStats.isRestDay) "Rest Day Marked" else "Take Rest Day")
                        }
                    }
                    FocusTrendBarChart(
                        days = state.weeklyStats,
                        dailyGoalMinutes = state.todayStats.goalMinutes
                    )
                }
            }

            // 7. Today's Highlight Challenge
            state.highlightChallenge?.let { ch ->
                item {
                    BotanicalCard(
                        modifier = Modifier.clickable { onOpenChallenges() }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Flag, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text("Today's Challenge", style = MaterialTheme.typography.titleMedium)
                            }
                            Text(
                                text = "+${ch.rewardXp} XP",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(ch.title, style = MaterialTheme.typography.titleSmall)
                        Text(
                            ch.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { ch.progressFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${ch.currentProgress} / ${ch.targetValue}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 8. Forest Growth Preview Card
            item {
                BotanicalCard(
                    modifier = Modifier.clickable { onOpenForest() }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Forest, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("Your Virtual Forest", style = MaterialTheme.typography.titleMedium)
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "Lv.${state.forestState.forestLevel} • ${state.forestState.currentStage.label}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(18.dp))
                    ) {
                        ForestIllustrationCanvas(forestState = state.forestState)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${state.forestState.totalTreesPlanted} trees planted • ${state.forestState.spendableLeafPoints} Leaf Points available",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
