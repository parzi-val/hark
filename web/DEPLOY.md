# Deploy — Cloudflare Pages

Hark web is a static Vite SPA (PWA, client-side only, no backend).

## Cloudflare Pages settings
- **Framework preset:** Vite (or None)
- **Root directory:** `web`
- **Build command:** `npm run build`
- **Build output directory:** `dist`
- **Node version:** 20 — pinned via `web/.nvmrc`; if a build ignores it, set a
  `NODE_VERSION=20` environment variable.

SPA routing (path-based, e.g. `/home`) is handled by `web/wrangler.jsonc`
(`assets.not_found_handling: "single-page-application"`). Do **not** add a
`_redirects` file — its `/* -> /index.html` rule trips the Workers Assets loop
check (`[code: 100324]`). If a build still complains about `_redirects`, make sure
none exists in `public/` or `dist/`.

## Google OAuth (required for Drive sync)
In the GCP OAuth client → **Authorized JavaScript origins**, add the deployed origin:
- `https://<project>.pages.dev`
- and later the custom domain (e.g. `https://hark.notes`)

Keep the existing `http://localhost:5173` origin for local dev. The scope is
`drive.appdata` (non-sensitive), so no verification review is required.

## Custom domain (later)
Pages → **Custom domains** → add the domain. If it's on Cloudflare DNS the record
is created automatically; otherwise add the CNAME it shows. Then add that origin to
the OAuth client too.

## Notes
- No environment secrets are needed at build time — the OAuth **client id** is
  public and lives in the source; the client *secret* is never used by the web app.
- A tiny Cloudflare **Worker** is the natural place to add later if we want a
  refresh-token backend (to remove the ~1h token expiry on web).
