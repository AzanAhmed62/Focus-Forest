package com.azanahmed.focusforest.features.settings

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.azanahmed.focusforest.BuildConfig
import com.azanahmed.focusforest.domain.model.AmbientSoundPreset
import com.azanahmed.focusforest.domain.model.SessionType
import com.azanahmed.focusforest.domain.model.ThemePreference
import com.azanahmed.focusforest.platform.usage.AndroidUsageStatsGateway
import com.azanahmed.focusforest.ui.components.BotanicalCard
import com.azanahmed.focusforest.ui.components.SectionHeader
import com.azanahmed.focusforest.ui.components.UsageAccessDisclosureDialog
import com.azanahmed.focusforest.ui.theme.ForestRadii
import com.azanahmed.focusforest.ui.theme.ForestSizes
import com.azanahmed.focusforest.ui.theme.ForestSpacing

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onDataDeletedResetToOnboarding: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }

    var editableName by remember(state.displayName) { mutableStateOf(state.displayName) }
    var editableGoal by remember(state.dailyGoalMinutes) { mutableIntStateOf(state.dailyGoalMinutes) }
    var editableWeeklyGoal by remember(state.weeklyGoalMinutes) {
        mutableIntStateOf(state.weeklyGoalMinutes)
    }
    var editableBudget by remember(state.dailyUsageBudgetMinutes) {
        mutableIntStateOf(state.dailyUsageBudgetMinutes)
    }
    var editableVolume by remember(state.ambientVolume) {
        mutableFloatStateOf(state.ambientVolume)
    }

    val notificationPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.refreshPermissions()
        if (granted) {
            viewModel.updateNotificationPrefs(
                state.notificationPreferences.copy(notificationsEnabled = true)
            )
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        viewModel.shareIntentEvents.collect { intent ->
            context.startActivity(Intent.createChooser(intent, "Export Focus Forest CSV"))
        }
    }
    LaunchedEffect(Unit) {
        viewModel.toastEvents.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    if (state.showUsageDisclosureDialog) {
        UsageAccessDisclosureDialog(
            onConfirmOpenSettings = {
                viewModel.confirmUsageDisclosure {
                    runCatching {
                        context.startActivity(AndroidUsageStatsGateway.buildUsageAccessSettingsIntent())
                    }
                }
            },
            onDismiss = { viewModel.dismissUsageDisclosure() }
        )
    }

    if (state.showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeleteAllDialog() },
            title = { Text("Delete all local data?") },
            text = {
                Text(
                    "This permanently erases all focus sessions, streaks, forest trees, " +
                        "challenges, shop items, scheduled reminders, and usage snapshots " +
                        "from this device. This cannot be undone."
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmDeleteAllData(onDataDeletedResetToOnboarding) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.testTag("confirm_delete_all_button")
                ) {
                    Text("Delete Everything")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDeleteAllDialog() }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("settings_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
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
                    .testTag("settings_lazy_column"),
                contentPadding = PaddingValues(
                    horizontal = ForestSpacing.screenHorizontal,
                    vertical = ForestSpacing.screenVertical
                ),
                verticalArrangement = Arrangement.spacedBy(ForestSpacing.sectionGap)
            ) {
                // 1. Appearance
                item {
                    SectionHeader(
                        title = "Appearance",
                        subtitle = "Choose the palette that feels calm to you."
                    )
                }
                item {
                    BotanicalCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.Palette,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(ForestSpacing.xs))
                            Text("Theme", style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(modifier = Modifier.height(ForestSpacing.xs))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(ForestSpacing.xs)) {
                            ThemePreference.entries.forEach { pref ->
                                FilterChip(
                                    selected = state.themePreference == pref,
                                    onClick = { viewModel.setThemePreference(pref) },
                                    label = { Text(pref.label) },
                                    modifier = Modifier.testTag("theme_chip_${pref.name}")
                                )
                            }
                        }
                    }
                }

                // 2. Focus defaults
                item {
                    SectionHeader(
                        title = "Focus",
                        subtitle = "The starting session on the Focus screen. " +
                            "Any running timer keeps its own settings."
                    )
                }
                item {
                    BotanicalCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.Timer,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(ForestSpacing.xs))
                            Text("Default session", style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(modifier = Modifier.height(ForestSpacing.xs))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(ForestSpacing.xs)) {
                            listOf(
                                SessionType.POMODORO,
                                SessionType.DEEP_WORK,
                                SessionType.CUSTOM
                            ).forEach { type ->
                                FilterChip(
                                    selected = state.defaultSessionType == type,
                                    onClick = {
                                        viewModel.setDefaultSession(
                                            type = type,
                                            minutes = if (type == SessionType.CUSTOM) {
                                                state.defaultSessionMinutes
                                            } else {
                                                type.defaultMinutes
                                            }
                                        )
                                    },
                                    label = {
                                        Text("${type.displayName} (${type.defaultMinutes}m)")
                                    },
                                    modifier = Modifier.testTag(
                                        "settings_default_session_${type.name}"
                                    )
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
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Slider(
                                value = state.defaultSessionMinutes.toFloat(),
                                onValueChange = {
                                    viewModel.setDefaultSession(
                                        SessionType.CUSTOM,
                                        (it / 5f).toInt() * 5
                                    )
                                },
                                valueRange = 10f..90f,
                                modifier = Modifier.testTag("settings_custom_minutes_slider")
                            )
                        } else {
                            Spacer(modifier = Modifier.height(ForestSpacing.xxs))
                            Text(
                                text = "${state.defaultSessionType.defaultMinutes} minutes, " +
                                    "planting a ${state.defaultSessionType.treeSpecies.displayName}.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // 3. Profile and goals
                item {
                    SectionHeader(
                        title = "Goals",
                        subtitle = "Targets are intentions, never debts."
                    )
                }
                item {
                    BotanicalCard {
                        OutlinedTextField(
                            value = editableName,
                            onValueChange = { editableName = it.take(40) },
                            label = { Text("Display name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(ForestRadii.control)
                        )
                        Spacer(modifier = Modifier.height(ForestSpacing.sm))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Daily focus goal", style = MaterialTheme.typography.bodyMedium)
                            Text("${editableGoal} min", style = MaterialTheme.typography.titleSmall)
                        }
                        Slider(
                            value = editableGoal.toFloat(),
                            onValueChange = { editableGoal = (it / 5f).toInt() * 5 },
                            valueRange = 15f..180f
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Weekly focus goal", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = if (editableWeeklyGoal <= 0) {
                                    "No target"
                                } else {
                                    "$editableWeeklyGoal min"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.testTag("weekly_goal_value")
                            )
                        }
                        Slider(
                            value = editableWeeklyGoal.toFloat(),
                            onValueChange = { editableWeeklyGoal = (it / 60f).toInt() * 60 },
                            valueRange = 0f..2520f,
                            steps = 41,
                            modifier = Modifier.testTag("weekly_goal_slider")
                        )
                        Text(
                            text = "Independent from your daily goal. Set to 0 to disable " +
                                "the weekly target.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(ForestSpacing.xs))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Daily screen-time budget",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                "${editableBudget} min",
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                        Slider(
                            value = editableBudget.toFloat(),
                            onValueChange = { editableBudget = (it / 15f).toInt() * 15 },
                            valueRange = 30f..360f
                        )
                        Text(
                            text = "Only used when optional Usage Access is enabled.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(ForestSpacing.sm))
                        Button(
                            onClick = {
                                viewModel.updateProfile(editableName, editableGoal)
                                viewModel.updateWeeklyGoal(editableWeeklyGoal)
                                viewModel.updateDailyUsageBudget(editableBudget)
                            },
                            modifier = Modifier.testTag("save_profile_settings_button")
                        ) {
                            Text("Save Profile & Goals")
                        }
                    }
                }

                // 4. Ambient audio
                item {
                    SectionHeader(
                        title = "Audio",
                        subtitle = "Sound is generated on this device and never starts on its own."
                    )
                }
                item {
                    BotanicalCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.AutoMirrored.Rounded.VolumeUp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(ForestSpacing.xs))
                            Text("Ambient soundscape", style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(modifier = Modifier.height(ForestSpacing.xs))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(ForestSpacing.xs)) {
                            AmbientSoundPreset.entries.forEach { preset ->
                                FilterChip(
                                    selected = state.ambientPreset == preset,
                                    onClick = { viewModel.setAmbientPreset(preset) },
                                    label = { Text(preset.displayName) },
                                    modifier = Modifier.testTag(
                                        "settings_ambient_${preset.name}"
                                    )
                                )
                            }
                        }
                        if (state.ambientPreset != AmbientSoundPreset.NONE) {
                            Spacer(modifier = Modifier.height(ForestSpacing.xs))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Volume", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = "${(editableVolume * 100).toInt()}%",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Slider(
                                value = editableVolume,
                                onValueChange = {
                                    editableVolume = it
                                    viewModel.setAmbientVolume(it)
                                },
                                valueRange = 0f..1f,
                                modifier = Modifier.testTag("settings_ambient_volume_slider")
                            )
                            SwitchRow(
                                label = "Start with focus sessions",
                                checked = state.ambientPlayWithSession,
                                onCheckedChange = { viewModel.setAmbientPlayWithSession(it) },
                                switchModifier = Modifier.testTag(
                                    "settings_ambient_play_switch"
                                )
                            )
                        }
                    }
                }

                // 5. Notifications
                item {
                    SectionHeader(
                        title = "Notifications",
                        subtitle = "Optional and off by default. The timer works without them."
                    )
                }
                item {
                    val np = state.notificationPreferences
                    BotanicalCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.Notifications,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(ForestSpacing.xs))
                            Text("Reminders", style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(modifier = Modifier.height(ForestSpacing.xs))
                        SwitchRow(
                            label = "Enable optional notifications",
                            checked = np.notificationsEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled &&
                                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    !state.notificationPermissionGranted
                                ) {
                                    notificationPermLauncher.launch(
                                        Manifest.permission.POST_NOTIFICATIONS
                                    )
                                } else {
                                    viewModel.updateNotificationPrefs(
                                        np.copy(notificationsEnabled = enabled)
                                    )
                                }
                            }
                        )
                        if (np.notificationsEnabled) {
                            SwitchRow(
                                label = "Completed focus session alert",
                                checked = np.sessionCompletionEnabled,
                                onCheckedChange = {
                                    viewModel.updateNotificationPrefs(
                                        np.copy(sessionCompletionEnabled = it)
                                    )
                                }
                            )
                            SwitchRow(
                                label = "Gentle daily reminder " +
                                    "(%02d:%02d)".format(
                                        np.dailyReminderHour,
                                        np.dailyReminderMinute
                                    ),
                                checked = np.dailyReminderEnabled,
                                onCheckedChange = {
                                    viewModel.updateNotificationPrefs(
                                        np.copy(dailyReminderEnabled = it)
                                    )
                                }
                            )
                            if (np.dailyReminderEnabled) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Reminder hour",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Row {
                                        TextButton(onClick = {
                                            viewModel.updateNotificationPrefs(
                                                np.copy(
                                                    dailyReminderHour =
                                                        (np.dailyReminderHour + 23) % 24
                                                )
                                            )
                                        }) { Text("−1h") }
                                        TextButton(onClick = {
                                            viewModel.updateNotificationPrefs(
                                                np.copy(
                                                    dailyReminderHour =
                                                        (np.dailyReminderHour + 1) % 24
                                                )
                                            )
                                        }) { Text("+1h") }
                                    }
                                }
                            }
                            SwitchRow(
                                label = "Incomplete daily goal nudge",
                                checked = np.incompleteGoalReminderEnabled,
                                onCheckedChange = {
                                    viewModel.updateNotificationPrefs(
                                        np.copy(incompleteGoalReminderEnabled = it)
                                    )
                                }
                            )
                            SwitchRow(
                                label = "Weekly reflection summary",
                                checked = np.weeklySummaryEnabled,
                                onCheckedChange = {
                                    viewModel.updateNotificationPrefs(
                                        np.copy(weeklySummaryEnabled = it)
                                    )
                                }
                            )
                            SwitchRow(
                                label = "Quiet hours " +
                                    "(${np.quietHoursStartHour}:00 – ${np.quietHoursEndHour}:00)",
                                checked = np.quietHoursEnabled,
                                onCheckedChange = {
                                    viewModel.updateNotificationPrefs(
                                        np.copy(quietHoursEnabled = it)
                                    )
                                }
                            )
                            if (np.quietHoursEnabled) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Quiet start (${np.quietHoursStartHour}:00)",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Row {
                                        TextButton(onClick = {
                                            viewModel.updateNotificationPrefs(
                                                np.copy(
                                                    quietHoursStartHour =
                                                        (np.quietHoursStartHour + 23) % 24
                                                )
                                            )
                                        }) { Text("−1h") }
                                        TextButton(onClick = {
                                            viewModel.updateNotificationPrefs(
                                                np.copy(
                                                    quietHoursStartHour =
                                                        (np.quietHoursStartHour + 1) % 24
                                                )
                                            )
                                        }) { Text("+1h") }
                                    }
                                }
                            }
                        }
                    }
                }

                // 6. Privacy and data
                item {
                    SectionHeader(
                        title = "Privacy & data",
                        subtitle = "Everything stays on this device."
                    )
                }
                item {
                    BotanicalCard(
                        modifier = Modifier
                            .clickable { onOpenPrivacy() }
                            .testTag("open_privacy_screen_button")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Rounded.PrivacyTip,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(ForestSpacing.sm))
                                Column {
                                    Text(
                                        "Privacy, Permissions & Data",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        "Review Usage Access status, backup exclusions, " +
                                            "and local-only guarantees",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Icon(
                                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = null
                            )
                        }
                    }
                }
                item {
                    BotanicalCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.Download,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(ForestSpacing.xs))
                            Text(
                                "Export Local Data (CSV)",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Spacer(modifier = Modifier.height(ForestSpacing.xs))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(ForestSpacing.xs)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.exportFocusSessionsCsv() },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("export_sessions_csv_button")
                            ) {
                                Text("Sessions CSV")
                            }
                            OutlinedButton(
                                onClick = { viewModel.exportDailyStatsCsv() },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("export_daily_csv_button")
                            ) {
                                Text("Daily Stats CSV")
                            }
                        }
                    }
                }
                item {
                    OutlinedButton(
                        onClick = { viewModel.requestDeleteAllData() },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(ForestSizes.minTouchTarget + 2.dp)
                            .testTag("delete_all_local_data_button"),
                        shape = RoundedCornerShape(ForestRadii.control)
                    ) {
                        Icon(Icons.Rounded.DeleteForever, contentDescription = null)
                        Spacer(modifier = Modifier.width(ForestSpacing.xs))
                        Text("Delete All Local Data")
                    }
                }

                // 7. About
                item {
                    SectionHeader(title = "About")
                }
                item {
                    BotanicalCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.Spa,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(ForestSpacing.xs))
                            Column {
                                Text("Focus Forest", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "Version ${BuildConfig.VERSION_NAME} " +
                                        "(${BuildConfig.VERSION_CODE})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.testTag("app_version_text")
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(ForestSpacing.xs))
                        Text(
                            text = "A local-first focus timer and private forest. " +
                                "No account, ads, analytics, cloud sync, or network permission.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    switchModifier: Modifier = Modifier
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = ForestSpacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = switchModifier
        )
    }
}
