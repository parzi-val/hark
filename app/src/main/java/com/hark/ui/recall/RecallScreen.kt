package com.hark.ui.recall

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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hark.data.local.NoteEntity
import com.hark.ui.components.MetaLabel
import com.hark.ui.components.NoteDash
import com.hark.ui.components.SectionLabel
import com.hark.ui.harkViewModel
import com.hark.ui.theme.Hark
import com.hark.ui.theme.HarkType

@Composable
fun RecallScreen(onOpenNote: (Long) -> Unit) {
    val vm: RecallViewModel = harkViewModel { RecallViewModel(it.repository, it.recallService) }
    val state by vm.ui.collectAsStateWithLifecycle()
    val c = Hark.colors

    LazyColumn(Modifier.fillMaxSize().background(c.paper)) {
        item {
            Column(Modifier.padding(start = 26.dp, end = 26.dp, top = 24.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SectionLabel("RECALL", color = c.inkFaint)
                OutlinedTextField(
                    value = state.query,
                    onValueChange = vm::setQuery,
                    placeholder = { Text("Ask about your notes…", color = c.inkFaint, style = HarkType.body) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { vm.search() }),
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

        if (!state.searched) {
            item {
                Column(Modifier.padding(horizontal = 26.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionLabel("TRY ASKING")
                    listOf("what did I say about the framer?", "everything I wrote this week", "notes about sourdough").forEach { s ->
                        Text("“$s”", style = HarkType.secondary.copy(fontStyle = FontStyle.Italic), color = c.inkMuted)
                    }
                }
            }
        } else {
            item {
                Column(Modifier.padding(start = 26.dp, end = 26.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("SHORT ANSWER", color = c.rust)
                    Text(
                        state.answer ?: "Thinking…",
                        style = HarkType.bodyRelaxed,
                        color = if (state.answer == null) c.inkFaint else c.ink,
                    )
                }
            }
            if (state.hits.isNotEmpty()) {
                item { SectionLabel("${state.hits.size} HITS", Modifier.padding(start = 26.dp, end = 26.dp, bottom = 4.dp)) }
                items(state.hits, key = { it.id }) { HitRow(it, onOpenNote) }
            } else if (!state.loading) {
                item {
                    Text("No matching notes.", style = HarkType.secondary, color = c.inkFaint, modifier = Modifier.padding(horizontal = 26.dp, vertical = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun HitRow(note: NoteEntity, onOpenNote: (Long) -> Unit) {
    val c = Hark.colors
    Column {
        HorizontalDivider(color = c.inkHairline)
        Row(
            Modifier.fillMaxWidth().clickable { onOpenNote(note.id) }.padding(horizontal = 26.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            NoteDash(Modifier.padding(top = 11.dp))
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(note.title, style = HarkType.item, color = c.ink)
                if (note.body.isNotBlank()) {
                    Text(note.body, style = HarkType.secondary, color = c.inkMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
