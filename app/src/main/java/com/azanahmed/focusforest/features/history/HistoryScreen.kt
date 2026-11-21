package com.azanahmed.focusforest.features.history

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.azanahmed.focusforest.data.repository.FocusRepository
import com.azanahmed.focusforest.domain.model.FocusSession
import com.azanahmed.focusforest.domain.model.SessionReflection
import com.azanahmed.focusforest.domain.model.SessionStatus
import com.azanahmed.focusforest.ui.components.BotanicalCard
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { message -> snackbarHostState.showSnackbar(message) }
    }

    state.detail?.let { detail ->
        HistoryDetailDialog(
            detail = detail,
            onNoteChange = viewModel::updateNote,
            onReflectionChange = viewModel::updateReflection,
            onSave = viewModel::saveDetail,
            onDismiss = viewModel::dismissDetail
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Focus History") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("history_back_button")) {
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
                    .testTag("history_lazy_column"),
                contentPadding = PaddingValues(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Text(
                        text = "Your focus history",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Every finished session keeps the routine and category names it used, " +
                            "even after a template is edited, archived, or deleted. Notes and " +
                            "reflections never change XP, seeds, trees, or achievements.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                item {
                    BotanicalCard {
                        Text("Filters", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = state.filters.query,
                            onValueChange = viewModel::setQuery,
                            label = { Text("Search intentions, notes, routines") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("history_search_input"),
                            shape = RoundedCornerShape(14.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        Text("Date range", style = MaterialTheme.typography.labelLarge)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            HistoryRange.entries.forEach { range ->
                                FilterChip(
                                    selected = state.filters.range == range,
                                    onClick = { viewModel.setRange(range) },
                                    label = { Text(range.label) },
                                    modifier = Modifier.testTag("history_range_${range.name}")
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Category", style = MaterialTheme.typography.labelLarge)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            FilterChip(
                                selected = state.filters.categoryId == null && !state.filters.uncategorized,
                                onClick = { viewModel.setCategory(null) },
                                label = { Text("All") },
                                modifier = Modifier.testTag("history_category_all")
                            )
                            FilterChip(
                                selected = state.filters.uncategorized,
                                onClick = { viewModel.setUncategorized(!state.filters.uncategorized) },
                                label = { Text("Uncategorized") },
                                modifier = Modifier.testTag("history_category_uncategorized")
                            )
                            state.categories.forEach { category ->
                                FilterChip(
                                    selected = state.filters.categoryId == category.id,
                                    onClick = { viewModel.setCategory(category.id) },
                                    label = { Text(category.name) },
                                    modifier = Modifier.testTag("history_category_${category.id}")
                                )
                            }
                        }
                        if (state.routines.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text("Routine", style = MaterialTheme.typography.labelLarge)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                FilterChip(
                                    selected = state.filters.routineId == null,
                                    onClick = { viewModel.setRoutine(null) },
                                    label = { Text("All routines") },
                                    modifier = Modifier.testTag("history_routine_all")
                                )
                                state.routines.forEach { routine ->
                                    FilterChip(
                                        selected = state.filters.routineId == routine.id,
                                        onClick = { viewModel.setRoutine(routine.id) },
                                        label = { Text(routine.name) },
                                        modifier = Modifier.testTag("history_routine_${routine.id}")
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Status", style = MaterialTheme.typography.labelLarge)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            FilterChip(
                                selected = state.filters.status == null,
                                onClick = { viewModel.setStatus(null) },
                                label = { Text("All") },
                                modifier = Modifier.testTag("history_status_ALL")
                            )
                            SessionStatus.entries
                                .filter { it != SessionStatus.RUNNING && it != SessionStatus.PAUSED }
                                .forEach { status ->
                                    FilterChip(
                                        selected = state.filters.status == status,
                                        onClick = { viewModel.setStatus(status) },
                                        label = { Text(statusLabel(status)) },
                                        modifier = Modifier.testTag("history_status_${status.name}")
                                    )
                                }
                        }
                        Spacer(Modifier.height(8.dp))
                        TextButton(
                            onClick = viewModel::clearFilters,
                            modifier = Modifier.testTag("history_clear_filters_button")
                        ) {
                            Text("Clear filters")
                        }
                    }
                }

                item {
                    Text(
                        text = if (state.shownCount == 1) "1 session" else "${state.shownCount} sessions",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("history_result_count")
                    )
                }

                if (!state.isLoading && state.sessions.isEmpty()) {
                    item {
                        BotanicalCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Rounded.History,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "No sessions match these filters",
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Complete a focus session or widen the date range to see it here.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                items(state.sessions, key = { it.id }) { session ->
                    HistorySessionCard(
                        session = session,
                        onClick = { viewModel.openSession(session.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun HistorySessionCard(
    session: FocusSession,
    onClick: () -> Unit
) {
    BotanicalCard(
        modifier = Modifier
            .clickable(onClick = onClick)
            .testTag("history_session_${session.id}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when (session.status) {
                        SessionStatus.COMPLETED -> Icons.Rounded.CheckCircle
                        SessionStatus.PAUSED -> Icons.Rounded.Pause
                        else -> Icons.Rounded.Stop
                    },
                    contentDescription = null,
                    tint = if (session.status == SessionStatus.COMPLETED) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = session.sessionType.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                text = statusLabel(session.status),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "${formatTimestamp(session.startedAtWallMs)} · " +
                "${session.completedMinutes}/${session.plannedMinutes} min",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        val contextLabel = listOfNotNull(
            session.routineNameSnapshot.takeIf { it.isNotBlank() },
            session.categoryNameSnapshot.takeIf { it.isNotBlank() }
        ).joinToString(" • ")
        if (contextLabel.isNotBlank()) {
            Text(
                text = contextLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        if (session.intentionLabel.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "\"${session.intentionLabel}\"",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        if (session.note.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = session.note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "${session.xpAwarded} XP · ${session.seedsAwarded} seeds" +
                if (session.reflection != null) " · ${session.reflection!!.label}" else "",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoryDetailDialog(
    detail: HistoryDetailState,
    onNoteChange: (String) -> Unit,
    onReflectionChange: (SessionReflection?) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    val session = detail.session
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("history_detail_dialog"),
        title = { Text("Session Reflection") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "${session.sessionType.displayName} · ${statusLabel(session.status)}",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = "${formatTimestamp(session.startedAtWallMs)} · " +
                        "${session.completedMinutes}/${session.plannedMinutes} min · " +
                        "${session.xpAwarded} XP · ${session.seedsAwarded} seeds",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (session.routineNameSnapshot.isNotBlank()) {
                    Text(
                        text = "Routine: ${session.routineNameSnapshot}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (session.routineSnapshot.isNotBlank()) {
                        Text(
                            text = session.routineSnapshot,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (session.categoryNameSnapshot.isNotBlank()) {
                    Text(
                        text = "Category: ${session.categoryNameSnapshot}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (session.intentionLabel.isNotBlank()) {
                    Text(
                        text = "Intention: \"${session.intentionLabel}\"",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(Modifier.height(4.dp))
                Text("Reflection", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilterChip(
                        selected = detail.reflection == null,
                        onClick = { onReflectionChange(null) },
                        label = { Text("None") },
                        modifier = Modifier.testTag("history_reflection_NONE")
                    )
                    SessionReflection.entries.forEach { reflection ->
                        FilterChip(
                            selected = detail.reflection == reflection,
                            onClick = { onReflectionChange(reflection) },
                            label = { Text(reflection.label) },
                            modifier = Modifier.testTag("history_reflection_${reflection.name}")
                        )
                    }
                }

                OutlinedTextField(
                    value = detail.note,
                    onValueChange = onNoteChange,
                    label = { Text("Notes") },
                    placeholder = { Text("What helped or distracted you?") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("history_note_input"),
                    shape = RoundedCornerShape(14.dp),
                    supportingText = {
                        Text("${detail.note.length}/${FocusRepository.MAX_NOTE_LENGTH}")
                    }
                )

                Text(
                    text = "Notes and reflections are personal context only. They never change " +
                        "XP, seeds, trees, achievements, or challenges.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (detail.error != null) {
                    Text(
                        text = detail.error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                enabled = !detail.isSaving,
                modifier = Modifier.testTag("history_save_button")
            ) {
                Text(if (detail.isSaving) "Saving…" else "Save")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("history_close_button")
            ) {
                Text("Close")
            }
        }
    )
}

private fun statusLabel(status: SessionStatus): String = when (status) {
    SessionStatus.COMPLETED -> "Completed"
    SessionStatus.ABORTED -> "Stopped"
    SessionStatus.INTERRUPTED -> "Interrupted"
    SessionStatus.RUNNING -> "Running"
    SessionStatus.PAUSED -> "Paused"
}

private fun formatTimestamp(wallMs: Long): String =
    Instant.ofEpochMilli(wallMs)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("MMM d, yyyy · HH:mm", Locale.getDefault()))
