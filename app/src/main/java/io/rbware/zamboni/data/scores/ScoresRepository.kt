package io.rbware.zamboni.data.scores

import io.rbware.zamboni.data.model.GameDetails
import io.rbware.zamboni.data.model.League
import io.rbware.zamboni.data.model.Schedule
import java.time.LocalDate

interface ScoresRepository {
    /** Leagues available for filtering, in display order. */
    suspend fun getLeagues(): List<League>

    /** All games on the game day [date] (North American game day, as the leagues define it). */
    suspend fun getSchedule(date: LocalDate): Schedule

    /**
     * Full details for the game with app-wide [gameId] (as in [io.rbware.zamboni.data.model.Game.id]).
     *
     * @throws GameNotFoundException if the source doesn't know this game.
     */
    suspend fun getGameDetails(gameId: String): GameDetails
}

class GameNotFoundException(gameId: String) : Exception("Unknown game: $gameId")
