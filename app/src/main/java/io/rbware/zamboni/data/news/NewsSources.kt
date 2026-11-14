package io.rbware.zamboni.data.news

import io.rbware.zamboni.data.model.NewsSource

/**
 * Every RSS feed the user can choose from, in display order.
 *
 * To add a feed, add a line here. The `id` is stored in user preferences, so it must be unique
 * and must not change once released. Feeds must be served over https (Android blocks plain
 * http by default). Set `requiredCategory` for feeds that mix in other sports.
 */
object NewsSources {
    val all: List<NewsSource> = listOf(
        NewsSource(id = "thehockeywriters", name = "The Hockey Writers", feedUrl = "https://thehockeywriters.com/feed/"),
        NewsSource(id = "theahl", name = "The American Hockey League (AHL)", feedUrl = "https://theahl.com/feed"),
        NewsSource(id = "nhlrumors", name = "NHL Rumors", feedUrl = "https://nhlrumors.com/feed/"),
        NewsSource(id = "nhltradetalk", name = "NHL Trade Talk", feedUrl = "https://nhltradetalk.com/feed/"),
        NewsSource(id = "sportsnet", name = "Sportsnet.ca", feedUrl = "https://www.sportsnet.ca/hockey/nhl/feed"),
        NewsSource(id = "espn", name = "ESPN", feedUrl = "https://www.espn.com/espn/rss/nhl/news"),
    )
}
