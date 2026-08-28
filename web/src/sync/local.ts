// Bridge between the local IndexedDB (Dexie) and the canonical sync Snapshot.
// export: rows -> Snapshot (mapping source VOICE->SPOKEN and sourceNoteId->noteUid).
// apply:  merged Snapshot -> rows (upsert by uid, tombstones, noteUid->sourceNoteId).
import { db, NoteEntity, TaskEntity } from '../db/db';
import { Snapshot, SyncNote, SyncTask, SNAPSHOT_VERSION } from './merge';

/** Read the whole local store (tombstones included) as a canonical snapshot. */
export async function exportSnapshot(): Promise<Snapshot> {
  const [notes, tasks] = await Promise.all([db.notes.toArray(), db.tasks.toArray()]);

  const uidByLocalId = new Map<number, string>();
  for (const n of notes) if (n.id != null && n.uid) uidByLocalId.set(n.id, n.uid);

  const snapNotes: SyncNote[] = notes
    .filter((n) => !!n.uid)
    .map((n) => ({
      uid: n.uid!,
      title: n.title,
      body: n.body,
      heardAs: n.heardAs ?? null,
      source: n.source === 'VOICE' ? 'SPOKEN' : 'TYPED',
      pinnedToWidget: n.pinnedToWidget,
      shelf: n.shelf ?? false,
      createdAt: n.createdAt,
      updatedAt: n.updatedAt,
      deleted: n.deleted,
    }));

  const snapTasks: SyncTask[] = tasks
    .filter((t) => !!t.uid)
    .map((t) => ({
      uid: t.uid!,
      noteUid: t.sourceNoteId != null ? uidByLocalId.get(t.sourceNoteId) ?? null : null,
      title: t.title,
      done: t.done,
      doneAt: t.doneAt ?? null,
      dueAt: t.dueAt ?? null,
      dueHint: t.dueHint ?? null,
      createdAt: t.createdAt,
      updatedAt: t.updatedAt,
      deleted: t.deleted,
    }));

  return { version: SNAPSHOT_VERSION, updatedAt: Date.now(), notes: snapNotes, tasks: snapTasks };
}

/**
 * Write a merged snapshot back into IndexedDB. Per record: update in place when the
 * snapshot is strictly newer than the local row (so a concurrent local edit made after
 * export is never clobbered), insert when we've never seen the uid, and skip brand-new
 * tombstones (nothing to hide locally — the remote file keeps the tombstone).
 */
export async function applySnapshot(snap: Snapshot): Promise<void> {
  await db.transaction('rw', db.notes, db.tasks, async () => {
    // ---- notes ----
    const noteByUid = new Map<string, NoteEntity>();
    for (const n of await db.notes.toArray()) if (n.uid) noteByUid.set(n.uid, n);

    for (const s of snap.notes) {
      const fields = {
        uid: s.uid,
        title: s.title,
        body: s.body,
        heardAs: s.heardAs,
        source: (s.source === 'SPOKEN' ? 'VOICE' : 'TYPED') as NoteEntity['source'],
        pinnedToWidget: s.pinnedToWidget,
        shelf: s.shelf,
        createdAt: s.createdAt,
        updatedAt: s.updatedAt,
        deleted: s.deleted,
      };
      const local = noteByUid.get(s.uid);
      if (local) {
        if (s.updatedAt > local.updatedAt) await db.notes.update(local.id!, fields);
      } else if (!s.deleted) {
        await db.notes.add(fields);
      }
    }

    // ---- tasks (resolve parent uid -> local note id AFTER notes are applied) ----
    const localIdByUid = new Map<string, number>();
    for (const n of await db.notes.toArray()) if (n.uid && n.id != null) localIdByUid.set(n.uid, n.id);

    const taskByUid = new Map<string, TaskEntity>();
    for (const t of await db.tasks.toArray()) if (t.uid) taskByUid.set(t.uid, t);

    for (const s of snap.tasks) {
      const fields = {
        uid: s.uid,
        title: s.title,
        done: s.done,
        doneAt: s.doneAt,
        dueAt: s.dueAt,
        dueHint: s.dueHint,
        sourceNoteId: s.noteUid != null ? localIdByUid.get(s.noteUid) ?? null : null,
        createdAt: s.createdAt,
        updatedAt: s.updatedAt,
        deleted: s.deleted,
      };
      const local = taskByUid.get(s.uid);
      if (local) {
        if (s.updatedAt > local.updatedAt) await db.tasks.update(local.id!, fields);
      } else if (!s.deleted) {
        await db.tasks.add(fields);
      }
    }
  });
}
