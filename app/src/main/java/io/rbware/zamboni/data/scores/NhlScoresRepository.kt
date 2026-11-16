package io.rbware.zamboni.data.scores

import android.util.Log
import io.rbware.zamboni.data.leagues.Leagues
import io.rbware.zamboni.data.model.Game
import io.rbware.zamboni.data.model.GameDetails
import io.rbware.zamboni.data.model.GameStatus
import io.rbware.zamboni.data.model.League
import io.rbware.zamboni.data.model.Schedule
import io.rbware.zamboni.data.remote.NhlApi
import io.rbware.zamboni.data.teams.TeamRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Headers
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** Scores from the public NHL web API (api-web.nhle.com). */
class NhlScoresRepository(
    private val api: NhlApi,
    private val teamRepository: TeamRepository,
    private val clock: Clock = Clock.system(ZoneId.of("America/New_York")),
) : ScoresRepository {

    private val recordsMutex = Mutex()
    private val recordsCache = mutableMapOf<String, CachedRecords>()

    private class CachedRecords(val records: Map<String, String>, val fetchedAt: TimeSource.Monotonic.ValueTimeMark)

    override suspend fun getLeagues(): List<League> = listOf(Leagues.NHL)

    override suspend fun getSchedule(date: LocalDate): Schedule = coroutineScope {
        val records = async { recordsBefore(date) }
        val knownTeams = teamRepository.getTeams().associateBy { it.id }
        val response = api.getScore(date.toString())
        val freshness = NhlFreshness.of(response.headers(), clock.instant())
        val schedule = response.bodyOrThrow().toSchedule(knownTeams, records.await())
        schedule.copy(
            games = schedule.games.map { it.withClockAsOf(freshness.asOf) },
            suggestedRefresh = freshness.nextRefresh(schedule.suggestedRefresh, liveMax = NHL_LIVE_SCHEDULE_REFRESH),
        )
    }

    override suspend fun getGameDetails(gameId: String): GameDetails = coroutineScope {
        val apiGameId = apiGameIdOrNull(gameId) ?: throw GameNotFoundException(gameId)
        // Stats are a nice-to-have; the details still show without them.
        val rightRail = async {
            try {
                api.getRightRail(apiGameId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Team stats for $gameId failed: ${e.message}")
                null
            }
        }
        val knownTeams = teamRepository.getTeams().associateBy { it.id }
        val response = api.getLanding(apiGameId)
        if (response.code() == 404) throw GameNotFoundException(gameId)
        val freshness = NhlFreshness.of(response.headers(), clock.instant())
        val details = response.bodyOrThrow().toGameDetails(rightRail.await(), knownTeams)
        details.copy(
            game = details.game.withClockAsOf(freshness.asOf),
            suggestedRefresh = freshness.nextRefresh(details.suggestedRefresh, liveMax = NHL_LIVE_GAME_REFRESH),
        )
    }

    /**
     * Records going into [date]'s games: the standings at the end of the day before, or the
     * current standings for upcoming days. Cached, and empty if the standings fail to load.
     */
    private suspend fun recordsBefore(date: LocalDate): Map<String, String> {
        val dayBefore = date.minusDays(1)
        val key = if (dayBefore < LocalDate.now(clock)) dayBefore.toString() else "now"
        recordsMutex.withLock {
            val cached = recordsCache[key]
            // Past standings never change; current ones do as games finish.
            if (cached != null && (key != "now" || cached.fetchedAt.elapsedNow() < CURRENT_RECORDS_MAX_AGE)) {
                return cached.records
            }
        }
        val records = try {
            api.getStandings(key).toRecords()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Standings for $key failed: ${e.message}")
            return emptyMap()
        }
        recordsMutex.withLock { recordsCache[key] = CachedRecords(records, TimeSource.Monotonic.markNow()) }
        return records
    }

    private companion object {
        const val TAG = "NhlScoresRepository"
        val CURRENT_RECORDS_MAX_AGE = 10.minutes
    }
}

private fun <T> Response<T>.bodyOrThrow(): T {
    if (!isSuccessful) throw HttpException(this)
    return body() ?: throw IOException("Empty response from ${raw().request.url.host}")
}

private fun Game.withClockAsOf(asOf: Instant): Game {
    val live = status as? GameStatus.Live ?: return this
    return copy(status = live.copy(clockAsOf = asOf))
}

/**
 * How old an NHL API response is, from its CDN headers. The API sits behind a CDN that caches
 * each URL for `max-age` seconds (about 20 for scores, 10 for a game) and reports how long the
 * copy has been cached in `Age`. So the data was produced about `Age` seconds ago, and a fresh
 * copy appears `max-age - Age` seconds from now.
 */
internal data class NhlFreshness(val asOf: Instant, val freshIn: Duration?) {

    /**
     * When to poll next while games are live: just after the cached copy expires (capped at
     * [liveMax]), rather than at a fixed interval that may land just before it does.
     * [default] (from the mapper) is kept when no game is live or the headers say nothing.
     */
    fun nextRefresh(default: Duration?, liveMax: Duration): Duration? {
        if (default == null || default > liveMax || freshIn == null) return default
        return (freshIn + 1.seconds).coerceIn(MIN_REFRESH, liveMax)
    }

    companion object {
        private val MIN_REFRESH = 2.seconds
        private val MAX_AGE = Regex("""(?:^|,)\s*max-age=(\d+)""")

        fun of(headers: Headers, now: Instant): NhlFreshness {
            val age = headers["Age"]?.trim()?.toLongOrNull()?.coerceAtLeast(0) ?: 0
            val maxAge = headers["Cache-Control"]?.let { MAX_AGE.find(it)?.groupValues?.get(1)?.toLongOrNull() }
            return NhlFreshness(
                asOf = now.minusSeconds(age),
                freshIn = maxAge?.let { (it - age).coerceAtLeast(0).seconds },
            )
        }
    }
}
