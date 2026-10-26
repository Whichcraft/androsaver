package com.androsaver.source

import java.security.MessageDigest

/** Only random identity and non-secret configuration enter the persisted scope. */
internal object SourceScope {
    fun create(provider: String, identity: String, configuration: List<String?>): String {
        val input = (listOf(provider, identity) + configuration).joinToString("\u0000")
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return "source:$provider:$digest"
    }
    fun bind(item: ImageItem, scope: String) = item.copy(stableId = "$scope:${item.stableId}", sourceScope = scope)
    fun eligible(scope: String?, allowed: Set<String>) = scope != null && scope in allowed
    fun provider(scope: String?) = scope?.takeIf { it.startsWith("source:") }?.split(':')?.getOrNull(1)
}
