package io.rbware.zamboni.data.social

import io.rbware.zamboni.data.model.SocialPost
import io.rbware.zamboni.data.model.SocialSource
import io.rbware.zamboni.data.model.YouTubeChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/** What the user typed to add a channel, once understood. */
sealed interface YouTubeChannelInput {
    data class Id(val channelId: String) : YouTubeChannelInput

    /** A page path to look the channel up by, e.g. "@NHL", "c/NHL" or "user/NHL". */
    data class Path(val path: String) : YouTubeChannelInput

    companion object {
        private val HANDLE = Regex("@?([A-Za-z0-9._-]{3,30})")
        private val HOSTS = setOf("youtube.com", "www.youtube.com", "m.youtube.com")

        /**
         * Accepts a channel ID, an @handle (with or without the @), or a channel link
         * (youtube.com/@handle, /channel/ID, /c/name or /user/name). Returns null otherwise.
         */
        fun parse(input: String): YouTubeChannelInput? {
            val text = input.trim()
            if (YouTubeChannel.CHANNEL_ID.matches(text)) return Id(text)
            val url = (if ("://" in text) text else "https://$text").toHttpUrlOrNull()
            if (url != null && url.host in HOSTS) {
                val segments = url.pathSegments.filter { it.isNotEmpty() }
                val first = segments.firstOrNull() ?: return null
                return when {
                    first == "channel" -> segments.getOrNull(1)?.takeIf { YouTubeChannel.CHANNEL_ID.matches(it) }?.let(::Id)
                    first.startsWith("@") -> HANDLE.matchEntire(first)?.let { Path("@${it.groupValues[1]}") }
                    first == "c" || first == "user" -> segments.getOrNull(1)?.let { Path("$first/$it") }
                    else -> null
                }
            }
            return HANDLE.matchEntire(text)?.let { Path("@${it.groupValues[1]}") }
        }
    }
}

class YouTubeChannelNotFoundException(input: String) : SocialAccountNotFoundException("No YouTube channel $input")

/** A channel as found when adding it. */
data class YouTubeChannelInfo(val channel: YouTubeChannel, val name: String)

/**
 * Reads YouTube channels without an API key: each channel's public RSS feed lists its latest
 * 15 uploads, and a channel's page gives its ID when the user enters an @handle or link.
 *
 * The RSS feeds sometimes fail for every channel at once (404 or 500 from YouTube), so when a
 * feed fails the channel's "Videos" page is read instead, and the feeds are skipped for a while.
 */
class YouTubeClient(private val client: OkHttpClient, private val clock: () -> Instant = Instant::now) {

    /** Until when to skip the RSS feeds after one failed; null while they work. */
    @Volatile
    private var feedsBrokenUntil: Instant? = null

    /**
     * @throws YouTubeChannelNotFoundException if there's no such channel.
     * @throws IOException if YouTube can't be reached or the page couldn't be understood.
     */
    suspend fun resolve(input: YouTubeChannelInput): YouTubeChannelInfo {
        // The channel page rather than the feed, since the feeds have outages.
        val path = when (input) {
            is YouTubeChannelInput.Id -> "channel/${input.channelId}"
            is YouTubeChannelInput.Path -> input.path
        }
        val page = parseChannelPage(get("https://www.youtube.com/$path", path))
            ?: throw IOException("Couldn't read the channel page for $path")
        val handle = (input as? YouTubeChannelInput.Path)?.path?.takeIf { it.startsWith("@") } ?: page.handle
        return YouTubeChannelInfo(YouTubeChannel(page.channelId, handle), page.name)
    }

    /** The channel's latest uploads, newest first. */
    suspend fun videos(source: SocialSource): List<SocialPost> {
        val channel = requireNotNull(YouTubeChannel.of(source)) { "Not a YouTube source: ${source.id}" }
        val skipFeed = feedsBrokenUntil?.let { clock() < it } == true
        if (!skipFeed) {
            try {
                val videos = parseYouTubeFeed(get(feedUrl(channel.channelId), channel.channelId), source)
                feedsBrokenUntil = null
                return videos
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A missing feed is as likely an outage as a deleted channel; the page tells.
                Log.w(TAG, "RSS feed failed for ${channel.channelId}, reading the channel page: $e")
                feedsBrokenUntil = clock().plus(FEED_RETRY_AFTER)
            }
        }
        val page = get("https://www.youtube.com/channel/${channel.channelId}/videos", channel.channelId)
        return parseChannelVideosPage(page, source, clock()) ?: run {
            Log.w(TAG, "Unreadable videos page for ${channel.channelId}: ${initialDataSnippet(page)}")
            throw IOException("Couldn't read the videos page for ${channel.channelId}")
        }
    }

    private fun feedUrl(channelId: String) = "https://www.youtube.com/feeds/videos.xml?channel_id=$channelId"

    private suspend fun get(url: String, what: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            // A desktop browser gets the full channel page (with the channel ID in it), and the
            // consent cookie skips the EU cookie banner page.
            .header("User-Agent", DESKTOP_USER_AGENT)
            .header("Cookie", "SOCS=CAI")
            .header("Accept-Language", "en")
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code == 404) throw YouTubeChannelNotFoundException(what)
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} from YouTube")
            response.body.string()
        }
    }

    private companion object {
        const val TAG = "YouTubeClient"
        val FEED_RETRY_AFTER: Duration = Duration.ofMinutes(15)
        const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0 Safari/537.36"
    }
}

/** Channel details from a channel page's metadata. */
data class ChannelPageInfo(val channelId: String, val name: String, val handle: String?)

fun parseChannelPage(html: String): ChannelPageInfo? {
    val doc = Jsoup.parse(html)
    val channelId = doc.selectFirst("meta[itemprop=identifier]")?.attr("content")?.takeIf { YouTubeChannel.CHANNEL_ID.matches(it) }
        ?: doc.selectFirst("link[rel=canonical]")?.attr("href")?.let { YouTubeChannel.CHANNEL_ID.find(it)?.value }
        ?: Regex("\"externalId\":\"(UC[A-Za-z0-9_-]{22})\"").find(html)?.groupValues?.get(1)
        ?: return null
    val name = doc.selectFirst("meta[itemprop=name]")?.attr("content")
        ?: doc.selectFirst("meta[property=og:title]")?.attr("content")
        ?: return null
    val handle = Regex("\"canonicalBaseUrl\":\"/(@[A-Za-z0-9._-]+)\"").find(html)?.groupValues?.get(1)
    return ChannelPageInfo(channelId, name, handle)
}

/** Videos from a channel's RSS feed, newest first. */
fun parseYouTubeFeed(xml: String, source: SocialSource): List<SocialPost> {
    val doc = Jsoup.parse(xml, "", Parser.xmlParser())
    val channelName = doc.selectFirst("feed > author > name")?.text() ?: source.name
    return doc.select("entry").mapNotNull { entry ->
        val videoId = entry.selectFirst("yt|videoId")?.text() ?: return@mapNotNull null
        val published = entry.selectFirst("published")?.text()?.let {
            try {
                OffsetDateTime.parse(it).toInstant()
            } catch (e: DateTimeParseException) {
                null
            }
        } ?: return@mapNotNull null
        val link = entry.selectFirst("link[rel=alternate]")?.attr("href")?.takeIf { it.isNotBlank() }
            ?: "https://www.youtube.com/watch?v=$videoId"
        SocialPost(
            id = "${source.id}:$videoId",
            source = source,
            authorName = channelName,
            authorHandle = source.handle,
            authorAvatarUrl = null,
            title = entry.selectFirst("title")?.text().orEmpty(),
            text = entry.selectFirst("media|description")?.text().orEmpty(),
            contentWarning = null,
            url = link,
            publishedAt = published,
            imageUrl = entry.selectFirst("media|thumbnail")?.attr("url")?.takeIf { it.isNotBlank() }
                ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
            isBoost = false,
            replies = null,
            boosts = null,
            favourites = null,
            views = entry.selectFirst("media|statistics")?.attr("views")?.toLongOrNull(),
            isShort = "/shorts/" in link,
        )
    }.sortedByDescending { it.publishedAt }
}

/**
 * Videos from a channel's "Videos" page (the data the page embeds as `ytInitialData`), newest
 * first, or null if the page doesn't have it. Upload times are only given as "17 hours ago",
 * so they're approximate, counted back from [now]. Shorts aren't on this page.
 */
fun parseChannelVideosPage(html: String, source: SocialSource, now: Instant): List<SocialPost>? {
    val data = extractInitialData(html) ?: return null
    val channelName = (data.path("metadata", "channelMetadataRenderer", "title") as? JsonPrimitive)?.contentOrNull
        ?: source.name
    val lockups = mutableListOf<JsonObject>()
    collectLockups(data, lockups)
    return lockups.mapNotNull { lockup ->
        val videoId = lockup.string("contentId") ?: return@mapNotNull null
        if (lockup.string("contentType") != "LOCKUP_CONTENT_TYPE_VIDEO") return@mapNotNull null
        val metadata = lockup.path("metadata", "lockupMetadataViewModel") as? JsonObject ?: return@mapNotNull null
        val title = (metadata.path("title", "content") as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
        // Each part has a short text ("17h ago") and a spelled-out label ("17 hours ago").
        val labels = (metadata.path("metadata", "contentMetadataViewModel", "metadataRows") as? JsonArray).orEmpty()
            .flatMap { row -> ((row as? JsonObject)?.get("metadataParts") as? JsonArray).orEmpty() }
            .mapNotNull { part ->
                (part as? JsonObject)?.let { it.string("accessibilityLabel") ?: (it.path("text", "content") as? JsonPrimitive)?.contentOrNull }
            }
        val published = labels.firstNotNullOfOrNull { parseRelativeAge(it, now) } ?: return@mapNotNull null
        SocialPost(
            id = "${source.id}:$videoId",
            source = source,
            authorName = channelName,
            authorHandle = source.handle,
            authorAvatarUrl = null,
            title = title,
            text = "",
            contentWarning = null,
            url = "https://www.youtube.com/watch?v=$videoId",
            publishedAt = published,
            imageUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
            isBoost = false,
            replies = null,
            boosts = null,
            favourites = null,
            views = labels.firstNotNullOfOrNull(::parseViewCount),
        )
    }.distinctBy { it.id }.sortedByDescending { it.publishedAt }
}

private val pageJson = Json { ignoreUnknownKeys = true }

/**
 * The page's `ytInitialData`. YouTube embeds it in a few ways: as a JSON object
 * (`var ytInitialData = {...};`), on `window["ytInitialData"]`, or as an escaped JavaScript
 * string (`var ytInitialData = '{\x22responseContext...';`).
 */
internal fun extractInitialData(html: String): JsonObject? {
    val marker = INITIAL_DATA_MARKERS.firstNotNullOfOrNull { m -> html.indexOf(m).takeIf { it >= 0 }?.plus(m.length) }
        ?: return null
    val text = when (html.getOrNull(marker)) {
        '{' -> html.indexOf(";</script>", marker).takeIf { it >= 0 }?.let { html.substring(marker, it) }
        '\'' -> html.indexOf("';", marker + 1).takeIf { it >= 0 }?.let { unescapeJsString(html.substring(marker + 1, it)) }
        else -> null
    } ?: return null
    return try {
        pageJson.parseToJsonElement(text) as? JsonObject
    } catch (e: IllegalArgumentException) {
        null
    }
}

/** What follows the data marker, for logging when a page can't be read. */
internal fun initialDataSnippet(html: String): String {
    val marker = INITIAL_DATA_MARKERS.firstNotNullOfOrNull { m -> html.indexOf(m).takeIf { it >= 0 } }
        ?: return "no ytInitialData (${html.length} chars, title: ${Regex("<title>([^<]*)").find(html)?.groupValues?.get(1)})"
    return html.substring(marker, minOf(html.length, marker + 80))
}

private val INITIAL_DATA_MARKERS = listOf("var ytInitialData = ", "window[\"ytInitialData\"] = ", "ytInitialData = ")

/** Undoes JavaScript string escapes: \xHH, \uHHHH and backslash-escaped characters. */
private fun unescapeJsString(text: String): String = buildString(text.length) {
    var i = 0
    while (i < text.length) {
        val c = text[i]
        if (c != '\\' || i + 1 >= text.length) {
            append(c)
            i++
            continue
        }
        when (val next = text[i + 1]) {
            'x' -> { text.substring(i + 2, minOf(text.length, i + 4)).toIntOrNull(16)?.let { append(it.toChar()) }; i += 4 }
            'u' -> { text.substring(i + 2, minOf(text.length, i + 6)).toIntOrNull(16)?.let { append(it.toChar()) }; i += 6 }
            'n' -> { append('\n'); i += 2 }
            't' -> { append('\t'); i += 2 }
            'r' -> { append('\r'); i += 2 }
            else -> { append(next); i += 2 }
        }
    }
}

private fun collectLockups(element: JsonElement, into: MutableList<JsonObject>) {
    when (element) {
        is JsonObject -> {
            val lockup = element["lockupViewModel"] as? JsonObject
            if (lockup != null) into += lockup else element.values.forEach { collectLockups(it, into) }
        }
        is JsonArray -> element.forEach { collectLockups(it, into) }
        else -> Unit
    }
}

private fun JsonElement.path(vararg keys: String): JsonElement? =
    keys.fold(this as JsonElement?) { node, key -> (node as? JsonObject)?.get(key) }

private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

private val AGE = Regex("""(\d+)\s*(second|minute|hour|day|week|month|year)s?\s+ago""", RegexOption.IGNORE_CASE)

/** "17 hours ago" (or "Streamed 2 days ago") as an instant before [now]; null otherwise. */
fun parseRelativeAge(text: String, now: Instant): Instant? {
    val match = AGE.find(text) ?: return null
    val amount = match.groupValues[1].toLong()
    val unit = when (match.groupValues[2].lowercase()) {
        "second" -> Duration.ofSeconds(1)
        "minute" -> Duration.ofMinutes(1)
        "hour" -> Duration.ofHours(1)
        "day" -> Duration.ofDays(1)
        "week" -> Duration.ofDays(7)
        "month" -> Duration.ofDays(30)
        else -> Duration.ofDays(365)
    }
    return now.minus(unit.multipliedBy(amount))
}

private val VIEWS = Regex("""([\d.,]+)\s*(thousand|million|billion)?\s+views?""", RegexOption.IGNORE_CASE)

/** "27 thousand views", "1.2 million views" or "843 views" as a number; null otherwise. */
fun parseViewCount(text: String): Long? {
    if (text.equals("No views", ignoreCase = true)) return 0
    val match = VIEWS.find(text) ?: return null
    val number = match.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
    val scale = when (match.groupValues[2].lowercase()) {
        "thousand" -> 1e3
        "million" -> 1e6
        "billion" -> 1e9
        else -> 1.0
    }
    return (number * scale).toLong()
}
