package io.rbware.zamboni.ui.scores

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.rbware.zamboni.R
import io.rbware.zamboni.data.model.Decision
import io.rbware.zamboni.data.model.Game
import io.rbware.zamboni.data.model.GameStatus
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date

/** "Today", "Yesterday" or "Tomorrow", or null for any other date. */
@Composable
fun relativeDayLabel(date: LocalDate, today: LocalDate): String? = when (date) {
    today -> stringResource(R.string.scores_today)
    today.minusDays(1) -> stringResource(R.string.scores_yesterday)
    today.plusDays(1) -> stringResource(R.string.scores_tomorrow)
    else -> null
}

/** Locale-appropriate short date, e.g. "Sat, Nov 14". Adds the year when it differs from [today]. */
@Composable
fun formatDate(date: LocalDate, today: LocalDate, withWeekday: Boolean = true): String {
    val locale = LocalConfiguration.current.locales[0]
    val skeleton = buildString {
        if (withWeekday) append("EEE")
        append("MMMd")
        if (date.year != today.year) append("yyyy")
    }
    return DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale).format(date)
}

/** Start time in the device's time zone, honoring the 12/24-hour system setting. */
@Composable
fun formatTime(instant: Instant): String =
    DateFormat.getTimeFormat(LocalContext.current).format(Date.from(instant))

@Composable
fun periodLabel(period: Int): String = when (period) {
    1 -> stringResource(R.string.period_1)
    2 -> stringResource(R.string.period_2)
    3 -> stringResource(R.string.period_3)
    4 -> stringResource(R.string.period_ot)
    else -> stringResource(R.string.period_multi_ot, period - 3)
}

/** Short status for a game card, e.g. "7:00 PM", "2nd 12:34", "Final/OT". */
@Composable
fun gameStatusText(game: Game): String = when (val status = game.status) {
    GameStatus.Scheduled -> formatTime(game.startTime)
    GameStatus.Postponed -> stringResource(R.string.status_postponed)
    GameStatus.Cancelled -> stringResource(R.string.status_cancelled)
    is GameStatus.Live -> {
        val period = status.period
        when {
            status.shootout -> stringResource(R.string.period_shootout)
            // Shown under the LIVE badge, so say something different.
            period == null -> stringResource(R.string.status_in_progress)
            status.intermission -> stringResource(R.string.status_intermission, periodLabel(period))
            status.clock != null -> "${periodLabel(period)} ${liveClockText(status)}"
            else -> periodLabel(period)
        }
    }
    is GameStatus.Final -> when (status.decision) {
        Decision.REGULATION -> stringResource(R.string.status_final)
        Decision.OVERTIME -> stringResource(R.string.status_final_ot)
        Decision.SHOOTOUT -> stringResource(R.string.status_final_so)
    }
}

/**
 * The period clock, counting down once a second while it's running, from the moment the data
 * was produced. Fixes itself on the next update (e.g. after a whistle the data hadn't shown yet).
 */
@Composable
private fun liveClockText(status: GameStatus.Live): String? {
    // When the source didn't say how old the data is, count from when it reached the screen.
    val shownAt = remember(status) { Instant.now() }
    if (!status.clockRunning || status.secondsRemaining == null) return status.clock
    val now by produceState(Instant.now(), status) {
        while (true) {
            value = Instant.now()
            // Tick on the second boundary so every card changes together.
            delay(1_000 - System.currentTimeMillis() % 1_000)
        }
    }
    val seconds = status.secondsRemainingAt(now, since = shownAt) ?: return status.clock
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
