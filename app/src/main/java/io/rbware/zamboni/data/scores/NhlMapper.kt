package io.rbware.zamboni.data.scores

import io.rbware.zamboni.data.leagues.Leagues
import io.rbware.zamboni.data.model.Broadcast
import io.rbware.zamboni.data.model.BroadcastMarket
import io.rbware.zamboni.data.model.Game
import io.rbware.zamboni.data.model.GameDetails
import io.rbware.zamboni.data.model.GameStatus
import io.rbware.zamboni.data.model.Goal
import io.rbware.zamboni.data.model.GoalPlayer
import io.rbware.zamboni.data.model.GoalStrength
import io.rbware.zamboni.data.model.RadioStream
import io.rbware.zamboni.data.model.Schedule
import io.rbware.zamboni.data.model.Team
import io.rbware.zamboni.data.model.TeamLogo
import io.rbware.zamboni.data.model.TeamSide
import io.rbware.zamboni.data.model.TeamStat
import io.rbware.zamboni.data.model.TeamStatType
import io.rbware.zamboni.data.remote.NhlAssistDto
import io.rbware.zamboni.data.remote.NhlBroadcastDto
import io.rbware.zamboni.data.remote.NhlGoalDto
import io.rbware.zamboni.data.remote.NhlLandingResponse
import io.rbware.zamboni.data.remote.NhlRightRailResponse
import io.rbware.zamboni.data.remote.NhlScoreGameDto
import io.rbware.zamboni.data.remote.NhlScoreResponse
import io.rbware.zamboni.data.remote.NhlStandingsResponse
import io.rbware.zamboni.data.remote.PeriodDto
import java.time.Instant
import java.time.LocalDate
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/*
 * Converts NHL web API responses into app models. Game IDs are the NHL's own, the same ones
 * hockeynerd.com uses, so either source can serve a game the other listed.
 */

/** The NHL API gives no polling hint, so these stand in for one. Its CDN caches for ~20s. */
internal val NHL_LIVE_SCHEDULE_REFRESH: Duration = 20.seconds
internal val NHL_LIVE_GAME_REFRESH: Duration = 10.seconds
internal val NHL_IDLE_REFRESH: Duration = 60.seconds

/**
 * Season records ("W-L-OTL") by team abbreviation.
 */
fun NhlStandingsResponse.toRecords(): Map<String, String> =
    standings.associate { it.teamAbbrev.default to "${it.wins}-${it.losses}-${it.otLosses}" }

/**
 * @param knownTeams see [toSchedule] in ScheduleMapper.
 * @param records season records by team abbreviation, from [toRecords]. May be empty.
 */
fun NhlScoreResponse.toSchedule(knownTeams: Map<String, Team>, records: Map<String, String>): Schedule {
    val games = games.map { it.toGame(knownTeams, records) }
    return Schedule(
        date = LocalDate.parse(currentDate),
        games = games,
        suggestedRefresh = if (games.any { it.status is GameStatus.Live }) {
            NHL_LIVE_SCHEDULE_REFRESH
        } else {
            NHL_IDLE_REFRESH
        },
    )
}

private fun NhlScoreGameDto.toGame(knownTeams: Map<String, Team>, records: Map<String, String>): Game {
    val status = gameStatus(gameState, gameScheduleState, periodDescriptor, clock)
    return Game(
        id = nhlGameId(id),
        league = Leagues.NHL,
        startTime = Instant.parse(startTimeUTC),
        status = status,
        away = resolveNhlTeam(awayTeam.abbrev, awayTeam.name.default, knownTeams),
        home = resolveNhlTeam(homeTeam.abbrev, homeTeam.name.default, knownTeams),
        awayScore = scoreFor(status, awayTeam.score),
        homeScore = scoreFor(status, homeTeam.score),
        venue = venue?.default,
        awayRecord = records[awayTeam.abbrev],
        homeRecord = records[homeTeam.abbrev],
    )
}

/**
 * Combines the landing and right-rail responses. [rightRail] may be null if it failed to load;
 * the details are then shown without team stats.
 */
fun NhlLandingResponse.toGameDetails(
    rightRail: NhlRightRailResponse?,
    knownTeams: Map<String, Team>,
): GameDetails {
    val status = gameStatus(gameState, gameScheduleState, periodDescriptor, clock)
    val awayName = listOfNotNull(awayTeam.placeName?.default, awayTeam.commonName.default).joinToString(" ")
    val homeName = listOfNotNull(homeTeam.placeName?.default, homeTeam.commonName.default).joinToString(" ")
    return GameDetails(
        game = Game(
            id = nhlGameId(id),
            league = Leagues.NHL,
            startTime = Instant.parse(startTimeUTC),
            status = status,
            away = resolveNhlTeam(awayTeam.abbrev, awayName, knownTeams),
            home = resolveNhlTeam(homeTeam.abbrev, homeName, knownTeams),
            awayScore = scoreFor(status, awayTeam.score),
            homeScore = scoreFor(status, homeTeam.score),
            venue = venue?.default,
        ),
        goals = summary?.scoring.orEmpty()
            // The shootout "goal" is only the deciding attempt; it isn't a real goal.
            .filter { it.periodDescriptor.periodType != "SO" }
            .flatMap { period -> period.goals.map { it.toGoal(period.periodDescriptor) } },
        teamStats = rightRail?.toTeamStats().orEmpty(),
        suggestedRefresh = if (status is GameStatus.Live) NHL_LIVE_GAME_REFRESH else NHL_IDLE_REFRESH,
        broadcasts = tvBroadcasts.mapNotNull { it.toBroadcast() },
        radio = listOfNotNull(
            awayTeam.radioLink?.takeIf { it.startsWith("https://") }?.let { RadioStream(TeamSide.AWAY, it) },
            homeTeam.radioLink?.takeIf { it.startsWith("https://") }?.let { RadioStream(TeamSide.HOME, it) },
        ),
    )
}

private fun NhlBroadcastDto.toBroadcast(): Broadcast? {
    val market = when (market) {
        "N" -> BroadcastMarket.NATIONAL
        "H" -> BroadcastMarket.HOME
        "A" -> BroadcastMarket.AWAY
        else -> return null
    }
    val logos = "https://assets.nhle.com/logos/broadcast/$id"
    return Broadcast(
        network = network.trim(),
        market = market,
        countryCode = countryCode,
        logo = TeamLogo(light = "$logos-light.svg", dark = "$logos-dark.svg"),
    )
}

private fun NhlGoalDto.toGoal(period: PeriodDto): Goal {
    val side = if (isHome) TeamSide.HOME else TeamSide.AWAY
    val fullName = listOfNotNull(firstName?.default, lastName?.default)
        .joinToString(" ")
        .ifBlank { name.default }
    return Goal(
        id = eventId,
        side = side,
        period = period.number,
        isOvertime = period.periodType == "OT",
        timeInPeriod = trimClock(timeInPeriod),
        awayScore = awayScore,
        homeScore = homeScore,
        strength = when (strength) {
            "pp" -> GoalStrength.POWER_PLAY
            "sh" -> GoalStrength.SHORTHANDED
            else -> GoalStrength.EVEN
        },
        emptyNet = isEmptyNet(situationCode, side),
        scorer = GoalPlayer(
            id = playerId,
            name = fullName,
            number = null,
            headshotUrl = headshot,
            seasonTotal = goalsToDate,
        ),
        assists = assists.map { it.toPlayer() },
    )
}

private fun NhlAssistDto.toPlayer() = GoalPlayer(
    id = playerId,
    name = name.default,
    number = sweaterNumber,
    headshotUrl = null,
    seasonTotal = assistsToDate,
)

/** True if the other team's goalie was out of the net, per the situation code digits. */
internal fun isEmptyNet(situationCode: String?, scoringSide: TeamSide): Boolean {
    if (situationCode?.length != 4) return false
    val opposingGoalie = if (scoringSide == TeamSide.HOME) situationCode[0] else situationCode[3]
    return opposingGoalie == '0'
}

/** Picks out the stats in [TeamStatType] order, skipping any that are missing or malformed. */
private fun NhlRightRailResponse.toTeamStats(): List<TeamStat> {
    val byKey = teamGameStats.associateBy { it.category }
    return TeamStatType.entries.mapNotNull { type ->
        val stat = byKey[type.key] ?: return@mapNotNull null
        val away = stat.awayValue.toStatValue() ?: return@mapNotNull null
        val home = stat.homeValue.toStatValue() ?: return@mapNotNull null
        TeamStat(type, away, home)
    }
}
