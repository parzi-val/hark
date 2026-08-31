package com.hark.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hark.ai.HarkService
import com.hark.data.local.Source
import com.hark.data.repo.HarkRepository
import com.hark.ui.components.MetaLabel
import com.hark.ui.components.SectionLabel
import com.hark.ui.harkViewModel
import com.hark.ui.theme.Hark
import com.hark.ui.theme.HarkType
import kotlinx.coroutines.launch

enum class ComposeMode { NOTE, TASK }

@Composable
fun ComposeScreen(onClose: () -> Unit, onSaved: () -> Unit) {
    val vm: ComposeViewModel = harkViewModel { ComposeViewModel(it.repository, it.harkService) }
    val c = Hark.colors

    var mode by remember { mutableStateOf(ComposeMode.NOTE) }

    // Note fields
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }

    // Task fields
    var taskTitle by remember { mutableStateOf("") }
    var taskDueHint by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.paper)
    ) {
        // Top bar
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 22.dp, end = 22.dp, top = 20.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MetaLabel("↩ Back", color = c.inkMuted, modifier = Modifier.clickable { onClose() })
            MetaLabel(if (mode == ComposeMode.NOTE) "Write note" else "Add task", color = c.inkFaint)
        }

        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Mode Tabs (NOTE vs CHECKLIST TASK)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                listOf(ComposeMode.NOTE to "Note", ComposeMode.TASK to "Checklist item").forEach { (m, label) ->
                    val isSelected = m == mode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clip(RoundedCornerShape(21.dp))
                            .background(if (isSelected) c.ink else c.paper)
                            .border(1.dp, if (isSelected) c.ink else c.inkHairline, RoundedCornerShape(21.dp))
                            .clickable { mode = m },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            style = HarkType.label,
                            color = if (isSelected) c.paper else c.inkMuted,
                        )
                    }
                }
            }

            if (mode == ComposeMode.NOTE) {
                // NOTE MODE
                Text("New Thought", style = HarkType.title, color = c.ink)
                Text(
                    "Save directly as a note, or let Hark tidy and extract tasks.",
                    style = HarkType.secondary,
                    color = c.inkMuted,
                )

                // Title input
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("Title")
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        placeholder = { Text("Optional title", color = c.inkFaint, style = HarkType.body) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = c.ink,
                            unfocusedBorderColor = c.checkboxBorder,
                            focusedTextColor = c.ink,
                            unfocusedTextColor = c.ink,
                        ),
                        textStyle = HarkType.body,
                    )
                }

                // Body input
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("Note body")
                    OutlinedTextField(
                        value = body,
                        onValueChange = { body = it },
                        placeholder = { Text("Write your thoughts...", color = c.inkFaint, style = HarkType.bodyRelaxed) },
                        minLines = 6,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = c.ink,
                            unfocusedBorderColor = c.checkboxBorder,
                            focusedTextColor = c.ink,
                            unfocusedTextColor = c.ink,
                        ),
                        textStyle = HarkType.bodyRelaxed,
                    )
                }

                MetaLabel(
                    text = "Extract tasks: " + if (vm.extractTasks) "On" else "Off",
                    color = if (vm.extractTasks) c.rust else c.inkFaint,
                    modifier = Modifier.clickable { vm.toggleExtractTasks() },
                )
            } else {
                // TASK MODE
                Text("New Checklist Item", style = HarkType.title, color = c.ink)
                Text(
                    "Add a standalone task directly to your open docket and widget.",
                    style = HarkType.secondary,
                    color = c.inkMuted,
                )

                // Task Title input
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("Task description")
                    OutlinedTextField(
                        value = taskTitle,
                        onValueChange = { taskTitle = it },
                        placeholder = { Text("e.g. Call dentist, Buy groceries", color = c.inkFaint, style = HarkType.body) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = c.ink,
                            unfocusedBorderColor = c.checkboxBorder,
                            focusedTextColor = c.ink,
                            unfocusedTextColor = c.ink,
                        ),
                        textStyle = HarkType.body,
                    )
                }

                // Due Hint input
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("Due / reminder (optional)")
                    OutlinedTextField(
                        value = taskDueHint,
                        onValueChange = { taskDueHint = it },
                        placeholder = { Text("e.g. Today 5pm, Tomorrow, Friday", color = c.inkFaint, style = HarkType.body) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = c.ink,
                            unfocusedBorderColor = c.checkboxBorder,
                            focusedTextColor = c.ink,
                            unfocusedTextColor = c.ink,
                        ),
                        textStyle = HarkType.body,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }

        // Action Buttons
        if (mode == ComposeMode.NOTE) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val hasContent = title.isNotBlank() || body.isNotBlank()
                val busy = vm.saving || vm.tidying

                // 1. SAVE (direct / raw)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                        .clip(RoundedCornerShape(27.dp))
                        .border(1.dp, if (hasContent && !busy) c.ink else c.inkHairline, RoundedCornerShape(27.dp))
                        .clickable(enabled = hasContent && !busy) {
                            vm.saveRaw(title, body, onSaved)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (vm.saving) "Saving…" else "Save",
                        style = HarkType.label,
                        color = if (hasContent && !busy) c.ink else c.inkFaint,
                    )
                }

                // 2. TIDY & SAVE (with AI processing)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                        .clip(RoundedCornerShape(27.dp))
                        .background(if (hasContent && !busy) c.ink else c.inkHairline)
                        .clickable(enabled = hasContent && !busy) {
                            vm.saveTidied(title, body, onSaved)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (vm.tidying) "Tidying…" else "Tidy & save",
                        style = HarkType.label,
                        color = if (hasContent && !busy) c.paper else c.inkFaint,
                    )
                }
            }
        } else {
            // TASK ACTION
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
            ) {
                val hasContent = taskTitle.isNotBlank()
                val busy = vm.saving
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .clip(RoundedCornerShape(27.dp))
                        .background(if (hasContent && !busy) c.ink else c.inkHairline)
                        .clickable(enabled = hasContent && !busy) {
                            vm.saveTask(taskTitle, taskDueHint, onSaved)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (busy) "Adding…" else "Add to checklist",
                        style = HarkType.label,
                        color = if (hasContent && !busy) c.paper else c.inkFaint,
                    )
                }
            }
        }
    }
}

class ComposeViewModel(
    private val repo: HarkRepository,
    private val harkService: HarkService,
) : ViewModel() {
    var saving by mutableStateOf(false)
        private set
    var tidying by mutableStateOf(false)
        private set
    var extractTasks by mutableStateOf(true)
        private set

    fun toggleExtractTasks() {
        extractTasks = !extractTasks
    }

    fun saveRaw(title: String, body: String, onDone: () -> Unit) {
        if (saving || tidying) return
        saving = true
        viewModelScope.launch {
            repo.saveTypedNote(title, body)
            saving = false
            onDone()
        }
    }

    fun saveTidied(title: String, body: String, onDone: () -> Unit) {
        if (saving || tidying) return
        tidying = true
        val text = listOf(title.trim(), body.trim()).filter { it.isNotBlank() }.joinToString("\n\n")
        viewModelScope.launch {
            val notes = repo.recentNoteRefs()
            val action = harkService.process(
                transcript = text,
                extractTasks = extractTasks,
                notes = notes,
            )
            repo.applyAction(action, text, Source.TYPED)
            tidying = false
            onDone()
        }
    }

    fun saveTask(title: String, dueHint: String, onDone: () -> Unit) {
        if (saving || tidying || title.isBlank()) return
        saving = true
        viewModelScope.launch {
            repo.createStandaloneTask(title = title.trim(), dueHint = dueHint.trim().ifBlank { null })
            saving = false
            onDone()
        }
    }
}
