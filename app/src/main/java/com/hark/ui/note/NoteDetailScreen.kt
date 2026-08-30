package com.hark.ui.note

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hark.ui.components.HarkMarkdown
import com.hark.ui.components.HilbertSpinner
import com.hark.ui.components.MetaLabel
import com.hark.ui.components.SectionLabel
import com.hark.ui.components.TaskCheck
import com.hark.ui.harkViewModel
import com.hark.ui.theme.Hark
import com.hark.ui.theme.HarkType
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun NoteDetailScreen(
    noteId: Long,
    onClose: () -> Unit,
    onTalkToEdit: (() -> Unit)? = null,
) {
    val vm: NoteDetailViewModel = harkViewModel(key = "note-$noteId") {
        NoteDetailViewModel(noteId, it.repository, it.harkService)
    }
    val state by vm.ui.collectAsStateWithLifecycle()
    val c = Hark.colors

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showTasksModal by remember { mutableStateOf(false) }
    var drawerExpanded by remember { mutableStateOf(false) }
    var editingBody by remember { mutableStateOf(false) }
    var heardExpanded by remember { mutableStateOf(false) }

    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }

    // Sync from database while preserving user typing
    LaunchedEffect(state.note?.title, state.note?.body) {
        val n = state.note
        if (n != null) {
            title = n.title
            body = n.body
        }
    }

    val handleBack = {
        vm.closeNote(title, body, onClose)
    }

    // System back / back-gesture must run the same discard-empty logic as the on-screen Back,
    // otherwise a blank note exits as a saved "Untitled note" instead of being trashed.
    BackHandler { handleBack() }

    // Delete confirmation dialog
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Note", style = HarkType.title, color = c.ink) },
            text = {
                Text(
                    "Are you sure you want to delete this note and its tasks? This action cannot be undone.",
                    style = HarkType.secondary,
                    color = c.inkMuted,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        vm.deleteNote(onDeleted = onClose)
                    },
                ) {
                    Text("DELETE", style = HarkType.label, color = c.rust)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("CANCEL", style = HarkType.label, color = c.inkMuted)
                }
            },
            containerColor = c.paper,
        )
    }

    // Shape Error dialog
    if (state.shapeError != null) {
        AlertDialog(
            onDismissRequest = { vm.clearShapeError() },
            title = { Text("Shape Note", style = HarkType.title, color = c.ink) },
            text = {
                Text(
                    state.shapeError ?: "Unable to shape note.",
                    style = HarkType.secondary,
                    color = c.inkMuted,
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.clearShapeError() }) {
                    Text("OK", style = HarkType.label, color = c.ink)
                }
            },
            containerColor = c.paper,
        )
    }

    // Tasks modal dialog
    if (showTasksModal) {
        TasksModalDialog(
            tasks = state.tasks,
            onDismiss = { showTasksModal = false },
            onToggleTask = { vm.toggleTask(it) },
            onAddTask = { vm.addTask(it) },
        )
    }

    // Strip leading "# Title" heading from body if it repeats note title
    val displayBody = remember(title, body) {
        val regex = Regex("""^\s*#\s+(.+?)\s*\n+""")
        val match = regex.find(body)
        if (match != null && match.groupValues[1].trim().equals(title.trim(), ignoreCase = true)) {
            body.substring(match.range.last + 1)
        } else {
            body
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(c.paper)
    ) {
        Column(Modifier.fillMaxSize()) {
            // Clean Top Bar with Back and subtle Options trigger
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 22.dp, end = 22.dp, top = 20.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MetaLabel("↩ Back", color = c.inkMuted, modifier = Modifier.clickable { handleBack() })

                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (state.tasks.isNotEmpty()) {
                        MetaLabel(
                            text = "${state.tasks.count { !it.done }} TASKS",
                            color = c.rust,
                            modifier = Modifier.clickable { showTasksModal = true },
                        )
                    }
                    MetaLabel(
                        text = "OPTIONS ☰",
                        color = c.inkMuted,
                        modifier = Modifier.clickable { drawerExpanded = true },
                    )
                }
            }

            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = c.ink)
                }
            } else {
                val note = state.note
                if (note == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Note not found or deleted.", style = HarkType.body, color = c.inkMuted)
                    }
                } else {
                    // Pure, distraction-free writing canvas
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        // INLINE EDITABLE TITLE
                        BasicTextField(
                            value = title,
                            onValueChange = {
                                title = it
                                vm.updateContent(it, body)
                            },
                            textStyle = HarkType.noteTitle.copy(color = c.ink),
                            cursorBrush = SolidColor(c.ink),
                            decorationBox = { innerTextField ->
                                if (title.isEmpty()) {
                                    Text("Untitled Note", style = HarkType.noteTitle, color = c.inkFaint)
                                }
                                innerTextField()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )

                        val formattedDate = DateTimeFormatter.ofPattern("EEEE, MMM d · HH:mm")
                            .withZone(ZoneId.systemDefault())
                            .format(note.createdAt)
                        MetaLabel(formattedDate, color = c.inkFaint)

                        HorizontalDivider(color = c.inkHairline)

                        // BODY — MARKDOWN BY DEFAULT, TAP TO EDIT
                        if (editingBody) {
                            BasicTextField(
                                value = body,
                                onValueChange = {
                                    body = it
                                    vm.updateContent(title, it)
                                },
                                textStyle = HarkType.bodyRelaxed.copy(color = c.ink.copy(alpha = 0.88f)),
                                cursorBrush = SolidColor(c.ink),
                                decorationBox = { innerTextField ->
                                    if (body.isEmpty()) {
                                        Text("Write your thoughts here... (markdown supported)", style = HarkType.bodyRelaxed, color = c.inkFaint)
                                    }
                                    innerTextField()
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else if (body.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { editingBody = true },
                            ) {
                                HarkMarkdown(text = displayBody)
                            }
                        } else {
                            Text(
                                text = "Write your thoughts here... (markdown supported)",
                                style = HarkType.bodyRelaxed,
                                color = c.inkFaint,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { editingBody = true },
                            )
                        }

                        // Raw transcript if spoken (collapsible)
                        note.heardAs?.let { heard ->
                            Spacer(Modifier.height(16.dp))
                            HorizontalDivider(color = c.inkHairline)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.clickable { heardExpanded = !heardExpanded },
                            ) {
                                SectionLabel("HEARD AS")
                                Text(
                                    text = if (heardExpanded) "▲" else "▼",
                                    style = HarkType.meta,
                                    color = c.inkFaint,
                                )
                            }
                            if (heardExpanded) {
                                Text(
                                    "\"$heard\"",
                                    style = HarkType.secondary.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                                    color = c.inkMuted,
                                )
                            }
                        }

                        Spacer(Modifier.height(100.dp))
                    }
                }
            }
        }

        // Floating Action Button (FAB) at bottom-right
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(horizontal = 20.dp, vertical = 22.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .shadow(8.dp, CircleShape)
                    .clip(CircleShape)
                    .background(c.ink)
                    .clickable { drawerExpanded = true },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "☰",
                    style = HarkType.title.copy(fontSize = 22.sp),
                    color = c.paper,
                )
            }
        }

        // Vertical Side Drawer & Backdrop Overlay
        val note = state.note
        if (note != null) {
            VerticalSideDrawer(
                visible = drawerExpanded,
                onDismiss = { drawerExpanded = false },
                tasksCount = state.tasks.size,
                openTasksCount = state.tasks.count { !it.done },
                isShaping = state.isShaping,
                isShelf = note.shelf,
                isPinned = note.pinnedToWidget,
                isArchived = note.archived,
                hasTalk = onTalkToEdit != null,
                onTasksClick = {
                    drawerExpanded = false
                    showTasksModal = true
                },
                onTalkClick = {
                    drawerExpanded = false
                    vm.flushSave(title, body)
                    onTalkToEdit?.invoke()
                },
                onShapeClick = {
                    drawerExpanded = false
                    vm.shape(title, body) { newTitle, newBody ->
                        title = newTitle
                        body = newBody
                    }
                },
                onToggleShelfClick = {
                    drawerExpanded = false
                    vm.toggleShelf()
                },
                onTogglePinClick = {
                    drawerExpanded = false
                    vm.togglePin()
                },
                onArchiveClick = {
                    drawerExpanded = false
                    if (note.archived) vm.unarchiveNote(onClose) else vm.archiveNote(title, body, onClose)
                },
                onDeleteClick = {
                    drawerExpanded = false
                    showDeleteConfirm = true
                },
            )
        }

        // Looping Hilbert Curve Spinner Overlay during AI Shaping
        if (state.isShaping) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(c.paper.copy(alpha = 0.93f))
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.padding(32.dp),
                ) {
                    HilbertSpinner(
                        modifier = Modifier.size(92.dp),
                        color = c.rust,
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "Shaping note…",
                            style = HarkType.title.copy(fontSize = 24.sp),
                            color = c.ink,
                        )
                        Text(
                            text = "Hark is structuring your thoughts into clean Markdown.",
                            style = HarkType.secondary,
                            color = c.inkMuted,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VerticalSideDrawer(
    visible: Boolean,
    onDismiss: () -> Unit,
    tasksCount: Int,
    openTasksCount: Int,
    isShaping: Boolean,
    isShelf: Boolean,
    isPinned: Boolean,
    isArchived: Boolean,
    hasTalk: Boolean,
    onTasksClick: () -> Unit,
    onTalkClick: () -> Unit,
    onShapeClick: () -> Unit,
    onToggleShelfClick: () -> Unit,
    onTogglePinClick: () -> Unit,
    onArchiveClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    val c = Hark.colors

    if (visible) {
        // Scrim backdrop
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable { onDismiss() }
        )
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
        exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(300.dp)
                    .shadow(16.dp)
                    .background(c.paper)
                    .border(1.dp, c.inkHairline)
                    .clickable(enabled = false) {}
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionLabel("NOTE OPTIONS")
                    MetaLabel("✕", color = c.inkMuted, modifier = Modifier.clickable { onDismiss() })
                }

                HorizontalDivider(color = c.inkHairline)

                // 1. TASKS
                DrawerOptionItem(
                    title = "Tasks",
                    badge = if (tasksCount > 0) "$openTasksCount open" else "0 tasks",
                    subtext = "View & manage checklist",
                    badgeColor = if (openTasksCount > 0) c.rust else c.inkFaint,
                    onClick = onTasksClick,
                )

                // 2. TALK TO EDIT
                if (hasTalk) {
                    DrawerOptionItem(
                        title = "Talk to Edit",
                        badge = "VOICE",
                        subtext = "Dictate additions or edits",
                        onClick = onTalkClick,
                    )
                }

                // 3. SHAPE NOTE (AI Markdown formatting)
                DrawerOptionItem(
                    title = if (isShaping) "Shaping Note…" else "Shape Note",
                    badge = "AI",
                    subtext = "Reformat into structured markdown",
                    titleColor = c.rust,
                    badgeColor = c.rust,
                    onClick = onShapeClick,
                )

                // 4. SHELF / STREAM
                DrawerOptionItem(
                    title = if (isShelf) "Move to Stream" else "Move to Shelf",
                    badge = if (isShelf) "STREAM" else "SHELF",
                    subtext = if (isShelf) "Show on stream & daily list" else "Long-form reading list",
                    onClick = onToggleShelfClick,
                )

                // 5. PIN TO WIDGET (for stream notes)
                if (!isShelf) {
                    DrawerOptionItem(
                        title = if (isPinned) "Unpin from Widget" else "Pin to Widget",
                        badge = if (isPinned) "★ PINNED" else "☆ PIN",
                        badgeColor = if (isPinned) c.rust else c.inkFaint,
                        subtext = "Show at the top of 4×2 widget",
                        onClick = onTogglePinClick,
                    )
                }

                Spacer(Modifier.weight(1f))

                HorizontalDivider(color = c.inkHairline)

                // 6. ARCHIVE / UNARCHIVE NOTE
                DrawerOptionItem(
                    title = if (isArchived) "Unarchive Note" else "Archive Note",
                    badge = if (isArchived) "UNARCHIVE" else "ARCHIVE",
                    subtext = if (isArchived) "Bring it back to the Stream" else "File it away — hides from Stream & Today",
                    onClick = onArchiveClick,
                )

                // 7. DELETE NOTE
                DrawerOptionItem(
                    title = "Delete Note",
                    badge = "DELETE",
                    subtext = "Permanently remove note and tasks",
                    titleColor = c.rust,
                    badgeColor = c.rust,
                    onClick = onDeleteClick,
                )
            }
        }
    }
}

@Composable
private fun DrawerOptionItem(
    title: String,
    badge: String? = null,
    subtext: String? = null,
    titleColor: Color = Hark.colors.ink,
    badgeColor: Color = Hark.colors.inkMuted,
    onClick: () -> Unit,
) {
    val c = Hark.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = HarkType.item, color = titleColor)
            if (badge != null) {
                MetaLabel(badge, color = badgeColor)
            }
        }
        if (subtext != null) {
            Text(subtext, style = HarkType.meta, color = c.inkFaint)
        }
    }
}

@Composable
private fun TasksModalDialog(
    tasks: List<com.hark.data.local.TaskEntity>,
    onDismiss: () -> Unit,
    onToggleTask: (com.hark.data.local.TaskEntity) -> Unit,
    onAddTask: (String) -> Unit,
) {
    val c = Hark.colors
    var newTaskTitle by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Tasks in Note", style = HarkType.title, color = c.ink)
                MetaLabel("${tasks.size} total", color = c.inkFaint)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (tasks.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        tasks.forEach { task ->
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                TaskCheck(
                                    done = task.done,
                                    onToggle = { onToggleTask(task) },
                                    modifier = Modifier.padding(top = 3.dp),
                                )
                                Column(
                                    Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text(
                                        text = task.title,
                                        style = HarkType.item,
                                        color = if (task.done) c.inkFaint else c.ink,
                                        textDecoration = if (task.done) TextDecoration.LineThrough else null,
                                    )
                                    task.dueHint?.let {
                                        MetaLabel("heard \"$it\"", color = c.rust)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        "No checklist items in this note yet.",
                        style = HarkType.secondary,
                        color = c.inkMuted,
                    )
                }

                HorizontalDivider(color = c.inkHairline)

                // Quick Add Task input
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = newTaskTitle,
                        onValueChange = { newTaskTitle = it },
                        placeholder = { Text("Add task...", style = HarkType.secondary, color = c.inkFaint) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = c.ink,
                            unfocusedBorderColor = c.checkboxBorder,
                            focusedTextColor = c.ink,
                            unfocusedTextColor = c.ink,
                        ),
                        textStyle = HarkType.secondary,
                    )

                    Box(
                        modifier = Modifier
                            .height(46.dp)
                            .clip(RoundedCornerShape(23.dp))
                            .background(if (newTaskTitle.isNotBlank()) c.ink else c.inkHairline)
                            .clickable(enabled = newTaskTitle.isNotBlank()) {
                                onAddTask(newTaskTitle)
                                newTaskTitle = ""
                            }
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("＋ ADD", style = HarkType.label, color = if (newTaskTitle.isNotBlank()) c.paper else c.inkFaint)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("DONE", style = HarkType.label, color = c.ink)
            }
        },
        containerColor = c.paper,
    )
}
