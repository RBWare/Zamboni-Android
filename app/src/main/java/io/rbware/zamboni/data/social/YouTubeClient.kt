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
import java.io.IOException
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
 */
class YouTubeClient(private val client: OkHttpClient) {

    /**
     * @throws YouTubeChannelNotFoundException if there's no such channel.
     * @throws IOException if YouTube can't be reached or the page couldn't be understood.
     */
    suspend fun resolve(input: YouTubeChannelInput): YouTubeChannelInfo = when (input) {
        is YouTubeChannelInput.Id -> {
            val feed = parseYouTubeFeedInfo(get(feedUrl(input.channelId), input.channelId))
            YouTubeChannelInfo(YouTubeChannel(input.channelId, handle = null), feed.title)
        }
        is YouTubeChannelInput.Path -> {
            val page = parseChannelPage(get("https://www.youtube.com/${input.path}", input.path))
                ?: throw IOException("Couldn't read the channel page for ${input.path}")
            val handle = input.path.takeIf { it.startsWith("@") } ?: page.handle
            YouTubeChannelInfo(YouTubeChannel(page.channelId, handle), page.name)
        }
    }

    /** The channel's latest uploads, newest first. */
    suspend fun videos(source: SocialSource): List<SocialPost> {
        val channel = requireNotNull(YouTubeChannel.of(source)) { "Not a YouTube source: ${source.id}" }
        return parseYouTubeFeed(get(feedUrl(channel.channelId), channel.channelId), source)
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

/** The channel-level part of a feed. */
data class YouTubeFeedInfo(val title: String)

fun parseYouTubeFeedInfo(xml: String): YouTubeFeedInfo {
    val doc = Jsoup.parse(xml, "", Parser.xmlParser())
    val title = doc.selectFirst("feed > title")?.text() ?: throw IOException("Not a YouTube feed")
    return YouTubeFeedInfo(title)
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
