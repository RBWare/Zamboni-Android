package io.rbware.zamboni

import io.rbware.zamboni.data.model.NewsArticle
import io.rbware.zamboni.data.model.NewsSource
import io.rbware.zamboni.data.news.NewsSources
import io.rbware.zamboni.data.news.RssParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Parses trimmed copies of the real feeds (src/test/resources/rss_*.xml). */
class RssParserTest {
    private fun parse(file: String, sourceId: String): List<NewsArticle> =
        parse(file, NewsSources.all.first { it.id == sourceId })

    private fun parse(file: String, source: NewsSource): List<NewsArticle> {
        val stream = checkNotNull(javaClass.classLoader?.getResourceAsStream(file))
        return stream.use { RssParser.parse(it, source) }
    }

    /** Fox Sports isn't offered right now, but its feed is a good test of [NewsSource.requiredCategory]. */
    private val foxSports = NewsSource(
        id = "foxsports",
        name = "Fox Sports",
        feedUrl = "https://api.foxsports.com/v2/content/optimized-rss?tags=fs/nhl",
        requiredCategory = "nhl",
    )

    @Test
    fun hockeyWriters_textOnlyFeed() {
        val articles = parse("rss_thehockeywriters.xml", "thehockeywriters")
        assertEquals(3, articles.size)
        val first = articles.first()
        // Curly apostrophe comes through decoded.
        assertEquals("Highs and Lows of the Washington Capitals\u2019 Season So Far", first.title)
        assertEquals("https://thehockeywriters.com/highs-and-lows-of-the-washington-capitals-season-so-far/", first.link)
        assertEquals(Instant.parse("2026-11-14T19:45:00Z"), first.publishedAt)
        assertEquals("Hayley Paljug", first.author)
        assertTrue(first.summary!!.startsWith("The Washington Capitals have had an up-and-down season"))
        assertTrue("Washington Capitals" in first.categories)
        assertNull(first.imageUrl)
        assertEquals("thehockeywriters:https://thehockeywriters.com/?p=1715413", first.id)
    }

    @Test
    fun ahl_usesItemImageElement_andStripsTrackingParams() {
        val first = parse("rss_theahl.xml", "theahl").first()
        assertEquals("https://theahl.com/wp-content/uploads/sites/3/2026/09/ahl1200g-1024x576.jpg", first.imageUrl)
        assertEquals("https://theahl.com/news/hache-suspended-26nov14", first.link)
        assertEquals("AHL PR", first.author)
    }

    @Test
    fun nhlRumors_usesMediaContentImage() {
        val first = parse("rss_nhlrumors.xml", "nhlrumors").first()
        assertEquals(
            "https://s47317.pcdn.co/wp-content/uploads/2026/11/USATSI_28710673_168394737_lowres.jpg",
            first.imageUrl,
        )
        assertEquals("Mark Easson", first.author)
    }

    @Test
    fun nhlTradeTalk_dropsBoilerplate_andFindsImageInContent() {
        val first = parse("rss_nhltradetalk.xml", "nhltradetalk").first()
        val summary = first.summary!!
        assertFalse("appeared first on" in summary)
        assertTrue(summary.startsWith("Gavin McKenna is adjusting to the NHL"))
        // The &#038; in the HTML is decoded to a real ampersand.
        assertEquals(
            "https://i0.wp.com/nhltradetalk.com/wp-content/uploads/2026/10/Jim-Hiller-Gavin-McKenna-Maple-Leafs.jpg?resize=1000%2C500&ssl=1",
            first.imageUrl,
        )
        assertEquals("https://nhltradetalk.com/whats-gavin-mckennas-biggest-challenge-in-his-first-nhl-season/", first.link)
    }

    @Test
    fun sportsnet_skipsLayoutEntries_andParsesZoneNameDates() {
        val articles = parse("rss_sportsnet.xml", "sportsnet")
        // 4 items in the fixture; the first is an "NHL Featured" collection with no content.
        assertEquals(3, articles.size)
        assertFalse(articles.any { it.title == "NHL Featured" })
        val first = articles.first()
        assertEquals("NHL Global Series on Sportsnet: Kraken vs. Hurricanes", first.title)
        // "Sat, 14 Nov 2026 12:51:53 EST"
        assertEquals(Instant.parse("2026-11-14T17:51:53Z"), first.publishedAt)
        // Links in this feed are padded with whitespace.
        assertEquals("https://www.sportsnet.ca/hockey/nhl/games/d9ec29ac-5bc8-4677-947e-b51e25063261", first.link)
        assertTrue(first.imageUrl!!.startsWith("https://www.sportsnet.ca/wp-content/uploads/"))
        // An article with no description is kept because it has an image.
        val noSummary = articles.first { it.title.startsWith("Injured Avalanche stars") }
        assertNull(noSummary.summary)
    }

    @Test
    fun requiredCategory_keepsOnlyMatchingItems() {
        val articles = parse("rss_foxsports.xml", foxSports)
        // 6 items in the fixture: 2 tagged nhl, the rest nfl/nba.
        assertEquals(
            listOf("2026 NHL Stanley Cup Odds: Hurricanes New Favorites", "2026 NHL Conn Smythe Trophy Odds: Colorado's Nathan MacKinnon Favored"),
            articles.map { it.title },
        )
        assertTrue(articles.all { article -> article.categories.any { it.equals("nhl", ignoreCase = true) } })
        assertTrue(articles.first().imageUrl!!.startsWith("https://statics.foxsports.com/"))
        assertEquals(Instant.parse("2026-05-05T20:46:13Z"), articles.first().publishedAt)
    }

    @Test
    fun espn_textOnlyFeedWithEstDates() {
        val articles = parse("rss_espn.xml", "espn")
        assertEquals(3, articles.size)
        val first = articles.first()
        // "Sat, 14 Nov 2026 08:07:53 EST"
        assertEquals(Instant.parse("2026-11-14T13:07:53Z"), first.publishedAt)
        assertEquals("ESPN staff", first.author)
        assertNull(first.imageUrl)
        assertEquals("Find game previews, betting intel, broadcast information, stats and highlights here.", first.summary)
    }

    @Test
    fun allFeeds_parseEveryItemWithTitleLinkAndDate() {
        listOf(
            "rss_thehockeywriters.xml" to "thehockeywriters",
            "rss_theahl.xml" to "theahl",
            "rss_nhlrumors.xml" to "nhlrumors",
            "rss_nhltradetalk.xml" to "nhltradetalk",
            "rss_sportsnet.xml" to "sportsnet",
            "rss_espn.xml" to "espn",
        ).forEach { (file, id) ->
            val articles = parse(file, id)
            assertTrue(file, articles.isNotEmpty())
            articles.forEach {
                assertTrue(file, it.title.isNotBlank())
                // Links may be http (one AHL item points at http://youtube.com); browsers handle both.
                assertTrue(file, it.link.startsWith("http"))
                assertTrue(file, it.publishedAt != null)
                assertTrue(file, (it.summary?.length ?: 0) <= 281)
            }
        }
    }
}
