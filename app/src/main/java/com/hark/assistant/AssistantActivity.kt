package com.hark.assistant

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.hark.HarkApp
import com.hark.MainActivity
import com.hark.data.local.Source
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Trampoline activity for handling voice notes and actions from Google Assistant,
 * Android Share Sheet (SEND), and Android text selection (PROCESS_TEXT).
 */
class AssistantActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = applicationContext as HarkApp
        val rawText = extractText(intent)

        if (rawText.isNullOrBlank()) {
            // No text provided — just open Hark to compose or talk
            val mainIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(mainIntent)
            finish()
            return
        }

        // Process incoming text in background
        val settings = app.container.settingsStore.settings.value
        val repo = app.container.repository
        val tidy = app.container.tidyService

        Toast.makeText(applicationContext, "Hark: Capturing thought…", Toast.LENGTH_SHORT).show()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (settings.isConfigured) {
                    val result = tidy.tidy(rawText.trim(), LocalDate.now())
                    repo.saveTidied(result, heardAs = rawText.trim(), source = Source.TYPED)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            applicationContext,
                            "Hark: Saved \"${result.title}\"${if (result.tasks.isNotEmpty()) " (${result.tasks.size} tasks)" else ""}",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                } else {
                    val lines = rawText.trim().lines()
                    val title = lines.firstOrNull().orEmpty().take(50).ifBlank { "Assistant note" }
                    val body = if (lines.size > 1) lines.drop(1).joinToString("\n") else rawText.trim()
                    repo.saveTypedNote(title, body)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(applicationContext, "Hark: Saved note", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                // Fallback to plain save if AI network fails
                val lines = rawText.trim().lines()
                val title = lines.firstOrNull().orEmpty().take(50).ifBlank { "Assistant note" }
                val body = rawText.trim()
                repo.saveTypedNote(title, body)
                withContext(Dispatchers.Main) {
                    Toast.makeText(applicationContext, "Hark: Saved note", Toast.LENGTH_SHORT).show()
                }
            }
        }

        finish()
    }

    private fun extractText(intent: Intent?): String? {
        if (intent == null) return null

        // 1. Android PROCESS_TEXT (text highlighted in any app)
        val processText = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
        if (!processText.isNullOrBlank()) return processText

        // 2. Android standard EXTRA_TEXT / EXTRA_SUBJECT
        val extraText = intent.getStringExtra(Intent.EXTRA_TEXT)
        val extraSubject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
        if (!extraText.isNullOrBlank()) {
            return if (!extraSubject.isNullOrBlank() && extraSubject != extraText) {
                "$extraSubject\n\n$extraText"
            } else {
                extraText
            }
        }

        // 3. Google Assistant BII params
        val noteText = intent.getStringExtra("noteText") ?: intent.data?.getQueryParameter("noteText")
        if (!noteText.isNullOrBlank()) return noteText

        val taskName = intent.getStringExtra("taskName") ?: intent.data?.getQueryParameter("taskName")
        if (!taskName.isNullOrBlank()) return taskName

        return null
    }
}
