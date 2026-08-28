package com.hark.sync

import android.content.Context
import com.hark.ai.SettingsStore
import com.hark.ai.ThemeMode
import com.hark.data.local.HarkDatabase
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.io.IOException

/**
 * One sync cycle against the user's Drive appDataFolder, plus the settings/config file and
 * the per-device flags. Mirrors web/src/sync/sync.ts. No backend.
 */
class SyncManager(
    private val appContext: Context,
    db: HarkDatabase,
    private val settingsStore: SettingsStore,
) {
    private val prefs = appContext.getSharedPreferences("hark_sync", Context.MODE_PRIVATE)
    private val local = SyncLocal(db)
    private val http = OkHttpClient()
    private val drive = DriveClient(http) {
        GoogleAuth.silentToken(appContext) ?: throw IOException("Google sign-in required")
    }
    private val mutex = Mutex()

    var isEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(v) = prefs.edit().putBoolean(KEY_ENABLED, v).apply()

    var isApiKeySynced: Boolean
        get() = prefs.getBoolean(KEY_OPTIN, false)
        set(v) = prefs.edit().putBoolean(KEY_OPTIN, v).apply()

    /** Give pre-sync rows a stable uid (one-time, on startup). */
    suspend fun backfillUids() = local.backfillUids()

    /** Full cycle: pull remote → merge → apply locally → push merged. Reentrancy-guarded. */
    suspend fun syncNow() = mutex.withLock {
        val localSnap = local.export()
        val remoteSnap = parseSnapshot(drive.read(DATA_FILE))
        val merged = mergeSnapshots(localSnap, remoteSnap)
        local.apply(merged)
        drive.write(DATA_FILE, merged.toJson())
    }

    /** Push local settings (API key included only when opted in). Call after the user saves. */
    suspend fun pushSettings() {
        val s = settingsStore.settings.value
        val cfg = JSONObject()
            .put("baseUrl", s.baseUrl)
            .put("model", s.model)
            .put("themeMode", s.themeMode.name)
        if (isApiKeySynced) cfg.put("apiKey", s.apiKey)
        drive.write(CONFIG_FILE, cfg.toString())
    }

    /** On a fresh (unconfigured) device, pull config so it arrives set up. */
    suspend fun pullSettingsIfFresh() {
        val cur = settingsStore.settings.value
        if (cur.apiKey.isNotBlank()) return
        val text = drive.read(CONFIG_FILE) ?: return
        val o = try {
            JSONObject(text)
        } catch (e: Exception) {
            return
        }
        val theme = runCatching { ThemeMode.valueOf(o.optString("themeMode", cur.themeMode.name)) }
            .getOrDefault(cur.themeMode)
        settingsStore.update(
            cur.copy(
                baseUrl = o.optString("baseUrl", cur.baseUrl).ifBlank { cur.baseUrl },
                model = o.optString("model", cur.model).ifBlank { cur.model },
                themeMode = theme,
                apiKey = if (isApiKeySynced) o.optString("apiKey", cur.apiKey) else cur.apiKey,
            ),
        )
    }

    /** After an interactive sign-in completes: mark enabled, bootstrap config, first sync. */
    suspend fun onSignedIn() {
        isEnabled = true
        pullSettingsIfFresh()
        syncNow()
    }

    fun signOut() {
        isEnabled = false
    }

    private companion object {
        const val DATA_FILE = "hark.json"
        const val CONFIG_FILE = "config.json"
        const val KEY_ENABLED = "enabled"
        const val KEY_OPTIN = "sync_api_key"
    }
}
