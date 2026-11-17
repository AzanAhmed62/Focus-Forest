package com.example.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.UsagePermissionStatus
import com.example.platform.usage.AndroidUsageStatsGateway
import com.example.ui.components.BotanicalCard
import com.example.ui.components.UsageAccessDisclosureDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy & Data Safety") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("privacy_back_button")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
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
                    .widthIn(max = 620.dp),
                contentPadding = PaddingValues(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    BotanicalCard(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Text("Strict Local-First Architecture", style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "• No INTERNET permission is declared or used in this release.\n" +
                                "• Android cloud backup and device transfer of local databases and preferences are disabled (allowBackup=false).\n" +
                                "• No accounts, telemetry, remote analytics, or ad SDKs.\n" +
                                "• Focus sessions, streaks, challenges, and Android usage snapshots remain strictly inside your device's private storage.\n" +
                                "• Focus Forest is a wellness habit tool and does not diagnose, treat, or provide medical advice for ADHD, anxiety, or addiction.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                item {
                    BotanicalCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Text("Permission Status", style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(Modifier.height(10.dp))
                        PermissionRow(
                            title = "Android Usage Access (PACKAGE_USAGE_STATS)",
                            status = if (state.usagePermissionStatus == UsagePermissionStatus.GRANTED) "Granted" else "Not granted"
                        )
                        Spacer(Modifier.height(8.dp))
                        PermissionRow(
                            title = "Notifications (POST_NOTIFICATIONS)",
                            status = if (state.notificationPermissionGranted) "Granted" else "Not granted"
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = { viewModel.openUsageDisclosure() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Review Disclosure & Open Usage Settings")
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { viewModel.disableAndDeleteUsageRecords() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Disable Monitoring & Clear Usage Snapshots")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionRow(title: String, status: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = status,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
