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
        
        StreamWidget().updateAll(context)
    }

    companion object {
        val TaskIdKey = ActionParameters.Key<Long>("task_id")
        val DoneKey = ActionParameters.Key<Boolean>("done")
    }
}
