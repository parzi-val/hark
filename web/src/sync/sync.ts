// One sync cycle against the user's Drive appDataFolder: pull → merge → apply → push.
import { db, SettingsEntity } from '../db/db';
import { mergeSnapshots, parseSnapshot, serializeSnapshot, Snapshot } from './merge';
import { exportSnapshot, applySnapshot } from './local';
import { readAppData, readAppDataById, statAppData, writeAppData } from './drive';

export { isSignedIn, signIn, signOut } from './drive';

const DATA_FILE = 'hark.json';
const CONFIG_FILE = 'config.json';
const ENABLED_KEY = 'hark.syncEnabled';
const OPTIN_KEY = 'hark.syncApiKey';
// Timestamp of the in-flight sync (0 = none). A stale value >30s is treated as a dead/hung run so
// the guard self-heals — a stalled request can never permanently block the poll.
let syncingSince = 0;
let debounceTimer: ReturnType<typeof setTimeout> | null = null;
// Change-detection cache: skip the download when the file hasn't moved since our last sync, and
// skip the upload unless this device has something newer. Reset on reload (first sync re-seeds them).
let lastRemoteSnap: Snapshot | null = null;
let lastRemoteModified: string | null = null;

// --- per-device flags (never synced) ---
function flag(key: string): boolean {
  try {
    return localStorage.getItem(key) === '1';
  } catch {
    return false;
  }
}
function setFlag(key: string, on: boolean): void {
  try {
    localStorage.setItem(key, on ? '1' : '0');
  } catch {
    /* private mode — ignore */
  }
}

/** Has this device turned sync on (so we auto-sync on open)? */
export const isSyncEnabled = () => flag(ENABLED_KEY);
export const setSyncEnabled = (on: boolean) => setFlag(ENABLED_KEY, on);
/** Include the API key in the synced config. On by default (your own key, your own private
 *  Drive folder) — turn it off explicitly and it stays off. */
export const isApiKeySynced = () => {
  try {
    return localStorage.getItem(OPTIN_KEY) !== '0';
  } catch {
    return true;
  }
};
export const setApiKeySynced = (on: boolean) => setFlag(OPTIN_KEY, on);

// --- initial-sync flag (for the entry loader), observable via useSyncExternalStore ---
// Start true when sync is enabled so the very first paint shows the loader, not the starter note.
let _initialSyncing = isSyncEnabled();
const initialSyncingListeners = new Set<() => void>();
export function subscribeInitialSyncing(cb: () => void): () => void {
  initialSyncingListeners.add(cb);
  return () => {
    initialSyncingListeners.delete(cb);
  };
}
export function getInitialSyncing(): boolean {
  return _initialSyncing;
}
function setInitialSyncing(v: boolean): void {
  _initialSyncing = v;
  initialSyncingListeners.forEach((l) => l());
}

/** Fire-and-forget first sync that flips the loader flag, so entering the app after onboarding
 *  shows the Hilbert loader until the initial sync lands (rather than flashing the starter note). */
export function firstSyncAsync(): void {
  setInitialSyncing(true);
  void syncNow()
    .catch(() => {})
    .finally(() => setInitialSyncing(false));
}

interface RemoteConfig {
  baseUrl?: string;
  model?: string;
  themeMode?: SettingsEntity['themeMode'];
  apiKey?: string;
  userName?: string;
}

/** Push local settings to Drive. Call after the user explicitly saves settings. The API
 *  key is included only when the opt-in is on. */
export async function pushSettings(): Promise<void> {
  const s = await db.settings.get(1);
  if (!s) return;
  const cfg: RemoteConfig = { baseUrl: s.baseUrl, model: s.model, themeMode: s.themeMode, userName: s.userName };
  if (isApiKeySynced() && s.apiKey) cfg.apiKey = s.apiKey;
  await writeAppData(CONFIG_FILE, JSON.stringify(cfg));
}

/** On a fresh device pull config from Drive so it arrives configured. */
export async function pullSettingsIfFresh(): Promise<void> {
  const text = await readAppData(CONFIG_FILE);
  if (!text) return;
  let cfg: RemoteConfig;
  try {
    cfg = JSON.parse(text) as RemoteConfig;
  } catch {
    return;
  }
  const s = await db.settings.get(1);
  const patch: Partial<SettingsEntity> = {};
  if (cfg.baseUrl && (!s?.baseUrl || s.baseUrl === 'https://api.groq.com/openai/v1')) patch.baseUrl = cfg.baseUrl;
  if (cfg.model && (!s?.model || s.model === 'llama-3.3-70b-versatile')) patch.model = cfg.model;
  if (cfg.themeMode) patch.themeMode = cfg.themeMode;
  if (cfg.userName && !s?.userName) patch.userName = cfg.userName;
  if (cfg.apiKey && !s?.apiKey?.trim()) {
    patch.apiKey = cfg.apiKey;
    setApiKeySynced(true);
  }
  if (Object.keys(patch).length) await db.settings.update(1, patch);
}

/** Adopt the synced API key on a device that has none yet (fills a blank only, so it never
 *  overwrites a device that has its own key). Runs each sync so a later-added key propagates
 *  without needing to re-sign-in. */
async function catchUpApiKey(): Promise<void> {
  const s = await db.settings.get(1);
  if (s?.apiKey?.trim()) return;
  const text = await readAppData(CONFIG_FILE);
  if (!text) return;
  try {
    const cfg = JSON.parse(text) as RemoteConfig;
    if (cfg.apiKey) {
      await db.settings.update(1, { apiKey: cfg.apiKey });
      setApiKeySynced(true);
    }
  } catch {
    /* ignore malformed config */
  }
}

function isStarterNote(title: string): boolean {
  const t = title.trim().toLowerCase();
  return t === 'welcome to hark' || t === 'welcome to hark.';
}

function isStarterTask(title: string): boolean {
  const t = title.trim().toLowerCase();
  return (
    t.startsWith('tap talk or hold space') ||
    t.startsWith('configure your groq') ||
    t.startsWith('set your groq') ||
    t.startsWith('add the hark') ||
    t.startsWith('tap talk and speak')
  );
}

/**
 * Pull the remote snapshot, merge with local, write the result back to IndexedDB and
 * re-upload it. Reentrancy-guarded, so overlapping triggers collapse into one run.
 */
export async function syncNow(): Promise<void> {
  if (syncingSince && Date.now() - syncingSince < 30000) return;
  syncingSince = Date.now();
  try {
    const meta = await statAppData(DATA_FILE);

    // Pull only if the file moved since our last sync; otherwise reuse the cached snapshot.
    let remote: Snapshot;
    if (meta?.modifiedTime && meta.modifiedTime === lastRemoteModified && lastRemoteSnap) {
      remote = lastRemoteSnap;
    } else {
      remote = parseSnapshot(meta ? await readAppDataById(meta.id) : null);
      lastRemoteSnap = remote;
      lastRemoteModified = meta?.modifiedTime ?? null;
    }

    const hasRealRemoteData =
      remote.notes.some((n) => !isStarterNote(n.title) && !n.deleted) ||
      remote.tasks.some((t) => !isStarterTask(t.title) && !t.deleted);

    // If real data exists remotely, purge local starter dummy items from IndexedDB
    if (hasRealRemoteData) {
      const allNotes = await db.notes.toArray();
      for (const n of allNotes) {
        if (isStarterNote(n.title) && n.id != null) {
          await db.notes.delete(n.id);
        }
      }
      const allTasks = await db.tasks.toArray();
      for (const t of allTasks) {
        if (isStarterTask(t.title) && t.id != null) {
          await db.tasks.delete(t.id);
        }
      }
    }

    const local = await exportSnapshot();

    let cleanedRemote = remote;
    let cleanedLocal = local;
    if (hasRealRemoteData) {
      cleanedRemote = {
        ...remote,
        notes: remote.notes.filter((n) => !isStarterNote(n.title)),
        tasks: remote.tasks.filter((t) => !isStarterTask(t.title)),
      };
      cleanedLocal = {
        ...local,
        notes: local.notes.filter((n) => !isStarterNote(n.title)),
        tasks: local.tasks.filter((t) => !isStarterTask(t.title)),
      };
    }

    const merged = mergeSnapshots(cleanedLocal, cleanedRemote);
    await applySnapshot(merged);

    // Push only when we have something newer than the remote — otherwise a receiver would write
    // the file straight back and the two clients would rewrite it forever.
    if (hasLocalChangesToPush(merged, cleanedRemote)) {
      lastRemoteModified = await writeAppData(DATA_FILE, serializeSnapshot(merged));
      lastRemoteSnap = merged;
    }
    await catchUpApiKey();
  } finally {
    syncingSince = 0;
  }
}

/** True if `merged` carries a record `remote` lacks or has an older copy of — i.e. this device has
 *  something to upload. merged is a union of both sides, so it suffices to check every merged
 *  record matches remote's by uid + updatedAt. */
function hasLocalChangesToPush(merged: Snapshot, remote: Snapshot): boolean {
  const rNotes = new Map(remote.notes.map((n) => [n.uid, n.updatedAt] as const));
  const rTasks = new Map(remote.tasks.map((t) => [t.uid, t.updatedAt] as const));
  return (
    merged.notes.some((n) => rNotes.get(n.uid) !== n.updatedAt) ||
    merged.tasks.some((t) => rTasks.get(t.uid) !== t.updatedAt)
  );
}

/**
 * Schedule a debounced auto-sync (coalesces rapid keystrokes/edits into one push).
 */
export function scheduleSync(delayMs = 800): void {
  if (!isSyncEnabled()) return;
  if (debounceTimer) clearTimeout(debounceTimer);
  debounceTimer = setTimeout(() => {
    debounceTimer = null;
    void syncNow().catch(() => {});
  }, delayMs);
}
