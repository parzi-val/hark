package com.hark.ui.lexicon

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hark.data.repo.LexiconRepository
import com.hark.domain.LexiconWord
import com.hark.ui.components.LexiconCard
import com.hark.ui.components.MetaLabel
import com.hark.ui.components.SectionLabel
import com.hark.ui.harkViewModel
import com.hark.ui.theme.Hark
import com.hark.ui.theme.HarkType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class LexiconUiState(
    val words: List<LexiconWord> = emptyList(),
    val query: String = "",
    val selectedTier: Int? = null,
)

class LexiconViewModel(private val repo: LexiconRepository) : ViewModel() {

    // Only words unlocked so far — search and filters stay within this set.
    private val unlocked: List<LexiconWord> = repo.getUnlockedWords()

    private val _ui = MutableStateFlow(LexiconUiState(words = unlocked))
    val ui: StateFlow<LexiconUiState> = _ui.asStateFlow()

    fun setQuery(q: String) {
        _ui.update { it.copy(query = q) }
        filter()
    }

    fun setTier(tier: Int?) {
        _ui.update { it.copy(selectedTier = tier) }
        filter()
    }

    private fun filter() {
        val s = _ui.value
        _ui.update { it.copy(words = repo.search(s.query, s.selectedTier, unlocked)) }
    }
}

@Composable
fun LexiconScreen(
    onClose: () -> Unit,
    onOpenWord: (String) -> Unit = {},
) {
    val vm: LexiconViewModel = harkViewModel { LexiconViewModel(it.lexiconRepository) }
    val state by vm.ui.collectAsStateWithLifecycle()
    val c = Hark.colors

    Column(
        Modifier
            .fillMaxSize()
            .background(c.paper)
    ) {
        // Top Bar
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
                Text(
                    "The Lexicon",
                    style = HarkType.title.copy(fontSize = 24.sp, lineHeight = 28.sp),
                    color = c.rust,
                )
                MetaLabel("λέξις · ${state.words.size}", color = c.inkFaint)
            }

            MetaLabel("↩ Back", color = c.inkMuted, modifier = Modifier.clickable { onClose() })
        }

        // Search field
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp)
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::setQuery,
                placeholder = { Text("Search word, nuance, or distinction…", style = HarkType.secondary, color = c.inkFaint) },
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

        // Tier Filter chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TierChip("ALL", null, state.selectedTier, vm::setTier)
            TierChip("T1 ELEVATED", 1, state.selectedTier, vm::setTier)
            TierChip("T2 DISCRIMINATING", 2, state.selectedTier, vm::setTier)
            TierChip("T3 LITERARY", 3, state.selectedTier, vm::setTier)
            TierChip("T4 ESOTERIC", 4, state.selectedTier, vm::setTier)
            TierChip("T5 LEGENDARY", 5, state.selectedTier, vm::setTier)
        }

        // Word list
        if (state.words.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No words found matching your search.",
                    style = HarkType.secondary,
                    color = c.inkFaint,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item { Spacer(Modifier.height(4.dp)) }

                items(state.words, key = { it.id }) { word ->
                    LexiconCard(
                        word = word,
                        onClick = { onOpenWord(word.id) },
                    )
                }

                item { Spacer(Modifier.height(32.dp)) }
            }
        }
    }
}

@Composable
private fun TierChip(
    label: String,
    tier: Int?,
    selectedTier: Int?,
    onSelect: (Int?) -> Unit,
) {
    val c = Hark.colors
    val isSelected = selectedTier == tier
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) c.ink else c.paperRaised)
            .border(1.dp, if (isSelected) c.ink else c.inkHairline, RoundedCornerShape(16.dp))
            .clickable { onSelect(tier) }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = HarkType.meta,
            color = if (isSelected) c.paper else c.inkMuted,
        )
    }
}
