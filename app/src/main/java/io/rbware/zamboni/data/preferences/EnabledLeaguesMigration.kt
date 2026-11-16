package io.rbware.zamboni.data.preferences

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import io.rbware.zamboni.data.leagues.Leagues
import io.rbware.zamboni.data.leagues.leagueIdOfTeam

/**
 * Picks the enabled leagues for people who finished onboarding before leagues could be turned
 * off, so nothing they use disappears: the NHL (always shown before), every league with a
 * favorite team, and the NAHL if they entered a Gamesheet key. New users choose in onboarding.
 *
 * Runs after [AhlTeamIdMigration], so favorites already have their current IDs.
 */
object EnabledLeaguesMigration : DataMigration<Preferences> {
    private val KEYS = UserPreferencesRepository.Keys

    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        currentData[KEYS.ONBOARDING_COMPLETE] == true && currentData[KEYS.ENABLED_LEAGUES] == null

    override suspend fun migrate(currentData: Preferences): Preferences =
        currentData.toMutablePreferences().apply {
            this[KEYS.ENABLED_LEAGUES] = enabledLeaguesFor(
                favoriteTeamIds = currentData[KEYS.FAVORITE_TEAMS].orEmpty(),
                hasGamesheetKey = !currentData[KEYS.GAMESHEET_API_KEY].isNullOrBlank(),
            )
        }.toPreferences()

    override suspend fun cleanUp() = Unit

    internal fun enabledLeaguesFor(favoriteTeamIds: Set<String>, hasGamesheetKey: Boolean): Set<String> = buildSet {
        add(Leagues.NHL.id)
        favoriteTeamIds.mapNotNullTo(this, ::leagueIdOfTeam)
        if (hasGamesheetKey) add(Leagues.NAHL.id)
    }
}
