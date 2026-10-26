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
                    SourceResult(src, result)
                }
            }.awaitAll()
        }

        val sourceNeedsRetry = results.any { PrefetchRetryPolicy.source(it.result) }
        if (results.all { it.result is ImageSourceResult.Failure }) {
            return if (PrefetchRetryPolicy.retry(runAttemptCount, sourceNeedsRetry)) Result.retry() else Result.success()
        }

        val queues = results.map {
            (it.result as? ImageSourceResult.Success)?.items?.toMutableList() ?: mutableListOf()
        }
        val selected = mutableListOf<Pair<ImageSource, ImageItem>>()
        while (selected.size < 200 && queues.any { it.isNotEmpty() }) {
            results.forEachIndexed { index, result ->
                if (selected.size >= 200) return@forEachIndexed
                queues[index].removeFirstOrNull()?.let { selected += result.source to it }
            }
        }

        try {
            var cached = 0
            var downloaded = 0
            var failed = 0
            var shouldRetry = sourceNeedsRetry
            selected.groupBy({ it.first }, { it.second }).forEach { (source, sourceItems) ->
                val saved = imageCache.saveImages(sourceItems, source.name)
                cached += saved.cached
                downloaded += saved.downloaded
                failed += saved.failed
                shouldRetry = shouldRetry || saved.needsRetry
            }
            if (BuildConfig.DEBUG_LOGGING) Log.d(TAG,
                "Prefetch: $cached already cached, $downloaded downloaded, $failed failed")
            if (PrefetchRetryPolicy.retry(runAttemptCount, shouldRetry)) return Result.retry()
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
