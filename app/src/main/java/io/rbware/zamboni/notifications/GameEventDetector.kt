package io.rbware.zamboni.notifications

import io.rbware.zamboni.data.model.Game
import io.rbware.zamboni.data.model.GameStatus
import io.rbware.zamboni.data.model.TeamSide
import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.Instant

/** Coarse game state, enough to tell when something worth notifying about has happened. */
enum class GamePhase { SCHEDULED, LIVE, FINAL, OTHER }

/** What a game looked like the last time it was checked. Persisted between checks. */
@Serializable
data class GameSnapshot(
    val phase: GamePhase,
    val awayScore: Int,
    val homeScore: Int,
    /** Epoch millis of the check that produced this snapshot. */
    val observedAt: Long,
)

sealed interface GameEvent {
    val game: Game

    data class Started(override val game: Game) : GameEvent

    /** @param side the team that scored, or null if both teams scored since the last check. */
    data class Scored(override val game: Game, val side: TeamSide?) : GameEvent

    data class Ended(override val game: Game) : GameEvent
}

val Game.phase: GamePhase
    get() = when (status) {
        GameStatus.Scheduled -> GamePhase.SCHEDULED
        is GameStatus.Live -> GamePhase.LIVE
        is GameStatus.Final -> GamePhase.FINAL
        GameStatus.Postponed, GameStatus.Cancelled -> GamePhase.OTHER
    }

fun Game.snapshot(now: Instant) = GameSnapshot(
    phase = phase,
    awayScore = awayScore ?: 0,
    homeScore = homeScore ?: 0,
    observedAt = now.toEpochMilli(),
)

/**
 * Works out what changed in a game between two checks.
 *
 * The first time a game is seen there is nothing to compare against, so it only produces a
 * start event (if the game began moments ago); anything else is taken as the starting point.
 * This keeps a newly added favorite or a fresh install from replaying a game in progress.
 */
object GameEventDetector {
    /** A start alert later than this after puck drop is old news, so it is skipped. */
    val START_GRACE: Duration = Duration.ofMinutes(30)

    /**
     * If the previous check is older than this (the phone was offline or in deep sleep), goals
     * since then are skipped; the final score still reports the result.
     */
    val STALE_AFTER: Duration = Duration.ofMinutes(20)

    fun detect(previous: GameSnapshot?, game: Game, now: Instant): List<GameEvent> {
        val phase = game.phase
        val recentlyStarted = Duration.between(game.startTime, now) <= START_GRACE

        if (previous == null) {
            return if (phase == GamePhase.LIVE && recentlyStarted) listOf(GameEvent.Started(game)) else emptyList()
        }

        val events = mutableListOf<GameEvent>()
        val wasUnderway = previous.phase == GamePhase.LIVE || previous.phase == GamePhase.FINAL

        if (phase == GamePhase.LIVE && !wasUnderway && recentlyStarted) {
            events += GameEvent.Started(game)
        }

        val stale = Duration.between(Instant.ofEpochMilli(previous.observedAt), now) > STALE_AFTER
        if (phase == GamePhase.LIVE && !stale) {
            val awayScored = (game.awayScore ?: 0) > previous.awayScore
            val homeScored = (game.homeScore ?: 0) > previous.homeScore
            if (awayScored || homeScored) {
                val side = when {
                    awayScored && homeScored -> null
                    awayScored -> TeamSide.AWAY
                    else -> TeamSide.HOME
                }
                events += GameEvent.Scored(game, side)
            }
        }

        if (phase == GamePhase.FINAL && previous.phase != GamePhase.FINAL) {
            events += GameEvent.Ended(game)
        }
        return events
    }
}
