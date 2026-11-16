package io.rbware.zamboni.data.scores

import io.rbware.zamboni.data.leagues.Leagues
import io.rbware.zamboni.data.model.Game
import io.rbware.zamboni.data.model.GameDetails
import io.rbware.zamboni.data.model.GameStatus
import io.rbware.zamboni.data.model.League
import io.rbware.zamboni.data.model.Schedule
import io.rbware.zamboni.data.model.Team
import io.rbware.zamboni.data.remote.HockeyTechApi
import io.rbware.zamboni.data.remote.HockeyTechClockDto
import io.rbware.zamboni.data.remote.HockeyTechClockResponse
import io.rbware.zamboni.data.remote.HockeyTechGameSummaryDto
import io.rbware.zamboni.data.remote.HockeyTechKeyRejectedException
import io.rbware.zamboni.data.remote.HockeyTechSeasonDto
import io.rbware.zamboni.data.remote.HockeyTechSiteKitResponse
import io.rbware.zamboni.data.remote.decodeHockeyTech
import io.rbware.zamboni.data.teams.TeamRepository
import kotlinx.coroutines.CancellationException
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
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * AHL scores, game details and teams from HockeyTech's feed (see [HockeyTechApi]).
 *
 * Days around today come from the scores bar, which is small and has live status. Other days
 * come from the season's full schedule, which is large, so it is kept for a while.
 *
 * Every call needs a key from [apiKey] (the AHL website's public one unless the user entered
 * another). Without one the AHL is left out: no league, no games and no teams.
 */
class HockeyTechAhlRepository(
    private val api: HockeyTechApi,
    private val apiKey: suspend () -> String?,
    private val clock: Clock = Clock.system(ZoneId.of("America/New_York")),
) : ScoresRepository, TeamRepository {

    private val mutex = Mutex()
    private var seasons: Cached<List<HockeyTechSeasonDto>>? = null
    private var teams: Cached<List<Team>>? = null
    /** Logo URLs by HockeyTech team ID, from the same team list. */
    private var teamLogos: Map<String, String> = emptyMap()
    private val schedules = mutableMapOf<String, Cached<List<DatedGame>>>()

    /** Cached data is only reused with the key it was fetched with. */
    private class Cached<T>(
        val value: T,
        val key: String,
        val at: TimeSource.Monotonic.ValueTimeMark = TimeSource.Monotonic.markNow(),
    ) {
        fun isFreshFor(key: String, maxAge: Duration) = this.key == key && at.elapsedNow() < maxAge
    }

    override suspend fun getLeagues(): List<League> = if (apiKey() != null) listOf(Leagues.AHL) else emptyList()

    override suspend fun getSchedule(date: LocalDate): Schedule {
        val key = apiKey() ?: return Schedule(date = date, games = emptyList())
        val today = LocalDate.now(clock)
        val games = if (date in today.minusDays(1)..today.plusDays(1)) {
            // One game day either way covers yesterday and tomorrow (or less, when those had no games).
            api.getScorebar(key, HockeyTechApi.AHL, daysBack = 1, daysAhead = 1)
                .decodeHockeyTech<HockeyTechSiteKitResponse>()
                .siteKit.scorebar.orEmpty()
                .filter { it.date == date.toString() }
                .map { it.toGame(logos(key)) }
        } else {
            seasonGames(key, date)
        }.sortedBy { it.startTime }
        return Schedule(date = date, games = games, suggestedRefresh = refreshFor(games.any { it.status is GameStatus.Live }))
    }

    override suspend fun getGameDetails(gameId: String): GameDetails = coroutineScope {
        val key = apiKey() ?: throw GameNotFoundException(gameId)
        val id = parseAhlGameId(gameId) ?: throw GameNotFoundException(gameId)
        val summary = async { api.getGameSummary(key, HockeyTechApi.AHL, id).decodeHockeyTech<HockeyTechGameSummaryDto>() }
        // The summary has no period or clock; without them a live game just shows as in progress.
        val liveClock = async {
            try {
                api.getClock(key, HockeyTechApi.AHL, id).decodeHockeyTech<HockeyTechClockResponse>().gc.clock
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null as HockeyTechClockDto?
            }
        }
        val details = summary.await().toGameDetails(liveClock.await(), suggestedRefresh = null)
        details.copy(suggestedRefresh = refreshFor(details.game.status is GameStatus.Live))
    }

    /** Teams in the current regular season, sorted by name. Empty without a key. */
    override suspend fun getTeams(): List<Team> {
        val key = apiKey() ?: return emptyList()
        mutex.withLock { teams?.takeIf { it.isFreshFor(key, TEAMS_MAX_AGE) }?.let { return it.value } }
        val season = seasons(key).currentRegularSeason(LocalDate.now(clock)) ?: return emptyList()
        val dtos = api.getTeams(key, HockeyTechApi.AHL, season.id)
            .decodeHockeyTech<HockeyTechSiteKitResponse>()
            .siteKit.teams.orEmpty()
        val result = dtos.map { it.toTeam() }.sortedBy { it.name }
        mutex.withLock {
            teams = Cached(result, key)
            teamLogos = dtos.mapNotNull { dto -> dto.logo?.let { dto.id to it } }.toMap()
        }
        return result
    }

    /**
     * Logo URLs by HockeyTech team ID. Some logo files carry a season ("373_94.png"), which the
     * schedule doesn't say; empty if the team list can't be loaded (logos are then guessed).
     */
    private suspend fun logos(key: String): Map<String, String> = try {
        getTeams()
        mutex.withLock { teamLogos }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        emptyMap()
    }

    /** Checks [key] against the feed without saving or caching anything. */
    suspend fun checkKey(key: String): ApiKeyCheck = try {
        val seasons = api.getSeasons(key.trim(), HockeyTechApi.AHL).decodeHockeyTech<HockeyTechSiteKitResponse>().siteKit.seasons
        if (seasons.isNullOrEmpty()) ApiKeyCheck.REJECTED else ApiKeyCheck.VALID
    } catch (e: CancellationException) {
        throw e
    } catch (e: HockeyTechKeyRejectedException) {
        ApiKeyCheck.REJECTED
    } catch (e: Exception) {
        ApiKeyCheck.FAILED
    }

    /** Games on [date] from the schedules of the seasons covering it. */
    private suspend fun seasonGames(key: String, date: LocalDate): List<Game> = coroutineScope {
        seasons(key).coveringDate(date).map { season ->
            async { seasonSchedule(key, season.id).filter { it.date == date } }
        }.awaitAll().flatten().map { it.game }
    }

    /** A schedule game and its game day (the local date at the rink). */
    private class DatedGame(val date: LocalDate, val game: Game)

    private suspend fun seasonSchedule(key: String, seasonId: String): List<DatedGame> {
        mutex.withLock { schedules[seasonId]?.takeIf { it.isFreshFor(key, SCHEDULE_MAX_AGE) }?.let { return it.value } }
        val logos = logos(key)
        val games = api.getSchedule(key, HockeyTechApi.AHL, seasonId)
            .decodeHockeyTech<HockeyTechSiteKitResponse>()
            .siteKit.schedule.orEmpty()
            .mapNotNull { dto ->
                val day = runCatching { LocalDate.parse(dto.date) }.getOrNull() ?: return@mapNotNull null
                DatedGame(day, dto.toGame(logos))
            }
        mutex.withLock { schedules[seasonId] = Cached(games, key) }
        return games
    }

    private suspend fun seasons(key: String): List<HockeyTechSeasonDto> {
        mutex.withLock { seasons?.takeIf { it.isFreshFor(key, SEASONS_MAX_AGE) }?.let { return it.value } }
        val result = api.getSeasons(key, HockeyTechApi.AHL).decodeHockeyTech<HockeyTechSiteKitResponse>().siteKit.seasons.orEmpty()
        mutex.withLock { seasons = Cached(result, key) }
        return result
    }

    private fun refreshFor(live: Boolean): Duration = if (live) LIVE_REFRESH else IDLE_REFRESH

    private companion object {
        val LIVE_REFRESH = 20.seconds
        val IDLE_REFRESH = 120.seconds
        val SEASONS_MAX_AGE = 12.hours
        val TEAMS_MAX_AGE = 12.hours
        /** Days away from today change rarely (a final score, a postponement). */
        val SCHEDULE_MAX_AGE = 30.minutes
    }
}
