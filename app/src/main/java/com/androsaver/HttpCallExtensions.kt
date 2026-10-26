package com.androsaver

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import okhttp3.ResponseBody
import okio.ForwardingSource
import okio.buffer
import java.util.concurrent.atomic.AtomicBoolean
import java.io.IOException
import java.nio.charset.StandardCharsets
import kotlin.coroutines.resumeWithException

/** Cancellation covers headers AND body consumption; callers must close the response. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
suspend fun Call.awaitResponse(): Response {
    val call = this
    val finished = AtomicBoolean(false)
    // Unconfined cancellation runs promptly even if the consumer is blocked in
    // a socket read on its dispatcher. Closing the body retires this child job.
    val lifetime = CoroutineScope(currentCoroutineContext()).launch(
        Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED
    ) {
        try { awaitCancellation() }
        finally { if (!finished.get()) call.cancel() }
    }
    fun complete() {
        finished.set(true)
        lifetime.cancel()
    }
    try {
        return suspendCancellableCoroutine { continuation ->
            enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    complete()
                    if (!continuation.isCancelled) continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    val body = response.body
                    val wrapped = if (body == null) {
                        complete()
                        response
                    } else response.newBuilder().body(object : ResponseBody() {
                        private val stream by lazy {
                            object : ForwardingSource(body.source()) {
                                override fun close() {
                                    try { super.close() } finally { complete() }
                                }
                            }.buffer()
                        }
                        override fun contentType() = body.contentType()
                        override fun contentLength() = body.contentLength()
                        override fun source() = stream
                    }).build()
                    continuation.resume(wrapped) { wrapped.close() }
                }
            })
            continuation.invokeOnCancellation {
                call.cancel()
                complete()
            }
        }
    } catch (error: Throwable) {
        call.cancel()
        complete()
        throw error
    }
}

fun okhttp3.ResponseBody.stringLimited(maxBytes: Long): String {
    byteStream().use { input ->
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > maxBytes) throw IOException("Response exceeds size limit")
            output.write(buffer, 0, read)
        }
        return output.toString(StandardCharsets.UTF_8.name())
    }
}
