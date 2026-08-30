package com.hark.ui.stream

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hark.data.local.TaskEntity
import com.hark.data.repo.HarkRepository
import com.hark.domain.StreamItem
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class StreamFilter { ALL, OPEN, NOTES, ARCHIVE }

data class StreamUiState(
    val items: List<StreamItem> = emptyList(),
    val openCount: Int = 0,
    val shelfCount: Int = 0,
    val archived: List<StreamItem.Note> = emptyList(),
    val filter: StreamFilter = StreamFilter.ALL,
) {
    val visible: List<StreamItem> get() = when (filter) {
        StreamFilter.ALL -> items
        // OPEN keeps the parent-note grouping: each note shows only its not-done tasks nested
        // under its header (deferred stay visible, grayed); loose open tasks stand alone.
        StreamFilter.OPEN -> items.mapNotNull { item ->
            when (item) {
                is StreamItem.Note -> item.tasks.filter { !it.done }
                    .takeIf { it.isNotEmpty() }
                    ?.let { StreamItem.Note(item.note, it) }
                is StreamItem.Task -> if (!item.task.done) item else null
            }
        }
        // NOTES = notes as documents (no task checklists — that's what ALL is for).
        StreamFilter.NOTES -> items.filterIsInstance<StreamItem.Note>()
            .map { StreamItem.Note(it.note, emptyList()) }
        StreamFilter.ARCHIVE -> archived
    }
}

class StreamViewModel(private val repo: HarkRepository) : ViewModel() {

    private val filter = kotlinx.coroutines.flow.MutableStateFlow(StreamFilter.ALL)

    val ui: StateFlow<StreamUiState> =
        combine(repo.stream, repo.openCount, repo.shelfNotes, repo.archivedNotes, filter) { items, open, shelfNotes, archived, f ->
            StreamUiState(
                items = items,
                openCount = open,
                shelfCount = shelfNotes.size,
                archived = archived.map { StreamItem.Note(it, emptyList()) },
                filter = f,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StreamUiState())

    fun setFilter(f: StreamFilter) { filter.value = f }

    fun toggle(task: TaskEntity) {
        viewModelScope.launch { repo.setTaskDone(task, !task.done) }
    }

    fun toggleDeferred(task: TaskEntity) {
        viewModelScope.launch { repo.setTaskDeferred(task, !task.deferred) }
    }

    fun updateTask(taskId: Long, title: String, dueHint: String?) {
        viewModelScope.launch { repo.updateTask(taskId, title, dueHint) }
    }

    fun deleteTask(taskId: Long) {
        viewModelScope.launch { repo.deleteTask(taskId) }
    }
}
