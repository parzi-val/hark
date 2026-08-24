package com.hark.ui.note

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hark.ui.components.MetaLabel
import com.hark.ui.components.SectionLabel
import com.hark.ui.components.TaskCheck
import com.hark.ui.harkViewModel
import com.hark.ui.theme.Hark
import com.hark.ui.theme.HarkType
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun NoteDetailScreen(noteId: Long, onClose: () -> Unit) {
    val vm: NoteDetailViewModel = harkViewModel(key = "note-$noteId") { NoteDetailViewModel(noteId, it.repository) }
    val state by vm.ui.collectAsStateWithLifecycle()
    val c = Hark.colors

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var newTaskTitle by remember { mutableStateOf("") }

    var title by remember(state.note?.id) { mutableStateOf(state.note?.title.orEmpty()) }
    var body by remember(state.note?.id) { mutableStateOf(state.note?.body.orEmpty()) }

    // Keep in sync if loaded asynchronously
    if (state.note != null && title.isEmpty() && state.note!!.title.isNotEmpty()) {
        title = state.note!!.title
        body = state.note!!.body
    }

    val handleBack = {
        vm.flushSave(title, body)
        onClose()
    }

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

    Column(
        Modifier
            .fillMaxSize()
            .background(c.paper),
    ) {
        // Top Bar & Action Row
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 22.dp, end = 22.dp, top = 20.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MetaLabel("↩ Back", color = c.inkMuted, modifier = Modifier.clickable { handleBack() })

            val note = state.note
            if (note != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    // PIN TOGGLE
                    MetaLabel(
                        text = if (note.pinnedToWidget) "★ PINNED" else "☆ PIN",
                        color = if (note.pinnedToWidget) c.rust else c.inkMuted,
                        modifier = Modifier.clickable { vm.togglePin() },
                    )

                    // DELETE
                    MetaLabel(
                        text = "DELETE",
                        color = c.rust,
                        modifier = Modifier.clickable { showDeleteConfirm = true },
                    )
                }
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

                    // INLINE EDITABLE BODY
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
                                Text("Write your thoughts here...", style = HarkType.bodyRelaxed, color = c.inkFaint)
                            }
                            innerTextField()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // Tasks in this note
                    Spacer(Modifier.height(8.dp))
                    SectionLabel("TASKS IN THIS NOTE (${state.tasks.size})")

                    if (state.tasks.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            state.tasks.forEach { task ->
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.Top,
                                ) {
                                    TaskCheck(
                                        done = task.done,
                                        onToggle = { vm.toggleTask(task) },
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
                    }

                    // Quick Add Task row
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
                                    vm.addTask(newTaskTitle)
                                    newTaskTitle = ""
                                }
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("＋ ADD", style = HarkType.label, color = if (newTaskTitle.isNotBlank()) c.paper else c.inkFaint)
                        }
                    }

                    // Raw transcript if spoken
                    note.heardAs?.let { heard ->
                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider(color = c.inkHairline)
                        SectionLabel("HEARD AS")
                        Text(
                            heard,
                            style = HarkType.secondary,
                            color = c.inkMuted,
                        )
                    }

                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}
