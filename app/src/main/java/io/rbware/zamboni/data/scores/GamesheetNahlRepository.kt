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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** Result of checking a Gamesheet key. */
enum class GamesheetKeyCheck { VALID, REJECTED, FAILED }

/**
 * NAHL scores, game details and teams from Gamesheet.
 *
 * Gamesheet organizes everything by season, and seasons are discovered through one club's
 * "myclub" page ([clubNid]); any NAHL club works, since they all play the same seasons. Game
 * IDs include the season (see [nahlGameId]) so details can be fetched directly.
 *
 * Every call needs a partner key from [apiKey]. Without one the NAHL is simply left out: no
 * league, no games and no teams, rather than errors.
 */
class GamesheetNahlRepository(
    private val api: GamesheetApi,
    private val apiKey: suspend () -> String?,
    private val clubNid: String = LONE_STAR_BRAHMAS_NID,
    private val clock: Clock = Clock.system(ZoneId.of("America/Chicago")),
) : ScoresRepository, TeamRepository {

    private val mutex = Mutex()
    private var seasons: Cached<List<GamesheetSeasonDto>>? = null
    private var teams: Cached<List<Team>>? = null

    /** Cached data is only reused with the key it was fetched with. */
    private class Cached<T>(
        val value: T,
        val key: String,
        val at: TimeSource.Monotonic.ValueTimeMark = TimeSource.Monotonic.markNow(),
    ) {
        fun isFreshFor(key: String, maxAge: Duration) = this.key == key && at.elapsedNow() < maxAge
    }

    override suspend fun getLeagues(): List<League> = if (apiKey() != null) listOf(Leagues.NAHL) else emptyList()

    override suspend fun getSchedule(date: LocalDate): Schedule = coroutineScope {
        val key = apiKey() ?: return@coroutineScope Schedule(date = date, games = emptyList())
        val games = seasons(key).coveringDate(date).map { season ->
            async {
                api.getUnifiedSchedule(key, season.id, start = date.toString(), end = date.plusDays(1).toString())
                    .data.orEmpty()
                    .map { it.toGame(season.id) }
            }
        }.awaitAll().flatten().sortedBy { it.startTime }
        Schedule(date = date, games = games, suggestedRefresh = refreshFor(games.any { it.status is GameStatus.Live }))
    }

    override suspend fun getGameDetails(gameId: String): GameDetails {
        val key = apiKey() ?: throw GameNotFoundException(gameId)
        val (seasonId, apiGameId) = parseNahlGameId(gameId) ?: throw GameNotFoundException(gameId)
        val game = api.getUnifiedSchedule(key, seasonId, gameId = apiGameId).data
            ?.firstOrNull { it.gameId == apiGameId }
            ?: throw GameNotFoundException(gameId)
        val live = gamesheetStatus(game) is GameStatus.Live
        return game.toGameDetails(seasonId, refreshFor(live))
    }

    /** Teams in the current regular season, sorted by name. Empty without a key. */
    override suspend fun getTeams(): List<Team> {
        val key = apiKey() ?: return emptyList()
        mutex.withLock { teams?.takeIf { it.isFreshFor(key, TEAMS_MAX_AGE) }?.let { return it.value } }
        val season = seasons(key).currentRegularSeason(LocalDate.now(clock)) ?: return emptyList()
        val result = api.getTeams(key, season.id).data.orEmpty()
            .filter { !it.hidden && it.division?.title?.equals("GUEST", ignoreCase = true) != true }
            .map { it.toTeam() }
            .sortedBy { it.name }
        mutex.withLock { teams = Cached(result, key) }
        return result
    }

    /** Checks [key] against the gateway without saving or caching anything. */
    suspend fun checkKey(key: String): GamesheetKeyCheck = try {
        val response = api.getMyClub(key.trim(), clubNid)
        if (response.status == "success") GamesheetKeyCheck.VALID else GamesheetKeyCheck.REJECTED
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpException) {
        if (e.code() == 401 || e.code() == 403) GamesheetKeyCheck.REJECTED else GamesheetKeyCheck.FAILED
    } catch (e: Exception) {
        GamesheetKeyCheck.FAILED
    }

    private suspend fun seasons(key: String): List<GamesheetSeasonDto> {
        mutex.withLock { seasons?.takeIf { it.isFreshFor(key, SEASONS_MAX_AGE) }?.let { return it.value } }
        val result = api.getMyClub(key, clubNid).data?.seasons.orEmpty()
        mutex.withLock { seasons = Cached(result, key) }
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
