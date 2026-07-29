package com.androsaver

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.androsaver.source.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

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
        val prefs = com.androsaver.Prefs.get(applicationContext)
        val imageCache = ImageCache(applicationContext)

        val sources = buildList {
            if (prefs.getBoolean(Prefs.ENABLE_GOOGLE_DRIVE, false)) add(GoogleDriveSource(applicationContext))
            if (prefs.getBoolean(Prefs.ENABLE_ONEDRIVE, false)) add(OneDriveSource(applicationContext))
            if (prefs.getBoolean(Prefs.ENABLE_DROPBOX, false)) add(DropboxSource(applicationContext))
            if (prefs.getBoolean(Prefs.ENABLE_IMMICH, false)) add(ImmichSource(applicationContext))
            if (prefs.getBoolean(Prefs.ENABLE_NEXTCLOUD, false)) add(NextcloudSource(applicationContext))
            if (prefs.getBoolean(Prefs.ENABLE_SYNOLOGY, false)) add(SynologySource(applicationContext))
            if (prefs.getBoolean(Prefs.ENABLE_LOCAL_STORAGE, false)) add(LocalStorageSource(applicationContext))
        }

        if (sources.isEmpty()) {
            if (BuildConfig.DEBUG_LOGGING) Log.d(TAG, "No remote sources configured for prefetch")
            return Result.success()
        }

        val items = coroutineScope {
            sources.map { src ->
                async {
                    try {
                        withTimeoutOrNull(60_000L) { src.getImageUrls() } ?: emptyList()
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        if (BuildConfig.DEBUG_LOGGING) Log.e(TAG, "Prefetch error from ${src.name}", e)
                        emptyList()
                    }
                }
            }.awaitAll().flatten()
        }

        if (items.isNotEmpty()) {
            try {
                imageCache.saveImages(items, "mixed")
                if (BuildConfig.DEBUG_LOGGING) Log.d(TAG, "Prefetch successfully cached ${items.size} images")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (BuildConfig.DEBUG_LOGGING) Log.e(TAG, "Failed to cache prefetched images", e)
                return Result.retry()
            }
        }
        return Result.success()
    }

    companion object {
        private const val TAG = "ImagePrefetchWorker"
    }
}
