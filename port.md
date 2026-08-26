# Port to Android — hand-off

Bring the **Android app up to the Web app's current behavior**. The Web (`web/`) is the finished
reference and the **source of truth**; the Android app (`app/`) is behind. This doc is the plan.

Three things to port: **(1) voice-actions** (one smart call → create/append/edit), **(2) markdown
notes**, **(3) the Shelf** (long-form notes). Plus the tuned prompts and the note-model changes.

> Copy the **prompt strings verbatim** from `web/src/ai/groq.ts` — they were tuned carefully.
> `docs/voice-actions.md` is older and partly stale; **this file supersedes it** for the port.

---

## 0. Build environment (you'll need this to compile Android)

```powershell
$env:JAVA_HOME='D:\AndroidStudio\jbr'
Set-Location 'D:\dev\hark'
.\gradlew.bat :app:assembleDebug --console=plain
```
- Toolchain is bleeding-edge: **AGP 9.2.1, Kotlin 2.2.10, compileSdk 37, minSdk 26**.
- `gradle.properties` has `android.disallowKotlinSourceSets=false` — **keep it** (KSP↔AGP9 interop).
- KSP powers Room codegen. Adding a dependency = edit `gradle/libs.versions.toml` + `app/build.gradle.kts`.
- A cold build is slow (~2–6 min); the daemon warms up after the first.
- To install on the device: `& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r app\build\outputs\apk\debug\app-debug.apk` (device must have USB-debugging authorized).

---

## 1. What Android already has (do NOT rebuild these)

- `com.hark`, Kotlin + Compose (M3), Room (+KSP), Glance widget, **manual DI** via `di/AppContainer.kt`
  (no Hilt), OkHttp + `org.json`.
- **AI:** `ai/OpenAiClient.kt` (`completeJson`, `completeText`, `transcribe` = Groq Whisper
  `whisper-large-v3-turbo`), `ai/Settings.kt` (`AiSettings` + `SettingsStore`, has theme/widget opts),
  `ai/TidyService.kt` (OLD single-note tidy → `TidyResult`), `ai/RecallService.kt`.
- **Data:** `data/local/` (`NoteEntity`, `TaskEntity`, `Source`, DAOs, `HarkDatabase`, `Converters`),
  `data/repo/HarkRepository.kt` (`stream`, `openCount`, `tasks`, `notes`, `searchNotes`, `setTaskDone`,
  `saveTidied`, `getNoteById`, `observeTasksForNote`, `updateTask`, `deleteTask`).
- **UI:** bottom-nav shell in `MainActivity.kt` (STREAM / TODAY / RECALL / SETTINGS, plus pushed
  Talk / Compose / NoteDetail); screens under `ui/stream`, `ui/talk`, `ui/compose`, `ui/note`,
  `ui/settings`, `ui/today`, `ui/recall`; `ui/components/HarkComponents.kt`; `ui/theme/`
  (Color/Type/Theme — bundled fonts Libre Baskerville + Syne Mono in `res/font`).
- **Widget:** `widget/StreamWidget.kt` (Glance, Syne-Mono-as-bitmap labels, nested tasks),
  receiver, `ToggleTaskAction`, `TalkTrampolineActivity`.

The Android capture is the **old** flow: `TidyService.tidy(transcript)` → one new note. No
append/edit, no markdown, no shelf. That's what we're upgrading.

---

## 2. The contract to preserve (behavior — same on both platforms)

1. **One LLM call → a `HarkAction`** and the client executes it locally. Shape:
   `{action:"create|append|edit", targetNoteId:number|null, title:string|null, body:string,
   tasks:[{title,due,dueHint}], reason:string}`.
2. **create / append / edit:**
   - `create` = new note. `append` = add to an existing note the user named (target from the note
     index). `edit` = rewrite the focused note's body.
   - **Prefer create.** If unsure which note, create. A missing/unknown `targetNoteId` **degrades to
     create** — never mutate the wrong note. **Capture is never lost.**
3. **Confirm the target before applying** (append/edit): the Talk RESULT screen shows
   *"→ APPENDING TO 'Grocery list'"* / *"→ EDITING '…'"* / "NEW NOTE" with KEEP / AGAIN.
4. **body = prose only.** Never put checklist items in `body`; they go in `tasks`. A pure checklist
   note has an **empty body**. **Append items to a list → `tasks`, not prose.**
5. **Tidy = near-verbatim transcription cleanup, NOT a summary.** Keep every point/example/aside in
   order; remove only filler / false starts / repetition; fix punctuation; paragraph breaks; first
   person; never "the transcript…". Don't start the body with the title as a heading.
6. **Tasks toggle.** Short/stream captures extract tasks per the toggle. **A capture that auto-shelves
   (transcript > 400 chars) skips task extraction** (shelf = prose).
7. **Note index** given to the model: recent ~40 non-deleted notes as
   `- [id N] {title} · {taskCount} tasks — "{~80-char snippet}"`. The **task count** is what lets it
   tell a checklist apart. For edit-by-voice, also pass the focused note's full body **and its tasks**.
8. **Shelf:** long-form home. Shelf notes are **hidden from the Stream**, open **full-screen**.
   **Shape** = on-demand reformat to markdown, **prose-only**, and **rebuilds from the raw transcript
   (`heardAs`) when present**. Promote/demote between Stream/Shelf; **shelving clears the widget pin**.
   Auto-shelve when the voice transcript > **400 chars** (`SHELF_THRESHOLD`).
9. **Title optional** (empty allowed; UI shows a placeholder). Shape generates one if missing; titles
   are **3–6 words**.
10. **Markdown** rendered in the note view + the Talk result preview; Stream/Grid snippets are
    markdown-stripped to plain text.

---

## 3. Web files = the spec (read these, mirror them)

| Concern | Web file | Port to (Android) |
|---|---|---|
| `HarkAction`/`NoteRef`/`FocusedNote`/`HarkTask` types, `processCapture`, `shapeNote`, `ACTIONS_SYSTEM`, `SHAPE_SYSTEM`, `normalizeAction`, whisper model | `web/src/ai/groq.ts` | new `ai/HarkService.kt` (+ `domain/HarkAction.kt`); reuse `OpenAiClient` |
| `applyAction`, `recentNoteRefs`, `focusedNoteOf`, `newShelfNote`, `setShelf`, `SHELF_THRESHOLD` | `web/src/db/actions.ts` | `data/repo/HarkRepository.kt` |
| `shelf` flag; defaults | `web/src/db/db.ts` | `data/local/Entities.kt` + Room migration |
| Talk flow (record→transcribe→process→RESULT→apply, toggle, shelf-skip-tasks, focusedNote) | `web/src/components/TalkModal.tsx` | `ui/talk/TalkViewModel.kt` + `TalkScreen.kt` |
| Typed compose routed through the pipeline + toggle | `web/src/components/ComposeModal.tsx` | `ui/compose/ComposeScreen.kt` |
| Markdown render + tap-to-edit, SHAPE (from heardAs), move to shelf/stream, collapsible HEARD AS, optional title, double-title strip, wider column | `web/src/components/NoteDetail.tsx` | `ui/note/NoteDetailScreen.kt` |
| Shelf reading list | `web/src/components/ShelfView.tsx` | new `ui/shelf/ShelfScreen.kt` |
| Stream/Shelf split, view toggle, full-screen writer for shelf, new-shelf-note | `web/src/App.tsx` | `MainActivity.kt` nav |
| Header STREAM/SHELF toggle | `web/src/components/Header.tsx` | Stream header / nav |
| Markdown renderer + `stripMarkdown` | `web/src/components/Markdown.tsx`, `web/src/lib/md.ts` | markdown lib + a Kotlin `stripMarkdown` |

---

## 4. Step-by-step (recommended order)

**Step 1 — Room: add `shelf`.**
- `NoteEntity`: add `val shelf: Boolean = false`.
- Bump DB version 1→2 and add a migration:
  `ALTER TABLE notes ADD COLUMN shelf INTEGER NOT NULL DEFAULT 0`, register it in
  `Room.databaseBuilder(...).addMigrations(MIGRATION_1_2)`. (Do NOT use destructive migration — it
  wipes the user's notes.)

**Step 2 — `ai/HarkService.kt` (replaces `TidyService`).**
- `domain/HarkAction.kt`: `data class HarkAction(action, targetNoteId, title, body, tasks, reason)`
  with `enum Action { CREATE, APPEND, EDIT }`; `data class HarkTask(title, dueMillis?, dueHint?)`;
  `data class NoteRef(id, title, snippet, taskCount)`; `data class FocusedNote(id, title, body, tasks: List<String>)`.
- `process(transcript, today, extractTasks, notes: List<NoteRef>, focused: FocusedNote?): HarkAction`
  — build the user message exactly like `processCapture` (today, `Extract tasks:`, `Focused note:`
  incl. `Its tasks:`, the `- [id N] title · N tasks — "snippet"` list, the transcript), call
  `OpenAiClient.completeJson(ACTIONS_SYSTEM, user)`, parse with `org.json`, **port `normalizeAction`**
  (coerce action; force `tasks=[]` when `!extractTasks`; **degrade append/edit with null target to
  create**). On any exception → create-from-transcript fallback.
- `shape(title, body, extractTasks): ShapeResult(title, body, tasks)` — `SHAPE_SYSTEM`, same parse.
- **Copy `ACTIONS_SYSTEM` and `SHAPE_SYSTEM` verbatim** from `web/src/ai/groq.ts` into Kotlin
  triple-quoted strings. Keep `whisper-large-v3-turbo` (already in `OpenAiClient.transcribe`).

**Step 3 — `HarkRepository`.**
- `recentNoteRefs(limit=40)`: recent non-deleted notes → `NoteRef` (snippet = first ~80 chars of body,
  whitespace-collapsed; `taskCount` = count of that note's non-deleted tasks).
- `focusedNoteOf(id)`: the note + its task titles.
- `applyAction(action, transcript, source)`: create / append / edit over Room (see `actions.ts`):
  - create → insert note; `shelf = source==VOICE && transcript.length > 400`; `heardAs = transcript`
    if voice; then insert `action.tasks` with `sourceNoteId`.
  - append → load target (missing → create fallback); append `body` with `\n\n`; add tasks; update.
  - edit → load target; replace `body`, set `title ?: existing`; add tasks; update. (append/edit also
    append the transcript to `heardAs` as a provenance trail — see `trail()`.)
- `newShelfNote(): Long` (blank note, `shelf=true`, source TYPED). `setShelf(id, shelf)` — **when
  shelving, also clear `pinnedToWidget`.**
- **`stream` must exclude shelf notes** (`!note.shelf`). Consider excluding shelf-note tasks from
  `openCount`/Today (minor).

**Step 4 — Talk.**
- `TalkViewModel`: after Whisper transcript, `notes = repo.recentNoteRefs()`,
  `willShelf = focused==null && transcript.length > 400`,
  `action = harkService.process(transcript, today, extractTasks && !willShelf, notes, focused)`.
  The Android `TalkScreen` already has a RESULT phase — show the **action target** there
  (append/edit → the target note's title; resolve from `focused` or `notes`) with KEEP/AGAIN.
  KEEP → `repo.applyAction(action, transcript, VOICE)`.
- Add an **Extract-tasks toggle** (default on) in the listening view.
- **Edit-by-voice:** Talk launched from an open note passes that note as `focused`
  (via the trampoline/nav — plumb a `focusedNoteId`).

**Step 5 — Compose (typed).** Route "Tidy & Save" through `process` + `applyAction(TYPED)`; add the
extract-tasks toggle. Keep the plain "Save" path (no AI).

**Step 6 — Markdown + NoteDetail.**
- Add a Compose markdown renderer dependency. Options: `com.mikepenz:multiplatform-markdown-renderer-m3`
  or `dev.jeziellago:compose-markdown` (Markwon-based). Style it to Hark (serif body via `HarkType`,
  headings by **size not weight**, Syne Mono for code, rust links). Verify it builds on this toolchain.
- NoteDetail: render body as markdown by default, **tap to edit** raw; **SHAPE** button (shelf notes)
  → `harkService.shape(...)` sourced from **`heardAs` when present** else body, `extractTasks=false`,
  write back title+body; **move to Shelf/Stream** button; **collapsible HEARD AS** (chevron, hidden by
  default); **optional title** (don't force "Untitled note"); **strip a leading `# Title`** from the
  body when it equals the note title. Make it reactive (Room Flow) so voice edits show live.
- Kotlin `stripMarkdown(s)` for snippets — port the regex from `web/src/lib/md.ts`.

**Step 7 — Shelf.**
- `ui/shelf/ShelfScreen.kt`: reading list of `shelf` notes (title, stripped excerpt, date) + a "NEW"
  action (`newShelfNote` → open full-screen). Empty state.
- Nav: a **SHELF ↔ STREAM** toggle (in the Stream header or bottom nav). Shelf notes open the
  **full-screen** NoteDetail (hide the stream). Add a **Shelf** entry to the capture FAB/bar.
- The `stream` already excludes shelf notes (Step 3), so they only appear on the Shelf.

**Step 8 — Widget.** Exclude `shelf` notes from the Glance stream widget (it already de-dupes a note's
child tasks; just also filter `!note.shelf`).

**Step 9 — Build, install, smoke-test** each behavior from §2 with a real Groq key.

---

## 5. Gotchas

- **Prompts are load-bearing** — copy them exactly; small wording changes regressed us (summary vs
  verbatim, tasks-in-body, forced titles). See git history around these edits if unsure.
- **Room migration**, not destructive (don't wipe notes).
- **Markdown lib on the 2026 toolchain**: pick one, add the dep, and do a throwaway compile before
  building the whole NoteDetail on it (same caution we used for KSP/Glance/lifecycle).
- **`due` type**: web stores `dueHint` (string) + optional `due` (YYYY-MM-DD); Android `TaskEntity`
  uses `dueAt: Instant?` + `dueHint`. Parse `due` → `Instant` (start of day, system zone) as
  `TidyService` already did.
- **openCount / Today**: decide whether shelf-note tasks count (web currently counts all; keeping
  shelf tasks out of the stream counts is cleaner but optional).
- Verify JSON parsing handles the model returning `"null"` strings (org.json's `optString` quirk) —
  the web `normStr`/`normDue` guard this; port that.

---

## 6. Definition of done

Both platforms behave identically for: append-to-named-note, edit-by-voice, near-verbatim tidy,
markdown notes, the Shelf (auto + manual, Shape-from-transcript, no auto-tasks), optional titles.
Then the next milestone (separate) is **sync**, and after that, **deploy the PWA**.
