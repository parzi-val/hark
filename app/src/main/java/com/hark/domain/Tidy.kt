package com.hark.domain

import java.time.Instant

/** The structured result of tidying a raw transcript: one note + any tasks inside it. */
data class TidyResult(
    val title: String,
    val note: String,
    val tasks: List<TidyTask>,
)

data class TidyTask(
    val title: String,
    val due: Instant? = null,
    val dueHint: String? = null,
)
