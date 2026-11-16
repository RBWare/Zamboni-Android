package io.rbware.zamboni

import io.rbware.zamboni.data.leagues.Leagues
import io.rbware.zamboni.data.model.Game
import io.rbware.zamboni.data.model.GameStatus
import io.rbware.zamboni.data.model.Team
import io.rbware.zamboni.data.scores.NhlFreshness
import okhttp3.Headers.Companion.headersOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

class LiveClockTest {
    private val t0 = Instant.parse("2026-11-16T01:00:00Z")

    private fun live(seconds: Int, running: Boolean = true, asOf: Instant? = t0) =
        GameStatus.Live(period = 2, clock = "x", secondsRemaining = seconds, clockRunning = running, clockAsOf = asOf)

    // Countdown

    @Test
    fun countdown_runsFromSnapshot() {
        assertEquals(600, live(612).secondsRemainingAt(t0.plusSeconds(12)))
    }

    @Test
    fun countdown_stoppedClockStays() {
        assertEquals(612, live(612, running = false).secondsRemainingAt(t0.plusSeconds(12)))
    }

    @Test
    fun countdown_neverBelowZeroAndCapped() {
        assertEquals(0, live(5).secondsRemainingAt(t0.plusSeconds(20)))
        // A missed update can't run the clock more than 30s ahead.
        assertEquals(570, live(600).secondsRemainingAt(t0.plusSeconds(300)))
    }

    @Test
    fun countdown_usesFallbackStartWithoutSnapshotTime() {
        assertEquals(590, live(600, asOf = null).secondsRemainingAt(t0.plusSeconds(10), since = t0))
        assertEquals(600, live(600, asOf = null).secondsRemainingAt(t0.plusSeconds(10)))
        assertNull(GameStatus.Live(period = 1, clock = null).secondsRemainingAt(t0))
    }

    // CDN headers

    @Test
    fun freshness_fromAgeAndMaxAge() {
        val f = NhlFreshness.of(headersOf("Cache-Control", "no-transform, max-age=19, s-maxage=19", "Age", "9"), t0)
        assertEquals(t0.minusSeconds(9), f.asOf)
        assertEquals(10.seconds, f.freshIn)
        // Poll just after the copy expires, but never more often than every 2s or less than liveMax.
        assertEquals(11.seconds, f.nextRefresh(default = 20.seconds, liveMax = 20.seconds))
        assertEquals(2.seconds, NhlFreshness(t0, 0.seconds).nextRefresh(20.seconds, 20.seconds))
        // Idle (nothing live) keeps the mapper's longer interval.
        assertEquals(60.seconds, f.nextRefresh(default = 60.seconds, liveMax = 20.seconds))
    }

    @Test
    fun freshness_withoutHeaders() {
        val f = NhlFreshness.of(headersOf(), t0)
        assertEquals(t0, f.asOf)
        assertNull(f.freshIn)
        assertEquals(20.seconds, f.nextRefresh(default = 20.seconds, liveMax = 20.seconds))
    }

    // Merging details into the list

    private val team = Team(id = "nhl-dal", name = "Dallas Stars", abbreviation = "DAL", league = Leagues.NHL)
    private fun game(status: GameStatus, away: Int = 1) = Game(
        id = "g", league = Leagues.NHL, startTime = t0, status = status, away = team, home = team,
        awayScore = away, homeScore = 0, awayRecord = "9-7-1",
    )

    @Test
    fun update_newerSnapshotWinsAndKeepsRecords() {
        val list = game(live(600, asOf = t0))
        val merged = list.updatedWith(game(live(580, asOf = t0.plusSeconds(15)), away = 2))
        assertEquals(580, (merged.status as GameStatus.Live).secondsRemaining)
        assertEquals(2, merged.awayScore)
        assertEquals("9-7-1", merged.awayRecord)
    }

    @Test
    fun update_olderSnapshotIgnored() {
        val list = game(live(580, asOf = t0.plusSeconds(15)))
        assertSame(list, list.updatedWith(game(live(600, asOf = t0))))
    }

    @Test
    fun update_finalNotUndoneByLive() {
        val list = game(GameStatus.Final())
        assertSame(list, list.updatedWith(game(live(10))))
    }
}
