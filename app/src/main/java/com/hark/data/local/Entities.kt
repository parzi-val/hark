package com.hark.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

/** How an item entered Hark. */
enum class Source { SPOKEN, TYPED }

/**
 * A note — a page, not a card. Notes may own tasks (see [TaskEntity.sourceNoteId]).
 * [heardAs] keeps the raw transcript so a bad "tidy" never loses the original capture.
 */
@Entity(tableName = "notes", indices = [Index("remoteId")])
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val body: String,
    val heardAs: String? = null,
    val source: Source = Source.TYPED,
    val pinnedToWidget: Boolean = false,
    val shelf: Boolean = false,
    // Filed away: set automatically when all of a note's tasks complete, or manually from the
    // note drawer. Archived notes leave the Stream/Today and live only in the Archive (web).
    val archived: Boolean = false,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    // Stable global id for cross-device sync (the Drive appData snapshot key). Auto-assigned
    // to new rows; existing null rows are backfilled on startup. Column stays nullable so no
    // Room migration is needed.
    val remoteId: String? = UUID.randomUUID().toString(),
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
    indices = [Index("sourceNoteId"), Index("remoteId")],
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val done: Boolean = false,
    val doneAt: Instant? = null,
    // Set aside: open but not on the active plate. Only meaningful when !done; kept off the
    // open count and Today, shown grayed in the Stream.
    val deferred: Boolean = false,
    val dueAt: Instant? = null,
    val dueHint: String? = null,
    val sourceNoteId: Long? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    val remoteId: String? = UUID.randomUUID().toString(),
    val deleted: Boolean = false,
)
