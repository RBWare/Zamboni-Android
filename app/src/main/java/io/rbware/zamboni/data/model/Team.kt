package io.rbware.zamboni.data.model

/**
 * A hockey team. [id] is the stable key stored in user preferences; once the hockeynerd.com API
 * is wired up it should be the API's team identifier.
 */
data class Team(
    val id: String,
    val name: String,
    val abbreviation: String,
)
