package io.rbware.zamboni.data.model

import java.time.Instant

data class Game(
    val id: String,
    val league: League,
    val startTime: Instant,
    val status: GameStatus,
    val away: Team,
    val home: Team,
    /** Null until the game has started. */
    val awayScore: Int? = null,
    val homeScore: Int? = null,
    val venue: String? = null,
    /** Season record at game time, e.g. "9-7-1". */
    val awayRecord: String? = null,
    val homeRecord: String? = null,
) {
    fun involvesAny(teamIds: Set<String>): Boolean = away.id in teamIds || home.id in teamIds

    /** The winning team once the game is final, otherwise null. */
    val winner: Team?
        get() {
            if (status !is GameStatus.Final || awayScore == null || homeScore == null) return null
            return when {
                awayScore > homeScore -> away
                homeScore > awayScore -> home
                else -> null
            }
        }
}

sealed interface GameStatus {
    data object Scheduled : GameStatus

    /**
     * @param period 1-3 for regulation, 4 for OT, 5+ for additional OT periods.
     * @param clock time remaining in the period (e.g. "12:34"), or null if unknown.
     * @param intermission true during the break after [period].
     * @param shootout true once the game has gone to a shootout.
     */
    data class Live(
        val period: Int,
        val clock: String?,
        val intermission: Boolean = false,
        val shootout: Boolean = false,
    ) : GameStatus

    data class Final(val decision: Decision = Decision.REGULATION) : GameStatus

    data object Postponed : GameStatus

    data object Cancelled : GameStatus
}

enum class Decision { REGULATION, OVERTIME, SHOOTOUT }
