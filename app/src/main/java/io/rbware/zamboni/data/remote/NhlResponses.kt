package io.rbware.zamboni.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/*
 * Responses of the NHL web API ([NhlApi]). Field names match the JSON exactly. Only the fields
 * the app uses are modeled; the rest are ignored when parsing. Game states, schedule states,
 * periods and clocks use the same values as hockeynerd.com, so [PeriodDto] and [ClockDto] are
 * shared.
 */

/** A translated string, e.g. `{"default": "Kraken", "fr": "..."}`. Only the default is kept. */
@Serializable
data class NhlText(val default: String)

/** Response of `GET /v1/score/{date}`. */
@Serializable
data class NhlScoreResponse(
    /** The game day these games belong to, `YYYY-MM-DD`. */
    val currentDate: String,
    val prevDate: String? = null,
    val nextDate: String? = null,
    val games: List<NhlScoreGameDto> = emptyList(),
)

@Serializable
data class NhlScoreGameDto(
    val id: Long,
    /** 1 = preseason, 2 = regular season, 3 = playoffs. */
    val gameType: Int? = null,
    val gameState: String,
    val gameScheduleState: String? = null,
    val startTimeUTC: String,
    val venue: NhlText? = null,
    val periodDescriptor: PeriodDto? = null,
    val clock: ClockDto? = null,
    val awayTeam: NhlScoreTeamDto,
    val homeTeam: NhlScoreTeamDto,
)

@Serializable
data class NhlScoreTeamDto(
    val abbrev: String,
    /** Nickname only, e.g. "Kraken". */
    val name: NhlText,
    /** Missing before the game starts. */
    val score: Int? = null,
    val sog: Int? = null,
)

/** Response of `GET /v1/gamecenter/{id}/landing`. */
@Serializable
data class NhlLandingResponse(
    val id: Long,
    val gameState: String,
    val gameScheduleState: String? = null,
    val startTimeUTC: String,
    val venue: NhlText? = null,
    val periodDescriptor: PeriodDto? = null,
    val clock: ClockDto? = null,
    val awayTeam: NhlLandingTeamDto,
    val homeTeam: NhlLandingTeamDto,
    /** Missing before the game starts. */
    val summary: NhlSummaryDto? = null,
    val tvBroadcasts: List<NhlBroadcastDto> = emptyList(),
)

/** One TV or streaming broadcast. */
@Serializable
data class NhlBroadcastDto(
    /** Also names the logo: assets.nhle.com/logos/broadcast/{id}-light.svg (not every network has one). */
    val id: Long,
    /** "N" national, "H" home team's local, "A" away team's local. */
    val market: String,
    val countryCode: String? = null,
    /** Short name, e.g. "SN1", "NHLN", "Prime Video". */
    val network: String,
)

@Serializable
data class NhlLandingTeamDto(
    val abbrev: String,
    /** e.g. "Kraken". */
    val commonName: NhlText,
    /** e.g. "Seattle". */
    val placeName: NhlText? = null,
    val score: Int? = null,
    val sog: Int? = null,
    /** The team's radio broadcast as an HLS stream (.m3u8). */
    val radioLink: String? = null,
)

@Serializable
data class NhlSummaryDto(
    /** One entry per period played, including OT and the shootout. */
    val scoring: List<NhlScoringPeriodDto> = emptyList(),
)

@Serializable
data class NhlScoringPeriodDto(
    val periodDescriptor: PeriodDto,
    val goals: List<NhlGoalDto> = emptyList(),
)

@Serializable
data class NhlGoalDto(
    val eventId: Long,
    /**
     * Four digits: away goalie in net (0/1), away skaters, home skaters, home goalie in net.
     * "1551" is 5-on-5 with both goalies in.
     */
    val situationCode: String? = null,
    /** "ev", "pp" or "sh". */
    val strength: String? = null,
    val playerId: Long,
    val firstName: NhlText? = null,
    val lastName: NhlText? = null,
    /** Abbreviated, e.g. "J. Blake". */
    val name: NhlText,
    val headshot: String? = null,
    val goalsToDate: Int? = null,
    /** Score after this goal. */
    val awayScore: Int,
    val homeScore: Int,
    /** Elapsed time in the period, "MM:SS". */
    val timeInPeriod: String,
    val isHome: Boolean,
    val assists: List<NhlAssistDto> = emptyList(),
)

@Serializable
data class NhlAssistDto(
    val playerId: Long,
    val name: NhlText,
    val assistsToDate: Int? = null,
    val sweaterNumber: Int? = null,
)

/** Response of `GET /v1/gamecenter/{id}/right-rail`. */
@Serializable
data class NhlRightRailResponse(
    /** Missing before the game starts. */
    val teamGameStats: List<NhlTeamGameStatDto> = emptyList(),
)

@Serializable
data class NhlTeamGameStatDto(
    /** Same keys as [io.rbware.zamboni.data.model.TeamStatType.key], e.g. "sog". */
    val category: String,
    /** A number, or a pre-formatted string such as "3/5". */
    val awayValue: JsonElement,
    val homeValue: JsonElement,
)

/** Response of `GET /v1/standings/{date}`. */
@Serializable
data class NhlStandingsResponse(
    val standings: List<NhlStandingDto> = emptyList(),
)

@Serializable
data class NhlStandingDto(
    val teamAbbrev: NhlText,
    val wins: Int,
    val losses: Int,
    val otLosses: Int,
)
