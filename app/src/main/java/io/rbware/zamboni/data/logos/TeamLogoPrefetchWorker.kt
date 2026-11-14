package io.rbware.zamboni.data.logos

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import coil3.SingletonImageLoader
import io.rbware.zamboni.ZamboniApplication
import java.util.concurrent.TimeUnit

/**
 * Runs [TeamLogoPrefetcher] through WorkManager so it only starts once the app actually has
 * network access, and retries with backoff if some logos fail to download.
 *
 * Running it directly from Application.onCreate is unreliable: a freshly started process can
 * have its network blocked for a moment, and every DNS lookup fails during that window.
 */
class TeamLogoPrefetchWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as ZamboniApplication
        val prefetcher = TeamLogoPrefetcher(
            context = app,
            imageLoader = SingletonImageLoader.get(app),
            teamRepository = app.container.teamRepository,
        )
        val failures = prefetcher.prefetchMissing()
        return when {
            failures == 0 -> Result.success()
            runAttemptCount < MAX_ATTEMPTS -> Result.retry()
            // Give up for now; the next app launch enqueues another attempt.
            else -> Result.success()
        }
    }

    companion object {
        private const val WORK_NAME = "team-logo-prefetch"
        private const val MAX_ATTEMPTS = 5

        /**
         * Safe to call on every launch: if logos are already cached the work finishes immediately,
         * and an attempt that is already queued or running is kept rather than duplicated.
         */
        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<TeamLogoPrefetchWorker>()
                .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
