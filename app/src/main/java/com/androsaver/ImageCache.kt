package com.androsaver

import android.content.Context
import android.util.Log
import com.androsaver.source.ImageItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Request
import java.io.File
import java.security.MessageDigest

class ImageCache(private val context: Context) {

    companion object {
        private const val TAG = "ImageCache"
        private const val CACHE_DIR = "image_cache"
        private const val MANIFEST = "manifest.json"
        private const val MAX_ENTRIES = 200
        private const val MAX_BYTES = 150L * 1024 * 1024
        private val mutex = Mutex()
    }

    private val gson = Gson()
    private val dir: File get() = File(context.cacheDir, CACHE_DIR).also { it.mkdirs() }

    data class Entry(val url: String, val file: String, val source: String, val ts: Long, val size: Long)

    suspend fun hasCache(): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock { readManifest().isNotEmpty() }
    }

    suspend fun getCachedItems(): List<ImageItem> = withContext(Dispatchers.IO) {
        mutex.withLock {
            readManifest().mapNotNull { e ->
                val f = File(dir, e.file)
                if (!f.exists()) return@mapNotNull null
                ImageItem(url = f.toURI().toString(), name = e.file)
            }
        }
    }

    suspend fun saveImages(items: List<ImageItem>, sourceName: String) = withContext(Dispatchers.IO) {
        val coroutineContext = currentCoroutineContext()
        mutex.withLock {
            val manifest = readManifest().toMutableList()
            val existing = manifest.map { it.url }.toHashSet()
            var saved = 0
            for (item in items.take(MAX_ENTRIES)) {
                // Synchronous OkHttp calls are not themselves coroutine-aware.
                // Stop the potentially long cache loop after the current request
                // when its owner (DreamService or WorkManager) is cancelled.
                coroutineContext.ensureActive()
                if (item.url in existing) continue
                try {
                    val req = Request.Builder().url(item.url).apply {
                        item.headers.forEach { (k, v) -> addHeader(k, v) }
                    }.build()
                    val fname = sha16(item.url) + ".jpg"
                    val file = File(dir, fname)
                    val size = HttpClients.forHost(context, req.url.host)
                        .newCall(req).execute().use { response ->
                        if (!response.isSuccessful) return@use 0L
                        val body = response.body ?: return@use 0L
                        body.byteStream().use { input ->
                            file.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        file.length()
                    }
                    if (size > 0L) {
                        manifest.add(Entry(item.url, fname, sourceName, System.currentTimeMillis(), size))
                        existing.add(item.url)
                        saved++
                    } else {
                        file.delete()
                    }
                } catch (e: Exception) {
                    // Some download URLs contain short-lived access tokens or a
                    // Synology SID in their query string. Never copy them to logcat.
                    val label = item.name.ifBlank { "image" }
                    if (BuildConfig.DEBUG_LOGGING) {
                        Log.w(TAG, "Cache miss for $label (${e::class.java.simpleName})")
                    }
                }
            }
            evict(manifest)
            if (saved > 0 && BuildConfig.DEBUG_LOGGING) Log.d(TAG, "Cached $saved new images")
        }
    }

    private fun evict(manifest: MutableList<Entry>) {
        manifest.sortByDescending { it.ts }
        var totalBytes = manifest.sumOf { it.size }
        while (manifest.size > MAX_ENTRIES || totalBytes > MAX_BYTES) {
            // Do not use MutableList.removeLast() here. When compiled on JDK 21
            // it binds to java.util.List.removeLast(), which does not exist on
            // older Android runtimes and throws NoSuchMethodError.
            val removed = manifest.removeAt(manifest.lastIndex)
            totalBytes -= removed.size
            File(dir, removed.file).delete()
        }
        writeManifest(manifest)
    }

    private fun readManifest(): List<Entry> = synchronized(this) {
        try {
            val f = File(dir, MANIFEST)
            if (f.exists()) gson.fromJson(f.readText(), object : TypeToken<List<Entry>>() {}.type) ?: emptyList()
            else emptyList()
        } catch (_: Exception) { emptyList() }
    }

    private fun writeManifest(entries: List<Entry>) {
        synchronized(this) {
            try {
                val manifest = File(dir, MANIFEST)
                val temporary = File(dir, "$MANIFEST.tmp")
                temporary.writeText(gson.toJson(entries))
                if (!temporary.renameTo(manifest)) {
                    manifest.writeText(temporary.readText())
                    temporary.delete()
                }
            } catch (_: Exception) {}
        }
    }

    private fun sha16(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }.take(16)
}
