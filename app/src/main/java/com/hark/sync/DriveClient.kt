package com.hark.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

private const val DRIVE = "https://www.googleapis.com/drive/v3"
private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"
private val JSON = "application/json".toMediaType()

/** Reads/writes text files in the app's private Drive appDataFolder. [tokenProvider] returns
 *  a fresh OAuth access token (throws if sign-in is required). */
class DriveClient(
    private val client: OkHttpClient,
    private val tokenProvider: suspend () -> String,
) {
    private suspend fun run(req: Request): okhttp3.Response =
        withContext(Dispatchers.IO) { client.newCall(req).execute() }

    private suspend fun auth(): String = "Bearer " + tokenProvider()

    private suspend fun findFileId(name: String): String? {
        val q = URLEncoder.encode("name='$name'", "UTF-8")
        val req = Request.Builder()
            .url("$DRIVE/files?spaces=appDataFolder&q=$q&fields=files(id,name)")
            .header("Authorization", auth())
            .build()
        run(req).use { res ->
            if (!res.isSuccessful) throw IOException("Drive list failed: ${res.code}")
            val body = res.body?.string() ?: return null
            val files = JSONObject(body).optJSONArray("files") ?: return null
            return if (files.length() > 0) files.getJSONObject(0).getString("id") else null
        }
    }

    /** File text from appDataFolder, or null if it doesn't exist yet. */
    suspend fun read(name: String): String? {
        val id = findFileId(name) ?: return null
        val req = Request.Builder()
            .url("$DRIVE/files/$id?alt=media")
            .header("Authorization", auth())
            .build()
        run(req).use { res ->
            if (!res.isSuccessful) throw IOException("Drive read failed: ${res.code}")
            return res.body?.string()
        }
    }

    /** Create or overwrite a text file in appDataFolder. */
    suspend fun write(name: String, content: String) {
        val id = findFileId(name)
        if (id != null) {
            val req = Request.Builder()
                .url("$UPLOAD/files/$id?uploadType=media")
                .header("Authorization", auth())
                .patch(content.toRequestBody(JSON))
                .build()
            run(req).use { res -> if (!res.isSuccessful) throw IOException("Drive update failed: ${res.code}") }
            return
        }
        val boundary = "hark" + System.currentTimeMillis()
        val meta = JSONObject()
            .put("name", name)
            .put("parents", JSONArray().put("appDataFolder"))
            .toString()
        val multipart = buildString {
            append("--").append(boundary).append("\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append(meta).append("\r\n")
            append("--").append(boundary).append("\r\n")
            append("Content-Type: application/json\r\n\r\n")
            append(content).append("\r\n")
            append("--").append(boundary).append("--")
        }
        val req = Request.Builder()
            .url("$UPLOAD/files?uploadType=multipart&fields=id")
            .header("Authorization", auth())
            .post(multipart.toRequestBody("multipart/related; boundary=$boundary".toMediaType()))
            .build()
        run(req).use { res -> if (!res.isSuccessful) throw IOException("Drive create failed: ${res.code}") }
    }
}
