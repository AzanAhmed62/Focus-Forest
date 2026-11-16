package com.example.features.settings

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
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Spa
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
import com.example.domain.model.ThemePreference
import com.example.platform.usage.AndroidUsageStatsGateway
import com.example.ui.components.BotanicalCard
import com.example.ui.components.UsageAccessDisclosureDialog

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

    val notificationPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.refreshPermissions()
        if (granted) {
            viewModel.updateNotificationPrefs(state.notificationPreferences.copy(notificationsEnabled = true))
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
                Text("This permanently erases all focus sessions, streaks, forest trees, challenges, shop items, and usage snapshots from this device.")
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmDeleteAllData(onDataDeletedResetToOnboarding) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
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
                    IconButton(onClick = onBack, modifier = Modifier.testTag("settings_back_button")) {
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
                contentPadding = PaddingValues(18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Personal Profile & Daily Goal
                item {
                    BotanicalCard {
                        Text("Profile & Daily Goal", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = editableName,
                            onValueChange = { editableName = it.take(40) },
                            label = { Text("Display name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        )
                        Spacer(Modifier.height(10.dp))
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
                        Button(
                            onClick = { viewModel.updateProfile(editableName, editableGoal) },
                            modifier = Modifier.testTag("save_profile_settings_button")
                        ) {
                            Text("Save Profile & Goal")
                        }
                    }
                }

                // 2. Appearance Theme
                item {
                    BotanicalCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("Appearance", style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(Modifier.height(10.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

                // 3. Respectful Notifications
                item {
                    val np = state.notificationPreferences
                    BotanicalCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("Respectful Notifications", style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(Modifier.height(8.dp))
                        SwitchRow(
                            label = "Enable optional notifications",
                            checked = np.notificationsEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !state.notificationPermissionGranted) {
                                    notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    viewModel.updateNotificationPrefs(np.copy(notificationsEnabled = enabled))
                                }
                            }
                        )
                        if (np.notificationsEnabled) {
                            SwitchRow(
                                label = "Completed focus session alert",
                                checked = np.sessionCompletionEnabled,
                                onCheckedChange = {
                                    viewModel.updateNotificationPrefs(np.copy(sessionCompletionEnabled = it))
                                }
                            )
                            SwitchRow(
                                label = "Gentle daily reminder (${np.dailyReminderHour}:00)",
                                checked = np.dailyReminderEnabled,
                                onCheckedChange = {
                                    viewModel.updateNotificationPrefs(np.copy(dailyReminderEnabled = it))
                                }
                            )
                            SwitchRow(
                                label = "Incomplete daily goal nudge",
                                checked = np.incompleteGoalReminderEnabled,
                                onCheckedChange = {
                                    viewModel.updateNotificationPrefs(np.copy(incompleteGoalReminderEnabled = it))
                                }
                            )
                            SwitchRow(
                                label = "Weekly summary",
                                checked = np.weeklySummaryEnabled,
                                onCheckedChange = {
                                    viewModel.updateNotificationPrefs(np.copy(weeklySummaryEnabled = it))
                                }
                            )
                            SwitchRow(
                                label = "Quiet hours (${np.quietHoursStartHour}:00 – ${np.quietHoursEndHour}:00)",
                                checked = np.quietHoursEnabled,
                                onCheckedChange = {
                                    viewModel.updateNotificationPrefs(np.copy(quietHoursEnabled = it))
                                }
                            )
                        }
                    }
                }

                // 4. Privacy & Data Safety Screen Link
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
                                Icon(Icons.Rounded.PrivacyTip, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text("Privacy, Permissions & Data", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "Review Usage Access status and local-only guarantees",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
                        }
                    }
                }

                // 5. CSV Export
                item {
                    BotanicalCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Download, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("Export Local Data (CSV)", style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
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

                // 6. Future Roadmap (Honest, no mock billing)
                item {
                    BotanicalCard(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Spa, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("Focus Forest Plus (Planned)", style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Version 1.0.0 is 100% local and free. Future updates via Google Play Billing are planned to support optional cloud backup, extended ambient soundscapes, and real social challenge rooms.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 7. Delete All Local Data
                item {
                    OutlinedButton(
                        onClick = { viewModel.requestDeleteAllData() },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("delete_all_local_data_button"),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Rounded.DeleteForever, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Delete All Local Data")
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
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
