package com.hark.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hark.data.local.NoteEntity
import com.hark.data.repo.HarkRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val hits: List<NoteEntity> = emptyList(),
)

/** Plain local text search over notes (title/body/heardAs LIKE). No AI — mirrors the web search. */
class SearchViewModel(private val repo: HarkRepository) : ViewModel() {
    private val _ui = MutableStateFlow(SearchUiState())
    val ui: StateFlow<SearchUiState> = _ui.asStateFlow()
    private var job: Job? = null

    fun setQuery(q: String) {
        _ui.update { it.copy(query = q) }
        job?.cancel()
        if (q.isBlank()) {
            _ui.update { it.copy(hits = emptyList()) }
            return
        }
        job = viewModelScope.launch {
            delay(180) // debounce keystrokes
            _ui.update { it.copy(hits = repo.searchNotes(q)) }
        }
    }
}
