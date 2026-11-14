package io.rbware.zamboni

import io.rbware.zamboni.data.model.Game
import io.rbware.zamboni.data.model.GameStatus
import io.rbware.zamboni.data.model.League
import io.rbware.zamboni.data.model.Team
import io.rbware.zamboni.data.preferences.ScoresFilter
import io.rbware.zamboni.ui.scores.GameSection
import io.rbware.zamboni.ui.scores.buildGameSections
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class GameSectionsTest {
    private val nhl = League("nhl", "NHL", "National Hockey League")
    private val ahl = League("ahl", "AHL", "American Hockey League")
    private val leagues = listOf(nhl, ahl)

    private fun team(id: String, league: League) = Team(id, id, id.uppercase(), league)

    private fun game(id: String, league: League, away: String, home: String, hour: Long) = Game(
        id = id,
        league = league,
        startTime = Instant.EPOCH.plusSeconds(hour * 3600),
        status = GameStatus.Scheduled,
        away = team(away, league),
        home = team(home, league),
    )

    // AHL game listed first and earliest, to check league order wins over start time.
    private val games = listOf(
        game("a1", ahl, "her", "pro", hour = 1),
        game("n2", nhl, "bos", "tor", hour = 3),
        game("n1", nhl, "edm", "cgy", hour = 2),
        game("a2", ahl, "roc", "syr", hour = 4),
    )

    private fun List<GameSection>.ids() = map { section -> section.kind.key to section.games.map { it.id } }

    @Test
    fun noFilter_groupsByLeagueInLeagueOrder_sortedByStartTime() {
        val sections = buildGameSections(games, leagues, emptySet(), ScoresFilter(favoritesFirst = false))
        assertEquals(
            listOf("league-nhl" to listOf("n1", "n2"), "league-ahl" to listOf("a1", "a2")),
            sections.ids(),
        )
    }

    @Test
    fun favoritesFirst_pullsFavoriteGamesIntoLeadingSection() {
        val sections = buildGameSections(games, leagues, setOf("tor", "syr"), ScoresFilter(favoritesFirst = true))
        assertEquals(
            listOf(
                "favorites" to listOf("n2", "a2"),
                "league-nhl" to listOf("n1"),
                "league-ahl" to listOf("a1"),
            ),
            sections.ids(),
        )
    }

    @Test
    fun favoritesFirst_withNoFavoriteGames_hasNoFavoritesSection() {
        val sections = buildGameSections(games, leagues, setOf("nobody"), ScoresFilter(favoritesFirst = true))
        assertTrue(sections.none { it.kind == GameSection.Kind.Favorites })
    }

    @Test
    fun favoritesOnly_keepsOnlyFavoriteGames_groupedByLeague() {
        val sections = buildGameSections(
            games, leagues, setOf("tor", "syr"),
            ScoresFilter(favoritesFirst = true, favoritesOnly = true),
        )
        assertEquals(
            listOf("league-nhl" to listOf("n2"), "league-ahl" to listOf("a2")),
            sections.ids(),
        )
    }

    @Test
    fun leagueFilter_dropsOtherLeagues() {
        val sections = buildGameSections(games, leagues, setOf("syr"), ScoresFilter(leagueIds = setOf("nhl")))
        assertEquals(listOf("league-nhl" to listOf("n1", "n2")), sections.ids())
    }

    @Test
    fun favoritesOnly_withNoFavorites_isEmpty() {
        val sections = buildGameSections(games, leagues, emptySet(), ScoresFilter(favoritesOnly = true))
        assertTrue(sections.isEmpty())
    }
}
