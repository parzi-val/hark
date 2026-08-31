package com.hark.ai

import com.hark.domain.Action
import com.hark.domain.FocusedNote
import com.hark.domain.HarkAction
import com.hark.domain.HarkTask
import com.hark.domain.NoteRef
import com.hark.domain.ShapeResult
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class HarkService(
    private val client: OpenAiClient,
    private val settingsProvider: () -> AiSettings,
) {

    suspend fun process(
        transcript: String,
        extractTasks: Boolean,
        notes: List<NoteRef>,
        focusedNote: FocusedNote? = null,
        checklistOnly: Boolean = false,
    ): HarkAction {
        val s = settingsProvider()
        if (!s.isConfigured || transcript.isBlank()) {
            return fallbackAction(transcript)
        }
        // Checklist mode always extracts items and carries no prose.
        val extract = extractTasks || checklistOnly

        val today = LocalDate.now()
        val dateStr = today.toString()
        val dow = today.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.US)

        val noteLines = if (notes.isNotEmpty()) {
            notes.joinToString("\n") { n ->
                val taskStr = if (n.taskCount > 0) " · ${n.taskCount} tasks" else ""
                val snipStr = if (n.snippet.isNotBlank()) " — \"${n.snippet}\"" else ""
                "- [id ${n.id}] ${n.title}$taskStr$snipStr"
            }
        } else {
            "(none yet)"
        }

        val focused = if (focusedNote != null) {
            val bodyStr = if (focusedNote.body.isNotBlank()) " — ${focusedNote.body}" else ""
            val taskStr = if (focusedNote.tasks.isNotEmpty()) "\nIts tasks: ${focusedNote.tasks.joinToString("; ")}" else ""
            "[id ${focusedNote.id}] ${focusedNote.title}$bodyStr$taskStr"
        } else {
            "none"
        }

        val user = """
            Today: $dateStr ($dow)
            Extract tasks: $extract
            Focused note: $focused
            Your notes:
            $noteLines
            Transcript:
            \"\"\"
            $transcript
            \"\"\"
        """.trimIndent()

        return try {
            val system = if (checklistOnly) CHECKLIST_SYSTEM else ACTIONS_SYSTEM
            val response = client.completeJson(system, user)
            val json = JSONObject(response)
            val action = normalizeAction(json, transcript, extract)
            if (checklistOnly) action.copy(body = "") else action
        } catch (e: Exception) {
            fallbackAction(transcript)
        }
    }

    suspend fun shape(
        title: String,
        body: String,
        extractTasks: Boolean = false,
    ): ShapeResult {
        val s = settingsProvider()
        require(s.isConfigured) { "Please set your Groq/OpenAI API key in Settings." }
        require(body.isNotBlank()) { "Note body is empty." }

        val today = LocalDate.now().toString()
        val user = "Today: $today\nExtract tasks: $extractTasks\nTitle: ${title.ifBlank { "(none)" }}\nNote:\n\"\"\"\n$body\n\"\"\""

        val response = client.completeJson(SHAPE_SYSTEM, user)
        val json = JSONObject(response)
        val resTitle = normStr(json.optString("title")) ?: title
        val resBody = normStr(json.optString("body")) ?: body
        val tasks = if (extractTasks) {
            parseTasks(json.optJSONArray("tasks"))
        } else {
            emptyList()
        }
        return ShapeResult(title = resTitle, body = resBody, tasks = tasks)
    }

    private fun fallbackAction(transcript: String): HarkAction = HarkAction(
        action = Action.CREATE,
        targetNoteId = null,
        title = transcript.take(40).ifBlank { "Untitled note" },
        body = transcript,
        tasks = emptyList(),
        reason = "fallback",
    )

    private fun normalizeAction(raw: JSONObject, transcript: String, extractTasks: Boolean): HarkAction {
        val actionStr = normStr(raw.optString("action"))
        val action = Action.fromString(actionStr)
        val title = normStr(raw.optString("title"))
        val body = raw.optString("body", "")
        val tasks = if (extractTasks) parseTasks(raw.optJSONArray("tasks")) else emptyList()
        val reason = raw.optString("reason", "")
        val targetNoteId = if (raw.has("targetNoteId") && !raw.isNull("targetNoteId")) {
            raw.optLong("targetNoteId", -1L).takeIf { it > 0 }
        } else {
            null
        }

        // Degrade append/edit with no target to create
        if (action == Action.CREATE || targetNoteId == null) {
            return HarkAction(
                action = Action.CREATE,
                targetNoteId = null,
                title = title ?: transcript.take(40).ifBlank { "Untitled note" },
                body = body.ifBlank { transcript },
                tasks = tasks,
                reason = reason.ifBlank { if (action != Action.CREATE) "no target → create" else "" },
            )
        }

        return HarkAction(
            action = action,
            targetNoteId = targetNoteId,
            title = title,
            body = body,
            tasks = tasks,
            reason = reason,
        )
    }

    private fun parseTasks(array: JSONArray?): List<HarkTask> {
        if (array == null) return emptyList()
        val out = mutableListOf<HarkTask>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val taskTitle = normStr(obj.optString("title")) ?: continue
            val dueStr = normDue(obj.optString("due"))
            val dueHint = normStr(obj.optString("dueHint"))

            val dueInstant = dueStr?.let {
                try {
                    LocalDate.parse(it, DateTimeFormatter.ISO_LOCAL_DATE)
                        .atStartOfDay(ZoneId.systemDefault())
                        .toInstant()
                } catch (_: Exception) {
                    null
                }
            }

            out.add(HarkTask(title = taskTitle, dueAt = dueInstant, dueHint = dueHint))
        }
        return out
    }

    private fun normStr(v: String?): String? {
        if (v == null) return null
        val trimmed = v.trim()
        return if (trimmed.isNotBlank() && trimmed != "null") trimmed else null
    }

    private fun normDue(v: String?): String? {
        val s = normStr(v) ?: return null
        return if (s.matches(Regex("""^\d{4}-\d{2}-\d{2}$"""))) s else null
    }

    companion object {
        private const val ACTIONS_SYSTEM = """You are Hark, a voice-first note assistant. Turn the user's transcript into exactly ONE action on their notes, as a single JSON object.

Choose "action":
- "create": a new, standalone thought → a new note.
- "append": the user is clearly adding to an existing note they name or reference (e.g. "add milk to the grocery list", "in the sourdough note, jot that..."). Pick its id from "Your notes".
- "edit": the user is adding to or refining the Focused note (only when one is provided) → return the FULL note in "body", MERGING the existing note's content with the new input (keep everything already there, integrate the new). When a Focused note is provided, prefer "edit" over "append" so the result is the complete merged note in one step.

How a note is shaped — this matters:
- "body" is PROSE ONLY: context, thoughts, narrative. NEVER put the to-do / checklist items in "body"; the items go in "tasks".
- If a note is essentially just a checklist with no real prose, leave "body" EMPTY (at most a one-line summary of what the list is for — never the items themselves).

Rules:
- Prefer "create". Only "append"/"edit" when you are confident which note is meant. If unsure, "create". Never mutate a note you are not sure about.
- Keep EXACTLY what was said — this is a transcription cleanup, NOT a rewrite or summary. Preserve every distinct point, example, aside, and detail, in the original order. Do NOT summarize, condense, merge, paraphrase away, or drop anything. Remove ONLY filler ("um", "like", "you know"), false starts, and verbatim repetition; fix punctuation, capitalization, and obvious transcription errors; break it into paragraphs. The result should be nearly as long as the transcript. Never invent facts.
- Write it in the speaker's own words and voice (first person), AS the note. Never narrate the recording — no "the transcript…", "the speaker…", "I described…", "this note covers…".
- If "Extract tasks" is true, pull actionable items/to-dos into "tasks" (imperative, concise); resolve relative dates against Today into "due" (YYYY-MM-DD), "dueHint" = the words that implied it. If false, return "tasks": [] and do not split anything into tasks.
- For "append": adding items/things to a list or checklist → put them in "tasks", NOT "body". Use "body" only when the user is genuinely adding narrative prose. Mirror the note's existing task phrasing when obvious (e.g. tasks that start with "Buy ...").
- For "edit"/"append" on a note that already has tasks (shown under "Its tasks"), return ONLY genuinely new tasks in "tasks" — never repeat a task that is already in that list.
- Use Markdown in "body" to make longer or multi-topic notes readable: short "##" section headings, "- " bullet lists, **bold** for key terms, "> " for quotes. Keep short, single-idea notes as plain prose — don't over-format. Do NOT start the body with the title as a heading — the title is stored separately; begin the body with the content itself.
- "title": a short, specific title of 3-6 words, no trailing punctuation. Used only for "create"/"edit"; ignored for "append".
- Respond with ONLY the JSON object. No prose, no code fences.

JSON shape:
{"action":"create|append|edit","targetNoteId":number|null,"title":string|null,"body":string,"tasks":[{"title":string,"due":string|null,"dueHint":string|null}],"reason":string}"""

        private const val CHECKLIST_SYSTEM = """You are Hark. The user is dictating a checklist. Turn the transcript into a list, as a single JSON object.
- Extract EVERY item the user names into "tasks" (imperative/concise; keep their phrasing). Resolve relative dates against Today into "due" (YYYY-MM-DD), "dueHint" = the words that implied it. Never invent items.
- "body" MUST be "" (empty). Never put the items or any prose in "body".
- "action": "append" when the user is adding to a list they name or when a Focused note is provided → use its id (from the Focused note / Your notes). Otherwise "create" a new list.
- "title": for "create", a short specific name for the list, 3-6 words, no trailing punctuation (e.g. "Grocery List", "Packing List"). Ignored for "append".
- Respond with ONLY the JSON object. No prose, no code fences.
JSON shape:
{"action":"create|append","targetNoteId":number|null,"title":string|null,"body":"","tasks":[{"title":string,"due":string|null,"dueHint":string|null}],"reason":string}"""

        private const val SHAPE_SYSTEM = """You are Hark. Shape the user's raw note into clean, readable Markdown. Keep their words, voice, and meaning; never invent anything.
- Keep EXACTLY what the author wrote — a cleanup/formatting pass, NOT a rewrite or summary. Preserve every point, example, and detail in order; do NOT summarize, condense, merge, or drop anything. Remove only filler and verbatim repetition, fix punctuation, and add paragraph breaks / Markdown structure. Keep it about the same length. Write in the author's own words (first person); never narrate.
- Use short "##" headings, "- " bullet lists, and **bold** for key terms ONLY where it genuinely helps readability. Don't over-format a short note. Do NOT start the body with the title as a heading — the title is separate; begin with the content.
- Title: if it is missing or generic ("Untitled note"), generate a short, specific title (3-6 words, no trailing punctuation). Otherwise keep or lightly refine it.
- If "Extract tasks" is true, pull clear to-dos into "tasks" and remove them from the body; otherwise "tasks": [].
Respond with ONLY this JSON: {"title":string,"body":string,"tasks":[{"title":string,"due":string|null,"dueHint":string|null}]}"""
    }
}
