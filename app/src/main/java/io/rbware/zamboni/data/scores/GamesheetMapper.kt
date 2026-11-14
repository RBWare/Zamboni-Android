package io.rbware.zamboni.data.scores

import io.rbware.zamboni.data.leagues.Leagues
import io.rbware.zamboni.data.model.Decision
import io.rbware.zamboni.data.model.Game
import io.rbware.zamboni.data.model.GameDetails
import io.rbware.zamboni.data.model.GameStatus
import io.rbware.zamboni.data.model.Goal
import io.rbware.zamboni.data.model.GoalPlayer
import io.rbware.zamboni.data.model.GoalStrength
import io.rbware.zamboni.data.model.StatValue
import io.rbware.zamboni.data.model.Team
import io.rbware.zamboni.data.model.TeamLogo
import io.rbware.zamboni.data.model.TeamSide
import io.rbware.zamboni.data.model.TeamStat
import io.rbware.zamboni.data.model.TeamStatType
import io.rbware.zamboni.data.remote.GamesheetGoalDto
import io.rbware.zamboni.data.remote.GamesheetSeasonDto
import io.rbware.zamboni.data.remote.GamesheetTeamDto
import io.rbware.zamboni.data.remote.GamesheetUnifiedGameDto
import io.rbware.zamboni.data.remote.GamesheetUnifiedTeamDto
import java.time.Instant
import java.time.LocalDate
import java.util.Locale
import kotlin.time.Duration

/*
 * Converts Gamesheet responses into app models for the NAHL.
 */

private const val NAHL_GAME_ID_PREFIX = "nahl-"

/**
 * App-wide team ID for an NAHL team, from its name: "Lone Star Brahmas" -> "nahl-lone-star-brahmas".
 * Gamesheet gives each team a new numeric ID every season, so the name is the stable key.
 */
fun nahlTeamId(title: String): String =
    "${Leagues.NAHL.id}-" + title.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

/** App-wide game ID, e.g. "nahl-15192-2909587". The season is needed to look the game up again. */
fun nahlGameId(seasonId: Long, gameId: Long): String = "$NAHL_GAME_ID_PREFIX$seasonId-$gameId"

fun isNahlGameId(gameId: String): Boolean = gameId.startsWith(NAHL_GAME_ID_PREFIX)

/** The Gamesheet season and game IDs behind an app game ID, or null if it isn't an NAHL one. */
fun parseNahlGameId(gameId: String): Pair<Long, Long>? {
    val parts = gameId.takeIf(::isNahlGameId)?.removePrefix(NAHL_GAME_ID_PREFIX)?.split('-') ?: return null
    if (parts.size != 2) return null
    val season = parts[0].toLongOrNull() ?: return null
    val game = parts[1].toLongOrNull() ?: return null
    return season to game
}

/**
 * The seasons whose games may fall on [date]: the regular season, exhibition and playoff
 * seasons overlap, so this can be more than one.
 */
fun List<GamesheetSeasonDto>.coveringDate(date: LocalDate): List<GamesheetSeasonDto> = filter {
    date in LocalDate.parse(it.startDate)..LocalDate.parse(it.endDate)
}

/** The current regular season (title ending in "NAHL"), for the team list. */
fun List<GamesheetSeasonDto>.currentRegularSeason(today: LocalDate): GamesheetSeasonDto? {
    val regular = filter { it.title.trim().endsWith("NAHL") }
    return regular.coveringDate(today).firstOrNull()
        ?: regular.firstOrNull { it.isActive }
        ?: regular.maxByOrNull { it.startDate }
}

fun GamesheetUnifiedGameDto.toGame(seasonId: Long): Game {
    val status = gamesheetStatus(this)
    val showScore = status is GameStatus.Final || (status is GameStatus.Live && hasLiveData())
    val upcoming = status !is GameStatus.Final
    return Game(
        id = nahlGameId(seasonId, gameId),
        league = Leagues.NAHL,
        startTime = Instant.parse(timeStampZulu),
        status = status,
        away = visitor.toTeam(),
        home = home.toTeam(),
        awayScore = if (showScore) visitor.goals else null,
        homeScore = if (showScore) home.goals else null,
        venue = location?.takeIf { it.isNotBlank() },
        // The API sends the current record, which is only right for games yet to be played.
        awayRecord = if (upcoming) visitor.overallRecord else null,
        homeRecord = if (upcoming) home.overallRecord else null,
    )
}

/**
 * Full details for one game. Gamesheet has no assists, strengths or player photos, so goals
 * only carry the scorer and time.
 */
fun GamesheetUnifiedGameDto.toGameDetails(seasonId: Long, suggestedRefresh: Duration?): GameDetails {
    val game = toGame(seasonId)
    val started = game.awayScore != null
    return GameDetails(
        game = game,
        goals = if (started) goals() else emptyList(),
        teamStats = if (started) teamStats() else emptyList(),
        suggestedRefresh = suggestedRefresh,
    )
}

/**
 * Live games are listed as in progress, but scores and shots seem to be entered only
 * afterwards (they read 0 throughout). Only show a live score once something has been entered.
 */
private fun GamesheetUnifiedGameDto.hasLiveData(): Boolean =
    listOf(home, visitor).any { it.goals > 0 || (it.shots ?: 0) > 0 }

internal fun gamesheetStatus(game: GamesheetUnifiedGameDto): GameStatus = when (val status = game.status.lowercase()) {
    "final" -> {
        val periods = game.home.goalsByPeriod.keys + game.visitor.goalsByPeriod.keys
        fun goalsIn(key: (String) -> Boolean) = listOf(game.home, game.visitor)
            .sumOf { team -> team.goalsByPeriod.filterKeys { key(it.lowercase()) }.values.sum() }
        GameStatus.Final(
            when {
                periods.any { it.equals("so", ignoreCase = true) } && goalsIn { it == "so" } > 0 -> Decision.SHOOTOUT
                goalsIn { it.startsWith("ot") } > 0 -> Decision.OVERTIME
                else -> Decision.REGULATION
            },
        )
    }
    // No period or clock is available.
    "in_progress" -> GameStatus.Live(period = null, clock = null)
    else -> when {
        "postpone" in status -> GameStatus.Postponed
        "cancel" in status -> GameStatus.Cancelled
        else -> GameStatus.Scheduled
    }
}

/** Both teams' goals in the order scored, with the running score. Shootout goals are left out. */
private fun GamesheetUnifiedGameDto.goals(): List<Goal> {
    class Scored(val side: TeamSide, val dto: GamesheetGoalDto, val period: Int, val elapsedSeconds: Int)

    val scored = listOf(TeamSide.AWAY to visitor, TeamSide.HOME to home).flatMap { (side, team) ->
        team.goalDetails.orEmpty().mapNotNull { goal ->
            val period = periodNumber(goal.period) ?: return@mapNotNull null
            val elapsed = elapsedSeconds(goal.clockTime, period) ?: return@mapNotNull null
            Scored(side, goal, period, elapsed)
        }
    }.sortedWith(compareBy({ it.period }, { it.elapsedSeconds }))

    var away = 0
    var home = 0
    return scored.mapIndexed { index, goal ->
        if (goal.side == TeamSide.AWAY) away++ else home++
        Goal(
            id = index.toLong(),
            side = goal.side,
            period = goal.period,
            isOvertime = goal.period > REGULATION_PERIODS,
            timeInPeriod = formatSeconds(goal.elapsedSeconds),
            awayScore = away,
            homeScore = home,
            // Not provided by Gamesheet.
            strength = GoalStrength.EVEN,
            emptyNet = false,
            scorer = GoalPlayer(
                id = goal.dto.id,
                name = "${titleCase(goal.dto.firstName)} ${titleCase(goal.dto.lastName)}".trim(),
                number = null,
                headshotUrl = null,
                seasonTotal = null,
            ),
            // Not provided by Gamesheet.
            assists = null,
        )
    }
}

private fun GamesheetUnifiedGameDto.teamStats(): List<TeamStat> = buildList {
    val awayShots = visitor.shots
    val homeShots = home.shots
    if (awayShots != null && homeShots != null) {
        add(TeamStat(TeamStatType.SHOTS_ON_GOAL, StatValue.Number(awayShots.toDouble()), StatValue.Number(homeShots.toDouble())))
    }
    fun GamesheetUnifiedTeamDto.powerPlay(): StatValue? {
        val goals = ppGoals ?: return null
        val chances = ppOpportunities ?: return null
        return StatValue.Text("$goals/$chances")
    }
    val awayPp = visitor.powerPlay()
    val homePp = home.powerPlay()
    if (awayPp != null && homePp != null) add(TeamStat(TeamStatType.POWER_PLAY, awayPp, homePp))
}

private const val REGULATION_PERIODS = 3
private const val REGULATION_PERIOD_SECONDS = 20 * 60
private const val OVERTIME_PERIOD_SECONDS = 5 * 60

/** "1"-"3" as is, "ot_1" -> 4, "ot_2" -> 5. Null for the shootout or anything unknown. */
internal fun periodNumber(period: String): Int? {
    val p = period.lowercase()
    return when {
        p.startsWith("ot") -> p.removePrefix("ot").trimStart('_').ifEmpty { "1" }.toIntOrNull()?.plus(REGULATION_PERIODS)
        else -> p.toIntOrNull()
    }
}

/**
 * Gamesheet records the time left on the clock; the app shows time elapsed. Assumes 20-minute
 * periods and 5-minute overtime, as the NAHL plays.
 */
internal fun elapsedSeconds(clockTime: String, period: Int): Int? {
    val parts = clockTime.split(':')
    if (parts.size != 2) return null
    val remaining = (parts[0].toIntOrNull() ?: return null) * 60 + (parts[1].toIntOrNull() ?: return null)
    val length = if (period > REGULATION_PERIODS) OVERTIME_PERIOD_SECONDS else REGULATION_PERIOD_SECONDS
    return (length - remaining).coerceIn(0, length)
}

private fun formatSeconds(seconds: Int): String = String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)

/** "WILLIAM - ALEXIS" -> "William - Alexis", "O'BRIEN" -> "O'Brien". */
internal fun titleCase(name: String): String {
    val sb = StringBuilder(name.length)
    var startOfWord = true
    for (c in name.trim()) {
        sb.append(if (startOfWord) c.uppercaseChar() else c.lowercaseChar())
        startOfWord = !c.isLetter()
    }
    return sb.toString()
}

private fun GamesheetUnifiedTeamDto.toTeam(): Team = nahlTeam(title, logo, abbr)

fun GamesheetTeamDto.toTeam(): Team = nahlTeam(title, logo, abbreviation = null)

private fun nahlTeam(title: String, logo: String?, abbreviation: String?): Team {
    val logoUrl = logo?.let(::withImageVariant)
    return Team(
        id = nahlTeamId(title),
        name = title,
        abbreviation = abbreviation?.takeIf { it.isNotBlank() } ?: nahlAbbreviation(title),
        league = Leagues.NAHL,
        logo = logoUrl?.let { TeamLogo(light = it, dark = it) },
    )
}

/**
 * Gamesheet logos are Cloudflare Images URLs (`https://imagedelivery.net/{account}/{image}/{variant}`),
 * which fail without the variant. Some responses leave it off, so add one.
 */
internal fun withImageVariant(url: String): String {
    val path = url.substringAfter("://").substringAfter('/', "")
    return if (url.contains("imagedelivery.net") && path.trimEnd('/').count { it == '/' } == 1) {
        url.trimEnd('/') + "/256"
    } else {
        url
    }
}

/**
 * The team list has no abbreviations, so use initials ("Lone Star Brahmas" -> "LSB"), or the
 * first three letters of a one-word name. Only shown when a logo fails to load.
 */
internal fun nahlAbbreviation(title: String): String {
    val words = title.split(Regex("[^A-Za-z0-9]+")).filter { it.isNotEmpty() }
    return when {
        words.isEmpty() -> "?"
        words.size == 1 -> words[0].take(3).uppercase()
        else -> words.take(3).joinToString("") { it.take(1) }.uppercase()
    }
}
