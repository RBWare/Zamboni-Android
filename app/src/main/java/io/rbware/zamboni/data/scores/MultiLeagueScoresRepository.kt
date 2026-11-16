package io.rbware.zamboni.data.scores

import io.rbware.zamboni.data.model.GameDetails
import io.rbware.zamboni.data.model.League
import io.rbware.zamboni.data.model.Schedule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.time.LocalDate

/**
 * Combines one repository per league. Schedules are loaded in parallel and merged; if some
 * leagues fail, the others are still shown (the failure goes to [onLeagueFailed]), and only if
 * every league fails is an error thrown.
 *
 * Leagues the user turned off ([enabledLeagueIds]) aren't loaded at all, so they also produce no
 * alerts or Live Updates. Game details are still served for any league, e.g. for a game opened
 * from an older notification.
 *
 * @param sources each league's repository and a test for whether an app game ID belongs to it,
 *   used to route [getGameDetails].
 */
class MultiLeagueScoresRepository(
    private val sources: List<Source>,
    private val enabledLeagueIds: suspend () -> Set<String> = { sources.mapTo(mutableSetOf()) { it.leagueId } },
    private val onLeagueFailed: (error: Exception) -> Unit = {},
) : ScoresRepository {

    class Source(val leagueId: String, val repository: ScoresRepository, val ownsGameId: (String) -> Boolean)

    private suspend fun enabledSources(): List<Source> {
        val enabled = enabledLeagueIds()
        return sources.filter { it.leagueId in enabled }
    }

    override suspend fun getLeagues(): List<League> = enabledSources().flatMap { it.repository.getLeagues() }

    override suspend fun getSchedule(date: LocalDate): Schedule = coroutineScope {
        val active = enabledSources()
        if (active.isEmpty()) return@coroutineScope Schedule(date = date, games = emptyList())
        val results = active.map { source ->
            async {
                try {
                    Result.success(source.repository.getSchedule(date))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        }.awaitAll()
        val schedules = results.mapNotNull { it.getOrNull() }
        val errors = results.mapNotNull { it.exceptionOrNull() as? Exception }
        if (schedules.isEmpty()) {
            val first = errors.first()
            errors.drop(1).forEach(first::addSuppressed)
            throw first
        }
        errors.forEach(onLeagueFailed)
        Schedule(
            date = date,
            games = schedules.flatMap { it.games }.sortedBy { it.startTime },
            suggestedRefresh = schedules.mapNotNull { it.suggestedRefresh }.minOrNull(),
        )
    }

    override suspend fun getGameDetails(gameId: String): GameDetails {
        val source = sources.firstOrNull { it.ownsGameId(gameId) } ?: throw GameNotFoundException(gameId)
        return source.repository.getGameDetails(gameId)
    }
}
