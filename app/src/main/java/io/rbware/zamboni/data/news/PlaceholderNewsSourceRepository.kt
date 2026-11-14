package io.rbware.zamboni.data.news

import io.rbware.zamboni.data.model.NewsSource

/** Stand-in sources until the real RSS feeds are specified. */
class PlaceholderNewsSourceRepository : NewsSourceRepository {
    override suspend fun getSources(): List<NewsSource> = SOURCES

    private companion object {
        val SOURCES = listOf(
            NewsSource(id = "placeholder_a", name = "Example Source A", feedUrl = ""),
            NewsSource(id = "placeholder_b", name = "Example Source B", feedUrl = ""),
            NewsSource(id = "placeholder_c", name = "Example Source C", feedUrl = ""),
        )
    }
}
