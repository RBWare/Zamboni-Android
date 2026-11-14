package io.rbware.zamboni

import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringSetPreferencesKey
import io.rbware.zamboni.data.preferences.LegacyPreferencesMigration
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyPreferencesMigrationTest {
    private val key = stringSetPreferencesKey("favorite_team_ids")
    private val newsKey = stringSetPreferencesKey("news_source_ids")

    @Test
    fun rewritesBareIdsAndKeepsCurrentOnes() = runBlocking {
        val old = preferencesOf(key to setOf("dal", "det", "ahl-her", "nhl-tor"))
        assertTrue(LegacyPreferencesMigration.shouldMigrate(old))
        val migrated = LegacyPreferencesMigration.migrate(old)
        assertEquals(setOf("nhl-dal", "nhl-det", "ahl-her", "nhl-tor"), migrated[key])
    }

    @Test
    fun removesPlaceholderNewsSources() = runBlocking {
        val old = preferencesOf(newsKey to setOf("placeholder_a", "nhlrumors"))
        assertTrue(LegacyPreferencesMigration.shouldMigrate(old))
        assertEquals(setOf("nhlrumors"), LegacyPreferencesMigration.migrate(old)[newsKey])
    }

    @Test
    fun currentIds_needNoMigration() = runBlocking {
        assertFalse(
            LegacyPreferencesMigration.shouldMigrate(preferencesOf(key to setOf("nhl-dal"), newsKey to setOf("theahl"))),
        )
        assertFalse(LegacyPreferencesMigration.shouldMigrate(preferencesOf()))
    }
}
