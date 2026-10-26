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
    private val logFailure: (String) -> Unit = {},
    private val maxBytes: Long = MAX_BYTES,
    private val maxImageBytes: Long = MAX_IMAGE_BYTES
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
        private const val SOURCE_STATE = "source-state.json"
        private const val MAX_ENTRIES = 200
        private const val MAX_BYTES = 150L * 1024 * 1024
        private const val MAX_IMAGE_BYTES = 50L * 1024 * 1024
        private const val FRESHNESS_MS = 24L * 60 * 60 * 1000
        private val mutex = Mutex()
        // Serialise writers across prefetch and live engines. Readers can still
        // use the manifest mutex between downloads.
        private val writerMutex = Mutex()
        private var sourceStateGeneration = 0L
    }

    private val gson = Gson()
    private var cachedSourceGeneration = Long.MIN_VALUE
    private var cachedSourceState: Map<String, List<String>> = emptyMap()
    private val dir: File get() = cacheDirectory.also { it.mkdirs() }

    data class Entry(val key: String?, val file: String, val source: String, val ts: Long, val size: Long,
        val version: String? = null, val scope: String? = null)
    private class BudgetExhausted : IOException("Cache warming budget exhausted")
    private class DownloadFailure(val retryable: Boolean) : IOException("Image download rejected")

    suspend fun getCachedItems(): List<ImageItem> = withContext(Dispatchers.IO) {
        mutex.withLock {
            reconcileManifest().mapNotNull { e ->
                val key = e.key ?: return@mapNotNull null
                val f = File(dir, e.file)
                if (!f.exists()) return@mapNotNull null
                ImageItem(url = f.toURI().toString(), name = e.file, stableId = key, contentVersion = e.version, sourceScope = e.scope)
            }
        }
    }

    class Snapshot internal constructor(val items: List<ImageItem>, private val lease: java.io.Closeable) : java.io.Closeable {
        override fun close() = lease.close()
    }

    /** Acquire before releasing the manifest lock; cancellation must not strand a lease. */
    suspend fun acquireSnapshot(eligibleScopes: Set<String>? = null): Snapshot {
        var acquired: Snapshot? = null
        try {
            return withContext(Dispatchers.IO) {
                mutex.withLock {
                    val state = readSourceState()
                    val entries = reconcileManifest().filter {
                        (eligibleScopes == null || com.androsaver.source.SourceScope.eligible(it.scope, eligibleScopes)) &&
                            allowed(it.scope, it.key, state)
                    }
                    val files = entries.map { File(dir, it.file) }
                    Snapshot(entries.map { entry ->
                        ImageItem(File(dir, entry.file).toURI().toString(), entry.file,
                            stableId = requireNotNull(entry.key), contentVersion = entry.version, sourceScope = entry.scope)
                    }, CacheFileLeases.acquire(files)).also { acquired = it }
                }
            }
        } catch (error: Throwable) {
            acquired?.close()
            throw error
        }
    }

    suspend fun warm(groups: List<List<ImageItem>>, eligible: (ImageItem) -> Boolean = { true }): SaveResult {
        val selected = withContext(Dispatchers.Default) { CachePlan.select(groups) }
        return try {
            kotlinx.coroutines.withTimeout(90_000L) {
                saveImages(selected, "planned", selected.map { it.stableId }.toSet(), 100L * 1024 * 1024, eligible)
            }
        } catch (error: kotlinx.coroutines.TimeoutCancellationException) {
            currentCoroutineContext().ensureActive()
            SaveResult(failed = 1, retryableFailed = 1)
        }
    }

    /** A complete listing is durable authority, including an empty library. Leased bytes survive until retired. */
    internal suspend fun recordListing(scope: String, result: com.androsaver.source.ImageSourceResult, currentNamespaces: Set<String>? = null) = withContext(Dispatchers.IO) {
        val keys = when (result) {
            is com.androsaver.source.ImageSourceResult.Success -> if (result.retryableFailures) return@withContext else result.items.map { it.stableId }
            com.androsaver.source.ImageSourceResult.Empty -> emptyList()
            else -> return@withContext
        }
        mutex.withLock {
            val state = readSourceState().toMutableMap()
            state[scope] = keys
            // Preserve authority even when no image has committed yet: a writer may still be in flight.
            // Keep current configurations (including disabled sources) and compact historical authority to stored keys.
            if (currentNamespaces != null) {
                if (scope !in currentNamespaces) return@withLock
                val stored = readManifest().groupBy { it.scope }
                state.keys.retainAll(currentNamespaces + stored.keys.filterNotNull())
                state.entries.filter { it.key !in currentNamespaces }.forEach { entry ->
                    val keys = stored[entry.key].orEmpty().mapNotNull { it.key }.toSet()
                    entry.setValue(entry.value.filter { it in keys })
                }
            }
            val target = File(dir, SOURCE_STATE)
            val temporary = File(dir, "$SOURCE_STATE.tmp")
            temporary.writeText(gson.toJson(state))
            if (!temporary.renameTo(target)) { temporary.delete(); throw IOException("Could not save source state") }
            sourceStateGeneration++
            cachedSourceGeneration = sourceStateGeneration
            cachedSourceState = state
            // Do not evict here: another writer may be streaming a staged image.
            // Readers and writers check this authority; ordinary eviction reclaims retired bytes.
        }
    }
    private fun readSourceState(): Map<String, List<String>> {
        if (cachedSourceGeneration == sourceStateGeneration) return cachedSourceState
        val file = File(dir, SOURCE_STATE)
        val state: Map<String, List<String>> = if (!file.exists()) emptyMap() else try {
            gson.fromJson<Map<String, List<String>>>(file.readText(), object : TypeToken<Map<String, List<String>>>() {}.type)
                ?: throw IOException("Invalid saved source state")
        } catch (error: Exception) { throw IOException("Could not read saved source state", error) }
        cachedSourceState = state
        cachedSourceGeneration = sourceStateGeneration
        return state
    }
    private fun allowed(scope: String?, key: String?, state: Map<String, List<String>>) =
        scope == null || state[scope]?.let { key in it } != false
    private fun allowedByListing(item: ImageItem) = allowed(item.sourceScope, item.stableId, readSourceState())

    private fun canFit(entries: List<Entry>, protectedKey: String?, protectedPlan: Set<String>): Boolean {
        val referenced = entries.map { File(dir, it.file).absoluteFile }.toSet()
        val orphans = CacheFileLeases.inDirectory(dir) - referenced
        val required = entries.filter { it.key == protectedKey || it.key in protectedPlan || CacheFileLeases.contains(File(dir, it.file)) }
        return required.size + orphans.size <= MAX_ENTRIES && required.sumOf { it.size } + orphans.sumOf { it.length() } <= maxBytes
    }

    data class SourceUsage(val provider: String, val count: Int, val bytes: Long)
    suspend fun sourceUsage(): List<SourceUsage> = withContext(Dispatchers.IO) {
        mutex.withLock { readManifest().groupBy { com.androsaver.source.SourceScope.provider(it.scope) ?: "legacy" }
            .map { (provider, entries) -> SourceUsage(provider, entries.size, entries.sumOf { it.size }) } }
    }
    /** Refuse rather than invalidate an active reader; settings can retry after playback stops. */
    suspend fun clearSource(provider: String): Boolean = withContext(Dispatchers.IO) {
        writerMutex.withLock { mutex.withLock clear@ {
            val manifest = readManifest()
            val removed = manifest.filter { (com.androsaver.source.SourceScope.provider(it.scope) ?: "legacy") == provider }
            if (removed.any { CacheFileLeases.contains(File(dir, it.file)) }) return@clear false
            val failed = removed.filter { val file = File(dir, it.file); file.exists() && !file.delete() }
            writeManifest(manifest - (removed - failed.toSet()).toSet())
            if (failed.isNotEmpty()) throw IOException("Could not clear saved image")
            true
        } }
    }

    data class SaveResult(val cached: Int = 0, val downloaded: Int = 0, val failed: Int = 0,
        val retryableFailed: Int = failed) {
        val needsRetry: Boolean get() = retryableFailed > 0
    }

    suspend fun saveImages(
        items: List<ImageItem>, sourceName: String,
        plannedKeys: Set<String> = emptySet(), networkBudget: Long = Long.MAX_VALUE,
        eligible: (ImageItem) -> Boolean = { true }
    ): SaveResult = withContext(Dispatchers.IO) {
        writerMutex.withLock {
            // Validate cached contents once per batch, rather than once per item.
            val known = mutex.withLock { reconcileManifest().associateBy { it.key }.toMutableMap() }
            var cached = 0
            var downloaded = 0
            var failed = 0
            var retryableFailed = 0
            var remainingBytes = networkBudget
            val coroutineContext = currentCoroutineContext()
            for (item in items.asSequence().filter {
                it.url.startsWith("http://") || it.url.startsWith("https://")
            }.take(MAX_ENTRIES)) {
                coroutineContext.ensureActive()
                if (!eligible(item) || !mutex.withLock { allowedByListing(item) }) continue
                val existing = known[item.stableId]
                val age = existing?.let { now() - it.ts }
                if (existing != null && age != null && age in 0L until FRESHNESS_MS &&
                    (item.contentVersion == null || existing.version == item.contentVersion)) {
                    cached++
                    continue
                }
                if (remainingBytes <= 0) break
                // A non-destructive minimum-size check avoids network work when leases/count make any commit impossible.
                val possible = mutex.withLock {
                    val manifest = readManifest().filter { it.key != item.stableId } +
                        Entry(item.stableId, "unpublished-candidate", sourceName, now(), 1L, item.contentVersion, item.sourceScope)
                    canFit(manifest, item.stableId, plannedKeys)
                }
                if (!possible) { failed++; break }
                var capacityBlocked = false
                try {
                    val entry = download(item, sourceName, remainingBytes) { count -> remainingBytes -= count }
                    if (!eligible(item)) { File(dir, entry.file).delete(); continue }
                    run {
                        // Commit even if cancellation arrives just after rename.
                        kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                            mutex.withLock {
                                val manifest = readManifest().toMutableList()
                                if (!allowedByListing(item)) { File(dir, entry.file).delete(); return@withLock }
                                manifest.removeAll { it.key == item.stableId }
                                manifest += entry
                                if (!canFit(manifest, entry.key, plannedKeys)) {
                                    File(dir, entry.file).delete()
                                    failed++
                                    capacityBlocked = true
                                    return@withLock
                                }
                                evict(manifest, protectedKey = entry.key, protectedPlan = plannedKeys)
                                known.clear()
                                known.putAll(manifest.associateBy { it.key })
                                downloaded++
                            }
                        }
                    }
                } catch (_: BudgetExhausted) {
                    break
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
                if (capacityBlocked) break
            }
            SaveResult(cached, downloaded, failed, retryableFailed)
        }
    }

    private suspend fun download(
        item: ImageItem, sourceName: String, remainingBudget: Long = Long.MAX_VALUE,
        consumed: (Long) -> Unit = {}
    ): Entry {
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
                if (body.contentLength() > maxImageBytes) throw DownloadFailure(false)
                if (body.contentLength() > remainingBudget || remainingBudget <= 0) throw BudgetExhausted()
                body.byteStream().use { input ->
                    temporary.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var total = 0L
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val readLimit = if (remainingBudget == Long.MAX_VALUE) buffer.size
                                else (remainingBudget - total + 1).coerceIn(1L, buffer.size.toLong()).toInt()
                            val read = input.read(buffer, 0, readLimit)
                            if (read < 0) break
                            total += read
                            consumed(read.toLong())
                            if (total > remainingBudget) throw BudgetExhausted()
                            if (total > maxImageBytes) throw DownloadFailure(false)
                            output.write(buffer, 0, read)
                        }
                    }
                }
            }
            if (!validImage(temporary)) throw DownloadFailure(false)
            if (!temporary.renameTo(file)) throw IOException("Could not publish cached image")
            return Entry(item.stableId, fileName, sourceName, now(), file.length(), item.contentVersion, item.sourceScope)
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    private fun evict(
        manifest: MutableList<Entry>, byteLimit: Long = maxBytes,
        protectedKey: String? = null, entryLimit: Int = MAX_ENTRIES, protectedPlan: Set<String> = emptySet()
    ): Boolean {
        manifest.sortByDescending { it.ts }
        manifest.forEachIndexed { index, entry ->
            val actual = File(dir, entry.file).length()
            if (actual != entry.size) manifest[index] = entry.copy(size = actual)
        }
        val referencedFiles = manifest.map { File(dir, it.file).absoluteFile }.toSet()
        val leasedOrphans = CacheFileLeases.inDirectory(dir) - referencedFiles
        var totalBytes = manifest.sumOf { it.size } + leasedOrphans.sumOf { it.length() }
        while (manifest.size + leasedOrphans.size > entryLimit || totalBytes > byteLimit) {
            val index = manifest.indexOfLast {
                it.key != protectedKey && it.key !in protectedPlan && !CacheFileLeases.contains(File(dir, it.file))
            }
            if (index < 0) break
            val removed = manifest.removeAt(index)
            totalBytes -= removed.size
        }
        writeManifest(manifest)
        val referenced = manifest.map { it.file }.toHashSet()
        dir.listFiles()?.filter {
            it.name != MANIFEST && it.name != SOURCE_STATE && it.name !in referenced && !CacheFileLeases.contains(it)
        }?.forEach { it.delete() }
        return manifest.size + leasedOrphans.size <= entryLimit && totalBytes <= byteLimit
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
                    sourceStateGeneration++
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
            manifest.filter { it.file !in validFiles }.forEach {
                val file = File(dir, it.file)
                if (!CacheFileLeases.contains(file)) file.delete()
            }
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
