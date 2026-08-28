package com.hark.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hark.ai.SettingsStore
import com.hark.data.local.NoteEntity
import com.hark.data.local.TaskEntity
import com.hark.data.repo.HarkRepository
import com.hark.data.repo.LexiconRepository
import com.hark.domain.LexiconWord
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
    val wordOfTheDay: LexiconWord? = null,
    val showLexiconCard: Boolean = true,
) {
    val isEmpty: Boolean get() = overdue.isEmpty() && dueToday.isEmpty() && writtenToday.isEmpty() && (!showLexiconCard || wordOfTheDay == null)
}

class TodayViewModel(
    private val repo: HarkRepository,
    private val lexiconRepo: LexiconRepository,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    val ui: StateFlow<TodayUiState> =
        combine(repo.tasks, repo.notes, settingsStore.settings) { tasks, notes, settings ->
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            fun dateOf(t: TaskEntity) = t.dueAt?.atZone(zone)?.toLocalDate()
            val open = tasks.filter { !it.done }
            val word = lexiconRepo.getWordForDate(today)
            TodayUiState(
                overdue = open.filter { dateOf(it)?.isBefore(today) == true }.sortedBy { it.dueAt },
                dueToday = open.filter { dateOf(it) == today },
                writtenToday = notes.filter { it.createdAt.atZone(zone).toLocalDate() == today },
                wordOfTheDay = word,
                showLexiconCard = settings.showWordOfTheDay,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())

    fun toggle(task: TaskEntity) {
        viewModelScope.launch { repo.setTaskDone(task, !task.done) }
    }
}
