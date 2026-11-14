package io.rbware.zamboni.data.scores

import io.rbware.zamboni.data.leagues.Leagues
import io.rbware.zamboni.data.model.Game
import io.rbware.zamboni.data.model.GameDetails
import io.rbware.zamboni.data.model.Goal
import io.rbware.zamboni.data.model.GoalPlayer
import io.rbware.zamboni.data.model.GoalStrength
import io.rbware.zamboni.data.model.StatValue
import io.rbware.zamboni.data.model.Team
import io.rbware.zamboni.data.model.TeamColors
import io.rbware.zamboni.data.model.TeamSide
import io.rbware.zamboni.data.model.TeamStat
import io.rbware.zamboni.data.model.TeamStatType
import io.rbware.zamboni.data.remote.GameDetailResponse
import io.rbware.zamboni.data.remote.GoalDto
import io.rbware.zamboni.data.remote.GoalPlayerDto
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import java.time.Instant
import kotlin.time.Duration.Companion.milliseconds

/** Converts `/api/game/{id}` into app models. See [toSchedule] for [knownTeams]. */
fun GameDetailResponse.toGameDetails(knownTeams: Map<String, Team>): GameDetails {
    val status = gameStatus(game.gameState, game.gameScheduleState, game.period, game.clock)
    return GameDetails(
        game = Game(
            id = nhlGameId(game.id),
            league = Leagues.NHL,
            startTime = Instant.parse(game.startTimeUTC),
            status = status,
            away = resolveNhlTeam(
                game.away.abbrev, game.away.name, knownTeams,
                TeamColors.fromHex(game.away.primary, game.away.secondary),
            ),
            home = resolveNhlTeam(
                game.home.abbrev, game.home.name, knownTeams,
                TeamColors.fromHex(game.home.primary, game.home.secondary),
            ),
            awayScore = scoreFor(status, game.away.score),
            homeScore = scoreFor(status, game.home.score),
            venue = game.venue,
        ),
        goals = game.goals.mapNotNull { it.toGoal() },
        teamStats = parseTeamStats(game.stats),
        suggestedRefresh = refreshMs?.milliseconds,
    )
}

private fun GoalDto.toGoal(): Goal? {
    val side = when (side) {
        "away" -> TeamSide.AWAY
        "home" -> TeamSide.HOME
        else -> return null
    }
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
        emptyNet = emptyNet,
        scorer = scorer.toPlayer(),
        assists = assists.map { it.toPlayer() },
    )
}

private fun GoalPlayerDto.toPlayer() = GoalPlayer(
    id = id,
    name = name,
    number = number,
    headshotUrl = headshot,
    seasonTotal = total,
)

/** Picks out the stats in [TeamStatType] order, skipping any that are missing or malformed. */
private fun parseTeamStats(stats: JsonObject): List<TeamStat> = TeamStatType.entries.mapNotNull { type ->
    val pair = stats[type.key] as? JsonObject ?: return@mapNotNull null
    val away = pair["away"]?.toStatValue() ?: return@mapNotNull null
    val home = pair["home"]?.toStatValue() ?: return@mapNotNull null
    TeamStat(type, away, home)
}

internal fun JsonElement.toStatValue(): StatValue? {
    val primitive = this as? JsonPrimitive ?: return null
    return if (primitive.isString) {
        StatValue.Text(primitive.content)
    } else {
        primitive.doubleOrNull?.let { StatValue.Number(it) }
    }
}
