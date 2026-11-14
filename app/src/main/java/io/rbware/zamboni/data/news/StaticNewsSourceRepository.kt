package io.rbware.zamboni.data.news

import io.rbware.zamboni.data.model.NewsSource

/** Serves the built-in [NewsSources] list. */
class StaticNewsSourceRepository : NewsSourceRepository {
    override suspend fun getSources(): List<NewsSource> = NewsSources.all
}
