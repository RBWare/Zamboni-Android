package io.rbware.zamboni

import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringSetPreferencesKey
import io.rbware.zamboni.data.preferences.LegacyTeamIdMigration
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyTeamIdMigrationTest {
    private val key = stringSetPreferencesKey("favorite_team_ids")

    @Test
    fun rewritesBareIdsAndKeepsCurrentOnes() = runBlocking {
        val old = preferencesOf(key to setOf("dal", "det", "ahl-her", "nhl-tor"))
        assertTrue(LegacyTeamIdMigration.shouldMigrate(old))
        val migrated = LegacyTeamIdMigration.migrate(old)
        assertEquals(setOf("nhl-dal", "nhl-det", "ahl-her", "nhl-tor"), migrated[key])
    }

    @Test
    fun currentIds_needNoMigration() = runBlocking {
        assertFalse(LegacyTeamIdMigration.shouldMigrate(preferencesOf(key to setOf("nhl-dal"))))
        assertFalse(LegacyTeamIdMigration.shouldMigrate(preferencesOf()))
    }
}
