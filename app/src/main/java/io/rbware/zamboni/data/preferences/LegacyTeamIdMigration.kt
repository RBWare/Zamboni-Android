package io.rbware.zamboni.data.preferences

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import io.rbware.zamboni.data.scores.nhlTeamId

/**
 * Rewrites favorite team IDs saved by early builds, which used the bare abbreviation ("dal"),
 * to the current league-prefixed form ("nhl-dal"). Those builds only had NHL teams, so any ID
 * without a league prefix is an NHL team. Without this, old favorites never match any game.
 */
object LegacyTeamIdMigration : DataMigration<Preferences> {

    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        currentData[UserPreferencesRepository.Keys.FAVORITE_TEAMS].orEmpty().any(::isLegacyId)

    override suspend fun migrate(currentData: Preferences): Preferences {
        val key = UserPreferencesRepository.Keys.FAVORITE_TEAMS
        return currentData.toMutablePreferences().apply {
            this[key] = currentData[key].orEmpty()
                .mapTo(mutableSetOf()) { if (isLegacyId(it)) nhlTeamId(it) else it }
        }.toPreferences()
    }

    override suspend fun cleanUp() = Unit

    private fun isLegacyId(id: String) = '-' !in id
}
