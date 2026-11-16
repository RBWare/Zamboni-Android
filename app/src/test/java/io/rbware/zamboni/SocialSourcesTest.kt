package io.rbware.zamboni

import io.rbware.zamboni.data.model.MastodonHandle
import io.rbware.zamboni.data.model.SocialCategory
import io.rbware.zamboni.data.model.SocialPlatform
import io.rbware.zamboni.data.preferences.UserPreferences
import io.rbware.zamboni.data.preferences.isSocialSourceEnabled
import io.rbware.zamboni.data.social.ActivityPubActor
import io.rbware.zamboni.data.social.SocialSources
import io.rbware.zamboni.data.social.parseActor
import io.rbware.zamboni.data.social.parseOutboxPage
import io.rbware.zamboni.ui.preferences.builtInSocialGroups
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SocialSourcesTest {

    // Built-in list

    @Test
    fun defaults_haveUniqueIds() {
        val ids = SocialSources.defaults.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun defaults_featuredAccountsAreOn() {
        val on = SocialSources.defaults.filter { it.enabledByDefault && it.platform == SocialPlatform.MASTODON }.map { it.name }
        assertEquals(
            listOf(
                "NHL", "Spittin' Chiclets", "Sportsnet", "The Athletic NHL", "SleeperNHL", "NHL Player Safety",
                "NHL.com", "NHL Public Relations", "NHL GIFs", "Sportsfeed NHL",
            ),
            on,
        )
        assertTrue(SocialSources.defaults.all { it.isDefault && it.category != null })
        assertTrue(SocialSources.defaults.any { it.handle == "@Sportsnet@sportsbots.xyz" })
    }

    @Test
    fun defaults_youTubeHasNhlOnAndAll32Teams() {
        val youtube = SocialSources.defaults.filter { it.platform == SocialPlatform.YOUTUBE }
        assertEquals(listOf("NHL"), youtube.filter { it.enabledByDefault }.map { it.name })
        assertEquals(32, youtube.count { it.category == SocialCategory.TEAM })
        assertEquals(44, youtube.size)
    }

    // Enabled state

    private val featured = MastodonHandle("a", "x.social").toSource("A", isDefault = true, enabledByDefault = true)
    private val optional = MastodonHandle("b", "x.social").toSource("B", isDefault = true, enabledByDefault = false)

    @Test
    fun enabled_followsDefaultUntilChanged() {
        val prefs = UserPreferences()
        assertTrue(prefs.isSocialSourceEnabled(featured))
        assertFalse(prefs.isSocialSourceEnabled(optional))
    }

    @Test
    fun enabled_userChoiceWins() {
        val prefs = UserPreferences(disabledSocialSourceIds = setOf(featured.id), enabledSocialSourceIds = setOf(optional.id))
        assertFalse(prefs.isSocialSourceEnabled(featured))
        assertTrue(prefs.isSocialSourceEnabled(optional))
    }

    // Grouping

    @Test
    fun groups_featuredFirst_thenCategoriesInOrder() {
        val sources = listOf(
            MastodonHandle("t", "x.social").toSource("T", true, enabledByDefault = false, category = SocialCategory.TEAM),
            MastodonHandle("l", "x.social").toSource("L", true, enabledByDefault = false, category = SocialCategory.LEAGUE),
            featured,
            // User-added accounts aren't part of the built-in groups.
            MastodonHandle("mine", "x.social").toSource("Mine", isDefault = false),
        )
        val groups = builtInSocialGroups(sources, SocialPlatform.MASTODON)
        assertEquals(
            listOf(listOf("A"), listOf("L"), listOf("T")),
            groups.map { group -> group.sources.map { it.name } },
        )
    }

    // ActivityPub

    private val json = Json { ignoreUnknownKeys = true }
    private val source = MastodonHandle("NHL", "sportsbots.xyz").toSource("NHL", isDefault = true)

    @Test
    fun actor_readsNameAvatarAndOutbox() {
        val actor = parseActor(
            json.parseToJsonElement(
                """{"id":"https://sportsbots.xyz/users/NHL","type":"Service","preferredUsername":"NHL","name":"NHL",
                   "outbox":"https://sportsbots.xyz/users/50004938/outbox",
                   "icon":{"type":"Image","mediaType":"image/png","url":"https://img/nhl.jpg"}}""",
            ).jsonObject,
        )!!
        assertEquals("NHL", actor.name)
        assertEquals("https://img/nhl.jpg", actor.avatarUrl)
        assertEquals("https://sportsbots.xyz/users/50004938/outbox", actor.outbox)
    }

    private val actor = ActivityPubActor("https://sportsbots.xyz/users/NHL", "NHL", "NHL", "https://img/nhl.jpg", "outbox")

    // Shaped like a real sportsbots.xyz outbox page.
    private val page = json.parseToJsonElement(
        """
        {"type":"OrderedCollectionPage","orderedItems":[
          {"type":"Announce","published":"2026-11-15T22:04:11.000Z","object":"https://sportsbots.xyz/users/DallasStars/statuses/1"},
          {"type":"Create","published":"2026-11-15T20:55:11.000Z","object":{
            "id":"https://sportsbots.xyz/users/NHL/statuses/2","type":"Note","summary":null,"inReplyTo":null,
            "published":"2026-11-15T20:55:11.000Z","url":"https://twitter.com/NHL/status/2","sensitive":false,
            "content":"<p>A pretty cool jersey!</p><p>Catch the <span class=\"h-card\"><a href=\"https://sportsbots.xyz/@NHLFlames\" class=\"u-url mention\">@<span>NHLFlames</span></a></span> at 8 PM ET!</p>",
            "attachment":[{"type":"Document","mediaType":"image/jpeg","url":"https://pbs.twimg.com/media/x.jpg"}]}},
          {"type":"Create","published":"2026-11-15T19:00:00.000Z","object":{
            "id":"https://sportsbots.xyz/users/NHL/statuses/3","type":"Note","inReplyTo":"https://sportsbots.xyz/users/NHL/statuses/2",
            "published":"2026-11-15T19:00:00.000Z","content":"<p>A reply</p>"}},
          {"type":"Create","object":{"id":"https://sportsbots.xyz/users/NHL/statuses/4","type":"Note","content":"<p>No date</p>"}},
          {"type":"Create","published":"2026-11-15T18:00:00.000Z","object":{
            "id":"https://sportsbots.xyz/users/NHL/statuses/5","type":"Note","published":"2026-11-15T18:00:00.000Z",
            "summary":"Spoilers","sensitive":true,"content":"<p>Hidden</p>",
            "attachment":[{"type":"Document","mediaType":"image/png","url":"https://img/s.png"}]}}
        ]}
        """,
    ).jsonObject

    @Test
    fun outbox_keepsOwnPosts_skipsBoostsRepliesAndUndated() {
        val posts = parseOutboxPage(page, actor, source, limit = 20)
        assertEquals(listOf("statuses/2", "statuses/5"), posts.map { it.id.substringAfter("users/NHL/") })
    }

    @Test
    fun outbox_mapsNote() {
        val post = parseOutboxPage(page, actor, source, limit = 20).first()
        assertEquals("NHL", post.authorName)
        assertEquals("@NHL@sportsbots.xyz", post.authorHandle)
        assertEquals("https://img/nhl.jpg", post.authorAvatarUrl)
        assertEquals("A pretty cool jersey!\n\nCatch the @NHLFlames at 8 PM ET!", post.text)
        assertEquals("https://twitter.com/NHL/status/2", post.url)
        assertEquals("https://pbs.twimg.com/media/x.jpg", post.imageUrl)
        assertEquals(Instant.parse("2026-11-15T20:55:11Z"), post.publishedAt)
        assertNull(post.replies)
        assertFalse(post.isBoost)
    }

    @Test
    fun outbox_contentWarningAndSensitiveImage() {
        val post = parseOutboxPage(page, actor, source, limit = 20).last()
        assertEquals("Spoilers", post.contentWarning)
        assertNull(post.imageUrl)
    }

    @Test
    fun outbox_respectsLimit() {
        assertEquals(1, parseOutboxPage(page, actor, source, limit = 1).size)
    }
}
