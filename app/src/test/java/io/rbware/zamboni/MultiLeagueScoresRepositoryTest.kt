package io.rbware.zamboni

import io.rbware.zamboni.data.leagues.Leagues
import io.rbware.zamboni.data.model.Game
import io.rbware.zamboni.data.model.GameDetails
import io.rbware.zamboni.data.model.GameStatus
import io.rbware.zamboni.data.model.League
import io.rbware.zamboni.data.model.Schedule
import io.rbware.zamboni.data.model.Team
import io.rbware.zamboni.data.scores.GameNotFoundException
import io.rbware.zamboni.data.scores.MultiLeagueScoresRepository
import io.rbware.zamboni.data.scores.ScoresRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import kotlin.time.Duration.Companion.seconds

class MultiLeagueScoresRepositoryTest {
    private val date = LocalDate.of(2026, 11, 14)

    private fun game(id: String, league: League, start: String): Game {
        val team = Team(id = "$id-t", name = id, abbreviation = "T", league = league)
        return Game(id, league, Instant.parse(start), GameStatus.Scheduled, team, team)
    }

    private class Fake(
        private val league: League,
        private val schedule: Schedule? = null,
        private val error: Exception? = null,
    ) : ScoresRepository {
        var detailsAsked: String? = null
        override suspend fun getLeagues() = listOf(league)
        override suspend fun getSchedule(date: LocalDate): Schedule = error?.let { throw it } ?: schedule!!
        override suspend fun getGameDetails(gameId: String): GameDetails {
            detailsAsked = gameId
            throw GameNotFoundException(gameId)
        }
    }

    private val failures = mutableListOf<Exception>()

    private fun repo(
        nhl: ScoresRepository,
        nahl: ScoresRepository,
        enabled: Set<String> = setOf(Leagues.NHL.id, Leagues.NAHL.id),
    ) = MultiLeagueScoresRepository(
        listOf(
            MultiLeagueScoresRepository.Source(Leagues.NHL.id, nhl) { it.startsWith("nhl-") },
            MultiLeagueScoresRepository.Source(Leagues.NAHL.id, nahl) { it.startsWith("nahl-") },
        ),
        enabledLeagueIds = { enabled },
        onLeagueFailed = { failures += it },
    )

    @Test
    fun turnedOffLeague_isNotLoaded() = runBlocking {
        val nhl = Fake(Leagues.NHL, Schedule(date, listOf(game("nhl-2", Leagues.NHL, "2026-11-15T00:00:00Z"))))
        // Would throw if it were asked.
        val nahl = Fake(Leagues.NAHL, error = IOException("must not be loaded"))
        val repo = repo(nhl, nahl, enabled = setOf(Leagues.NHL.id))
        assertEquals(listOf("nhl-2"), repo.getSchedule(date).games.map { it.id })
        assertEquals(listOf(Leagues.NHL), repo.getLeagues())
        assertEquals(emptyList<Exception>(), failures)
    }

    @Test
    fun noLoadableLeague_isAnEmptyDay() = runBlocking {
        val schedule = repo(Fake(Leagues.NHL), Fake(Leagues.NAHL), enabled = setOf("other")).getSchedule(date)
        assertEquals(emptyList<Game>(), schedule.games)
    }

    @Test
    fun mergesSchedulesInStartOrder() = runBlocking {
        val nhl = Fake(Leagues.NHL, Schedule(date, listOf(game("nhl-2", Leagues.NHL, "2026-11-15T00:00:00Z")), 20.seconds))
        val nahl = Fake(Leagues.NAHL, Schedule(date, listOf(game("nahl-1", Leagues.NAHL, "2026-11-14T23:00:00Z")), 120.seconds))
        val schedule = repo(nhl, nahl).getSchedule(date)
        assertEquals(listOf("nahl-1", "nhl-2"), schedule.games.map { it.id })
        assertEquals(20.seconds, schedule.suggestedRefresh)
        assertEquals(listOf(Leagues.NHL, Leagues.NAHL), repo(nhl, nahl).getLeagues())
    }

    @Test
    fun oneLeagueFailing_showsTheOther() = runBlocking {
        val error = IOException("gamesheet down")
        val nhl = Fake(Leagues.NHL, Schedule(date, listOf(game("nhl-2", Leagues.NHL, "2026-11-15T00:00:00Z"))))
        val schedule = repo(nhl, Fake(Leagues.NAHL, error = error)).getSchedule(date)
        assertEquals(listOf("nhl-2"), schedule.games.map { it.id })
        assertSame(error, failures.single())
    }

    @Test
    fun allLeaguesFailing_throws() = runBlocking {
        val first = IOException("nhl down")
        try {
            repo(Fake(Leagues.NHL, error = first), Fake(Leagues.NAHL, error = IOException("nahl down"))).getSchedule(date)
            fail("Expected an exception")
        } catch (e: IOException) {
            // Coroutines may rethrow a copy (stack trace recovery) without the suppressed
            // errors, so only the message can be compared.
            assertEquals(first.message, e.message)
        }
    }

    @Test
    fun detailsRouteByGameId() = runBlocking {
        val nhl = Fake(Leagues.NHL)
        val nahl = Fake(Leagues.NAHL)
        try {
            repo(nhl, nahl).getGameDetails("nahl-5")
        } catch (_: GameNotFoundException) {
        }
        assertEquals(null, nhl.detailsAsked)
        assertEquals("nahl-5", nahl.detailsAsked)
    }
}
