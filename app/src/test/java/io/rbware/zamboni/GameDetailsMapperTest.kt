package io.rbware.zamboni

import io.rbware.zamboni.data.model.Decision
import io.rbware.zamboni.data.model.GameDetails
import io.rbware.zamboni.data.model.GameStatus
import io.rbware.zamboni.data.model.GoalStrength
import io.rbware.zamboni.data.model.StatValue
import io.rbware.zamboni.data.model.TeamSide
import io.rbware.zamboni.data.model.TeamStatType
import io.rbware.zamboni.data.placeholder.PlaceholderHockeyData
import io.rbware.zamboni.data.remote.GameDetailResponse
import io.rbware.zamboni.data.remote.HockeyNerdApi
import io.rbware.zamboni.data.scores.apiGameIdOrNull
import io.rbware.zamboni.data.scores.nhlGameId
import io.rbware.zamboni.data.scores.toGameDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

/** Parses real /api/game/{id} responses from src/test/resources. */
class GameDetailsMapperTest {
    private val knownTeams = PlaceholderHockeyData.teams.associateBy { it.id }

    private fun load(name: String): GameDetails {
        val text = checkNotNull(javaClass.classLoader?.getResource(name)).readText()
        return HockeyNerdApi.json.decodeFromString<GameDetailResponse>(text).toGameDetails(knownTeams)
    }

    @Test
    fun finalShootout_mapsHeaderGoalsAndStats() {
        val details = load("game_final_shootout.json")
        val game = details.game
        assertEquals("nhl-2026020311", game.id)
        assertEquals(GameStatus.Final(Decision.SHOOTOUT), game.status)
        assertEquals(3, game.awayScore)
        assertEquals(2, game.homeScore)
        assertEquals("Nashville Predators", game.away.name)
        assertEquals("T-Mobile Arena", game.venue)

        // Shootout attempts aren't listed, only the 4 regulation goals.
        assertEquals(4, details.goals.size)
        val first = details.goals.first()
        assertEquals(TeamSide.AWAY, first.side)
        assertEquals(1, first.period)
        assertEquals("7:56", first.timeInPeriod)
        assertEquals(GoalStrength.EVEN, first.strength)
        assertEquals("Ryan O'Reilly", first.scorer.name)
        assertEquals(8, first.scorer.seasonTotal)
        assertEquals(listOf("S. Stamkos", "M. Bourque"), first.assists.map { it.name })
        assertEquals(1 to 0, first.awayScore to first.homeScore)

        val stats = details.teamStats.associateBy { it.type }
        assertEquals(TeamStatType.entries.toList(), details.teamStats.map { it.type })
        assertEquals(StatValue.Number(33.0), stats.getValue(TeamStatType.SHOTS_ON_GOAL).away)
        assertEquals(StatValue.Number(42.0), stats.getValue(TeamStatType.SHOTS_ON_GOAL).home)
        assertEquals(StatValue.Text("0/2"), stats.getValue(TeamStatType.POWER_PLAY).away)
        assertEquals(StatValue.Number(0.492537), stats.getValue(TeamStatType.FACEOFF_PERCENTAGE).away)
        assertEquals(60000.milliseconds, details.suggestedRefresh)
    }

    @Test
    fun liveGame_mapsClockAndPowerPlayGoals() {
        val details = load("game_live.json")
        assertEquals(GameStatus.Live(period = 2, clock = "1:40"), details.game.status)
        assertEquals(1, details.game.awayScore)
        assertEquals(4, details.game.homeScore)
        assertEquals(5, details.goals.size)
        assertEquals(3, details.goals.count { it.strength == GoalStrength.POWER_PLAY })
        assertEquals(3914.milliseconds, details.suggestedRefresh)
    }

    @Test
    fun futureGame_hasNoScoresGoalsOrStats() {
        val details = load("game_future.json")
        assertEquals(GameStatus.Scheduled, details.game.status)
        assertNull(details.game.awayScore)
        assertTrue(details.goals.isEmpty())
        assertTrue(details.teamStats.isEmpty())
    }

    @Test
    fun gameIds_roundTrip() {
        assertEquals("nhl-2026020311", nhlGameId(2026020311))
        assertEquals(2026020311L, apiGameIdOrNull("nhl-2026020311"))
        assertNull(apiGameIdOrNull("ahl-123"))
        assertNull(apiGameIdOrNull("nhl-abc"))
    }
}
