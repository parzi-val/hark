// Canonical, platform-neutral sync format shared by Web and Android via one JSON
// snapshot in the user's Google Drive appDataFolder. Records are keyed by `uid`
// (a UUID minted at creation), carry their own `updatedAt`, and soft-delete via
// `deleted` — so merges are per-record last-write-wins with tombstones, no backend.

export interface SyncNote {
  uid: string;
  title: string;
  body: string;
  heardAs: string | null;
  source: 'SPOKEN' | 'TYPED';
  pinnedToWidget: boolean;
  shelf: boolean;
  createdAt: number;
  updatedAt: number;
  deleted: boolean;
}

export interface SyncTask {
  uid: string;
  noteUid: string | null; // parent note's uid, NOT a local id — resolved per device
  title: string;
  done: boolean;
  doneAt: number | null;
  dueAt: number | null;
  dueHint: string | null;
  createdAt: number;
  updatedAt: number;
  deleted: boolean;
}

export interface Snapshot {
  version: number;
  updatedAt: number;
  notes: SyncNote[];
  tasks: SyncTask[];
}

export const SNAPSHOT_VERSION = 1;

export function emptySnapshot(): Snapshot {
  return { version: SNAPSHOT_VERSION, updatedAt: 0, notes: [], tasks: [] };
}

/**
 * Per-record last-write-wins by `updatedAt`. Tombstones participate, so a delete
 * propagates when it is the newer write. Records present on only one side are kept
 * (union). Ties favour the incoming (remote) record, which keeps the result
 * deterministic and the operation idempotent.
 */
export function mergeRecords<T extends { uid: string; updatedAt: number }>(
  local: T[],
  remote: T[],
): T[] {
  const byUid = new Map<string, T>();
  for (const r of local) byUid.set(r.uid, r);
  for (const r of remote) {
    const cur = byUid.get(r.uid);
    if (!cur || r.updatedAt >= cur.updatedAt) byUid.set(r.uid, r);
  }
  return [...byUid.values()];
}

export function serializeSnapshot(snap: Snapshot): string {
  return JSON.stringify(snap);
}

/** Defensive parse of a remote snapshot file: anything missing/corrupt reads as empty
 *  (so a bad remote never wipes local — merge just treats it as nothing to add). */
export function parseSnapshot(text: string | null | undefined): Snapshot {
  if (!text) return emptySnapshot();
  try {
    const o = JSON.parse(text) as Partial<Snapshot>;
    return {
      version: typeof o.version === 'number' ? o.version : SNAPSHOT_VERSION,
      updatedAt: typeof o.updatedAt === 'number' ? o.updatedAt : 0,
      notes: Array.isArray(o.notes) ? o.notes : [],
      tasks: Array.isArray(o.tasks) ? o.tasks : [],
    };
  } catch {
    return emptySnapshot();
  }
}

export function mergeSnapshots(local: Snapshot, remote: Snapshot): Snapshot {
  return {
    version: Math.max(local.version, remote.version, SNAPSHOT_VERSION),
    updatedAt: Date.now(),
    notes: mergeRecords(local.notes, remote.notes),
    tasks: mergeRecords(local.tasks, remote.tasks),
  };
}
