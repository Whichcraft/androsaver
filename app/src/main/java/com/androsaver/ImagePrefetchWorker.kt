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

        if (results.none { it.result !is ImageSourceResult.Failure }) return Result.retry()

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
            selected.groupBy({ it.first }, { it.second }).forEach { (source, sourceItems) ->
                imageCache.saveImages(sourceItems, source.name)
            }
            if (BuildConfig.DEBUG_LOGGING) Log.d(TAG, "Prefetch successfully cached ${selected.size} images")
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (BuildConfig.DEBUG_LOGGING) Log.e(TAG, "Failed to cache prefetched images", e)
            return Result.retry()
        }
        return Result.success()
    }

    companion object {
        private const val TAG = "ImagePrefetchWorker"
    }
}
