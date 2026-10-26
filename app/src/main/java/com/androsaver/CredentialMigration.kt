package com.androsaver

/** An encrypted value is authoritative, including after a failed plaintext cleanup. */
internal object CredentialMigration {
    fun migrate(
        plain: Map<String, *>, encryptedKeys: Set<String>, sensitiveKeys: Set<String>,
        writeEncrypted: (Map<String, Any>) -> Boolean, removePlain: (Set<String>) -> Boolean
    ): Boolean {
        val staleKeys = plain.keys.intersect(sensitiveKeys)
        val missing = staleKeys.filter { it !in encryptedKeys }.mapNotNull { key ->
            plain[key]?.let { key to it }
        }.toMap()
        if (missing.isNotEmpty() && !writeEncrypted(missing)) {
            throw IllegalStateException("Could not persist encrypted preference migration")
        }
        return staleKeys.isEmpty() || removePlain(staleKeys)
    }
}
