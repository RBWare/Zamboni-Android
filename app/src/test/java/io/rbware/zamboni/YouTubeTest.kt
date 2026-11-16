package io.rbware.zamboni

import io.rbware.zamboni.data.model.SocialPlatform
import io.rbware.zamboni.data.model.YouTubeChannel
import io.rbware.zamboni.data.social.YouTubeChannelInput
import io.rbware.zamboni.data.social.cleanDisplayName
import io.rbware.zamboni.data.social.parseChannelPage
import io.rbware.zamboni.data.social.parseYouTubeFeed
import io.rbware.zamboni.data.social.parseChannelVideosPage
import io.rbware.zamboni.data.social.parseRelativeAge
import io.rbware.zamboni.data.social.parseViewCount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class YouTubeTest {
    private val nhlId = "UCqFMzb-4AUf6WAIbl132QKA"

    // Input

    @Test
    fun input_channelIdAndChannelLinks() {
        val expected = YouTubeChannelInput.Id(nhlId)
        assertEquals(expected, YouTubeChannelInput.parse(nhlId))
        assertEquals(expected, YouTubeChannelInput.parse("https://www.youtube.com/channel/$nhlId"))
        assertEquals(expected, YouTubeChannelInput.parse("youtube.com/channel/$nhlId/videos"))
    }

    @Test
    fun input_handlesWithOrWithoutAtAndLinks() {
        val expected = YouTubeChannelInput.Path("@NHL")
        assertEquals(expected, YouTubeChannelInput.parse("@NHL"))
        assertEquals(expected, YouTubeChannelInput.parse(" NHL "))
        assertEquals(expected, YouTubeChannelInput.parse("https://m.youtube.com/@NHL"))
        assertEquals(expected, YouTubeChannelInput.parse("https://www.youtube.com/@NHL/shorts"))
    }

    @Test
    fun input_legacyCustomAndUserLinks() {
        assertEquals(YouTubeChannelInput.Path("c/NHLVideo"), YouTubeChannelInput.parse("https://www.youtube.com/c/NHLVideo"))
        assertEquals(YouTubeChannelInput.Path("user/NHLVideo"), YouTubeChannelInput.parse("youtube.com/user/NHLVideo"))
    }

    @Test
    fun input_rejectsOtherText() {
        assertNull(YouTubeChannelInput.parse("https://www.youtube.com/watch?v=YYGMjMiKTvM"))
        assertNull(YouTubeChannelInput.parse("https://example.com/@NHL"))
        assertNull(YouTubeChannelInput.parse("two words"))
        assertNull(YouTubeChannelInput.parse("@a"))
    }

    // Channel key

    @Test
    fun channel_roundTripsThroughSource() {
        val source = YouTubeChannel(nhlId, "@NHL").toSource("NHL", isDefault = false)
        assertEquals("youtube:$nhlId", source.id)
        assertEquals(SocialPlatform.YOUTUBE, source.platform)
        assertEquals("@NHL", source.handle)
        assertEquals(YouTubeChannel(nhlId, "@NHL"), YouTubeChannel.of(source))
    }

    @Test
    fun channel_withoutHandleShowsId() {
        val source = YouTubeChannel(nhlId, handle = null).toSource("", isDefault = false)
        assertEquals(nhlId, source.handle)
        assertEquals(nhlId, source.name)
        assertEquals(YouTubeChannel(nhlId, null), YouTubeChannel.of(source))
    }

    // Channel page

    @Test
    fun page_readsIdNameAndHandle() {
        val html = """
            <html><head>
            <link rel="canonical" href="https://www.youtube.com/channel/$nhlId">
            <meta itemprop="name" content="NHL"><meta itemprop="identifier" content="$nhlId">
            </head><body><script>var ytInitialData = {"canonicalBaseUrl":"/@NHL","channelId":"UCK3CHl-6e3hq4gQaz_TOyoQ"};</script></body></html>
        """.trimIndent()
        val page = parseChannelPage(html)!!
        assertEquals(nhlId, page.channelId)
        assertEquals("NHL", page.name)
        assertEquals("@NHL", page.handle)
    }

    @Test
    fun page_fallsBackToCanonicalLink() {
        val html = """<html><head><link rel="canonical" href="https://www.youtube.com/channel/$nhlId">
            <meta property="og:title" content="NHL"></head></html>"""
        assertEquals(nhlId, parseChannelPage(html)!!.channelId)
    }

    @Test
    fun page_withoutChannelIsNull() {
        assertNull(parseChannelPage("<html><head><title>Consent</title></head></html>"))
    }

    // Feed (shaped like a real channel feed)

    private val feed = """
        <?xml version="1.0" encoding="UTF-8"?>
        <feed xmlns:yt="http://www.youtube.com/xml/schemas/2015" xmlns:media="http://search.yahoo.com/mrss/" xmlns="http://www.w3.org/2005/Atom">
         <title>NHL</title>
         <author><name>NHL</name><uri>https://www.youtube.com/channel/$nhlId</uri></author>
         <entry>
          <id>yt:video:OMaIWDPApVM</id><yt:videoId>OMaIWDPApVM</yt:videoId>
          <title>Flames vs. Kings | NHL Highlights | Nov 14, 2026</title>
          <link rel="alternate" href="https://www.youtube.com/watch?v=OMaIWDPApVM"/>
          <published>2026-11-15T06:13:34+00:00</published>
          <media:group>
           <media:thumbnail url="https://i2.ytimg.com/vi/OMaIWDPApVM/hqdefault.jpg" width="480" height="360"/>
           <media:description>Highlights &amp; more</media:description>
           <media:community><media:statistics views="123456"/></media:community>
          </media:group>
         </entry>
         <entry>
          <id>yt:video:YYGMjMiKTvM</id><yt:videoId>YYGMjMiKTvM</yt:videoId>
          <title>IDENTICAL shootout goals</title>
          <link rel="alternate" href="https://www.youtube.com/shorts/YYGMjMiKTvM"/>
          <published>2026-11-15T14:00:00+00:00</published>
          <media:group><media:community><media:statistics views="88437"/></media:community></media:group>
         </entry>
        </feed>
    """.trimIndent()

    private val source = YouTubeChannel(nhlId, "@NHL").toSource("NHL", isDefault = true)

    @Test
    fun feed_videosNewestFirst() {
        val videos = parseYouTubeFeed(feed, source)
        assertEquals(listOf("YYGMjMiKTvM", "OMaIWDPApVM"), videos.map { it.id.substringAfterLast(':') })
    }

    @Test
    fun feed_mapsVideo() {
        val video = parseYouTubeFeed(feed, source).last()
        assertEquals("youtube:$nhlId:OMaIWDPApVM", video.id)
        assertEquals("Flames vs. Kings | NHL Highlights | Nov 14, 2026", video.title)
        assertEquals("Highlights & more", video.text)
        assertEquals("https://www.youtube.com/watch?v=OMaIWDPApVM", video.url)
        assertEquals("https://i2.ytimg.com/vi/OMaIWDPApVM/hqdefault.jpg", video.imageUrl)
        assertEquals(123456L, video.views)
        assertEquals(Instant.parse("2026-11-15T06:13:34Z"), video.publishedAt)
        assertEquals("NHL", video.authorName)
        assertFalse(video.isShort)
    }

    @Test
    fun feed_shortWithoutThumbnailUsesStandardOne() {
        val short = parseYouTubeFeed(feed, source).first()
        assertTrue(short.isShort)
        assertEquals("https://i.ytimg.com/vi/YYGMjMiKTvM/hqdefault.jpg", short.imageUrl)
        assertEquals(88437L, short.views)
    }

    // Videos page (used while the feeds are down); shaped like the real page data.

    private val now = Instant.parse("2026-11-15T23:00:00Z")

    private fun lockup(id: String, title: String, vararg labels: String, type: String = "LOCKUP_CONTENT_TYPE_VIDEO") =
        """{"richItemRenderer":{"content":{"lockupViewModel":{"contentId":"$id","contentType":"$type",
          "metadata":{"lockupMetadataViewModel":{"title":{"content":"$title"},
          "metadata":{"contentMetadataViewModel":{"metadataRows":[{"metadataParts":[""" +
            labels.joinToString(",") { """{"text":{"content":"x"},"accessibilityLabel":"$it"}""" } +
            """]}]}}}}}}}}"""

    private val videosPage = """
        <html><script>var ytInitialData = {"metadata":{"channelMetadataRenderer":{"title":"NHL"}},
        "contents":{"tabs":[{"richGridRenderer":{"contents":[
        ${lockup("OMaIWDPApVM", "Flames vs. Kings", "27 thousand views", "17 hours ago")},
        ${lockup("eshE96_xi4k", "Goalies talk", "NHL", "and 3 more", "1.2 million views", "2 days ago")},
        ${lockup("PLx", "A playlist", "3 days ago", type = "LOCKUP_CONTENT_TYPE_PLAYLIST")},
        ${lockup("0ZscgJdu6KA", "Sharks score twice", "No views", "Streamed 20 minutes ago")}
        ]}}]}};</script></html>
    """.trimIndent()

    @Test
    fun videosPage_readsVideosNewestFirst() {
        val videos = parseChannelVideosPage(videosPage, source, now)!!
        assertEquals(listOf("0ZscgJdu6KA", "OMaIWDPApVM", "eshE96_xi4k"), videos.map { it.id.substringAfterLast(':') })
        val video = videos[1]
        assertEquals("Flames vs. Kings", video.title)
        assertEquals("NHL", video.authorName)
        assertEquals("https://www.youtube.com/watch?v=OMaIWDPApVM", video.url)
        assertEquals("https://i.ytimg.com/vi/OMaIWDPApVM/hqdefault.jpg", video.imageUrl)
        assertEquals(27_000L, video.views)
        assertEquals(Instant.parse("2026-11-15T06:00:00Z"), video.publishedAt)
        assertEquals(1_200_000L, videos[2].views)
        assertEquals(0L, videos[0].views)
    }

    @Test
    fun videosPage_readsDataEmbeddedAsJsString() {
        val json = videosPage.substringAfter("var ytInitialData = ").substringBefore(";</script>")
        // The way YouTube writes it as a string: quotes and other symbols as \xHH escapes.
        val escaped = json.replace("\\", "\\\\").replace("\"", "\\x22").replace("'", "\\x27").replace("&", "\\x26")
        val page = "<html><script>var ytInitialData = '$escaped';</script></html>"
        assertEquals(3, parseChannelVideosPage(page, source, now)!!.size)
    }

    @Test
    fun videosPage_withoutDataIsNull() {
        assertNull(parseChannelVideosPage("<html>Sorry</html>", source, now))
    }

    @Test
    fun relativeAgeAndViews() {
        assertEquals(Instant.parse("2026-11-08T23:00:00Z"), parseRelativeAge("1 week ago", now))
        assertNull(parseRelativeAge("Premieres tomorrow", now))
        assertEquals(843L, parseViewCount("843 views"))
        assertEquals(1L, parseViewCount("1 view"))
        assertNull(parseViewCount("and 3 more"))
    }

    // Names

    @Test
    fun displayName_dropsRobotEmoji() {
        assertEquals("Sportsnet", cleanDisplayName("Sportsnet 🤖"))
        assertEquals("NHL GIFs", cleanDisplayName("NHL 🤖 GIFs"))
        assertEquals("Plain", cleanDisplayName("Plain"))
    }
}
