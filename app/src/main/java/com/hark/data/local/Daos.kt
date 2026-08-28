package com.hark.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface NoteDao {
    @Insert
    suspend fun insert(note: NoteEntity): Long

    @Update
    suspend fun update(note: NoteEntity)

    @Query("SELECT * FROM notes WHERE deleted = 0 ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id AND deleted = 0")
    fun observeById(id: Long): Flow<NoteEntity?>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getById(id: Long): NoteEntity?

    @Query("UPDATE notes SET pinnedToWidget = :pinned, updatedAt = :now WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean, now: Instant)

    @Query("UPDATE notes SET shelf = :shelf, pinnedToWidget = :pinned, updatedAt = :now WHERE id = :id")
    suspend fun setShelf(id: Long, shelf: Boolean, pinned: Boolean, now: Instant)

    @Query("UPDATE notes SET title = :title, body = :body, updatedAt = :now WHERE id = :id")
    suspend fun updateContent(id: Long, title: String, body: String, now: Instant)

    @Query("UPDATE notes SET deleted = 1, updatedAt = :now WHERE id = :id")
    suspend fun delete(id: Long, now: Instant)

    @Query("SELECT COUNT(*) FROM notes WHERE deleted = 0")
    suspend fun count(): Int

    @Query("SELECT * FROM notes WHERE deleted = 0 ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 40): List<NoteEntity>

    @Query(
        "SELECT * FROM notes WHERE deleted = 0 AND " +
            "(title LIKE :like OR body LIKE :like OR heardAs LIKE :like) " +
            "ORDER BY createdAt DESC LIMIT 20",
    )
    suspend fun search(like: String): List<NoteEntity>

    // ---- sync ----
    @Query("SELECT * FROM notes")
    suspend fun getAll(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE remoteId = :rid LIMIT 1")
    suspend fun getByRemoteId(rid: String): NoteEntity?
}

@Dao
interface TaskDao {
    @Insert
    suspend fun insert(task: TaskEntity): Long

    @Insert
    suspend fun insertAll(tasks: List<TaskEntity>): List<Long>

    @Update
    suspend fun update(task: TaskEntity)

    @Query("SELECT * FROM tasks WHERE deleted = 0 ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE deleted = 0")
    suspend fun getAllActive(): List<TaskEntity>

    @Query("SELECT COUNT(*) FROM tasks WHERE deleted = 0 AND done = 0")
    fun observeOpenCount(): Flow<Int>

    @Query("UPDATE tasks SET done = :done, doneAt = :doneAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setDone(id: Long, done: Boolean, doneAt: Instant?, updatedAt: Instant)

    @Query("SELECT * FROM tasks WHERE sourceNoteId = :noteId AND deleted = 0 ORDER BY createdAt ASC")
    fun observeForNote(noteId: Long): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE sourceNoteId = :noteId AND deleted = 0 ORDER BY createdAt ASC")
    suspend fun getForNote(noteId: Long): List<TaskEntity>

    @Query("UPDATE tasks SET title = :title, dueHint = :dueHint, dueAt = :dueAt, updatedAt = :now WHERE id = :id")
    suspend fun updateContent(id: Long, title: String, dueHint: String?, dueAt: Instant?, now: Instant)

    @Query("UPDATE tasks SET deleted = 1, updatedAt = :now WHERE sourceNoteId = :noteId")
    suspend fun deleteForNote(noteId: Long, now: Instant)

    @Query("UPDATE tasks SET deleted = 1, updatedAt = :now WHERE id = :id")
    suspend fun delete(id: Long, now: Instant)

    @Query("SELECT COUNT(*) FROM tasks WHERE deleted = 0")
    suspend fun count(): Int

    // ---- sync ----
    @Query("SELECT * FROM tasks")
    suspend fun getAll(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE remoteId = :rid LIMIT 1")
    suspend fun getByRemoteId(rid: String): TaskEntity?
}
