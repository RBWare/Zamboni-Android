package io.rbware.zamboni.data.model

import java.time.Instant
import kotlin.time.Duration.Companion.seconds

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
    /** When the live data was produced, if known. */
    val dataAsOf: Instant? get() = (status as? GameStatus.Live)?.clockAsOf

    /**
     * This game with the status and score from [update] (the same game, e.g. from its details),
     * unless [update] is an older live snapshot than what's here. Records and venue are kept.
     */
    fun updatedWith(update: Game): Game {
        if (update.id != id) return this
        val mine = dataAsOf
        val theirs = update.dataAsOf
        if (mine != null && theirs != null && theirs < mine && update.status is GameStatus.Live) return this
        // An older source still saying "live" shouldn't undo a final we already have.
        if (status is GameStatus.Final && update.status is GameStatus.Live) return this
        return copy(
            status = update.status,
            awayScore = update.awayScore ?: awayScore,
            homeScore = update.homeScore ?: homeScore,
        )
    }

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
     * @param period 1-3 for regulation, 4 for OT, 5+ for additional OT periods. Null if the
     *   source only says the game is in progress.
     * @param clock time remaining in the period (e.g. "12:34"), or null if unknown.
     * @param intermission true during the break after [period].
     * @param shootout true once the game has gone to a shootout.
     * @param secondsRemaining [clock] in seconds, when the source gives it.
     * @param clockRunning the clock was running when the data was produced (not stopped for a
     *   whistle or intermission), so it can count down on screen between updates.
     * @param clockAsOf when the source produced this snapshot, or null if unknown.
     */
    data class Live(
        val period: Int?,
        val clock: String?,
        val intermission: Boolean = false,
        val shootout: Boolean = false,
        val secondsRemaining: Int? = null,
        val clockRunning: Boolean = false,
        val clockAsOf: Instant? = null,
    ) : GameStatus {
        /**
         * Seconds left at [now], counting down from [secondsRemaining] while the clock runs.
         * Stops counting [MAX_EXTRAPOLATION] after the snapshot, so a missed update (or a
         * whistle the data hasn't caught up with) can't run the clock far ahead. [since] stands
         * in for [clockAsOf] when the source didn't give one.
         */
        fun secondsRemainingAt(now: Instant, since: Instant? = null): Int? {
            val seconds = secondsRemaining ?: return null
            val asOf = clockAsOf ?: since
            if (!clockRunning || asOf == null) return seconds
            val elapsed = java.time.Duration.between(asOf, now).seconds.coerceIn(0, MAX_EXTRAPOLATION.inWholeSeconds)
            return (seconds - elapsed).toInt().coerceAtLeast(0)
        }

        companion object {
            val MAX_EXTRAPOLATION = 30.seconds
        }
    }

    data class Final(val decision: Decision = Decision.REGULATION) : GameStatus

    data object Postponed : GameStatus

    data object Cancelled : GameStatus
}

enum class Decision { REGULATION, OVERTIME, SHOOTOUT }
