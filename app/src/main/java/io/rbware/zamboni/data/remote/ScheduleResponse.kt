package io.rbware.zamboni.data.remote

import kotlinx.serialization.Serializable

/** Response of `GET /api/schedule`. Field names match the JSON exactly. */
@Serializable
data class ScheduleResponse(
    /** The game day these games belong to, `YYYY-MM-DD`. */
    val date: String,
    val today: String? = null,
    val prevDate: String? = null,
    val nextDate: String? = null,
    val games: List<GameDto> = emptyList(),
    /** Suggested polling interval: roughly 10s while games are live, 60s otherwise. */
    val refreshMs: Long? = null,
)

@Serializable
data class GameDto(
    val id: Long,
    /** 1 = preseason, 2 = regular season, 3 = playoffs. */
    val gameType: Int? = null,
    /** FUT / PRE = not started, LIVE / CRIT = in progress, OFF / FINAL = finished. */
    val gameState: String,
    /** OK, or PPD (postponed), SUSP (suspended), CNCL (cancelled). */
    val gameScheduleState: String? = null,
    val startTimeUTC: String,
    val venue: String? = null,
    val period: PeriodDto? = null,
    val clock: ClockDto? = null,
    val away: GameTeamDto,
    val home: GameTeamDto,
)

@Serializable
data class PeriodDto(
    val number: Int,
    /** REG, OT or SO. */
    val periodType: String? = null,
    val maxRegulationPeriods: Int? = null,
)

@Serializable
data class ClockDto(
    /** "MM:SS", e.g. "09:08". */
    val timeRemaining: String? = null,
    val secondsRemaining: Int? = null,
    val running: Boolean = false,
    val inIntermission: Boolean = false,
)

@Serializable
data class GameTeamDto(
    /** NHL three-letter code, e.g. "SEA". */
    val abbrev: String,
    /** Nickname only, e.g. "Kraken". */
    val name: String,
    val logo: String? = null,
    /** Primary team color as hex, e.g. "#001628". */
    val primary: String? = null,
    /** Null before the game starts. */
    val score: Int? = null,
    /** Wins-losses-OT losses, e.g. "9-7-1". */
    val record: String? = null,
    val goalsPerGame: Double? = null,
    val powerPlayPct: Double? = null,
    val penaltyKillPct: Double? = null,
)
