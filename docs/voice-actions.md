# Hark voice actions — one smart call, no backend

The intelligence that lets Hark **create, append to, or edit** notes from a single spoken
capture — deciding *which* note you mean — in **one structured LLM call**. No agent loop, no
server. Android (Kotlin) and Web (TS) implement this identical contract.

Fits the identity: `capture → tidy → resurface`. Your voice becomes the interface to notes you
already have, not just new ones. Anything that needs multiple chained calls or server state is
out of scope for v1 (that's the trigger to revisit a backend later).

---

## Two entry points, one call

| Entry point | `focusedNote` | Typical actions |
|---|---|---|
| General capture (home / widget TALK) | none | `create`, or `append` to a note resolved by name |
| Edit-by-voice (TALK from an open note) | the open note (full body) | `edit` / `append` on that note |

Both hit the same function with the same schema; only the context differs.

---

## The call

`POST {baseUrl}/chat/completions` with `response_format: {type: "json_object"}` (same as today's
tidy). One system message (verbatim below) + one user message (context).

### System prompt (copy verbatim on both platforms)

```
You are Hark, a voice-first note assistant. Turn the user's spoken transcript into exactly ONE
action on their notes, returned as a single JSON object.

Choose "action":
- "create": a new, standalone thought → a new note.
- "append": the user is clearly adding to an existing note they name or reference
  (e.g. "add milk to the grocery list", "in the sourdough note, jot that..."). Pick its id from
  "Your notes". Put any new prose in "body" (or "" if only adding to-dos) and any to-dos in "tasks".
- "edit": the user is revising the focused note (only when a Focused note is provided) → return
  the FULL rewritten note in "body".

Rules:
- Prefer "create". Only choose "append"/"edit" when you are confident which note is meant.
  If unsure, "create". Never mutate a note you are not sure about.
- Tidy the wording: fix disfluencies and false starts, keep the meaning and the speaker's voice.
  Never invent facts.
- If "Extract tasks" is true, pull actionable to-dos into "tasks" (imperative, concise); resolve
  relative dates against Today into "due" (YYYY-MM-DD) and set "dueHint" to the words that implied
  it. If "Extract tasks" is false, return an empty "tasks" array and do NOT split anything into tasks.
- Format "body" as clean Markdown when it helps (short paragraphs, "- " bullets, "#" headings for
  long notes). Do not over-format short notes.
- "title" is used only for "create" and "edit"; for "append" it is ignored.
- Respond with ONLY the JSON object. No prose, no code fences.

JSON shape:
{"action":"create|append|edit","targetNoteId":number|null,"title":string|null,"body":string,
 "tasks":[{"title":string,"due":string|null,"dueHint":string|null}],"reason":string}
```

### User message (context)

```
Today: 2026-08-25 (Tuesday)
Extract tasks: true
Focused note: none
Your notes:
- [id 12] Grocery list — "oat milk, bread, eggs, dish soap"
- [id 8] Sourdough, third attempt — "levain doubled in 5h at 24C, crumb tight near base"
- [id 5] Overheard on the 14 — "it's not a shortcut if you enjoy the long way"
Transcript:
"""
add oat milk and paper towels to the grocery list, and I'm out of coffee
"""
```

- **Note index rules:** the most-recent ~40 non-deleted notes as
  `- [id N] {title} · {taskCount} tasks — "{first ~80 chars of body}"`. Titles resolve the target;
  the **task count** tells the model a note is a checklist (so "add milk to the grocery list" appends
  a *task*, not prose). Cap count + snippet length to bound tokens.
- **Focused note:** when capture starts from an open note, replace `Focused note: none` with the full
  note **and its tasks**: `Focused note: [id 8] Sourdough — <body>\nIts tasks: Buy flour; …`. Bias is
  toward `edit`/`append` on it, and it can match the existing task phrasing.
- **Extract tasks:** the #1 toggle, threaded straight into the same call.

**Body vs tasks (important):** `body` is **prose only** — context/narrative. The to-do / checklist
items live in `tasks`, never restated in `body`. A pure checklist note has an **empty body** (at most
a one-line summary of what the list is for). This is why "append to the grocery list" adds tasks, and
why a note that's just a list doesn't carry a garbled prose copy of itself.

---

## Response schema

```jsonc
{
  "action": "create" | "append" | "edit",
  "targetNoteId": 12,          // required for append/edit; null for create
  "title": "Grocery list",     // applied on create & edit; ignored on append
  "body": "",                  // create: full body · append: text to ADD (may be "") · edit: full replacement
  "tasks": [                   // always "tasks to ADD" (v1 does not edit tasks individually)
    { "title": "Oat milk",     "due": null, "dueHint": null },
    { "title": "Paper towels", "due": null, "dueHint": null },
    { "title": "Coffee",       "due": null, "dueHint": null }
  ],
  "reason": "User said 'add … to the grocery list' → matched note 12."
}
```

---

## Client execution (shared logic, per platform)

```
result = call(transcript, today, extractTasks, focusedNote, noteIndex)

when (result.action):
  create ->
    id = insertNote(title = result.title ?: firstLine(transcript),
                    body  = result.body,
                    heardAs = transcript, source = SPOKEN)
    insertTasks(result.tasks, sourceNoteId = id)

  append ->
    note = getNote(result.targetNoteId) ?: return createFallback()   // safety
    if (result.body.isNotBlank()) note.body = note.body + "\n\n" + result.body
    note.heardAs = appendTrail(note.heardAs, transcript)             // optional provenance
    note.updatedAt = now; update(note)
    insertTasks(result.tasks, sourceNoteId = note.id)

  edit ->
    note = getNote(result.targetNoteId) ?: return createFallback()
    note.title = result.title ?: note.title
    note.body  = result.body
    note.heardAs = appendTrail(note.heardAs, transcript)
    note.updatedAt = now; update(note)
    insertTasks(result.tasks, sourceNoteId = note.id)
```

- `createFallback()` = a `create` with `body = result.body.ifBlank { transcript }`, tasks as given.
  **Capture is never lost**, and a wrong/missing target degrades to a new note, never a silent
  mutation of the wrong one.
- **Confirmation is the safety net** (no backend needed): the Talk result screen already shows the
  tidied result with AGAIN / KEEP. For `append`/`edit`, show the target first —
  *"→ Appending to 'Grocery list'"* / *"→ Editing 'Sourdough'"* — so the user sees and confirms the
  target before KEEP. AGAIN re-dictates.

---

## Fallbacks

- JSON parse fails / unknown `action` / missing required field → treat as `create`, `body =
  transcript`, `tasks = []`. (Same defensive rule as today's tidy.)
- `append`/`edit` with a `targetNoteId` not in the DB → `createFallback()`.
- Empty transcript → error, offer AGAIN (unchanged).

---

## Examples

1. **New capture** — *"call the framer tomorrow about the Kiyoshi print; the levain doubled in five
   hours at 24 degrees"* →
   `create` · body: *"The levain doubled in five hours at 24°C."* · tasks: `[{Call the framer about
   the Kiyoshi print, due: <tomorrow>, dueHint: "tomorrow"}]`

2. **Append to-dos by name** — *"add oat milk and paper towels to the grocery list, and I'm out of
   coffee"* → `append` id 12 · body: "" · tasks: `[Oat milk, Paper towels, Coffee]`

3. **Append prose by name** — *"in the sourdough note, add that I tried 80% hydration and it was too
   slack"* → `append` id 8 · body: *"Tried 80% hydration — too slack."* · tasks: `[]`

4. **Edit focused note** — (focused = Sourdough) *"rewrite this clearer and add that bake time was 40
   minutes"* → `edit` id 8 · body: *full rewritten note incl. 40‑min bake* · tasks: `[]`

5. **Reflective, tasks off** — (Extract tasks: false) *"just thinking about life goals — travel more,
   maybe learn piano, be less anxious"* → `create` · body: reflective Markdown prose · tasks: `[]`
   (no checklist forced — fixes the "life goals became a to-do list" annoyance)

---

## Per-platform mapping

**Android**
- `TidyService.tidy(...)` → `HarkService.process(transcript, today, extractTasks, focusedNote, notes): HarkAction`
- New `domain/HarkAction.kt` (sealed: Create/Append/Edit + tasks).
- `HarkRepository` gains `applyAction(action, transcript)` doing the execution above.
- Talk VM passes `extractTasks` (from settings/toggle) + optional `focusedNoteId`; result screen shows the target for append/edit.

**Web**
- `tidyNote(...)` → `processCapture(transcript, today, extractTasks, focusedNote, notes, apiKey, baseUrl, model): HarkAction`
- `applyAction(action, transcript)` executes via Dexie.
- TalkModal / ComposeModal pass `extractTasks` + optional focused note; NoteDetail gains a "talk to edit" affordance that sets the focused note.

Keep the system prompt + schema **identical** across both (copy from this file). The only
duplication is the prompt string + a small parser — acceptable; if the smarts ever go multi-step,
that's the signal to centralize behind one endpoint (Cloudflare Worker) instead.

---

## v1 scope / deferred

- **In:** create / append (body + tasks) / edit-focused; the extract-tasks toggle; Markdown output;
  name-based target resolution from the note index; confirmation of target before apply.
- **Deferred:** editing/removing *individual existing tasks* by voice ("mark milk done", "move the
  framer task to Friday"); semantic (embedding) target resolution for large note sets; multi-note
  actions in one utterance; long-form (30-min) mode. Each is additive on this same schema.
