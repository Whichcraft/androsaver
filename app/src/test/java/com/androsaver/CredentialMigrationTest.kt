package com.androsaver

import org.junit.Assert.*
import org.junit.Test

class CredentialMigrationTest {
    @Test fun failedPlaintextCleanupCannotRollBackALaterEncryptedToken() {
        val plain = mutableMapOf<String, Any>("credential" to "placeholder-old")
        val encrypted = mutableMapOf<String, Any>()
        fun migrate(clean: Boolean) = CredentialMigration.migrate(plain, encrypted.keys, setOf("credential"),
            { encrypted.putAll(it); true }, { keys -> if (clean) keys.forEach { plain.remove(it) }; clean })
        assertFalse(migrate(false))
        encrypted["credential"] = "placeholder-refreshed"
        assertTrue(migrate(true))
        assertEquals("placeholder-refreshed", encrypted["credential"])
        assertTrue(plain.isEmpty())
    }
    @Test fun aFailedEncryptedWriteNeverDeletesTheOnlyCopy() {
        var removed = false
        assertThrows(IllegalStateException::class.java) {
            CredentialMigration.migrate(mapOf("credential" to "placeholder"), emptySet(), setOf("credential"),
                { false }, { removed = true; true })
        }
        assertFalse(removed)
    }
}
