// One sync cycle against the user's Drive appDataFolder: pull → merge → apply → push.
import { db, SettingsEntity } from '../db/db';
import { mergeSnapshots, parseSnapshot, serializeSnapshot } from './merge';
import { exportSnapshot, applySnapshot } from './local';
import { readAppData, writeAppData } from './drive';

export { isSignedIn, signIn, signOut } from './drive';

const DATA_FILE = 'hark.json';
const CONFIG_FILE = 'config.json';
const ENABLED_KEY = 'hark.syncEnabled';
const OPTIN_KEY = 'hark.syncApiKey';
let syncing = false;

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
/** Opt-in to include the API key in the synced config. */
export const isApiKeySynced = () => flag(OPTIN_KEY);
export const setApiKeySynced = (on: boolean) => setFlag(OPTIN_KEY, on);

interface RemoteConfig {
  baseUrl?: string;
  model?: string;
  themeMode?: SettingsEntity['themeMode'];
  apiKey?: string;
}

/** Push local settings to Drive. Call after the user explicitly saves settings. The API
 *  key is included only when the opt-in is on. */
export async function pushSettings(): Promise<void> {
  const s = await db.settings.get(1);
  if (!s) return;
  const cfg: RemoteConfig = { baseUrl: s.baseUrl, model: s.model, themeMode: s.themeMode };
  if (isApiKeySynced()) cfg.apiKey = s.apiKey;
  await writeAppData(CONFIG_FILE, JSON.stringify(cfg));
}

/** On a fresh device (no API key yet) pull config from Drive so it arrives configured.
 *  An already-configured device is authoritative and keeps its own settings. */
export async function pullSettingsIfFresh(): Promise<void> {
  const s = await db.settings.get(1);
  if (s?.apiKey?.trim()) return;
  const text = await readAppData(CONFIG_FILE);
  if (!text) return;
  let cfg: RemoteConfig;
  try {
    cfg = JSON.parse(text) as RemoteConfig;
  } catch {
    return;
  }
  const patch: Partial<SettingsEntity> = {};
  if (cfg.baseUrl) patch.baseUrl = cfg.baseUrl;
  if (cfg.model) patch.model = cfg.model;
  if (cfg.themeMode) patch.themeMode = cfg.themeMode;
  if (isApiKeySynced() && cfg.apiKey) patch.apiKey = cfg.apiKey;
  if (Object.keys(patch).length) await db.settings.update(1, patch);
}

/**
 * Pull the remote snapshot, merge with local, write the result back to IndexedDB and
 * re-upload it. Reentrancy-guarded, so overlapping triggers collapse into one run.
 *
 * ponytail: no ETag/if-match on the upload, so two devices writing at the same instant
 * can have one overwrite the other's file. It's self-healing, not lossy — each device
 * keeps its own local rows and re-merges them on its next sync, so the set converges a
 * cycle later. Add an ETag precondition only if that one-cycle lag ever matters.
 */
export async function syncNow(): Promise<void> {
  if (syncing) return;
  syncing = true;
  try {
    const local = await exportSnapshot();
    const remote = parseSnapshot(await readAppData(DATA_FILE));
    const merged = mergeSnapshots(local, remote);
    await applySnapshot(merged);
    await writeAppData(DATA_FILE, serializeSnapshot(merged));
  } finally {
    syncing = false;
  }
}
