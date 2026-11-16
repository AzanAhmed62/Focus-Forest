package com.example.features.onboarding

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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Forest
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.model.UsagePermissionStatus
import com.example.domain.model.UserIntention
import com.example.platform.usage.AndroidUsageStatsGateway
import com.example.ui.components.BotanicalCard
import com.example.ui.components.UsageAccessDisclosureDialog

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onCompleted: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshUsagePermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BackHandler(enabled = state.currentPage > 0) {
        viewModel.previousPage()
    }

    if (state.showDisclosureDialog) {
        UsageAccessDisclosureDialog(
            onConfirmOpenSettings = {
                viewModel.confirmUsageDisclosureAndLaunchSettings {
                    runCatching {
                        context.startActivity(AndroidUsageStatsGateway.buildUsageAccessSettingsIntent())
                    }
                }
            },
            onDismiss = { viewModel.dismissDisclosureDialog() }
        )
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
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                // Top step header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (state.currentPage > 0) {
                        IconButton(
                            onClick = { viewModel.previousPage() },
                            modifier = Modifier.testTag("onboarding_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Previous step"
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.size(48.dp))
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(4) { idx ->
                            val selected = idx == state.currentPage
                            Box(
                                modifier = Modifier
                                    .height(8.dp)
                                    .width(if (selected) 26.dp else 8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                                    )
                            )
                        }
                    }

                    Text(
                        text = "${state.currentPage + 1} / 4",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

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
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        when (page) {
                            0 -> OnboardingWelcomePage()
                            1 -> OnboardingGoalsPage(
                                state = state,
                                onNameChange = viewModel::updateDisplayName,
                                onGoalChange = viewModel::updateDailyGoal,
                                onIntentionSelect = viewModel::selectIntention
                            )
                            2 -> OnboardingUsageAccessPage(
                                permissionStatus = state.usagePermissionStatus,
                                onReviewAndEnable = { viewModel.openDisclosureDialog() },
                                onSkip = { viewModel.nextPage() }
                            )
                            else -> OnboardingSummaryPage(state = state)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (state.currentPage < 3) {
                        Button(
                            onClick = { viewModel.nextPage() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("onboarding_next_button"),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(
                                text = if (state.currentPage == 0) "Begin Your Forest" else "Continue",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Spacer(modifier = Modifier.width(8.dp))
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
                                .height(54.dp)
                                .testTag("onboarding_finish_button"),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(
                                text = if (state.isSaving) "Preparing Sanctuary..." else "Enter Focus Forest",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
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
            .height(210.dp)
            .clip(RoundedCornerShape(24.dp)),
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
        text = "A calm, private digital wellness space designed around three steady steps:",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    BotanicalCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Insights, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("1. Measure Honestly", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Observe focus sessions and optional Android foreground usage locally on your device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("2. Challenge Gently", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Build achievable daily rhythms without guilt-tripping or harsh streak resets.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Forest, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("3. Grow Your Forest", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Every completed focus block plants a new tree and unlocks botanical rewards.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OnboardingGoalsPage(
    state: OnboardingUiState,
    onNameChange: (String) -> Unit,
    onGoalChange: (Int) -> Unit,
    onIntentionSelect: (UserIntention) -> Unit
) {
    Text(
        text = "Shape your daily rhythm",
        style = MaterialTheme.typography.headlineLarge
    )
    Text(
        text = "Everything is saved locally on your phone — no account or internet connection required.",
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
        shape = RoundedCornerShape(16.dp)
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
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        UserIntention.entries.forEach { intention ->
            val selected = state.selectedIntention == intention
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onIntentionSelect(intention) }
                    .testTag("intention_${intention.name}"),
                shape = RoundedCornerShape(18.dp),
                color = if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = intention.label,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = intention.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingUsageAccessPage(
    permissionStatus: UsagePermissionStatus,
    onReviewAndEnable: () -> Unit,
    onSkip: () -> Unit
) {
    Text(
        text = "Digital habits & privacy",
        style = MaterialTheme.typography.headlineLarge
    )
    Text(
        text = "Focus Forest offers optional Android Usage Access so you can understand foreground app habits alongside your focus sessions.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    BotanicalCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Text("Local-Only Privacy Commitment", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = "• What is accessed: Daily Android-observed foreground app usage totals for launchable apps you choose.\n" +
                "• Why it is used: To display honest usage summaries, 7-day baselines, and optional reduction challenges.\n" +
                "• Where it stays: Strictly inside this device's local database. Nothing is uploaded, sold, or shared.\n" +
                "• Your control: Completely optional. You can skip now, disable monitoring, or delete all usage snapshots anytime.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (permissionStatus == UsagePermissionStatus.GRANTED) {
        BotanicalCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Usage Access is enabled on this device.",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    } else {
        OutlinedButton(
            onClick = onReviewAndEnable,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("onboarding_enable_usage_button"),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Review Disclosure & Enable Access")
        }
        TextButton(
            onClick = onSkip,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("onboarding_skip_usage_button")
        ) {
            Text("Skip for now — enable anytime in Settings")
        }
    }
}

@Composable
private fun OnboardingSummaryPage(state: OnboardingUiState) {
    Text(
        text = "Your sanctuary is ready",
        style = MaterialTheme.typography.headlineLarge
    )
    Text(
        text = "Here is your starting setup. You can adjust any goal or privacy preference later.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    BotanicalCard {
        SummaryRow("Greeting Name", state.displayName.ifBlank { "Forest Explorer" })
        Spacer(Modifier.height(10.dp))
        SummaryRow("Daily Focus Goal", "${state.dailyGoalMinutes} minutes / day")
        Spacer(Modifier.height(10.dp))
        SummaryRow("Main Intention", state.selectedIntention.label)
        Spacer(Modifier.height(10.dp))
        SummaryRow(
            "Android Usage Access",
            if (state.usagePermissionStatus == UsagePermissionStatus.GRANTED) "Enabled (local-only)" else "Skipped for now"
        )
    }

    BotanicalCard(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)) {
        Text(
            text = "Celebrate consistency over perfection",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Even one short session plants a seed in your grove. Take rest days whenever you need them.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}
