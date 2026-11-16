package io.rbware.zamboni.data.scores

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Where a data source's API key in use comes from. */
enum class ApiKeySource {
    /** Entered by the user in Settings. */
    USER,

    /** Built into this app. */
    BUILT_IN,

    /** No key: the source's data is unavailable. */
    NONE,
}

data class ApiKey(val value: String?, val source: ApiKeySource)

/** Result of checking a key against its source. */
enum class ApiKeyCheck { VALID, REJECTED, FAILED }

/** Picks the user's key if one was entered, otherwise the built-in one. */
fun resolveApiKey(userKey: String?, builtInKey: String): ApiKey = when {
    !userKey.isNullOrBlank() -> ApiKey(userKey.trim(), ApiKeySource.USER)
    builtInKey.isNotBlank() -> ApiKey(builtInKey, ApiKeySource.BUILT_IN)
    else -> ApiKey(null, ApiKeySource.NONE)
}

/**
 * The key to use for one data source, kept up to date with Settings.
 *
 * @param userKey the key entered in Settings, if any.
 * @param builtInKey the key built into the app, or empty for none.
 */
class ApiKeyProvider(userKey: Flow<String?>, val builtInKey: String) {
    val key: Flow<ApiKey> = userKey
        .map { resolveApiKey(it, builtInKey) }
        .distinctUntilChanged()

    suspend fun current(): String? = key.first().value
}
