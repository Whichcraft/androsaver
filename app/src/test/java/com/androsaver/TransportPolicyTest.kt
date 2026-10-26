package com.androsaver

import com.androsaver.source.TransportPolicy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import javax.net.ssl.SSLHandshakeException

class TransportPolicyTest {
    @Test fun certificateFailuresNeverTriggerAnUnrequestedTrustOrHttpFallback() = runBlocking {
        for (optIn in listOf(false, true)) {
            val attempts = mutableListOf<Boolean>()
            val failure = SSLHandshakeException("Expired or self-signed placeholder certificate")
            try {
                TransportPolicy.connect(true, optIn) { attempts += it; throw failure }
                fail("Expected TLS failure")
            } catch (actual: SSLHandshakeException) { assertSame(failure, actual) }
            assertEquals(listOf(optIn), attempts)
        }
    }
    @Test fun httpNeedsExplicitConsentAndHttpsRespectsEitherChoice() = runBlocking {
        var called = false
        try { TransportPolicy.connect(false, false) { called = true }; fail("HTTP accepted") }
        catch (_: java.io.IOException) { }
        assertFalse(called)
        assertTrue(TransportPolicy.connect(false, true) { it })
        assertFalse(TransportPolicy.connect(true, false) { it })
    }
}
