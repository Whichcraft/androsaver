package com.androsaver

import android.graphics.Bitmap
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.androsaver.source.ImageItem
import com.bumptech.glide.Glide
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

class ImageIntegrationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun actualCacheManifestProducesAModelThatGlideCanDecodeOffline() = runBlocking {
        val directory = File(context.cacheDir, "integration-cache").apply { mkdirs() }
        val file = File(directory, "cached.img")
        try {
            val bitmap = Bitmap.createBitmap(12, 8, Bitmap.Config.ARGB_8888)
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            File(directory, "manifest.json").writeText(Gson().toJson(listOf(
                ImageCache.Entry("offline-image", file.name, "test", System.currentTimeMillis(), file.length())
            )))
            val cache = ImageCache(directory, { it.length() > 0 }, { error("Offline test must not make HTTP requests") })
            val item = cache.getCachedItems().single()
            assertTrue(item.url.startsWith("file:/"))
            val model = ImageModels.forItem(item)
            assertEquals(file, model)
            val request = Glide.with(context).asBitmap().load(model).submit()
            try {
                val decoded = request.get(15, TimeUnit.SECONDS)
                assertEquals(12, decoded.width)
                assertEquals(8, decoded.height)
            } finally { Glide.with(context).clear(request) }
        } finally { directory.deleteRecursively() }
    }

    @Test fun staticImportAcceptsJavaFileUrisAndPreservesSelectionAfterInvalidImport() = runBlocking(Dispatchers.IO) {
        val prefs = Prefs.get(context)
        val oldPath = prefs.getString(Prefs.STATIC_IMAGE_LOCAL_PATH, null)
        val oldName = prefs.getString(Prefs.STATIC_IMAGE_DISPLAY_NAME, null)
        val file = File(context.cacheDir, "static-integration.png")
        try {
            val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            val selected = StaticImageStore.copyItem(context, ImageItem(file.toURI().toString()))!!
            assertTrue(File(selected).isFile)
            assertEquals(selected, prefs.getString(Prefs.STATIC_IMAGE_LOCAL_PATH, null))
            file.writeText("not an image")
            assertNull(StaticImageStore.copyItem(context, ImageItem(file.toURI().toString())))
            assertEquals(selected, prefs.getString(Prefs.STATIC_IMAGE_LOCAL_PATH, null))
            assertEquals(Uri.parse("content://media/external/images/media/1"),
                ImageModels.forItem(ImageItem("content://media/external/images/media/1")))
        } finally {
            prefs.edit().putString(Prefs.STATIC_IMAGE_LOCAL_PATH, oldPath)
                .putString(Prefs.STATIC_IMAGE_DISPLAY_NAME, oldName).commit()
            file.delete()
        }
    }
}
