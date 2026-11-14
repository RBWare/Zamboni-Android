package io.rbware.zamboni.data.scores

import io.rbware.zamboni.data.model.Decision
import io.rbware.zamboni.data.model.Game
import io.rbware.zamboni.data.model.GameStatus
import io.rbware.zamboni.data.model.League
import io.rbware.zamboni.data.placeholder.PlaceholderHockeyData
import kotlinx.coroutines.delay
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import kotlin.random.Random

/**
 * Generates fake games until the hockeynerd.com API is integrated.
 *
 * The schedule for a given date is deterministic (seeded by the date), and each game's status
 * is derived from the clock: past start times become live or final, future ones are scheduled.
 */
class PlaceholderScoresRepository(
    private val clock: Clock = Clock.systemDefaultZone(),
) : ScoresRepository {

    override suspend fun getLeagues(): List<League> = PlaceholderHockeyData.leagues

    override suspend fun getGames(date: LocalDate): List<Game> {
        delay(400) // Simulate network latency so loading states are visible.
        val now = clock.instant()
        return PlaceholderHockeyData.leagues.flatMapIndexed { leagueIndex, league ->
            val random = Random(date.toEpochDay() * 31 + leagueIndex)
            val teams = PlaceholderHockeyData.teams.filter { it.league == league }.shuffled(random)
            val gameCount = random.nextInt(0, teams.size / 4 + 1)
            (0 until gameCount).map { i ->
                val startOffsetMinutes = START_OFFSETS[random.nextInt(START_OFFSETS.size)]
                val start = date.atTime(LocalTime.of(19, 0).plusMinutes(startOffsetMinutes))
                    .atZone(clock.zone)
                    .toInstant()
                val elapsed = Duration.between(start, now).toMinutes()
                val scoreRandom = Random(date.toEpochDay() * 997 + leagueIndex * 101 + i)

                var awayScore: Int? = null
                var homeScore: Int? = null
                val status: GameStatus = when {
                    elapsed < 0 -> GameStatus.Scheduled
                    elapsed >= GAME_LENGTH_MINUTES -> {
                        var away = scoreRandom.nextInt(0, 7)
                        var home = scoreRandom.nextInt(0, 7)
                        val decision = if (away == home) {
                            if (scoreRandom.nextBoolean()) away++ else home++
                            if (scoreRandom.nextBoolean()) Decision.OVERTIME else Decision.SHOOTOUT
                        } else {
                            Decision.REGULATION
                        }
                        awayScore = away
                        homeScore = home
                        GameStatus.Final(decision)
                    }
                    else -> {
                        val period = (elapsed / PERIOD_BLOCK_MINUTES).toInt() + 1
                        val minutesIntoBlock = elapsed % PERIOD_BLOCK_MINUTES
                        val intermission = minutesIntoBlock >= PLAY_MINUTES_PER_PERIOD && period < 3
                        val clockText = if (intermission) {
                            null
                        } else {
                            // Map wall-clock minutes onto the 20:00 game clock.
                            val remainingSeconds = (20 * 60 - minutesIntoBlock * 60 * 20 / PLAY_MINUTES_PER_PERIOD)
                                .coerceAtLeast(0)
                            "%d:%02d".format(remainingSeconds / 60, remainingSeconds % 60)
                        }
                        awayScore = scoreRandom.nextInt(0, period + 2)
                        homeScore = scoreRandom.nextInt(0, period + 2)
                        GameStatus.Live(period = period.coerceAtMost(3), clock = clockText, intermission = intermission)
                    }
                }

                Game(
                    id = "${league.id}-$date-$i",
                    league = league,
                    startTime = start,
                    status = status,
                    away = teams[i * 2],
                    home = teams[i * 2 + 1],
                    awayScore = awayScore,
                    homeScore = homeScore,
                )
            }
        }
    }

    private companion object {
        val START_OFFSETS = longArrayOf(0, 0, 0, 30, 60, -60, -180, 180)
        const val PERIOD_BLOCK_MINUTES = 55L
        const val PLAY_MINUTES_PER_PERIOD = 40L
        const val GAME_LENGTH_MINUTES = 160L
    }
}
