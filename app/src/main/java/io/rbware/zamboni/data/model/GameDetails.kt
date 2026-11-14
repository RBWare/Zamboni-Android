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
)

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
