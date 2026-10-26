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


    @Test fun completeEmptyAndDeletionAuthoritySurvivesRecreationAndRejectsLateWriters() = runBlocking {
        val requests = AtomicInteger()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests.incrementAndGet()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body("image".toResponseBody()).build()
        }.build()
        try {
            val dir = temporary.newFolder()
            val scope = "source:dropbox:fixture"
            val a = ImageItem("https://example.invalid/a", stableId = "a", sourceScope = scope)
            val b = a.copy(url = "https://example.invalid/b", stableId = "b")
            val cache = ImageCache(dir, { it.length() > 0 }, { client })
            cache.recordListing(scope, com.androsaver.source.ImageSourceResult.Success(listOf(a, b)))
            cache.saveImages(listOf(a, b), "test")
            cache.acquireSnapshot(setOf(scope)).use { active ->
                cache.recordListing(scope, com.androsaver.source.ImageSourceResult.Empty)
                assertTrue(active.items.all { File(URI(it.url)).isFile })
                val recreated = ImageCache(dir, { it.length() > 0 }, { client })
                recreated.acquireSnapshot(setOf(scope)).use { assertTrue(it.items.isEmpty()) }
                assertEquals(0, recreated.saveImages(listOf(a, b), "late").downloaded)
                assertEquals(2, requests.get())
                recreated.recordListing(scope, com.androsaver.source.ImageSourceResult.Success(listOf(b)))
                recreated.acquireSnapshot(setOf(scope)).use { assertEquals(listOf("b"), it.items.map { item -> item.stableId }) }
            }
        } finally { client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll() }
    }

    @Test fun actualSizeReservationUsesFullCapacityAndNetworkFailurePreservesThePlaylist() = runBlocking {
        val status = AtomicInteger(200)
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(status.get()).message("fixture")
                .body("x".toResponseBody()).build()
        }.build()
        try {
            val cache = ImageCache(temporary.newFolder(), { it.length() > 0 }, { client }, maxBytes = 150, maxImageBytes = 50)
            val items = (1..150).map { ImageItem("https://example.invalid/$it", stableId = "image-$it") }
            assertEquals(150, cache.saveImages(items, "test", items.map { it.stableId }.toSet()).downloaded)
            status.set(503)
            val before = cache.getCachedItems().map { it.url }.toSet()
            assertEquals(1, cache.saveImages(listOf(ImageItem("https://example.invalid/new")), "test").failed)
            assertEquals(before, cache.getCachedItems().map { it.url }.toSet())
            status.set(200)
            assertEquals(0, cache.saveImages(listOf(ImageItem("https://example.invalid/new")), "test", items.map { it.stableId }.toSet()).downloaded)
            assertEquals(before, cache.getCachedItems().map { it.url }.toSet())
        } finally { client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll() }
    }

    @Test fun failedManifestCommitRetiresTheStageWithoutDeletingTheWorkingPhoto() = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body("image".toResponseBody()).build()
        }.build()
        try {
            val dir = temporary.newFolder()
            val cache = ImageCache(dir, { it.length() > 0 }, { client })
            cache.saveImages(listOf(ImageItem("https://example.invalid/old")), "test")
            val old = cache.getCachedItems().single().url
            assertTrue(File(dir, "manifest.json.tmp").mkdir())
            assertEquals(1, cache.saveImages(listOf(ImageItem("https://example.invalid/new")), "test").failed)
            assertEquals(old, cache.getCachedItems().single().url)
            assertEquals(1, dir.listFiles()!!.count { it.extension == "img" })
        } finally { client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll() }
    }

    @Test fun downloadBytesAreBoundedAcrossTheWholePlan() = runBlocking {
        val requests = AtomicInteger()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests.incrementAndGet()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("12345".toResponseBody()).build()
        }.build()
        try {
            val cache = ImageCache(temporary.newFolder(), { it.length() > 0 }, { client })
            val images = (1..5).map { ImageItem("https://example.invalid/$it") }
            assertEquals(2, cache.saveImages(images, "test", networkBudget = 10).downloaded)
            assertEquals(2, requests.get())
        } finally { client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll() }
    }

    @Test fun fallbackNeverIncludesDisabledPreviousAccountOrLegacyImages() = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("image".toResponseBody()).build()
        }.build()
        try {
            val cache = ImageCache(temporary.newFolder(), { it.length() > 0 }, { client })
            val one = com.androsaver.source.SourceScope.create("dropbox", "account-one", emptyList())
            val two = com.androsaver.source.SourceScope.create("dropbox", "account-two", emptyList())
            val base = ImageItem("https://example.invalid/same-path", stableId = "dropbox:/photo.jpg")
            cache.saveImages(listOf(com.androsaver.source.SourceScope.bind(base, one),
                com.androsaver.source.SourceScope.bind(base, two), base.copy(stableId = "legacy")), "test")
            cache.acquireSnapshot(setOf(two)).use { snapshot ->
                assertEquals(1, snapshot.items.size)
                assertEquals(two, snapshot.items.single().sourceScope)
            }
            cache.acquireSnapshot(emptySet()).use { assertTrue(it.items.isEmpty()) }
            assertTrue(cache.clearSource("dropbox"))
            cache.acquireSnapshot(setOf(one, two)).use { assertTrue(it.items.isEmpty()) }
        } finally { client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll() }
    }

    @Test fun oneFairPlanDoesNotRedownloadSourcesThatPreviouslyEvictedEachOther() = runBlocking {
        val requests = AtomicInteger()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests.incrementAndGet()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("image".toResponseBody()).build()
        }.build()
        try {
            val cache = ImageCache(temporary.newFolder(), { it.length() > 0 }, { client })
            val a = (1..250).map { ImageItem("https://a.invalid/$it", stableId = "a-$it", sourceScope = "a") }
            val b = (1..250).map { ImageItem("https://b.invalid/$it", stableId = "b-$it", sourceScope = "b") }
            assertEquals(200, cache.warm(listOf(a, b)).downloaded)
            assertEquals(0, cache.warm(listOf(b.reversed(), a.reversed())).downloaded)
            assertEquals(200, requests.get())
        } finally { client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll() }
    }

    @Test fun leasedOfflinePlaylistsSurviveAnotherCacheInstanceWritingAndEvicting() = runBlocking {
        val requests = AtomicInteger()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests.incrementAndGet()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body("image".toResponseBody()).build()
        }.build()
        try {
            val directory = temporary.newFolder()
            val first = ImageCache(directory, { it.length() > 0 }, { client })
            val writer = ImageCache(directory, { it.length() > 0 }, { client })
            first.saveImages((1..200).map { ImageItem("https://example.invalid/$it", stableId = "image-$it") }, "test")
            val snapshot = first.acquireSnapshot()
            try {
                val before = requests.get()
                assertEquals(0, writer.saveImages(listOf(ImageItem("https://example.invalid/new")), "test").downloaded)
                assertEquals(before, requests.get())
                assertTrue(snapshot.items.all { File(URI(it.url)).isFile })
            } finally { snapshot.close(); snapshot.close() }
            assertEquals(1, writer.saveImages(listOf(ImageItem("https://example.invalid/new")), "test").downloaded)
        } finally { client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll() }
    }

    @Test fun aLeasedOldGenerationSurvivesAnImageRevisionReplacement() = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body("image".toResponseBody()).build()
        }.build()
        try {
            val cache = ImageCache(temporary.newFolder(), { it.length() > 0 }, { client })
            val image = ImageItem("https://example.invalid/photo", stableId = "photo", contentVersion = "one")
            cache.saveImages(listOf(image), "test")
            val snapshot = cache.acquireSnapshot()
            val oldFile = File(URI(snapshot.items.single().url))
            try {
                cache.saveImages(listOf(image.copy(contentVersion = "two")), "test")
                assertTrue(oldFile.isFile)
                assertNotEquals(oldFile, File(URI(cache.getCachedItems().single().url)))
            } finally { snapshot.close() }
            cache.saveImages(listOf(ImageItem("https://example.invalid/another")), "test")
            assertFalse(oldFile.exists())
        } finally { client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll() }
    }

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

    @Test fun authoritativeEmptyDoesNotDeleteAnActivePartAndPreventsItsLateCommit() = runBlocking {
        exercisePendingDownload(pendingIndex = 0, authoritativeEmpty = true)
    }

    private suspend fun kotlinx.coroutines.CoroutineScope.exercisePendingDownload(pendingIndex: Int, authoritativeEmpty: Boolean = false) {
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
            ImageItem(url = "https://example.invalid/$index", name = "test", stableId = "image-$index", sourceScope = if (authoritativeEmpty) "fixture" else null)
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
            if (authoritativeEmpty) {
                cache.recordListing("fixture", com.androsaver.source.ImageSourceResult.Empty)
                cache.recordListing("another-provider", com.androsaver.source.ImageSourceResult.Empty, setOf("fixture", "another-provider"))
                assertTrue("Listing deleted a live download", part.exists())
            }
        } finally {
            release.countDown()
            try {
                saving.await()
            } finally {
                client.dispatcher.executorService.shutdown()
                client.connectionPool.evictAll()
            }
        }
        assertEquals(if (authoritativeEmpty) 0 else items.size, cache.getCachedItems().size)
        assertFalse(directory.listFiles()!!.any { it.extension == "part" })
    }
}
