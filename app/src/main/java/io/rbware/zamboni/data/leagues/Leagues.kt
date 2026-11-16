package io.rbware.zamboni.data.leagues

import io.rbware.zamboni.data.model.League

/** Leagues the app knows about. IDs are stored in user preferences, so don't change them. */
object Leagues {
    val NHL = League(id = "nhl", shortName = "NHL", name = "National Hockey League")
    val AHL = League(id = "ahl", shortName = "AHL", name = "American Hockey League")
    val SPHL = League(id = "sphl", shortName = "SPHL", name = "Southern Professional Hockey League")
    val NAHL = League(id = "nahl", shortName = "NAHL", name = "North American Hockey League")

    /**
     * Every league, in display order (league picker, scores filter, team lists). A new league
     * goes here, with a scores source in AppContainer.
     */
    val all: List<League> = listOf(NHL, AHL, SPHL, NAHL)

    /** Leagues turned on for new users. */
    val defaultEnabledIds: Set<String> = setOf(NHL.id)

    fun byId(id: String): League? = all.firstOrNull { it.id == id }
}

/**
 * The league of an app-wide team ID ("ahl-bel" -> "ahl"), or null if the prefix isn't a known
 * league. All team IDs are "<league id>-<team>".
 */
fun leagueIdOfTeam(teamId: String): String? = teamId.substringBefore('-', "").takeIf { Leagues.byId(it) != null }
