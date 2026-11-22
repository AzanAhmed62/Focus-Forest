package com.azanahmed.focusforest.features.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Forest
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.azanahmed.focusforest.R
import com.azanahmed.focusforest.domain.model.SessionType
import com.azanahmed.focusforest.domain.model.UserIntention
import com.azanahmed.focusforest.ui.components.BotanicalCard
import com.azanahmed.focusforest.ui.theme.ForestRadii
import com.azanahmed.focusforest.ui.theme.ForestSizes
import com.azanahmed.focusforest.ui.theme.ForestSpacing

private const val PAGE_COUNT = 3

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onCompleted: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    BackHandler(enabled = state.currentPage > 0) {
        viewModel.previousPage()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 600.dp)
                    .padding(
                        horizontal = ForestSpacing.screenHorizontal,
                        vertical = ForestSpacing.screenVertical
                    )
            ) {
                OnboardingTopBar(
                    currentPage = state.currentPage,
                    canSkip = !state.isSaving,
                    onBack = viewModel::previousPage,
                    onSkip = { viewModel.skipOnboarding(onCompleted) }
                )

                Spacer(modifier = Modifier.height(ForestSpacing.sm))

                AnimatedContent(
                    targetState = state.currentPage,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    label = "onboarding_page"
                ) { page ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(ForestSpacing.md)
                    ) {
                        when (page) {
                            0 -> OnboardingWelcomePage()
                            1 -> OnboardingRhythmPage(
                                state = state,
                                onNameChange = viewModel::updateDisplayName,
                                onGoalChange = viewModel::updateDailyGoal,
                                onIntentionSelect = viewModel::selectIntention
                            )
                            else -> OnboardingFirstSessionPage(
                                state = state,
                                onSessionTypeSelect = viewModel::selectDefaultSession,
                                onMinutesChange = viewModel::updateDefaultSessionMinutes
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(ForestSpacing.sm))

                if (state.currentPage < PAGE_COUNT - 1) {
                    Button(
                        onClick = viewModel::nextPage,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(ForestSizes.minTouchTarget + 6.dp)
                            .testTag("onboarding_next_button"),
                        shape = RoundedCornerShape(ForestRadii.control)
                    ) {
                        Text(
                            text = if (state.currentPage == 0) "Begin Your Forest" else "Continue",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Spacer(modifier = Modifier.width(ForestSpacing.xs))
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null
                        )
                    }
                } else {
                    Button(
                        onClick = { viewModel.finishOnboarding(onCompleted) },
                        enabled = !state.isSaving,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(ForestSizes.minTouchTarget + 6.dp)
                            .testTag("onboarding_finish_button"),
                        shape = RoundedCornerShape(ForestRadii.control)
                    ) {
                        Text(
                            text = if (state.isSaving) {
                                "Preparing Sanctuary..."
                            } else {
                                "Enter Focus Forest"
                            },
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingTopBar(
    currentPage: Int,
    canSkip: Boolean,
    onBack: () -> Unit,
    onSkip: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        if (currentPage > 0) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("onboarding_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Previous step"
                )
            }
        } else {
            Spacer(modifier = Modifier.size(ForestSizes.minTouchTarget))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(ForestSpacing.xs)) {
            repeat(PAGE_COUNT) { idx ->
                val selected = idx == currentPage
                Box(
                    modifier = Modifier
                        .height(8.dp)
                        .width(if (selected) 26.dp else 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                            }
                        )
                )
            }
        }

        TextButton(
            onClick = onSkip,
            enabled = canSkip,
            modifier = Modifier.testTag("onboarding_skip_button")
        ) {
            Text("Skip")
        }
    }
}

@Composable
private fun OnboardingWelcomePage() {
    Image(
        painter = painterResource(id = R.drawable.img_onboarding_forest),
        contentDescription = "Sunlit botanical forest illustration",
        modifier = Modifier
            .fillMaxWidth()
            .height(190.dp)
            .clip(RoundedCornerShape(ForestRadii.hero)),
        contentScale = ContentScale.Crop
    )

    Text(
        text = "Welcome to Focus Forest",
        style = MaterialTheme.typography.displayMedium,
        color = MaterialTheme.colorScheme.onBackground
    )
    Text(
        text = "Grow your focus. Reclaim your time.",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary
    )
    Text(
        text = "A calm, private place to protect your attention. " +
            "No account, no feed, and nothing leaves your phone.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    BotanicalCard {
        OnboardingPoint(
            icon = Icons.Rounded.Timer,
            title = "Focus without pressure",
            body = "Start a session when you are ready. Pause, resume, and stop without penalty."
        )
        Spacer(modifier = Modifier.height(ForestSpacing.sm))
        OnboardingPoint(
            icon = Icons.Rounded.Forest,
            title = "Plant something real",
            body = "Each completed focus session plants one tree in a grove that is yours alone."
        )
        Spacer(modifier = Modifier.height(ForestSpacing.sm))
        OnboardingPoint(
            icon = Icons.Rounded.Lock,
            title = "Stay private",
            body = "Sessions, goals, and history stay in a local database on this device."
        )
    }
}

@Composable
private fun OnboardingPoint(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(ForestSpacing.sm))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OnboardingRhythmPage(
    state: OnboardingUiState,
    onNameChange: (String) -> Unit,
    onGoalChange: (Int) -> Unit,
    onIntentionSelect: (UserIntention) -> Unit
) {
    Text(
        text = "Shape your rhythm",
        style = MaterialTheme.typography.headlineLarge
    )
    Text(
        text = "Everything is saved locally on your phone. " +
            "You can change these choices later in Settings.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    OutlinedTextField(
        value = state.displayName,
        onValueChange = onNameChange,
        label = { Text("Display name (optional)") },
        placeholder = { Text("e.g. Alex") },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("onboarding_name_input"),
        shape = RoundedCornerShape(ForestRadii.control)
    )

    BotanicalCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Daily focus goal", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "${state.dailyGoalMinutes} min",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
        Slider(
            value = state.dailyGoalMinutes.toFloat(),
            onValueChange = { onGoalChange((it / 5f).toInt() * 5) },
            valueRange = 15f..180f,
            modifier = Modifier.testTag("onboarding_goal_slider")
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(ForestSpacing.xs)) {
            listOf(25, 50, 75, 100, 120).forEach { preset ->
                FilterChip(
                    selected = state.dailyGoalMinutes == preset,
                    onClick = { onGoalChange(preset) },
                    label = { Text("${preset}m") }
                )
            }
        }
    }

    Text(
        text = "Primary intention",
        style = MaterialTheme.typography.titleMedium
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(ForestSpacing.xs),
        verticalArrangement = Arrangement.spacedBy(ForestSpacing.xxs)
    ) {
        UserIntention.entries.forEach { intention ->
            FilterChip(
                selected = state.selectedIntention == intention,
                onClick = { onIntentionSelect(intention) },
                label = { Text(intention.label) },
                modifier = Modifier.testTag("intention_${intention.name}")
            )
        }
    }
    Text(
        text = state.selectedIntention.subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OnboardingFirstSessionPage(
    state: OnboardingUiState,
    onSessionTypeSelect: (SessionType) -> Unit,
    onMinutesChange: (Int) -> Unit
) {
    Text(
        text = "Your first session",
        style = MaterialTheme.typography.headlineLarge
    )
    Text(
        text = "Choose what opens when you tap Start Focus. " +
            "Pomodoro plants a pine, Deep Work plants an oak, and Custom Focus plants a birch.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    BotanicalCard {
        Text("Default focus session", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(ForestSpacing.xs))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(ForestSpacing.xs)) {
            listOf(SessionType.POMODORO, SessionType.DEEP_WORK, SessionType.CUSTOM).forEach { type ->
                FilterChip(
                    selected = state.defaultSessionType == type,
                    onClick = { onSessionTypeSelect(type) },
                    label = { Text("${type.displayName} (${type.defaultMinutes}m)") },
                    modifier = Modifier.testTag("onboarding_session_${type.name}")
                )
            }
        }
        if (state.defaultSessionType == SessionType.CUSTOM) {
            Spacer(modifier = Modifier.height(ForestSpacing.xs))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Custom length", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = "${state.defaultSessionMinutes} min",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            Slider(
                value = state.defaultSessionMinutes.toFloat(),
                onValueChange = { onMinutesChange((it / 5f).toInt() * 5) },
                valueRange = 10f..90f,
                modifier = Modifier.testTag("onboarding_custom_minutes_slider")
            )
        }
        Spacer(modifier = Modifier.height(ForestSpacing.xxs))
        Text(
            text = "This selection only sets the starting point. " +
                "You can choose another session type every time you focus.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    BotanicalCard {
        SummaryRow("Greeting Name", state.displayName.ifBlank { "Forest Explorer" })
        Spacer(modifier = Modifier.height(ForestSpacing.xs))
        SummaryRow("Daily Focus Goal", "${state.dailyGoalMinutes} minutes / day")
        Spacer(modifier = Modifier.height(ForestSpacing.xs))
        SummaryRow("Main Intention", state.selectedIntention.label)
        Spacer(modifier = Modifier.height(ForestSpacing.xs))
        SummaryRow(
            "Default Session",
            "${state.defaultSessionType.displayName} · ${state.plannedDefaultMinutes} min"
        )
    }

    BotanicalCard(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.Insights,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.width(ForestSpacing.xs))
            Text(
                text = "Optional usage insights",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Spacer(modifier = Modifier.height(ForestSpacing.xxs))
        Text(
            text = "Android Usage Access is never required. If you want a local-only view of " +
                "foreground app habits, you can enable it later in Settings.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }

    Text(
        text = "Celebrate consistency over perfection. Even one short session plants a seed, " +
            "and rest days are part of a sustainable rhythm.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = ForestSpacing.xs)
    )
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}
