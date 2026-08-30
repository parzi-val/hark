package com.hark.sync

import androidx.room.withTransaction
import com.hark.data.local.HarkDatabase
import com.hark.data.local.NoteEntity
import com.hark.data.local.Source
import com.hark.data.local.TaskEntity
import java.time.Instant

/** Bridge between Room and the canonical [Snapshot]. Instants are stored as epoch millis so
 *  timestamps match the Web client for cross-device last-write-wins. */
class SyncLocal(private val db: HarkDatabase) {
    private val noteDao = db.noteDao()
    private val taskDao = db.taskDao()

    /** Whole local store (tombstones included) as a snapshot. */
    suspend fun export(): Snapshot {
        val notes = noteDao.getAll()
        val tasks = taskDao.getAll()

        val uidByLocalId = HashMap<Long, String>()
        for (n in notes) n.remoteId?.let { uidByLocalId[n.id] = it }

        val snapNotes = notes.mapNotNull { n ->
            val uid = n.remoteId ?: return@mapNotNull null
            SyncNote(
                uid = uid,
                title = n.title,
                body = n.body,
                heardAs = n.heardAs,
                source = n.source.name, // SPOKEN | TYPED (matches canonical)
                pinnedToWidget = n.pinnedToWidget,
                shelf = n.shelf,
                archived = n.archived,
                createdAt = n.createdAt.toEpochMilli(),
                updatedAt = n.updatedAt.toEpochMilli(),
                deleted = n.deleted,
            )
        }
        val snapTasks = tasks.mapNotNull { t ->
            val uid = t.remoteId ?: return@mapNotNull null
            SyncTask(
                uid = uid,
                noteUid = t.sourceNoteId?.let { uidByLocalId[it] },
                title = t.title,
                done = t.done,
                doneAt = t.doneAt?.toEpochMilli(),
                dueAt = t.dueAt?.toEpochMilli(),
                dueHint = t.dueHint,
                deferred = t.deferred,
                createdAt = t.createdAt.toEpochMilli(),
                updatedAt = t.updatedAt.toEpochMilli(),
                deleted = t.deleted,
            )
        }
        return Snapshot(SNAPSHOT_VERSION, System.currentTimeMillis(), snapNotes, snapTasks)
    }

    private fun isStarterTask(title: String): Boolean {
        val t = title.trim().lowercase()
        return t.startsWith("tap talk or hold space") ||
                t.startsWith("configure your groq") ||
                t.startsWith("set your groq") ||
                t.startsWith("add the hark") ||
                t.startsWith("tap talk and speak")
    }

    /** Purge local starter items if remote snapshot already has user notes. */
    suspend fun purgeStarterIfRemotePresent(remoteSnap: Snapshot) {
        val remoteUids = remoteSnap.notes.map { it.uid }.toSet()
        for (n in noteDao.getAll()) {
            val titleLower = n.title.trim().lowercase()
            if ((titleLower == "welcome to hark" || titleLower == "welcome to hark.") && n.remoteId !in remoteUids) {
                noteDao.delete(n.id, Instant.now())
                taskDao.deleteForNote(n.id, Instant.now())
            }
        }
        for (t in taskDao.getAll()) {
            if (isStarterTask(t.title)) {
                taskDao.delete(t.id, Instant.now())
            }
        }
    }

    /** Write a merged snapshot back to Room. Update in place only when the snapshot is
     *  strictly newer (so a concurrent local edit is never clobbered); insert unseen uids;
     *  skip brand-new tombstones (nothing to hide locally). */
    suspend fun apply(snap: Snapshot): Boolean = db.withTransaction {
        var changed = false
        for (s in snap.notes) {
            val local = noteDao.getByRemoteId(s.uid)
            val src = if (s.source == "SPOKEN") Source.SPOKEN else Source.TYPED
            if (local != null) {
                if (s.updatedAt > local.updatedAt.toEpochMilli()) {
                    noteDao.update(
                        local.copy(
                            title = s.title,
                            body = s.body,
                            heardAs = s.heardAs,
                            source = src,
                            pinnedToWidget = s.pinnedToWidget,
                            shelf = s.shelf,
                            archived = s.archived,
                            createdAt = Instant.ofEpochMilli(s.createdAt),
                            updatedAt = Instant.ofEpochMilli(s.updatedAt),
                            deleted = s.deleted,
                        ),
                    )
                    changed = true
                }
            } else if (!s.deleted) {
                noteDao.insert(
                    NoteEntity(
                        title = s.title,
                        body = s.body,
                        heardAs = s.heardAs,
                        source = src,
                        pinnedToWidget = s.pinnedToWidget,
                        shelf = s.shelf,
                        archived = s.archived,
                        createdAt = Instant.ofEpochMilli(s.createdAt),
                        updatedAt = Instant.ofEpochMilli(s.updatedAt),
                        remoteId = s.uid,
                        deleted = s.deleted,
                    ),
                )
                changed = true
            }
        }

        for (s in snap.tasks) {
            val parentLocalId = s.noteUid?.let { noteDao.getByRemoteId(it)?.id }
            val local = taskDao.getByRemoteId(s.uid)
            if (local != null) {
                if (s.updatedAt > local.updatedAt.toEpochMilli()) {
                    taskDao.update(
                        local.copy(
                            title = s.title,
                            done = s.done,
                            doneAt = s.doneAt?.let(Instant::ofEpochMilli),
                            dueAt = s.dueAt?.let(Instant::ofEpochMilli),
                            dueHint = s.dueHint,
                            deferred = s.deferred,
                            sourceNoteId = parentLocalId,
                            createdAt = Instant.ofEpochMilli(s.createdAt),
                            updatedAt = Instant.ofEpochMilli(s.updatedAt),
                            deleted = s.deleted,
                        ),
                    )
                    changed = true
                }
            } else if (!s.deleted) {
                taskDao.insert(
                    TaskEntity(
                        title = s.title,
                        done = s.done,
                        doneAt = s.doneAt?.let(Instant::ofEpochMilli),
                        dueAt = s.dueAt?.let(Instant::ofEpochMilli),
                        dueHint = s.dueHint,
                        deferred = s.deferred,
                        sourceNoteId = parentLocalId,
                        createdAt = Instant.ofEpochMilli(s.createdAt),
                        updatedAt = Instant.ofEpochMilli(s.updatedAt),
                        remoteId = s.uid,
                        deleted = s.deleted,
                    ),
                )
                changed = true
            }
        }
        changed
    }

    /** Give any pre-sync rows (remoteId == null) a stable uid. One-time, on startup. */
    suspend fun backfillUids() {
        for (n in noteDao.getAll()) {
            if (n.remoteId == null) noteDao.update(n.copy(remoteId = java.util.UUID.randomUUID().toString()))
        }
        for (t in taskDao.getAll()) {
            if (t.remoteId == null) taskDao.update(t.copy(remoteId = java.util.UUID.randomUUID().toString()))
        }
    }
}
