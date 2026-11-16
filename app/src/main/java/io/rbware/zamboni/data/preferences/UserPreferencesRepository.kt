package io.rbware.zamboni.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import io.rbware.zamboni.data.model.MastodonHandle
import io.rbware.zamboni.data.model.NotificationType
import io.rbware.zamboni.data.model.SocialPlatform
import io.rbware.zamboni.data.model.SocialSource
import io.rbware.zamboni.data.model.YouTubeChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException

/** Persists onboarding state and the user's choices (teams, notifications, news and social sources). */
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
                disabledSocialSourceIds = prefs[Keys.DISABLED_SOCIAL_SOURCES].orEmpty(),
                enabledSocialSourceIds = prefs[Keys.ENABLED_SOCIAL_SOURCES].orEmpty(),
                customSocialSources = decodeCustomSources(prefs[Keys.CUSTOM_SOCIAL_SOURCES]),
                dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: true,
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
        disabledSocialSourceIds: Set<String>,
        enabledSocialSourceIds: Set<String>,
    ) {
        dataStore.edit { prefs ->
            prefs[Keys.FAVORITE_TEAMS] = favoriteTeamIds
            prefs[Keys.NOTIFICATION_TYPES] = notificationTypes.mapTo(mutableSetOf()) { it.name }
            prefs[Keys.NEWS_SOURCES] = newsSourceIds
            prefs[Keys.DISABLED_SOCIAL_SOURCES] = disabledSocialSourceIds
            prefs[Keys.ENABLED_SOCIAL_SOURCES] = enabledSocialSourceIds
            prefs[Keys.ONBOARDING_COMPLETE] = true
        }
    }

    suspend fun toggleFavoriteTeam(teamId: String) = toggle(Keys.FAVORITE_TEAMS, teamId)

    suspend fun toggleNotificationType(type: NotificationType) = toggle(Keys.NOTIFICATION_TYPES, type.name)

    suspend fun toggleNewsSource(sourceId: String) = toggle(Keys.NEWS_SOURCES, sourceId)

    /** Turns a social account on or off, storing only how it differs from its default. */
    suspend fun setSocialSourceEnabled(source: SocialSource, enabled: Boolean) {
        dataStore.edit { prefs ->
            val disabled = prefs[Keys.DISABLED_SOCIAL_SOURCES].orEmpty() - source.id
            val on = prefs[Keys.ENABLED_SOCIAL_SOURCES].orEmpty() - source.id
            prefs[Keys.DISABLED_SOCIAL_SOURCES] = if (!enabled && source.enabledByDefault) disabled + source.id else disabled
            prefs[Keys.ENABLED_SOCIAL_SOURCES] = if (enabled && !source.enabledByDefault) on + source.id else on
        }
    }

    suspend fun addCustomSocialSource(source: SocialSource) {
        dataStore.edit { prefs ->
            val current = decodeCustomSources(prefs[Keys.CUSTOM_SOCIAL_SOURCES])
            if (current.none { it.id == source.id }) {
                prefs[Keys.CUSTOM_SOCIAL_SOURCES] = encodeCustomSources(current + source)
            }
            // Re-adding an account that was turned off before turns it back on.
            prefs[Keys.DISABLED_SOCIAL_SOURCES] = prefs[Keys.DISABLED_SOCIAL_SOURCES].orEmpty() - source.id
            prefs[Keys.ENABLED_SOCIAL_SOURCES] = prefs[Keys.ENABLED_SOCIAL_SOURCES].orEmpty() - source.id
        }
    }

    suspend fun removeCustomSocialSource(sourceId: String) {
        dataStore.edit { prefs ->
            val remaining = decodeCustomSources(prefs[Keys.CUSTOM_SOCIAL_SOURCES]).filter { it.id != sourceId }
            prefs[Keys.CUSTOM_SOCIAL_SOURCES] = encodeCustomSources(remaining)
            prefs[Keys.DISABLED_SOCIAL_SOURCES] = prefs[Keys.DISABLED_SOCIAL_SOURCES].orEmpty() - sourceId
            prefs[Keys.ENABLED_SOCIAL_SOURCES] = prefs[Keys.ENABLED_SOCIAL_SOURCES].orEmpty() - sourceId
        }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

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
        val DISABLED_SOCIAL_SOURCES = stringSetPreferencesKey("disabled_social_source_ids")
        val ENABLED_SOCIAL_SOURCES = stringSetPreferencesKey("enabled_social_source_ids")
        val CUSTOM_SOCIAL_SOURCES = stringPreferencesKey("custom_social_sources")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val SCORES_LEAGUES = stringSetPreferencesKey("scores_league_ids")
        val SCORES_FAVORITES_FIRST = booleanPreferencesKey("scores_favorites_first")
        val SCORES_FAVORITES_ONLY = booleanPreferencesKey("scores_favorites_only")
        val GAMESHEET_API_KEY = stringPreferencesKey("gamesheet_api_key")
    }
}

/** How a user-added social account is stored. Field names are persisted; don't rename them. */
@Serializable
private data class StoredSocialSource(
    val platform: String,
    val handle: String,
    val name: String,
    /** Platform ID the account is fetched by, when the handle isn't enough (YouTube channel ID). */
    val key: String? = null,
)

private val storageJson = Json { ignoreUnknownKeys = true }

private fun decodeCustomSources(raw: String?): List<SocialSource> {
    if (raw.isNullOrBlank()) return emptyList()
    val stored = try {
        storageJson.decodeFromString<List<StoredSocialSource>>(raw)
    } catch (e: IllegalArgumentException) {
        // Unreadable: drop the list rather than crash.
        return emptyList()
    }
    return stored.mapNotNull { item ->
        when (SocialPlatform.entries.firstOrNull { it.name == item.platform }) {
            SocialPlatform.MASTODON -> MastodonHandle.parse(item.handle)?.toSource(item.name, isDefault = false)
            SocialPlatform.YOUTUBE -> item.key?.takeIf { YouTubeChannel.CHANNEL_ID.matches(it) }?.let { channelId ->
                YouTubeChannel(channelId, item.handle.takeIf { it.startsWith("@") }).toSource(item.name, isDefault = false)
            }
            // From a newer version, or a platform that was removed.
            null -> null
        }
    }
}

private fun encodeCustomSources(sources: List<SocialSource>): String =
    storageJson.encodeToString(
        sources.map { StoredSocialSource(it.platform.name, it.handle, it.name, key = YouTubeChannel.of(it)?.channelId) },
    )
