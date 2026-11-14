package io.rbware.zamboni.data.model

import java.time.Instant

data class NewsArticle(
    /** Unique across sources: the source ID plus the feed's guid (or link). */
    val id: String,
    val source: NewsSource,
    val title: String,
    /** Article URL, with tracking (utm_*) parameters removed. */
    val link: String,
    /** Plain-text summary, or null if the feed has none. */
    val summary: String?,
    val imageUrl: String?,
    val author: String?,
    val publishedAt: Instant?,
    val categories: List<String>,
)
