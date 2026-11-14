package io.rbware.zamboni.data.logos

import android.content.Context
import android.util.Log
import coil3.ImageLoader
import coil3.request.CachePolicy
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import io.rbware.zamboni.data.teams.TeamRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Downloads every team logo (light and dark) into the image loader's disk cache so logos are
 * available immediately, and offline, wherever a team is shown.
 *
 * Logos already in the disk cache are skipped, so after the first successful run this does no
 * network work. Anything that fails (e.g. no connection) is retried on the next run.
 */
class TeamLogoPrefetcher(
    private val context: Context,
    private val imageLoader: ImageLoader,
    private val teamRepository: TeamRepository,
) {
    /** @return the number of logos that failed to download. */
    suspend fun prefetchMissing(): Int {
        val diskCache = imageLoader.diskCache ?: return 0
        val missing = teamRepository.getTeams()
            .flatMap { it.logo?.all.orEmpty() }
            .distinct()
            .filter { url -> diskCache.openSnapshot(url)?.use { true } != true }
        if (missing.isEmpty()) return 0

        val permits = Semaphore(MAX_CONCURRENT_DOWNLOADS)
        val failures = coroutineScope {
            missing.map { url ->
                async {
                    permits.withPermit {
                        val request = ImageRequest.Builder(context)
                            .data(url)
                            // Only the disk copy matters here; don't fill the memory cache.
                            .memoryCachePolicy(CachePolicy.DISABLED)
                            .build()
                        val result = imageLoader.execute(request)
                        if (result is ErrorResult) Log.w(TAG, "Failed to prefetch $url: ${result.throwable}")
                        result is ErrorResult
                    }
                }
            }.awaitAll().count { it }
        }
        Log.d(TAG, "Prefetched ${missing.size - failures} of ${missing.size} missing team logos")
        return failures
    }

    private companion object {
        const val TAG = "TeamLogoPrefetcher"
        const val MAX_CONCURRENT_DOWNLOADS = 4
    }
}
