package io.rbware.zamboni.di

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import io.rbware.zamboni.data.news.NewsSourceRepository
import io.rbware.zamboni.data.news.PlaceholderNewsSourceRepository
import io.rbware.zamboni.data.preferences.UserPreferencesRepository
import io.rbware.zamboni.data.scores.PlaceholderScoresRepository
import io.rbware.zamboni.data.scores.ScoresRepository
import io.rbware.zamboni.data.teams.PlaceholderTeamRepository
import io.rbware.zamboni.data.teams.TeamRepository

private val Context.userPreferencesDataStore by preferencesDataStore(name = "user_preferences")

/**
 * Manual dependency container, created once in [io.rbware.zamboni.ZamboniApplication].
 * Swap the placeholder repositories for real implementations (hockeynerd API, RSS) here.
 */
class AppContainer(context: Context) {
    val userPreferencesRepository = UserPreferencesRepository(context.userPreferencesDataStore)

    // TODO: replace with a hockeynerd.com-backed implementation once the API is specified.
    val teamRepository: TeamRepository = PlaceholderTeamRepository()

    // TODO: replace with a hockeynerd.com-backed implementation once the API is specified.
    val scoresRepository: ScoresRepository = PlaceholderScoresRepository()

    // TODO: replace with the real list of RSS feeds once specified.
    val newsSourceRepository: NewsSourceRepository = PlaceholderNewsSourceRepository()
}
