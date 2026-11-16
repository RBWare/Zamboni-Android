package io.rbware.zamboni

import io.rbware.zamboni.data.leagues.Leagues
import io.rbware.zamboni.data.model.Game
import io.rbware.zamboni.data.model.GameStatus
import io.rbware.zamboni.data.model.Team
import io.rbware.zamboni.ui.scores.GameMuteState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class GameMuteStateTest {
    private val dal = Team(id = "nhl-dal", name = "Dallas Stars", abbreviation = "DAL", league = Leagues.NHL)
    private val fla = Team(id = "nhl-fla", name = "Florida Panthers", abbreviation = "FLA", league = Leagues.NHL)
    private val sea = Team(id = "nhl-sea", name = "Seattle Kraken", abbreviation = "SEA", league = Leagues.NHL)
    private fun game(status: GameStatus, away: Team = fla, home: Team = dal) =
        Game(id = "g", league = Leagues.NHL, startTime = Instant.EPOCH, status = status, away = away, home = home)

    private val state = GameMuteState(favoriteTeamIds = setOf("nhl-dal"), gameAlertsOn = true)

    @Test
    fun favoriteTeamsGamesCanBeMutedUntilTheyEnd() {
        assertTrue(state.canMute(game(GameStatus.Scheduled)))
        assertTrue(state.canMute(game(GameStatus.Live(period = 1, clock = "5:00"))))
        assertFalse(state.canMute(game(GameStatus.Final())))
    }

    @Test
    fun otherTeamsGamesOrNoAlertsCantBe() {
        assertFalse(state.canMute(game(GameStatus.Scheduled, away = fla, home = sea)))
        assertFalse(state.copy(gameAlertsOn = false).canMute(game(GameStatus.Scheduled)))
    }
}
