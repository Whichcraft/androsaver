package com.androsaver

import android.content.Context
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.androsaver.source.ImageItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
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
    private val client = HttpClients.trustAll
    private val dir: File get() = File(context.cacheDir, CACHE_DIR).also { it.mkdirs() }

    data class Entry(val url: String, val file: String, val source: String, val ts: Long, val size: Long)

    fun hasCache(): Boolean = readManifest().isNotEmpty()

    fun getCachedItems(): List<ImageItem> =
        readManifest().mapNotNull { e ->
            val f = File(dir, e.file)
            if (!f.exists()) return@mapNotNull null
            val orientation = try { ExifInterface(f.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED) } catch (_: Exception) { ExifInterface.ORIENTATION_UNDEFINED }
            ImageItem(url = f.toURI().toString(), name = e.file, orientation = orientation)
        }

    suspend fun saveImages(items: List<ImageItem>, sourceName: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val manifest = readManifest().toMutableList()
            val existing = manifest.map { it.url }.toHashSet()
            var saved = 0
            for (item in items.take(MAX_ENTRIES)) {
                if (item.url in existing) continue
                try {
                    val req = Request.Builder().url(item.url).apply {
                        item.headers.forEach { (k, v) -> addHeader(k, v) }
                    }.build()
                    val fname = sha16(item.url) + ".jpg"
                    val file = File(dir, fname)
                    val size = client.newCall(req).execute().use { response ->
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
                        saved++
                    }
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG_LOGGING) Log.w(TAG, "Cache miss for ${item.url}: ${e.message}")
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
            val removed = manifest.removeLast()
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
            try { File(dir, MANIFEST).writeText(gson.toJson(entries)) } catch (_: Exception) {}
        }
    }

    private fun sha16(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }.take(16)
}
