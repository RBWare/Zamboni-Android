package io.rbware.zamboni.data.scores

import io.rbware.zamboni.data.model.Game
import io.rbware.zamboni.data.model.League
import java.time.LocalDate

interface ScoresRepository {
    /** Leagues available for filtering, in display order. */
    suspend fun getLeagues(): List<League>

    /** All games across all leagues on [date] (in the device's local time zone). */
    suspend fun getGames(date: LocalDate): List<Game>
}
