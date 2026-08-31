package com.hark.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
fun SearchScreen(onClose: () -> Unit, onOpenNote: (Long) -> Unit) {
    val vm: SearchViewModel = harkViewModel { SearchViewModel(it.repository) }
    val state by vm.ui.collectAsStateWithLifecycle()
    val c = Hark.colors

    Column(Modifier.fillMaxSize().background(c.paper)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 20.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MetaLabel("↩ Back", color = c.inkMuted, modifier = Modifier.clickable { onClose() })
            MetaLabel("Search", color = c.inkFaint)
        }

        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::setQuery,
                placeholder = { Text("Search your notes…", style = HarkType.secondary, color = c.inkFaint) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = c.ink,
                    unfocusedBorderColor = c.inkHairline,
                    focusedTextColor = c.ink,
                    unfocusedTextColor = c.ink,
                ),
                textStyle = HarkType.secondary,
                shape = RoundedCornerShape(12.dp),
            )
        }

        when {
            state.query.isBlank() -> EmptyNote("Search across your notes by word or phrase.")
            state.hits.isEmpty() -> EmptyNote("No notes match “${state.query}”.")
            else -> LazyColumn(Modifier.fillMaxSize()) {
                item { SectionLabel("${state.hits.size} results", Modifier.padding(start = 26.dp, end = 26.dp, top = 8.dp, bottom = 4.dp)) }
                items(state.hits, key = { it.id }) { HitRow(it, onOpenNote) }
            }
        }
    }
}

@Composable
private fun EmptyNote(text: String) {
    val c = Hark.colors
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, style = HarkType.secondary, color = c.inkFaint)
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
