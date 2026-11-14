package io.rbware.zamboni

import io.rbware.zamboni.data.model.Decision
import io.rbware.zamboni.data.model.GameStatus
import io.rbware.zamboni.data.placeholder.PlaceholderHockeyData
import io.rbware.zamboni.data.remote.HockeyNerdApi
import io.rbware.zamboni.data.remote.ScheduleResponse
import io.rbware.zamboni.data.scores.toSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import kotlin.time.Duration.Companion.milliseconds

/** Parses a real /api/schedule response (src/test/resources/schedule_sample.json). */
class ScheduleMapperTest {
    private val response: ScheduleResponse = HockeyNerdApi.json.decodeFromString(
        checkNotNull(javaClass.classLoader?.getResource("schedule_sample.json")).readText(),
    )
    private val knownTeams = PlaceholderHockeyData.teams.associateBy { it.id }
    private val schedule = response.toSchedule(knownTeams)
    private val games = schedule.games

    @Test
    fun parsesTopLevelFields() {
        assertEquals(LocalDate.of(2026, 11, 14), schedule.date)
        assertEquals(10222.milliseconds, schedule.suggestedRefresh)
        assertEquals(5, games.size)
    }

    @Test
    fun liveGame_mapsPeriodClockAndScores() {
        val live = games[0]
        assertEquals(GameStatus.Live(period = 2, clock = "9:08", intermission = false), live.status)
        assertEquals(1, live.awayScore)
        assertEquals(4, live.homeScore)
        assertEquals(Instant.parse("2026-11-14T18:00:00Z"), live.startTime)
        assertEquals("Veikkaus Arena", live.venue)
        assertEquals("9-7-1", live.awayRecord)
    }

    @Test
    fun knownTeams_useFullNamesAndAppIds() {
        val live = games[0]
        assertEquals("nhl-sea", live.away.id)
        assertEquals("Seattle Kraken", live.away.name)
        assertEquals("Carolina Hurricanes", live.home.name)
    }

    @Test
    fun scheduledGame_hasNoScores() {
        val future = games[1]
        assertEquals(GameStatus.Scheduled, future.status)
        assertNull(future.awayScore)
        assertNull(future.homeScore)
    }

    @Test
    fun finalGames_mapDecision() {
        assertEquals(GameStatus.Final(Decision.REGULATION), games[2].status)
        assertEquals(GameStatus.Final(Decision.SHOOTOUT), games[3].status)
        assertEquals(3, games[3].awayScore)
        assertEquals(2, games[3].homeScore)
        assertEquals("nhl-nsh", games[3].winner?.id)
    }

    @Test
    fun unknownTeam_fallsBackToApiData() {
        val away = games[4].away
        assertEquals("nhl-xyz", away.id)
        assertEquals("Testers", away.name)
        assertEquals("XYZ", away.abbreviation)
        assertEquals("https://assets.nhle.com/logos/nhl/svg/XYZ_light.svg", away.logo?.light)
    }
}
