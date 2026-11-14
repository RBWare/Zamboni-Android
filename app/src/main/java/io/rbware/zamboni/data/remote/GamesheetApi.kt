package io.rbware.zamboni.data.remote

import kotlinx.serialization.json.Json
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * The Gamesheet stats gateway (gateway.gamesheet.io), used for NAHL data. Every request needs
 * the partner key in [API_KEY_HEADER]; the client in AppContainer adds it.
 *
 * Responses are wrapped as `{"status": "success", "data": ...}`. `data` is null rather than an
 * empty list when nothing matches.
 */
interface GamesheetApi {

    /** One club's details and every season it has played in. [nid] is e.g. "4-605". */
    @GET("stats/myclub")
    suspend fun getMyClub(@Query("filter[nid]") nid: String): GamesheetResponse<GamesheetMyClubDto>

    /**
     * Games of one season with scores, goal scorers and team stats: the data behind the game
     * center on nahl.com. Only one season per request.
     *
     * @param start first game day to return, `YYYY-MM-DD` (local to the rink).
     * @param end day after the last one (exclusive). With [start], returns a single day.
     * @param gameId returns only this game.
     * @param teamId returns only this (season-specific) team's games.
     */
    @GET("stats/unifiedschedule")
    suspend fun getUnifiedSchedule(
        @Query("filter[seasons]") seasonId: Long,
        @Query("filter[start]") start: String? = null,
        @Query("filter[end]") end: String? = null,
        @Query("filter[games]") gameId: Long? = null,
        @Query("filter[teams]") teamId: Long? = null,
    ): GamesheetResponse<List<GamesheetUnifiedGameDto>>

    /** Teams in one season. */
    @GET("stats/teams")
    suspend fun getTeams(@Query("filter[seasons]") seasonId: Long): GamesheetResponse<List<GamesheetTeamDto>>

    companion object {
        const val BASE_URL = "https://gateway.gamesheet.io/"
        const val API_KEY_HEADER = "X-Gamesheet-Partner-ApiKey"

        /** Lenient so new fields added to the API don't break parsing. */
        val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            explicitNulls = false
        }
    }
}
