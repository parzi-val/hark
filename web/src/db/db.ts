import Dexie, { Table } from 'dexie';

export interface NoteEntity {
  id?: number;
  uid?: string; // stable global id for cross-device sync; auto-assigned on create
  title: string;
  body: string;
  heardAs?: string | null;
  source: 'VOICE' | 'TYPED';
  pinnedToWidget: boolean;
  shelf?: boolean; // long-form note (lives on the Shelf, not the Stream)
  archived?: boolean; // ponytail: filed away when all tasks complete or manually
  createdAt: number;
  updatedAt: number;
  deleted: boolean;
}

export interface TaskEntity {
  id?: number;
  uid?: string; // stable global id for cross-device sync; auto-assigned on create
  title: string;
  done: boolean;
  doneAt?: number | null;
  dueAt?: number | null;
  dueHint?: string | null;
  deferred?: boolean; // ponytail: set-aside; only meaningful when !done
  sourceNoteId?: number | null;
  createdAt: number;
  updatedAt: number;
  deleted: boolean;
}

// Any OpenAI-compatible endpoint works; defaults match the Android app.
export const DEFAULT_BASE_URL = 'https://api.groq.com/openai/v1';
export const DEFAULT_MODEL = 'openai/gpt-oss-120b';

export interface SettingsEntity {
  id: number;
  apiKey: string;
  baseUrl: string;
  model: string;
  userName?: string;
  themeMode: 'SYSTEM' | 'LIGHT' | 'DARK';
  viewMode: 'STREAM' | 'GRID';
  hasCompletedOnboarding?: boolean;
}

export class HarkDatabase extends Dexie {
  notes!: Table<NoteEntity, number>;
  tasks!: Table<TaskEntity, number>;
  settings!: Table<SettingsEntity, number>;

  constructor() {
    super('HarkWebDb');
    this.version(1).stores({
      notes: '++id, pinnedToWidget, createdAt, deleted',
      tasks: '++id, done, sourceNoteId, createdAt, deleted',
      settings: 'id',
    });
    // v2: add the cross-device sync key. Index it and backfill existing rows.
    this.version(2)
      .stores({
        notes: '++id, uid, pinnedToWidget, createdAt, deleted',
        tasks: '++id, uid, done, sourceNoteId, createdAt, deleted',
        settings: 'id',
      })
      .upgrade(async (tx) => {
        await tx.table('notes').toCollection().modify((n: NoteEntity) => {
          if (!n.uid) n.uid = crypto.randomUUID();
        });
        await tx.table('tasks').toCollection().modify((t: TaskEntity) => {
          if (!t.uid) t.uid = crypto.randomUUID();
        });
      });
  }
}

export const db = new HarkDatabase();

// Auto-assign a stable sync uid to every new note/task, so no insert site can forget it.
db.notes.hook('creating', (_pk, obj: NoteEntity) => {
  if (!obj.uid) obj.uid = crypto.randomUUID();
});
db.tasks.hook('creating', (_pk, obj: TaskEntity) => {
  if (!obj.uid) obj.uid = crypto.randomUUID();
});

// Seed initial starter note if fresh. Guard against concurrent double-seed
// (React StrictMode invokes effects twice in dev).
let seeding = false;
export async function seedStarterIfEmpty() {
  if (seeding) return;
  seeding = true;
  const count = await db.notes.count();
  if (count === 0) {
    const now = Date.now();
    const noteId = await db.notes.add({
      title: 'welcome to hark.',
      body: 'a thinking space that shapes your spoken stream of consciousness into structured notes and actionable checklists.',
      source: 'TYPED',
      pinnedToWidget: true,
      createdAt: now,
      updatedAt: now,
      deleted: false,
    });

    await db.tasks.bulkAdd([
      {
        title: 'tap talk or hold space to capture a thought',
        done: false,
        sourceNoteId: noteId,
        createdAt: now,
        updatedAt: now,
        deleted: false,
      },
      {
        title: 'configure your Groq API key in settings',
        done: false,
        dueHint: 'today',
        sourceNoteId: noteId,
        createdAt: now,
        updatedAt: now,
        deleted: false,
      },
    ]);

    await db.settings.put({
      id: 1,
      apiKey: '',
      baseUrl: DEFAULT_BASE_URL,
      model: DEFAULT_MODEL,
      themeMode: 'LIGHT',
      viewMode: 'STREAM',
    });
  }
}
