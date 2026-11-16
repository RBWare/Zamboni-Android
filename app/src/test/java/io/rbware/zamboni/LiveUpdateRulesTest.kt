package io.rbware.zamboni

import io.rbware.zamboni.data.leagues.Leagues
import io.rbware.zamboni.data.model.Game
import io.rbware.zamboni.data.model.GameStatus
import io.rbware.zamboni.data.model.Team
import io.rbware.zamboni.notifications.NotificationChecker.Companion.wantsLiveUpdate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class LiveUpdateRulesTest {
    private val start = Instant.parse("2026-11-16T01:00:00Z")
    private val team = Team(id = "nhl-dal", name = "Dallas Stars", abbreviation = "DAL", league = Leagues.NHL)
    private fun game(status: GameStatus) = Game(id = "g", league = Leagues.NHL, startTime = start, status = status, away = team, home = team)

    @Test
    fun liveGamesAlwaysGetOne() {
        assertTrue(wantsLiveUpdate(game(GameStatus.Live(period = 2, clock = "5:00")), start.plusSeconds(7200)))
    }

    @Test
    fun scheduledGamesFromFiveMinutesBeforeUntilHalfAnHourLate() {
        val scheduled = game(GameStatus.Scheduled)
        assertFalse(wantsLiveUpdate(scheduled, start.minusSeconds(6 * 60)))
        assertTrue(wantsLiveUpdate(scheduled, start.minusSeconds(4 * 60)))
        assertTrue(wantsLiveUpdate(scheduled, start.plusSeconds(29 * 60)))
        assertFalse(wantsLiveUpdate(scheduled, start.plusSeconds(31 * 60)))
    }

    @Test
    fun finishedOrCalledOffGamesDont() {
        assertFalse(wantsLiveUpdate(game(GameStatus.Final()), start.plusSeconds(60)))
        assertFalse(wantsLiveUpdate(game(GameStatus.Postponed), start))
    }
}
