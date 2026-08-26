import { DEFAULT_BASE_URL, DEFAULT_MODEL } from '../db/db';

// ── Voice-action contract (see docs/voice-actions.md) ────────────────────────
export interface NoteRef { id: number; title: string; snippet: string; taskCount: number; }
export interface FocusedNote { id: number; title: string; body: string; tasks: string[]; }
export interface HarkTask { title: string; due?: string | null; dueHint?: string | null; }
export interface HarkAction {
  action: 'create' | 'append' | 'edit';
  targetNoteId: number | null;
  title: string | null;
  body: string;
  tasks: HarkTask[];
  reason: string;
}

const chatUrl = (baseUrl: string) =>
  `${(baseUrl || DEFAULT_BASE_URL).replace(/\/+$/, '')}/chat/completions`;
const transcribeUrl = (baseUrl: string) =>
  `${(baseUrl || DEFAULT_BASE_URL).replace(/\/+$/, '')}/audio/transcriptions`;

export async function transcribeAudio(
  audioBlob: Blob,
  apiKey: string,
  baseUrl: string
): Promise<string> {
  const formData = new FormData();
  formData.append('file', audioBlob, 'audio.webm');
  formData.append('model', 'whisper-large-v3-turbo');
  formData.append('response_format', 'json');

  const res = await fetch(transcribeUrl(baseUrl), {
    method: 'POST',
    headers: { Authorization: `Bearer ${apiKey}` },
    body: formData,
  });

  if (!res.ok) {
    const err = await res.text();
    throw new Error(`Transcription failed (${res.status}): ${err}`);
  }
  const data = await res.json();
  return data.text || '';
}

const ACTIONS_SYSTEM = `You are Hark, a voice-first note assistant. Turn the user's transcript into exactly ONE action on their notes, as a single JSON object.

Choose "action":
- "create": a new, standalone thought → a new note.
- "append": the user is clearly adding to an existing note they name or reference (e.g. "add milk to the grocery list", "in the sourdough note, jot that..."). Pick its id from "Your notes".
- "edit": the user is revising the Focused note (only when one is provided) → return the FULL rewritten prose in "body".

How a note is shaped — this matters:
- "body" is PROSE ONLY: context, thoughts, narrative. NEVER put the to-do / checklist items in "body"; the items go in "tasks".
- If a note is essentially just a checklist with no real prose, leave "body" EMPTY (at most a one-line summary of what the list is for — never the items themselves).

Rules:
- Prefer "create". Only "append"/"edit" when you are confident which note is meant. If unsure, "create". Never mutate a note you are not sure about.
- Keep EXACTLY what was said — this is a transcription cleanup, NOT a rewrite or summary. Preserve every distinct point, example, aside, and detail, in the original order. Do NOT summarize, condense, merge, paraphrase away, or drop anything. Remove ONLY filler ("um", "like", "you know"), false starts, and verbatim repetition; fix punctuation, capitalization, and obvious transcription errors; break it into paragraphs. The result should be nearly as long as the transcript. Never invent facts.
- Write it in the speaker's own words and voice (first person), AS the note. Never narrate the recording — no "the transcript…", "the speaker…", "I described…", "this note covers…".
- If "Extract tasks" is true, pull actionable items/to-dos into "tasks" (imperative, concise); resolve relative dates against Today into "due" (YYYY-MM-DD), "dueHint" = the words that implied it. If false, return "tasks": [] and do not split anything into tasks.
- For "append": adding items/things to a list or checklist → put them in "tasks", NOT "body". Use "body" only when the user is genuinely adding narrative prose. Mirror the note's existing task phrasing when obvious (e.g. tasks that start with "Buy ...").
- Use Markdown in "body" to make longer or multi-topic notes readable: short "##" section headings, "- " bullet lists, **bold** for key terms, "> " for quotes. Keep short, single-idea notes as plain prose — don't over-format. Do NOT start the body with the title as a heading — the title is stored separately; begin the body with the content itself.
- "title": a short, specific title of 3-6 words, no trailing punctuation. Used only for "create"/"edit"; ignored for "append".
- Respond with ONLY the JSON object. No prose, no code fences.

JSON shape:
{"action":"create|append|edit","targetNoteId":number|null,"title":string|null,"body":string,"tasks":[{"title":string,"due":string|null,"dueHint":string|null}],"reason":string}`;

export async function processCapture(opts: {
  transcript: string;
  apiKey: string;
  baseUrl: string;
  model?: string;
  extractTasks: boolean;
  notes: NoteRef[];
  focusedNote?: FocusedNote | null;
}): Promise<HarkAction> {
  const { transcript, apiKey, baseUrl, model = DEFAULT_MODEL, extractTasks, notes, focusedNote } = opts;

  const fallback = (): HarkAction => ({
    action: 'create',
    targetNoteId: null,
    title: transcript.slice(0, 40) || 'Untitled note',
    body: transcript,
    tasks: [],
    reason: 'fallback',
  });

  if (!apiKey) return fallback();

  const today = new Date();
  const dateStr = today.toISOString().split('T')[0];
  const dow = today.toLocaleDateString('en-US', { weekday: 'long' });
  const noteLines = notes.length
    ? notes
        .map((n) => `- [id ${n.id}] ${n.title}${n.taskCount ? ` · ${n.taskCount} tasks` : ''}${n.snippet ? ` — "${n.snippet}"` : ''}`)
        .join('\n')
    : '(none yet)';
  const focused = focusedNote
    ? `[id ${focusedNote.id}] ${focusedNote.title}${focusedNote.body ? ` — ${focusedNote.body}` : ''}` +
      (focusedNote.tasks.length ? `\nIts tasks: ${focusedNote.tasks.join('; ')}` : '')
    : 'none';

  const user = `Today: ${dateStr} (${dow})
Extract tasks: ${extractTasks}
Focused note: ${focused}
Your notes:
${noteLines}
Transcript:
"""
${transcript}
"""`;

  try {
    const res = await fetch(chatUrl(baseUrl), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${apiKey}` },
      body: JSON.stringify({
        model: model || DEFAULT_MODEL,
        messages: [
          { role: 'system', content: ACTIONS_SYSTEM },
          { role: 'user', content: user },
        ],
        response_format: { type: 'json_object' },
        temperature: 0.2,
      }),
    });
    if (!res.ok) throw new Error(`AI ${res.status}: ${(await res.text()).slice(0, 200)}`);
    const data = await res.json();
    const content = data.choices?.[0]?.message?.content || '{}';
    return normalizeAction(JSON.parse(content), transcript, extractTasks);
  } catch (e) {
    return fallback();
  }
}

const normStr = (v: unknown): string | null =>
  typeof v === 'string' && v.trim() && v !== 'null' ? v.trim() : null;
const normDue = (v: unknown): string | null => {
  const s = normStr(v);
  return s && /^\d{4}-\d{2}-\d{2}$/.test(s) ? s : null;
};

function normalizeAction(raw: any, transcript: string, extractTasks: boolean): HarkAction {
  const action: HarkAction['action'] = ['create', 'append', 'edit'].includes(raw?.action) ? raw.action : 'create';
  const title = normStr(raw?.title);
  const body = typeof raw?.body === 'string' ? raw.body : '';
  const tasks: HarkTask[] =
    extractTasks && Array.isArray(raw?.tasks)
      ? raw.tasks
          .filter((t: any) => t && typeof t.title === 'string' && t.title.trim())
          .map((t: any) => ({ title: t.title.trim(), due: normDue(t.due), dueHint: normStr(t.dueHint) }))
      : [];
  const reason = typeof raw?.reason === 'string' ? raw.reason : '';
  const targetNoteId = typeof raw?.targetNoteId === 'number' ? raw.targetNoteId : null;

  // create — or degrade append/edit with no target to a safe create (never mutate the wrong note)
  if (action === 'create' || targetNoteId == null) {
    return {
      action: 'create',
      targetNoteId: null,
      title: title ?? (transcript.slice(0, 40) || 'Untitled note'),
      body: body || transcript,
      tasks,
      reason: reason || (action !== 'create' ? 'no target → create' : ''),
    };
  }
  return { action, targetNoteId, title, body, tasks, reason };
}

const SHAPE_SYSTEM = `You are Hark. Shape the user's raw note into clean, readable Markdown. Keep their words, voice, and meaning; never invent anything.
- Keep EXACTLY what the author wrote — a cleanup/formatting pass, NOT a rewrite or summary. Preserve every point, example, and detail in order; do NOT summarize, condense, merge, or drop anything. Remove only filler and verbatim repetition, fix punctuation, and add paragraph breaks / Markdown structure. Keep it about the same length. Write in the author's own words (first person); never narrate.
- Use short "##" headings, "- " bullet lists, and **bold** for key terms ONLY where it genuinely helps readability. Don't over-format a short note. Do NOT start the body with the title as a heading — the title is separate; begin with the content.
- Title: if it is missing or generic ("Untitled note"), generate a short, specific title (3-6 words, no trailing punctuation). Otherwise keep or lightly refine it.
- If "Extract tasks" is true, pull clear to-dos into "tasks" and remove them from the body; otherwise "tasks": [].
Respond with ONLY this JSON: {"title":string,"body":string,"tasks":[{"title":string,"due":string|null,"dueHint":string|null}]}`;

/** On-demand reformat of a shelf note's raw prose into structured Markdown (+ optional tasks). */
export async function shapeNote(opts: {
  title: string;
  body: string;
  apiKey: string;
  baseUrl: string;
  model?: string;
  extractTasks: boolean;
}): Promise<{ title: string; body: string; tasks: HarkTask[] }> {
  const { title, body, apiKey, baseUrl, model = DEFAULT_MODEL, extractTasks } = opts;
  const unchanged = { title, body, tasks: [] as HarkTask[] };
  if (!apiKey || !body.trim()) return unchanged;

  const today = new Date().toISOString().split('T')[0];
  const user = `Today: ${today}\nExtract tasks: ${extractTasks}\nTitle: ${title || '(none)'}\nNote:\n"""\n${body}\n"""`;

  try {
    const res = await fetch(chatUrl(baseUrl), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${apiKey}` },
      body: JSON.stringify({
        model: model || DEFAULT_MODEL,
        messages: [
          { role: 'system', content: SHAPE_SYSTEM },
          { role: 'user', content: user },
        ],
        response_format: { type: 'json_object' },
        temperature: 0.2,
      }),
    });
    if (!res.ok) throw new Error(`AI ${res.status}`);
    const data = await res.json();
    const raw = JSON.parse(data.choices?.[0]?.message?.content || '{}');
    return {
      title: normStr(raw?.title) ?? unchanged.title,
      body: typeof raw?.body === 'string' && raw.body.trim() ? raw.body : body,
      tasks:
        extractTasks && Array.isArray(raw?.tasks)
          ? raw.tasks
              .filter((t: any) => t && typeof t.title === 'string' && t.title.trim())
              .map((t: any) => ({ title: t.title.trim(), due: normDue(t.due), dueHint: normStr(t.dueHint) }))
          : [],
    };
  } catch (e) {
    return unchanged;
  }
}

export async function recallQuery(
  query: string,
  notesContext: string,
  apiKey: string,
  baseUrl: string,
  model: string = DEFAULT_MODEL
): Promise<string> {
  const systemPrompt = `You are Hark Recall, a memory assistant. Answer the user's question directly using ONLY their notes provided below. Be concise, direct, and thoughtful. Cite note titles where appropriate.

USER NOTES:
${notesContext}`;

  const res = await fetch(chatUrl(baseUrl), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${apiKey}` },
    body: JSON.stringify({
      model: model || DEFAULT_MODEL,
      messages: [
        { role: 'system', content: systemPrompt },
        { role: 'user', content: query },
      ],
      temperature: 0.3,
    }),
  });

  if (!res.ok) {
    const err = await res.text();
    throw new Error(`Recall failed (${res.status}): ${err}`);
  }
  const data = await res.json();
  return data.choices?.[0]?.message?.content || 'No relevant notes found.';
}
