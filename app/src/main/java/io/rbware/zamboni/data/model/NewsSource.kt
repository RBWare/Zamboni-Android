package io.rbware.zamboni.data.model

/** An RSS feed the user can choose to see in the News tab. */
data class NewsSource(
    val id: String,
    val name: String,
    val feedUrl: String,
    /**
     * When set, only items tagged with this `<category>` (case-insensitive) are kept. For feeds
     * that mix in other sports.
     */
    val requiredCategory: String? = null,
)
