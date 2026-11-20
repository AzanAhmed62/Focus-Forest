package com.azanahmed.focusforest.features.routines

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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.azanahmed.focusforest.domain.model.AmbientSoundPreset
import com.azanahmed.focusforest.domain.model.ChainConfig
import com.azanahmed.focusforest.domain.model.FocusCategory
import com.azanahmed.focusforest.domain.model.FocusRoutine
import com.azanahmed.focusforest.ui.components.BotanicalCard

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RoutinesScreen(
    viewModel: RoutinesViewModel,
    onBack: () -> Unit,
    onStartRoutine: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { message -> snackbarHostState.showSnackbar(message) }
    }

    state.routineDraft?.let { draft ->
        RoutineEditorDialog(
            draft = draft,
            categories = state.categories,
            error = state.routineError,
            onDraftChange = viewModel::updateRoutineDraft,
            onSave = viewModel::saveRoutine,
            onDismiss = viewModel::dismissRoutineEditor
        )
    }

    state.categoryDraft?.let { draft ->
        CategoryEditorDialog(
            draft = draft,
            error = state.categoryError,
            onNameChange = viewModel::updateCategoryName,
            onSave = viewModel::saveCategory,
            onDismiss = viewModel::dismissCategoryEditor
        )
    }

    state.confirmDeleteRoutineId?.let { id ->
        val routine = state.routines.firstOrNull { it.id == id }
            ?: state.archivedRoutines.firstOrNull { it.id == id }
        AlertDialog(
            onDismissRequest = viewModel::dismissDeleteRoutine,
            title = { Text("Delete routine?") },
            text = {
                Text(
                    "\"${routine?.name ?: "This routine"}\" will be removed. Past sessions keep their " +
                        "saved routine name and configuration, so history stays intact."
                )
            },
            confirmButton = {
                Button(
                    onClick = viewModel::confirmDeleteRoutine,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_routine_button")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDeleteRoutine) {
                    Text("Cancel")
                }
            }
        )
    }

    state.confirmArchiveCategoryId?.let { id ->
        val category = state.categories.firstOrNull { it.id == id }
        AlertDialog(
            onDismissRequest = viewModel::dismissArchiveCategory,
            title = { Text("Archive category?") },
            text = {
                Text(
                    "\"${category?.name ?: "This category"}\" will no longer be offered for new sessions. " +
                        "Past sessions keep their saved category name."
                )
            },
            confirmButton = {
                Button(
                    onClick = viewModel::confirmArchiveCategory,
                    modifier = Modifier.testTag("confirm_archive_category_button")
                ) {
                    Text("Archive")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissArchiveCategory) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Routines") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("routines_back_button")) {
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
                    .testTag("routines_lazy_column"),
                contentPadding = PaddingValues(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Text(
                        text = "Reusable focus templates",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Start a routine with one tap. Editing or deleting a template never " +
                            "changes an active timer or a completed session.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                item {
                    Button(
                        onClick = viewModel::openCreateRoutine,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("new_routine_button"),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("New Routine")
                    }
                }

                if (state.routines.isEmpty() && !state.isLoading) {
                    item {
                        BotanicalCard {
                            Text("No active routines", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Create a routine or restore one from the archive below.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                items(state.routines, key = { it.id }) { routine ->
                    RoutineCard(
                        routine = routine,
                        defaultCategoryName = state.categories
                            .firstOrNull { it.id == routine.defaultCategoryId }?.name,
                        onStart = { onStartRoutine(routine.id) },
                        onEdit = { viewModel.openEditRoutine(routine.id) },
                        onTogglePin = { viewModel.togglePinned(routine.id) },
                        onDuplicate = { viewModel.duplicateRoutine(routine.id) },
                        onMoveUp = { viewModel.moveRoutine(routine.id, -1) },
                        onMoveDown = { viewModel.moveRoutine(routine.id, 1) },
                        onArchive = { viewModel.setRoutineArchived(routine.id, true) },
                        onDelete = { viewModel.requestDeleteRoutine(routine.id) }
                    )
                }

                item {
                    CategoryManagementCard(
                        categories = state.categories,
                        archivedCategories = state.archivedCategories,
                        onAdd = viewModel::openCreateCategory,
                        onRename = viewModel::openRenameCategory,
                        onArchive = viewModel::requestArchiveCategory,
                        onRestore = viewModel::restoreCategory
                    )
                }

                if (state.archivedRoutines.isNotEmpty()) {
                    item {
                        Text("Archived Routines", style = MaterialTheme.typography.titleMedium)
                    }
                    items(state.archivedRoutines, key = { "archived_${it.id}" }) { routine ->
                        BotanicalCard(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ) {
                            Text(routine.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                routine.snapshotLabel(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(
                                    onClick = { viewModel.setRoutineArchived(routine.id, false) },
                                    modifier = Modifier.testTag("restore_routine_${routine.id}")
                                ) {
                                    Text("Restore")
                                }
                                TextButton(
                                    onClick = { viewModel.requestDeleteRoutine(routine.id) },
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Text("Delete")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoutineCard(
    routine: FocusRoutine,
    defaultCategoryName: String?,
    onStart: () -> Unit,
    onEdit: () -> Unit,
    onTogglePin: () -> Unit,
    onDuplicate: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    BotanicalCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = routine.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (routine.isPinned) {
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                "Pinned",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                if (routine.description.isNotBlank()) {
                    Text(
                        text = routine.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = routine.snapshotLabel(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (defaultCategoryName != null) {
                    Text(
                        text = "Category: $defaultCategoryName",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.testTag("routine_menu_${routine.id}")
                ) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "Routine actions for ${routine.name}")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        onClick = { menuExpanded = false; onEdit() },
                        modifier = Modifier.testTag("routine_edit_${routine.id}")
                    )
                    DropdownMenuItem(
                        text = { Text(if (routine.isPinned) "Unpin from Today" else "Pin to Today") },
                        onClick = { menuExpanded = false; onTogglePin() }
                    )
                    DropdownMenuItem(
                        text = { Text("Duplicate") },
                        onClick = { menuExpanded = false; onDuplicate() }
                    )
                    DropdownMenuItem(
                        text = { Text("Move up") },
                        onClick = { menuExpanded = false; onMoveUp() }
                    )
                    DropdownMenuItem(
                        text = { Text("Move down") },
                        onClick = { menuExpanded = false; onMoveDown() }
                    )
                    DropdownMenuItem(
                        text = { Text("Archive") },
                        onClick = { menuExpanded = false; onArchive() }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = { menuExpanded = false; onDelete() }
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onStart,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("routine_start_${routine.id}"),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Start ${routine.name}")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryManagementCard(
    categories: List<FocusCategory>,
    archivedCategories: List<FocusCategory>,
    onAdd: () -> Unit,
    onRename: (String) -> Unit,
    onArchive: (String) -> Unit,
    onRestore: (String) -> Unit
) {
    BotanicalCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Categories", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onAdd, modifier = Modifier.testTag("add_category_button")) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.width(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add")
            }
        }
        Text(
            text = "Categories are optional labels. Renaming or archiving one never rewrites history.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            categories.forEach { category ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = category.name,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { onRename(category.id) }) {
                        Text("Rename")
                    }
                    TextButton(
                        onClick = { onArchive(category.id) },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text("Archive")
                    }
                }
            }
        }
        if (archivedCategories.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("Archived categories", style = MaterialTheme.typography.labelLarge)
            archivedCategories.forEach { category ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = category.name,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = { onRestore(category.id) }) {
                        Text("Restore")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoutineEditorDialog(
    draft: RoutineDraft,
    categories: List<FocusCategory>,
    error: String?,
    onDraftChange: ((RoutineDraft) -> RoutineDraft) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (draft.id == null) "New Routine" else "Edit Routine") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 470.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { value ->
                        onDraftChange { it.copy(name = value.take(40)) }
                    },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("routine_name_input"),
                    shape = RoundedCornerShape(14.dp)
                )
                OutlinedTextField(
                    value = draft.description,
                    onValueChange = { value ->
                        onDraftChange { it.copy(description = value.take(200)) }
                    },
                    label = { Text("Description (optional)") },
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("routine_description_input"),
                    shape = RoundedCornerShape(14.dp)
                )

                LabeledSlider(
                    label = "Focus block",
                    value = draft.config.focusMinutes,
                    valueLabel = "${draft.config.focusMinutes} min",
                    range = 5f..180f,
                    steps = 34,
                    onChange = { value ->
                        onDraftChange { it.copy(config = it.config.copy(focusMinutes = value)) }
                    },
                    testTag = "routine_focus_slider"
                )
                LabeledSlider(
                    label = "Short break",
                    value = draft.config.shortBreakMinutes,
                    valueLabel = "${draft.config.shortBreakMinutes} min",
                    range = 1f..30f,
                    steps = 28,
                    onChange = { value ->
                        onDraftChange { it.copy(config = it.config.copy(shortBreakMinutes = value)) }
                    },
                    testTag = "routine_short_break_slider"
                )
                LabeledSlider(
                    label = "Long break",
                    value = draft.config.longBreakMinutes,
                    valueLabel = "${draft.config.longBreakMinutes} min",
                    range = 5f..45f,
                    steps = 7,
                    onChange = { value ->
                        onDraftChange { it.copy(config = it.config.copy(longBreakMinutes = value)) }
                    },
                    testTag = "routine_long_break_slider"
                )
                LabeledSlider(
                    label = "Focus rounds",
                    value = draft.config.totalFocusSessions,
                    valueLabel = "${draft.config.totalFocusSessions}",
                    range = 1f..8f,
                    steps = 6,
                    onChange = { value ->
                        onDraftChange { it.copy(config = it.config.copy(totalFocusSessions = value)) }
                    },
                    testTag = "routine_rounds_slider"
                )
                LabeledSlider(
                    label = "Long break every",
                    value = draft.config.longBreakFrequency,
                    valueLabel = "${draft.config.longBreakFrequency} rounds",
                    range = 2f..8f,
                    steps = 5,
                    onChange = { value ->
                        onDraftChange { it.copy(config = it.config.copy(longBreakFrequency = value)) }
                    },
                    testTag = "routine_cadence_slider"
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Auto-start next step", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = draft.config.autoTransition,
                        onCheckedChange = { checked ->
                            onDraftChange { it.copy(config = it.config.copy(autoTransition = checked)) }
                        },
                        modifier = Modifier.testTag("routine_auto_transition_switch")
                    )
                }

                if (categories.isNotEmpty()) {
                    Text("Default category", style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FilterChip(
                            selected = draft.defaultCategoryId == null,
                            onClick = { onDraftChange { it.copy(defaultCategoryId = null) } },
                            label = { Text("None") }
                        )
                        categories.forEach { category ->
                            FilterChip(
                                selected = draft.defaultCategoryId == category.id,
                                onClick = { onDraftChange { it.copy(defaultCategoryId = category.id) } },
                                label = { Text(category.name) }
                            )
                        }
                    }
                }

                Text("Ambient sound", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    AmbientSoundPreset.entries.forEach { preset ->
                        FilterChip(
                            selected = draft.ambientPreset == preset,
                            onClick = { onDraftChange { it.copy(ambientPreset = preset) } },
                            label = { Text(preset.displayName) },
                            modifier = Modifier.testTag("routine_ambient_${preset.name}")
                        )
                    }
                }

                if (error != null) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("routine_editor_error")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                modifier = Modifier.testTag("save_routine_button")
            ) {
                Text("Save Routine")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Int,
    valueLabel: String,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Int) -> Unit,
    testTag: String
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text(valueLabel, style = MaterialTheme.typography.labelMedium)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = range,
            steps = steps,
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
private fun CategoryEditorDialog(
    draft: CategoryDraft,
    error: String?,
    onNameChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (draft.id == null) "Add Category" else "Rename Category") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = onNameChange,
                    label = { Text("Category name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("category_name_input"),
                    shape = RoundedCornerShape(14.dp)
                )
                if (error != null) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                modifier = Modifier.testTag("save_category_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
