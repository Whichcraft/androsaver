package com.androsaver

import com.androsaver.source.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class TemporaryLinksTest {
    @Test fun partialNetworkFailureKeepsLinksAndRequestsWorkerRetry() = runBlocking {
        val result = TemporaryLinks.load(listOf("good", "offline", "busy")) { id ->
            when (id) {
                "offline" -> throw IOException("offline")
                "busy" -> throw ImageSourceHttpException(503)
                else -> ImageItem(id)
            }
        }
        assertEquals(listOf("good"), result.items.map { it.url })
        assertTrue(PrefetchRetryPolicy.source(result))
    }
    @Test fun allFailedLinksAreRetryableAndRateLimitingStopsQueuedRequests() = runBlocking {
        var calls = 0
        val result = TemporaryLinks.load((1..100).toList()) {
            calls++
            throw ImageSourceHttpException(429)
        }
        assertTrue(result.items.isEmpty())
        assertTrue(PrefetchRetryPolicy.source(result))
        assertEquals(1, calls)
    }
    @Test fun authenticationAndCancellationAreNotDisguisedAsAnEmptyFolder() = runBlocking {
        val auth = imageSourceResult { TemporaryLinks.load(listOf(1)) { throw ImageSourceHttpException(401) } }
        assertEquals(ImageSourceResult.FailureKind.AUTHENTICATION, (auth as ImageSourceResult.Failure).kind)
        assertEquals(401, auth.httpStatus)
        try {
            TemporaryLinks.load(listOf(1)) { throw CancellationException("stop") }
            fail("Cancellation was swallowed")
        } catch (_: CancellationException) { }
    }
}
