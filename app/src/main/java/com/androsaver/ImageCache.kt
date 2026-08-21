package com.androsaver

import android.content.Context
import android.util.Log
import android.graphics.BitmapFactory
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
        private const val MAX_IMAGE_BYTES = 50L * 1024 * 1024
        private val mutex = Mutex()
        private val activeKeys = mutableSetOf<String>()
    }

    private val gson = Gson()
    private val dir: File get() = File(context.cacheDir, CACHE_DIR).also { it.mkdirs() }

    data class Entry(val key: String?, val file: String, val source: String, val ts: Long, val size: Long)

    suspend fun hasCache(): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock { reconcileManifest().isNotEmpty() }
    }

    suspend fun getCachedItems(): List<ImageItem> = withContext(Dispatchers.IO) {
        mutex.withLock {
            reconcileManifest().mapNotNull { e ->
                val key = e.key ?: return@mapNotNull null
                val f = File(dir, e.file)
                if (!f.exists()) return@mapNotNull null
                ImageItem(url = f.toURI().toString(), name = e.file, stableId = key)
            }
        }
    }

    suspend fun saveImages(items: List<ImageItem>, sourceName: String) = withContext(Dispatchers.IO) {
        val coroutineContext = currentCoroutineContext()
        val existing = mutex.withLock { reconcileManifest().mapNotNull { it.key }.toHashSet() }
        val newEntries = mutableListOf<Entry>()
        for (item in items.asSequence().filter { it.url.startsWith("http://") || it.url.startsWith("https://") }.take(MAX_ENTRIES)) {
            coroutineContext.ensureActive()
            if (item.stableId in existing) continue
            val claimed = mutex.withLock { activeKeys.add(item.stableId) }
            if (!claimed) continue
            try {
                download(item, sourceName)?.let {
                    newEntries += it
                    existing += item.stableId
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (BuildConfig.DEBUG_LOGGING) Log.w(TAG, "Cache miss for ${item.name.ifBlank { "image" }} (${e::class.java.simpleName})")
            } finally {
                mutex.withLock { activeKeys.remove(item.stableId) }
            }
        }
        if (newEntries.isNotEmpty()) {
            mutex.withLock {
                val manifest = readManifest().toMutableList()
                val known = manifest.mapNotNull { it.key }.toHashSet()
                newEntries.filter { known.add(it.key ?: "") }.forEach { manifest += it }
                evict(manifest)
            }
        }
    }

    private suspend fun download(item: ImageItem, sourceName: String): Entry? {
        val request = Request.Builder().url(item.url).apply {
            item.headers.forEach { (k, v) -> addHeader(k, v) }
        }.build()
        val temporary = File(dir, "${sha16(item.stableId)}.part")
        val fileName = sha16(item.stableId) + ".img"
        val file = File(dir, fileName)
        try {
                HttpClients.forImageItem(context, item).newCall(request).awaitResponse().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body ?: return null
                if (body.contentLength() > MAX_IMAGE_BYTES) return null
                body.byteStream().use { input ->
                    temporary.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var total = 0L
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            total += read
                            if (total > MAX_IMAGE_BYTES) return null
                            output.write(buffer, 0, read)
                        }
                    }
                }
            }
            if (!isDecodableImage(temporary)) return null
            if (file.exists()) file.delete()
            if (!temporary.renameTo(file)) return null
            return Entry(item.stableId, fileName, sourceName, System.currentTimeMillis(), file.length())
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    private fun isDecodableImage(file: File): Boolean {
        if (!file.isFile || file.length() <= 0L) return false
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        return options.outWidth > 0 && options.outHeight > 0
    }

    private fun evict(manifest: MutableList<Entry>) {
        manifest.sortByDescending { it.ts }
        val referenced = manifest.map { it.file }.toHashSet()
        dir.listFiles()?.filter { it.name != MANIFEST && it.name !in referenced }?.forEach { it.delete() }
        manifest.forEachIndexed { index, entry ->
            val actual = File(dir, entry.file).length()
            if (actual != entry.size) manifest[index] = entry.copy(size = actual)
        }
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
            if (f.exists()) {
                val raw = f.readText()
                // Legacy manifests contain secret-bearing URL fields. Discard the
                // entire legacy cache rather than retaining those values or trying
                // to migrate temporary credentials.
                if (raw.contains("\"url\"") || !raw.contains("\"key\"")) {
                    dir.listFiles()?.filter { it != f }?.forEach { it.delete() }
                    f.delete()
                    return@synchronized emptyList()
                }
                val entries: List<Entry> = gson.fromJson(raw, object : TypeToken<List<Entry>>() {}.type) ?: emptyList()
                entries.filter { !it.key.isNullOrBlank() }
            }
            else emptyList()
        } catch (_: Exception) { emptyList() }
    }

    /** Removes manifest entries whose files were partially evicted or corrupted. */
    private fun reconcileManifest(): List<Entry> {
        val manifest = readManifest()
        val valid = manifest.filter { entry ->
            val file = File(dir, entry.file)
            file.isFile && file.length() == entry.size && isDecodableImage(file)
        }
        if (valid.size != manifest.size) {
            val validFiles = valid.map { it.file }.toHashSet()
            manifest.filter { it.file !in validFiles }.forEach { File(dir, it.file).delete() }
            writeManifest(valid)
        }
        return valid
    }

    private fun writeManifest(entries: List<Entry>) {
        synchronized(this) {
            val manifest = File(dir, MANIFEST)
            val temporary = File(dir, "$MANIFEST.tmp")
            temporary.writeText(gson.toJson(entries))
            if (!temporary.renameTo(manifest)) {
                temporary.delete()
                throw java.io.IOException("Could not commit image cache manifest")
            }
        }
    }

    private fun sha16(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }.take(16)
}
