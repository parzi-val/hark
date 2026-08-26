package com.hark.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/** How an item entered Hark. */
enum class Source { SPOKEN, TYPED }

/**
 * A note — a page, not a card. Notes may own tasks (see [TaskEntity.sourceNoteId]).
 * [heardAs] keeps the raw transcript so a bad "tidy" never loses the original capture.
 */
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val body: String,
    val heardAs: String? = null,
    val source: Source = Source.TYPED,
    val pinnedToWidget: Boolean = false,
    val shelf: Boolean = false,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    // Sync prep — unused in v1, present so the schema never needs a migration for it.
    val remoteId: String? = null,
    val deleted: Boolean = false,
)

/**
 * A task in the one stream. [dueAt] is stored now; reminder notifications come in a later
 * phase. A task extracted from a note points back to it via [sourceNoteId].
 */
@Entity(
    tableName = "tasks",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceNoteId"],
            onDelete = ForeignKey.SET_NULL,
        )
    ],
    indices = [Index("sourceNoteId")],
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val done: Boolean = false,
    val doneAt: Instant? = null,
    val dueAt: Instant? = null,
    val dueHint: String? = null,
    val sourceNoteId: Long? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    val remoteId: String? = null,
    val deleted: Boolean = false,
)
