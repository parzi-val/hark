// Google sign-in (GIS token flow) + Drive REST access to the app's private
// appDataFolder. No backend: the browser holds a short-lived access token and talks
// to Drive directly. The client id is public (it ships in the bundle either way).
const CLIENT_ID = '255797362875-kcdg3j78vo40o8fdfqgp5hiq5dtppn5h.apps.googleusercontent.com';
const SCOPES = 'https://www.googleapis.com/auth/drive.appdata openid email profile';
const GIS_SRC = 'https://accounts.google.com/gsi/client';

// Minimal typing for the bits of Google Identity Services we use (avoids a @types dep).
interface TokenResponse {
  access_token?: string;
  expires_in?: number;
  scope?: string;
  error?: string;
}
interface TokenClient {
  requestAccessToken(opts?: { prompt?: string }): void;
}
declare global {
  interface Window {
    google?: {
      accounts: {
        oauth2: {
          initTokenClient(cfg: {
            client_id: string;
            scope: string;
            callback: (resp: TokenResponse) => void;
            error_callback?: (err: unknown) => void;
          }): TokenClient;
          revoke(token: string, done?: () => void): void;
        };
      };
    };
  }
}

let gisReady: Promise<void> | null = null;
function loadGis(): Promise<void> {
  if (gisReady) return gisReady;
  gisReady = new Promise<void>((resolve, reject) => {
    if (window.google?.accounts?.oauth2) return resolve();
    const s = document.createElement('script');
    s.src = GIS_SRC;
    s.async = true;
    s.defer = true;
    s.onload = () => resolve();
    s.onerror = () => reject(new Error('Failed to load Google Identity Services'));
    document.head.appendChild(s);
  });
  return gisReady;
}

let accessToken: string | null = null;
let tokenExpiry = 0; // epoch ms (with a safety margin)
let tokenClient: TokenClient | null = null;
let pendingResolve: ((r: TokenResponse) => void) | null = null;
let pendingReject: ((e: unknown) => void) | null = null;

// The GIS token flow can't fetch a token silently after a page reload (it would pop a dialog), so
// we persist the token and reuse it until it expires (~1h). Background sync never re-invokes GIS.
const TOKEN_KEY = 'hark.driveToken';
try {
  const raw = localStorage.getItem(TOKEN_KEY);
  if (raw) {
    const saved = JSON.parse(raw) as { token: string; expiry: number };
    if (saved.token && Date.now() < saved.expiry) {
      accessToken = saved.token;
      tokenExpiry = saved.expiry;
    }
  }
} catch {
  /* ignore */
}

async function getTokenClient(): Promise<TokenClient> {
  await loadGis();
  if (tokenClient) return tokenClient;
  tokenClient = window.google!.accounts.oauth2.initTokenClient({
    client_id: CLIENT_ID,
    scope: SCOPES,
    callback: (resp) => pendingResolve?.(resp),
    error_callback: (err) => pendingReject?.(err),
  });
  return tokenClient;
}

async function requestToken(prompt: string): Promise<TokenResponse> {
  const client = await getTokenClient();
  return new Promise<TokenResponse>((resolve, reject) => {
    pendingResolve = (r) => {
      pendingResolve = pendingReject = null;
      resolve(r);
    };
    pendingReject = (e) => {
      pendingResolve = pendingReject = null;
      reject(e);
    };
    client.requestAccessToken({ prompt });
  });
}

function storeToken(resp: TokenResponse): string {
  if (resp.error || !resp.access_token) throw new Error(resp.error || 'Google authorization failed');
  accessToken = resp.access_token;
  tokenExpiry = Date.now() + (resp.expires_in ?? 3600) * 1000 - 60_000; // renew a minute early
  try {
    localStorage.setItem(TOKEN_KEY, JSON.stringify({ token: accessToken, expiry: tokenExpiry }));
  } catch {
    /* ignore */
  }
  return accessToken;
}

export function isSignedIn(): boolean {
  return !!accessToken && Date.now() < tokenExpiry;
}

/** Interactive sign-in — shows Google's account chooser / consent. */
export async function signIn(): Promise<void> {
  storeToken(await requestToken('consent'));
}

export function signOut(): void {
  const t = accessToken;
  accessToken = null;
  tokenExpiry = 0;
  try {
    localStorage.removeItem(TOKEN_KEY);
  } catch {
    /* ignore */
  }
  if (t) window.google?.accounts.oauth2.revoke(t);
}

/** A valid access token, or throws. NEVER invokes GIS — background sync must be silent, so a
 *  lapsed token just makes the sync no-op. Interactive (re)auth happens only via signIn(). */
async function token(): Promise<string> {
  if (isSignedIn()) return accessToken!;
  throw new Error('Drive sign-in required');
}

// ---- Drive REST, scoped to appDataFolder ----
const DRIVE = 'https://www.googleapis.com/drive/v3';
const UPLOAD = 'https://www.googleapis.com/upload/drive/v3';

async function authFetch(url: string, init: RequestInit = {}): Promise<Response> {
  const headers = new Headers(init.headers);
  headers.set('Authorization', `Bearer ${await token()}`);
  // Abort a stalled request after 15s so a hung fetch can't wedge the sync guard.
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 15000);
  try {
    return await fetch(url, { ...init, headers, signal: controller.signal });
  } finally {
    clearTimeout(timer);
  }
}

async function findFileId(name: string): Promise<string | null> {
  const q = encodeURIComponent(`name='${name}'`);
  const res = await authFetch(`${DRIVE}/files?spaces=appDataFolder&q=${q}&fields=files(id,name)`);
  if (!res.ok) throw new Error(`Drive list failed: ${res.status}`);
  const data = (await res.json()) as { files?: { id: string }[] };
  return data.files?.[0]?.id ?? null;
}

/** Read a text file from appDataFolder, or null if it doesn't exist yet. */
export async function readAppData(name: string): Promise<string | null> {
  const id = await findFileId(name);
  if (!id) return null;
  const res = await authFetch(`${DRIVE}/files/${id}?alt=media`);
  if (!res.ok) throw new Error(`Drive read failed: ${res.status}`);
  return res.text();
}

/** Create or overwrite a text file in appDataFolder. */
export async function writeAppData(name: string, content: string): Promise<void> {
  const id = await findFileId(name);
  if (id) {
    const res = await authFetch(`${UPLOAD}/files/${id}?uploadType=media`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: content,
    });
    if (!res.ok) throw new Error(`Drive update failed: ${res.status}`);
    return;
  }
  const boundary = 'hark' + Math.random().toString(36).slice(2);
  const body =
    `--${boundary}\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n` +
    JSON.stringify({ name, parents: ['appDataFolder'] }) +
    `\r\n--${boundary}\r\nContent-Type: application/json\r\n\r\n` +
    content +
    `\r\n--${boundary}--`;
  const res = await authFetch(`${UPLOAD}/files?uploadType=multipart&fields=id`, {
    method: 'POST',
    headers: { 'Content-Type': `multipart/related; boundary=${boundary}` },
    body,
  });
  if (!res.ok) throw new Error(`Drive create failed: ${res.status}`);
}
