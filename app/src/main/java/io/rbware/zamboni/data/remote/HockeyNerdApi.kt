package io.rbware.zamboni.data.remote

import kotlinx.serialization.json.Json
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/** The hockeynerd.com REST API. Endpoints are added here as they are integrated. */
interface HockeyNerdApi {

    /**
     * NHL games for one game day.
     *
     * @param date game day as `YYYY-MM-DD` (North American game day, not UTC). Null, or an
     *   invalid value, returns today's games.
     */
    @GET("api/schedule")
    suspend fun getSchedule(@Query("date") date: String? = null): ScheduleResponse

    /**
     * Full details for one game: goals, team stats, live clock. Responds 404 for unknown IDs.
     *
     * @param gameId the NHL game ID, as in [GameDto.id].
     */
    @GET("api/game/{gameId}")
    suspend fun getGame(@Path("gameId") gameId: Long): GameDetailResponse

    companion object {
        const val BASE_URL = "https://www.hockeynerd.com/"

        /** Lenient so new fields added to the API don't break parsing. */
        val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }
    }
}
