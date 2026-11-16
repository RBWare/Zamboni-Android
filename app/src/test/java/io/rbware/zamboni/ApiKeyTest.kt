package io.rbware.zamboni

import io.rbware.zamboni.data.scores.ApiKey
import io.rbware.zamboni.data.scores.ApiKeySource
import io.rbware.zamboni.data.scores.resolveApiKey
import org.junit.Assert.assertEquals
import org.junit.Test

class ApiKeyTest {

    @Test
    fun userKey_winsOverBuiltIn() {
        assertEquals(ApiKey("user", ApiKeySource.USER), resolveApiKey("  user ", "built"))
    }

    @Test
    fun blankUserKey_fallsBackToBuiltIn() {
        assertEquals(ApiKey("built", ApiKeySource.BUILT_IN), resolveApiKey(" ", "built"))
        assertEquals(ApiKey("built", ApiKeySource.BUILT_IN), resolveApiKey(null, "built"))
    }

    @Test
    fun noKeys_isNone() {
        assertEquals(ApiKey(null, ApiKeySource.NONE), resolveApiKey(null, ""))
    }
}
