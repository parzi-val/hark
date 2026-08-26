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

enum class StreamFilter { ALL, OPEN, NOTES }

data class StreamUiState(
    val items: List<StreamItem> = emptyList(),
    val openCount: Int = 0,
    val shelfCount: Int = 0,
    val filter: StreamFilter = StreamFilter.ALL,
) {
    val visible: List<StreamItem> get() = when (filter) {
        StreamFilter.ALL -> items
        StreamFilter.OPEN -> items.flatMap { item ->
            when (item) {
                is StreamItem.Note -> item.tasks.filter { !it.done }.map { StreamItem.Task(it) }
                is StreamItem.Task -> if (!item.task.done) listOf(item) else emptyList()
            }
        }
        StreamFilter.NOTES -> items.filterIsInstance<StreamItem.Note>()
    }
}

class StreamViewModel(private val repo: HarkRepository) : ViewModel() {

    private val filter = kotlinx.coroutines.flow.MutableStateFlow(StreamFilter.ALL)

    val ui: StateFlow<StreamUiState> =
        combine(repo.stream, repo.openCount, repo.shelfNotes, filter) { items, open, shelfNotes, f ->
            StreamUiState(items = items, openCount = open, shelfCount = shelfNotes.size, filter = f)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StreamUiState())

    fun setFilter(f: StreamFilter) { filter.value = f }

    fun toggle(task: TaskEntity) {
        viewModelScope.launch { repo.setTaskDone(task, !task.done) }
    }

    fun updateTask(taskId: Long, title: String, dueHint: String?) {
        viewModelScope.launch { repo.updateTask(taskId, title, dueHint) }
    }

    fun deleteTask(taskId: Long) {
        viewModelScope.launch { repo.deleteTask(taskId) }
    }
}
