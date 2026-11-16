package io.rbware.zamboni

import io.rbware.zamboni.data.model.League
import io.rbware.zamboni.data.model.NewsArticle
import io.rbware.zamboni.data.model.NewsSource
import io.rbware.zamboni.data.model.Team
import io.rbware.zamboni.notifications.NewsAlertMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class NewsAlertMatcherTest {
    private val nhl = League("nhl", "NHL", "National Hockey League")
    private val source = NewsSource("src", "Source", "https://example.com/feed")
    private val now = Instant.parse("2026-11-15T12:00:00Z")

    private val matcher = NewsAlertMatcher(
        listOf(
            Team("tor", "Toronto Maple Leafs", "TOR", nhl),
            Team("min", "Minnesota Wild", "MIN", nhl),
        ),
    )

    private fun article(title: String, id: String = title, ageHours: Long? = 1) = NewsArticle(
        id = id,
        source = source,
        title = title,
        link = "https://example.com/$id",
        summary = null,
        imageUrl = null,
        author = null,
        publishedAt = ageHours?.let { now.minus(Duration.ofHours(it)) },
        categories = emptyList(),
    )

    @Test
    fun matchesFullNameAnyCase() {
        assertTrue(matcher.matches(article("TORONTO MAPLE LEAFS sign defenceman")))
    }

    @Test
    fun matchesCapitalizedNickname() {
        assertTrue(matcher.matches(article("Leafs recall forward from Marlies")))
        assertTrue(matcher.matches(article("Wild extend goaltender")))
    }

    @Test
    fun lowercaseNicknameIsNotATeam() {
        assertFalse(matcher.matches(article("A wild finish in Boston")))
    }

    @Test
    fun matchesBreakingButNotBreakingDown() {
        assertTrue(matcher.matches(article("Breaking: Blockbuster trade completed")))
        assertFalse(matcher.matches(article("Breaking down the Oilers power play")))
    }

    @Test
    fun ignoresOtherTeams() {
        assertFalse(matcher.matches(article("Oilers beat Flames in overtime")))
    }

    @Test
    fun select_skipsSeenAndOldArticles_andCaps() {
        val articles = listOf(
            article("Leafs news 1", id = "seen"),
            article("Leafs news 2", id = "old", ageHours = 10),
            article("Leafs news 3", id = "a"),
            article("Leafs news 4", id = "b", ageHours = null),
            article("Leafs news 5", id = "c"),
            article("Leafs news 6", id = "d"),
            article("Oilers news", id = "other"),
        )
        val selected = matcher.select(articles, seenIds = setOf("seen"), now = now)
        assertEquals(listOf("a", "b", "c"), selected.map { it.id })
    }
}
