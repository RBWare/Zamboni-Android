package io.rbware.zamboni

import io.rbware.zamboni.data.model.Game
import io.rbware.zamboni.data.model.GameStatus
import io.rbware.zamboni.data.model.League
import io.rbware.zamboni.data.model.Team
import io.rbware.zamboni.data.model.TeamSide
import io.rbware.zamboni.notifications.GameEvent
import io.rbware.zamboni.notifications.GameEventDetector
import io.rbware.zamboni.notifications.GamePhase
import io.rbware.zamboni.notifications.GameSnapshot
import io.rbware.zamboni.notifications.NotificationChecker
import io.rbware.zamboni.notifications.snapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class GameEventDetectorTest {
    private val nhl = League("nhl", "NHL", "National Hockey League")
    private val start = Instant.parse("2026-11-15T00:00:00Z")
    private val live = GameStatus.Live(period = 1, clock = "15:00")

    private fun game(status: GameStatus, away: Int? = null, home: Int? = null, startTime: Instant = start) = Game(
        id = "g1",
        league = nhl,
        startTime = startTime,
        status = status,
        away = Team("tor", "Toronto Maple Leafs", "TOR", nhl),
        home = Team("mtl", "Montreal Canadiens", "MTL", nhl),
        awayScore = away,
        homeScore = home,
    )

    private fun minutes(n: Long): Instant = start.plus(Duration.ofMinutes(n))

    private fun snapshotOf(game: Game, at: Instant) = game.snapshot(at)

    private fun detect(previous: GameSnapshot?, game: Game, now: Instant) =
        GameEventDetector.detect(previous, game, now).map { it::class.simpleName to (it as? GameEvent.Scored)?.side }

    @Test
    fun firstSeen_liveJustStarted_reportsStart() {
        assertEquals(listOf("Started" to null), detect(null, game(live, 0, 0), minutes(5)))
    }

    @Test
    fun firstSeen_midGame_isSilentBaseline() {
        assertEquals(emptyList<Any>(), detect(null, game(live, 2, 1), minutes(70)))
    }

    @Test
    fun firstSeen_final_isSilent() {
        assertEquals(emptyList<Any>(), detect(null, game(GameStatus.Final(), 3, 2), minutes(180)))
    }

    @Test
    fun scheduledToLive_reportsStart() {
        val before = snapshotOf(game(GameStatus.Scheduled), minutes(-2))
        assertEquals(listOf("Started" to null), detect(before, game(live, 0, 0), minutes(8)))
    }

    @Test
    fun scheduledToLive_longAfterStart_skipsStart() {
        val before = snapshotOf(game(GameStatus.Scheduled), minutes(40))
        assertEquals(emptyList<Any>(), detect(before, game(live, 0, 0), minutes(45)))
    }

    @Test
    fun goal_reportsScoringSide() {
        val before = snapshotOf(game(live, 0, 0), minutes(20))
        assertEquals(listOf("Scored" to TeamSide.HOME), detect(before, game(live, 0, 1), minutes(22)))
        assertEquals(listOf("Scored" to TeamSide.AWAY), detect(before, game(live, 1, 0), minutes(22)))
    }

    @Test
    fun bothTeamsScoredSinceLastCheck_sideIsNull() {
        val before = snapshotOf(game(live, 1, 1), minutes(20))
        assertEquals(listOf("Scored" to null), detect(before, game(live, 2, 2), minutes(22)))
    }

    @Test
    fun goalAfterStaleCheck_isSkipped() {
        val before = snapshotOf(game(live, 0, 0), minutes(20))
        assertEquals(emptyList<Any>(), detect(before, game(live, 1, 0), minutes(60)))
    }

    @Test
    fun overturnedGoal_isSilent() {
        val before = snapshotOf(game(live, 2, 0), minutes(20))
        assertEquals(emptyList<Any>(), detect(before, game(live, 1, 0), minutes(22)))
    }

    @Test
    fun liveToFinal_reportsEndOnly() {
        // The shootout winner's extra goal arrives with the final; no separate goal alert.
        val before = snapshotOf(game(live, 2, 2), minutes(150))
        assertEquals(listOf("Ended" to null), detect(before, game(GameStatus.Final(), 3, 2), minutes(152)))
    }

    @Test
    fun finalAfterStaleCheck_stillReportsEnd() {
        val before = snapshotOf(game(live, 2, 2), minutes(60))
        assertEquals(listOf("Ended" to null), detect(before, game(GameStatus.Final(), 3, 2), minutes(200)))
    }

    @Test
    fun finalStaysFinal_isSilent() {
        val before = snapshotOf(game(GameStatus.Final(), 3, 2), minutes(180))
        assertEquals(emptyList<Any>(), detect(before, game(GameStatus.Final(), 3, 2), minutes(182)))
    }

    @Test
    fun snapshot_recordsPhaseAndScores() {
        val snapshot = game(live, 2, 1).snapshot(minutes(30))
        assertEquals(GameSnapshot(GamePhase.LIVE, 2, 1, minutes(30).toEpochMilli()), snapshot)
    }

    // nextGamePoll

    @Test
    fun nextPoll_liveGame_pollsOften() {
        assertEquals(NotificationChecker.LIVE_POLL, NotificationChecker.nextGamePoll(listOf(game(live, 0, 0)), minutes(10)))
    }

    @Test
    fun nextPoll_laterToday_wakesAtPuckDrop() {
        val games = listOf(game(GameStatus.Scheduled))
        assertEquals(Duration.ofHours(3), NotificationChecker.nextGamePoll(games, minutes(-180)))
    }

    @Test
    fun nextPoll_pastStartTimeButNotLive_pollsOften() {
        val games = listOf(game(GameStatus.Scheduled))
        assertEquals(NotificationChecker.LIVE_POLL, NotificationChecker.nextGamePoll(games, minutes(5)))
    }

    @Test
    fun nextPoll_longOverdue_stopsPolling() {
        assertNull(NotificationChecker.nextGamePoll(listOf(game(GameStatus.Scheduled)), minutes(240)))
    }

    @Test
    fun nextPoll_farAwayOrDone_leavesItToPeriodic() {
        assertNull(NotificationChecker.nextGamePoll(listOf(game(GameStatus.Scheduled)), minutes(-24 * 60)))
        assertNull(NotificationChecker.nextGamePoll(listOf(game(GameStatus.Final(), 3, 2)), minutes(200)))
        assertTrue(NotificationChecker.nextGamePoll(emptyList(), minutes(0)) == null)
    }
}
