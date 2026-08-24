package com.hark.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Minimal OpenAI-compatible chat client. One blocking POST to `{baseUrl}/chat/completions`,
 * moved off the main thread. [settingsProvider] is read per-call so Settings changes take
 * effect immediately.
 */
class OpenAiClient(private val settingsProvider: () -> AiSettings) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(75, TimeUnit.SECONDS)
        .build()

    /** Sends a system+user pair asking for a JSON object; returns the message content string. */
    suspend fun completeJson(system: String, user: String): String = withContext(Dispatchers.IO) {
        val s = settingsProvider()
        require(s.isConfigured) { "No API key set. Add one in Settings." }

        val payload = JSONObject()
            .put("model", s.model)
            .put("temperature", 0.2)
            .put("response_format", JSONObject().put("type", "json_object"))
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", system))
                    .put(JSONObject().put("role", "user").put("content", user)),
            )

        val request = Request.Builder()
            .url(s.baseUrl.trimEnd('/') + "/chat/completions")
            .addHeader("Authorization", "Bearer ${s.apiKey}")
            .post(payload.toString().toRequestBody(JSON_MEDIA))
            .build()

        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            check(response.isSuccessful) {
                "AI request failed (HTTP ${response.code}): ${body.take(300)}"
            }
            JSONObject(body)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
        }
    }

    /** A plain-text chat completion (no JSON mode) — used for Recall's one-sentence answer. */
    suspend fun completeText(system: String, user: String): String = withContext(Dispatchers.IO) {
        val s = settingsProvider()
        require(s.isConfigured) { "No API key set. Add one in Settings." }

        val payload = JSONObject()
            .put("model", s.model)
            .put("temperature", 0.3)
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", system))
                    .put(JSONObject().put("role", "user").put("content", user)),
            )

        val request = Request.Builder()
            .url(s.baseUrl.trimEnd('/') + "/chat/completions")
            .addHeader("Authorization", "Bearer ${s.apiKey}")
            .post(payload.toString().toRequestBody(JSON_MEDIA))
            .build()

        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            check(response.isSuccessful) { "AI request failed (HTTP ${response.code}): ${body.take(300)}" }
            JSONObject(body).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
        }
    }

    /** Transcribes an audio file via the OpenAI-compatible `/audio/transcriptions` endpoint (Groq Whisper). */
    suspend fun transcribe(file: File): String = withContext(Dispatchers.IO) {
        val s = settingsProvider()
        require(s.isConfigured) { "No API key set. Add one in Settings." }

        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model", STT_MODEL)
            .addFormDataPart("response_format", "text")
            .addFormDataPart("file", file.name, file.asRequestBody("audio/m4a".toMediaType()))
            .build()

        val request = Request.Builder()
            .url(s.baseUrl.trimEnd('/') + "/audio/transcriptions")
            .addHeader("Authorization", "Bearer ${s.apiKey}")
            .post(body)
            .build()

        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            check(response.isSuccessful) { "Transcription failed (HTTP ${response.code}): ${text.take(300)}" }
            text.trim()
        }
    }

    private companion object {
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        // ponytail: hardcoded Groq STT model; add a Settings field if a non-Groq endpoint needs a different id.
        const val STT_MODEL = "whisper-large-v3-turbo"
    }
}
