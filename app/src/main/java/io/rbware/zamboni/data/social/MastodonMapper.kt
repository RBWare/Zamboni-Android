package io.rbware.zamboni.data.social

import io.rbware.zamboni.data.model.SocialPost
import io.rbware.zamboni.data.model.SocialSource
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.time.Instant

/** Turns a Mastodon status from [source]'s timeline into a [SocialPost]. */
fun MastodonStatusDto.toSocialPost(source: SocialSource): SocialPost {
    // For a boost, show the original post (its author, text and link).
    val post = reblog ?: this
    val author = post.account
    val image = if (post.sensitive) {
        // Don't show images the author marked sensitive.
        null
    } else {
        post.mediaAttachments.firstOrNull { it.type in PREVIEW_TYPES }?.let { it.previewUrl ?: it.url }
            ?: post.card?.image
    }
    return SocialPost(
        id = "${source.id}:$id",
        source = source,
        authorName = cleanDisplayName(author.displayName).ifBlank { author.username },
        authorHandle = "@${author.acct}",
        authorAvatarUrl = author.avatar,
        text = mastodonHtmlToText(post.content),
        contentWarning = post.spoilerText.takeIf { it.isNotBlank() },
        url = post.url ?: post.uri,
        publishedAt = Instant.parse(post.createdAt),
        imageUrl = image,
        isBoost = reblog != null,
        replies = post.repliesCount,
        boosts = post.reblogsCount,
        favourites = post.favouritesCount,
    )
}

private val PREVIEW_TYPES = setOf("image", "gifv", "video")

/**
 * Plain text from Mastodon's post HTML, keeping line and paragraph breaks.
 *
 * Mastodon shortens links by wrapping the hidden parts in `span.invisible` (e.g. "https://")
 * and marking a cut-off with `span.ellipsis`; those are dropped or given "..." so links read
 * the way they do on the web.
 */
fun mastodonHtmlToText(html: String): String {
    if (html.isBlank()) return ""
    val doc: Document = Jsoup.parseBodyFragment(html)
    doc.select("span.invisible").remove()
    doc.select("span.ellipsis").append("...")
    doc.select("br").after(LINE_BREAK)
    doc.select("p").forEach { it.prepend(PARAGRAPH_BREAK) }
    return doc.body().text()
        .replace(PARAGRAPH_BREAK, "\n\n")
        .replace(LINE_BREAK, "\n")
        .lines()
        .joinToString("\n") { it.trim() }
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
}

// Placeholders that survive Jsoup's whitespace normalization in text().
private const val LINE_BREAK = " "
private const val PARAGRAPH_BREAK = " "

/**
 * A display name without the robot emoji that bot accounts (e.g. the sportsbots.xyz mirrors)
 * add to their names: "Sportsnet \uD83E\uDD16" becomes "Sportsnet".
 */
fun cleanDisplayName(name: String): String = name.replace(ROBOT, "").replace(Regex("\\s{2,}"), " ").trim()

private const val ROBOT = "\uD83E\uDD16"
