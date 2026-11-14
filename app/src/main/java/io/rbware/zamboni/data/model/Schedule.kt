package io.rbware.zamboni.data.model

import java.time.LocalDate
import kotlin.time.Duration

/** All games for one game day. */
data class Schedule(
    val date: LocalDate,
    val games: List<Game>,
    /** How soon the source suggests checking again, or null if it gave no hint. */
    val suggestedRefresh: Duration? = null,
)
