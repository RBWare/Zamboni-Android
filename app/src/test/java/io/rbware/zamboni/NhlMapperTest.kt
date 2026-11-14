package io.rbware.zamboni

import io.rbware.zamboni.data.model.Decision
import io.rbware.zamboni.data.model.GameStatus
import io.rbware.zamboni.data.model.GoalStrength
import io.rbware.zamboni.data.model.StatValue
import io.rbware.zamboni.data.model.TeamSide
import io.rbware.zamboni.data.model.TeamStatType
import io.rbware.zamboni.data.placeholder.PlaceholderHockeyData
import io.rbware.zamboni.data.remote.NhlApi
import io.rbware.zamboni.data.remote.NhlLandingResponse
import io.rbware.zamboni.data.remote.NhlRightRailResponse
import io.rbware.zamboni.data.remote.NhlScoreResponse
import io.rbware.zamboni.data.remote.NhlStandingsResponse
import io.rbware.zamboni.data.scores.NHL_IDLE_REFRESH
import io.rbware.zamboni.data.scores.NHL_LIVE_GAME_REFRESH
import io.rbware.zamboni.data.scores.NHL_LIVE_SCHEDULE_REFRESH
import io.rbware.zamboni.data.scores.isEmptyNet
import io.rbware.zamboni.data.scores.toGameDetails
import io.rbware.zamboni.data.scores.toRecords
import io.rbware.zamboni.data.scores.toSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Parses real api-web.nhle.com responses from src/test/resources. */
class NhlMapperTest {
    private val knownTeams = PlaceholderHockeyData.teams.associateBy { it.id }

    private inline fun <reified T> load(name: String): T {
        val text = checkNotNull(javaClass.classLoader?.getResource(name)).readText()
        return NhlApi.json.decodeFromString<T>(text)
    }

    private val records by lazy { load<NhlStandingsResponse>("nhl_standings.json").toRecords() }

    @Test
    fun standings_buildRecords() {
        assertEquals(32, records.size)
        assertEquals("9-7-1", records["SEA"])
        assertEquals("11-4-5", records["NSH"])
    }

    @Test
    fun score_liveDay() {
        val schedule = load<NhlScoreResponse>("nhl_score_live.json").toSchedule(knownTeams, records)
        assertEquals(LocalDate.of(2026, 11, 14), schedule.date)
        assertEquals(14, schedule.games.size)
        assertEquals(NHL_LIVE_SCHEDULE_REFRESH, schedule.suggestedRefresh)

        val live = schedule.games.first { it.id == "nhl-2026020321" }
        assertEquals(GameStatus.Live(period = 3, clock = "8:54"), live.status)
        assertEquals("Seattle Kraken", live.away.name)
        assertEquals("nhl-car", live.home.id)
        assertEquals(1, live.awayScore)
        assertEquals(5, live.homeScore)
        assertEquals("Veikkaus Arena", live.venue)
        assertEquals("9-7-1", live.awayRecord)
        assertNotNull(live.home.colors?.primary)

        val upcoming = schedule.games.first { it.id == "nhl-2026020313" }
        assertEquals(GameStatus.Scheduled, upcoming.status)
        assertNull(upcoming.awayScore)
    }

    @Test
    fun score_finishedDay() {
        val schedule = load<NhlScoreResponse>("nhl_score_final.json").toSchedule(knownTeams, emptyMap())
        assertEquals(NHL_IDLE_REFRESH, schedule.suggestedRefresh)
        assertTrue(schedule.games.all { it.status is GameStatus.Final })
        val shootout = schedule.games.first { it.id == "nhl-2026020311" }
        assertEquals(GameStatus.Final(Decision.SHOOTOUT), shootout.status)
        assertEquals(3, shootout.awayScore)
        assertEquals(2, shootout.homeScore)
        assertNull(shootout.awayRecord)
    }

    @Test
    fun landing_finalShootout_matchesHockeyNerd() {
        val details = load<NhlLandingResponse>("nhl_landing_final_shootout.json")
            .toGameDetails(load<NhlRightRailResponse>("nhl_rightrail_final_shootout.json"), knownTeams)
        val game = details.game
        assertEquals("nhl-2026020311", game.id)
        assertEquals(GameStatus.Final(Decision.SHOOTOUT), game.status)
        assertEquals("Nashville Predators", game.away.name)
        assertEquals(3, game.awayScore)
        assertEquals(2, game.homeScore)

        // Same four goals hockeynerd.com reports; the shootout winner isn't one.
        assertEquals(4, details.goals.size)
        val first = details.goals.first()
        assertEquals(TeamSide.AWAY, first.side)
        assertEquals(1, first.period)
        assertEquals("7:56", first.timeInPeriod)
        assertEquals("Ryan O'Reilly", first.scorer.name)
        assertEquals(GoalStrength.EVEN, first.strength)
        assertFalse(first.emptyNet)
        assertEquals(2, details.goals.last().awayScore)

        assertEquals(TeamStatType.entries.toList(), details.teamStats.map { it.type })
        val shots = details.teamStats.first { it.type == TeamStatType.SHOTS_ON_GOAL }
        assertEquals(StatValue.Number(33.0), shots.away)
        val powerPlay = details.teamStats.first { it.type == TeamStatType.POWER_PLAY }
        assertEquals(StatValue.Text("0/2"), powerPlay.away)
        assertEquals(NHL_IDLE_REFRESH, details.suggestedRefresh)
    }

    @Test
    fun landing_live() {
        val details = load<NhlLandingResponse>("nhl_landing_live.json").toGameDetails(null, knownTeams)
        assertTrue(details.game.status is GameStatus.Live)
        assertEquals(6, details.goals.size)
        assertEquals(GoalStrength.POWER_PLAY, details.goals.first().strength)
        assertEquals(listOf("S. Aho", "N. Ehlers"), details.goals.first().assists!!.map { it.name })
        assertEquals(17, details.goals.first().assists!!.first().seasonTotal)
        assertTrue(details.teamStats.isEmpty())
        assertEquals(NHL_LIVE_GAME_REFRESH, details.suggestedRefresh)
    }

    @Test
    fun landing_future_hasNoGoalsOrStats() {
        val details = load<NhlLandingResponse>("nhl_landing_future.json")
            .toGameDetails(load<NhlRightRailResponse>("nhl_rightrail_future.json"), knownTeams)
        assertEquals(GameStatus.Scheduled, details.game.status)
        assertNull(details.game.awayScore)
        assertTrue(details.goals.isEmpty())
        assertTrue(details.teamStats.isEmpty())
    }

    @Test
    fun emptyNet_fromSituationCode() {
        assertTrue(isEmptyNet("0651", TeamSide.HOME))
        assertTrue(isEmptyNet("1560", TeamSide.AWAY))
        assertFalse(isEmptyNet("1551", TeamSide.HOME))
        assertFalse(isEmptyNet("0651", TeamSide.AWAY))
        assertFalse(isEmptyNet(null, TeamSide.AWAY))
    }
}
