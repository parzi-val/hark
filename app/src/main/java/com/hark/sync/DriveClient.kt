package com.hark.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

private const val DRIVE = "https://www.googleapis.com/drive/v3"
private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"
private val JSON = "application/json".toMediaType()

/** Minimal Drive file metadata for cheap change-detection (skip the download when unchanged). */
data class DriveFile(val id: String, val modifiedTime: String?)

/**
 * Reads/writes text files in the app's private Drive appDataFolder. [tokenProvider] returns an
 * OAuth access token; [onUnauthorized] invalidates a stale one so a 401 can be retried —
 * GoogleAuthUtil caches tokens and will happily hand back one that has already expired.
 */
class DriveClient(
    private val client: OkHttpClient,
    private val tokenProvider: suspend () -> String,
    private val onUnauthorized: suspend (String) -> Unit = {},
) {
    /** Build + run a request with a bearer token; on 401, drop the stale token and retry once. */
    private suspend fun exec(build: (auth: String) -> Request): Response {
        val token = tokenProvider()
        var res = withContext(Dispatchers.IO) { client.newCall(build("Bearer $token")).execute() }
        if (res.code == 401) {
            res.close()
            onUnauthorized(token)
            val fresh = tokenProvider()
            res = withContext(Dispatchers.IO) { client.newCall(build("Bearer $fresh")).execute() }
        }
        return res
    }

    private suspend fun findFileId(name: String): String? {
        val q = URLEncoder.encode("name='$name'", "UTF-8")
        exec { auth ->
            Request.Builder()
                .url("$DRIVE/files?spaces=appDataFolder&q=$q&fields=files(id,name)")
                .header("Authorization", auth)
                .build()
        }.use { res ->
            if (!res.isSuccessful) {
                val err = res.body?.string()
                android.util.Log.e("HarkDrive", "Drive list failed (${res.code}): $err")
                throw IOException("Drive list failed: ${res.code}")
            }
            val body = res.body?.string() ?: return null
            val files = JSONObject(body).optJSONArray("files") ?: return null
            return if (files.length() > 0) files.getJSONObject(0).getString("id") else null
        }
    }

    /** id + modifiedTime for a file (one cheap list call), or null if it doesn't exist. */
    suspend fun stat(name: String): DriveFile? {
        val q = URLEncoder.encode("name='$name'", "UTF-8")
        exec { auth ->
            Request.Builder()
                .url("$DRIVE/files?spaces=appDataFolder&q=$q&fields=files(id,modifiedTime)")
                .header("Authorization", auth)
                .build()
        }.use { res ->
            if (!res.isSuccessful) throw IOException("Drive stat failed: ${res.code}")
            val body = res.body?.string() ?: return null
            val files = JSONObject(body).optJSONArray("files") ?: return null
            if (files.length() == 0) return null
            val f = files.getJSONObject(0)
            return DriveFile(f.getString("id"), f.optString("modifiedTime").ifEmpty { null })
        }
    }

    /** File text by a known id (from [stat]) — skips the extra list [read] would do. */
    suspend fun readById(id: String): String? {
        exec { auth ->
            Request.Builder()
                .url("$DRIVE/files/$id?alt=media")
                .header("Authorization", auth)
                .build()
        }.use { res ->
            if (!res.isSuccessful) throw IOException("Drive read failed: ${res.code}")
            return res.body?.string()
        }
    }

    /** File text from appDataFolder, or null if it doesn't exist yet. */
    suspend fun read(name: String): String? {
        val id = findFileId(name) ?: return null
        exec { auth ->
            Request.Builder()
                .url("$DRIVE/files/$id?alt=media")
                .header("Authorization", auth)
                .build()
        }.use { res ->
            if (!res.isSuccessful) throw IOException("Drive read failed: ${res.code}")
            return res.body?.string()
        }
    }

    /** Create or overwrite a text file in appDataFolder; returns the new modifiedTime (for
     *  change-detection), or null if the response didn't carry one. */
    suspend fun write(name: String, content: String): String? {
        val id = findFileId(name)
        if (id != null) {
            exec { auth ->
                Request.Builder()
                    .url("$UPLOAD/files/$id?uploadType=media&fields=id,modifiedTime")
                    .header("Authorization", auth)
                    .patch(content.toRequestBody(JSON))
                    .build()
            }.use { res ->
                if (!res.isSuccessful) throw IOException("Drive update failed: ${res.code}")
                return res.body?.string()?.let { JSONObject(it).optString("modifiedTime").ifEmpty { null } }
            }
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
        exec { auth ->
            Request.Builder()
                .url("$UPLOAD/files?uploadType=multipart&fields=id,modifiedTime")
                .header("Authorization", auth)
                .post(multipart.toRequestBody("multipart/related; boundary=$boundary".toMediaType()))
                .build()
        }.use { res ->
            if (!res.isSuccessful) throw IOException("Drive create failed: ${res.code}")
            return res.body?.string()?.let { JSONObject(it).optString("modifiedTime").ifEmpty { null } }
        }
    }
}
