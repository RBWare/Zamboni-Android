package io.rbware.zamboni.data.preferences

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import io.rbware.zamboni.data.scores.nhlTeamId

/**
 * Cleans up preferences saved by early development builds:
 *
 * - Favorite team IDs that used the bare abbreviation ("dal") become league-prefixed
 *   ("nhl-dal"). Those builds only had NHL teams, so any ID without a prefix is an NHL team.
 *   Without this, old favorites never match any game.
 * - Placeholder news source IDs ("placeholder_a") are removed, since no such sources exist.
 */
object LegacyPreferencesMigration : DataMigration<Preferences> {
    private val TEAMS = UserPreferencesRepository.Keys.FAVORITE_TEAMS
    private val NEWS = UserPreferencesRepository.Keys.NEWS_SOURCES

    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        currentData[TEAMS].orEmpty().any(::isLegacyTeamId) ||
            currentData[NEWS].orEmpty().any(::isPlaceholderSourceId)

    override suspend fun migrate(currentData: Preferences): Preferences =
        currentData.toMutablePreferences().apply {
            currentData[TEAMS]?.let { ids ->
                this[TEAMS] = ids.mapTo(mutableSetOf()) { if (isLegacyTeamId(it)) nhlTeamId(it) else it }
            }
            currentData[NEWS]?.let { ids ->
                this[NEWS] = ids.filterNotTo(mutableSetOf(), ::isPlaceholderSourceId)
            }
        }.toPreferences()

    override suspend fun cleanUp() = Unit

    private fun isLegacyTeamId(id: String) = '-' !in id

    private fun isPlaceholderSourceId(id: String) = id.startsWith("placeholder_")
}
