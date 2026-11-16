package io.rbware.zamboni.data.social

import android.util.Log
import io.rbware.zamboni.data.model.MastodonHandle
import io.rbware.zamboni.data.model.SocialPlatform
import io.rbware.zamboni.data.model.SocialPost
import io.rbware.zamboni.data.model.SocialSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap

/** Posts from several sources, plus any sources that couldn't be loaded. */
data class SocialFeed(
    /** Newest first. A post boosted by several followed accounts appears once. */
    val posts: List<SocialPost>,
    val failedSources: List<SocialSource>,
)

/** Loads recent posts for each source from its platform. */
class SocialFeedRepository(private val mastodon: MastodonClient, private val youtube: YouTubeClient) {

    /** Each source's last successful result, shown again if a later refresh of it fails. */
    private val lastGood = ConcurrentHashMap<String, List<SocialPost>>()

    /** Fetches all [sources] in parallel. One failing source doesn't fail the others. */
    suspend fun getPosts(sources: List<SocialSource>): SocialFeed = coroutineScope {
        // Many accounts share one server (e.g. sportsbots.xyz); don't hit it with all at once.
        val limit = Semaphore(MAX_CONCURRENT)
        val results = sources.map { source ->
            async {
                source to try {
                    val posts = limit.withPermit { fetch(source) }
                    lastGood[source.id] = posts
                    Result.success(posts)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to load ${source.handle}: $e")
                    // Older posts beat an empty spot, and aren't worth an error message.
                    lastGood[source.id]?.let { Result.success(it) } ?: Result.failure(e)
                }
            }
        }.awaitAll()

        SocialFeed(
            posts = results
                .flatMap { (_, result) -> result.getOrNull().orEmpty() }
                .sortedByDescending { it.publishedAt }
                .distinctBy { it.url },
            failedSources = results.filter { (_, result) -> result.isFailure }.map { (source, _) -> source },
        )
    }

    private suspend fun fetch(source: SocialSource): List<SocialPost> = when (source.platform) {
        SocialPlatform.MASTODON -> {
            val handle = requireNotNull(MastodonHandle.parse(source.handle)) { "Bad handle ${source.handle}" }
            mastodon.posts(handle, source)
        }
        SocialPlatform.YOUTUBE -> youtube.videos(source)
    }

    private companion object {
        const val TAG = "SocialFeedRepository"
        const val MAX_CONCURRENT = 6
    }
}
