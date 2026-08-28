package com.hark.sync

import org.json.JSONArray
import org.json.JSONObject

// Canonical, platform-neutral sync format shared with the Web app via one JSON snapshot
// in Google Drive's appDataFolder. Mirrors web/src/sync/merge.ts exactly (keys, enums,
// epoch-millis timestamps) so the two clients read each other's data.

const val SNAPSHOT_VERSION = 1

interface SyncRecord {
    val uid: String
    val updatedAt: Long
}

data class SyncNote(
    override val uid: String,
    val title: String,
    val body: String,
    val heardAs: String?,
    val source: String, // "SPOKEN" | "TYPED"
    val pinnedToWidget: Boolean,
    val shelf: Boolean,
    val createdAt: Long,
    override val updatedAt: Long,
    val deleted: Boolean,
) : SyncRecord

data class SyncTask(
    override val uid: String,
    val noteUid: String?, // parent note's uid, resolved to a local id per device
    val title: String,
    val done: Boolean,
    val doneAt: Long?,
    val dueAt: Long?,
    val dueHint: String?,
    val createdAt: Long,
    override val updatedAt: Long,
    val deleted: Boolean,
) : SyncRecord

data class Snapshot(
    val version: Int = SNAPSHOT_VERSION,
    val updatedAt: Long = 0,
    val notes: List<SyncNote> = emptyList(),
    val tasks: List<SyncTask> = emptyList(),
)

/** Per-record last-write-wins by [SyncRecord.updatedAt]; tombstones participate, union of
 *  both sides, ties favour the incoming (remote) record — deterministic and idempotent. */
fun <T : SyncRecord> mergeRecords(local: List<T>, remote: List<T>): List<T> {
    val byUid = LinkedHashMap<String, T>()
    for (r in local) byUid[r.uid] = r
    for (r in remote) {
        val cur = byUid[r.uid]
        if (cur == null || r.updatedAt >= cur.updatedAt) byUid[r.uid] = r
    }
    return byUid.values.toList()
}

fun mergeSnapshots(local: Snapshot, remote: Snapshot): Snapshot = Snapshot(
    version = maxOf(local.version, remote.version, SNAPSHOT_VERSION),
    updatedAt = System.currentTimeMillis(),
    notes = mergeRecords(local.notes, remote.notes),
    tasks = mergeRecords(local.tasks, remote.tasks),
)

// ---- JSON (org.json) ----

fun Snapshot.toJson(): String {
    val notesArr = JSONArray()
    for (n in notes) {
        notesArr.put(
            JSONObject()
                .put("uid", n.uid)
                .put("title", n.title)
                .put("body", n.body)
                .put("heardAs", n.heardAs ?: JSONObject.NULL)
                .put("source", n.source)
                .put("pinnedToWidget", n.pinnedToWidget)
                .put("shelf", n.shelf)
                .put("createdAt", n.createdAt)
                .put("updatedAt", n.updatedAt)
                .put("deleted", n.deleted),
        )
    }
    val tasksArr = JSONArray()
    for (t in tasks) {
        tasksArr.put(
            JSONObject()
                .put("uid", t.uid)
                .put("noteUid", t.noteUid ?: JSONObject.NULL)
                .put("title", t.title)
                .put("done", t.done)
                .put("doneAt", t.doneAt ?: JSONObject.NULL)
                .put("dueAt", t.dueAt ?: JSONObject.NULL)
                .put("dueHint", t.dueHint ?: JSONObject.NULL)
                .put("createdAt", t.createdAt)
                .put("updatedAt", t.updatedAt)
                .put("deleted", t.deleted),
        )
    }
    return JSONObject()
        .put("version", version)
        .put("updatedAt", updatedAt)
        .put("notes", notesArr)
        .put("tasks", tasksArr)
        .toString()
}

/** Defensive parse: anything missing/corrupt reads as an empty snapshot, so a bad remote
 *  file never wipes local — the merge just treats it as nothing to add. */
fun parseSnapshot(text: String?): Snapshot {
    if (text.isNullOrBlank()) return Snapshot()
    return try {
        val root = JSONObject(text)
        val notes = ArrayList<SyncNote>()
        val na = root.optJSONArray("notes") ?: JSONArray()
        for (i in 0 until na.length()) {
            val o = na.getJSONObject(i)
            notes.add(
                SyncNote(
                    uid = o.getString("uid"),
                    title = o.optString("title", ""),
                    body = o.optString("body", ""),
                    heardAs = o.optStringOrNull("heardAs"),
                    source = o.optString("source", "TYPED"),
                    pinnedToWidget = o.optBoolean("pinnedToWidget", false),
                    shelf = o.optBoolean("shelf", false),
                    createdAt = o.optLong("createdAt", 0),
                    updatedAt = o.optLong("updatedAt", 0),
                    deleted = o.optBoolean("deleted", false),
                ),
            )
        }
        val tasks = ArrayList<SyncTask>()
        val ta = root.optJSONArray("tasks") ?: JSONArray()
        for (i in 0 until ta.length()) {
            val o = ta.getJSONObject(i)
            tasks.add(
                SyncTask(
                    uid = o.getString("uid"),
                    noteUid = o.optStringOrNull("noteUid"),
                    title = o.optString("title", ""),
                    done = o.optBoolean("done", false),
                    doneAt = o.optLongOrNull("doneAt"),
                    dueAt = o.optLongOrNull("dueAt"),
                    dueHint = o.optStringOrNull("dueHint"),
                    createdAt = o.optLong("createdAt", 0),
                    updatedAt = o.optLong("updatedAt", 0),
                    deleted = o.optBoolean("deleted", false),
                ),
            )
        }
        Snapshot(
            version = root.optInt("version", SNAPSHOT_VERSION),
            updatedAt = root.optLong("updatedAt", 0),
            notes = notes,
            tasks = tasks,
        )
    } catch (e: Exception) {
        Snapshot()
    }
}

// org.json returns the literal string "null" for a JSON null via optString — guard it.
private fun JSONObject.optStringOrNull(key: String): String? =
    if (!has(key) || isNull(key)) null else optString(key)

private fun JSONObject.optLongOrNull(key: String): Long? =
    if (!has(key) || isNull(key)) null else optLong(key)
