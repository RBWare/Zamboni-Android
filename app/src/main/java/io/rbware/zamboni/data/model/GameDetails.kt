package io.rbware.zamboni.data.model

import kotlin.time.Duration

/** Everything shown when a game is opened. */
data class GameDetails(
    /** Up-to-date header info (status, score). Records are not included; take them from the schedule. */
    val game: Game,
    val goals: List<Goal>,
    /** In display order. Empty before the game starts. */
    val teamStats: List<TeamStat>,
    val suggestedRefresh: Duration? = null,
    /** TV and streaming broadcasts, in the source's order. Empty if the source doesn't list them. */
    val broadcasts: List<Broadcast> = emptyList(),
    /** Each team's live radio stream, when the source has one. */
    val radio: List<RadioStream> = emptyList(),
    /** The power play under way, if any. Only the NHL reports it. */
    val powerPlay: PowerPlay? = null,
)

/**
 * A power play under way.
 *
 * @param side the team with the extra skater.
 * @param secondsRemaining time left on the penalty when the data was produced, if known. It
 *   counts down with the game clock (see GameStatus.Live.secondsRemainingAt).
 */
data class PowerPlay(val side: TeamSide, val secondsRemaining: Int?) {
    /**
     * Time left on the penalty at [now]: it runs with the game clock, so it loses as many
     * seconds as [status]'s clock has since the data was produced. Null if unknown or used up.
     */
    fun secondsRemainingAt(status: GameStatus.Live, now: java.time.Instant): Int? {
        val seconds = secondsRemaining ?: return null
        val clockNow = status.secondsRemainingAt(now)
        val elapsed = if (status.secondsRemaining != null && clockNow != null) status.secondsRemaining - clockNow else 0
        return (seconds - elapsed).takeIf { it > 0 }
    }
}

/** Shots on goal from the team stats, as (away, home), or null if the source has none. */
val GameDetails.shotsOnGoal: Pair<Int, Int>?
    get() {
        val stat = teamStats.firstOrNull { it.type == TeamStatType.SHOTS_ON_GOAL } ?: return null
        val away = (stat.away as? StatValue.Number)?.value?.toInt() ?: return null
        val home = (stat.home as? StatValue.Number)?.value?.toInt() ?: return null
        return away to home
    }

/** Who a broadcast is for. */
enum class BroadcastMarket { NATIONAL, HOME, AWAY }

/**
 * A TV channel or streaming service carrying the game.
 *
 * @param network the source's short name, e.g. "SN1" or "Prime Video" (see BroadcastNames).
 * @param countryCode "US" or "CA", if known.
 * @param logo the network's logo; not every network has one, so it may fail to load.
 */
data class Broadcast(
    val network: String,
    val market: BroadcastMarket,
    val countryCode: String?,
    val logo: TeamLogo?,
)

/**
 * A team's radio broadcast: an audio stream (HLS) that plays in the app, or, when [isWebPage],
 * the broadcaster's web page (e.g. iHeartRadio), which opens in the browser.
 */
data class RadioStream(val side: TeamSide, val url: String, val isWebPage: Boolean = false)

enum class TeamSide { AWAY, HOME }

data class Goal(
    val id: Long,
    val side: TeamSide,
    val period: Int,
    val isOvertime: Boolean,
    /** Elapsed time in the period, e.g. "7:56". */
    val timeInPeriod: String,
    /** Score after this goal. */
    val awayScore: Int,
    val homeScore: Int,
    val strength: GoalStrength,
    val emptyNet: Boolean,
    val scorer: GoalPlayer,
    /** Empty if unassisted; null if the source doesn't report assists. */
    val assists: List<GoalPlayer>?,
)

enum class GoalStrength { EVEN, POWER_PLAY, SHORTHANDED }

/**
 * The goal that made the score [awayScore]-[homeScore], or null if these goals don't include it
 * yet. Game details can lag a few seconds behind the score, so the newest goal in them isn't
 * always the one that was just scored.
 */
fun List<Goal>.goalAtScore(awayScore: Int, homeScore: Int): Goal? =
    lastOrNull { it.awayScore == awayScore && it.homeScore == homeScore }

data class GoalPlayer(
    val id: Long,
    val name: String,
    val number: Int?,
    val headshotUrl: String?,
    /** Season total including this goal or assist. */
    val seasonTotal: Int?,
)

/** One row of the team stats comparison. */
data class TeamStat(
    val type: TeamStatType,
    val away: StatValue,
    val home: StatValue,
)

/** Stats shown in the comparison, in display order. [key] is the field name in the API. */
enum class TeamStatType(val key: String) {
    SHOTS_ON_GOAL("sog"),
    FACEOFF_PERCENTAGE("faceoffWinningPctg"),
    POWER_PLAY("powerPlay"),
    PENALTY_MINUTES("pim"),
    HITS("hits"),
    BLOCKED_SHOTS("blockedShots"),
    GIVEAWAYS("giveaways"),
    TAKEAWAYS("takeaways"),
}

sealed interface StatValue {
    /** A count, or a 0-1 fraction for percentage stats. */
    data class Number(val value: Double) : StatValue

    /** Pre-formatted by the API, e.g. power play "3/5". */
    data class Text(val value: String) : StatValue
}
