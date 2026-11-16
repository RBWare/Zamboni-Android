package io.rbware.zamboni

import io.rbware.zamboni.data.model.Goal
import io.rbware.zamboni.data.model.GoalPlayer
import io.rbware.zamboni.data.model.GoalStrength
import io.rbware.zamboni.data.model.TeamSide
import io.rbware.zamboni.data.model.goalAtScore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** FLA at DAL, Nov 15 2026: Tkachuk 1-0, Johnston 1-1, Robertson 1-2 in overtime. */
class GoalAtScoreTest {

    private fun goal(id: Long, side: TeamSide, name: String, away: Int, home: Int, period: Int = 2) = Goal(
        id = id,
        side = side,
        period = period,
        isOvertime = period > 3,
        timeInPeriod = "1:00",
        awayScore = away,
        homeScore = home,
        strength = GoalStrength.EVEN,
        emptyNet = false,
        scorer = GoalPlayer(id = id, name = name, number = null, headshotUrl = null, seasonTotal = null),
        assists = emptyList(),
    )

    private val tkachuk = goal(1, TeamSide.AWAY, "M. Tkachuk", 1, 0)
    private val johnston = goal(2, TeamSide.HOME, "W. Johnston", 1, 1)
    private val robertson = goal(3, TeamSide.HOME, "J. Robertson", 1, 2, period = 4)

    @Test
    fun findsTheGoalThatMadeTheScore() {
        assertEquals("J. Robertson", listOf(tkachuk, johnston, robertson).goalAtScore(1, 2)?.scorer?.name)
        assertEquals("W. Johnston", listOf(tkachuk, johnston, robertson).goalAtScore(1, 1)?.scorer?.name)
    }

    @Test
    fun detailsBehindTheScoreGiveNoGoal() {
        // The score already says 1-2, but the details only have the first two goals: the
        // newest Dallas goal there (Johnston's) must not be taken for the new one.
        assertNull(listOf(tkachuk, johnston).goalAtScore(1, 2))
    }

    @Test
    fun noGoalsAtAll() {
        assertNull(emptyList<Goal>().goalAtScore(0, 1))
    }
}
