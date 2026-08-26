import Dexie, { Table } from 'dexie';

export interface NoteEntity {
  id?: number;
  title: string;
  body: string;
  heardAs?: string | null;
  source: 'VOICE' | 'TYPED';
  pinnedToWidget: boolean;
  shelf?: boolean; // long-form note (lives on the Shelf, not the Stream)
  createdAt: number;
  updatedAt: number;
  deleted: boolean;
}

export interface TaskEntity {
  id?: number;
  title: string;
  done: boolean;
  doneAt?: number | null;
  dueAt?: number | null;
  dueHint?: string | null;
  sourceNoteId?: number | null;
  createdAt: number;
  updatedAt: number;
  deleted: boolean;
}

// Any OpenAI-compatible endpoint works; defaults match the Android app.
export const DEFAULT_BASE_URL = 'https://api.groq.com/openai/v1';
export const DEFAULT_MODEL = 'llama-3.3-70b-versatile';

export interface SettingsEntity {
  id: number;
  apiKey: string;
  baseUrl: string;
  model: string;
  themeMode: 'SYSTEM' | 'LIGHT' | 'DARK';
  viewMode: 'STREAM' | 'GRID';
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
  }
}

export const db = new HarkDatabase();

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
      themeMode: 'SYSTEM',
      viewMode: 'STREAM',
    });
  }
}
