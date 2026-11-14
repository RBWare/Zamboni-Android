package io.rbware.zamboni.data.scores

import android.util.Log
import io.rbware.zamboni.data.leagues.Leagues
import io.rbware.zamboni.data.model.GameDetails
import io.rbware.zamboni.data.model.League
import io.rbware.zamboni.data.model.Schedule
import io.rbware.zamboni.data.remote.NhlApi
import io.rbware.zamboni.data.teams.TeamRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import kotlin.time.Duration.Companion.minutes
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
        val score = api.getScore(date.toString())
        score.toSchedule(knownTeams, records.await())
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
        val landing = try {
            api.getLanding(apiGameId)
        } catch (e: HttpException) {
            if (e.code() == 404) throw GameNotFoundException(gameId) else throw e
        }
        landing.toGameDetails(rightRail.await(), knownTeams)
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
