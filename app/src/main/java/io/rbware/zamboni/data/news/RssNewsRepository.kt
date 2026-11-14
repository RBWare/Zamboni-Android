package io.rbware.zamboni.data.news

import android.util.Log
import io.rbware.zamboni.data.model.NewsArticle
import io.rbware.zamboni.data.model.NewsSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.Instant

/** Downloads and parses RSS feeds directly from each source. */
class RssNewsRepository(private val client: OkHttpClient) : NewsRepository {

    override suspend fun getArticles(sources: List<NewsSource>): NewsFeed = coroutineScope {
        val results = sources.map { source ->
            async {
                source to try {
                    Result.success(fetch(source))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to load ${source.feedUrl}: $e")
                    Result.failure(e)
                }
            }
        }.awaitAll()

        NewsFeed(
            articles = results
                .flatMap { (_, result) -> result.getOrNull().orEmpty() }
                .distinctBy { it.link }
                .sortedByDescending { it.publishedAt ?: Instant.EPOCH },
            failedSources = results.filter { (_, result) -> result.isFailure }.map { (source, _) -> source },
        )
    }

    private suspend fun fetch(source: NewsSource): List<NewsArticle> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(source.feedUrl).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            RssParser.parse(response.body.byteStream(), source)
        }
    }

    private companion object {
        const val TAG = "RssNewsRepository"
    }
}
