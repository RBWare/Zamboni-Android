package io.rbware.zamboni.data.model

/** A hockey league, e.g. NHL or AHL. [id] is the stable key stored in the Scores league filter. */
data class League(
    val id: String,
    val shortName: String,
    val name: String,
)
