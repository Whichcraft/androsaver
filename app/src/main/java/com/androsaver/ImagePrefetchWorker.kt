package com.androsaver

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.androsaver.source.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException

/**
 * Background worker that prefetches and caches images from all configured
 * remote image sources in the background so that offline fallback is warm.
 */
class ImagePrefetchWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (BuildConfig.DEBUG_LOGGING) Log.d(TAG, "Starting background image prefetch...")
        val imageCache = ImageCache(applicationContext)

        val sources = ImageSourceRegistry.configured(applicationContext)
            .filterNot { it is LocalStorageSource }

        if (sources.isEmpty()) {
            if (BuildConfig.DEBUG_LOGGING) Log.d(TAG, "No remote sources configured for prefetch")
            return Result.success()
        }

        data class SourceResult(val source: ImageSource, val result: ImageSourceResult)
        val results = coroutineScope {
            sources.map { src ->
                async {
                    val result = try {
                        withTimeout(60_000L) { src.enumerate() }
                    } catch (e: TimeoutCancellationException) {
                        ImageSourceResult.Failure(ImageSourceResult.FailureKind.TIMEOUT)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        if (BuildConfig.DEBUG_LOGGING) Log.e(TAG, "Prefetch error from ${src.name}", e)
                        ImageSourceResult.Failure(ImageSourceResult.FailureKind.UNKNOWN)
                    }
                    SourceHealthStore.record(applicationContext, src.name, result)
                    SourceResult(src, result)
                }
            }.awaitAll()
        }

        val sourceNeedsRetry = results.any { PrefetchRetryPolicy.source(it.result) }
        if (results.all { it.result is ImageSourceResult.Failure }) {
            return if (PrefetchRetryPolicy.retry(runAttemptCount, sourceNeedsRetry)) Result.retry() else Result.success()
        }

        try {
            val groups = results.map { (it.result as? ImageSourceResult.Success)?.items.orEmpty() }
            val prefs = Prefs.get(applicationContext)
            val saved = imageCache.warm(groups) { SourceIdentity.eligible(prefs, it.sourceScope) }
            if (PrefetchRetryPolicy.retry(runAttemptCount, sourceNeedsRetry || saved.needsRetry)) return Result.retry()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (BuildConfig.DEBUG_LOGGING) Log.e(TAG, "Failed to cache prefetched images", e)
            return if (PrefetchRetryPolicy.retry(runAttemptCount, e is java.io.IOException)) Result.retry() else Result.success()
        }
        return Result.success()
    }

    companion object {
        private const val TAG = "ImagePrefetchWorker"
    }
}
