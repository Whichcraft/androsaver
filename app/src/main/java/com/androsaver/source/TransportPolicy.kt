package com.androsaver.source

/** No fallback can turn a failed secure connection into a weaker connection. */
internal object TransportPolicy {
    suspend fun <T> connect(https: Boolean, insecureOptIn: Boolean, attempt: suspend (Boolean) -> T): T {
        requireAllowed(https, insecureOptIn)
        return attempt(insecureOptIn)
    }
    fun requireAllowed(https: Boolean, insecureOptIn: Boolean) {
        if (!https && !insecureOptIn) throw java.io.IOException("HTTP requires explicit insecure-connection consent")
    }
}
