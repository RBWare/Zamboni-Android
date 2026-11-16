package io.rbware.zamboni.data.social

import io.rbware.zamboni.data.model.SocialSource
import io.rbware.zamboni.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** The built-in [SocialSources] plus the accounts the user added, defaults first. */
class SocialSourceRepository(private val userPreferencesRepository: UserPreferencesRepository) {

    val sources: Flow<List<SocialSource>> = userPreferencesRepository.userPreferences
        .map { prefs ->
            val defaultIds = SocialSources.defaults.mapTo(mutableSetOf()) { it.id }
            // A user-added account that later became a default is shown once, as the default.
            SocialSources.defaults + prefs.customSocialSources.filter { it.id !in defaultIds }
        }
        .distinctUntilChanged()

    suspend fun getSources(): List<SocialSource> = sources.first()

    /** Adds a user account (turned on). Does nothing if it is already in the list. */
    suspend fun add(source: SocialSource) {
        if (getSources().any { it.id == source.id }) return
        userPreferencesRepository.addCustomSocialSource(source.copy(isDefault = false))
    }

    /** Removes a user-added account. Defaults can't be removed, only turned off. */
    suspend fun remove(source: SocialSource) {
        if (source.isDefault) return
        userPreferencesRepository.removeCustomSocialSource(source.id)
    }
}
