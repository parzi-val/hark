package com.hark.notifications

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hark.HarkApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(ReminderScheduler.EXTRA_TASK_ID, -1L)
        if (taskId == -1L) return

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(taskId.toInt())

        val app = context.applicationContext as? HarkApp ?: return

        when (intent.action) {
            ACTION_MARK_DONE -> {
                CoroutineScope(Dispatchers.IO).launch {
                    val now = Instant.now()
                    app.container.repository.setTaskDone(
                        task = com.hark.data.local.TaskEntity(
                            id = taskId,
                            title = "",
                            done = false,
                            createdAt = now,
                            updatedAt = now,
                        ),
                        done = true,
                    )
                }
            }

            ACTION_SNOOZE -> {
                val taskTitle = intent.getStringExtra(ReminderScheduler.EXTRA_TASK_TITLE).orEmpty()
                val newDueAt = Instant.now().plus(Duration.ofHours(1))
                ReminderScheduler.schedule(context, taskId, taskTitle, newDueAt)
            }
        }
    }

    companion object {
        const val ACTION_MARK_DONE = "com.hark.action.MARK_DONE"
        const val ACTION_SNOOZE = "com.hark.action.SNOOZE"
    }
}
