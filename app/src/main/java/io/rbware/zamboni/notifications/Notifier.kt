package io.rbware.zamboni.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import io.rbware.zamboni.MainActivity
import io.rbware.zamboni.R
import io.rbware.zamboni.data.model.Decision
import io.rbware.zamboni.data.model.Game
import io.rbware.zamboni.data.model.GameStatus
import io.rbware.zamboni.data.model.Goal
import io.rbware.zamboni.data.model.GoalPlayer
import io.rbware.zamboni.data.model.NewsArticle
import io.rbware.zamboni.data.model.NotificationType
import io.rbware.zamboni.data.model.PowerPlay
import io.rbware.zamboni.data.model.Team
import io.rbware.zamboni.data.model.TeamSide

/**
 * Builds and posts notifications. Each [NotificationType] has its own channel, so people can
 * fine-tune sound and importance per type in system settings.
 *
 * Each game uses a single notification that is updated as it goes (start, goals, final),
 * so the shade shows the latest state of the game instead of a stack of old alerts.
 */
class Notifier(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    /** Safe to call repeatedly; existing channels keep the user's settings. */
    fun createChannels() {
        val channels = NotificationType.entries.map { type ->
            NotificationChannelCompat.Builder(type.channelId, type.importance)
                .setName(context.getString(type.label))
                .setDescription(context.getString(type.description))
                .apply {
                    // Updated every few seconds: never make a sound or vibrate.
                    if (type == NotificationType.LIVE_UPDATES) setSound(null, null).setVibrationEnabled(false)
                }
                .build()
        }
        manager.createNotificationChannelsCompat(channels)
    }

    /** False when the user has turned notifications off (or not granted the permission). */
    fun canPost(): Boolean = notificationsAllowed(context)

    /**
     * Posts a sample goal alert on the goals channel, so people can check how alerts look and
     * sound. Uses [team] (e.g. a favorite) in the title when given.
     */
    fun postTest(team: Team?): TestNotificationResult {
        if (!canPost()) return TestNotificationResult.APP_BLOCKED
        val channel = manager.getNotificationChannelCompat(NotificationType.GOALS.channelId)
        if (channel?.importance == NotificationManagerCompat.IMPORTANCE_NONE) {
            return TestNotificationResult.CHANNEL_BLOCKED
        }
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, NotificationType.GOALS.channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(
                team?.let { context.getString(R.string.notif_goal_title, it.name) }
                    ?: context.getString(R.string.notif_test_title),
            )
            .setContentText(context.getString(R.string.notif_test_body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationType.GOALS.priority)
            .build()
        notify(TEST_TAG, notification)
        return TestNotificationResult.SENT
    }

    /** @param scorer who scored, for goal events when the source reports it. */
    fun postGameEvent(event: GameEvent, scorer: GoalPlayer? = null) {
        val game = event.game
        val (type, title, text) = when (event) {
            is GameEvent.Started -> Triple(
                NotificationType.GAME_START,
                matchup(game),
                context.getString(R.string.notif_game_started),
            )
            is GameEvent.Scored -> Triple(
                NotificationType.GOALS,
                when (event.side) {
                    TeamSide.AWAY -> context.getString(R.string.notif_goal_title, game.away.name)
                    TeamSide.HOME -> context.getString(R.string.notif_goal_title, game.home.name)
                    null -> context.getString(R.string.notif_goals_title, matchup(game))
                },
                listOfNotNull(scorer?.let(::scorerLabel), scoreLine(game)).joinToString(" - "),
            )
            is GameEvent.Ended -> Triple(
                NotificationType.FINAL_SCORE,
                game.winner?.let { context.getString(R.string.notif_final_title_winner, it.name) }
                    ?: context.getString(R.string.notif_final_title, matchup(game)),
                scoreLine(game),
            )
        }
        val notification = NotificationCompat.Builder(context, type.channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openGameIntent(game.id))
            .setAutoCancel(true)
            .setPriority(type.priority)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .build()
        notify(GAME_TAG_PREFIX + game.id, notification)
    }

    fun postArticle(article: NewsArticle) {
        val open = PendingIntent.getActivity(
            context,
            article.id.hashCode(),
            Intent(Intent.ACTION_VIEW, article.link.toUri()),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, NotificationType.BREAKING_NEWS.channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(article.title)
            .setContentText(article.source.name)
            .apply { article.summary?.let { setStyle(NotificationCompat.BigTextStyle().bigText(it)) } }
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationType.BREAKING_NEWS.priority)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .apply { article.publishedAt?.let { setWhen(it.toEpochMilli()) } }
            .build()
        notify(NEWS_TAG_PREFIX + article.id, notification)
    }

    // Permission is checked by canPost(), which callers use before posting; a revoked
    // permission between the two just makes notify() a no-op.
    @SuppressLint("MissingPermission")
    private fun notify(tag: String, notification: android.app.Notification) {
        if (!canPost()) return
        try {
            manager.notify(tag, NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Permission revoked mid-check; nothing to do.
        }
    }

    private fun openGameIntent(gameId: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_GAME_ID, gameId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context,
            gameId.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /** "Auston Matthews (12)", or just the name when the season total is unknown. */
    private fun scorerLabel(player: GoalPlayer): String =
        player.seasonTotal?.let { context.getString(R.string.notif_scorer, player.name, it) } ?: player.name

    private fun matchup(game: Game) = context.getString(R.string.notif_matchup, game.away.name, game.home.name)

    /** "TOR 2, MTL 1 (2nd)" while live, "TOR 3, MTL 2 (OT)" when final past regulation. */
    private fun scoreLine(game: Game): String {
        val score = context.getString(
            R.string.notif_score_line,
            game.away.abbreviation, game.awayScore ?: 0,
            game.home.abbreviation, game.homeScore ?: 0,
        )
        val suffix = when (val status = game.status) {
            is GameStatus.Live -> when {
                status.shootout -> context.getString(R.string.period_shootout)
                status.period != null -> periodLabel(status.period)
                else -> null
            }
            is GameStatus.Final -> when (status.decision) {
                Decision.REGULATION -> null
                Decision.OVERTIME -> context.getString(R.string.period_ot)
                Decision.SHOOTOUT -> context.getString(R.string.period_shootout)
            }
            else -> null
        }
        return if (suffix == null) score else "$score ($suffix)"
    }

    private fun periodLabel(period: Int): String = when (period) {
        1 -> context.getString(R.string.period_1)
        2 -> context.getString(R.string.period_2)
        3 -> context.getString(R.string.period_3)
        4 -> context.getString(R.string.period_ot)
        else -> context.getString(R.string.period_multi_ot, period - 3)
    }

    /**
     * A Live Update for [game]: an ongoing notification Android shows as a chip in the status
     * bar (with [favoriteTeamIds]'s team's score first) and at the top of the shade, with the period
     * and time left on the second line.
     *
     * @param lastGoal the most recent goal, when known.
     * @param shots shots on goal as (away, home), when known.
     * @param powerPlay the power play under way, if any.
     */
    fun buildLiveUpdate(
        game: Game,
        lastGoal: Goal?,
        favoriteTeamIds: Set<String>,
        shots: Pair<Int, Int>? = null,
        powerPlay: PowerPlay? = null,
    ): Notification {
        val awayScore = game.awayScore ?: 0
        val homeScore = game.homeScore ?: 0
        val favoriteIsAway = game.away.id in favoriteTeamIds && game.home.id !in favoriteTeamIds
        val chip = if (favoriteIsAway) {
            context.getString(R.string.live_chip, game.away.abbreviation, awayScore, homeScore)
        } else {
            context.getString(R.string.live_chip, game.home.abbreviation, homeScore, awayScore)
        }
        val builder = NotificationCompat.Builder(context, NotificationType.LIVE_UPDATES.channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.live_title, game.away.name, awayScore, game.home.name, homeScore))
            .setContentIntent(openGameIntent(game.id))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setRequestPromotedOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            // A score isn't private: show it in full on the lock screen, even when the user hides
            // sensitive notification content there (otherwise only the app name shows).
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setShowWhen(false)
        when (val status = game.status) {
            is GameStatus.Live -> {
                builder.setShortCriticalText(chip)
                val period = status.period?.let(::periodLabel)
                // While the clock runs, the time left as of now (the data may be a few seconds
                // old). The service reposts every few seconds during play, so it stays close; a
                // system countdown beside the title would crowd out the score.
                val now = java.time.Instant.now()
                val clock = status.secondsRemainingAt(now)
                    ?.takeIf { status.clockRunning }
                    ?.let { "%d:%02d".format(it / 60, it % 60) }
                    ?: status.clock
                val state = when {
                    status.shootout -> context.getString(R.string.period_shootout)
                    status.intermission && period != null -> context.getString(R.string.status_intermission, period)
                    period != null && clock != null -> context.getString(R.string.live_period_clock, period, clock)
                    else -> period ?: context.getString(R.string.status_in_progress)
                }
                // "1st, 2:43 left, Shots 8-6, NYR PP 1:07": the power play only while one is on.
                val shotsText = shots?.takeUnless { status.shootout }
                    ?.let { (away, home) -> context.getString(R.string.live_shots, away, home) }
                val powerPlayText = powerPlay?.takeUnless { status.shootout }?.let { pp ->
                    val team = if (pp.side == TeamSide.HOME) game.home else game.away
                    pp.secondsRemainingAt(status, now)
                        ?.let { context.getString(R.string.live_power_play, team.abbreviation, "%d:%02d".format(it / 60, it % 60)) }
                        ?: context.getString(R.string.live_power_play_no_time, team.abbreviation)
                }
                val text = listOfNotNull(state, shotsText, powerPlayText).joinToString(", ")
                builder.setContentText(text)
                lastGoal?.let { goal ->
                    val team = if (goal.side == TeamSide.HOME) game.home else game.away
                    val line = context.getString(R.string.live_last_goal, scorerLabel(goal.scorer), team.abbreviation)
                    builder.setStyle(NotificationCompat.BigTextStyle().bigText("$text\n$line"))
                }
            }
            else -> {
                val started = game.startTime <= java.time.Instant.now()
                val time = DateFormat.getTimeFormat(context).format(java.util.Date.from(game.startTime))
                builder.setContentText(
                    if (started) context.getString(R.string.live_starting_soon) else context.getString(R.string.live_starting_at, time),
                )
                builder.setShortCriticalText(if (started) context.getString(R.string.live_starting_soon) else time)
            }
        }
        return builder.build()
    }

    /** A placeholder Live Update, shown for a moment while the live service loads the game. */
    fun buildLiveUpdatePlaceholder(): Notification =
        NotificationCompat.Builder(context, NotificationType.LIVE_UPDATES.channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.live_loading))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

    private companion object {
        const val NOTIFICATION_ID = 1
        const val GAME_TAG_PREFIX = "game:"
        const val NEWS_TAG_PREFIX = "news:"
        const val TEST_TAG = "test"
    }
}

enum class TestNotificationResult {
    SENT,

    /** Notifications are off for the whole app (or the permission isn't granted). */
    APP_BLOCKED,

    /** The goals channel the test uses is turned off in system settings. */
    CHANNEL_BLOCKED,
}

/** Whether the app may post notifications at all: permission granted and not turned off. */
fun notificationsAllowed(context: Context): Boolean {
    val permitted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    return permitted && NotificationManagerCompat.from(context).areNotificationsEnabled()
}

/** The system screen with all of this app's notification settings. */
fun appNotificationSettingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

/** The system screen for one notification type's channel (sound, vibration, pop-up). */
fun channelSettingsIntent(context: Context, type: NotificationType): Intent =
    Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .putExtra(Settings.EXTRA_CHANNEL_ID, type.channelId)

/** Channel IDs are stored by the system, so don't change them once released. */
val NotificationType.channelId: String
    get() = when (this) {
        NotificationType.GAME_START -> "game_start"
        NotificationType.GOALS -> "goals"
        NotificationType.FINAL_SCORE -> "final_score"
        NotificationType.LIVE_UPDATES -> "live_updates"
        NotificationType.BREAKING_NEWS -> "breaking_news"
    }

/** Goals pop up as heads-up alerts; the rest arrive quietly in the shade with a sound. */
private val NotificationType.importance: Int
    get() = if (this == NotificationType.GOALS) {
        NotificationManagerCompat.IMPORTANCE_HIGH
    } else {
        NotificationManagerCompat.IMPORTANCE_DEFAULT
    }

/** Pre-Android 8 equivalent of [importance]. */
private val NotificationType.priority: Int
    get() = if (this == NotificationType.GOALS) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT
