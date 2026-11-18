package com.azanahmed.focusforest.features.insights

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.azanahmed.focusforest.domain.model.AppUsageSummary
import com.azanahmed.focusforest.domain.model.FocusSession
import com.azanahmed.focusforest.domain.model.SessionStatus
import com.azanahmed.focusforest.domain.model.SnapshotCompleteness
import com.azanahmed.focusforest.domain.model.UsagePermissionStatus
import com.azanahmed.focusforest.platform.usage.AndroidUsageStatsGateway
import com.azanahmed.focusforest.ui.components.BotanicalCard
import com.azanahmed.focusforest.ui.components.EmptyStateCard
import com.azanahmed.focusforest.ui.components.FocusTrendBarChart
import com.azanahmed.focusforest.ui.components.StatPill
import com.azanahmed.focusforest.ui.components.StreakCalendarCard
import com.azanahmed.focusforest.ui.components.UsageAccessDisclosureDialog
import com.azanahmed.focusforest.ui.components.UsageSevenDayBarChart

@Composable
fun InsightsScreen(
    viewModel: InsightsViewModel
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshUsage(force = false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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

    if (state.showDeleteUsageDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeleteUsageDialog() },
            title = { Text("Disable & delete usage records?") },
            text = {
                Text("This disables local Android usage monitoring in Focus Forest and permanently deletes all stored usage snapshots on this device.")
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmDeleteUsageData() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Records")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDeleteUsageDialog() }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (state.showAppSelectorDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissAppSelectorDialog() },
            title = { Text("Select Monitored Apps") },
            text = {
                val apps = state.usageState.availableLaunchableApps
                if (apps.isEmpty()) {
                    Text("No launchable third-party apps discovered on this device yet.")
                } else {
                    LazyColumn(modifier = Modifier.height(320.dp)) {
                        items(apps, key = { it.packageName }) { app ->
                            val checked = app.packageName in state.usageState.monitoredPackages
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.toggleMonitoredApp(app.packageName) }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AppIconImage(drawable = app.icon, label = app.appName)
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = app.appName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { viewModel.toggleMonitoredApp(app.packageName) }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.dismissAppSelectorDialog() }) {
                    Text("Done")
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 620.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                Text("Insights", style = MaterialTheme.typography.headlineLarge)
                Text(
                    text = "Focus engine records and Android-observed app usage are tracked independently.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            TabRow(selectedTabIndex = state.selectedTab) {
                Tab(
                    selected = state.selectedTab == 0,
                    onClick = { viewModel.selectTab(0) },
                    text = { Text("Focus Sessions") },
                    modifier = Modifier.testTag("insights_tab_focus")
                )
                Tab(
                    selected = state.selectedTab == 1,
                    onClick = { viewModel.selectTab(1) },
                    text = { Text("App Usage (Android)") },
                    modifier = Modifier.testTag("insights_tab_usage")
                )
            }

            if (state.selectedTab == 0) {
                FocusInsightsTab(
                    state = state,
                    onFilterSelect = viewModel::selectDateFilter,
                    onPreviousMonth = viewModel::previousMonth,
                    onNextMonth = viewModel::nextMonth,
                    onSelectCalendarDate = viewModel::selectCalendarDate
                )
            } else {
                UsageInsightsTab(
                    state = state,
                    onEnableAccess = viewModel::openDisclosureDialog,
                    onSelectApps = viewModel::openAppSelectorDialog,
                    onRefresh = { viewModel.refreshUsage(force = true) },
                    onUpdateBudget = viewModel::updateDailyUsageBudget,
                    onDeleteUsageData = viewModel::requestDeleteUsageData
                )
            }
        }
    }
}

@Composable
private fun FocusInsightsTab(
    state: InsightsUiState,
    onFilterSelect: (InsightsDateFilter) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectCalendarDate: (java.time.LocalDate) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("insights_focus_lazy_column"),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InsightsDateFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = state.dateFilter == filter,
                        onClick = { onFilterSelect(filter) },
                        label = { Text(filter.label) },
                        modifier = Modifier.testTag("filter_${filter.name}")
                    )
                }
            }
        }

        item {
            BotanicalCard {
                val windowTitle = when (state.dateFilter) {
                    InsightsDateFilter.SEVEN_DAYS -> "7-Day Focus Minutes"
                    InsightsDateFilter.THIRTY_DAYS -> "30-Day Focus Minutes"
                    InsightsDateFilter.NINETY_DAYS -> "90-Day Focus Minutes"
                    InsightsDateFilter.ALL_TIME -> "Recent 30-Day Focus Trend"
                }
                Text(windowTitle, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Measured from Focus Forest timer sessions",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                FocusTrendBarChart(
                    days = state.trendFocusDays,
                    dailyGoalMinutes = state.dailyGoalMinutes
                )
            }
        }

        // Key metrics grid
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatPill(
                        label = "Total Focus",
                        value = "${state.records.totalFocusMinutes} min",
                        icon = Icons.Rounded.Timer,
                        modifier = Modifier.weight(1f)
                    )
                    StatPill(
                        label = "Completed",
                        value = "${state.records.totalCompletedSessions} sessions",
                        icon = Icons.Rounded.EmojiEvents,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatPill(
                        label = "Monthly Consistency",
                        value = "${state.advancedInsights.monthlyConsistencyPercent}% (${state.advancedInsights.monthlyActiveOrRestDays}/30d)",
                        icon = Icons.Rounded.CalendarMonth,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    StatPill(
                        label = "Best Time of Day",
                        value = state.advancedInsights.bestTimeOfDay?.label ?: "Not enough data",
                        icon = Icons.Rounded.Schedule,
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatPill(
                        label = "Longest Session",
                        value = "${state.records.longestSessionMinutes} min",
                        icon = Icons.Rounded.History,
                        modifier = Modifier.weight(1f)
                    )
                    StatPill(
                        label = "Best Day • Best Streak",
                        value = "${state.advancedInsights.bestFocusDayMinutes}m • ${state.records.longestStreakDays}d",
                        icon = Icons.Rounded.LocalFireDepartment,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Interactive Streak Calendar Heatmap
        item {
            Text("Consistency Calendar", style = MaterialTheme.typography.titleLarge)
        }
        item {
            StreakCalendarCard(
                displayedMonth = state.displayedMonth,
                cells = state.calendarCells,
                selectedDate = state.selectedCalendarDate,
                onPreviousMonth = onPreviousMonth,
                onNextMonth = onNextMonth,
                onSelectDate = onSelectCalendarDate
            )
        }
        state.selectedCalendarDate?.let { selDate ->
            item {
                val stat = state.selectedDayStats
                BotanicalCard(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)) {
                    Text(
                        text = "Selected Date: $selDate",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.testTag("calendar_selected_date_summary")
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = when {
                            stat == null || (stat.focusMinutes == 0 && !stat.isRestDay) ->
                                "No focus sessions logged on this day."
                            stat.isRestDay && stat.focusMinutes == 0 ->
                                "Rest day marked — streak continuity preserved."
                            else ->
                                "${stat.focusMinutes} min focused across ${stat.completedSessions} session(s) (Goal: ${stat.goalMinutes}m)"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Session-type breakdown
        if (state.advancedInsights.sessionTypeBreakdown.isNotEmpty()) {
            item {
                BotanicalCard {
                    Text("Session Type Breakdown", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.advancedInsights.sessionTypeBreakdown.forEach { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(item.sessionType.displayName, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    text = "${item.count} (${item.minutes}m)",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            LinearProgressIndicator(
                                progress = { item.shareFraction },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                            )
                        }
                    }
                }
            }
        }

        item {
            Text("Session History", style = MaterialTheme.typography.titleLarge)
        }

        if (state.filteredSessions.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Rounded.History,
                    title = "No focus sessions in this range",
                    subtitle = "Start a Pomodoro or Deep Work session to record your first entry."
                )
            }
        } else {
            items(state.filteredSessions, key = { it.id }) { session ->
                SessionHistoryCard(session)
            }
        }
    }
}

@Composable
private fun SessionHistoryCard(session: FocusSession) {
    BotanicalCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.sessionType.displayName +
                        if (session.intentionLabel.isNotBlank()) " — ${session.intentionLabel}" else "",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = "${session.localDate} • ${session.completedMinutes}/${session.plannedMinutes} min",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = when (session.status) {
                    SessionStatus.COMPLETED -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.errorContainer
                }
            ) {
                Text(
                    text = if (session.status == SessionStatus.COMPLETED) "+${session.xpAwarded} XP"
                    else session.status.name.lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun UsageInsightsTab(
    state: InsightsUiState,
    onEnableAccess: () -> Unit,
    onSelectApps: () -> Unit,
    onRefresh: () -> Unit,
    onUpdateBudget: (Int) -> Unit,
    onDeleteUsageData: () -> Unit
) {
    val usage = state.usageState
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Android-observed foreground app usage • Stored locally only",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }

        if (usage.permissionStatus != UsagePermissionStatus.GRANTED) {
            item {
                EmptyStateCard(
                    icon = Icons.Rounded.PhoneAndroid,
                    title = "Usage Access is not enabled",
                    subtitle = "Focus Forest never guesses or fabricates screen time. Review the local privacy disclosure to enable Android Usage Access.",
                    actionLabel = "Review & Enable Usage Access",
                    onAction = onEnableAccess
                )
            }
        } else if (!usage.isTrackingEnabled) {
            item {
                EmptyStateCard(
                    icon = Icons.Rounded.PhoneAndroid,
                    title = "Usage monitoring is paused",
                    subtitle = "Android Usage Access is granted, but local snapshot capture is currently disabled.",
                    actionLabel = "Enable Monitoring",
                    onAction = onEnableAccess
                )
            }
        } else {
            // Controls row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onSelectApps,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("select_monitored_apps_button")
                    ) {
                        Icon(Icons.Rounded.Apps, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Monitored (${usage.monitoredPackages.size})")
                    }
                    OutlinedButton(
                        onClick = onRefresh,
                        modifier = Modifier.testTag("refresh_usage_button")
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Refresh")
                    }
                }
            }

            usage.queryErrorMessage?.let { errMsg ->
                item {
                    BotanicalCard(containerColor = MaterialTheme.colorScheme.errorContainer) {
                        Text(
                            text = errMsg,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(Modifier.height(6.dp))
                        TextButton(onClick = onRefresh) {
                            Text("Retry Query")
                        }
                    }
                }
            }

            // Today's Observed Usage (Partial Day vs Daily Budget)
            item {
                val today = usage.todaySummary
                val budget = usage.dailyUsageBudgetMinutes
                val baseline = usage.baselineStatus

                BotanicalCard {
                    Text("Today's Observed Usage (Day in Progress)", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    when {
                        usage.todayCompleteness == SnapshotCompleteness.ZERO_USAGE -> {
                            Text(
                                text = "0 min observed foreground time today (zero-usage day).",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        usage.isDataUnavailable || today == null || !today.hasUsableData -> {
                            Text(
                                text = "Data unavailable or insufficient from Android UsageStatsManager for today.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        else -> {
                            val activeMinutes = if (usage.monitoredPackages.isNotEmpty()) {
                                today.monitoredAppsMinutes
                            } else {
                                today.totalObservedMinutes
                            }
                            Text(
                                text = "${today.totalObservedMinutes} min total foreground",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (usage.monitoredPackages.isNotEmpty()) {
                                Text(
                                    text = "${today.monitoredAppsMinutes} min across selected monitored apps",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            val budgetFraction = (activeMinutes.toFloat() / budget.toFloat()).coerceIn(0f, 1f)
                            LinearProgressIndicator(
                                progress = { budgetFraction },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "$activeMinutes / $budget min daily usage budget",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (activeMinutes <= budget) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Daily Budget (${budget}m)", style = MaterialTheme.typography.labelMedium)
                        Row {
                            TextButton(
                                onClick = { onUpdateBudget((budget - 15).coerceAtLeast(15)) },
                                modifier = Modifier.testTag("usage_budget_dec")
                            ) {
                                Text("−15m")
                            }
                            TextButton(
                                onClick = { onUpdateBudget((budget + 15).coerceAtMost(720)) },
                                modifier = Modifier.testTag("usage_budget_inc")
                            ) {
                                Text("+15m")
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Text("Completed-Day Baseline", style = MaterialTheme.typography.titleSmall)
                    if (!baseline.isBaselineReady) {
                        Text(
                            text = "${baseline.validBaselineDays} / 7 completed days collected (excluding today's partial hours).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "7-Day Completed Average: ${baseline.sevenDayAverageMinutes} min/day",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        val compDate = baseline.comparableCompletedDayDate
                        val compMin = baseline.comparableCompletedDayMinutes
                        val pct = baseline.improvementPercent
                        if (compDate != null && compMin != null && pct != null) {
                            val deltaMsg = if (pct >= 0) "$pct% lower than prior 7-day average"
                            else "${-pct}% higher than prior 7-day average"
                            Text(
                                text = "Latest completed day ($compDate: ${compMin}m) is $deltaMsg.",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (pct >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                    if (baseline.isWeeklyBaselineReady && baseline.weeklyReductionPercent != null) {
                        Spacer(Modifier.height(4.dp))
                        val wPct = baseline.weeklyReductionPercent
                        Text(
                            text = "7-Day Weekly Comparison: ${baseline.weeklyCompletedMinutes}m vs ${baseline.weeklyPriorBaselineMinutes}m prior week (${if (wPct >= 0) "-$wPct%" else "+${-wPct}%"}).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 7-Day Usage Chart
            item {
                BotanicalCard {
                    Text(
                        text = if (usage.monitoredPackages.isNotEmpty()) "7-Day Monitored Apps Usage"
                        else "7-Day Foreground Usage",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    UsageSevenDayBarChart(
                        days = usage.sevenDayHistory,
                        useMonitoredSubset = usage.monitoredPackages.isNotEmpty()
                    )
                }
            }

            // Top Apps list
            val apps = usage.todaySummary?.topApps.orEmpty()
            if (apps.isNotEmpty()) {
                item {
                    Text("Most-Used Apps Today", style = MaterialTheme.typography.titleLarge)
                }
                items(apps.take(15), key = { it.packageName }) { app ->
                    AppUsageRowCard(app)
                }
            }

            item {
                TextButton(
                    onClick = onDeleteUsageData,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("delete_usage_records_button")
                ) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Disable Monitoring & Delete Usage Records")
                }
            }
        }
    }
}

@Composable
private fun AppUsageRowCard(app: AppUsageSummary) {
    BotanicalCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIconImage(drawable = app.icon, label = app.appName)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(app.appName, style = MaterialTheme.typography.titleSmall)
                if (app.isMonitored) {
                    Text(
                        "Monitored",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Text(
                text = app.displayDuration,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun AppIconImage(drawable: Drawable?, label: String) {
    val bitmap = remember(drawable) {
        drawable?.let { runCatching { it.toBitmap(72, 72).asImageBitmap() }.getOrNull() }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = "$label icon",
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(8.dp))
        )
    } else {
        val initial = label.trim().firstOrNull()?.uppercaseChar()?.toString()
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            if (initial != null) {
                Text(
                    text = initial,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Apps,
                    contentDescription = "$label icon",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}
