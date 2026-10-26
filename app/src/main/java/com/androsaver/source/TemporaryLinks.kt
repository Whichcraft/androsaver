package com.androsaver.source

import com.androsaver.PrefetchRetryPolicy
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SSLException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Keep useful links, preserve retry information, and stop queued calls after throttling. */
internal object TemporaryLinks {
    private data class Link(val item: ImageItem? = null, val retryable: Boolean = false)
    suspend fun <T> load(items: List<T>, fetch: suspend (T) -> ImageItem): ImageSourceResult.Success = coroutineScope {
        val throttled = AtomicBoolean(false)
        val semaphore = Semaphore(10)
        val results = items.map { item ->
            async {
                semaphore.withPermit {
                    if (throttled.get()) return@withPermit Link(retryable = true)
                    try { Link(fetch(item)) } catch (e: Exception) {
                        when {
                            e is CancellationException -> throw e
                            e is ImageSourceHttpException && (e.code == 401 || e.code == 403) -> throw e
                            e is ImageSourceHttpException -> {
                                if (e.code == 429) throttled.set(true)
                                Link(retryable = PrefetchRetryPolicy.http(e.code))
                            }
                            e is IOException && e !is SSLException -> Link(retryable = true)
                            else -> throw e
                        }
                    }
                }
            }
        }.awaitAll()
        ImageSourceResult.Success(results.mapNotNull { it.item }, results.any { it.retryable })
    }
}
