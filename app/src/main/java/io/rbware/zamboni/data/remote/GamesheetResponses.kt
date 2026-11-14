package io.rbware.zamboni.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * Responses of the Gamesheet stats gateway ([GamesheetApi]). Field names match the JSON
 * exactly. Only the fields the app uses are modeled.
 */

@Serializable
data class GamesheetResponse<T>(
    val status: String,
    /** Null when nothing matched. */
    val data: T? = null,
)

/** `GET /stats/myclub`. */
@Serializable
data class GamesheetMyClubDto(
    val seasons: List<GamesheetSeasonDto> = emptyList(),
)

@Serializable
data class GamesheetSeasonDto(
    val id: Long,
    /** e.g. "2026-27 NAHL", "2026-27 Exhibition", "2026 Robertson Cup". */
    val title: String,
    /** `YYYY-MM-DD`. Seasons of different types overlap. */
    @SerialName("start_date") val startDate: String,
    @SerialName("end_date") val endDate: String,
    @SerialName("is_active") val isActive: Boolean = false,
)

/** One game in `GET /stats/unifiedschedule`. */
@Serializable
data class GamesheetUnifiedGameDto(
    val gameId: Long,
    /** "scheduled", "in_progress" or "final". */
    val status: String,
    /** e.g. "regular_season". */
    val gameType: String? = null,
    /** Start time in UTC, e.g. "2026-11-14T01:30:00Z". */
    val timeStampZulu: String,
    /** The rink's zone, e.g. "America/Chicago". */
    val timeZoneName: String? = null,
    val location: String? = null,
    val home: GamesheetUnifiedTeamDto,
    val visitor: GamesheetUnifiedTeamDto,
)

@Serializable
data class GamesheetUnifiedTeamDto(
    /** Season-specific team ID; the same team has a new ID each season. */
    val id: Long,
    /** Full name, e.g. "Lone Star Brahmas". */
    val title: String,
    /** e.g. "LON". */
    val abbr: String? = null,
    val logo: String? = null,
    /** Final score, shootout winner included. 0 before the game. */
    val goals: Int = 0,
    /**
     * Goals by period: "1"-"3", "ot_1" (and up), "so" (shootout goals scored), and "final".
     * OT and shootout keys only appear in games that needed them.
     */
    val goalsByPeriod: Map<String, Int> = emptyMap(),
    /** Null before the first goal. Shootout goals are listed with period "SO" and no time. */
    val goalDetails: List<GamesheetGoalDto>? = null,
    val shots: Int? = null,
    val ppOpportunities: Int? = null,
    val ppGoals: Int? = null,
    /** Current season record (not the record at game time), e.g. "14-3-0-1". */
    val overallRecord: String? = null,
)

@Serializable
data class GamesheetGoalDto(
    /** Scorer's player ID. */
    val id: Long,
    /** Upper case, e.g. "HUGO". */
    val firstName: String,
    val lastName: String,
    /** "1"-"3", "ot_1" and up, or "SO". */
    val period: String,
    /** Time left on the period clock when the goal was scored, "MM:SS". Empty in shootouts. */
    val clockTime: String,
)

/** One team in `GET /stats/teams`. */
@Serializable
data class GamesheetTeamDto(
    val id: Long,
    val title: String,
    val division: GamesheetDivisionDto? = null,
    val logo: String? = null,
    val hidden: Boolean = false,
)

@Serializable
data class GamesheetDivisionDto(
    val id: Long,
    /** e.g. "South Division", or "GUEST" for non-league opponents such as the USA NTDP. */
    val title: String,
)
