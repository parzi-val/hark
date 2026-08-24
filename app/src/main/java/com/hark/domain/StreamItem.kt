package com.hark.domain

import com.hark.data.local.NoteEntity
import com.hark.data.local.TaskEntity
import java.time.Instant

/** One entry in the single stream — either a task or a note. */
sealed interface StreamItem {
    val id: Long
    val createdAt: Instant

    data class Task(val task: TaskEntity) : StreamItem {
        override val id get() = task.id
        override val createdAt get() = task.createdAt
    }

    data class Note(val note: NoteEntity, val tasks: List<TaskEntity>) : StreamItem {
        override val id get() = note.id
        override val createdAt get() = note.createdAt
        val taskCount: Int get() = tasks.size
    }
}
