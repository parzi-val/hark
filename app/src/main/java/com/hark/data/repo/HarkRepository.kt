package com.hark.data.repo

import com.hark.data.local.NoteDao
import com.hark.data.local.NoteEntity
import com.hark.data.local.Source
import com.hark.data.local.TaskDao
import com.hark.data.local.TaskEntity
import com.hark.domain.StreamItem
import com.hark.domain.TidyResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Instant

/**
 * The one place features read and write the stream. Notes and tasks live in separate tables
 * and are merged here into a single time-ordered river.
 *
 * [onChanged] is invoked after every write so the home-screen widget can refresh; it defaults
 * to a no-op (wired to Glance in [com.hark.di.AppContainer]).
 */
class HarkRepository(
    private val noteDao: NoteDao,
    private val taskDao: TaskDao,
    private val onChanged: suspend () -> Unit = {},
    private val onTaskScheduled: (taskId: Long, title: String, dueAt: Instant) -> Unit = { _, _, _ -> },
    private val onTaskCancelled: (taskId: Long) -> Unit = {},
) {
    val stream: Flow<List<StreamItem>> =
        combine(noteDao.observeAll(), taskDao.observeAll()) { notes, tasks ->
            // Tasks extracted from a note travel with that note; only standalone tasks stand alone.
            val childrenByNote = tasks.filter { it.sourceNoteId != null }.groupBy { it.sourceNoteId }
            buildList {
                notes.forEach { note ->
                    add(StreamItem.Note(note, childrenByNote[note.id].orEmpty().sortedBy { it.createdAt }))
                }
                tasks.filter { it.sourceNoteId == null }.forEach { add(StreamItem.Task(it)) }
            }.sortedByDescending { it.createdAt }
        }

    val openCount: Flow<Int> = taskDao.observeOpenCount()

    /** Raw streams for screens that need to slice by date/state themselves (e.g. Today). */
    val tasks: Flow<List<TaskEntity>> = taskDao.observeAll()
    val notes: Flow<List<NoteEntity>> = noteDao.observeAll()

    suspend fun searchNotes(query: String): List<NoteEntity> =
        if (query.isBlank()) emptyList() else noteDao.search("%${query.trim()}%")

    /** Up to three items for the 4×2 widget: skip completed tasks. */
    val widgetItems: Flow<List<StreamItem>> = stream.map { items ->
        items.filterNot { it is StreamItem.Task && it.task.done }.take(3)
    }

    suspend fun setTaskDone(task: TaskEntity, done: Boolean) {
        val now = Instant.now()
        taskDao.setDone(task.id, done, doneAt = if (done) now else null, updatedAt = now)
        if (done) onTaskCancelled(task.id)
        onChanged()
    }

    suspend fun getNoteById(id: Long): NoteEntity? = noteDao.getById(id)

    fun observeNoteById(id: Long): Flow<NoteEntity?> = noteDao.observeById(id)

    fun observeTasksForNote(noteId: Long): Flow<List<TaskEntity>> = taskDao.observeForNote(noteId)

    suspend fun setNotePinned(id: Long, pinned: Boolean) {
        noteDao.setPinned(id, pinned, Instant.now())
        onChanged()
    }

    suspend fun updateNote(id: Long, title: String, body: String) {
        noteDao.updateContent(id, title.trim().ifBlank { "Untitled note" }, body.trim(), Instant.now())
        onChanged()
    }

    suspend fun deleteNote(id: Long) {
        val now = Instant.now()
        noteDao.delete(id, now)
        taskDao.deleteForNote(id, now)
        onChanged()
    }

    suspend fun updateTask(id: Long, title: String, dueHint: String? = null, dueAt: Instant? = null) {
        val now = Instant.now()
        taskDao.updateContent(id, title.trim(), dueHint?.trim()?.ifBlank { null }, dueAt, now)
        if (dueAt != null) {
            onTaskScheduled(id, title.trim(), dueAt)
        } else {
            onTaskCancelled(id)
        }
        onChanged()
    }

    suspend fun deleteTask(id: Long) {
        val now = Instant.now()
        taskDao.delete(id, now)
        onTaskCancelled(id)
        onChanged()
    }

    suspend fun createStandaloneTask(title: String, dueAt: Instant? = null, dueHint: String? = null): Long {
        val now = Instant.now()
        val taskId = taskDao.insert(
            TaskEntity(
                title = title.trim(),
                dueAt = dueAt,
                dueHint = dueHint,
                sourceNoteId = null,
                createdAt = now,
                updatedAt = now,
            )
        )
        if (dueAt != null) {
            onTaskScheduled(taskId, title.trim(), dueAt)
        }
        onChanged()
        return taskId
    }

    suspend fun addTaskToNote(noteId: Long, title: String, dueAt: Instant? = null, dueHint: String? = null): Long {
        val now = Instant.now()
        val taskId = taskDao.insert(
            TaskEntity(
                title = title.trim(),
                dueAt = dueAt,
                dueHint = dueHint,
                sourceNoteId = noteId,
                createdAt = now,
                updatedAt = now,
            )
        )
        if (dueAt != null) {
            onTaskScheduled(taskId, title.trim(), dueAt)
        }
        onChanged()
        return taskId
    }

    suspend fun saveTypedNote(title: String, body: String): Long {
        val now = Instant.now()
        val noteId = noteDao.insert(
            NoteEntity(
                title = title.trim().ifBlank { "Untitled note" },
                body = body.trim(),
                heardAs = null,
                source = Source.TYPED,
                createdAt = now,
                updatedAt = now,
            )
        )
        onChanged()
        return noteId
    }

    suspend fun seedStarterNoteIfEmpty() {
        if (noteDao.count() == 0 && taskDao.count() == 0) {
            val now = Instant.now()
            val noteId = noteDao.insert(
                NoteEntity(
                    title = "Welcome to Hark",
                    body = "Speak a messy thought; Hark tidies it into a clean note plus extracted tasks. Tap TALK to capture anything on your mind, or add the Hark widget to your home screen.",
                    heardAs = "Welcome to Hark. Speak a messy thought and Hark will tidy it into a clean note and extract tasks for you. Try tapping talk or adding the home screen widget.",
                    source = Source.SPOKEN,
                    pinnedToWidget = true,
                    createdAt = now,
                    updatedAt = now,
                )
            )
            taskDao.insertAll(
                listOf(
                    TaskEntity(
                        title = "Add the Hark 4×2 widget to your home screen",
                        dueAt = now,
                        dueHint = "today",
                        sourceNoteId = noteId,
                        createdAt = now,
                        updatedAt = now,
                    ),
                    TaskEntity(
                        title = "Tap TALK and speak a messy thought",
                        dueAt = now,
                        dueHint = "today",
                        sourceNoteId = noteId,
                        createdAt = now.plusMillis(1),
                        updatedAt = now.plusMillis(1),
                    ),
                    TaskEntity(
                        title = "Set your Groq/OpenAI API key in Settings",
                        dueAt = null,
                        dueHint = null,
                        sourceNoteId = noteId,
                        createdAt = now.plusMillis(2),
                        updatedAt = now.plusMillis(2),
                    ),
                )
            )
            onChanged()
        }
    }

    /** Persist a tidied capture: the note, then its extracted tasks pointing back to it. */
    suspend fun saveTidied(result: TidyResult, heardAs: String?, source: Source): Long {
        val now = Instant.now()
        val noteId = noteDao.insert(
            NoteEntity(
                title = result.title,
                body = result.note,
                heardAs = heardAs,
                source = source,
                createdAt = now,
                updatedAt = now,
            )
        )
        if (result.tasks.isNotEmpty()) {
            val tasks = result.tasks.map { t ->
                TaskEntity(
                    title = t.title,
                    dueAt = t.due,
                    dueHint = t.dueHint,
                    sourceNoteId = noteId,
                    createdAt = now,
                    updatedAt = now,
                )
            }
            val taskIds = taskDao.insertAll(tasks)
            tasks.forEachIndexed { i, t ->
                if (t.dueAt != null) {
                    val id = taskIds.getOrNull(i) ?: 0L
                    if (id > 0) onTaskScheduled(id, t.title, t.dueAt)
                }
            }
        }
        onChanged()
        return noteId
    }
}
