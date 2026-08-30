package com.hark.sync

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.hark.ai.SettingsStore
import com.hark.ai.ThemeMode
import com.hark.data.local.HarkDatabase
import com.hark.widget.StreamWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
    // Explicit timeouts so a stalled Drive call fails fast instead of holding the mutex forever.
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .callTimeout(25, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    private val drive = DriveClient(
        client = http,
        tokenProvider = { GoogleAuth.silentToken(appContext) ?: throw IOException("Google sign-in required") },
        onUnauthorized = { token -> GoogleAuth.clearToken(appContext, token) },
    )
    private val mutex = Mutex()
    private var syncJob: Job? = null
    private val syncScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // True only while the post-sign-in FIRST sync runs, so the app can show the Hilbert loader
    // on entry instead of flashing the starter note. The foreground poller never sets this.
    private val _initialSyncing = MutableStateFlow(false)
    val initialSyncing: StateFlow<Boolean> = _initialSyncing.asStateFlow()

    var isEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(v) = prefs.edit().putBoolean(KEY_ENABLED, v).apply()

    // On by default (your own key, your own private appData folder); turn it off and it sticks.
    var isApiKeySynced: Boolean
        get() = prefs.getBoolean(KEY_OPTIN, true)
        set(v) = prefs.edit().putBoolean(KEY_OPTIN, v).apply()

    /** Give pre-sync rows a stable uid (one-time, on startup). */
    suspend fun backfillUids() = local.backfillUids()

    private fun isStarterNote(title: String): Boolean {
        val t = title.trim().lowercase()
        return t == "welcome to hark" || t == "welcome to hark."
    }

    private fun isStarterTask(title: String): Boolean {
        val t = title.trim().lowercase()
        return t.startsWith("tap talk or hold space") ||
                t.startsWith("configure your groq") ||
                t.startsWith("set your groq") ||
                t.startsWith("add the hark") ||
                t.startsWith("tap talk and speak")
    }

    /** Full cycle: pull remote → merge → apply locally → push merged. Reentrancy-guarded. */
    suspend fun syncNow() = mutex.withLock {
        val remoteSnap = parseSnapshot(drive.read(DATA_FILE))

        val hasRealRemoteData = remoteSnap.notes.any { !isStarterNote(it.title) && !it.deleted } ||
                remoteSnap.tasks.any { !isStarterTask(it.title) && !it.deleted }

        if (hasRealRemoteData) {
            local.purgeStarterIfRemotePresent(remoteSnap)
        }

        val localSnap = local.export()

        val cleanedRemote = if (hasRealRemoteData) {
            remoteSnap.copy(
                notes = remoteSnap.notes.filter { !isStarterNote(it.title) },
                tasks = remoteSnap.tasks.filter { !isStarterTask(it.title) },
            )
        } else remoteSnap

        val cleanedLocal = if (hasRealRemoteData) {
            localSnap.copy(
                notes = localSnap.notes.filter { !isStarterNote(it.title) },
                tasks = localSnap.tasks.filter { !isStarterTask(it.title) },
            )
        } else localSnap

        val merged = mergeSnapshots(cleanedLocal, cleanedRemote)
        val changed = local.apply(merged)
        drive.write(DATA_FILE, merged.toJson())
        catchUpApiKey()
        // Sync writes go through the DAOs directly (not the repo), so refresh the widget here —
        // otherwise it only updated on app-side edits, never on data pulled from Drive.
        if (changed) runCatching { StreamWidget().updateAll(appContext) }
    }

    /**
     * Debounced auto-sync (coalesces rapid keystrokes/edits into one push).
     * ponytail: coroutine job cancellation handles debouncing cleanly without third-party libraries.
     */
    fun scheduleSync(delayMillis: Long = 800) {
        if (!isEnabled) return
        syncJob?.cancel()
        syncJob = syncScope.launch {
            delay(delayMillis)
            runCatching { syncNow() }
        }
    }

    /**
     * Poll every 3s while in the foreground. Silent — errors or offline state cleanly no-op.
     * ponytail: simple loop tied to lifecycle scope fits within <0.1% of Drive rate limit.
     */
    suspend fun runForegroundPolling() {
        while (currentCoroutineContext().isActive) {
            if (isEnabled) runCatching { syncNow() }
            delay(3000)
        }
    }

    /** Push local settings (API key included only when opted in). Call after the user saves. */
    suspend fun pushSettings() {
        val s = settingsStore.settings.value
        val cfg = JSONObject()
            .put("baseUrl", s.baseUrl)
            .put("model", s.model)
            .put("userName", s.userName)
            .put("themeMode", s.themeMode.name)
        if (isApiKeySynced && s.apiKey.isNotBlank()) cfg.put("apiKey", s.apiKey)
        drive.write(CONFIG_FILE, cfg.toString())
    }

    /** Adopt the synced API key on a device that has none yet (fills a blank only, so it never
     *  overwrites a device's own key). Runs each sync so a later-added key propagates. */
    private suspend fun catchUpApiKey() {
        if (settingsStore.settings.value.apiKey.isNotBlank()) return
        val text = drive.read(CONFIG_FILE) ?: return
        val key = try {
            JSONObject(text).optString("apiKey", "")
        } catch (e: Exception) {
            ""
        }
        if (key.isNotBlank()) {
            isApiKeySynced = true
            settingsStore.update(settingsStore.settings.value.copy(apiKey = key))
        }
    }

    /** On a fresh (unconfigured) device, pull config so it arrives set up. */
    suspend fun pullSettingsIfFresh() {
        val text = drive.read(CONFIG_FILE) ?: return
        val o = try {
            JSONObject(text)
        } catch (e: Exception) {
            return
        }
        // Snapshot AFTER the slow Drive read: onboarding may finish (hasCompletedOnboarding=true)
        // while we wait, and a pre-read snapshot would clobber it back to false → onboarding re-shows.
        val cur = settingsStore.settings.value
        val theme = runCatching { ThemeMode.valueOf(o.optString("themeMode", cur.themeMode.name)) }
            .getOrDefault(cur.themeMode)
        val remoteApiKey = o.optString("apiKey", "")
        if (remoteApiKey.isNotBlank()) {
            isApiKeySynced = true
        }
        settingsStore.update(
            cur.copy(
                baseUrl = o.optString("baseUrl", cur.baseUrl).ifBlank { cur.baseUrl },
                model = o.optString("model", cur.model).ifBlank { cur.model },
                userName = o.optString("userName", cur.userName).ifBlank { cur.userName },
                themeMode = theme,
                apiKey = if (cur.apiKey.isBlank() && remoteApiKey.isNotBlank()) remoteApiKey else cur.apiKey,
            ),
        )
    }

    /** After an interactive sign-in completes: mark enabled, bootstrap config, first sync. */
    suspend fun onSignedIn() {
        isEnabled = true
        pullSettingsIfFresh()
        syncNow()
    }

    // Fire-and-forget variants on the app-lifetime [syncScope]. A screen's rememberCoroutineScope
    // is cancelled the moment the screen leaves composition — which silently killed the onboarding
    // first-sync when Enter Hark was tapped before it finished. These survive that navigation.
    fun onSignedInAsync() {
        syncScope.launch {
            _initialSyncing.value = true
            try {
                onSignedIn()
            } catch (e: Exception) {
                // failure is surfaced at the sign-in call site; just ensure the loader clears
            } finally {
                _initialSyncing.value = false
            }
        }
    }

    fun pushSettingsAsync() {
        syncScope.launch { runCatching { pushSettings() } }
    }

    fun syncNowAsync() {
        syncScope.launch { runCatching { syncNow() } }
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
