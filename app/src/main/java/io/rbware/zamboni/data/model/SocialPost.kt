package io.rbware.zamboni.data.model

import java.time.Instant

/** One post in the Social tab. Opening it goes to [url] on its platform. */
data class SocialPost(
    /** Unique across sources: the source ID plus the platform's post ID. */
    val id: String,
    /** The followed account this post came from. */
    val source: SocialSource,
    /** Who wrote it. Differs from [source] when [source] shared (boosted) someone else's post. */
    val authorName: String,
    val authorHandle: String,
    val authorAvatarUrl: String?,
    /** Headline, for posts that have one (YouTube video titles); null for Mastodon posts. */
    val title: String? = null,
    /** Plain text with paragraph breaks kept. */
    val text: String,
    /** Content warning shown before the text, if the author set one. */
    val contentWarning: String?,
    val url: String,
    val publishedAt: Instant,
    /** First image or video preview (or link preview image), if any. */
    val imageUrl: String?,
    /** True when [source] boosted this post rather than writing it. */
    val isBoost: Boolean,
    /** Interaction counts; null when the source doesn't report them. */
    val replies: Int?,
    val boosts: Int?,
    val favourites: Int?,
    /** View count, for videos. */
    val views: Long? = null,
    /** A YouTube Short (vertical video) rather than a regular video. */
    val isShort: Boolean = false,
)
