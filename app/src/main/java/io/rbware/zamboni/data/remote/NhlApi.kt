package io.rbware.zamboni.data.remote

import kotlinx.serialization.json.Json
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * The public NHL web API (api-web.nhle.com). No key needed. Only the endpoints the app uses
 * are declared here.
 */
interface NhlApi {

    /**
     * Games for one game day, with goals. Days without games (e.g. the off-season) return an
     * empty list rather than an error.
     *
     * @param date `YYYY-MM-DD` (North American game day), or "now".
     */
    @GET("v1/score/{date}")
    suspend fun getScore(@Path("date") date: String): NhlScoreResponse

    /** Header, goals and three stars for one game. Responds 404 for unknown IDs. */
    @GET("v1/gamecenter/{gameId}/landing")
    suspend fun getLanding(@Path("gameId") gameId: Long): NhlLandingResponse

    /** Team stats comparison (once the game has started) and other sidebar data. */
    @GET("v1/gamecenter/{gameId}/right-rail")
    suspend fun getRightRail(@Path("gameId") gameId: Long): NhlRightRailResponse

    /**
     * League standings at the end of [date] (`YYYY-MM-DD`), or "now". Dates in the future return
     * an empty list.
     */
    @GET("v1/standings/{date}")
    suspend fun getStandings(@Path("date") date: String): NhlStandingsResponse

    companion object {
        const val BASE_URL = "https://api-web.nhle.com/"

        /** Lenient so new fields added to the API don't break parsing. */
        val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }
    }
}
