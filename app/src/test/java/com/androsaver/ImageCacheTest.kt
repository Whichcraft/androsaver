package com.androsaver

import com.androsaver.source.ImageItem
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.buffer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.io.File
import java.net.URI

class ImageCacheTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun readingEmptyManifestDoesNotDeleteAnActiveDownload() = runBlocking {
        exercisePendingDownload(pendingIndex = 0)
    }

    @Test fun completedImagesAreCommittedBeforeTheNextDownloadFinishes() = runBlocking {
        exercisePendingDownload(pendingIndex = 1)
    }

    @Test fun cachedBatchValidatesEachExistingImageOnlyOnce() = runBlocking {
        val validations = AtomicInteger()
        val requests = AtomicInteger()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests.incrementAndGet()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body("image".toResponseBody()).build()
        }.build()
        try {
            val cache = ImageCache(temporary.newFolder(), {
                validations.incrementAndGet()
                it.length() > 0
            }, { client })
            val items = (0..4).map { ImageItem("https://example.invalid/$it", stableId = "image-$it") }
            assertEquals(5, cache.saveImages(items, "test").downloaded)
            validations.set(0)
            val saved = cache.saveImages(items, "test")
            assertEquals(ImageCache.SaveResult(cached = 5), saved)
            assertEquals(5, validations.get())
            assertEquals(5, requests.get())
        } finally {
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }

    @Test fun allFailedDownloadsAreReportedAsRetryable() = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(503).message("Unavailable").body("".toResponseBody()).build()
        }.build()
        try {
            val cache = ImageCache(temporary.newFolder(), { true }, { client })
            val saved = cache.saveImages(listOf(ImageItem("https://example.invalid/image")), "test")
            assertEquals(ImageCache.SaveResult(failed = 1), saved)
            assertTrue(saved.needsRetry)
            assertTrue(cache.getCachedItems().isEmpty())
        } finally {
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }

    @Test fun revisionsAndAgeRefreshCachedPhotosWithoutDestroyingTheWorkingCopyOnFailure() = runBlocking {
        var clock = 1000L
        val status = AtomicInteger(200)
        val revision = AtomicInteger(1)
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(status.get()).message("test").body("revision-${revision.get()}".toResponseBody()).build()
        }.build()
        try {
            val cache = ImageCache(temporary.newFolder(), { it.length() > 0 }, { client }, now = { clock })
            val first = ImageItem("https://example.invalid/photo", stableId = "photo", contentVersion = "v1")
            assertEquals(1, cache.saveImages(listOf(first), "test").downloaded)
            val old = cache.getCachedItems().single()
            val changed = first.copy(contentVersion = "v2")
            revision.set(2)
            assertEquals(1, cache.saveImages(listOf(changed), "test").downloaded)
            val updated = cache.getCachedItems().single()
            assertNotEquals(old.url, updated.url)
            assertEquals("revision-2", File(URI(updated.url)).readText())
            assertEquals(1, cache.saveImages(listOf(changed), "test").cached)
            clock += 25L * 60 * 60 * 1000
            status.set(404)
            val failed = cache.saveImages(listOf(changed), "test")
            assertEquals(1, failed.failed)
            assertFalse(failed.needsRetry)
            assertEquals(updated.url, cache.getCachedItems().single().url)
            assertEquals("revision-2", File(URI(updated.url)).readText())
            status.set(200)
            revision.set(3)
            assertEquals(1, cache.saveImages(listOf(changed), "test").downloaded)
            val fresh = cache.getCachedItems().single()
            assertEquals("revision-3", File(URI(fresh.url)).readText())
        } finally {
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }

    private suspend fun kotlinx.coroutines.CoroutineScope.exercisePendingDownload(pendingIndex: Int) {
        val reading = CountDownLatch(1)
        val release = CountDownLatch(1)
        val directory = temporary.newFolder()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val index = chain.request().url.pathSegments.last().toInt()
            var firstRead = true
            val source = object : ForwardingSource(Buffer().writeUtf8("image bytes")) {
                override fun read(sink: Buffer, byteCount: Long): Long {
                    if (index == pendingIndex && firstRead) {
                        firstRead = false
                        reading.countDown()
                        check(release.await(10, TimeUnit.SECONDS)) { "Test download was not released" }
                    }
                    return super.read(sink, byteCount)
                }
            }.buffer()
            val body = object : ResponseBody() {
                override fun contentType(): MediaType? = null
                override fun contentLength() = 11L
                override fun source() = source
            }
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body(body).build()
        }.build()
        val cache = ImageCache(directory, { it.isFile && it.length() > 0 }, { client })
        val items = (0..pendingIndex).map { index ->
            ImageItem(url = "https://example.invalid/$index", name = "test", stableId = "image-$index")
        }
        // UNDISPATCHED enters saveImages before we block waiting for the IO thread.
        val saving = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            cache.saveImages(items, "test")
        }
        try {
            assertTrue(reading.await(10, TimeUnit.SECONDS))
            val part = directory.listFiles()!!.single { it.extension == "part" }
            assertEquals(pendingIndex, cache.getCachedItems().size)
            assertTrue("Reader deleted a live download", part.exists())
        } finally {
            release.countDown()
            try {
                saving.await()
            } finally {
                client.dispatcher.executorService.shutdown()
                client.connectionPool.evictAll()
            }
        }
        assertEquals(items.size, cache.getCachedItems().size)
        assertFalse(directory.listFiles()!!.any { it.extension == "part" })
    }
}
