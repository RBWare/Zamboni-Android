package io.rbware.zamboni.notifications

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.rbware.zamboni.ZamboniApplication
import kotlinx.coroutines.CancellationException
import java.time.Duration

/**
 * Runs a [NotificationChecker] check, then schedules the next one if the checker wants to look
 * again sooner than the periodic check would.
 */
class NotificationWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val checker = (applicationContext as ZamboniApplication).container.notificationChecker
        val next = try {
            checker.check()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Notification check failed: $e")
            null
        }
        if (next != null) {
            NotificationScheduler.scheduleNext(applicationContext, next, fromChain = inputData.getBoolean(KEY_CHAINED, false))
        }
        // Always succeed: the periodic check is the retry, and a failed result would also fail
        // anything appended to this work.
        return Result.success()
    }

    internal companion object {
        const val TAG = "NotificationWorker"
        const val KEY_CHAINED = "chained"
    }
}

/**
 * Keeps notification checks scheduled:
 *  - A periodic check every 15 minutes (WorkManager's minimum) as a safety net, which also
 *    covers news.
 *  - A chain of one-time checks while games need closer watching: every couple of minutes
 *    while a favorite team is playing, and a wake-up at puck drop. Each check in the chain
 *    schedules the next.
 */
object NotificationScheduler {
    private const val PERIODIC_WORK = "notification-check-periodic"
    private const val CHAIN_WORK = "notification-check-chain"

    private val networkConstraint = Constraints(requiredNetworkType = NetworkType.CONNECTED)

    /**
     * Call on launch and whenever notification preferences change. Turns checks on or off.
     *
     * @param checkNow also run a check right away, so changed settings take effect immediately.
     *   This replaces any pending chained check (e.g. a puck-drop wake-up for a team no longer
     *   followed); the new check schedules whatever is still needed.
     */
    fun sync(context: Context, enabled: Boolean, checkNow: Boolean) {
        val workManager = WorkManager.getInstance(context)
        if (!enabled) {
            workManager.cancelUniqueWork(PERIODIC_WORK)
            workManager.cancelUniqueWork(CHAIN_WORK)
            return
        }
        val periodic = PeriodicWorkRequestBuilder<NotificationWorker>(Duration.ofMinutes(15))
            .setConstraints(networkConstraint)
            .build()
        workManager.enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, periodic)
        if (checkNow) {
            workManager.enqueueUniqueWork(CHAIN_WORK, ExistingWorkPolicy.REPLACE, chainRequest(Duration.ZERO))
        }
    }

    /**
     * @param fromChain true when called by a check in the chain itself. That check is still
     *   running, so the next one is appended to it (replacing it would cancel the caller).
     *   Otherwise (the periodic check) an existing chain is left alone.
     */
    internal fun scheduleNext(context: Context, delay: Duration, fromChain: Boolean) {
        val policy = if (fromChain) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.KEEP
        WorkManager.getInstance(context).enqueueUniqueWork(CHAIN_WORK, policy, chainRequest(delay))
    }

    private fun chainRequest(delay: Duration) = OneTimeWorkRequestBuilder<NotificationWorker>()
        .setInitialDelay(delay)
        .setConstraints(networkConstraint)
        .setInputData(workDataOf(NotificationWorker.KEY_CHAINED to true))
        .build()
}
