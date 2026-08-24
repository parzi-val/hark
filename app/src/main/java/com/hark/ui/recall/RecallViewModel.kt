package com.hark.ui.recall

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hark.ai.RecallService
import com.hark.data.local.NoteEntity
import com.hark.data.repo.HarkRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RecallUiState(
    val query: String = "",
    val loading: Boolean = false,
    val searched: Boolean = false,
    val answer: String? = null,
    val hits: List<NoteEntity> = emptyList(),
)

class RecallViewModel(
    private val repo: HarkRepository,
    private val recall: RecallService,
) : ViewModel() {

    private val _ui = MutableStateFlow(RecallUiState())
    val ui: StateFlow<RecallUiState> = _ui.asStateFlow()

    fun setQuery(q: String) = _ui.update { it.copy(query = q) }

    fun search() {
        val q = _ui.value.query.trim()
        if (q.isBlank()) return
        _ui.update { it.copy(loading = true, searched = true, answer = null, hits = emptyList()) }
        viewModelScope.launch {
            val hits = repo.searchNotes(q)
            _ui.update { it.copy(hits = hits) }
            val answer = recall.answer(q, hits)
            _ui.update { it.copy(loading = false, answer = answer) }
        }
    }
}
