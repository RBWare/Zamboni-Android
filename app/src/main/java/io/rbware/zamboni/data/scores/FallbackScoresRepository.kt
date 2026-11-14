package io.rbware.zamboni.data.scores

import android.util.Log
import io.rbware.zamboni.data.model.GameDetails
import io.rbware.zamboni.data.model.League
import io.rbware.zamboni.data.model.Schedule
import kotlinx.coroutines.CancellationException
import java.time.LocalDate

/**
 * Uses [primary], and asks [fallback] whenever a call to it fails. If both fail, the primary's
 * error is thrown (with the fallback's attached as suppressed).
 *
 * Both sources must use the same game and team IDs, since a game listed by one may be opened
 * through the other.
 */
class FallbackScoresRepository(
    private val primary: ScoresRepository,
    private val fallback: ScoresRepository,
    private val onFallback: (operation: String, error: Exception) -> Unit = { operation, error ->
        Log.w(TAG, "$operation failed on the primary source, using the fallback: $error")
    },
) : ScoresRepository {

    override suspend fun getLeagues(): List<League> =
        withFallback("getLeagues") { getLeagues() }

    override suspend fun getSchedule(date: LocalDate): Schedule =
        withFallback("getSchedule($date)") { getSchedule(date) }

    override suspend fun getGameDetails(gameId: String): GameDetails =
        withFallback("getGameDetails($gameId)") { getGameDetails(gameId) }

    private suspend fun <T> withFallback(operation: String, call: suspend ScoresRepository.() -> T): T {
        val primaryError = try {
            return primary.call()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e
        }
        onFallback(operation, primaryError)
        return try {
            fallback.call()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Prefer "not found" if either source says so, so the UI shows the right message.
            val thrown = if (e is GameNotFoundException && primaryError !is GameNotFoundException) e else primaryError
            thrown.addSuppressed(if (thrown === e) primaryError else e)
            throw thrown
        }
    }

    private companion object {
        const val TAG = "FallbackScores"
    }
}
