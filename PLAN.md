# Hark — Implementation Plan

Widget-first notes & tasks for Android. Speak a messy thought; Hark *tidies* it into a
clean note plus extracted tasks, and it all lives in one stream you mostly touch from a
home-screen widget.

The `prototype/` canvas doc is the visual + interaction spec. Screens and states below map
back to it 1:1, and we reuse its exact palette and type.

---

## Decisions locked

| Area | Decision |
|---|---|
| Goal | Personal daily driver. Local-first, minimal surface, no accounts. |
| AI ("Tidy" + Recall) | Generic **OpenAI-compatible** chat-completions client. Base URL + API key + model are a **runtime Setting**, defaulting to **Groq**. No backend. |
| Storage | **Room**, on-device. Sync is a later phase; schema is prepped for it. |
| First milestone | **Vertical slice**: tap widget → Talk → Tidy → item lands in Stream. |
| Speech→text | Android on-device **`SpeechRecognizer`** (live partial transcripts). |
| First widget | **Stream 4×2** (Glance). |
| Reminders | Due-date fields now; **notifications are a later phase**. |

## Stack

- **Kotlin + Jetpack Compose** (Material 3) for app UI.
- **Glance** (Compose-based) for widgets.
- **Room** (+ FTS) for storage; **DataStore** for settings; **Jetpack Security** (EncryptedSharedPreferences) for the API key.
- **Ktor Client** + **kotlinx.serialization** for the AI calls (Retrofit is a fine alternative).
- **Hilt** for DI. **Navigation-Compose** for routing.
- **minSdk 31** (Android 12) for good interactive widgets; compile/target latest.

---

## Architecture

Single `:app` module, split by package. Clean-ish layering: `ui → domain → data`, with
`ai`, `speech`, `widget` as feature-side services.

```
com.hark
├── data/            Room entities, DAOs, database, repositories
│   ├── local/       NoteEntity, TaskEntity, NoteFts, HarkDatabase, DAOs
│   └── repo/        StreamRepository, NoteRepository, TaskRepository
├── domain/          StreamItem sealed model, use-cases (TidyTranscript, Recall)
├── ai/              OpenAiCompatClient, ChatModels, TidyService, RecallService
├── speech/          SpeechRecognizerSource (callbackFlow of partial/final/rms)
├── settings/        SettingsStore (DataStore) + SecureKeyStore (encrypted key)
├── ui/
│   ├── theme/       Color, Type (Libre Baskerville / Syne Mono), HarkTheme
│   ├── components/  StreamItemRow, TaskCheckbox, SectionLabel, PillButton, Waveform…
│   ├── stream/  today/  note/  compose/  talk/  recall/  settings/
│   └── nav/         HarkNavHost, bottom nav (Stream · Today · Recall · Widgets)
├── widget/          StreamWidget (Glance), receiver, ToggleTaskAction, TalkTrampoline
├── notifications/   [M5] reminder scheduling
└── di/              Hilt modules
```

---

## Data model (Room)

Two tables (Note, Task) merged into one stream in the repository — matches the prototype's
single `items` river while keeping fields clean and FTS easy. Notes can own tasks; a task
can point back to the note it was extracted from.

```kotlin
@Entity(tableName = "notes")
data class NoteEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val title: String,
  val body: String,
  val heardAs: String?,          // raw transcript → "HEARD AS"
  val source: Source,            // SPOKEN | TYPED
  val pinnedToWidget: Boolean = false,
  val createdAt: Instant,
  val updatedAt: Instant,
  // sync prep (unused in v1):
  val remoteId: String? = null,
  val deleted: Boolean = false,
)

@Entity(tableName = "tasks")
data class TaskEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val title: String,
  val done: Boolean = false,
  val doneAt: Instant? = null,
  val dueAt: Instant? = null,     // nullable; stored now, notified in M5
  val dueHint: String? = null,    // e.g. HEARD "TOMORROW"
  val sourceNoteId: Long? = null, // FK → notes.id
  val createdAt: Instant,
  val updatedAt: Instant,
  val remoteId: String? = null,
  val deleted: Boolean = false,
)

@Fts4(contentEntity = NoteEntity::class)   // Recall search
@Entity(tableName = "notes_fts")
data class NoteFts(val title: String, val body: String, val heardAs: String?)
```

- `StreamRepository` `combine`s the note + task DAO `Flow`s into a single
  `Flow<List<StreamItem>>` (sealed: `StreamItem.Note` / `StreamItem.Task`), sorted by
  `createdAt` desc and grouped into TODAY / EARLIER for the UI.
- Widget reads the same repo. On any write, call `StreamWidget().updateAll(context)`.

---

## AI layer (no backend)

One thin OpenAI-compatible client hitting `POST {baseUrl}/chat/completions`.

**Settings (runtime, in the Widgets/Settings screen):**
- `baseUrl` — default `https://api.groq.com/openai/v1`
- `apiKey` — user's own key, stored via EncryptedSharedPreferences
- `model` — default a fast Groq model (configurable; verify the current id in Settings, e.g. Llama 3.3 70B)
- optional: temperature, separate model for Recall

**Tidy** (`TidyTranscript`): raw transcript + today's date → strict JSON. Use
`response_format = {"type":"json_object"}` (Groq supports it) plus a schema in the prompt,
and defensive parsing — if the JSON is unusable, fall back to saving the raw text as a
plain note so capture never fails.

```json
{
  "title": "Framing & the third levain",
  "note":  "The levain doubled in five hours at 24°C. Try a longer autolyse next time; the crumb was tight near the base.",
  "tasks": [
    {"title": "Call the framer about the Kiyoshi print", "due": "2026-08-25", "dueHint": "tomorrow"},
    {"title": "Buy bread flour", "due": null, "dueHint": null}
  ]
}
```
System prompt: tidy faithfully, don't invent, extract tasks, resolve relative dates against
the supplied today's date, keep the transcript as `heardAs`.

**Recall** (`Recall`, M4): FTS query over notes → top-N hits → LLM writes a one-sentence
"short answer" grounded in those hits; UI shows the answer + the hit rows. Embeddings /
vector search are explicitly deferred.

---

## Voice / Talk flow

`SpeechRecognizerSource` wraps `SpeechRecognizer` in a `callbackFlow` emitting
`Partial(text)` / `Final(text)` / `Rms(level)` / `Error`. RMS drives the live waveform.

State machine (mirrors prototype 1c-talk):
`listening` (waveform + partial text streaming in) → **STOP & TIDY** →
`tidying` (LLM call) → `result` (tidied note + tasks) → **KEEP** (persist) or **AGAIN** (re-listen).

- `RECORD_AUDIO` permission handled on first Talk.
- Handle `ERROR_NO_MATCH` / timeouts gracefully → offer AGAIN.
- Widget **TALK** → `TalkTrampoline` activity → launches app straight into Talk, listening.

---

## Stream 4×2 widget (Glance)

Renders: header (`HARK · N OPEN` / `TUE 24`), up to 3 open items with checkboxes, divider,
action row (`＋ · TALK · TODAY · ⌕`).

- Checkbox → `ToggleTaskAction` (`actionRunCallback`) flips `done` in Room; widget re-renders.
- `＋` and `TALK` → `actionStartActivity` into Compose / Talk (via the trampoline).
- **Known constraint:** RemoteViews/Glance can't reliably load custom `res/font` faces, so
  the widget approximates with system serif/monospace. In-app typography is pixel-accurate.

---

## Design system (ported from prototype)

**Color**
- Light: paper `#f4f2ed`, ink `#1c1b19`, rust accent `#8a4b34`; muted text via ink alpha.
- Dark: bg `#33342f`, text `#eceae4`, rust `#d99f83`.
- `HarkTheme` supports Light / Dark / System (System is the default per the Widgets screen).

**Type**
- **Libre Baskerville** — titles & body (serif). **Syne Mono** — small uppercase labels &
  meta, wide letter-spacing.
- Bundle the TTFs in `res/font` (reliable offline) rather than downloadable fonts.

**Components to build**
`SectionLabel` (Syne Mono caps), `MetaLabel`, `TaskCheckbox`, `StreamItemRow` (task/note
variants), `PillButton` (WRITE/TALK), `BottomNav`, `Waveform`, `TalkBars`, `NoteDot`.

---

## Screens ↔ prototype

| Screen | Prototype | Notes |
|---|---|---|
| Stream | 2a, 1c-stream | One river; filters ALL/OPEN/NOTES; bottom WRITE/TALK. |
| Today | 2b | Date header; OVERDUE / DUE TODAY / WRITTEN TODAY; empty-state line. |
| Note | 2c | Title, body, TASKS IN THIS NOTE, HEARD AS, actions (EDIT/PIN/DUE). |
| Recall | 2d | Query, SHORT ANSWER, hits, TRY ASKING. (M4) |
| Widgets/Settings | 2e | Widget list + APPEARANCE + **AI config lives here**. |
| Compose | 1c-compose | Typed capture; dash-line → task. |
| Talk | 1c-talk | The voice flow above. |
| (dark) | 2f, 1b | Handled by theme, not separate screens. |

---

## Milestones

**M0 — Starter & foundation** *(you scaffold, I wire)*
Compose + Hilt + Room skeleton, DataStore, theme (colors + bundled fonts), Navigation-Compose
with bottom nav, base components.

**M1 — Vertical slice (the hero loop)** ← first real target
Note/Task entities + DAOs + `StreamRepository`; Stream screen (reads Room, checkboxes toggle);
`SpeechRecognizerSource` + Talk screen; OpenAI-compatible client + `TidyTranscript` + Settings
(Groq defaults); KEEP persists → shows in Stream; **Stream 4×2 Glance widget** with toggle +
TALK/＋ launch. Demoable end-to-end from the home screen.

**M2 — App breadth**
Today, Note detail (tasks-in-note, heard-as), Compose (typed), filters, pin-to-widget,
note↔task linking, empty states, dark-theme pass.

**M3 — Widget family**
Capture bar 4×1, Today 2×2, One-note 2×2; widget appearance controls (transparency, toolbar
toggles) from the Widgets screen.

**M4 — Recall**
FTS search + LLM short-answer + hits + suggestions. Optional: Groq Whisper as a selectable
higher-accuracy STT.

**M5 — Reminders**
Wire due dates to WorkManager/AlarmManager notifications; permission handling; complete/snooze
from the notification.

**M6 — Sync (optional, later)**
Pick a backend (Supabase / Firebase / self-host) + auth + conflict resolution. `updatedAt` /
`remoteId` / `deleted` fields are already in the schema for this.

---

## M1 checklist (ordered)

1. Room: `NoteEntity`, `TaskEntity`, DAOs, `HarkDatabase`, `Instant` type converters.
2. `StreamRepository` merging note+task flows → `List<StreamItem>`, grouped TODAY/EARLIER.
3. Stream screen + `StreamItemRow` + `TaskCheckbox`; toggle writes through repo.
4. `SettingsStore` (baseUrl/model) + `SecureKeyStore` (apiKey); Settings UI stub for the three fields.
5. `OpenAiCompatClient` (Ktor) + `TidyTranscript` use-case with JSON-mode + fallback.
6. `SpeechRecognizerSource` (callbackFlow) + RECORD_AUDIO permission.
7. Talk screen state machine → result → KEEP persists note + tasks.
8. `StreamWidget` (Glance): header + 3 items + action row; `ToggleTaskAction`; TALK/＋ launch; `updateAll` on writes.
9. Wire `TalkTrampoline` so the widget's TALK opens straight into listening.

---

## Constraints & risks to keep in view

- **API key on-device** is fine for *your own* personal build. If the goal ever shifts to a
  shared/published multi-user app, the key must move behind a backend proxy — revisit then.
- **`SpeechRecognizer`** relies on the device's recognition service, has per-utterance time
  limits, and may need network on some devices. Always allow re-listen (AGAIN).
- **Widget fonts** approximate (see above).
- **Model ids drift** — because base URL / model are configurable, a stale default is
  harmless; just fix it in Settings.
- **JSON reliability** — always keep the raw transcript so a bad Tidy never loses capture.

---

## What I need from you for the starter (M0)

- **applicationId / package** (I assumed `com.hark`).
- Confirm **minSdk 31**, compile/target latest, **Kotlin + Compose + Material 3**.
- Add these to the starter (or tell me to add them): Hilt + KSP, Room + Room-KSP,
  Glance, Ktor client + kotlinx.serialization, DataStore, Jetpack Security, Navigation-Compose,
  Compose BOM. Bundle the two font TTFs in `res/font`.
- Single `:app` module is fine to start.
- Your **Groq API key is entered at runtime** in Settings — nothing secret goes in the repo.
