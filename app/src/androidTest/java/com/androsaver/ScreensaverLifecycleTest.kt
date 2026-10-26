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
        try {
            val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
            image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
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
                    scenario.moveToState(Lifecycle.State.CREATED)
                    scenario.moveToState(Lifecycle.State.RESUMED)
                    scenario.recreate()
                    val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(20)
                    var ready = mode == Prefs.MODE_BLANK
                    while (!ready && System.nanoTime() < deadline) {
                        scenario.onActivity { activity ->
                            assertFalse(activity.isFinishing)
                            val error = activity.findViewById<android.widget.TextView>(R.id.devErrorOverlay)
                            assertTrue("Unexpected diagnostic in $mode: ${error.text}", error.visibility != android.view.View.VISIBLE)
                            ready = if (mode == Prefs.MODE_VISUALIZER)
                                activity.findViewById<android.widget.TextView>(R.id.devPerformanceOverlay).text.isNotEmpty()
                            else activity.findViewById<ImageView>(R.id.imageView1).drawable != null ||
                                activity.findViewById<ImageView>(R.id.imageView2).drawable != null
                        }
                        if (!ready) Thread.sleep(100)
                    }
                    assertTrue("Mode did not produce an image or GL sample: $mode", ready)
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
        }
    }
}
