package com.hark.ui.note

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hark.ai.HarkService
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
import java.time.Instant

data class NoteDetailUiState(
    val note: NoteEntity? = null,
    val tasks: List<TaskEntity> = emptyList(),
    val isLoading: Boolean = true,
    val isShaping: Boolean = false,
    val shapeError: String? = null,
)

class NoteDetailViewModel(
    private val noteId: Long,
    private val repo: HarkRepository,
    private val harkService: HarkService,
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

    fun toggleShelf() {
        val current = _ui.value.note ?: return
        viewModelScope.launch {
            repo.setShelf(noteId, !current.shelf)
        }
    }

    fun shape(currentTitle: String, currentBody: String, onResult: (String, String) -> Unit = { _, _ -> }) {
        if (_ui.value.isShaping) return
        val note = _ui.value.note ?: return
        val source = if (!note.heardAs.isNullOrBlank()) note.heardAs!! else currentBody
        if (source.isBlank()) {
            _ui.update { it.copy(shapeError = "Note is empty.") }
            return
        }

        viewModelScope.launch {
            _ui.update { it.copy(isShaping = true, shapeError = null) }
            try {
                val result = harkService.shape(
                    title = currentTitle,
                    body = source,
                    extractTasks = false,
                )
                repo.updateNote(noteId, result.title, result.body)
                _ui.update {
                    it.copy(
                        isShaping = false,
                        note = note.copy(title = result.title, body = result.body, updatedAt = Instant.now()),
                    )
                }
                onResult(result.title, result.body)
            } catch (e: Exception) {
                _ui.update {
                    it.copy(
                        isShaping = false,
                        shapeError = e.message ?: "Failed to shape note. Check your API key in Settings.",
                    )
                }
            }
        }
    }

    fun clearShapeError() {
        _ui.update { it.copy(shapeError = null) }
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

    fun closeNote(title: String, body: String, onClose: () -> Unit) {
        autoSaveJob?.cancel()
        viewModelScope.launch {
            val tasks = _ui.value.tasks
            if (title.isBlank() && body.isBlank() && tasks.isEmpty()) {
                repo.deleteNote(noteId)
            } else {
                repo.updateNote(noteId, title, body)
            }
            onClose()
        }
    }

    fun deleteNote(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repo.deleteNote(noteId)
            onDeleted()
        }
    }

    fun archiveNote(title: String, body: String, onArchived: () -> Unit) {
        autoSaveJob?.cancel()
        viewModelScope.launch {
            // Persist pending edits, then file it away — one coroutine so the order is guaranteed.
            repo.updateNote(noteId, title, body)
            repo.archiveNote(noteId)
            onArchived()
        }
    }

    fun unarchiveNote(onDone: () -> Unit) {
        viewModelScope.launch {
            repo.unarchiveNote(noteId)
            onDone()
        }
    }

    fun addTask(title: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            repo.addTaskToNote(noteId, title)
        }
    }
}
