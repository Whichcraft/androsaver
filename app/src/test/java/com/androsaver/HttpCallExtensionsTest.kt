package com.androsaver

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.withTimeout
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.Timeout
import okio.buffer
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class HttpCallExtensionsTest {
    @Test fun cancellationInterruptsBodyConsumptionAfterHeadersWereDelivered() = runBlocking {
        withTimeout(3000) {
            val reading = CompletableDeferred<Unit>()
            val release = CountDownLatch(1)
            val call = DeferredCall { release.countDown() }
            val source = object : ForwardingSource(Buffer()) {
                override fun read(sink: Buffer, byteCount: Long): Long {
                    reading.complete(Unit)
                    check(release.await(2, TimeUnit.SECONDS)) { "Read was not interrupted by cancellation" }
                    throw java.io.IOException("Canceled read")
                }
            }.buffer()
            val body = object : ResponseBody() {
                override fun contentType(): MediaType? = null
                override fun contentLength() = -1L
                override fun source() = source
            }
            val consuming = async(Dispatchers.IO) { call.awaitResponse().use { it.body!!.string() } }
            call.enqueued.await()
            call.callback.onResponse(call, Response.Builder().request(call.request())
                .protocol(Protocol.HTTP_1_1).code(200).message("OK").body(body).build())
            reading.await()
            consuming.cancelAndJoin()
            assertTrue(call.isCanceled())
            assertEquals(0L, release.count)
        }
    }

    @Test fun closingAResponseRetiresTheCancellationWatcher() = runBlocking {
        withTimeout(2000) {
            val call = DeferredCall()
            val consuming = async { call.awaitResponse().close() }
            call.enqueued.await()
            call.callback.onResponse(call, Response.Builder().request(call.request())
                .protocol(Protocol.HTTP_1_1).code(200).message("OK").body("".toResponseBody()).build())
            consuming.await()
            assertFalse(call.isCanceled())
        }
    }

    @Test fun responseByteLimitRejectsOversizedBodies() {
        assertThrows(java.io.IOException::class.java) {
            "x".repeat(100).toResponseBody().use { it.stringLimited(10) }
        }
    }

    @Test fun cancellationAfterResponseBeforeDispatchClosesBody() = runBlocking {
        checkCancellation(afterResponse = true)
    }

    @Test fun responseArrivingAfterCancellationIsClosed() = runBlocking {
        checkCancellation(afterResponse = false)
    }

    private suspend fun kotlinx.coroutines.CoroutineScope.checkCancellation(afterResponse: Boolean) {
        val call = DeferredCall()
        var closed = false
        val source = object : ForwardingSource(Buffer()) {
            override fun close() { closed = true; super.close() }
        }.buffer()
        val body = object : ResponseBody() {
            override fun contentType(): MediaType? = null
            override fun contentLength() = 0L
            override fun source() = source
        }
        val response = Response.Builder().request(call.request()).protocol(Protocol.HTTP_1_1)
            .code(200).message("OK").body(body).build()
        val result = async(start = CoroutineStart.UNDISPATCHED) { call.awaitResponse().close() }
        if (afterResponse) call.callback.onResponse(call, response)
        result.cancel()
        if (!afterResponse) call.callback.onResponse(call, response)
        result.join()
        assertTrue(call.isCanceled())
        assertTrue(closed)
    }

    private class DeferredCall(private val onCancel: () -> Unit = {}) : Call {
        lateinit var callback: Callback
        val enqueued = CompletableDeferred<Unit>()
        @Volatile private var cancelled = false
        override fun request() = Request.Builder().url("https://example.invalid/").build()
        override fun execute(): Response = error("Not used")
        override fun enqueue(responseCallback: Callback) { callback = responseCallback; enqueued.complete(Unit) }
        override fun cancel() { cancelled = true; onCancel() }
        override fun isExecuted() = ::callback.isInitialized
        override fun isCanceled() = cancelled
        override fun timeout() = Timeout.NONE
        override fun clone(): Call = DeferredCall()
    }
}
