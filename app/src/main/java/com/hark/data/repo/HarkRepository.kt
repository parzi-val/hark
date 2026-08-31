package com.hark.data.repo

import com.hark.data.local.NoteDao
import com.hark.data.local.NoteEntity
import com.hark.data.local.Source
import com.hark.data.local.TaskDao
import com.hark.data.local.TaskEntity
import com.hark.domain.Action
import com.hark.domain.FocusedNote
import com.hark.domain.HarkAction
import com.hark.domain.NoteRef
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
    // Stream excludes Shelf notes — Shelf has its own reading list.
    val stream: Flow<List<StreamItem>> =
        combine(noteDao.observeAll(), taskDao.observeAll()) { notes, tasks ->
            val streamNotes = notes.filter { !it.shelf && !it.archived }
            val childrenByNote = tasks.filter { it.sourceNoteId != null }.groupBy { it.sourceNoteId }
            buildList {
                streamNotes.forEach { note ->
                    add(StreamItem.Note(note, childrenByNote[note.id].orEmpty().sortedBy { it.createdAt }))
                }
                tasks.filter { it.sourceNoteId == null }.forEach { add(StreamItem.Task(it)) }
            }.sortedByDescending { it.createdAt }
        }

    val openCount: Flow<Int> = taskDao.observeOpenCount()

    /** Raw streams for screens that need to slice by date/state themselves (e.g. Today).
     *  Archived notes are hidden everywhere except the (web) Archive view. */
    val tasks: Flow<List<TaskEntity>> = taskDao.observeAll()
    val notes: Flow<List<NoteEntity>> = noteDao.observeAll().map { list ->
        list.filterNot { it.archived }
    }

    val shelfNotes: Flow<List<NoteEntity>> = noteDao.observeAll().map { list ->
        list.filter { it.shelf && !it.archived }
    }

    val archivedNotes: Flow<List<NoteEntity>> = noteDao.observeAll().map { list ->
        list.filter { it.archived }
    }

    suspend fun searchNotes(query: String): List<NoteEntity> =
        if (query.isBlank()) emptyList() else noteDao.search("%${query.trim()}%")

    /** Up to three items for the 4×2 widget: skip completed tasks and shelf notes. */
    val widgetItems: Flow<List<StreamItem>> = stream.map { items ->
        items.filterNot { it is StreamItem.Task && it.task.done }.take(3)
    }

    suspend fun setTaskDone(task: TaskEntity, done: Boolean) {
        val now = Instant.now()
        taskDao.setDone(task.id, done, doneAt = if (done) now else null, updatedAt = now)
        if (done) onTaskCancelled(task.id)
        reconcileNoteArchive(task.sourceNoteId)
        onChanged()
    }

    /** Set a task aside (or bring it back). Deferring forces it open and drops it off the open
     *  count and Today; it stays visible (grayed) in the Stream. */
    suspend fun setTaskDeferred(task: TaskEntity, deferred: Boolean) {
        val now = Instant.now()
        taskDao.setDeferred(task.id, deferred, now)
        if (deferred) onTaskCancelled(task.id)
        reconcileNoteArchive(task.sourceNoteId)
        onChanged()
    }

    /** Auto-archive a note once every one of its tasks is complete. Monotonic — only ever sets
     *  archived = true; un-archiving is always explicit (note drawer / web Archive view), so it
     *  never fights a manual archive. */
    suspend fun reconcileNoteArchive(noteId: Long?) {
        if (noteId == null) return
        val tasks = taskDao.getForNote(noteId) // non-deleted (query filters deleted = 0)
        val allDone = tasks.isNotEmpty() && tasks.all { it.done }
        val note = noteDao.getById(noteId) ?: return
        if (allDone && !note.archived) noteDao.setArchived(noteId, true, Instant.now())
    }

    suspend fun archiveNote(id: Long) {
        noteDao.setArchived(id, true, Instant.now())
        onChanged()
    }

    suspend fun unarchiveNote(id: Long) {
        noteDao.setArchived(id, false, Instant.now())
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

    /** Create a blank Shelf note and return its id (for the full-screen writer). */
    suspend fun newShelfNote(): Long {
        val now = Instant.now()
        val noteId = noteDao.insert(
            NoteEntity(
                title = "",
                body = "",
                source = Source.TYPED,
                pinnedToWidget = false,
                shelf = true,
                createdAt = now,
                updatedAt = now,
            )
        )
        onChanged()
        return noteId
    }

    /** Move a note between Stream and Shelf. Shelving clears any widget pin. */
    suspend fun setShelf(id: Long, shelf: Boolean) {
        val now = Instant.now()
        val pinned = if (shelf) false else (noteDao.getById(id)?.pinnedToWidget ?: false)
        noteDao.setShelf(id, shelf, pinned, now)
        onChanged()
    }

    /** Recent notes as a lightweight index for the model to resolve "which note". */
    suspend fun recentNoteRefs(limit: Int = 40): List<NoteRef> {
        val allNotes = noteDao.getRecent(limit)
        val allTasks = taskDao.getAllActive()
        val countByNote = allTasks.filter { it.sourceNoteId != null }.groupingBy { it.sourceNoteId!! }.eachCount()

        return allNotes.map { n ->
            NoteRef(
                id = n.id,
                title = n.title,
                snippet = n.body.replace(Regex("""\s+"""), " ").trim().take(80),
                taskCount = countByNote[n.id] ?: 0,
            )
        }
    }

    suspend fun focusedNoteOf(id: Long): FocusedNote? {
        val note = noteDao.getById(id) ?: return null
        if (note.deleted) return null
        val tasks = taskDao.getForNote(id).filter { !it.deleted }.map { it.title }
        return FocusedNote(id = note.id, title = note.title, body = note.body, tasks = tasks)
    }

    /** Applies a HarkAction locally. Returns the affected note id. Never loses capture. */
    suspend fun applyAction(
        action: HarkAction,
        transcript: String,
        source: Source,
    ): Long {
        val now = Instant.now()

        val addTasks: suspend (Long) -> Unit = { noteId ->
            // Skip tasks already on the note — talk-to-edit re-extracts existing ones, which would
            // otherwise duplicate the whole checklist. (getForNote returns live tasks only.)
            val existing = taskDao.getForNote(noteId).map { it.title.trim().lowercase() }.toSet()
            val fresh = action.tasks.filter { it.title.trim().lowercase() !in existing }
            if (fresh.isNotEmpty()) {
                val tasks = fresh.map { t ->
                    TaskEntity(
                        title = t.title,
                        dueAt = t.dueAt,
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
        }

        if (action.action != Action.CREATE && action.targetNoteId != null) {
            val existing = noteDao.getById(action.targetNoteId)
            if (existing != null && !existing.deleted) {
                if (action.action == Action.APPEND) {
                    val add = action.body.trim()
                    val body = if (add.isNotEmpty()) {
                        if (existing.body.isNotBlank()) "${existing.body}\n\n$add" else add
                    } else existing.body
                    noteDao.update(
                        existing.copy(
                            body = body,
                            heardAs = trail(existing.heardAs, transcript, source),
                            updatedAt = now,
                        )
                    )
                } else { // Action.EDIT
                    noteDao.update(
                        existing.copy(
                            title = action.title?.ifBlank { null } ?: existing.title,
                            body = action.body,
                            heardAs = trail(existing.heardAs, transcript, source),
                            updatedAt = now,
                        )
                    )
                }
                addTasks(existing.id)
                onChanged()
                return existing.id
            }
            // Target missing -> falls through to CREATE
        }

        // CREATE
        val isLongVoice = source == Source.SPOKEN && transcript.length > SHELF_THRESHOLD
        val noteId = noteDao.insert(
            NoteEntity(
                title = action.title?.ifBlank { null } ?: transcript.take(40).ifBlank { "Untitled note" },
                // Empty body is intentional for a checklist (items carry the content); only fall
                // back to the raw transcript when there's nothing at all to show.
                body = action.body.ifBlank { if (action.tasks.isNotEmpty()) "" else transcript },
                heardAs = if (source == Source.SPOKEN) transcript else null,
                source = source,
                pinnedToWidget = false,
                shelf = isLongVoice,
                createdAt = now,
                updatedAt = now,
            )
        )
        addTasks(noteId)
        onChanged()
        return noteId
    }

    private fun trail(existing: String?, transcript: String, source: Source): String? {
        if (source != Source.SPOKEN) return existing
        return if (!existing.isNullOrBlank()) "$existing\n\n— — —\n$transcript" else transcript
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
                    shelf = false,
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

    /** Legacy helper — now wraps applyAction. */
    suspend fun saveTidied(result: TidyResult, heardAs: String?, source: Source): Long {
        val action = HarkAction(
            action = Action.CREATE,
            targetNoteId = null,
            title = result.title,
            body = result.note,
            tasks = result.tasks.map { com.hark.domain.HarkTask(it.title, it.due, it.dueHint) },
            reason = "tidy",
        )
        return applyAction(action, heardAs ?: result.note, source)
    }

    companion object {
        const val SHELF_THRESHOLD = 400
    }
}
