package com.hark.di

import android.content.Context
import com.hark.ai.HarkService
import com.hark.ai.OpenAiClient
import com.hark.ai.SettingsStore
import com.hark.ai.TidyService
import com.hark.data.local.HarkDatabase
import com.hark.data.repo.HarkRepository
import com.hark.speech.AudioRecorder
import com.hark.sync.SyncManager

import androidx.glance.appwidget.updateAll
import com.hark.widget.StreamWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Manual dependency container — created once in [com.hark.HarkApp]. Hilt is deliberately
 * deferred for M1; at this size a hand-wired container is simpler and has no codegen risk.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    private val database = HarkDatabase.get(appContext)

    val settingsStore = SettingsStore(
        context = appContext,
        onWidgetSettingsChanged = {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    StreamWidget().updateAll(appContext)
                } catch (_: Exception) {
                    // Ignore if no widget active
                }
            }
        },
    )

    val syncManager = SyncManager(appContext, database, settingsStore)

    val repository = HarkRepository(
        noteDao = database.noteDao(),
        taskDao = database.taskDao(),
        onChanged = {
            try {
                StreamWidget().updateAll(appContext)
            } catch (_: Exception) {
                // Ignore if no widget active on launcher
            }
            // ponytail: debounced auto-sync after any database write
            syncManager.scheduleSync(1000)
        },
        onTaskScheduled = { taskId, title, dueAt ->
            com.hark.notifications.ReminderScheduler.schedule(appContext, taskId, title, dueAt)
        },
        onTaskCancelled = { taskId ->
            com.hark.notifications.ReminderScheduler.cancel(appContext, taskId)
        },
    )

    val openAiClient = OpenAiClient(settingsProvider = { settingsStore.settings.value })

    val harkService = HarkService(openAiClient, settingsProvider = { settingsStore.settings.value })

    val tidyService = TidyService(openAiClient)

    val lexiconRepository = com.hark.data.repo.LexiconRepository(appContext)

    init {
        CoroutineScope(Dispatchers.IO).launch {
            repository.seedStarterNoteIfEmpty()
            syncManager.backfillUids()
            // Auto-sync on launch if the user has turned it on. Silent — a lapsed grant
            // just no-ops until they sign in again from Settings.
            if (syncManager.isEnabled) {
                try {
                    syncManager.syncNow()
                } catch (_: Exception) {
                }
            }
        }
    }

    /** A fresh recorder per capture session. */
    fun newAudioRecorder(): AudioRecorder = AudioRecorder(appContext)
}
