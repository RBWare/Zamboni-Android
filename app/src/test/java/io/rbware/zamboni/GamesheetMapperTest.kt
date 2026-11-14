package io.rbware.zamboni

import io.rbware.zamboni.data.leagues.Leagues
import io.rbware.zamboni.data.model.Decision
import io.rbware.zamboni.data.model.GameStatus
import io.rbware.zamboni.data.model.StatValue
import io.rbware.zamboni.data.model.TeamSide
import io.rbware.zamboni.data.model.TeamStatType
import io.rbware.zamboni.data.remote.GamesheetApi
import io.rbware.zamboni.data.remote.GamesheetMyClubDto
import io.rbware.zamboni.data.remote.GamesheetResponse
import io.rbware.zamboni.data.remote.GamesheetTeamDto
import io.rbware.zamboni.data.remote.GamesheetUnifiedGameDto
import io.rbware.zamboni.data.scores.coveringDate
import io.rbware.zamboni.data.scores.currentRegularSeason
import io.rbware.zamboni.data.scores.elapsedSeconds
import io.rbware.zamboni.data.scores.nahlAbbreviation
import io.rbware.zamboni.data.scores.nahlGameId
import io.rbware.zamboni.data.scores.nahlTeamId
import io.rbware.zamboni.data.scores.parseNahlGameId
import io.rbware.zamboni.data.scores.periodNumber
import io.rbware.zamboni.data.scores.titleCase
import io.rbware.zamboni.data.scores.toGame
import io.rbware.zamboni.data.scores.toGameDetails
import io.rbware.zamboni.data.scores.toTeam
import io.rbware.zamboni.data.scores.withImageVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import kotlin.time.Duration.Companion.seconds

/** Parses real gateway.gamesheet.io responses from src/test/resources. */
class GamesheetMapperTest {
    private val season = 15192L

    private inline fun <reified T> load(name: String): T {
        val text = checkNotNull(javaClass.classLoader?.getResource(name)).readText()
        return checkNotNull(GamesheetApi.json.decodeFromString<GamesheetResponse<T>>(text).data)
    }

    private val seasons by lazy { load<GamesheetMyClubDto>("gamesheet_myclub.json").seasons }

    private fun day(name: String) = load<List<GamesheetUnifiedGameDto>>(name)

    @Test
    fun seasons_pickCoveringAndRegular() {
        val nov = LocalDate.of(2026, 11, 14)
        assertEquals(listOf(15192L, 15600L), seasons.coveringDate(nov).map { it.id })
        assertEquals(15192L, seasons.currentRegularSeason(nov)?.id)
        // Playoff time last season: regular, exhibition and Robertson Cup seasons all overlap.
        assertTrue(14810L in seasons.coveringDate(LocalDate.of(2026, 5, 1)).map { it.id })
    }

    @Test
    fun finishedDay() {
        val games = day("gamesheet_unified_final.json").map { it.toGame(season) }
        assertEquals(15, games.size)
        assertTrue(games.all { it.league == Leagues.NAHL && it.status is GameStatus.Final })

        val lsb = games.first { it.id == "nahl-15192-2909587" }
        assertEquals("nahl-shreveport-mudbugs", lsb.away.id)
        assertEquals("SHV", lsb.away.abbreviation)
        assertEquals("Lone Star Brahmas", lsb.home.name)
        assertEquals(3, lsb.awayScore)
        assertEquals(5, lsb.homeScore)
        assertNull(lsb.homeRecord)
        assertEquals(Instant.parse("2026-11-14T01:30:00Z"), lsb.startTime)
        assertEquals("NYTEX Sports Centre", lsb.venue)

        assertEquals(GameStatus.Final(Decision.SHOOTOUT), games.first { it.id == "nahl-15192-2909066" }.status)
        assertEquals(GameStatus.Final(Decision.OVERTIME), games.first { it.id == "nahl-15192-2908722" }.status)
    }

    @Test
    fun liveDay_hidesEmptyScores() {
        val games = day("gamesheet_unified_live.json").map { it.toGame(season) }
        assertEquals(16, games.size)
        val live = games.first { it.id == "nahl-15192-2908880" }
        assertEquals(GameStatus.Live(period = null, clock = null), live.status)
        // Gamesheet reports 0-0 with no shots while the game is on; that isn't a real score.
        assertNull(live.awayScore)
        assertEquals("8-9-3-0", live.homeRecord)
        assertEquals(Instant.parse("2026-11-14T20:30:00Z"), live.startTime)
        assertTrue(live.home.logo!!.light.endsWith("/256"))
    }

    @Test
    fun details_goalsInOrderWithRunningScore() {
        val details = day("gamesheet_unified_game.json").single().toGameDetails(season, 120.seconds)
        val goals = details.goals
        assertEquals(8, goals.size)
        // Shreveport scored three in a row midway through the 1st (10:12, 10:35, 10:51 elapsed).
        assertEquals(TeamSide.HOME, goals[0].side)
        assertEquals("3:43", goals[0].timeInPeriod)
        assertEquals("Colvin Callahan", goals[0].scorer.name)
        assertEquals(listOf("10:12", "10:35", "10:51"), goals.subList(1, 4).map { it.timeInPeriod })
        assertEquals(3, goals[3].awayScore)
        assertEquals(1, goals[3].homeScore)
        assertEquals("William - Alexis Tremblay", goals[3].scorer.name)
        // Gamesheet has no assist data, which is not the same as unassisted.
        assertNull(goals[3].assists)
        val last = goals.last()
        assertEquals(3, last.period)
        assertEquals(3, last.awayScore)
        assertEquals(5, last.homeScore)

        val shots = details.teamStats.first { it.type == TeamStatType.SHOTS_ON_GOAL }
        assertEquals(StatValue.Number(25.0), shots.away)
        assertEquals(StatValue.Number(26.0), shots.home)
        val pp = details.teamStats.first { it.type == TeamStatType.POWER_PLAY }
        assertEquals(StatValue.Text("1/2"), pp.away)
        assertEquals(StatValue.Text("0/3"), pp.home)
    }

    @Test
    fun details_overtimeAndShootout() {
        val games = day("gamesheet_unified_final.json")
        val ot = games.first { it.gameId == 2908722L }.toGameDetails(season, null)
        val otGoal = ot.goals.last()
        assertEquals(4, otGoal.period)
        assertTrue(otGoal.isOvertime)
        assertEquals("3:05", otGoal.timeInPeriod)
        assertEquals(3, otGoal.awayScore)

        // Shootout goals aren't listed; regulation ended 1-1.
        val so = games.first { it.gameId == 2909066L }.toGameDetails(season, null)
        assertEquals(2, so.goals.size)
        assertEquals(1, so.goals.last().awayScore)
        assertEquals(1, so.goals.last().homeScore)
    }

    @Test
    fun teams_haveStableIds() {
        val teams = load<List<GamesheetTeamDto>>("gamesheet_teams.json").map { it.toTeam() }
        assertTrue(teams.any { it.id == "nahl-lone-star-brahmas" })
        assertTrue(teams.any { it.id == "nahl-springfield-jr-blues" })
    }

    @Test
    fun helpers() {
        assertEquals("nahl-st-cloud-norsemen", nahlTeamId("St. Cloud Norsemen"))
        assertEquals(15192L to 2909587L, parseNahlGameId(nahlGameId(15192, 2909587)))
        assertNull(parseNahlGameId("nhl-2026020311"))
        assertNull(parseNahlGameId("nahl-2909587"))
        assertEquals(4, periodNumber("ot_1"))
        assertEquals(2, periodNumber("2"))
        assertNull(periodNumber("SO"))
        assertEquals(20 * 60 - (16 * 60 + 17), elapsedSeconds("16:17", 1))
        assertEquals(4 * 60 + 3, elapsedSeconds("00:57", 4))
        assertNull(elapsedSeconds("", 1))
        assertEquals("O'Brien", titleCase("O'BRIEN"))
        assertEquals("LSB", nahlAbbreviation("Lone Star Brahmas"))
        assertEquals(
            "https://imagedelivery.net/acct/img/256",
            withImageVariant("https://imagedelivery.net/acct/img"),
        )
        assertEquals(
            "https://imagedelivery.net/acct/img/256",
            withImageVariant("https://imagedelivery.net/acct/img/256"),
        )
    }
}
