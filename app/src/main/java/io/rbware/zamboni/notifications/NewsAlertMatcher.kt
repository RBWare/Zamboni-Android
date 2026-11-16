package io.rbware.zamboni.notifications

import io.rbware.zamboni.data.model.NewsArticle
import io.rbware.zamboni.data.model.Team
import java.time.Duration
import java.time.Instant

/**
 * Decides which new articles deserve a "Breaking news" alert: ones whose title names a favorite
 * team, or that call themselves breaking news.
 */
class NewsAlertMatcher(favoriteTeams: Collection<Team>) {

    private val patterns: List<Regex> = favoriteTeams.flatMap { team ->
        val nickname = team.name.trim().substringAfterLast(' ')
        listOfNotNull(
            // Full name, any case: "Toronto Maple Leafs".
            Regex("\\b${Regex.escape(team.name.trim())}\\b", RegexOption.IGNORE_CASE),
            // Nickname as a capitalized word: "Leafs", "Canadiens". Case-sensitive so words
            // like "wild" or "stars" in ordinary text don't match.
            nickname.takeIf { it.length >= 3 && it.first().isUpperCase() }
                ?.let { Regex("\\b${Regex.escape(it)}\\b") },
        )
    } + BREAKING

    fun matches(article: NewsArticle): Boolean = patterns.any { it.containsMatchIn(article.title) }

    /**
     * Articles from [articles] to alert about. Skips ones already seen in an earlier check,
     * ones older than [MAX_AGE] (when the feed gives a date), and caps the result at [MAX_ALERTS]
     * so a burst of stories doesn't flood the shade.
     */
    fun select(articles: List<NewsArticle>, seenIds: Set<String>, now: Instant): List<NewsArticle> =
        articles.asSequence()
            .filter { it.id !in seenIds }
            .filter { article -> article.publishedAt?.let { Duration.between(it, now) <= MAX_AGE } ?: true }
            .filter(::matches)
            .take(MAX_ALERTS)
            .toList()

    companion object {
        val MAX_AGE: Duration = Duration.ofHours(6)
        const val MAX_ALERTS = 3
        /** "Breaking: ..." but not "Breaking down the ...", a common analysis headline. */
        private val BREAKING = Regex("\\bbreaking\\b(?!\\s+down)", RegexOption.IGNORE_CASE)
    }
}
