package io.rbware.zamboni.data.scores

import io.rbware.zamboni.data.leagues.Leagues
import io.rbware.zamboni.data.logos.NhlLogos
import io.rbware.zamboni.data.logos.NhlTeamColors
import io.rbware.zamboni.data.model.Decision
import io.rbware.zamboni.data.model.Game
import io.rbware.zamboni.data.model.GameStatus
import io.rbware.zamboni.data.model.Schedule
import io.rbware.zamboni.data.model.Team
import io.rbware.zamboni.data.model.TeamColors
import io.rbware.zamboni.data.remote.ClockDto
import io.rbware.zamboni.data.remote.GameDto
import io.rbware.zamboni.data.remote.PeriodDto
import io.rbware.zamboni.data.remote.ScheduleResponse
import java.time.Instant
import java.time.LocalDate
import kotlin.time.Duration.Companion.milliseconds

private const val NHL_GAME_ID_PREFIX = "nhl-"

/** Team ID used across the app for an NHL team, e.g. "nhl-sea". Must match stored favorites. */
fun nhlTeamId(abbrev: String): String = "${Leagues.NHL.id}-${abbrev.lowercase()}"

/** App-wide game ID for an NHL game, e.g. "nhl-2026020311". */
fun nhlGameId(apiGameId: Long): String = "$NHL_GAME_ID_PREFIX$apiGameId"

/** The NHL game ID (also used by hockeynerd.com) behind an app game ID, or null if it isn't an NHL game ID. */
fun apiGameIdOrNull(gameId: String): Long? =
    gameId.takeIf { it.startsWith(NHL_GAME_ID_PREFIX) }?.removePrefix(NHL_GAME_ID_PREFIX)?.toLongOrNull()

/**
 * Converts the API response into app models.
 *
 * @param knownTeams teams by ID, used for full names ("Seattle Kraken" rather than the API's
 *   "Kraken"). Teams not found here are built from the API data instead.
 */
fun ScheduleResponse.toSchedule(knownTeams: Map<String, Team>): Schedule = Schedule(
    date = LocalDate.parse(date),
    games = games.map { it.toGame(knownTeams) },
    suggestedRefresh = refreshMs?.milliseconds,
)

internal fun GameDto.toGame(knownTeams: Map<String, Team>): Game {
    val status = gameStatus(gameState, gameScheduleState, period, clock)
    return Game(
        id = nhlGameId(id),
        league = Leagues.NHL,
        startTime = Instant.parse(startTimeUTC),
        status = status,
        away = resolveNhlTeam(away.abbrev, away.name, knownTeams, TeamColors.fromHex(away.primary)),
        home = resolveNhlTeam(home.abbrev, home.name, knownTeams, TeamColors.fromHex(home.primary)),
        awayScore = scoreFor(status, away.score),
        homeScore = scoreFor(status, home.score),
        venue = venue,
        awayRecord = away.record,
        homeRecord = home.record,
    )
}

/**
 * The known team for [abbrev] (with [colors] from the API applied), or one built from API data
 * if the app doesn't know it.
 */
internal fun resolveNhlTeam(
    abbrev: String,
    apiName: String,
    knownTeams: Map<String, Team>,
    colors: TeamColors? = null,
): Team {
    val id = nhlTeamId(abbrev)
    val known = knownTeams[id]
    return known?.copy(colors = colors ?: known.colors) ?: Team(
        id = id,
        name = apiName,
        abbreviation = abbrev,
        league = Leagues.NHL,
        logo = NhlLogos.forTeam(abbrev),
        colors = colors ?: NhlTeamColors.forTeam(abbrev),
    )
}

/** Scores are only meaningful once the game is under way; the API sends 0 or null before. */
internal fun scoreFor(status: GameStatus, score: Int?): Int? =
    if (status is GameStatus.Live || status is GameStatus.Final) score ?: 0 else null

/** "09:08" -> "9:08". */
internal fun trimClock(time: String): String = time.removePrefix("0")

internal fun gameStatus(
    gameState: String,
    gameScheduleState: String?,
    period: PeriodDto?,
    clock: ClockDto?,
): GameStatus {
    when (gameScheduleState) {
        "PPD", "SUSP" -> return GameStatus.Postponed
        "CNCL" -> return GameStatus.Cancelled
    }
    return when (gameState) {
        "LIVE", "CRIT" -> GameStatus.Live(
            period = period?.number ?: 1,
            clock = clock?.timeRemaining?.let(::trimClock)?.takeUnless { clock.inIntermission },
            intermission = clock?.inIntermission == true,
            shootout = period?.periodType == "SO",
            secondsRemaining = clock?.secondsRemaining?.takeUnless { clock.inIntermission },
            clockRunning = clock?.running == true && !clock.inIntermission,
        )
        "OFF", "FINAL" -> GameStatus.Final(
            when (period?.periodType) {
                "OT" -> Decision.OVERTIME
                "SO" -> Decision.SHOOTOUT
                else -> Decision.REGULATION
            },
        )
        else -> GameStatus.Scheduled
    }
}
