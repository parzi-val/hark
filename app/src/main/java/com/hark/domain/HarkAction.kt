package com.hark.domain

import java.time.Instant

enum class Action {
    CREATE,
    APPEND,
    EDIT;

    companion object {
        fun fromString(s: String?): Action = when (s?.lowercase()?.trim()) {
            "append" -> APPEND
            "edit" -> EDIT
            else -> CREATE
        }
    }
}

data class HarkTask(
    val title: String,
    val dueAt: Instant? = null,
    val dueHint: String? = null,
)

data class HarkAction(
    val action: Action,
    val targetNoteId: Long?,
    val title: String?,
    val body: String,
    val tasks: List<HarkTask>,
    val reason: String,
)

data class NoteRef(
    val id: Long,
    val title: String,
    val snippet: String,
    val taskCount: Int,
)

data class FocusedNote(
    val id: Long,
    val title: String,
    val body: String,
    val tasks: List<String>,
)

data class ShapeResult(
    val title: String,
    val body: String,
    val tasks: List<HarkTask>,
)
