package io.rbware.zamboni.data.news

import io.rbware.zamboni.data.model.NewsSource

interface NewsSourceRepository {
    /** All RSS sources the user can choose from. */
    suspend fun getSources(): List<NewsSource>
}
