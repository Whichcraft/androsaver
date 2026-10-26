package com.androsaver

import android.content.Context
import com.androsaver.source.ImageItem
import com.bumptech.glide.Glide
import com.bumptech.glide.signature.ObjectKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal object ImageCachePolicy {
    fun signature(item: ImageItem) = ObjectKey(listOf(item.sourceScope.orEmpty(), item.stableId, item.contentVersion.orEmpty()))
    private val mutex = Mutex()
    suspend fun purgeLegacyDiskCache(context: Context) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val prefs = context.getSharedPreferences("image_cache_policy", Context.MODE_PRIVATE)
            if (!prefs.getBoolean("legacy_glide_removed", false)) {
                Glide.get(context).clearDiskCache()
                check(prefs.edit().putBoolean("legacy_glide_removed", true).commit())
            }
        }
    }
}
