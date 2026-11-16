package io.rbware.zamboni.notifications

import android.util.Log
import io.rbware.zamboni.data.model.Game
import io.rbware.zamboni.data.model.GoalPlayer
import io.rbware.zamboni.data.model.NewsArticle
import io.rbware.zamboni.data.model.NotificationType
import io.rbware.zamboni.data.news.NewsRepository
import io.rbware.zamboni.data.news.NewsSourceRepository
import io.rbware.zamboni.data.preferences.UserPreferences
import io.rbware.zamboni.data.preferences.UserPreferencesRepository
import io.rbware.zamboni.data.scores.ScoresRepository
import io.rbware.zamboni.data.teams.TeamRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * One background check: fetches the favorite teams' games (and news, when due), compares them
 * with what was seen last time, posts notifications for anything new, and says when to look again.
 */
class NotificationChecker(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val scoresRepository: ScoresRepository,
    private val teamRepository: TeamRepository,
    private val newsSourceRepository: NewsSourceRepository,
    private val newsRepository: NewsRepository,
    private val stateStore: NotificationStateStore,
    private val notifier: Notifier,
    private val clock: () -> Instant = Instant::now,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) {
    /** The periodic and the chained worker can overlap; run one check at a time. */
    private val mutex = Mutex()

    /**
     * @return how soon to check again, or null when nothing needs watching more closely than the
     *   periodic check provides.
     */
    suspend fun check(): Duration? = mutex.withLock {
        val prefs = userPreferencesRepository.userPreferences.first()
        val now = clock()
        val state = stateStore.load()
        val canPost = notifier.canPost()

        val games = checkGames(prefs, state, now)
        val news = checkNews(prefs, state, now)

        // Saving and posting must not be split by cancellation, or an event could be marked
        // as seen without ever being shown.
        withContext(NonCancellable) {
            stateStore.save(state.copy(games = games.snapshots).let(news.applyTo))
            if (canPost) {
                games.alerts.forEach { notifier.postGameEvent(it.event, it.scorer) }
                news.alerts.forEach(notifier::postArticle)
            }
        }
        if (canPost) games.nextPoll else null
    }

    private class GameAlert(val event: GameEvent, val scorer: GoalPlayer?)

    private class GameResult(
        val snapshots: Map<String, GameSnapshot>,
        val alerts: List<GameAlert>,
        val nextPoll: Duration?,
    )

    private suspend fun checkGames(prefs: UserPreferences, state: NotificationState, now: Instant): GameResult {
        val enabled = prefs.notificationTypes.intersect(GAME_TYPES)
        if (enabled.isEmpty() || prefs.favoriteTeamIds.isEmpty()) {
            // Forget everything, so turning alerts back on starts fresh instead of replaying.
            return GameResult(emptyMap(), emptyList(), null)
        }

        val (games, anyFailed) = fetchFavoriteGames(prefs.favoriteTeamIds, now)
        val snapshots = state.games.filterValues { snapshot ->
            Duration.between(Instant.ofEpochMilli(snapshot.observedAt), now) < SNAPSHOT_RETENTION
        }.toMutableMap()
        val alerts = mutableListOf<GameAlert>()

        for (game in games) {
            val events = GameEventDetector.detect(snapshots[game.id], game, now)
            snapshots[game.id] = game.snapshot(now)
            for (event in events) {
                val type = when (event) {
                    is GameEvent.Started -> NotificationType.GAME_START
                    is GameEvent.Scored -> NotificationType.GOALS
                    is GameEvent.Ended -> NotificationType.FINAL_SCORE
                }
                if (type !in enabled) continue
                val scorer = (event as? GameEvent.Scored)?.let { latestScorer(it) }
                alerts += GameAlert(event, scorer)
            }
        }

        val nextPoll = nextGamePoll(games, now)
            // A failed fetch during a live game shouldn't wait for the periodic check.
            ?: RETRY_DELAY.takeIf { anyFailed && state.games.values.any { it.phase == GamePhase.LIVE } }
        return GameResult(snapshots, alerts, nextPoll)
    }

    /**
     * Today's games, plus yesterday's in the early hours, since late West Coast games are
     * still on the previous game day after midnight.
     */
    private suspend fun fetchFavoriteGames(favorites: Set<String>, now: Instant): Pair<List<Game>, Boolean> {
        val local = ZonedDateTime.ofInstant(now, zone())
        val today = local.toLocalDate()
        val dates = buildList {
            if (local.hour < LATE_GAME_CUTOFF_HOUR) add(today.minusDays(1))
            add(today)
        }
        var anyFailed = false
        val games = dates.flatMap { date: LocalDate ->
            try {
                scoresRepository.getSchedule(date).games
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't load schedule for $date: $e")
                anyFailed = true
                emptyList()
            }
        }
        return games.filter { it.involvesAny(favorites) }.distinctBy { it.id } to anyFailed
    }

    /** The scorer of the newest goal by the side that scored, if the source reports it. */
    private suspend fun latestScorer(event: GameEvent.Scored): GoalPlayer? {
        val side = event.side ?: return null
        return try {
            scoresRepository.getGameDetails(event.game.id).goals.lastOrNull { it.side == side }?.scorer
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The alert still goes out, just without the scorer's name.
            Log.w(TAG, "Couldn't load goal details for ${event.game.id}: $e")
            null
        }
    }

    private class NewsResult(val alerts: List<NewsArticle>, val applyTo: (NotificationState) -> NotificationState)

    private suspend fun checkNews(prefs: UserPreferences, state: NotificationState, now: Instant): NewsResult {
        if (NotificationType.BREAKING_NEWS !in prefs.notificationTypes) {
            return NewsResult(emptyList()) {
                it.copy(seenArticleIds = emptySet(), newsSourceIds = emptySet(), lastNewsCheck = null)
            }
        }
        val unchanged = NewsResult(emptyList()) { it }
        val lastCheck = state.lastNewsCheck?.let(Instant::ofEpochMilli)
        if (lastCheck != null && Duration.between(lastCheck, now) < NEWS_INTERVAL) return unchanged

        val sources = try {
            newsSourceRepository.getSources().filter { it.id in prefs.newsSourceIds }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't load news sources: $e")
            return unchanged
        }
        if (sources.isEmpty()) return unchanged

        val feed = try {
            newsRepository.getArticles(sources)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't load news: $e")
            return unchanged
        }
        val loadedSourceIds = (sources - feed.failedSources.toSet()).map { it.id }.toSet()
        if (loadedSourceIds.isEmpty()) return unchanged

        // Articles from a source that wasn't part of the last check are all "new"; take them as
        // the starting point rather than alerting about the whole feed.
        val alerts = if (lastCheck == null) {
            emptyList()
        } else {
            val matcher = NewsAlertMatcher(favoriteTeams(prefs.favoriteTeamIds))
            matcher.select(
                articles = feed.articles.filter { it.source.id in state.newsSourceIds },
                seenIds = state.seenArticleIds,
                now = now,
            )
        }
        val currentIds = feed.articles.mapTo(mutableSetOf()) { it.id }
        // Keep old IDs only while some source failed, so its articles aren't alerted when it recovers.
        val seen = if (feed.failedSources.isEmpty()) currentIds else (currentIds + state.seenArticleIds)
        return NewsResult(alerts) {
            it.copy(
                seenArticleIds = seen.take(MAX_SEEN_ARTICLES).toSet(),
                newsSourceIds = state.newsSourceIds.intersect(prefs.newsSourceIds) + loadedSourceIds,
                lastNewsCheck = now.toEpochMilli(),
            )
        }
    }

    private suspend fun favoriteTeams(ids: Set<String>) = try {
        teamRepository.getTeams().filter { it.id in ids }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // Without team names, only "breaking" headlines match.
        Log.w(TAG, "Couldn't load teams: $e")
        emptyList()
    }

    companion object {
        private const val TAG = "NotificationChecker"

        private val GAME_TYPES = setOf(NotificationType.GAME_START, NotificationType.GOALS, NotificationType.FINAL_SCORE)

        /** Poll interval while a favorite team is playing. */
        val LIVE_POLL: Duration = Duration.ofMinutes(2)

        /** Schedule a wake-up for puck drop when it is at most this far away. */
        val LOOKAHEAD: Duration = Duration.ofHours(12)

        /**
         * A game still listed as scheduled this long after its start time is assumed to be
         * delayed indefinitely (or a stale listing) and no longer polled for.
         */
        val OVERDUE_LIMIT: Duration = Duration.ofHours(3)

        val RETRY_DELAY: Duration = Duration.ofMinutes(5)
        val NEWS_INTERVAL: Duration = Duration.ofMinutes(30)
        private val SNAPSHOT_RETENTION: Duration = Duration.ofDays(2)
        private const val LATE_GAME_CUTOFF_HOUR = 6
        private const val MAX_SEEN_ARTICLES = 2000

        /**
         * How soon to check [games] again: often while one is live or about to start (leagues
         * usually mark a game live a few minutes after its listed time), at puck drop when the
         * next game is later today, otherwise null.
         */
        fun nextGamePoll(games: List<Game>, now: Instant): Duration? {
            if (games.any { it.phase == GamePhase.LIVE }) return LIVE_POLL
            val nextStart = games
                .filter { it.phase == GamePhase.SCHEDULED && Duration.between(it.startTime, now) < OVERDUE_LIMIT }
                .minOfOrNull { it.startTime }
                ?: return null
            val untilStart = Duration.between(now, nextStart)
            return when {
                untilStart <= LIVE_POLL -> LIVE_POLL
                untilStart <= LOOKAHEAD -> untilStart
                else -> null
            }
        }
    }
}
