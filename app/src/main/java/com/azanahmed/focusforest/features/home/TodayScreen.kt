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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Forest
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
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
import com.azanahmed.focusforest.domain.model.SessionStatus
import com.azanahmed.focusforest.domain.model.SnapshotCompleteness
import com.azanahmed.focusforest.domain.model.UsagePermissionStatus
import com.azanahmed.focusforest.platform.usage.AndroidUsageStatsGateway
import com.azanahmed.focusforest.ui.components.AvatarBadge
import com.azanahmed.focusforest.ui.components.BotanicalCard
import com.azanahmed.focusforest.ui.components.FocusTrendBarChart
import com.azanahmed.focusforest.ui.components.ForestIllustrationCanvas
import com.azanahmed.focusforest.ui.components.SectionHeader
import com.azanahmed.focusforest.ui.components.StatPill
import com.azanahmed.focusforest.ui.components.UsageAccessDisclosureDialog
import com.azanahmed.focusforest.ui.theme.ForestRadii
import com.azanahmed.focusforest.ui.theme.ForestSizes
import com.azanahmed.focusforest.ui.theme.ForestSpacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TodayScreen(
    viewModel: TodayViewModel,
    onQuickStartFocus: () -> Unit,
    onStartRoutine: (String) -> Unit,
    onOpenChallenges: () -> Unit,
    onOpenInsights: () -> Unit,
    onOpenForest: () -> Unit,
    onOpenHistory: () -> Unit,
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
                .widthIn(max = 620.dp)
                .testTag("today_lazy_column"),
            contentPadding = PaddingValues(
                horizontal = ForestSpacing.screenHorizontal,
                vertical = ForestSpacing.screenVertical
            ),
            verticalArrangement = Arrangement.spacedBy(ForestSpacing.sectionGap)
        ) {
            // 1. Greeting and date
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AvatarBadge(avatarId = state.profile?.equippedAvatarId ?: "")
                        Spacer(modifier = Modifier.width(ForestSpacing.sm))
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onOpenHistory,
                            modifier = Modifier.testTag("today_history_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.History,
                                contentDescription = "Open focus history"
                            )
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
            }

            // 2. Today's focus: the one obvious next action.
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
                            Spacer(modifier = Modifier.height(ForestSpacing.xxs))
                            Text(
                                text = "${today.focusMinutes} / ${today.goalMinutes} min",
                                style = MaterialTheme.typography.displayMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(ForestSpacing.xxs))
                            Text(
                                text = when {
                                    today.goalMet -> "Daily goal reached — calm consistency."
                                    today.focusMinutes == 0 && state.isFirstTimeEmpty ->
                                        "Your first session plants the first tree in your grove."
                                    today.focusMinutes == 0 ->
                                        "A short session is enough to begin today's grove."
                                    else ->
                                        "${today.goalMinutes - today.focusMinutes} minutes remaining toward your goal."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(ForestSizes.goalRing)
                        ) {
                            CircularProgressIndicator(
                                progress = { today.goalProgressFraction },
                                modifier = Modifier.fillMaxSize(),
                                strokeWidth = ForestSizes.progressTrack,
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

                    Spacer(modifier = Modifier.height(ForestSpacing.md))
                    Button(
                        onClick = onQuickStartFocus,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(ForestSizes.minTouchTarget)
                            .testTag("quick_start_focus_button"),
                        shape = RoundedCornerShape(ForestRadii.control)
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(ForestSpacing.xs))
                        Text(
                            when {
                                state.activeTimer != null -> "Resume Focus Session"
                                state.activeChain != null && !state.activeChain!!.isCompleted ->
                                    "Resume Pomodoro Chain"
                                else -> "Start Focus Session"
                            }
                        )
                    }
                }
            }

            // 3. Live session context, when one exists.
            state.activeTimer?.let { timer ->
                item {
                    BotanicalCard(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (timer.status == SessionStatus.PAUSED) {
                                        Icons.Rounded.Pause
                                    } else {
                                        Icons.Rounded.PlayArrow
                                    },
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(ForestSpacing.xs))
                                Text(
                                    text = if (timer.status == SessionStatus.PAUSED) {
                                        "Focus session paused"
                                    } else {
                                        "Focus session in progress"
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            TextButton(
                                onClick = onQuickStartFocus,
                                modifier = Modifier.testTag("today_resume_session_button")
                            ) {
                                Text("Open")
                            }
                        }
                        Spacer(Modifier.height(ForestSpacing.xxs))
                        Text(
                            text = "${timer.sessionType.displayName} · " +
                                "${timer.plannedDurationMs / 60_000} min planned",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val contextLabel = listOfNotNull(
                            timer.routineNameSnapshot.takeIf { it.isNotBlank() },
                            timer.categoryNameSnapshot.takeIf { it.isNotBlank() }
                        ).joinToString(" • ")
                        if (contextLabel.isNotBlank()) {
                            Text(
                                text = contextLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.testTag("today_active_session_context")
                            )
                        }
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
                            Icon(
                                Icons.Rounded.Repeat,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(ForestSpacing.xs))
                            Text(
                                text = "Pomodoro Chain Active " +
                                    "(${chain.completedFocusCount}/${chain.config.totalFocusSessions} rounds)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(Modifier.height(ForestSpacing.xxs))
                        Text(
                            text = if (chain.waitingForUserNextStep) {
                                "Waiting for you: ${chain.currentStepType.displayName} " +
                                    "(${chain.currentStepPlannedMinutes}m). Open Focus to continue."
                            } else {
                                "Next step: ${chain.currentStepType.displayName} " +
                                    "(${chain.currentStepPlannedMinutes}m)"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("chain_continuation_hint")
                        )
                    }
                }
            }

            // 4. Consistency, without competing with the primary action.
            item {
                SectionHeader(
                    title = "Your rhythm",
                    subtitle = "Steady days matter more than perfect days."
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ForestSpacing.sm)
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

            val idleForRoutines = state.activeTimer == null &&
                (state.activeChain == null || state.activeChain?.isCompleted == true)
            if (idleForRoutines && state.pinnedRoutines.isNotEmpty()) {
                item {
                    BotanicalCard {
                        Text("Pinned Routines", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Start a saved focus template in one tap.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(ForestSpacing.xs))
                        Column(verticalArrangement = Arrangement.spacedBy(ForestSpacing.xxs)) {
                            state.pinnedRoutines.forEach { routine ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = routine.name,
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Text(
                                            text = routine.snapshotLabel(),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    TextButton(
                                        onClick = { onStartRoutine(routine.id) },
                                        modifier = Modifier.testTag("today_start_routine_${routine.id}")
                                    ) {
                                        Icon(
                                            Icons.Rounded.PlayArrow,
                                            contentDescription = null,
                                            modifier = Modifier.size(ForestSizes.iconSmall)
                                        )
                                        Spacer(modifier = Modifier.width(ForestSpacing.xxs))
                                        Text("Start")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 5. Weekly intention.
            item {
                SectionHeader(
                    title = "This week",
                    subtitle = "A gentle view of the time you have protected."
                )
            }
            item {
                val weeklyFraction = if (state.weeklyGoalMinutes <= 0) {
                    0f
                } else {
                    (state.weeklyFocusMinutes.toFloat() / state.weeklyGoalMinutes.toFloat())
                        .coerceIn(0f, 1f)
                }
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
                                modifier = Modifier.size(ForestSizes.iconSmall)
                            )
                            Spacer(modifier = Modifier.width(ForestSpacing.xxs))
                            Text(if (state.todayStats.isRestDay) "Rest Day Marked" else "Take Rest Day")
                        }
                    }
                    FocusTrendBarChart(
                        days = state.weeklyStats,
                        dailyGoalMinutes = state.todayStats.goalMinutes
                    )
                    Spacer(modifier = Modifier.height(ForestSpacing.sm))
                    LinearProgressIndicator(
                        progress = { weeklyFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(ForestSizes.progressTrack)
                            .clip(RoundedCornerShape(ForestSizes.progressTrack / 2))
                            .testTag("weekly_goal_progress")
                    )
                    Spacer(modifier = Modifier.height(ForestSpacing.xxs))
                    Text(
                        text = if (state.weeklyGoalMinutes <= 0) {
                            "${state.weeklyFocusMinutes} min this week • no weekly target set"
                        } else {
                            "${state.weeklyFocusMinutes} / ${state.weeklyGoalMinutes} min • " +
                                "${(weeklyFraction * 100).toInt()}% of your weekly intention"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("weekly_goal_summary")
                    )
                }
            }

            if (state.recentSessions.isNotEmpty()) {
                item {
                    BotanicalCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Rounded.History,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(ForestSpacing.xs))
                                Text("Recent Activity", style = MaterialTheme.typography.titleMedium)
                            }
                            TextButton(
                                onClick = onOpenHistory,
                                modifier = Modifier.testTag("today_recent_history_button")
                            ) {
                                Text("View all")
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(ForestSpacing.xs)) {
                            state.recentSessions.take(3).forEach { session ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = when (session.status) {
                                            SessionStatus.COMPLETED -> Icons.Rounded.CheckCircle
                                            SessionStatus.PAUSED -> Icons.Rounded.Pause
                                            else -> Icons.Rounded.Stop
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(ForestSizes.iconSmall),
                                        tint = if (session.status == SessionStatus.COMPLETED) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(ForestSpacing.xs))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${session.sessionType.displayName} · " +
                                                "${session.completedMinutes} min",
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        val contextLabel = listOfNotNull(
                                            session.routineNameSnapshot.takeIf { it.isNotBlank() },
                                            session.categoryNameSnapshot.takeIf { it.isNotBlank() },
                                            formatSessionDate(session.localDate)
                                        ).joinToString(" • ")
                                        Text(
                                            text = contextLabel,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = when (session.status) {
                                            SessionStatus.COMPLETED -> "Completed"
                                            SessionStatus.ABORTED -> "Stopped"
                                            SessionStatus.INTERRUPTED -> "Interrupted"
                                            else -> session.status.name.lowercase()
                                                .replaceFirstChar { it.uppercase() }
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

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
                                Icon(
                                    Icons.Rounded.Flag,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(ForestSpacing.xs))
                                Text("Today's Challenge", style = MaterialTheme.typography.titleMedium)
                            }
                            Text(
                                text = "+${ch.rewardXp} XP",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                        Spacer(modifier = Modifier.height(ForestSpacing.xs))
                        Text(ch.title, style = MaterialTheme.typography.titleSmall)
                        Text(
                            ch.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(ForestSpacing.xs))
                        LinearProgressIndicator(
                            progress = { ch.progressFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(ForestSizes.progressTrack)
                                .clip(RoundedCornerShape(ForestSizes.progressTrack / 2))
                        )
                        Spacer(modifier = Modifier.height(ForestSpacing.xxs))
                        Text(
                            text = "${ch.currentProgress} / ${ch.targetValue}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 6. A small, honest window into the grove.
            item {
                SectionHeader(
                    title = "Your forest",
                    subtitle = "A living record of the time you have protected.",
                    actionLabel = "Visit",
                    onAction = onOpenForest
                )
            }
            item {
                val forest = state.forestState
                val forestSummary = "Level ${forest.forestLevel}, ${forest.currentStage.label}. " +
                    "${forest.totalTreesPlanted} trees planted. " +
                    "${forest.spendableLeafPoints} Leaf Points available. " +
                    "Open your forest to see the grove."
                BotanicalCard(
                    modifier = Modifier
                        .clickable { onOpenForest() }
                        .testTag("today_forest_preview")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.Forest,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(ForestSpacing.xs))
                            Text("Your Virtual Forest", style = MaterialTheme.typography.titleMedium)
                        }
                        Surface(
                            shape = RoundedCornerShape(ForestRadii.chip),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "Lv.${forest.forestLevel} • ${forest.currentStage.label}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(
                                    horizontal = ForestSpacing.xs,
                                    vertical = ForestSpacing.xxs
                                )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(ForestSpacing.sm))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(ForestSizes.forestPreviewHeight)
                            .clip(RoundedCornerShape(ForestRadii.control))
                    ) {
                        ForestIllustrationCanvas(
                            forestState = forest,
                            contentDescription = forestSummary,
                            motionEnabled = false
                        )
                    }
                    Spacer(modifier = Modifier.height(ForestSpacing.xs))
                    Text(
                        text = "${forest.totalTreesPlanted} trees planted • " +
                            "${forest.spendableLeafPoints} Leaf Points available",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 7. Optional, permission-gated usage insight stays last.
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
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(ForestSpacing.xs))
                            Text(
                                text = "Optional app usage",
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                        Text(
                            text = if (usage.permissionStatus == UsagePermissionStatus.GRANTED) {
                                "Enabled"
                            } else {
                                "Off"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(ForestSpacing.xs))
                    Text(
                        text = "Focus Forest works fully without Usage Access. " +
                            "This local-only signal is never required to focus or grow your forest.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(ForestSpacing.xs))

                    when {
                        usage.permissionStatus != UsagePermissionStatus.GRANTED -> {
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
                                text = "0 min observed foreground usage today " +
                                    "(zero-usage day vs ${usage.dailyUsageBudgetMinutes}m budget).",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        usage.isDataUnavailable || usage.todaySummary == null -> {
                            Text(
                                text = "Usage Access is active, but Android has not reported " +
                                    "usable foreground usage data yet for today.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        else -> {
                            val summary = usage.todaySummary
                            Text(
                                text = "${summary.totalObservedMinutes} min observed today " +
                                    "(${usage.dailyUsageBudgetMinutes}m budget)",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (summary.topApps.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(ForestSpacing.xxs))
                                Text(
                                    text = "Top today: " + summary.topApps.take(2)
                                        .joinToString(" • ") {
                                            "${it.appName} (${it.displayDuration})"
                                        },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatSessionDate(localDate: String): String = runCatching {
    LocalDate.parse(localDate).format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))
}.getOrDefault(localDate)
