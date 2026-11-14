package io.rbware.zamboni.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import io.rbware.zamboni.data.model.NotificationType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/** Persists onboarding state and the user's choices (teams, notifications, news sources). */
class UserPreferencesRepository(private val dataStore: DataStore<Preferences>) {

    val userPreferences: Flow<UserPreferences> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs ->
            UserPreferences(
                onboardingComplete = prefs[Keys.ONBOARDING_COMPLETE] ?: false,
                favoriteTeamIds = prefs[Keys.FAVORITE_TEAMS].orEmpty(),
                notificationTypes = prefs[Keys.NOTIFICATION_TYPES].orEmpty()
                    .mapNotNull { name -> NotificationType.entries.firstOrNull { it.name == name } }
                    .toSet(),
                newsSourceIds = prefs[Keys.NEWS_SOURCES].orEmpty(),
                scoresFilter = ScoresFilter(
                    leagueIds = prefs[Keys.SCORES_LEAGUES].orEmpty(),
                    favoritesFirst = prefs[Keys.SCORES_FAVORITES_FIRST] ?: true,
                    favoritesOnly = prefs[Keys.SCORES_FAVORITES_ONLY] ?: false,
                ),
                gamesheetApiKey = prefs[Keys.GAMESHEET_API_KEY]?.takeIf { it.isNotBlank() },
            )
        }

    /** Saves all onboarding choices and marks onboarding as done, atomically. */
    suspend fun completeOnboarding(
        favoriteTeamIds: Set<String>,
        notificationTypes: Set<NotificationType>,
        newsSourceIds: Set<String>,
    ) {
        dataStore.edit { prefs ->
            prefs[Keys.FAVORITE_TEAMS] = favoriteTeamIds
            prefs[Keys.NOTIFICATION_TYPES] = notificationTypes.mapTo(mutableSetOf()) { it.name }
            prefs[Keys.NEWS_SOURCES] = newsSourceIds
            prefs[Keys.ONBOARDING_COMPLETE] = true
        }
    }

    suspend fun toggleFavoriteTeam(teamId: String) = toggle(Keys.FAVORITE_TEAMS, teamId)

    suspend fun toggleNotificationType(type: NotificationType) = toggle(Keys.NOTIFICATION_TYPES, type.name)

    suspend fun toggleNewsSource(sourceId: String) = toggle(Keys.NEWS_SOURCES, sourceId)

    suspend fun toggleScoresLeague(leagueId: String) = toggle(Keys.SCORES_LEAGUES, leagueId)

    /** An empty league filter means all leagues are shown. */
    suspend fun clearScoresLeagues() {
        dataStore.edit { it.remove(Keys.SCORES_LEAGUES) }
    }

    suspend fun setScoresFavoritesFirst(enabled: Boolean) {
        dataStore.edit { it[Keys.SCORES_FAVORITES_FIRST] = enabled }
    }

    suspend fun setScoresFavoritesOnly(enabled: Boolean) {
        dataStore.edit { it[Keys.SCORES_FAVORITES_ONLY] = enabled }
    }

    /** Shows every league and turns off "favorites only". */
    suspend fun clearScoresFilters() {
        dataStore.edit {
            it.remove(Keys.SCORES_LEAGUES)
            it[Keys.SCORES_FAVORITES_ONLY] = false
        }
    }

    /** Saves the user's Gamesheet key; null or blank removes it (falling back to the built-in key). */
    suspend fun setGamesheetApiKey(key: String?) {
        dataStore.edit { prefs ->
            val trimmed = key?.trim()
            if (trimmed.isNullOrEmpty()) prefs.remove(Keys.GAMESHEET_API_KEY) else prefs[Keys.GAMESHEET_API_KEY] = trimmed
        }
    }

    private suspend fun toggle(key: Preferences.Key<Set<String>>, value: String) {
        dataStore.edit { prefs: MutablePreferences ->
            val current = prefs[key].orEmpty()
            prefs[key] = if (value in current) current - value else current + value
        }
    }

    internal object Keys {
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val FAVORITE_TEAMS = stringSetPreferencesKey("favorite_team_ids")
        val NOTIFICATION_TYPES = stringSetPreferencesKey("notification_types")
        val NEWS_SOURCES = stringSetPreferencesKey("news_source_ids")
        val SCORES_LEAGUES = stringSetPreferencesKey("scores_league_ids")
        val SCORES_FAVORITES_FIRST = booleanPreferencesKey("scores_favorites_first")
        val SCORES_FAVORITES_ONLY = booleanPreferencesKey("scores_favorites_only")
        val GAMESHEET_API_KEY = stringPreferencesKey("gamesheet_api_key")
    }
}
