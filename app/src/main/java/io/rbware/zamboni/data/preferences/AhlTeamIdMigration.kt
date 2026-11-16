package io.rbware.zamboni.data.preferences

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences

/**
 * Version 1.0 offered AHL favorites from a placeholder list whose codes differ from HockeyTech's
 * for a few teams. AHL team IDs now come from HockeyTech ("ahl-" plus its team code), so saved
 * favorites with the old codes are renamed to keep matching their games.
 *
 * Bridgeport ("ahl-bpt") has no counterpart in today's league and is left as it is.
 */
object AhlTeamIdMigration : DataMigration<Preferences> {
    private val TEAMS = UserPreferencesRepository.Keys.FAVORITE_TEAMS

    /** Placeholder ID to HockeyTech ID. */
    internal val RENAMED = mapOf(
        "ahl-cgw" to "ahl-cgy", // Calgary Wranglers
        "ahl-chw" to "ahl-chi", // Chicago Wolves
        "ahl-coe" to "ahl-col", // Colorado Eagles
        "ahl-uti" to "ahl-utc", // Utica Comets
    )

    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        currentData[TEAMS].orEmpty().any { it in RENAMED }

    override suspend fun migrate(currentData: Preferences): Preferences =
        currentData.toMutablePreferences().apply {
            currentData[TEAMS]?.let { ids -> this[TEAMS] = ids.mapTo(mutableSetOf()) { RENAMED[it] ?: it } }
        }.toPreferences()

    override suspend fun cleanUp() = Unit
}
