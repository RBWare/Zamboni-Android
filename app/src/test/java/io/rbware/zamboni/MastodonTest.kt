package io.rbware.zamboni

import io.rbware.zamboni.data.model.MastodonHandle
import io.rbware.zamboni.data.social.MastodonAccountDto
import io.rbware.zamboni.data.social.MastodonCardDto
import io.rbware.zamboni.data.social.MastodonClient
import io.rbware.zamboni.data.social.MastodonMediaDto
import io.rbware.zamboni.data.social.MastodonStatusDto
import io.rbware.zamboni.data.social.mastodonHtmlToText
import io.rbware.zamboni.data.social.toSocialPost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class MastodonTest {

    // Handles

    @Test
    fun parse_acceptsAddressWithOrWithoutLeadingAt() {
        val expected = MastodonHandle("NHL", "mastodon.social")
        assertEquals(expected, MastodonHandle.parse("@NHL@mastodon.social"))
        assertEquals(expected, MastodonHandle.parse("  NHL@Mastodon.Social "))
    }

    @Test
    fun parse_acceptsProfileUrl() {
        assertEquals(MastodonHandle("user_1", "hockey.social"), MastodonHandle.parse("https://hockey.social/@user_1"))
        assertEquals(MastodonHandle("user_1", "hockey.social"), MastodonHandle.parse("https://hockey.social/@user_1/"))
    }

    @Test
    fun parse_rejectsOtherText() {
        assertNull(MastodonHandle.parse("NHL"))
        assertNull(MastodonHandle.parse("@NHL"))
        assertNull(MastodonHandle.parse("nhl@localhost"))
        assertNull(MastodonHandle.parse("https://hockey.social/@user/12345"))
    }

    @Test
    fun sourceId_ignoresCase() {
        assertEquals(
            MastodonHandle.parse("@NHL@mastodon.social")!!.sourceId,
            MastodonHandle.parse("@nhl@MASTODON.social")!!.sourceId,
        )
        assertEquals("mastodon:nhl@mastodon.social", MastodonHandle("NHL", "mastodon.social").sourceId)
    }

    @Test
    fun toSource_fallsBackToUsernameForBlankName() {
        val source = MastodonHandle("NHL", "mastodon.social").toSource(name = " ", isDefault = true)
        assertEquals("NHL", source.name)
        assertEquals("@NHL@mastodon.social", source.handle)
        assertTrue(source.isDefault)
    }

    // HTML

    @Test
    fun html_keepsParagraphsAndLineBreaks() {
        val html = "<p>First line<br>second line</p><p>Second paragraph</p>"
        assertEquals("First line\nsecond line\n\nSecond paragraph", mastodonHtmlToText(html))
    }

    @Test
    fun html_shortensLinksLikeTheWeb() {
        val html = """<p>Read <a href="https://www.nhl.com/news/some-long-article-title">""" +
            """<span class="invisible">https://www.</span><span class="ellipsis">nhl.com/news/some-lo</span>""" +
            """<span class="invisible">ng-article-title</span></a></p>"""
        assertEquals("Read nhl.com/news/some-lo...", mastodonHtmlToText(html))
    }

    @Test
    fun html_keepsHashtagsAndMentionsAsText() {
        val html = """<p><a href="https://x/tags/GoLeafsGo" class="mention hashtag">#<span>GoLeafsGo</span></a> """ +
            """with <span class="h-card"><a href="https://x/@nhl" class="u-url mention">@<span>nhl</span></a></span></p>"""
        assertEquals("#GoLeafsGo with @nhl", mastodonHtmlToText(html))
    }

    @Test
    fun html_decodesEntitiesAndHandlesEmpty() {
        assertEquals("Leafs & Habs", mastodonHtmlToText("<p>Leafs &amp; Habs</p>"))
        assertEquals("", mastodonHtmlToText(""))
    }

    @Test
    fun html_dropsTwitterDomainFromMirroredMentions() {
        val html = """<p>Here are <span class="h-card"><a href="https://twitter.com/EmmySadler" class="u-url mention">""" +
            """@<span>EmmySadler@twitter.com</span></a></span>'s takeaways. cc @NHL@x.com, email news@x.com</p>"""
        assertEquals("Here are @EmmySadler's takeaways. cc @NHL, email news@x.com", mastodonHtmlToText(html))
        assertEquals("@nhl@mastodon.social", mastodonHtmlToText("<p>@nhl@mastodon.social</p>"))
    }

    // Mapping

    private val source = MastodonHandle("team", "hockey.social").toSource("Team", isDefault = true)
    private val team = MastodonAccountDto(id = "1", username = "team", acct = "team", displayName = "Team", avatar = "a.png")
    private val other = MastodonAccountDto(id = "2", username = "fan", acct = "fan@other.social", displayName = "", avatar = null)

    private fun status(
        id: String,
        account: MastodonAccountDto = team,
        reblog: MastodonStatusDto? = null,
        media: List<MastodonMediaDto> = emptyList(),
        card: MastodonCardDto? = null,
        sensitive: Boolean = false,
    ) = MastodonStatusDto(
        id = id,
        createdAt = "2026-11-15T20:00:00.000Z",
        url = "https://hockey.social/@${account.username}/$id",
        uri = "https://hockey.social/users/${account.username}/statuses/$id",
        content = "<p>Post $id</p>",
        sensitive = sensitive,
        account = account,
        reblog = reblog,
        mediaAttachments = media,
        card = card,
        repliesCount = 1,
        reblogsCount = 2,
        favouritesCount = 3,
    )

    @Test
    fun ownPost_mapsAuthorAndCounts() {
        val post = status("10").toSocialPost(source)
        assertEquals("mastodon:team@hockey.social:10", post.id)
        assertEquals("Team", post.authorName)
        assertEquals("@team", post.authorHandle)
        assertEquals("Post 10", post.text)
        assertEquals("https://hockey.social/@team/10", post.url)
        assertEquals(Instant.parse("2026-11-15T20:00:00Z"), post.publishedAt)
        assertFalse(post.isBoost)
        assertEquals(listOf(1, 2, 3), listOf(post.replies, post.boosts, post.favourites))
    }

    @Test
    fun boost_showsOriginalPostAndAuthor() {
        val post = status("11", reblog = status("99", account = other)).toSocialPost(source)
        assertTrue(post.isBoost)
        assertEquals("fan", post.authorName)
        assertEquals("@fan@other.social", post.authorHandle)
        assertEquals("Post 99", post.text)
        assertEquals("https://hockey.social/@fan/99", post.url)
        // The ID still comes from the followed account's timeline entry.
        assertEquals("mastodon:team@hockey.social:11", post.id)
    }

    @Test
    fun image_prefersMediaPreview_thenLinkCard_andSkipsSensitive() {
        val media = listOf(MastodonMediaDto(type = "audio", url = "a.mp3"), MastodonMediaDto(type = "image", url = "full.jpg", previewUrl = "small.jpg"))
        assertEquals("small.jpg", status("1", media = media).toSocialPost(source).imageUrl)
        assertEquals("card.jpg", status("2", card = MastodonCardDto("card.jpg")).toSocialPost(source).imageUrl)
        assertNull(status("3", media = media, sensitive = true).toSocialPost(source).imageUrl)
    }

    @Test
    fun json_decodesRealResponseShape() {
        val json = """
            [{"id":"5","created_at":"2026-10-19T09:59:05.999Z","url":null,"uri":"https://m.s/users/a/statuses/5",
              "content":"<p>Hi</p>","spoiler_text":"","sensitive":false,"visibility":"public",
              "account":{"id":"1","username":"a","acct":"a","display_name":"A","avatar":"x.png","url":"https://m.s/@a"},
              "reblog":null,"media_attachments":[],"card":null,"replies_count":0,"reblogs_count":0,"favourites_count":4}]
        """.trimIndent()
        val statuses = MastodonClient.json.decodeFromString<List<MastodonStatusDto>>(json)
        val post = statuses.single().toSocialPost(source)
        // Falls back to the URI when the web URL is missing.
        assertEquals("https://m.s/users/a/statuses/5", post.url)
        assertEquals(4, post.favourites)
    }
}
