package io.rbware.zamboni.notifications

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** What the background checks have already seen, so each event is only notified once. */
@Serializable
data class NotificationState(
    /** Last snapshot of each followed game, by app game ID. */
    val games: Map<String, GameSnapshot> = emptyMap(),
    /** IDs of the articles in the feeds at the last news check. */
    val seenArticleIds: Set<String> = emptySet(),
    /** Sources included in the last news check; articles from a newly added source aren't alerted. */
    val newsSourceIds: Set<String> = emptySet(),
    /** Epoch millis of the last news check, or null if news has never been checked. */
    val lastNewsCheck: Long? = null,
    /**
     * Games the user muted (e.g. because they're watching): no alerts and no Live Update for
     * them. App game ID to epoch millis when muted.
     */
    val mutedGames: Map<String, Long> = emptyMap(),
)

/** Stores [NotificationState] as one JSON value in its own DataStore. */
class NotificationStateStore(private val dataStore: DataStore<Preferences>) {

    suspend fun load(): NotificationState = states.first()

    /** The stored state, updated whenever it's saved. */
    val states: Flow<NotificationState> = dataStore.data.map { decode(it[KEY]) }

    private fun decode(raw: String?): NotificationState {
        if (raw == null) return NotificationState()
        return try {
            json.decodeFromString(raw)
        } catch (e: IllegalArgumentException) {
            // Includes SerializationException. Unreadable (e.g. written by an incompatible
            // version): start over rather than crash.
            Log.w(TAG, "Discarding notification state: $e")
            NotificationState()
        }
    }

    suspend fun save(state: NotificationState) {
        dataStore.edit { it[KEY] = json.encodeToString(state) }
    }

    private companion object {
        const val TAG = "NotificationState"
        val KEY = stringPreferencesKey("state")
        val json = Json { ignoreUnknownKeys = true }
    }
}
