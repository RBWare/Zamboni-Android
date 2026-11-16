package io.rbware.zamboni.data.scores

import io.rbware.zamboni.data.leagues.Leagues
import io.rbware.zamboni.data.model.Broadcast
import io.rbware.zamboni.data.model.BroadcastMarket
import io.rbware.zamboni.data.model.Decision
import io.rbware.zamboni.data.model.Game
import io.rbware.zamboni.data.model.GameDetails
import io.rbware.zamboni.data.model.GameStatus
import io.rbware.zamboni.data.model.Goal
import io.rbware.zamboni.data.model.GoalPlayer
import io.rbware.zamboni.data.model.GoalStrength
import io.rbware.zamboni.data.model.RadioStream
import io.rbware.zamboni.data.model.StatValue
import io.rbware.zamboni.data.model.Team
import io.rbware.zamboni.data.model.TeamLogo
import io.rbware.zamboni.data.model.TeamSide
import io.rbware.zamboni.data.model.TeamStat
import io.rbware.zamboni.data.model.TeamStatType
import io.rbware.zamboni.data.remote.HockeyTechClockDto
import io.rbware.zamboni.data.remote.HockeyTechGameSummaryDto
import io.rbware.zamboni.data.remote.HockeyTechPlayerDto
import io.rbware.zamboni.data.remote.HockeyTechScheduleGameDto
import io.rbware.zamboni.data.remote.HockeyTechScorebarGameDto
import io.rbware.zamboni.data.remote.HockeyTechSeasonDto
import io.rbware.zamboni.data.remote.HockeyTechSummaryTeamDto
import io.rbware.zamboni.data.remote.HockeyTechTeamDto
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.Locale
import kotlin.time.Duration

/*
 * Converts HockeyTech feed responses into app models for the AHL.
 */

private const val AHL_GAME_ID_PREFIX = "ahl-"

/**
 * App-wide team ID for an AHL team, from its code: "BEL" -> "ahl-bel", like NHL teams. The
 * placeholder list of early versions used a few other codes; see AhlTeamIdMigration.
 */
fun ahlTeamId(code: String): String = "${Leagues.AHL.id}-${code.lowercase(Locale.ROOT)}"

/** App-wide game ID, e.g. "ahl-1029321". HockeyTech game IDs are unique across seasons. */
fun ahlGameId(gameId: String): String = "$AHL_GAME_ID_PREFIX$gameId"

/** The HockeyTech game ID behind an app game ID, or null if it isn't an AHL one. */
fun parseAhlGameId(gameId: String): String? =
    gameId.takeIf { it.startsWith(AHL_GAME_ID_PREFIX) }
        ?.removePrefix(AHL_GAME_ID_PREFIX)
        ?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }

fun isAhlGameId(gameId: String): Boolean = parseAhlGameId(gameId) != null

/**
 * The 500px logo. Files are named by HockeyTech's numeric team ID, plus a season for teams that
 * changed their logo ("373_94.png"), so a URL from the feed is preferred: [known], else the
 * 50px one from the scores bar made full size, else a guess from the ID.
 */
internal fun ahlLogoUrl(hockeyTechTeamId: String, known: String? = null, small: String? = null): String =
    known?.takeIf { it.startsWith("https://") }
        ?: small?.takeIf { it.startsWith("https://") }?.replace("/logos/50x50/", "/logos/")
        ?: "https://assets.leaguestat.com/ahl/logos/$hockeyTechTeamId.png"

internal fun ahlTeam(hockeyTechTeamId: String, code: String, name: String, logo: String): Team {
    return Team(
        id = ahlTeamId(code),
        name = name,
        abbreviation = code,
        league = Leagues.AHL,
        logo = TeamLogo(light = logo, dark = logo),
    )
}

fun HockeyTechTeamDto.toTeam(): Team = ahlTeam(id, code, name, ahlLogoUrl(id, known = logo))

/** Seasons whose games may fall on [date], leaving out all-star events. */
fun List<HockeyTechSeasonDto>.coveringDate(date: LocalDate): List<HockeyTechSeasonDto> = filter { season ->
    val start = season.startDate.toLocalDateOrNull() ?: return@filter false
    val end = season.endDate.toLocalDateOrNull() ?: return@filter false
    season.career == "1" && date in start..end
}

/** The regular season under way on [today], else the latest one that has started, for the team list. */
fun List<HockeyTechSeasonDto>.currentRegularSeason(today: LocalDate): HockeyTechSeasonDto? {
    val regular = filter { it.career == "1" && it.playoff == "0" }
    return regular.coveringDate(today).firstOrNull()
        ?: regular
            .filter { season -> season.startDate.toLocalDateOrNull()?.let { it <= today } == true }
            .maxByOrNull { it.startDate }
}

private fun String.toLocalDateOrNull(): LocalDate? = runCatching { LocalDate.parse(this) }.getOrNull()

private fun parseStart(iso: String): Instant = OffsetDateTime.parse(iso).toInstant()

/** @param logos logo URLs by HockeyTech team ID, from the team list. */
fun HockeyTechScorebarGameDto.toGame(logos: Map<String, String> = emptyMap()): Game {
    val status = hockeyTechStatus(
        status = status,
        statusText = statusText,
        period = period,
        periodName = periodName,
        clock = clock,
        intermission = intermission == "1",
    )
    val started = status is GameStatus.Live || status is GameStatus.Final
    val upcoming = status !is GameStatus.Final
    return Game(
        id = ahlGameId(id),
        league = Leagues.AHL,
        startTime = parseStart(startTime),
        status = status,
        away = ahlTeam(visitorId, visitorCode, visitorName, ahlLogoUrl(visitorId, logos[visitorId], visitorLogo)),
        home = ahlTeam(homeId, homeCode, homeName, ahlLogoUrl(homeId, logos[homeId], homeLogo)),
        awayScore = if (started) visitorGoals.toIntOrNull() else null,
        homeScore = if (started) homeGoals.toIntOrNull() else null,
        venue = venue?.takeIf { it.isNotBlank() },
        // The feed sends the current record, which is only right for games yet to be played.
        awayRecord = if (upcoming) record(visitorWins, visitorLosses, visitorOtLosses, visitorSoLosses) else null,
        homeRecord = if (upcoming) record(homeWins, homeLosses, homeOtLosses, homeSoLosses) else null,
    )
}

/** @param logos logo URLs by HockeyTech team ID, from the team list (the schedule has none). */
fun HockeyTechScheduleGameDto.toGame(logos: Map<String, String> = emptyMap()): Game {
    val status = hockeyTechStatus(
        status = status,
        statusText = statusText,
        period = period,
        periodName = null,
        clock = clock,
        intermission = intermission == "1",
        overtime = overtime == "1",
        shootout = shootout == "1",
    )
    val started = status is GameStatus.Live || status is GameStatus.Final
    return Game(
        id = ahlGameId(id),
        league = Leagues.AHL,
        startTime = parseStart(startTime),
        status = status,
        away = ahlTeam(visitorId, visitorCode, visitorName, ahlLogoUrl(visitorId, logos[visitorId])),
        home = ahlTeam(homeId, homeCode, homeName, ahlLogoUrl(homeId, logos[homeId])),
        awayScore = if (started) visitorGoals.toIntOrNull() else null,
        homeScore = if (started) homeGoals.toIntOrNull() else null,
        venue = venue?.takeIf { it.isNotBlank() },
    )
}

/** The AHL's record format: wins, regulation losses, overtime losses, shootout losses. */
private fun record(wins: String?, losses: String?, otLosses: String?, soLosses: String?): String? {
    val parts = listOf(wins, losses, otLosses, soLosses).map { it?.toIntOrNull() ?: return null }
    return parts.joinToString("-")
}

/**
 * The app status from the feed's fields, which are named a little differently in each view.
 *
 * @param status "1" not started, "2" in progress, "3" unofficial final, "4" final.
 * @param statusText e.g. "Final SO", "Postponed", "Intermission", "7:00 PM EST".
 * @param period "1"-"3", "4" for overtime (also during a shootout), "5"+ for more overtimes.
 * @param periodName "OT", "SO"..., when the view has it.
 * @param clock time left in the period, "MM:SS" or "HH:MM:SS".
 * @param overtime the game went past regulation, when the view says so directly.
 * @param shootout the game went to a shootout, when the view says so directly.
 */
internal fun hockeyTechStatus(
    status: String,
    statusText: String?,
    period: String?,
    periodName: String?,
    clock: String?,
    intermission: Boolean,
    overtime: Boolean? = null,
    shootout: Boolean? = null,
): GameStatus {
    val text = statusText.orEmpty().trim()
    val lower = text.lowercase(Locale.ROOT)
    val words = text.uppercase(Locale.ROOT).split(Regex("[^A-Z0-9]+"))
    val periodNumber = period?.toIntOrNull()
    val isShootout = shootout == true || periodName.equals("SO", ignoreCase = true) || "SO" in words
    return when {
        "postpone" in lower -> GameStatus.Postponed
        "cancel" in lower -> GameStatus.Cancelled
        status == "3" || status == "4" -> GameStatus.Final(
            when {
                isShootout -> Decision.SHOOTOUT
                overtime == true || (periodNumber ?: 0) > REGULATION_PERIODS || words.any { it.endsWith("OT") } -> Decision.OVERTIME
                else -> Decision.REGULATION
            },
        )
        status == "2" -> {
            val seconds = clock?.let(::clockSeconds)
            GameStatus.Live(
                period = periodNumber?.takeIf { it > 0 },
                clock = seconds?.let(::formatSeconds),
                intermission = intermission || "intermission" in lower || lower.startsWith("end of"),
                shootout = isShootout,
                secondsRemaining = seconds,
            )
        }
        else -> GameStatus.Scheduled
    }
}

/**
 * Full details for one game from its summary, with the live state from [clock] when it could be
 * fetched (the summary itself has no period or clock).
 */
fun HockeyTechGameSummaryDto.toGameDetails(clock: HockeyTechClockDto?, suggestedRefresh: Duration?): GameDetails {
    val summaryStatus = when {
        details.final == "1" -> hockeyTechStatus(
            status = "4",
            statusText = details.status,
            period = periods.maxOfOrNull { it.info.id.toIntOrNull() ?: 0 }?.toString(),
            periodName = null,
            clock = null,
            intermission = false,
            shootout = hasShootout,
        )
        details.started == "1" -> GameStatus.Live(period = null, clock = null)
        else -> hockeyTechStatus("1", details.status, null, null, null, intermission = false)
    }
    val status = clock?.let {
        hockeyTechStatus(
            status = it.status,
            statusText = it.progress,
            period = it.period,
            periodName = it.periodName,
            clock = it.clock,
            intermission = false,
            shootout = if (it.status == "3" || it.status == "4") hasShootout else null,
        )
    } ?: summaryStatus
    val started = status is GameStatus.Live || status is GameStatus.Final
    val away = visitingTeam.toTeam()
    val home = homeTeam.toTeam()
    val game = Game(
        id = ahlGameId(details.id),
        league = Leagues.AHL,
        startTime = parseStart(details.startTime),
        status = status,
        away = away,
        home = home,
        awayScore = if (started) (clock?.visitorGoals ?: visitingTeam.stats?.goals)?.toIntOrNull() ?: 0 else null,
        homeScore = if (started) (clock?.homeGoals ?: homeTeam.stats?.goals)?.toIntOrNull() ?: 0 else null,
        venue = details.venue?.takeIf { it.isNotBlank() },
    )
    return GameDetails(
        game = game,
        goals = if (started) goals() else emptyList(),
        teamStats = if (started) teamStats() else emptyList(),
        suggestedRefresh = suggestedRefresh,
        broadcasts = listOfNotNull(
            details.floHockeyUrl?.takeIf { it.isNotBlank() }?.let {
                Broadcast(network = "FloHockey", market = BroadcastMarket.NATIONAL, countryCode = null, logo = null)
            },
        ),
        radio = listOfNotNull(
            visitingTeam.media?.audioUrl?.let { radioLink(TeamSide.AWAY, it) },
            homeTeam.media?.audioUrl?.let { radioLink(TeamSide.HOME, it) },
        ),
    )
}

private fun HockeyTechSummaryTeamDto.toTeam(): Team = ahlTeam(info.id, info.abbreviation, info.name, ahlLogoUrl(info.id, known = info.logo))

/**
 * Teams link to their radio broadcast's web page (iHeartRadio, Mixlr...), which opens in the
 * browser; a direct HLS stream would play in the app.
 */
internal fun radioLink(side: TeamSide, url: String): RadioStream? {
    val link = url.trim().takeIf { it.startsWith("https://") || it.startsWith("http://") } ?: return null
    val isStream = link.substringBefore('?').endsWith(".m3u8", ignoreCase = true)
    return RadioStream(side = side, url = link, isWebPage = !isStream)
}

/** Both teams' goals in the order scored, with the running score. Shootout goals aren't listed. */
private fun HockeyTechGameSummaryDto.goals(): List<Goal> {
    val homeId = homeTeam.info.id
    var away = 0
    var home = 0
    return periods
        .sortedBy { it.info.id.toIntOrNull() ?: Int.MAX_VALUE }
        .flatMap { period ->
            val number = period.info.id.toIntOrNull() ?: return@flatMap emptyList()
            period.goals.map { number to it }
        }
        .mapIndexed { index, (period, goal) ->
            val side = if (goal.team.id == homeId) TeamSide.HOME else TeamSide.AWAY
            if (side == TeamSide.AWAY) away++ else home++
            val props = goal.properties
            Goal(
                id = goal.id.toLongOrNull() ?: index.toLong(),
                side = side,
                period = period,
                isOvertime = period > REGULATION_PERIODS,
                timeInPeriod = clockSeconds(goal.time)?.let(::formatSeconds) ?: goal.time,
                awayScore = away,
                homeScore = home,
                strength = when {
                    props?.isPowerPlay == "1" -> GoalStrength.POWER_PLAY
                    props?.isShortHanded == "1" -> GoalStrength.SHORTHANDED
                    else -> GoalStrength.EVEN
                },
                emptyNet = props?.isEmptyNet == "1",
                scorer = goal.scoredBy.toGoalPlayer(goal.scorerGoalNumber),
                assists = goal.assists.mapIndexed { i, player -> player.toGoalPlayer(goal.assistNumbers.getOrNull(i)) },
            )
        }
}

private fun HockeyTechPlayerDto.toGoalPlayer(seasonTotal: String?): GoalPlayer = GoalPlayer(
    id = id.toLongOrNull() ?: 0L,
    name = listOfNotNull(firstName, lastName).joinToString(" ").trim(),
    number = jerseyNumber?.toIntOrNull()?.takeIf { it > 0 },
    headshotUrl = playerImageURL?.takeIf { it.startsWith("https://") },
    seasonTotal = seasonTotal?.toIntOrNull()?.takeIf { it > 0 },
)

private fun HockeyTechGameSummaryDto.teamStats(): List<TeamStat> = buildList {
    val a = visitingTeam.stats ?: return@buildList
    val h = homeTeam.stats ?: return@buildList
    fun number(type: TeamStatType, away: String?, home: String?, skipIfZero: Boolean = false) {
        val av = away?.toDoubleOrNull() ?: return
        val hv = home?.toDoubleOrNull() ?: return
        if (skipIfZero && av == 0.0 && hv == 0.0) return
        add(TeamStat(type, StatValue.Number(av), StatValue.Number(hv)))
    }
    number(TeamStatType.SHOTS_ON_GOAL, a.shots, h.shots)
    // Faceoffs and hits aren't recorded in every rink; they read 0 when missing.
    val aFaceoffs = a.faceoffAttempts?.toIntOrNull() ?: 0
    val hFaceoffs = h.faceoffAttempts?.toIntOrNull() ?: 0
    if (aFaceoffs > 0 && hFaceoffs > 0) {
        val aWins = a.faceoffWins?.toDoubleOrNull() ?: 0.0
        val hWins = h.faceoffWins?.toDoubleOrNull() ?: 0.0
        add(TeamStat(TeamStatType.FACEOFF_PERCENTAGE, StatValue.Number(aWins / aFaceoffs), StatValue.Number(hWins / hFaceoffs)))
    }
    val aPp = powerPlay(a.powerPlayGoals, a.powerPlayOpportunities)
    val hPp = powerPlay(h.powerPlayGoals, h.powerPlayOpportunities)
    if (aPp != null && hPp != null) add(TeamStat(TeamStatType.POWER_PLAY, aPp, hPp))
    number(TeamStatType.PENALTY_MINUTES, a.penaltyMinuteCount, h.penaltyMinuteCount)
    number(TeamStatType.HITS, a.hits, h.hits, skipIfZero = true)
}

private fun powerPlay(goals: String?, chances: String?): StatValue? {
    val g = goals?.toIntOrNull() ?: return null
    val c = chances?.toIntOrNull() ?: return null
    return StatValue.Text("$g/$c")
}

private const val REGULATION_PERIODS = 3

/** "12:34", "00:12:34" or "1:00" as seconds; null if it isn't a time. */
internal fun clockSeconds(clock: String): Int? {
    val parts = clock.trim().split(':').map { it.toIntOrNull() ?: return null }
    return when (parts.size) {
        2 -> parts[0] * 60 + parts[1]
        3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
        else -> null
    }
}

private fun formatSeconds(seconds: Int): String = String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
