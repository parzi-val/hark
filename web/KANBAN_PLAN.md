# Web plan — Task Kanban + Archive

Status: **proposal for review** (Bala + Claude to check before agy builds). Android side is handled
in parallel by Claude; this doc is the **web** implementation spec plus the one cross-platform
contract both sides must agree on.

---

## 1. Goal

The `OPEN` tab currently flattens open tasks into a thin list and wastes the wide desktop canvas.
Replace it with a **3-column kanban** and add an **Archive** tab:

- **OPEN tab → Kanban** with columns **Open / Deferred / Completed**.
- Tasks are **grouped by their parent note** (note title as the group label) inside each column.
- **Deferred** tasks render grayed out; **Completed** tasks render struck through.
- Drag a task between columns to change its state.
- New **Archive** tab (tabs become `ALL · OPEN · NOTES · ARCHIVE`).
- When **every task of a note is completed**, the note auto-moves to Archive behind the scenes.

Android parity (Claude, separate): long-press a task → context menu with **Mark as deferred**; side
drawer gets an **Archive note** button. No kanban on Android — mobile uses the menu + drawer.

---

## 2. Data model

Two additive fields. Keep the existing `done` semantics untouched — everything derives from `done` +
one new `deferred` boolean, so we avoid a risky `status`-enum refactor across the widget/Today/sync.

### `web/src/db/db.ts`

```ts
export interface TaskEntity {
  // ...existing...
  done: boolean;
  doneAt?: number | null;
  deferred?: boolean;      // NEW — set-aside; only meaningful when !done
}

export interface NoteEntity {
  // ...existing...
  archived?: boolean;      // NEW — filed away; auto-set when all tasks complete, or manually
}
```

No new Dexie index and **no migration needed** — both are non-indexed booleans (IndexedDB can't index
booleans anyway; we already filter booleans in JS). Missing value reads as `false` via `?? false`.

### Derived status (single source of truth)

| Column     | Predicate                 |
|------------|---------------------------|
| Open       | `!done && !deferred`      |
| Deferred   | `!done && deferred`       |
| Completed  | `done`                    |

Add a helper in `actions.ts`:

```ts
export type TaskStatus = 'OPEN' | 'DEFERRED' | 'COMPLETED';
export const taskStatus = (t: TaskEntity): TaskStatus =>
  t.done ? 'COMPLETED' : t.deferred ? 'DEFERRED' : 'OPEN';
```

---

## 3. ⚠ Cross-platform contract (web + Android must match)

The Drive snapshot (`hark.json`) is produced by **both** clients. If web adds a field that Android's
serializer doesn't know, **Android strips it on its next write** (and vice-versa). So these two fields
must be added to the shared snapshot schema on **both** sides in the same pass:

| Snapshot record | New key      | Type    | Default when missing |
|-----------------|--------------|---------|----------------------|
| `SyncTask`      | `"deferred"` | boolean | `false`              |
| `SyncNote`      | `"archived"` | boolean | `false`              |

Keep `SNAPSHOT_VERSION = 1` — the change is additive and defensive-parse fills missing → `false`.
`doneAt` already exists in the schema; no change there.

**Web files for the contract:**
- `web/src/sync/merge.ts` — add `deferred` to `SyncTask`, `archived` to `SyncNote`.
- `web/src/sync/local.ts` —
  - `exportSnapshot`: `deferred: t.deferred ?? false` on tasks, `archived: n.archived ?? false` on notes.
  - `applySnapshot`: write `deferred` back onto the task fields, `archived` back onto the note fields.
- `parseSnapshot`/`serializeSnapshot` need no change (objects round-trip through `JSON`).

**Android files (Claude, for reference):** `SyncModels.kt` (`SyncNote`/`SyncTask` + `toJson` +
`parseSnapshot` with `optBoolean(..., false)`), `SyncLocal.kt` export/apply, Room `Entities.kt` +
migration + `Daos.kt`. Claude keeps these in lockstep with the keys above.

---

## 4. Logic — `web/src/db/actions.ts`

One status setter that everything routes through (checkbox, drag, Android-parity), so archive
reconciliation lives in exactly one place.

```ts
export async function setTaskStatus(taskId: number, status: TaskStatus): Promise<void> {
  const now = Date.now();
  const patch =
    status === 'COMPLETED' ? { done: true,  deferred: false, doneAt: now  } :
    status === 'DEFERRED'  ? { done: false, deferred: true,  doneAt: null } :
                             { done: false, deferred: false, doneAt: null };
  await db.tasks.update(taskId, { ...patch, updatedAt: now });

  const t = await db.tasks.get(taskId);
  if (t?.sourceNoteId != null) await reconcileNoteArchive(t.sourceNoteId);
  scheduleSync(500);
}

// Auto-archive is monotonic: it only ever SETS archived=true (all tasks done). Un-archiving is
// always an explicit user action (see below) so it never fights a manual archive.
async function reconcileNoteArchive(noteId: number): Promise<void> {
  const tasks = (await db.tasks.where('sourceNoteId').equals(noteId).toArray())
    .filter((t) => !t.deleted);
  const allDone = tasks.length >= 1 && tasks.every((t) => t.done);
  const note = await db.notes.get(noteId);
  if (note && allDone && !note.archived) {
    await db.notes.update(noteId, { archived: true, updatedAt: Date.now() });
  }
}

export async function archiveNote(noteId: number): Promise<void> {   // manual (Android drawer parity)
  await db.notes.update(noteId, { archived: true, updatedAt: Date.now() });
  scheduleSync(500);
}
export async function unarchiveNote(noteId: number): Promise<void> { // Archive-tab "Unarchive"
  await db.notes.update(noteId, { archived: false, updatedAt: Date.now() });
  scheduleSync(500);
}
```

**Refactor** `App.tsx` `handleToggleTask` to call `setTaskStatus(id, currentDone ? 'OPEN' : 'COMPLETED')`
instead of updating `db.tasks` directly — so the plain checkbox in Stream/Grid also triggers
auto-archive through the same path.

---

## 5. UI wiring — `web/src/App.tsx` + `Header.tsx`

**Header** (`web/src/components/Header.tsx`): widen the filter type + tab list to include `ARCHIVE`,
and show the deferred count alongside OPEN.

```ts
activeFilter: 'ALL' | 'OPEN' | 'NOTES' | 'ARCHIVE';
deferredCount: number;   // NEW prop
// ...
(['ALL', 'OPEN', 'NOTES', 'ARCHIVE'] as const).map(...)
// meta line: `${dateStr} · ${greeting} · ${openCount} OPEN` + (deferredCount ? ` · ${deferredCount} DEFERRED` : '')
```
(Same change to the `filter` state type in `App.tsx`.) Show `· N DEFERRED` only when `> 0` so a
zero-deferred day stays clean.

**App.tsx**
- `openCount = tasks.filter((t) => !t.done && !t.deferred).length` (deferred no longer inflates OPEN).
- `deferredCount = tasks.filter((t) => !t.done && t.deferred).length` → pass to `<Header>`.
- Archived notes appear **only** in Archive — exclude them everywhere else:
  - `streamNotes = notes.filter((n) => !n.shelf && !n.archived)`
  - `shelfNotes  = notes.filter((n) =>  n.shelf && !n.archived)`
  - `archivedNotes = notes.filter((n) => n.archived)`
- **Manual archive from NoteDetail** (see §7b): `handleArchiveNote(id)` → `archiveNote(id)` then
  `setSelectedNoteId(null)` (close the pane, mirroring `handleDeleteNote`).
- Main render routing (replaces the current `viewMode` branch for these two filters):

```tsx
{view === 'shelf'        ? <ShelfView ... />
 : filter === 'OPEN'     ? <KanbanBoard notes={streamNotes} tasks={tasks}
                                        onOpenNote={setSelectedNoteId} onEditTask={setEditingTask} />
 : filter === 'ARCHIVE'  ? <ArchiveView notes={archivedNotes} tasks={tasks}
                                        onOpenNote={setSelectedNoteId} onUnarchive={unarchiveNote} />
 : settings.viewMode === 'STREAM' ? <StreamView ... />   /* ALL, NOTES */
 : <GridView ... />}
```

The old `filter === 'OPEN'` branch inside `StreamView`/`GridView` is now unreachable — leave it, it's
harmless (don't spend time deleting).

---

## 6. OPEN tab — desktop kanban + mobile grouped list

Two presentations of the same grouped-by-note data. **Kanban is desktop-only (`lg:` and up); phones
keep a flat list.** Both share one grouping container so they read consistently.

### 6a. Shared: `NoteTaskGroup` (the consistency primitive) — `web/src/components/NoteTaskGroup.tsx`
A note's tasks are always shown as a **bordered group**, so "these tasks belong to this note" looks the
same on desktop and mobile:
- Container: `rounded-xl border border-ink-hairline bg-paper-card` (matches the app's card aesthetic).
- Header: note `title` (font-serif text-item, clickable → `onOpenNote`) + a faint task count.
- Body: the note's task rows.
- **Loose tasks** (`sourceNoteId == null`) have no parent → render as bare rows **without** the border.
  Consistency rule: **border ⇔ belongs to a note.**

Task row (shared): checkbox (Completed ⇄ Open via `setTaskStatus`), title (font-serif text-item; click
→ `onEditTask`), optional `dueHint` pill. Deferred → `opacity-60 text-ink-faint`; Completed →
`line-through text-ink-faint`.

### 6b. Desktop — `web/src/components/KanbanBoard.tsx` (`lg:` and up)
Props: `{ notes, tasks, onOpenNote, onEditTask }`.
- Consider non-deleted tasks whose parent note is **not archived**, plus loose tasks.
- Bucket by `taskStatus(t)` → 3 columns. **Inside each column, render one `NoteTaskGroup` per note**
  that has task(s) in that column (a note can appear in more than one column); loose tasks last.
- `grid grid-cols-3 gap-4`; each column has a sticky header `OPEN · n` / `DEFERRED · n` /
  `COMPLETED · n` (font-mono text-label uppercase) and scrolls independently.
- **Drag — native HTML5 (zero deps):** task row `draggable`,
  `onDragStart = e.dataTransfer.setData('text/plain', String(t.id))`; column
  `onDragOver = e.preventDefault()` (+ `bg-rust-muted` highlight while hovered),
  `onDrop = setTaskStatus(Number(id), COLUMN_STATUS)`.
- Order: groups by note `createdAt` desc; tasks by `createdAt` asc.

### 6c. Mobile — flat grouped list (below `lg`)
Keep the familiar flat OPEN list, just grouped by parent note:
- Render not-done tasks (`!done` → Open + Deferred) as a single column of `NoteTaskGroup`s. Completed
  drop out of the OPEN tab (same as the old behaviour). Deferred rows show grayed.
- No drag on touch. State changes reuse the existing **tap-to-edit** path: tapping a task opens
  `EditTaskDialog`, which gains an Open / Defer / Complete control (`setTaskStatus`). The checkbox
  still completes inline.

*Escape hatch (not v1):* for real touch-drag + animations across both, swap native DnD for
`@dnd-kit/core` (one dep). Deliberately deferred.

## 7b. Manual archive — `web/src/components/NoteDetail.tsx`

Parity with the Android drawer, and useful for **prose notes with no tasks** too:
- Add an **Archive** action to NoteDetail (next to the existing delete/close controls; font-mono
  text-label, `Archive` icon from lucide is fine) → `onArchive(noteId)` → `App.handleArchiveNote`.
- Archiving closes the pane; the note leaves ALL/OPEN/NOTES and appears under ARCHIVE.
- This is independent of task state — a task-less note archives on demand and comes back only via the
  explicit **Unarchive** in the Archive tab (§7).

---

## 7. New component — `web/src/components/ArchiveView.tsx`

Props: `{ notes: NoteEntity[]; tasks: TaskEntity[]; onOpenNote; onUnarchive }`.

- List archived notes (reuse the NOTE card look from `StreamView`): title, snippet, and a meta line
  `n done · archived` (a manually-archived prose note may have 0 tasks — show just `archived`).
- Includes both **auto-archived** (all tasks done) and **manually archived** (§7b) notes.
- Each row: click → `onOpenNote(id)`; a small `UNARCHIVE` action (font-mono text-label) → `onUnarchive(id)`.
- Empty state: "Nothing archived yet. Finish every task in a note — or archive one — and it lands here."

---

## 8. Edge cases / decisions baked in

- **Auto-archive** requires `tasks.length >= 1 && all done` — a note with no tasks never *auto*-archives.
  It can still be **manually** archived (§7b).
- Auto-archive is **set-true-only**; reopening a task does **not** auto-unarchive. Un-archive is always
  explicit (Archive-tab `UNARCHIVE`). *(Decision #1.)*
- **Completing the last task** archives the note immediately, so its (now-completed) tasks disappear
  from the Completed column and appear in Archive — the intended "behind the scenes" move.
- **Standalone completed tasks** (no parent note) have nothing to archive into — they remain in the
  Completed column. Fine for v1; a "clear completed" affordance can come later if it gets noisy.

---

## 9. Decisions (resolved with Bala)

1. **Un-archive is explicit** — reopening a task does not pull an archived note back; use `UNARCHIVE`.
2. **Deferred count is shown** — header reads `… · N OPEN · M DEFERRED` (M shown only when `> 0`).
3. **Manual "Archive note" on web too** (§7b) — in NoteDetail, works for any note incl. task-less prose.
4. **Mobile OPEN = flat list, grouped by parent note** (kanban is desktop-only). Grouping is the shared
   bordered `NoteTaskGroup` (§6a) so it stays consistent with the desktop columns.

---

## 10. File checklist + suggested order

1. `db/db.ts` — add `deferred` / `archived` fields. *(no migration)*
2. `sync/merge.ts` + `sync/local.ts` — contract fields. **⚠ must land with Android's matching change.**
3. `db/actions.ts` — `taskStatus`, `setTaskStatus`, `reconcileNoteArchive`, `archiveNote`, `unarchiveNote`.
4. `App.tsx` — `openCount` + `deferredCount`, note filtering, `handleToggleTask` → `setTaskStatus`,
   `handleArchiveNote`, OPEN/ARCHIVE routing, filter type.
5. `components/Header.tsx` — 4 tabs + `deferredCount` prop + type.
6. `components/NoteTaskGroup.tsx` — new (shared grouping primitive, §6a).
7. `components/KanbanBoard.tsx` — new (desktop, §6b).
8. `components/ArchiveView.tsx` — new (§7).
9. `components/NoteDetail.tsx` — Archive action (§7b).
10. `components/EditTaskDialog.tsx` — Open/Defer/Complete control (mobile state changes, §6c).
11. *(Mobile OPEN list)* — either a small `OpenListMobile` or a `<lg` branch reusing `NoteTaskGroup` (§6c).

## 11. Verify

- `tsc --noEmit` clean.
- Drag Open→Deferred (grays), →Completed (strikes), →back to Open. Reload → state persisted.
- Complete every task of a note → it leaves the board and shows under Archive; Unarchive returns it.
- Header `N OPEN` ignores deferred + completed.
- Two-device sync: complete-all on web → Android shows the note archived (and vice-versa) once both
  clients ship the §3 contract fields. Before Android ships them, confirm web-only round-trips don't
  lose `deferred`/`archived` (they won't be stripped until an Android write occurs).
