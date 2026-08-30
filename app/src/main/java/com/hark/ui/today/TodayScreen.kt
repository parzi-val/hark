package com.hark.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hark.data.local.NoteEntity
import com.hark.data.local.TaskEntity
import com.hark.ui.components.LexiconCard
import com.hark.ui.components.MetaLabel
import com.hark.ui.components.NoteDash
import com.hark.ui.components.SectionLabel
import com.hark.ui.components.TaskCheck
import com.hark.ui.components.TaskTapMenu
import com.hark.ui.harkViewModel
import com.hark.ui.theme.Hark
import com.hark.ui.theme.HarkType
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun TodayScreen(
    onOpenNote: (Long) -> Unit,
    onOpenLexicon: () -> Unit = {},
    onOpenWord: (String) -> Unit = {},
) {
    val vm: TodayViewModel = harkViewModel { TodayViewModel(it.repository, it.lexiconRepository, it.settingsStore) }
    val state by vm.ui.collectAsStateWithLifecycle()
    val c = Hark.colors
    val today = LocalDate.now(ZoneId.systemDefault())

    LazyColumn(Modifier.fillMaxSize().background(c.paper)) {
        // Header: greeting on its own line, then the big date with the λέξις archive
        // baseline-aligned on the right (keeps the greeting from wrapping and stops the
        // archive floating beside the number).
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 26.dp, end = 20.dp, top = 24.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                MetaLabel(
                    "${today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()).uppercase()} · ${state.greeting.uppercase()}",
                    color = c.inkFaint,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("${today.dayOfMonth}", style = HarkType.displayNumber, color = c.ink)
                        Text(
                            today.month.getDisplayName(TextStyle.FULL, Locale.getDefault()).lowercase(),
                            style = HarkType.noteTitle.copy(fontStyle = FontStyle.Italic),
                            color = c.inkMuted,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }

                    // Lexis Archive (λέξις) button
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .padding(bottom = 10.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onOpenLexicon() }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    ) {
                        Text("λέξις", style = HarkType.label, color = c.rust, maxLines = 1)
                        Text("ARCHIVE", style = HarkType.label, color = c.inkMuted, maxLines = 1)
                    }
                }
            }
        }

        // Word of the Day Card (λέξις)
        val word = state.wordOfTheDay
        if (state.showLexiconCard && word != null) {
            item {
                LexiconCard(
                    word = word,
                    modifier = Modifier.padding(start = 26.dp, end = 26.dp, top = 6.dp, bottom = 14.dp),
                    onClick = { onOpenWord(word.id) },
                )
            }
        }

        if (state.overdue.isNotEmpty()) {
            item { SectionLabel("OVERDUE · ${state.overdue.size}", Modifier.padding(start = 26.dp, end = 26.dp, top = 12.dp, bottom = 6.dp), color = c.rust) }
            items(state.overdue, key = { "o${it.id}" }) { TaskRow(it, onToggle = vm::toggle, onToggleDeferred = vm::toggleDeferred, overdue = true) }
        }

        if (state.dueToday.isNotEmpty()) {
            item { SectionLabel("DUE TODAY · ${state.dueToday.size}", Modifier.padding(start = 26.dp, end = 26.dp, top = 24.dp, bottom = 6.dp)) }
            items(state.dueToday, key = { "d${it.id}" }) { TaskRow(it, onToggle = vm::toggle, onToggleDeferred = vm::toggleDeferred, overdue = false) }
        }

        if (state.writtenToday.isNotEmpty()) {
            item { SectionLabel("WRITTEN TODAY", Modifier.padding(start = 26.dp, end = 26.dp, top = 24.dp, bottom = 6.dp)) }
            items(state.writtenToday, key = { "n${it.id}" }) { NoteRow(it, onOpenNote) }
        }

        if (state.isEmpty) {
            item {
                Text(
                    "Nothing is waiting on you today.",
                    style = HarkType.bodyRelaxed.copy(fontStyle = FontStyle.Italic),
                    color = c.inkFaint,
                    modifier = Modifier.padding(horizontal = 26.dp, vertical = 32.dp),
                )
            }
        }
    }
}

@Composable
private fun TaskRow(
    task: TaskEntity,
    onToggle: (TaskEntity) -> Unit,
    onToggleDeferred: (TaskEntity) -> Unit,
    overdue: Boolean,
) {
    val c = Hark.colors
    Column {
        HorizontalDivider(color = c.inkHairline)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 26.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.spacedBy(13.dp),
            verticalAlignment = Alignment.Top,
        ) {
            TaskCheck(task.done, onToggle = { onToggle(task) }, modifier = Modifier.padding(top = 3.dp))
            // Long-press to set a task aside; it drops off Today and lands (grayed) in the Stream.
            TaskTapMenu(
                deferred = task.deferred,
                onClick = {},
                onToggleDeferred = { onToggleDeferred(task) },
                modifier = Modifier.weight(1f).padding(end = 8.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        task.title,
                        style = HarkType.item,
                        color = if (task.done) c.inkFaint else c.ink,
                        textDecoration = if (task.done) TextDecoration.LineThrough else null,
                    )
                    task.dueHint?.let { MetaLabel(it, color = if (overdue) c.rust else c.inkFaint) }
                }
            }
        }
    }
}

@Composable
private fun NoteRow(note: NoteEntity, onOpenNote: (Long) -> Unit) {
    val c = Hark.colors
    Column {
        HorizontalDivider(color = c.inkHairline)
        Row(
            Modifier.fillMaxWidth().clickable { onOpenNote(note.id) }.padding(horizontal = 26.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(13.dp),
            verticalAlignment = Alignment.Top,
        ) {
            NoteDash(Modifier.padding(top = 11.dp))
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(note.title, style = HarkType.item, color = c.ink)
                MetaLabel(DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(note.createdAt) + " · written", color = c.inkFaint)
            }
        }
    }
}
