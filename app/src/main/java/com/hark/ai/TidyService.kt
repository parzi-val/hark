package com.hark.ai

import com.hark.domain.TidyResult
import com.hark.domain.TidyTask
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/** Turns a raw transcript into a clean note + extracted tasks via the OpenAI-compatible client. */
class TidyService(private val client: OpenAiClient) {

    suspend fun tidy(transcript: String, today: LocalDate): TidyResult {
        val dow = today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
        val user = "Today is $today ($dow).\nTranscript:\n$transcript"
        return try {
            parse(client.completeJson(SYSTEM_PROMPT, user), transcript)
        } catch (e: Exception) {
            // Never lose the capture: fall back to a plain note holding the raw words.
            TidyResult(
                title = transcript.lineSequence().firstOrNull()?.take(48)?.ifBlank { null }
                    ?: "Untitled note",
                note = transcript,
                tasks = emptyList(),
            )
        }
    }

    private fun parse(json: String, rawFallback: String): TidyResult {
        val o = JSONObject(json)
        val title = o.stringOrNull("title") ?: rawFallback.take(48).ifBlank { "Untitled note" }
        val note = o.stringOrNull("note") ?: rawFallback
        val arr = o.optJSONArray("tasks")
        val tasks = buildList {
            if (arr != null) for (i in 0 until arr.length()) {
                val t = arr.optJSONObject(i) ?: continue
                val tTitle = t.stringOrNull("title") ?: continue
                add(
                    TidyTask(
                        title = tTitle,
                        due = parseDate(t.stringOrNull("due")),
                        dueHint = t.stringOrNull("dueHint"),
                    )
                )
            }
        }
        return TidyResult(title = title, note = note, tasks = tasks)
    }

    // org.json's optString yields the literal "null" for a JSON null — treat that as absent.
    private fun JSONObject.stringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).trim().takeIf { it.isNotEmpty() && it != "null" }

    private fun parseDate(value: String?): Instant? {
        if (value.isNullOrBlank() || value == "null") return null
        return try {
            LocalDate.parse(value).atStartOfDay(ZoneId.systemDefault()).toInstant()
        } catch (e: Exception) {
            null
        }
    }

    private companion object {
        val SYSTEM_PROMPT = """
            You are Hark's note tidier. Turn a raw spoken transcript into a clean note plus any
            tasks it contains. Return ONLY a JSON object (no prose, no markdown) with this shape:
            {"title": string, "note": string, "tasks": [{"title": string, "due": string|null, "dueHint": string|null}]}

            Rules:
            - title: short and specific, max ~6 words, no trailing punctuation.
            - note: the transcript cleaned into readable prose. Remove filler and false starts,
              keep the meaning and the speaker's voice. Never invent facts. If nothing beyond the
              tasks is worth keeping, write a one-line summary.
            - tasks: the actionable to-dos the speaker intends. Each title is imperative and concise.
            - due: resolve relative dates (tomorrow, Thursday, next week) to an absolute YYYY-MM-DD
              using the provided today's date; null if no date is implied.
            - dueHint: the exact words that implied the date (e.g. "tomorrow"), else null.
            - Use an empty array when there are no tasks.
            Return strictly valid JSON.
        """.trimIndent()
    }
}
