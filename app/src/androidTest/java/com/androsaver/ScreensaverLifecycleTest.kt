package com.androsaver

import android.graphics.Bitmap
import android.widget.ImageView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Real Activity/engine starts, stop/restart and GL teardown on the GitHub emulator. */
class ScreensaverLifecycleTest {
    @Test fun everyModeSurvivesPreviewStopRestartAndRecreation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = Prefs.get(context)
        val sourceFlags = listOf(Prefs.ENABLE_GOOGLE_DRIVE, Prefs.ENABLE_ONEDRIVE, Prefs.ENABLE_DROPBOX,
            Prefs.ENABLE_IMMICH, Prefs.ENABLE_NEXTCLOUD, Prefs.ENABLE_SYNOLOGY, Prefs.ENABLE_LOCAL_STORAGE)
        val original = prefs.all.toMap()
        val image = File(context.cacheDir, "lifecycle-image.png")
        val cacheDirectory = File(context.cacheDir, "image_cache").apply { mkdirs() }
        val cacheImage = File(cacheDirectory, "lifecycle-fixture.img")
        val manifest = File(cacheDirectory, "manifest.json")
        val originalManifest = manifest.takeIf { it.isFile }?.readBytes()
        try {
            val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.BLUE)
            image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            image.copyTo(cacheImage, overwrite = true)
            manifest.writeText(com.google.gson.Gson().toJson(listOf(ImageCache.Entry(
                "lifecycle-offline-image", cacheImage.name, "test", System.currentTimeMillis(), cacheImage.length()
            ))))
            runBlocking(Dispatchers.IO) { StaticImageStore.copyItem(context,
                com.androsaver.source.ImageItem(image.toURI().toString())) }
            prefs.edit().apply {
                sourceFlags.forEach { putBoolean(it, false) }
                putBoolean(Prefs.SCHEDULE_ENABLED, false)
                putBoolean(Prefs.WEATHER_ENABLED, false)
                putBoolean(Prefs.SHOW_CLOCK, false)
            }.commit()
            for (mode in listOf(Prefs.MODE_STATIC, Prefs.MODE_SLIDESHOW, Prefs.MODE_BLANK, Prefs.MODE_VISUALIZER)) {
                prefs.edit().putString(Prefs.SCREENSAVER_MODE, mode).commit()
                ActivityScenario.launch(PreviewActivity::class.java).use { scenario ->
                    scenario.onActivity { assertNotNull(it.findViewById<ImageView>(R.id.imageView1)) }
                    awaitReady(scenario, mode)
                    scenario.moveToState(Lifecycle.State.CREATED)
                    scenario.moveToState(Lifecycle.State.RESUMED)
                    awaitReady(scenario, mode)
                    scenario.recreate()
                    awaitReady(scenario, mode)
                    if (mode == Prefs.MODE_SLIDESHOW) scenario.onActivity { activity ->
                        assertTrue(activity.findViewById<android.widget.TextView>(R.id.sourceHealth)
                            .text.contains(context.getString(R.string.source_health_cache)))
                    }
                }
                assertFalse(File(context.noBackupFilesDir, "screensaver-postmortem.txt.active").exists())
            }
        } finally {
            prefs.edit().clear().apply {
                original.forEach { (key, value) -> when (value) {
                    is String -> putString(key, value)
                    is Boolean -> putBoolean(key, value)
                    is Int -> putInt(key, value)
                    is Long -> putLong(key, value)
                    is Float -> putFloat(key, value)
                    is Set<*> -> putStringSet(key, value.filterIsInstance<String>().toSet())
                } }
            }.commit()
            image.delete()
            cacheImage.delete()
            if (originalManifest == null) manifest.delete() else manifest.writeBytes(originalManifest)
        }
    }
    private fun awaitReady(scenario: ActivityScenario<PreviewActivity>, mode: String) {
        val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(20)
        var ready = mode == Prefs.MODE_BLANK
        while (!ready && System.nanoTime() < deadline) {
            scenario.onActivity { activity ->
                assertFalse(activity.isFinishing)
                val error = activity.findViewById<android.widget.TextView>(R.id.devErrorOverlay)
                assertTrue("Unexpected diagnostic in $mode: ${error.text}", error.visibility != android.view.View.VISIBLE)
                ready = if (mode == Prefs.MODE_VISUALIZER)
                    activity.findViewById<android.widget.TextView>(R.id.devPerformanceOverlay).text.isNotEmpty()
                else listOf(R.id.imageView1, R.id.imageView2).any { id ->
                    val imageView = activity.findViewById<ImageView>(id)
                    imageView.drawable != null && imageView.alpha > 0.01f && imageView.isShown
                }
            }
            if (!ready) Thread.sleep(100)
        }
        assertTrue("Mode did not produce an image or GL sample: $mode", ready)
    }

}
