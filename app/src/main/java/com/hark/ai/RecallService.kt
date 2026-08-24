package com.hark.ai

import com.hark.data.local.NoteEntity

/** Answers a recall query in one sentence, grounded only in the notes it's given. */
class RecallService(private val client: OpenAiClient) {

    suspend fun answer(query: String, notes: List<NoteEntity>): String {
        if (notes.isEmpty()) return "I don't have anything on that yet."
        val context = notes.take(6).joinToString("\n\n") { note ->
            buildString {
                append("# ").append(note.title).append('\n').append(note.body)
                note.heardAs?.let { append("\n(heard: ").append(it).append(')') }
            }
        }
        val system = "You answer the user's question in ONE concise sentence, using only the notes provided. " +
            "If the notes don't contain the answer, say you don't have anything on it. No preamble."
        return try {
            client.completeText(system, "Notes:\n$context\n\nQuestion: $query").trim()
        } catch (e: Exception) {
            "Couldn't reach the model — showing matches below."
        }
    }
}
