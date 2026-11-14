package io.rbware.zamboni

import io.rbware.zamboni.data.scores.GamesheetKey
import io.rbware.zamboni.data.scores.GamesheetKeySource
import io.rbware.zamboni.data.scores.resolveGamesheetKey
import org.junit.Assert.assertEquals
import org.junit.Test

class GamesheetApiKeyTest {

    @Test
    fun userKey_winsOverBuiltIn() {
        assertEquals(GamesheetKey("user", GamesheetKeySource.USER), resolveGamesheetKey("  user ", "built"))
    }

    @Test
    fun blankUserKey_fallsBackToBuiltIn() {
        assertEquals(GamesheetKey("built", GamesheetKeySource.BUILT_IN), resolveGamesheetKey(" ", "built"))
        assertEquals(GamesheetKey("built", GamesheetKeySource.BUILT_IN), resolveGamesheetKey(null, "built"))
    }

    @Test
    fun noKeys_isNone() {
        assertEquals(GamesheetKey(null, GamesheetKeySource.NONE), resolveGamesheetKey(null, ""))
    }
}
