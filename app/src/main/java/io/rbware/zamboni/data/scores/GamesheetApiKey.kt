package io.rbware.zamboni.data.scores

import io.rbware.zamboni.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Where the Gamesheet key in use comes from. */
enum class GamesheetKeySource {
    /** Entered by the user in Settings. */
    USER,

    /** Built into this app (developer builds, from local.properties). */
    BUILT_IN,

    /** No key: NAHL data is unavailable. */
    NONE,
}

data class GamesheetKey(val value: String?, val source: GamesheetKeySource)

/** Picks the user's key if one was entered, otherwise the built-in one. */
fun resolveGamesheetKey(userKey: String?, builtInKey: String): GamesheetKey = when {
    !userKey.isNullOrBlank() -> GamesheetKey(userKey.trim(), GamesheetKeySource.USER)
    builtInKey.isNotBlank() -> GamesheetKey(builtInKey, GamesheetKeySource.BUILT_IN)
    else -> GamesheetKey(null, GamesheetKeySource.NONE)
}

/**
 * The Gamesheet partner key to use, kept up to date with Settings.
 *
 * @param builtInKey BuildConfig.GAMESHEET_API_KEY: set from local.properties in debug builds,
 *   always empty in release builds.
 */
class GamesheetKeyProvider(
    private val userPreferencesRepository: UserPreferencesRepository,
    val builtInKey: String,
) {
    val key: Flow<GamesheetKey> = userPreferencesRepository.userPreferences
        .map { resolveGamesheetKey(it.gamesheetApiKey, builtInKey) }
        .distinctUntilChanged()

    suspend fun current(): String? = key.first().value
}
