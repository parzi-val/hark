package com.hark.ui.stream

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hark.data.local.NoteEntity
import com.hark.data.local.TaskEntity
import com.hark.domain.StreamItem
import com.hark.ui.components.MetaLabel
import com.hark.ui.components.NoteDash
import com.hark.ui.components.SectionLabel
import com.hark.ui.components.TalkNib
import com.hark.ui.components.TaskCheck
import com.hark.ui.harkViewModel
import com.hark.ui.task.EditTaskDialog
import com.hark.ui.theme.Hark
import com.hark.ui.theme.HarkType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun StreamScreen(
    onTalk: () -> Unit,
    onWrite: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenNote: (Long) -> Unit = {},
) {
    val vm: StreamViewModel = harkViewModel { StreamViewModel(it.repository) }
    val settingsVm: com.hark.ui.settings.SettingsViewModel = harkViewModel { com.hark.ui.settings.SettingsViewModel(it.settingsStore) }
    val state by vm.ui.collectAsStateWithLifecycle()
    val settings by settingsVm.settings.collectAsStateWithLifecycle()
    val c = Hark.colors

    var editingTask by remember { mutableStateOf<TaskEntity?>(null) }
    var captureDrawerExpanded by remember { mutableStateOf(false) }

    if (editingTask != null) {
        val taskToEdit = editingTask!!
        EditTaskDialog(
            task = taskToEdit,
            onDismiss = { editingTask = null },
            onSave = { newTitle, newHint ->
                vm.updateTask(taskToEdit.id, newTitle, newHint)
                editingTask = null
            },
            onDelete = {
                vm.deleteTask(taskToEdit.id)
                editingTask = null
            },
        )
    }

    Box(Modifier.fillMaxSize().background(c.paper)) {
        // Main Stream & Header (Fills entire screen)
        Column(Modifier.fillMaxSize()) {
            // Header
            Column(
                Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    Text("Hark", style = HarkType.title, color = c.ink)
                    MetaLabel(headerMeta(state.openCount), color = c.inkFaint)
                }

                if (!settings.isConfigured) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(c.rust.copy(alpha = 0.12f))
                            .clickable { onOpenSettings() }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(
                            "Tap here to set your Groq/OpenAI API key →",
                            style = HarkType.secondary,
                            color = c.rust,
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    FilterTab("ALL", StreamFilter.ALL, state.filter, vm::setFilter)
                    FilterTab("OPEN", StreamFilter.OPEN, state.filter, vm::setFilter)
                    FilterTab("NOTES", StreamFilter.NOTES, state.filter, vm::setFilter)
                }
            }

            // The river (scrolls with 90dp bottom padding so bottom items are never covered)
            val groups = groupItems(state.visible)
            if (groups.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        "Nothing here yet. Tap Talk and say what's on your mind.",
                        style = HarkType.secondary,
                        color = c.inkFaint,
                        modifier = Modifier.padding(horizontal = 40.dp),
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(bottom = 96.dp),
                ) {
                    groups.forEach { (label, items) ->
                        item(key = "hdr-$label") {
                            SectionLabel(label, Modifier.padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 6.dp))
                        }
                        items(items, key = { it.itemKey() }) { entry ->
                            StreamRow(
                                item = entry,
                                onToggle = { vm.toggle(it) },
                                onOpenNote = onOpenNote,
                                onEditTask = { editingTask = it },
                            )
                        }
                    }
                    item(key = "tail") { HorizontalDivider(color = c.inkHairline) }
                }
            }
        }

        // Floating Collapsible Capture Action Bar / FAB (Floating overlay)
        FloatingCaptureOverlay(
            expanded = captureDrawerExpanded,
            onExpandChange = { captureDrawerExpanded = it },
            onWrite = {
                captureDrawerExpanded = false
                onWrite()
            },
            onTalk = {
                captureDrawerExpanded = false
                onTalk()
            },
            modifier = Modifier.align(Alignment.BottomEnd),
        )
    }
}

@Composable
private fun FilterTab(label: String, value: StreamFilter, current: StreamFilter, onPick: (StreamFilter) -> Unit) {
    val c = Hark.colors
    val active = value == current
    Text(
        text = label,
        style = HarkType.label,
        color = if (active) c.ink else c.inkFaint,
        modifier = Modifier
            .clickable { onPick(value) }
            .then(if (active) Modifier.border(0.dp, c.paper) else Modifier)
            .padding(bottom = 3.dp),
    )
}

@Composable
private fun StreamRow(
    item: StreamItem,
    onToggle: (TaskEntity) -> Unit,
    onOpenNote: (Long) -> Unit,
    onEditTask: (TaskEntity) -> Unit,
) {
    val c = Hark.colors
    Column {
        HorizontalDivider(color = c.inkHairline)
        when (item) {
            is StreamItem.Task -> TaskLine(item.task, onToggle = onToggle, onEdit = onEditTask, indent = 22.dp)

            is StreamItem.Note -> {
                val hasTasks = item.tasks.isNotEmpty()
                // Note header
                Row(
                    Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 16.dp, bottom = if (hasTasks) 6.dp else 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(13.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    NoteDash(Modifier.padding(top = 11.dp))
                    Column(
                        Modifier.weight(1f).clickable { onOpenNote(item.note.id) },
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Text(item.note.title, style = HarkType.item, color = c.ink)
                        if (item.note.body.isNotBlank()) {
                            Text(
                                item.note.body,
                                style = HarkType.secondary,
                                color = c.inkMuted,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        MetaLabel(noteMeta(item.note), color = c.inkFaint)
                    }
                }
                // Tasks belonging to this note, nested under it.
                item.tasks.forEach { task ->
                    TaskLine(task, onToggle = onToggle, onEdit = onEditTask, indent = 51.dp, topPad = 6.dp, bottomPad = 6.dp)
                }
                if (hasTasks) Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun TaskLine(
    task: TaskEntity,
    onToggle: (TaskEntity) -> Unit,
    onEdit: (TaskEntity) -> Unit,
    indent: androidx.compose.ui.unit.Dp,
    topPad: androidx.compose.ui.unit.Dp = 16.dp,
    bottomPad: androidx.compose.ui.unit.Dp = 16.dp,
) {
    val c = Hark.colors
    Row(
        Modifier.fillMaxWidth().padding(start = indent, end = 22.dp, top = topPad, bottom = bottomPad),
        horizontalArrangement = Arrangement.spacedBy(13.dp),
        verticalAlignment = Alignment.Top,
    ) {
        TaskCheck(task.done, onToggle = { onToggle(task) }, modifier = Modifier.padding(top = 3.dp))
        Row(
            modifier = Modifier.weight(1f).clickable { onEdit(task) },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = task.title,
                style = HarkType.item,
                color = if (task.done) c.inkFaint else c.ink,
                textDecoration = if (task.done) TextDecoration.LineThrough else null,
                modifier = Modifier.weight(1f),
            )
            taskMeta(task)?.let { MetaLabel(it, color = if (isOverdue(task)) c.rust else c.inkFaint) }
        }
    }
}

@Composable
private fun FloatingCaptureOverlay(
    expanded: Boolean,
    onExpandChange: (Boolean) -> Unit,
    onWrite: () -> Unit,
    onTalk: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Hark.colors

    Box(
        modifier = modifier.padding(horizontal = 18.dp, vertical = 20.dp),
        contentAlignment = Alignment.BottomEnd,
    ) {
        // Collapsed state: Compact floating pencil FAB
        AnimatedVisibility(
            visible = !expanded,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .shadow(8.dp, CircleShape)
                    .clip(CircleShape)
                    .background(c.ink)
                    .clickable { onExpandChange(true) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "✎",
                    style = HarkType.title.copy(fontSize = 23.sp),
                    color = c.paper,
                )
            }
        }

        // Expanded state: Slides in floating drawer from right to left
        AnimatedVisibility(
            visible = expanded,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
        ) {
            Row(
                modifier = Modifier
                    .shadow(12.dp, RoundedCornerShape(32.dp))
                    .clip(RoundedCornerShape(32.dp))
                    .background(c.paper)
                    .border(1.dp, c.inkHairline, RoundedCornerShape(32.dp))
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Collapse button (✕)
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .border(1.dp, c.inkHairline, CircleShape)
                        .clickable { onExpandChange(false) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("✕", style = HarkType.label, color = c.inkMuted)
                }

                // WRITE
                Row(
                    Modifier
                        .height(46.dp)
                        .clip(RoundedCornerShape(23.dp))
                        .border(1.dp, c.ink.copy(alpha = 0.18f), RoundedCornerShape(23.dp))
                        .clickable { onWrite() }
                        .padding(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("+", style = HarkType.title.copy(fontSize = 15.sp), color = c.ink)
                    Text("WRITE", style = HarkType.label, color = c.inkMuted)
                }

                // TALK
                Row(
                    Modifier
                        .height(46.dp)
                        .clip(RoundedCornerShape(23.dp))
                        .background(c.ink)
                        .clickable { onTalk() }
                        .padding(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TalkNib(color = c.paper)
                    Text("TALK", style = HarkType.label, color = c.paper)
                }
            }
        }
    }
}

// ── formatting helpers ───────────────────────────────────────────────────────

private val zone: ZoneId get() = ZoneId.systemDefault()

private fun StreamItem.itemKey(): String = when (this) {
    is StreamItem.Task -> "t${task.id}"
    is StreamItem.Note -> "n${note.id}"
}

private fun headerMeta(openCount: Int): String {
    val now = LocalDate.now(zone)
    val dow = now.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
    return "$dow ${now.dayOfMonth} · $openCount OPEN"
}

private fun groupItems(items: List<StreamItem>): List<Pair<String, List<StreamItem>>> {
    val today = LocalDate.now(zone)
    val (pinned, unpinned) = items.partition { item ->
        (item as? StreamItem.Note)?.note?.pinnedToWidget == true
    }
    val (todayItems, earlier) = unpinned.partition { it.createdAt.atZone(zone).toLocalDate() == today }
    return buildList {
        if (pinned.isNotEmpty()) add("PINNED" to pinned)
        if (todayItems.isNotEmpty()) add("TODAY" to todayItems)
        if (earlier.isNotEmpty()) add("EARLIER" to earlier)
    }
}

private fun taskMeta(t: TaskEntity): String? = when {
    t.done -> "DONE"
    t.dueHint != null -> t.dueHint
    t.dueAt != null -> shortDate(t.dueAt)
    else -> null
}

private fun isOverdue(t: TaskEntity): Boolean {
    val due = t.dueAt ?: return false
    return !t.done && due.atZone(zone).toLocalDate().isBefore(LocalDate.now(zone))
}

private fun noteMeta(n: NoteEntity): String =
    DateTimeFormatter.ofPattern("HH:mm").withZone(zone).format(n.createdAt)

private fun shortDate(instant: Instant): String {
    val d = instant.atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    return when (d) {
        today -> "TODAY"
        today.plusDays(1) -> "TOMORROW"
        else -> d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
    }
}
