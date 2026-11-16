package io.rbware.zamboni

import io.rbware.zamboni.data.leagues.Leagues
import io.rbware.zamboni.data.model.Decision
import io.rbware.zamboni.data.model.GameStatus
import io.rbware.zamboni.data.model.GoalStrength
import io.rbware.zamboni.data.model.StatValue
import io.rbware.zamboni.data.model.TeamSide
import io.rbware.zamboni.data.model.TeamStatType
import io.rbware.zamboni.data.preferences.AhlTeamIdMigration
import io.rbware.zamboni.data.remote.HockeyTechClockResponse
import io.rbware.zamboni.data.remote.HockeyTechGameSummaryDto
import io.rbware.zamboni.data.remote.HockeyTechKeyRejectedException
import io.rbware.zamboni.data.remote.HockeyTechSiteKitResponse
import io.rbware.zamboni.data.remote.decodeHockeyTech
import io.rbware.zamboni.data.scores.ahlGameId
import io.rbware.zamboni.data.scores.clockSeconds
import io.rbware.zamboni.data.scores.coveringDate
import io.rbware.zamboni.data.scores.currentRegularSeason
import io.rbware.zamboni.data.scores.hockeyTechStatus
import io.rbware.zamboni.data.scores.isAhlGameId
import io.rbware.zamboni.data.scores.isNahlGameId
import io.rbware.zamboni.data.scores.parseAhlGameId
import io.rbware.zamboni.data.scores.radioLink
import io.rbware.zamboni.data.scores.toGame
import io.rbware.zamboni.data.scores.toGameDetails
import io.rbware.zamboni.data.scores.toTeam
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import kotlin.time.Duration.Companion.seconds

/** Parses real lscluster.hockeytech.com responses (AHL, November 2026) from src/test/resources. */
class HockeyTechMapperTest {

    private inline fun <reified T> load(name: String): T =
        checkNotNull(javaClass.classLoader?.getResource(name)).readText().toResponseBody().decodeHockeyTech()

    private val siteKit = { name: String -> load<HockeyTechSiteKitResponse>(name).siteKit }

    @Test
    fun seasons_pickCoveringAndRegular() {
        val seasons = checkNotNull(siteKit("hockeytech_seasons.json").seasons)
        val nov = LocalDate.of(2026, 11, 16)
        assertEquals(listOf("94"), seasons.coveringDate(nov).map { it.id })
        assertEquals("94", seasons.currentRegularSeason(nov)?.id)
        // The playoffs, not the all-star game, in May.
        assertEquals(listOf("92"), seasons.coveringDate(LocalDate.of(2026, 5, 1)).map { it.id })
        // Between seasons, the latest regular season that has started.
        assertEquals("90", seasons.currentRegularSeason(LocalDate.of(2026, 8, 1))?.id)
    }

    @Test
    fun scorebar_finalAndUpcomingGames() {
        val games = checkNotNull(siteKit("hockeytech_scorebar.json").scorebar).associateBy { it.id }

        val bel = games.getValue("1029321").toGame()
        assertEquals("ahl-1029321", bel.id)
        assertEquals(Leagues.AHL, bel.league)
        assertEquals("ahl-cle", bel.away.id)
        assertEquals("Cleveland Monsters", bel.away.name)
        // Cleveland's logo file carries the season; the 50px one from the scores bar, made full size.
        assertEquals("https://assets.leaguestat.com/ahl/logos/373_94.png", bel.away.logo?.light)
        assertEquals("https://assets.leaguestat.com/ahl/logos/413.png", bel.home.logo?.light)
        assertEquals("ahl-bel", bel.home.id)
        assertEquals(1, bel.awayScore)
        assertEquals(3, bel.homeScore)
        assertEquals(GameStatus.Final(Decision.REGULATION), bel.status)
        assertEquals(Instant.parse("2026-11-15T18:00:00Z"), bel.startTime)
        assertEquals("CIBC Place", bel.venue)
        assertNull(bel.awayRecord)

        assertEquals(GameStatus.Final(Decision.SHOOTOUT), games.getValue("1029323").toGame().status)

        val upcoming = games.getValue("1029332").toGame()
        assertEquals(GameStatus.Scheduled, upcoming.status)
        assertNull(upcoming.awayScore)
        assertTrue(upcoming.homeRecord!!.matches(Regex("""\d+-\d+-\d+-\d+""")))
    }

    @Test
    fun teams() {
        val teams = checkNotNull(siteKit("hockeytech_teams.json").teams).map { it.toTeam() }
        assertEquals(32, teams.size)
        val utica = teams.single { it.name == "Utica Comets" }
        assertEquals("ahl-utc", utica.id)
        assertEquals("UTC", utica.abbreviation)
        assertEquals(Leagues.AHL, utica.league)
        assertEquals("https://assets.leaguestat.com/ahl/logos/390_94.png", utica.logo?.light)
    }

    @Test
    fun summary_regulationFinal() {
        val details = load<HockeyTechGameSummaryDto>("hockeytech_summary_final.json").toGameDetails(clock = null, suggestedRefresh = 120.seconds)
        val game = details.game
        assertEquals("ahl-1029321", game.id)
        assertEquals(GameStatus.Final(Decision.REGULATION), game.status)
        assertEquals(1, game.awayScore)
        assertEquals(3, game.homeScore)

        val goals = details.goals
        assertEquals(4, goals.size)
        assertEquals(listOf(1 to 0, 1 to 1, 2 to 1, 3 to 1), goals.map { it.homeScore to it.awayScore })
        assertEquals("https://assets.leaguestat.com/ahl/logos/373_94.png", game.away.logo?.light)
        val first = goals[0]
        assertEquals(TeamSide.HOME, first.side)
        assertEquals("7:47", first.timeInPeriod)
        assertEquals(5, first.scorer.seasonTotal)
        assertEquals(listOf(10, 9), first.assists?.map { it.seasonTotal })
        assertTrue(first.scorer.headshotUrl!!.startsWith("https://assets.leaguestat.com/"))
        val second = goals[1]
        assertEquals(TeamSide.AWAY, second.side)
        assertEquals(2, second.period)
        assertEquals(GoalStrength.POWER_PLAY, second.strength)
        assertEquals(1, second.assists?.size)
        assertEquals(emptyList<Any>(), goals[2].assists)

        val stats = details.teamStats.associateBy { it.type }
        assertEquals(StatValue.Number(16.0), stats.getValue(TeamStatType.SHOTS_ON_GOAL).away)
        assertEquals(StatValue.Text("1/3"), stats.getValue(TeamStatType.POWER_PLAY).away)
        assertEquals(StatValue.Text("1/4"), stats.getValue(TeamStatType.POWER_PLAY).home)
        assertEquals(StatValue.Number(15.0), stats.getValue(TeamStatType.PENALTY_MINUTES).away)
        // Not recorded in this rink (all zeros), so left out.
        assertFalse(TeamStatType.HITS in stats)
        assertFalse(TeamStatType.FACEOFF_PERCENTAGE in stats)

        // Cleveland's radio is an iHeartRadio page; Belleville has none.
        val radio = details.radio.single()
        assertEquals(TeamSide.AWAY, radio.side)
        assertTrue(radio.isWebPage)
        assertEquals("FloHockey", details.broadcasts.single().network)
    }

    @Test
    fun summary_shootoutWithClock() {
        val clock = load<HockeyTechClockResponse>("hockeytech_clock_final.json").gc.clock
        val details = load<HockeyTechGameSummaryDto>("hockeytech_summary_shootout.json").toGameDetails(clock, null)
        assertEquals(GameStatus.Final(Decision.SHOOTOUT), details.game.status)
        assertEquals(1, details.game.awayScore)
        assertEquals(2, details.game.homeScore)
        // The shootout winner counts in the score but isn't a listed goal.
        assertEquals(2, details.goals.size)
        assertEquals(2, details.radio.size)
    }

    @Test
    fun liveStatus() {
        val live = hockeyTechStatus("2", "2nd", period = "2", periodName = "2", clock = "07:05", intermission = false)
        assertEquals(GameStatus.Live(period = 2, clock = "7:05", secondsRemaining = 425), live)
        val schedule = hockeyTechStatus("2", null, period = "1", periodName = null, clock = "00:00:00", intermission = true)
        assertEquals(true, (schedule as GameStatus.Live).intermission)
        val gc = hockeyTechStatus("2", "Intermission", period = "1", periodName = "1st", clock = "00:00:00", intermission = false)
        assertEquals(true, (gc as GameStatus.Live).intermission)
        val shootout = hockeyTechStatus("2", null, period = "4", periodName = "SO", clock = "00:00", intermission = false)
        assertEquals(true, (shootout as GameStatus.Live).shootout)
    }

    @Test
    fun finalAndOtherStatuses() {
        assertEquals(GameStatus.Final(Decision.OVERTIME), hockeyTechStatus("4", "Final OT", "4", "OT", null, false))
        assertEquals(GameStatus.Final(Decision.OVERTIME), hockeyTechStatus("4", "Final 2OT", "5", null, null, false))
        assertEquals(GameStatus.Final(Decision.SHOOTOUT), hockeyTechStatus("3", "Unofficial Final", "4", null, null, false, shootout = true))
        assertEquals(GameStatus.Postponed, hockeyTechStatus("1", "Postponed", null, null, null, false))
        assertEquals(GameStatus.Scheduled, hockeyTechStatus("1", "7:00 PM EST", "1", "1", "00:00", false))
    }

    @Test
    fun gameIds() {
        assertEquals("ahl-1029321", ahlGameId("1029321"))
        assertEquals("1029321", parseAhlGameId("ahl-1029321"))
        assertNull(parseAhlGameId("nahl-15192-2909587"))
        assertNull(parseAhlGameId("nhl-2026020311"))
        assertNull(parseAhlGameId("ahl-"))
        assertFalse(isNahlGameId(ahlGameId("1")))
        assertTrue(isAhlGameId("ahl-1"))
    }

    @Test
    fun radioLinks() {
        assertTrue(radioLink(TeamSide.HOME, "https://hfd-wolf-pack.mixlr.com/")!!.isWebPage)
        assertFalse(radioLink(TeamSide.HOME, "https://example.com/live/playlist.m3u8?x=1")!!.isWebPage)
        assertNull(radioLink(TeamSide.HOME, ""))
    }

    @Test
    fun clockSeconds_formats() {
        assertEquals(754, clockSeconds("12:34"))
        assertEquals(754, clockSeconds("00:12:34"))
        assertNull(clockSeconds("Final"))
    }

    @Test(expected = HockeyTechKeyRejectedException::class)
    fun invalidKey() {
        "Invalid key.".toResponseBody().decodeHockeyTech<HockeyTechSiteKitResponse>()
    }

    @Test
    fun oldAhlFavoritesAreRenamedToCurrentCodes() {
        val current = checkNotNull(siteKit("hockeytech_teams.json").teams).map { it.toTeam().id }.toSet()
        assertTrue(AhlTeamIdMigration.RENAMED.values.all { it in current })
    }
}
