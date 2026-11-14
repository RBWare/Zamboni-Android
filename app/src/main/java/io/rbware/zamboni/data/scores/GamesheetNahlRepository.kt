package io.rbware.zamboni.data.scores

import io.rbware.zamboni.data.leagues.Leagues
import io.rbware.zamboni.data.model.GameDetails
import io.rbware.zamboni.data.model.GameStatus
import io.rbware.zamboni.data.model.League
import io.rbware.zamboni.data.model.Schedule
import io.rbware.zamboni.data.model.Team
import io.rbware.zamboni.data.remote.GamesheetApi
import io.rbware.zamboni.data.remote.GamesheetSeasonDto
import io.rbware.zamboni.data.teams.TeamRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * NAHL scores, game details and teams from Gamesheet.
 *
 * Gamesheet organizes everything by season, and seasons are discovered through one club's
 * "myclub" page ([clubNid]); any NAHL club works, since they all play the same seasons. Game
 * IDs include the season (see [nahlGameId]) so details can be fetched directly.
 */
class GamesheetNahlRepository(
    private val api: GamesheetApi,
    private val clubNid: String = LONE_STAR_BRAHMAS_NID,
    private val clock: Clock = Clock.system(ZoneId.of("America/Chicago")),
) : ScoresRepository, TeamRepository {

    private val mutex = Mutex()
    private var seasons: Cached<List<GamesheetSeasonDto>>? = null
    private var teams: Cached<List<Team>>? = null

    private class Cached<T>(val value: T, val at: TimeSource.Monotonic.ValueTimeMark = TimeSource.Monotonic.markNow()) {
        fun isFresh(maxAge: Duration) = at.elapsedNow() < maxAge
    }

    override suspend fun getLeagues(): List<League> = listOf(Leagues.NAHL)

    override suspend fun getSchedule(date: LocalDate): Schedule = coroutineScope {
        val games = seasons().coveringDate(date).map { season ->
            async {
                api.getUnifiedSchedule(season.id, start = date.toString(), end = date.plusDays(1).toString())
                    .data.orEmpty()
                    .map { it.toGame(season.id) }
            }
        }.awaitAll().flatten().sortedBy { it.startTime }
        Schedule(date = date, games = games, suggestedRefresh = refreshFor(games.any { it.status is GameStatus.Live }))
    }

    override suspend fun getGameDetails(gameId: String): GameDetails {
        val (seasonId, apiGameId) = parseNahlGameId(gameId) ?: throw GameNotFoundException(gameId)
        val game = api.getUnifiedSchedule(seasonId, gameId = apiGameId).data
            ?.firstOrNull { it.gameId == apiGameId }
            ?: throw GameNotFoundException(gameId)
        val live = gamesheetStatus(game) is GameStatus.Live
        return game.toGameDetails(seasonId, refreshFor(live))
    }

    /** Teams in the current regular season, sorted by name. */
    override suspend fun getTeams(): List<Team> {
        mutex.withLock { teams?.takeIf { it.isFresh(TEAMS_MAX_AGE) }?.let { return it.value } }
        val season = seasons().currentRegularSeason(LocalDate.now(clock)) ?: return emptyList()
        val result = api.getTeams(season.id).data.orEmpty()
            .filter { !it.hidden && it.division?.title?.equals("GUEST", ignoreCase = true) != true }
            .map { it.toTeam() }
            .sortedBy { it.name }
        mutex.withLock { teams = Cached(result) }
        return result
    }

    private suspend fun seasons(): List<GamesheetSeasonDto> {
        mutex.withLock { seasons?.takeIf { it.isFresh(SEASONS_MAX_AGE) }?.let { return it.value } }
        val result = api.getMyClub(clubNid).data?.seasons.orEmpty()
        mutex.withLock { seasons = Cached(result) }
        return result
    }

    private fun refreshFor(live: Boolean): Duration = if (live) LIVE_REFRESH else IDLE_REFRESH

    companion object {
        /** The club ID used to look up seasons. */
        const val LONE_STAR_BRAHMAS_NID = "4-605"

        private val LIVE_REFRESH = 30.seconds
        private val IDLE_REFRESH = 120.seconds
        private val SEASONS_MAX_AGE = 12.hours
        private val TEAMS_MAX_AGE = 12.hours
    }
}
