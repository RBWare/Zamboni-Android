package io.rbware.zamboni.data.remote

import kotlinx.serialization.json.Json
import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * HockeyTech's LeagueStat feed (lscluster.hockeytech.com), the data behind theahl.com's and
 * thesphl.com's scores and game centers. Every request needs a key and a league's client code
 * ("ahl", "sphl"; see HockeyTechLeague); the key is passed per call because the user can change
 * it in Settings.
 *
 * Responses are returned raw because the feed doesn't always send JSON: a wrong key gets HTTP
 * 200 with the text "Invalid key.", and the game summary comes wrapped in parentheses. See
 * [decodeHockeyTech].
 */
interface HockeyTechApi {

    /** Every season of the league, newest first. */
    @GET(PATH)
    suspend fun getSeasons(
        @Query("key") key: String,
        @Query("client_code") clientCode: String,
        @Query("feed") feed: String = "modulekit",
        @Query("view") view: String = "seasons",
        @Query("fmt") fmt: String = "json",
        @Query("lang") lang: String = "en",
    ): ResponseBody

    /**
     * Games around today with live status, as on the scores bar of theahl.com. The ranges count
     * game days (days with at least one game), not calendar days; today's games are always
     * included.
     */
    @GET(PATH)
    suspend fun getScorebar(
        @Query("key") key: String,
        @Query("client_code") clientCode: String,
        @Query("numberofdaysback") daysBack: Int,
        @Query("numberofdaysahead") daysAhead: Int,
        @Query("feed") feed: String = "modulekit",
        @Query("view") view: String = "scorebar",
        @Query("fmt") fmt: String = "json",
        @Query("lang") lang: String = "en",
    ): ResponseBody

    /** Every game of one season. Large (about 2 MB for a full AHL season, much less gzipped). */
    @GET(PATH)
    suspend fun getSchedule(
        @Query("key") key: String,
        @Query("client_code") clientCode: String,
        @Query("season_id") seasonId: String,
        @Query("feed") feed: String = "modulekit",
        @Query("view") view: String = "schedule",
        @Query("fmt") fmt: String = "json",
        @Query("lang") lang: String = "en",
    ): ResponseBody

    /** Teams of one season. */
    @GET(PATH)
    suspend fun getTeams(
        @Query("key") key: String,
        @Query("client_code") clientCode: String,
        @Query("season_id") seasonId: String,
        @Query("feed") feed: String = "modulekit",
        @Query("view") view: String = "teamsbyseason",
        @Query("fmt") fmt: String = "json",
        @Query("lang") lang: String = "en",
    ): ResponseBody

    /** One game's summary: goals, shots, power plays and penalties, by period. Not cached. */
    @GET(PATH)
    suspend fun getGameSummary(
        @Query("key") key: String,
        @Query("client_code") clientCode: String,
        @Query("game_id") gameId: String,
        @Query("feed") feed: String = "statviewfeed",
        @Query("view") view: String = "gameSummary",
        @Query("site_id") siteId: String = "0",
        @Query("fmt") fmt: String = "json",
        @Query("lang") lang: String = "en",
    ): ResponseBody

    /** One game's live state: period, clock and progress ("Final OT", "Intermission"...). */
    @GET(PATH)
    suspend fun getClock(
        @Query("key") key: String,
        @Query("client_code") clientCode: String,
        @Query("game_id") gameId: String,
        @Query("feed") feed: String = "gc",
        @Query("tab") tab: String = "clock",
        @Query("fmt") fmt: String = "json",
        @Query("lang") lang: String = "en",
    ): ResponseBody

    companion object {
        const val BASE_URL = "https://lscluster.hockeytech.com/"
        private const val PATH = "feed/index.php"

        /** Lenient so new fields added to the feed don't break parsing. */
        val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            explicitNulls = false
            isLenient = true
        }
    }
}

/** The feed turned the key down ("Invalid key." or "Client access denied."). */
class HockeyTechKeyRejectedException(message: String) : Exception(message)

/**
 * Parses a feed response. The game summary is wrapped in parentheses (JSONP without a callback
 * name), so those are removed first.
 *
 * @throws HockeyTechKeyRejectedException if the feed refused the key.
 */
inline fun <reified T> ResponseBody.decodeHockeyTech(): T = use {
    val text = it.string().trim()
    if (!text.startsWith("{") && !text.startsWith("(")) {
        val reply = text.take(80)
        if ("key" in reply.lowercase() || "denied" in reply.lowercase()) throw HockeyTechKeyRejectedException(reply)
        throw java.io.IOException("Unexpected HockeyTech reply: $reply")
    }
    val body = if (text.startsWith("(") && text.endsWith(")")) text.substring(1, text.length - 1) else text
    HockeyTechApi.json.decodeFromString<T>(body)
}
