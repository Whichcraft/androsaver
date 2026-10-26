package com.androsaver

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import com.androsaver.source.ImageItem
import com.androsaver.source.ImageSourceResult
import com.bumptech.glide.Glide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.net.ServerSocket
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

class CacheIsolationIntegrationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun png(color: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        return ByteArrayOutputStream().use { output -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, output); bitmap.recycle(); output.toByteArray() }
    }
    @Test fun sameRemoteUrlCannotReuseAnotherAccountsPixelsOrLeaveAHiddenDiskCopy() = runBlocking(Dispatchers.IO) {
        val server = ServerSocket(0, 8, java.net.InetAddress.getByName("127.0.0.1"))
        val bytes = AtomicReference(png(Color.RED))
        val count = AtomicInteger()
        val serving = thread(isDaemon = true) {
            while (!server.isClosed) try {
                server.accept().use { socket ->
                    socket.soTimeout = 5000
                    val input = socket.getInputStream().bufferedReader()
                    while (!input.readLine().isNullOrEmpty()) { }
                    count.incrementAndGet()
                    val body = bytes.get()
                    socket.getOutputStream().apply {
                        write("HTTP/1.1 200 OK\r\nContent-Type: image/png\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
                        write(body); flush()
                    }
                }
            } catch (_: Exception) { if (server.isClosed) break }
        }
        try {
            Glide.get(context).clearDiskCache()
            val base = ImageItem("http://127.0.0.1:${server.localPort}/same.png", stableId = "photo", sourceScope = "account-one", contentVersion = "same")
            fun load(item: ImageItem): Int {
                val target = Glide.with(context).asBitmap().load(ImageModels.forItem(item))
                    .signature(ImageCachePolicy.signature(item)).submit(8, 8)
                return try { target.get(15, TimeUnit.SECONDS).getPixel(0, 0) } finally { Glide.with(context).clear(target) }
            }
            assertEquals(Color.RED, load(base))
            bytes.set(png(Color.BLUE))
            assertEquals(Color.BLUE, load(base.copy(sourceScope = "account-two")))
            assertEquals(2, count.get())
            assertTrue(Glide.getPhotoCacheDir(context)?.listFiles().orEmpty().isEmpty())
        } finally { server.close(); serving.join(5000) }
    }
    @Test fun nextcloudCarriesTheServerRevisionIntoItsImageIdentity() {
        val xml = """<d:multistatus xmlns:d="DAV:"><d:response><d:href>/photo.jpg</d:href><d:propstat><d:prop><d:getcontenttype>image/jpeg</d:getcontenttype><d:getetag>revision-two</d:getetag></d:prop></d:propstat></d:response></d:multistatus>"""
        val base = okhttp3.HttpUrl.Builder().scheme("https").host("example.invalid").build()
        val item = com.androsaver.source.NextcloudSource(context).parseResponse(xml, base, "nonfunctional-placeholder").single()
        assertEquals("revision-two", item.contentVersion)
    }

    @Test fun healthyRefreshTimesRemainVisibleAndFailurePreservesTheLastSuccess() {
        val prefs = context.getSharedPreferences("source_health", android.content.Context.MODE_PRIVATE)
        val name = "Integration source"
        try {
            SourceHealthStore.record(context, name, ImageSourceResult.Success(emptyList()))
            val success = prefs.getLong("$name.success", 0)
            assertTrue(success > 0)
            assertTrue(SourceHealthStore.report(context).contains(context.getString(R.string.source_last_success, java.util.Date(success).toString())))
            SourceHealthStore.record(context, name, ImageSourceResult.Failure(ImageSourceResult.FailureKind.TLS_EXPIRED,
                listOf("java.security.cert.CertificateExpiredException")))
            assertEquals(success, prefs.getLong("$name.success", 0))
            assertTrue(SourceHealthStore.report(context).contains("TLS_EXPIRED"))
        } finally {
            prefs.edit().apply { prefs.all.keys.filter { it.startsWith("$name.") }.forEach { remove(it) } }.commit()
        }
    }
}
