package io.rbware.zamboni.ui.scores

import io.rbware.zamboni.data.model.Game
import io.rbware.zamboni.data.model.League
import io.rbware.zamboni.data.preferences.ScoresFilter

/** A titled group of games in the Scores list. */
data class GameSection(val kind: Kind, val games: List<Game>) {
    sealed interface Kind {
        val key: String

        /** Games involving at least one favorite team, shown at the top. */
        data object Favorites : Kind {
            override val key = "favorites"
        }

        data class ByLeague(val league: League) : Kind {
            override val key = "league-${league.id}"
        }
    }
}

/**
 * Applies the Scores filter to [games] and groups the result for display.
 *
 * - Games outside [ScoresFilter.leagueIds] are dropped (an empty set means all leagues).
 * - With [ScoresFilter.favoritesOnly], only games involving a favorite team remain.
 * - With [ScoresFilter.favoritesFirst] (and not favorites-only), games involving a favorite team
 *   are pulled into a leading [GameSection.Kind.Favorites] section.
 * - Everything else is grouped by league in [leagueOrder], then by start time.
 */
fun buildGameSections(
    games: List<Game>,
    leagueOrder: List<League>,
    favoriteTeamIds: Set<String>,
    filter: ScoresFilter,
): List<GameSection> {
    val visible = games
        .filter { filter.leagueIds.isEmpty() || it.league.id in filter.leagueIds }
        .filter { !filter.favoritesOnly || it.involvesAny(favoriteTeamIds) }
        .sortedWith(compareBy({ it.startTime }, { it.id }))

    val (favorites, rest) = if (filter.favoritesFirst && !filter.favoritesOnly) {
        visible.partition { it.involvesAny(favoriteTeamIds) }
    } else {
        emptyList<Game>() to visible
    }

    val leagueRank = leagueOrder.withIndex().associate { (index, league) -> league.id to index }
    val byLeague = rest
        .groupBy { it.league }
        .entries
        .sortedBy { leagueRank[it.key.id] ?: Int.MAX_VALUE }
        .map { (league, leagueGames) -> GameSection(GameSection.Kind.ByLeague(league), leagueGames) }

    return buildList {
        if (favorites.isNotEmpty()) add(GameSection(GameSection.Kind.Favorites, favorites))
        addAll(byLeague)
    }
}
