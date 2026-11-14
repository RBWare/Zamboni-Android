package io.rbware.zamboni.data.news

import io.rbware.zamboni.data.model.NewsArticle
import io.rbware.zamboni.data.model.NewsSource

/** Articles from several sources, plus any sources that couldn't be loaded. */
data class NewsFeed(
    /** Newest first, with duplicates (same link) removed. */
    val articles: List<NewsArticle>,
    val failedSources: List<NewsSource>,
)

interface NewsRepository {
    /** Fetches all [sources] in parallel. One failing source doesn't fail the others. */
    suspend fun getArticles(sources: List<NewsSource>): NewsFeed
}
