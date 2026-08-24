package com.hark.ui.note

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hark.data.local.NoteEntity
import com.hark.data.local.TaskEntity
import com.hark.data.repo.HarkRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NoteDetailUiState(
    val note: NoteEntity? = null,
    val tasks: List<TaskEntity> = emptyList(),
    val isLoading: Boolean = true,
)

class NoteDetailViewModel(
    private val noteId: Long,
    private val repo: HarkRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(NoteDetailUiState())
    val ui: StateFlow<NoteDetailUiState> = _ui.asStateFlow()

    private var autoSaveJob: Job? = null

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            repo.observeNoteById(noteId).collect { note ->
                _ui.update { it.copy(note = note, isLoading = false) }
            }
        }
        viewModelScope.launch {
            repo.observeTasksForNote(noteId).collect { tasks ->
                _ui.update { it.copy(tasks = tasks) }
            }
        }
    }

    fun toggleTask(task: TaskEntity) {
        viewModelScope.launch {
            repo.setTaskDone(task, !task.done)
        }
    }

    fun togglePin() {
        val current = _ui.value.note ?: return
        viewModelScope.launch {
            repo.setNotePinned(noteId, !current.pinnedToWidget)
        }
    }

    fun updateContent(title: String, body: String) {
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(300)
            repo.updateNote(noteId, title, body)
        }
    }

    fun flushSave(title: String, body: String) {
        autoSaveJob?.cancel()
        viewModelScope.launch {
            repo.updateNote(noteId, title, body)
        }
    }

    fun deleteNote(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repo.deleteNote(noteId)
            onDeleted()
        }
    }

    fun addTask(title: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            repo.addTaskToNote(noteId, title)
        }
    }
}
