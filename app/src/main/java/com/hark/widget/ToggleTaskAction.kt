package com.hark.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll
import com.hark.data.local.HarkDatabase
import java.time.Instant

class ToggleTaskAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val taskId = parameters[TaskIdKey] ?: return
        val currentDone = parameters[DoneKey] ?: false
        val newDone = !currentDone
        val now = Instant.now()
        
        val db = HarkDatabase.get(context.applicationContext)
        db.taskDao().setDone(
            id = taskId,
            done = newDone,
            doneAt = if (newDone) now else null,
            updatedAt = now
        )

        // Reconcile the parent note's archive (all tasks done → archived), mirroring the repo, so a
        // note finished from the widget leaves the widget and the stream. Set-true-only.
        db.taskDao().getById(taskId)?.sourceNoteId?.let { noteId ->
            val tasks = db.taskDao().getForNote(noteId)
            if (tasks.isNotEmpty() && tasks.all { it.done }) {
                db.noteDao().setArchived(noteId, true, now)
            }
        }

        StreamWidget().updateAll(context)
    }

    companion object {
        val TaskIdKey = ActionParameters.Key<Long>("task_id")
        val DoneKey = ActionParameters.Key<Boolean>("done")
    }
}
