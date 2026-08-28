package com.hark.ui.shelf

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hark.data.local.NoteEntity
import com.hark.data.repo.HarkRepository
import com.hark.ui.components.MetaLabel
import com.hark.ui.components.SectionLabel
import com.hark.ui.components.stripMarkdown
import com.hark.ui.harkViewModel
import com.hark.ui.theme.Hark
import com.hark.ui.theme.HarkType
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class ShelfViewModel(private val repo: HarkRepository) : ViewModel() {
    val shelfNotes: StateFlow<List<NoteEntity>> = repo.shelfNotes.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
    )

    fun createShelfNote(onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val id = repo.newShelfNote()
            onCreated(id)
        }
    }
}

@Composable
fun ShelfScreen(
    onOpenNote: (Long) -> Unit,
    onToggleStream: () -> Unit,
) {
    val vm: ShelfViewModel = harkViewModel { ShelfViewModel(it.repository) }
    val notes by vm.shelfNotes.collectAsStateWithLifecycle()
    val c = Hark.colors

    Column(
        Modifier
            .fillMaxSize()
            .background(c.paper)
    ) {
        // Header
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 22.dp, end = 22.dp, top = 20.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("The Shelf", style = HarkType.title.copy(fontSize = 24.sp, lineHeight = 28.sp), color = c.rust)
                MetaLabel("· ${notes.size}", color = c.inkFaint)
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MetaLabel(
                    text = "STREAM",
                    color = c.inkMuted,
                    modifier = Modifier.clickable { onToggleStream() },
                )
                // Pencil FAB — same ✎ affordance as the Stream capture button.
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(c.ink)
                        .clickable { vm.createShelfNote(onOpenNote) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("✎", style = HarkType.title.copy(fontSize = 16.sp), color = c.paper)
                }
            }
        }

        if (notes.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Nothing on the shelf yet. Long notes you write or dictate land here.",
                    style = HarkType.secondary,
                    color = c.inkFaint,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { Spacer(Modifier.height(4.dp)) }

                items(notes, key = { it.id }) { note ->
                    val excerpt = stripMarkdown(note.body)
                    val dateStr = DateTimeFormatter.ofPattern("MMM d")
                        .withZone(ZoneId.systemDefault())
                        .format(note.updatedAt)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(c.paperRaised)
                            .border(1.dp, c.inkHairline, RoundedCornerShape(16.dp))
                            .clickable { onOpenNote(note.id) }
                            .padding(18.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = note.title.ifBlank { "Untitled note" },
                                style = HarkType.noteTitle,
                                color = c.ink,
                            )
                            if (excerpt.isNotBlank()) {
                                Text(
                                    text = excerpt,
                                    style = HarkType.secondary,
                                    color = c.inkMuted,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            MetaLabel(dateStr, color = c.inkFaint)
                        }
                    }
                }

                item { Spacer(Modifier.height(32.dp)) }
            }
        }
    }
}
