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
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.io.IOException

class ImageCache internal constructor(
    private val cacheDirectory: File,
    private val validImage: (File) -> Boolean,
    private val clientFor: (ImageItem) -> OkHttpClient,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val logFailure: (String) -> Unit = {}
) {
    constructor(context: Context) : this(
        File(context.cacheDir, CACHE_DIR),
        { file ->
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, options)
            file.isFile && file.length() > 0L && options.outWidth > 0 && options.outHeight > 0
        },
        { item -> HttpClients.forImageItem(context, item) },
        logFailure = { if (BuildConfig.DEBUG_LOGGING) Log.w(TAG, it) }
    )

    companion object {
        private const val TAG = "ImageCache"
        private const val CACHE_DIR = "image_cache"
        private const val MANIFEST = "manifest.json"
        private const val MAX_ENTRIES = 200
        private const val MAX_BYTES = 150L * 1024 * 1024
        private const val MAX_IMAGE_BYTES = 50L * 1024 * 1024
        private const val FRESHNESS_MS = 24L * 60 * 60 * 1000
        private val mutex = Mutex()
        // Serialise writers across prefetch and live engines. Readers can still
        // use the manifest mutex between downloads.
        private val writerMutex = Mutex()
    }

    private val gson = Gson()
    private val dir: File get() = cacheDirectory.also { it.mkdirs() }

    data class Entry(val key: String?, val file: String, val source: String, val ts: Long, val size: Long,
        val version: String? = null)
    private class DownloadFailure(val retryable: Boolean) : IOException("Image download rejected")

    suspend fun getCachedItems(): List<ImageItem> = withContext(Dispatchers.IO) {
        mutex.withLock {
            reconcileManifest().mapNotNull { e ->
                val key = e.key ?: return@mapNotNull null
                val f = File(dir, e.file)
                if (!f.exists()) return@mapNotNull null
                ImageItem(url = f.toURI().toString(), name = e.file, stableId = key, contentVersion = e.version)
            }
        }
    }

    data class SaveResult(val cached: Int = 0, val downloaded: Int = 0, val failed: Int = 0,
        val retryableFailed: Int = failed) {
        val needsRetry: Boolean get() = retryableFailed > 0
    }

    suspend fun saveImages(items: List<ImageItem>, sourceName: String): SaveResult = withContext(Dispatchers.IO) {
        writerMutex.withLock {
            // Validate cached contents once per batch, rather than once per item.
            val known = mutex.withLock { reconcileManifest().associateBy { it.key }.toMutableMap() }
            var cached = 0
            var downloaded = 0
            var failed = 0
            var retryableFailed = 0
            val coroutineContext = currentCoroutineContext()
            for (item in items.asSequence().filter {
                it.url.startsWith("http://") || it.url.startsWith("https://")
            }.take(MAX_ENTRIES)) {
                coroutineContext.ensureActive()
                val existing = known[item.stableId]
                val age = existing?.let { now() - it.ts }
                if (existing != null && age != null && age in 0L until FRESHNESS_MS &&
                    (item.contentVersion == null || existing.version == item.contentVersion)) {
                    cached++
                    continue
                }
                mutex.withLock {
                    val manifest = readManifest().toMutableList()
                    // Reserve space before opening the incoming file.
                    evict(manifest, MAX_BYTES - MAX_IMAGE_BYTES, protectedKey = item.stableId)
                    known.keys.retainAll(manifest.mapNotNull { it.key }.toSet())
                }
                try {
                    val entry = download(item, sourceName)
                    run {
                        // Commit even if cancellation arrives just after rename.
                        kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                            mutex.withLock {
                                val manifest = readManifest().toMutableList()
                                manifest.removeAll { it.key == item.stableId }
                                manifest += entry
                                evict(manifest)
                                known.clear()
                                known.putAll(manifest.associateBy { it.key })
                            }
                        }
                        downloaded++
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    coroutineContext.ensureActive()
                    failed++
                    if ((e is DownloadFailure && e.retryable) ||
                        (e is IOException && e !is DownloadFailure && e !is javax.net.ssl.SSLException)) {
                        retryableFailed++
                    }
                    logFailure("Cache miss (${e::class.java.simpleName})")
                }
            }
            SaveResult(cached, downloaded, failed, retryableFailed)
        }
    }

    private suspend fun download(item: ImageItem, sourceName: String): Entry {
        val request = Request.Builder().url(item.url).apply {
            item.headers.forEach { (k, v) -> addHeader(k, v) }
        }.build()
        val generation = "${sha16(item.stableId)}-${UUID.randomUUID()}"
        val temporary = File(dir, "$generation.part")
        val fileName = "$generation.img"
        val file = File(dir, fileName)
        try {
            clientFor(item).newCall(request).awaitResponse().use { response ->
                if (!response.isSuccessful) throw DownloadFailure(PrefetchRetryPolicy.http(response.code))
                val body = response.body ?: throw DownloadFailure(false)
                if (body.contentLength() > MAX_IMAGE_BYTES) throw DownloadFailure(false)
                body.byteStream().use { input ->
                    temporary.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var total = 0L
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            total += read
                            if (total > MAX_IMAGE_BYTES) throw DownloadFailure(false)
                            output.write(buffer, 0, read)
                        }
                    }
                }
            }
            if (!validImage(temporary)) throw DownloadFailure(false)
            if (!temporary.renameTo(file)) throw IOException("Could not publish cached image")
            return Entry(item.stableId, fileName, sourceName, now(), file.length(), item.contentVersion)
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    private fun evict(manifest: MutableList<Entry>, byteLimit: Long = MAX_BYTES, protectedKey: String? = null) {
        manifest.sortByDescending { it.ts }
        manifest.forEachIndexed { index, entry ->
            val actual = File(dir, entry.file).length()
            if (actual != entry.size) manifest[index] = entry.copy(size = actual)
        }
        var totalBytes = manifest.sumOf { it.size }
        while (manifest.size > MAX_ENTRIES || totalBytes > byteLimit) {
            val index = manifest.indexOfLast { it.key != protectedKey }
            if (index < 0) break
            val removed = manifest.removeAt(index)
            totalBytes -= removed.size
        }
        // Commit references before removing superseded files, so a failed
        // manifest write leaves the previous usable cache intact.
        writeManifest(manifest)
        val referenced = manifest.map { it.file }.toHashSet()
        dir.listFiles()?.filter { it.name != MANIFEST && it.name !in referenced }?.forEach { it.delete() }
    }

    private fun readManifest(): List<Entry> = synchronized(this) {
        try {
            val f = File(dir, MANIFEST)
            if (f.exists()) {
                val raw = f.readText()
                // Legacy manifests contain secret-bearing URL fields. Discard the
                // entire legacy cache rather than retaining those values or trying
                // to migrate temporary credentials.
                if (raw.contains("\"url\"")) {
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
            file.isFile && file.length() == entry.size && validImage(file)
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
