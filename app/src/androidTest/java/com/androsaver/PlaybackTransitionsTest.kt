package com.androsaver

import android.graphics.Bitmap
import android.view.KeyEvent
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.androsaver.databinding.DreamLayoutBinding
import com.androsaver.source.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class PlaybackTransitionsTest {
    @Test fun blankModePassesBothMediaKeyActionsThroughTheRealEngine() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = Prefs.get(context)
        val mode = prefs.getString(Prefs.SCREENSAVER_MODE, null)
        val schedule = prefs.getBoolean(Prefs.SCHEDULE_ENABLED, false)
        try {
            prefs.edit().putString(Prefs.SCREENSAVER_MODE, Prefs.MODE_BLANK).putBoolean(Prefs.SCHEDULE_ENABLED, false).commit()
            ActivityScenario.launch(PreviewActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    for (key in listOf(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_PREVIOUS)) {
                        assertFalse(activity.engine.handleKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, key)))
                        assertFalse(activity.engine.handleKeyEvent(KeyEvent(KeyEvent.ACTION_UP, key)))
                    }
                }
            }
        } finally { prefs.edit().putString(Prefs.SCREENSAVER_MODE, mode).putBoolean(Prefs.SCHEDULE_ENABLED, schedule).commit() }
    }

    @Test fun successfulEmptyRefreshClearsTheVisiblePhotoAndPendingRequestsWithoutReportingACrash() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val prefs = Prefs.get(context)
        val originalMode = prefs.getString(Prefs.SCREENSAVER_MODE, null)
        val originalSchedule = prefs.getBoolean(Prefs.SCHEDULE_ENABLED, false)
        val first = File(context.cacheDir, "transition-one.png")
        val second = File(context.cacheDir, "transition-two.png")
        val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        var engine: ScreensaverEngine? = null
        var binding: DreamLayoutBinding? = null
        var empty = false
        val source = object : ImageSource {
            override val name = "Fixture"
            override val cacheScope = "fixture"
            override fun isConfigured() = true
            override suspend fun getImageUrls() = if (empty) emptyList() else listOf(
                ImageItem(first.toURI().toString(), stableId = "one"), ImageItem(second.toURI().toString(), stableId = "two"))
        }
        try {
            for ((file, color) in listOf(first to android.graphics.Color.BLUE, second to android.graphics.Color.RED)) {
                val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(color)
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
            prefs.edit().putString(Prefs.SCREENSAVER_MODE, Prefs.MODE_BLANK).putBoolean(Prefs.SCHEDULE_ENABLED, false).commit()
            ActivityScenario.launch(PreviewActivity::class.java).use { scenario ->
                try {
                    scenario.onActivity { activity ->
                        val views = DreamLayoutBinding.inflate(activity.layoutInflater)
                        binding = views
                        activity.setContentView(views.root)
                        prefs.edit().putString(Prefs.SCREENSAVER_MODE, Prefs.MODE_SLIDESHOW).commit()
                        engine = ScreensaverEngine(activity, views, scope, sourceProvider = { listOf(source) },
                            eligibleScopesProvider = { emptySet() }, onRequestFinish = {})
                        engine!!.start(prefs)
                    }
                    val deadline = System.nanoTime() + 15_000_000_000L
                    var ready = false
                    while (!ready && System.nanoTime() < deadline) {
                        scenario.onActivity { ready = binding!!.imageView1.drawable != null || binding!!.imageView2.drawable != null }
                        if (!ready) Thread.sleep(50)
                    }
                    assertTrue("No initial photo displayed", ready)
                    runBlocking(Dispatchers.Main) {
                        engine!!.handleKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
                        empty = true
                        engine!!.refreshSourcesNow()
                    }
                    Thread.sleep(300)
                    scenario.onActivity {
                        assertNull(binding!!.imageView1.drawable)
                        assertNull(binding!!.imageView2.drawable)
                        assertEquals(View.GONE, binding!!.devErrorOverlay.visibility)
                        assertEquals(context.getString(R.string.no_images_found), binding!!.statusText.text.toString())
                    }
                } finally { scenario.onActivity { engine?.stop() }; scope.cancel() }
            }
        } finally {
            scope.cancel()
            prefs.edit().putString(Prefs.SCREENSAVER_MODE, originalMode).putBoolean(Prefs.SCHEDULE_ENABLED, originalSchedule).commit()
            first.delete(); second.delete()
        }
    }
}
