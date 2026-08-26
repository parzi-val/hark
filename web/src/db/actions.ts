import { db } from './db';
import { HarkAction, NoteRef, FocusedNote } from '../ai/groq';

// A voice capture longer than this auto-lands on the Shelf instead of the Stream.
export const SHELF_THRESHOLD = 400;

/** Recent notes as a lightweight index for the model to resolve "which note" (+ task counts
 *  so it can tell a checklist note apart and append items as tasks, not prose). */
export async function recentNoteRefs(limit = 40): Promise<NoteRef[]> {
  const [allNotes, allTasks] = await Promise.all([
    db.notes.filter((n) => !n.deleted).toArray(),
    db.tasks.filter((t) => !t.deleted).toArray(),
  ]);
  const countByNote = new Map<number, number>();
  for (const t of allTasks) {
    if (t.sourceNoteId != null) countByNote.set(t.sourceNoteId, (countByNote.get(t.sourceNoteId) || 0) + 1);
  }
  allNotes.sort((a, b) => b.updatedAt - a.updatedAt);
  return allNotes.slice(0, limit).map((n) => ({
    id: n.id!,
    title: n.title,
    snippet: (n.body || '').replace(/\s+/g, ' ').trim().slice(0, 80),
    taskCount: countByNote.get(n.id!) || 0,
  }));
}

export async function focusedNoteOf(id: number): Promise<FocusedNote | null> {
  const n = await db.notes.get(id);
  if (!n || n.deleted) return null;
  const tasks = (await db.tasks.where('sourceNoteId').equals(id).toArray())
    .filter((t) => !t.deleted)
    .map((t) => t.title);
  return { id: n.id!, title: n.title, body: n.body, tasks };
}

/** Applies a HarkAction locally. Returns the affected note id. Never loses capture. */
export async function applyAction(
  a: HarkAction,
  transcript: string,
  source: 'VOICE' | 'TYPED'
): Promise<number> {
  const now = Date.now();

  const addTasks = async (noteId: number) => {
    if (a.tasks.length) {
      await db.tasks.bulkAdd(
        a.tasks.map((t) => ({
          title: t.title,
          done: false,
          dueHint: t.dueHint ?? null,
          sourceNoteId: noteId,
          createdAt: now,
          updatedAt: now,
          deleted: false,
        }))
      );
    }
  };

  if (a.action !== 'create' && a.targetNoteId != null) {
    const note = await db.notes.get(a.targetNoteId);
    if (note && !note.deleted) {
      if (a.action === 'append') {
        const add = a.body.trim();
        const body = add ? (note.body?.trim() ? `${note.body}\n\n${add}` : add) : note.body;
        await db.notes.update(note.id!, { body, heardAs: trail(note.heardAs, transcript, source), updatedAt: now });
      } else {
        await db.notes.update(note.id!, {
          title: a.title || note.title,
          body: a.body,
          heardAs: trail(note.heardAs, transcript, source),
          updatedAt: now,
        });
      }
      await addTasks(note.id!);
      return note.id!;
    }
    // target vanished → fall through to create (safety)
  }

  const id = await db.notes.add({
    title: a.title || transcript.slice(0, 40) || 'Untitled note',
    body: a.body || transcript,
    heardAs: source === 'VOICE' ? transcript : null,
    source,
    pinnedToWidget: false,
    // Long voice captures shelve themselves (by how long you spoke); typed quick notes stay in the stream.
    shelf: source === 'VOICE' && transcript.length > SHELF_THRESHOLD,
    createdAt: now,
    updatedAt: now,
    deleted: false,
  });
  await addTasks(id);
  return id;
}

/** Create a blank Shelf note and return its id (for the full-screen writer). */
export async function newShelfNote(): Promise<number> {
  const now = Date.now();
  return db.notes.add({
    title: '',
    body: '',
    source: 'TYPED',
    pinnedToWidget: false,
    shelf: true,
    createdAt: now,
    updatedAt: now,
    deleted: false,
  });
}

/** Move a note between Stream and Shelf. Shelving clears any widget pin (pinning a shelf note is moot). */
export async function setShelf(id: number, shelf: boolean): Promise<void> {
  await db.notes.update(id, { shelf, ...(shelf ? { pinnedToWidget: false } : {}), updatedAt: Date.now() });
}

function trail(existing: string | null | undefined, transcript: string, source: 'VOICE' | 'TYPED'): string | null {
  if (source !== 'VOICE') return existing ?? null;
  return existing ? `${existing}\n\n— — —\n${transcript}` : transcript;
}
