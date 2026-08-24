package com.hark.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hark.data.local.NoteEntity
import com.hark.data.local.TaskEntity
import com.hark.data.repo.HarkRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

data class TodayUiState(
    val overdue: List<TaskEntity> = emptyList(),
    val dueToday: List<TaskEntity> = emptyList(),
    val writtenToday: List<NoteEntity> = emptyList(),
) {
    val isEmpty: Boolean get() = overdue.isEmpty() && dueToday.isEmpty() && writtenToday.isEmpty()
}

class TodayViewModel(private val repo: HarkRepository) : ViewModel() {

    val ui: StateFlow<TodayUiState> =
        combine(repo.tasks, repo.notes) { tasks, notes ->
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            fun dateOf(t: TaskEntity) = t.dueAt?.atZone(zone)?.toLocalDate()
            val open = tasks.filter { !it.done }
            TodayUiState(
                overdue = open.filter { dateOf(it)?.isBefore(today) == true }.sortedBy { it.dueAt },
                dueToday = open.filter { dateOf(it) == today },
                writtenToday = notes.filter { it.createdAt.atZone(zone).toLocalDate() == today },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())

    fun toggle(task: TaskEntity) {
        viewModelScope.launch { repo.setTaskDone(task, !task.done) }
    }
}
