package io.rbware.zamboni

import io.rbware.zamboni.data.leagues.Leagues
import io.rbware.zamboni.data.model.GameDetails
import io.rbware.zamboni.data.model.League
import io.rbware.zamboni.data.model.Schedule
import io.rbware.zamboni.data.scores.FallbackScoresRepository
import io.rbware.zamboni.data.scores.GameNotFoundException
import io.rbware.zamboni.data.scores.ScoresRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.time.LocalDate

class FallbackScoresRepositoryTest {
    private val date = LocalDate.of(2026, 11, 14)

    private class FakeRepository(private val schedule: Schedule? = null, private val error: Exception? = null) :
        ScoresRepository {
        var calls = 0
        override suspend fun getLeagues(): List<League> = listOf(Leagues.NHL)
        override suspend fun getSchedule(date: LocalDate): Schedule {
            calls++
            error?.let { throw it }
            return checkNotNull(schedule)
        }
        override suspend fun getGameDetails(gameId: String): GameDetails {
            calls++
            throw error ?: GameNotFoundException(gameId)
        }
    }

    private val fallbacks = mutableListOf<String>()

    private fun repo(primary: ScoresRepository, fallback: ScoresRepository) =
        FallbackScoresRepository(primary, fallback) { operation, _ -> fallbacks += operation }

    @Test
    fun usesPrimaryWhenItWorks() = runBlocking {
        val primary = FakeRepository(Schedule(date, emptyList()))
        val fallback = FakeRepository(Schedule(date.plusDays(1), emptyList()))
        assertEquals(date, repo(primary, fallback).getSchedule(date).date)
        assertEquals(0, fallback.calls)
        assertTrue(fallbacks.isEmpty())
    }

    @Test
    fun usesFallbackWhenPrimaryFails() = runBlocking {
        val primary = FakeRepository(error = IOException("down"))
        val fallback = FakeRepository(Schedule(date, emptyList()))
        assertEquals(date, repo(primary, fallback).getSchedule(date).date)
        assertEquals(1, fallback.calls)
        assertEquals(listOf("getSchedule($date)"), fallbacks)
    }

    @Test
    fun bothFail_throwsPrimaryError() = runBlocking {
        val primaryError = IOException("primary down")
        val fallbackError = IOException("fallback down")
        try {
            repo(FakeRepository(error = primaryError), FakeRepository(error = fallbackError)).getSchedule(date)
            fail("Expected an exception")
        } catch (e: IOException) {
            assertSame(primaryError, e)
            assertSame(fallbackError, e.suppressed.single())
        }
    }

    @Test
    fun bothFail_prefersNotFound() = runBlocking {
        try {
            repo(FakeRepository(error = IOException("down")), FakeRepository()).getGameDetails("nhl-1")
            fail("Expected an exception")
        } catch (e: GameNotFoundException) {
            assertTrue(e.suppressed.single() is IOException)
        }
    }
}
