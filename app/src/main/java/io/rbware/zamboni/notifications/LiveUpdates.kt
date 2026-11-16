package io.rbware.zamboni.notifications

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.rbware.zamboni.ZamboniApplication
import io.rbware.zamboni.data.model.Game
import io.rbware.zamboni.data.model.GameStatus
import io.rbware.zamboni.data.model.Goal
import io.rbware.zamboni.data.model.NotificationType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration
import java.time.Instant
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

/**
 * How a notification check hands off to Live Updates. Separate from Android so the checker
 * stays testable.
 */
interface LiveUpdateHost {
    /** Makes sure the live service is running. False if Android doesn't allow starting it now. */
    fun startService(): Boolean

    /**
     * Shows Live Updates for [games] straight from a check, for when the service can't run.
     * Clears any that are no longer wanted (all of them when [games] is empty).
     */
    fun showFromCheck(games: List<Game>, favoriteTeamIds: Set<String>)

    /** Starts the live service at [at] (puck drop), or cancels a pending start when null. */
    fun scheduleStart(at: Instant?)

    /** Asks a running live service to refresh now (e.g. after a game was muted). */
    fun refresh()

    object None : LiveUpdateHost {
        override fun startService() = true
        override fun showFromCheck(games: List<Game>, favoriteTeamIds: Set<String>) = Unit
        override fun scheduleStart(at: Instant?) = Unit
        override fun refresh() = Unit
    }
}

/**
 * Live Updates on Android: a foreground service keeps them current while a favorite team
 * plays. Android only lets an app start one while it's on screen, from an exact alarm, or in a
 * few other cases, so a puck-drop alarm starts it when "Alarms & reminders" is allowed, and
 * opening the app starts it otherwise. Until then the regular checks show the updates.
 */
class AndroidLiveUpdateHost(private val context: Context) : LiveUpdateHost {

    override fun startService(): Boolean {
        if (LiveGameService.running) return true
        return try {
            ContextCompat.startForegroundService(context, Intent(context, LiveGameService::class.java))
            true
        } catch (e: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException (Android 12+) is an IllegalStateException.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e !is ForegroundServiceStartNotAllowedException) throw e
            Log.i(TAG, "Live service not allowed to start from the background yet")
            false
        }
    }

    override fun refresh() {
        if (!LiveGameService.running) return
        try {
            context.startService(Intent(context, LiveGameService::class.java).setAction(LiveGameService.ACTION_REFRESH))
        } catch (e: IllegalStateException) {
            // Not allowed from the background; it refreshes on its own shortly anyway.
        }
    }

    override fun showFromCheck(games: List<Game>, favoriteTeamIds: Set<String>) {
        if (LiveGameService.running) return
        val notifier = (context.applicationContext as ZamboniApplication).container.notifier
        LiveUpdatePoster(context).post(games.map { LiveGame(it, null) }, favoriteTeamIds) { game ->
            notifier.buildLiveUpdate(game.game, null, favoriteTeamIds)
        }
    }

    override fun scheduleStart(at: Instant?) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, LiveUpdateAlarmReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        if (at == null || !canScheduleExactAlarms(context)) {
            alarms.cancel(intent)
            return
        }
        val wakeAt = at.minus(NotificationChecker.LIVE_UPDATE_LEAD)
        try {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, wakeAt.toEpochMilli(), intent)
        } catch (e: SecurityException) {
            // The permission was revoked in between.
        }
    }

    private companion object {
        const val TAG = "LiveUpdates"
    }
}

/** Whether exact alarms (and so starting Live Updates at puck drop) are allowed. */
fun canScheduleExactAlarms(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true

/** Puck drop: start the live service (allowed from an exact alarm). */
class LiveUpdateAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AndroidLiveUpdateHost(context.applicationContext).startService()
    }
}

/** A game to show, with its newest goal when the details were loaded. */
data class LiveGame(val game: Game, val lastGoal: Goal?)

/**
 * Posts one Live Update per game and removes stale ones. The first game's can be the
 * service's own notification ([foreground]); the others are separate.
 */
private class LiveUpdatePoster(private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    @SuppressLint("MissingPermission") // Callers check notificationsAllowed() first.
    fun post(
        games: List<LiveGame>,
        favoriteTeamIds: Set<String>,
        foreground: ((Notification) -> Unit)? = null,
        build: (LiveGame) -> Notification,
    ) {
        val separate = if (foreground != null) games.drop(1) else games
        if (foreground != null) games.firstOrNull()?.let { foreground(build(it)) }
        val keep = separate.mapTo(mutableSetOf()) { TAG_PREFIX + it.game.id }
        // Remove updates for games that ended or were stopped (also ones left by the other path).
        manager.activeNotifications
            .filter { it.tag?.startsWith(TAG_PREFIX) == true && it.tag !in keep }
            .forEach { manager.cancel(it.tag, it.id) }
        if (!notificationsAllowed(context)) return
        separate.forEach { game ->
            try {
                manager.notify(TAG_PREFIX + game.game.id, SEPARATE_ID, build(game))
            } catch (e: SecurityException) {
                // Permission revoked mid-update.
            }
        }
    }

    companion object {
        const val TAG_PREFIX = "live:"
        const val SEPARATE_ID = 2
    }
}

/**
 * Keeps Live Updates current while a favorite team's game is on: refreshes as often as the
 * NHL's data changes (every 10-20 seconds), runs the regular notification check alongside so goal
 * alerts keep pace, and stops itself when no game needs following.
 */
class LiveGameService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private var started = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Android requires the notification right away, before anything is loaded.
        if (!started) goForeground(container.notifier.buildLiveUpdatePlaceholder())
        if (!started) {
            started = true
            running = true
            scope.launch { run() }
        } else {
            wake.trySend(Unit)
        }
        return START_NOT_STICKY
    }

    private val container get() = (application as ZamboniApplication).container

    private suspend fun run() {
        val poster = LiveUpdatePoster(this)
        var lastCheck: Instant? = null
        try {
            while (true) {
                val prefs = container.userPreferencesRepository.userPreferences.first()
                if (NotificationType.LIVE_UPDATES !in prefs.notificationTypes || !notificationsAllowed(this)) break
                val now = Instant.now()
                // The regular check posts goal and final alerts; run it often so they keep pace.
                if (lastCheck == null || Duration.between(lastCheck, now) >= CHECK_INTERVAL) {
                    lastCheck = now
                    try {
                        container.notificationChecker.check()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "Check failed: $e")
                    }
                }
                val (games, failed) = container.notificationChecker.favoriteGames()
                val muted = container.notificationChecker.mutedGameIds.first()
                val wanted = games.filter { NotificationChecker.wantsLiveUpdate(it, now) && it.id !in muted }
                if (wanted.isEmpty() && !failed) break
                val shown = wanted.map { game -> withDetails(game) }
                if (shown.isNotEmpty()) {
                    poster.post(shown, prefs.favoriteTeamIds, foreground = ::goForeground) { live ->
                        container.notifier.buildLiveUpdate(live.game, live.lastGoal, prefs.favoriteTeamIds)
                    }
                }
                val delay = shown.mapNotNull { it.refresh }.minOrNull()?.coerceIn(MIN_REFRESH, MAX_REFRESH) ?: MAX_REFRESH
                withTimeoutOrNull(delay.toMillis()) { wake.receive() }
            }
        } finally {
            running = false
            poster.post(emptyList(), emptySet()) { error("nothing to build") }
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private val LiveGame.refresh: Duration? get() = refreshes[game.id]
    private val refreshes = mutableMapOf<String, Duration?>()

    /** Live games get their details (fresher than the schedule, and the last goal). */
    private suspend fun withDetails(game: Game): LiveGame {
        if (game.status !is GameStatus.Live) {
            refreshes[game.id] = MAX_REFRESH
            return LiveGame(game, null)
        }
        return try {
            val details = container.scoresRepository.getGameDetails(game.id)
            refreshes[game.id] = details.suggestedRefresh?.toJavaDuration()
            LiveGame(game.updatedWith(details.game), details.goals.lastOrNull())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Details for ${game.id} failed: $e")
            refreshes[game.id] = MIN_REFRESH.multipliedBy(2)
            LiveGame(game, null)
        }
    }

    private fun goForeground(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, FOREGROUND_ID, notification, type)
    }

    override fun onDestroy() {
        running = false
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "LiveGameService"
        const val ACTION_REFRESH = "io.rbware.zamboni.action.REFRESH_LIVE"
        private const val FOREGROUND_ID = 3
        private val MIN_REFRESH: Duration = 5.seconds.toJavaDuration()
        private val MAX_REFRESH: Duration = 30.seconds.toJavaDuration()
        private val CHECK_INTERVAL: Duration = Duration.ofSeconds(20)

        /** True while the service is following games (in this process). */
        @Volatile
        var running = false
            private set
    }
}
