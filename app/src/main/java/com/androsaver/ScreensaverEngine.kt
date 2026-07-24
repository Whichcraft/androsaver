package com.androsaver

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.SharedPreferences
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import androidx.preference.PreferenceManager
import com.androsaver.databinding.DreamLayoutBinding
import com.androsaver.source.DefaultImagesSource
import com.androsaver.source.DropboxSource
import com.androsaver.source.GoogleDriveSource
import com.androsaver.source.ImageItem
import com.androsaver.source.ImageSource
import com.androsaver.source.ImmichSource
import com.androsaver.source.LocalStorageSource
import com.androsaver.source.NextcloudSource
import com.androsaver.source.OneDriveSource
import com.androsaver.source.SynologySource
import com.androsaver.visualizer.VisualizerView
import androidx.exifinterface.media.ExifInterface
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.request.transition.Transition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeoutException

class ScreensaverEngine(
    private val context: Context,
    private val binding: DreamLayoutBinding,
    private val scope: CoroutineScope,
    private val onRequestFinish: () -> Unit
) {
    companion object {
        private const val TAG = "ScreensaverEngine"
        // Refresh image URLs 5 minutes before the Synology DSM session expires (~30 min).
        private const val IMAGE_REFRESH_INTERVAL_MS = 25 * 60 * 1000L
        private const val MAX_SLIDE_DURATION_MS = 30 * 60 * 1000L
        private const val MAX_TRANSITION_DURATION_MS = 5_000L
        // Genre → preferred visualizer mode names (ordered by priority).
        private val GENRE_MODES = mapOf(
            "electronic" to listOf("FlowField", "Fireworks", "Plasma", "Tunnel"),
            "rock"       to listOf("Branches", "TriFlux", "Nova"),
            "classical"  to listOf("Yantra", "Lissajous", "Spiral")
        )
        private val RANDOM_EFFECTS = listOf("crossfade","fade_black","slide_left","slide_right","zoom_in","zoom_out")
        val INTENSITY_STEPS = floatArrayOf(0.0f, 0.5f, 1.0f, 1.5f, 2.0f)
        // [startScale, endScale, startTxFrac, startTyFrac, endTxFrac, endTyFrac]
        // All presets end at (0,0) so the image is always centered at rest.
        // Keep the motion subtle so fitCenter still shows nearly all of the photo.
        private val KB_PRESETS = listOf(
            floatArrayOf(1.02f, 1.04f, -0.005f, -0.004f, 0f, 0f),  // zoom in,  upper-left  → center
            floatArrayOf(1.02f, 1.04f,  0.005f,  0.004f, 0f, 0f),  // zoom in,  lower-right → center
            floatArrayOf(1.04f, 1.02f,  0.005f, -0.004f, 0f, 0f),  // zoom out, upper-right → center
            floatArrayOf(1.04f, 1.02f, -0.005f,  0.004f, 0f, 0f)   // zoom out, lower-left  → center
        )
    }

    private val handler = Handler(Looper.getMainLooper())
    private val imageItems = mutableListOf<ImageItem>()
    private var currentIndex = 0
    private var activeView = 1
    private var slideshowRunnable: Runnable? = null
    private var imageRefreshRunnable: Runnable? = null
    private var retryRunnable: Runnable? = null
    private var consecutiveLoadFailures = 0
    private var displayedIndex = -1
    private var transitionSequence = 0L
    private var slideshowSession = 0L
    private var devTransitionErrorVisible = false
    private var activeTransitionAnimator: ValueAnimator? = null
    private var displayedItem: ImageItem? = null
    private val imageSourceNames = mutableMapOf<String, String>()
    private var blankMode = false
    private var visualizerView: VisualizerView? = null
    private var vizCycleRunnable: Runnable? = null
    private var vizCycleMs: Long = 0L
    private var genreDetectRunnable: Runnable? = null
    private var lastDetectedGenre = ""
    private var clockRunnable: Runnable? = null
    private var weatherRunnable: Runnable? = null
    private val kenBurnsAnimators = mutableMapOf<ImageView, ValueAnimator>()
    private val imageTargets = mutableMapOf<ImageView, Target<Drawable>>()
    private val imageCache by lazy { ImageCache(context) }
    private val weatherFetcher by lazy { WeatherFetcher(context) }
    private val timeFmt  = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateFmt  = SimpleDateFormat("EEE, d MMM", Locale.getDefault())

    fun start(prefs: SharedPreferences) {
        binding.imageView1.alpha = 1f
        binding.imageView2.alpha = 0f

        if (!checkSchedule(prefs)) {
            binding.root.setBackgroundColor(0xFF000000.toInt())
            handler.postDelayed({ onRequestFinish() }, 500)
            return
        }

        val mode = prefs.getString(Prefs.SCREENSAVER_MODE, Prefs.MODE_SLIDESHOW)
        blankMode = mode == Prefs.MODE_BLANK
        when (mode) {
            Prefs.MODE_VISUALIZER -> startVisualizerMode(prefs)
            Prefs.MODE_BLANK -> startBlankMode()
            else -> startSlideshowMode(prefs)
        }

        if (mode != Prefs.MODE_BLANK) {
            if (prefs.getBoolean(Prefs.SHOW_CLOCK, false)) startClock()
            if (prefs.getBoolean(Prefs.WEATHER_ENABLED, false)) startWeather(prefs)
        }
    }

    /** Pause the visualizer audio + GL when the host goes to background (e.g. Home press). */
    fun pauseVisualizer() { visualizerView?.stopVisualizer() }

    /** Resume the visualizer audio + GL when the host comes back to foreground. */
    fun resumeVisualizer() { visualizerView?.startVisualizer() }

    fun stop() {
        stopSlideshow()
        stopImageRefresh()
        stopVisualizerMode()
        stopClock()
        stopWeather()
        cancelAllKenBurns()
    }

    fun handleKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
            event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
            event.keyCode == KeyEvent.KEYCODE_VOLUME_MUTE) {
            return false
        }
        if (event.action != KeyEvent.ACTION_DOWN) return true
        if (blankMode && event.keyCode in setOf(
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_MEDIA_PLAY,
                KeyEvent.KEYCODE_MEDIA_PAUSE,
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                KeyEvent.KEYCODE_MEDIA_NEXT,
                KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
                KeyEvent.KEYCODE_MEDIA_REWIND
            )) {
            // Let Android TV's audio/media stack handle remote controls while the
            // screensaver remains a blank black page.
            return false
        }
        val vv = visualizerView
        if (vv != null) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_RIGHT -> { vv.nextMode(); resetIntensity(); resetVizCycleTimer() }
                KeyEvent.KEYCODE_DPAD_LEFT  -> { vv.previousMode(); resetIntensity(); resetVizCycleTimer() }
                KeyEvent.KEYCODE_DPAD_UP    -> adjustIntensity(+1)
                KeyEvent.KEYCODE_DPAD_DOWN  -> adjustIntensity(-1)
                else -> { onRequestFinish(); return true }
            }
        } else if (imageItems.isNotEmpty()) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_RIGHT -> slideshowSkip(+1)
                KeyEvent.KEYCODE_DPAD_LEFT  -> slideshowSkip(-1)
                else -> onRequestFinish()
            }
        } else {
            onRequestFinish()
        }
        return true
    }

    private fun slideshowSkip(delta: Int) {
        try {
            if (imageItems.isEmpty()) return
            val baseIndex = displayedIndex.takeIf { it in imageItems.indices } ?: currentIndex
            currentIndex = ((baseIndex + delta) % imageItems.size + imageItems.size) % imageItems.size
            slideshowRunnable?.let { handler.removeCallbacks(it) }
            slideshowRunnable = null
            settleTransitionBeforeManualSkip()
            showNextImage()
        } catch (t: Throwable) {
            reportTransitionFailure(
                "manual slideshow navigation",
                t,
                attemptedItem = imageItems.getOrNull(currentIndex)
            )
            recoverAfterSlideshowFailure()
        }
    }

    private fun settleTransitionBeforeManualSkip() {
        transitionSequence++
        cancelActiveTransition()
        val active = if (activeView == 1) binding.imageView1 else binding.imageView2
        val inactive = if (activeView == 1) binding.imageView2 else binding.imageView1

        active.animate().setListener(null).cancel()
        inactive.animate().setListener(null).cancel()
        cancelKenBurns(active)
        cancelKenBurns(inactive)

        resetIncomingViewState(active)
        resetViewState(inactive, clearDrawable = false)
        active.bringToFront()
        bringSlideshowOverlaysToFront()
    }

    // ── Schedule ──────────────────────────────────────────────────────────────

    private fun checkSchedule(prefs: SharedPreferences): Boolean {
        if (!prefs.getBoolean(Prefs.SCHEDULE_ENABLED, false)) return true
        val start = prefs.getString(Prefs.SCHEDULE_START_HR, "8")?.toIntOrNull() ?: 8
        val end   = prefs.getString(Prefs.SCHEDULE_END_HR, "22")?.toIntOrNull() ?: 22
        val hour  = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return if (start <= end) hour in start until end
        else hour >= start || hour < end
    }

    // ── Clock ─────────────────────────────────────────────────────────────────

    private fun startClock() {
        binding.clockOverlay.visibility = View.VISIBLE
        clockRunnable = object : Runnable {
            override fun run() {
                val now = Date()
                binding.clockOverlay.text = "${timeFmt.format(now)}\n${dateFmt.format(now)}"
                val ms = System.currentTimeMillis()
                val delayToNextMin = 60_000L - (ms % 60_000L)
                handler.postDelayed(this, delayToNextMin)
            }
        }
        (clockRunnable as Runnable).run()
    }

    private fun stopClock() {
        clockRunnable?.let { handler.removeCallbacks(it) }
        clockRunnable = null
        binding.clockOverlay.visibility = View.GONE
    }

    // ── Weather ───────────────────────────────────────────────────────────────

    private fun startWeather(prefs: SharedPreferences) {
        val city   = prefs.getString(Prefs.WEATHER_CITY, "") ?: ""
        val apiKey = prefs.getString(Prefs.WEATHER_API_KEY, "") ?: ""
        if (city.isBlank() || apiKey.isBlank()) return
        refreshWeather(city, apiKey)
        weatherRunnable = object : Runnable {
            override fun run() {
                refreshWeather(city, apiKey)
                handler.postDelayed(this, 30 * 60 * 1000L)
            }
        }
        handler.postDelayed(weatherRunnable!!, 30 * 60 * 1000L)
    }

    private fun refreshWeather(city: String, apiKey: String) {
        scope.launch {
            val data = weatherFetcher.getWeather(city, apiKey)
            if (data != null) {
                binding.weatherTemp.text = "%.0f°C".format(data.tempC)
                binding.weatherDesc.text = data.description
                binding.weatherWidget.visibility = View.VISIBLE
            } else {
                binding.weatherWidget.visibility = View.GONE
            }
        }
    }

    private fun stopWeather() {
        weatherRunnable?.let { handler.removeCallbacks(it) }
        weatherRunnable = null
        binding.weatherWidget.visibility = View.GONE
    }

    // ── Visualizer mode ───────────────────────────────────────────────────────

    private fun startVisualizerMode(prefs: SharedPreferences) {
        val modePref = prefs.getString(Prefs.VISUALIZER_MODE, "auto") ?: "auto"
        val vv = VisualizerView(context)
        visualizerView = vv

        val storedModes = prefs.getStringSet(Prefs.VIZ_ENABLED_MODES, null)
        if (!storedModes.isNullOrEmpty()) {
            // Auto-add any modes not present in the stored set (new effects added in a later version).
            // This ensures that after an update, freshly-added effects are navigable without requiring
            // the user to manually visit the Active Effects setting.
            val allNames = vv.renderer.modeNames.toSet()
            val newModes = allNames - storedModes
            val enabledModes: Set<String> = if (newModes.isEmpty()) storedModes
            else (storedModes + newModes).also {
                prefs.edit().putStringSet(Prefs.VIZ_ENABLED_MODES, it).apply()
            }
            vv.enabledModeNames = enabledModes
        }

        when (modePref) {
            "auto"   -> { /* start at index 0; nextMode() cycles enabled modes */ }
            "random" -> vv.randomMode()
            else     -> { /* off: stay on first enabled mode */ }
        }

        vv.renderer.beatGain = prefs.getString(Prefs.VISUALIZER_INTENSITY, "0.5")?.toFloatOrNull() ?: 0.5f
        val genre = prefs.getString(Prefs.AUDIO_GENRE, "any") ?: "any"
        if (genre == "auto") {
            vv.audio.applyGenreHint("any")
            lastDetectedGenre = ""
            genreDetectRunnable = object : Runnable {
                override fun run() {
                    val detected = vv.audio.detectGenre()
                    if (detected != null) {
                        vv.audio.applyGenreHint(detected)
                        if (detected != lastDetectedGenre && modePref == "auto") {
                            lastDetectedGenre = detected
                            val candidates = GENRE_MODES[detected]
                            if (!candidates.isNullOrEmpty()) {
                                val enabledNames = vv.enabledModeNames.ifEmpty {
                                    vv.renderer.modeNames.toSet()
                                }
                                val target = candidates.firstOrNull { it in enabledNames }
                                    ?: candidates.random()
                                vv.setMode(target)
                                resetVizCycleTimer()
                            }
                        }
                    }
                    handler.postDelayed(this, 30_000L)
                }
            }
            handler.postDelayed(genreDetectRunnable!!, 30_000L)
        } else {
            vv.audio.applyGenreHint(genre)
        }

        binding.visualizerContainer.addView(vv)
        binding.visualizerContainer.visibility = View.VISIBLE
        binding.imageView1.visibility = View.GONE
        binding.imageView2.visibility = View.GONE
        vv.startVisualizer()

        if (modePref == "auto" || modePref == "random") {
            vizCycleMs = prefs.getString(Prefs.VIZ_CYCLE_INTERVAL, "120000")?.toLongOrNull() ?: 120_000L
            if (vizCycleMs > 0L) {
                vizCycleRunnable = object : Runnable {
                    override fun run() {
                        if (modePref == "random") vv.randomMode() else vv.nextMode()
                        handler.postDelayed(this, vizCycleMs)
                    }
                }
                handler.postDelayed(vizCycleRunnable!!, vizCycleMs)
            }
        }
    }

    private fun resetVizCycleTimer() {
        val runnable = vizCycleRunnable ?: return
        handler.removeCallbacks(runnable)
        handler.postDelayed(runnable, vizCycleMs)
    }

    private fun stopVisualizerMode() {
        vizCycleRunnable?.let { handler.removeCallbacks(it) }
        vizCycleRunnable = null
        genreDetectRunnable?.let { handler.removeCallbacks(it) }
        genreDetectRunnable = null
        visualizerView?.let { vv ->
            vv.stopVisualizer()
            binding.visualizerContainer.removeView(vv)
        }
        visualizerView = null
        binding.visualizerContainer.visibility = View.GONE
    }

    // ── Slideshow mode ────────────────────────────────────────────────────────

    private fun startBlankMode() {
        binding.root.setBackgroundColor(0xFF000000.toInt())
        binding.imageView1.visibility = View.GONE
        binding.imageView2.visibility = View.GONE
        binding.visualizerContainer.visibility = View.GONE
        binding.statusText.visibility = View.GONE
        binding.devErrorOverlay.visibility = View.GONE
    }

    private fun startSlideshowMode(prefs: SharedPreferences) {
        binding.visualizerContainer.visibility = View.GONE
        binding.imageView1.visibility = View.VISIBLE
        binding.imageView2.visibility = View.VISIBLE
        devTransitionErrorVisible = false
        binding.devErrorOverlay.text = ""
        binding.devErrorOverlay.visibility = View.GONE
        slideshowSession++
        resetSlideshowViews()
        loadImages(prefs, slideshowSession)
    }

    private fun loadImages(prefs: SharedPreferences, session: Long) {
        if (!devTransitionErrorVisible) {
            binding.statusText.visibility = View.VISIBLE
            binding.statusText.text = context.getString(R.string.loading_images)
        }

        val sources = getConfiguredSources(prefs)
        if (sources.isEmpty()) {
            tryFallbackCache(session)
            return
        }

        scope.launch {
            try {
                val items = mutableListOf<ImageItem>()
                val deferreds = sources.map { src ->
                    async {
                        val urls = try {
                            val urls = withTimeoutOrNull(60_000L) { src.getImageUrls() }
                            if (urls == null) {
                                reportTransitionFailure(
                                    "image source timeout",
                                    TimeoutException("${src.name} did not respond within 60 seconds"),
                                    sourceName = src.name
                                )
                            }
                            urls
                        } catch (t: Throwable) {
                            if (t is CancellationException) throw t
                            reportTransitionFailure("image source load", t, sourceName = src.name)
                            null
                        }
                        src.name to urls
                    }
                }
                val results = deferreds.awaitAll()
                if (session != slideshowSession) return@launch
                imageSourceNames.clear()
                for ((sourceName, res) in results) {
                    if (res != null) {
                        res.forEach { imageSourceNames[imageKey(it)] = sourceName }
                        items.addAll(res)
                    }
                }

                if (items.isEmpty()) {
                    tryFallbackCache(session)
                } else {
                    scope.launch(Dispatchers.IO) {
                        try {
                            imageCache.saveImages(items, "mixed")
                        } catch (t: Throwable) {
                            if (t is CancellationException) throw t
                            reportTransitionFailure("image cache write", t)
                        }
                    }
                    if (session != slideshowSession) return@launch
                    if (!devTransitionErrorVisible) binding.statusText.visibility = View.GONE
                    imageItems.clear()
                    imageItems.addAll(items.shuffled())
                    startSlideshow(prefs, session)
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                reportTransitionFailure("slideshow loading pipeline", t)
                tryFallbackCache(session)
            }
        }
    }

    private fun tryFallbackCache(session: Long) {
        try {
            if (session != slideshowSession) return
            val cached = imageCache.getCachedItems()
            if (cached.isNotEmpty()) {
                if (!devTransitionErrorVisible) {
                    binding.statusText.text = context.getString(R.string.cache_fallback_notice)
                }
                handler.postDelayed({
                    if (session == slideshowSession && !devTransitionErrorVisible) {
                        binding.statusText.visibility = View.GONE
                    }
                }, 3000)
                imageItems.clear()
                imageItems.addAll(cached.shuffled())
                imageSourceNames.clear()
                cached.forEach { imageSourceNames[imageKey(it)] = "fallback image cache" }
                startSlideshow(com.androsaver.Prefs.get(context), session)
            } else if (!devTransitionErrorVisible) {
                binding.statusText.text = context.getString(R.string.no_images_found)
            }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            reportTransitionFailure("fallback image cache", t, sourceName = "fallback image cache")
        }
    }

    private fun getConfiguredSources(prefs: SharedPreferences): List<ImageSource> {
        val sources = mutableListOf<ImageSource>()
        fun addSource(name: String, enabled: Boolean, factory: () -> ImageSource) {
            if (!enabled) return
            try {
                sources.add(factory())
            } catch (t: Throwable) {
                reportTransitionFailure("image source initialization", t, sourceName = name)
            }
        }
        addSource("Google Drive", prefs.getBoolean(Prefs.ENABLE_GOOGLE_DRIVE, false)) {
            GoogleDriveSource(context)
        }
        addSource("OneDrive", prefs.getBoolean(Prefs.ENABLE_ONEDRIVE, false)) {
            OneDriveSource(context)
        }
        addSource("Dropbox", prefs.getBoolean(Prefs.ENABLE_DROPBOX, false)) {
            DropboxSource(context)
        }
        addSource("Immich", prefs.getBoolean(Prefs.ENABLE_IMMICH, false)) {
            ImmichSource(context)
        }
        addSource("Nextcloud", prefs.getBoolean(Prefs.ENABLE_NEXTCLOUD, false)) {
            NextcloudSource(context)
        }
        addSource("Synology", prefs.getBoolean(Prefs.ENABLE_SYNOLOGY, false)) {
            SynologySource(context)
        }
        addSource("Local storage", prefs.getBoolean(Prefs.ENABLE_LOCAL_STORAGE, false)) {
            LocalStorageSource(context)
        }
        if (sources.isEmpty()) {
            addSource("Bundled default images", enabled = true) { DefaultImagesSource(context) }
        }
        return sources
    }

    private fun startSlideshow(prefs: SharedPreferences, session: Long) {
        if (session != slideshowSession) return
        stopImageRefresh()
        activeView = 1
        currentIndex = 0
        displayedIndex = -1
        displayedItem = null
        transitionSequence++
        resetSlideshowViews()
        showNextImage()
        scheduleImageRefresh(prefs, session)
    }

    private fun stopSlideshow() {
        slideshowSession++
        slideshowRunnable?.let { handler.removeCallbacks(it) }
        slideshowRunnable = null
        retryRunnable?.let { handler.removeCallbacks(it) }
        retryRunnable = null
        transitionSequence++
        displayedIndex = -1
        displayedItem = null
        activeView = 1
        currentIndex = 0
        resetSlideshowViews()
    }

    // ── Periodic image refresh ────────────────────────────────────────────────
    // Re-fetches all sources every 25 minutes so Synology SIDs (which expire at ~30 min)
    // and other session-scoped credentials stay fresh without any blackout.

    private fun scheduleImageRefresh(prefs: SharedPreferences, session: Long) {
        imageRefreshRunnable = object : Runnable {
            override fun run() {
                if (session != slideshowSession) return
                scope.launch {
                    try {
                        if (session != slideshowSession) return@launch
                        val sources = getConfiguredSources(prefs)
                        if (sources.isEmpty()) return@launch
                        val fresh = mutableListOf<ImageItem>()
                        val deferreds = sources.map { src ->
                            async {
                                val urls = try {
                                    val urls = withTimeoutOrNull(60_000L) { src.getImageUrls() }
                                    if (urls == null) {
                                        reportTransitionFailure(
                                            "image source refresh timeout",
                                            TimeoutException("${src.name} did not respond within 60 seconds"),
                                            sourceName = src.name
                                        )
                                    }
                                    urls
                                } catch (t: Throwable) {
                                    if (t is CancellationException) throw t
                                    reportTransitionFailure(
                                        "image source refresh",
                                        t,
                                        sourceName = src.name
                                    )
                                    null
                                }
                                src.name to urls
                            }
                        }
                        val results = deferreds.awaitAll()
                        if (session != slideshowSession) return@launch
                        val freshSources = mutableMapOf<String, String>()
                        for ((sourceName, res) in results) {
                            if (res != null) {
                                res.forEach { freshSources[imageKey(it)] = sourceName }
                                fresh.addAll(res)
                            }
                        }

                        if (fresh.isNotEmpty()) {
                            if (session != slideshowSession) return@launch
                            val currentDisplayed = displayedItem
                            imageItems.clear()
                            imageItems.addAll(fresh.shuffled())
                            imageSourceNames.clear()
                            imageSourceNames.putAll(freshSources)
                            displayedIndex = currentDisplayed?.let { current ->
                                imageItems.indexOfFirst {
                                    it.url == current.url && it.orientation == current.orientation
                                }
                            } ?: -1
                            displayedItem = displayedIndex.takeIf { it >= 0 }?.let { imageItems[it] }
                            currentIndex = when {
                                imageItems.isEmpty() -> 0
                                displayedIndex >= 0 -> (displayedIndex + 1) % imageItems.size
                                else -> 0
                            }
                            scope.launch(Dispatchers.IO) {
                                try {
                                    imageCache.saveImages(fresh, "mixed")
                                } catch (t: Throwable) {
                                    if (t is CancellationException) throw t
                                    reportTransitionFailure("refreshed image cache write", t)
                                }
                            }
                            if (BuildConfig.DEBUG_LOGGING) {
                                Log.d(TAG, "Image list refreshed: ${fresh.size} items")
                            }
                        }
                    } catch (t: Throwable) {
                        if (t is CancellationException) throw t
                        reportTransitionFailure("image refresh pipeline", t)
                    }
                }
                if (session == slideshowSession) {
                    handler.postDelayed(this, IMAGE_REFRESH_INTERVAL_MS)
                }
            }
        }
        handler.postDelayed(imageRefreshRunnable!!, IMAGE_REFRESH_INTERVAL_MS)
    }

    private fun stopImageRefresh() {
        imageRefreshRunnable?.let { handler.removeCallbacks(it) }
        imageRefreshRunnable = null
    }

    private fun showNextImage() {
        try {
            showNextImageGuarded()
        } catch (t: Throwable) {
            reportTransitionFailure("show next image", t, attemptedItem = imageItems.getOrNull(currentIndex))
            recoverAfterSlideshowFailure()
        }
    }

    private fun showNextImageGuarded() {
        retryRunnable?.let { handler.removeCallbacks(it) }
        retryRunnable = null
        if (imageItems.isEmpty()) return
        val incoming = if (activeView == 1) binding.imageView2 else binding.imageView1
        val outgoing  = if (activeView == 1) binding.imageView1 else binding.imageView2
        val itemIndex = currentIndex.takeIf { it in imageItems.indices } ?: 0
        currentIndex = itemIndex
        val item = imageItems[itemIndex]
        val requestSequence = ++transitionSequence
        clearImageTarget(incoming)

        val glideUrl: Any = if (item.url.startsWith("content://") || item.url.startsWith("file://")) {
            android.net.Uri.parse(item.url)
        } else if (item.headers.isNotEmpty()) {
            val b = LazyHeaders.Builder()
            item.headers.forEach { (k, v) -> b.addHeader(k, v) }
            GlideUrl(item.url, b.build())
        } else {
            GlideUrl(item.url)
        }

        val glideRequest = Glide.with(context).load(glideUrl)
            .downsample(com.bumptech.glide.load.resource.bitmap.DownsampleStrategy.AT_MOST)
        // Apply explicit EXIF rotation for local/cached images where orientation is pre-read.
        // Remote HTTP images (orientation == 0) are handled by Glide's Downsampler.
        val request = if (item.orientation != 0 && item.orientation != ExifInterface.ORIENTATION_NORMAL)
            glideRequest.apply(RequestOptions().transform(ExifRotationTransformation(item.orientation)))
        else
            glideRequest

        var glideFailureReported = false
        val target = object : CustomTarget<Drawable>(
                binding.imageView1.width.takeIf { it > 0 } ?: context.resources.displayMetrics.widthPixels,
                binding.imageView1.height.takeIf { it > 0 } ?: context.resources.displayMetrics.heightPixels
            ) {
                override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                    try {
                        if (requestSequence != transitionSequence) return
                        if (imageTargets[incoming] !== this) return
                        if (imageItems.isEmpty()) {
                            reportTransitionFailure(
                                "image ready",
                                IllegalStateException("image list became empty"),
                                attemptedItem = item
                            )
                            clearImageTarget(incoming)
                            return
                        }
                        val resolvedItemIndex = findImageItemIndex(item)
                        if (resolvedItemIndex < 0) {
                            // The periodic refresh removed this item while it was loading.
                            clearImageTarget(incoming)
                            handler.post {
                                if (requestSequence == transitionSequence) showNextImage()
                            }
                            return
                        }
                        consecutiveLoadFailures = 0
                        cancelKenBurns(incoming)
                        incoming.setImageDrawable(resource)
                        activeView = if (activeView == 1) 2 else 1
                        displayedIndex = resolvedItemIndex
                        displayedItem = imageItems[resolvedItemIndex]
                        currentIndex = (resolvedItemIndex + 1) % imageItems.size
                        val prefs = com.androsaver.Prefs.get(context)
                        val effect = prefs.getString(Prefs.TRANSITION_EFFECT, "crossfade") ?: "crossfade"
                        val resolvedEffect = applyTransitionWithFallback(
                            incoming, outgoing, effect, prefs, requestSequence
                        )
                        scheduleNextSlide(prefs, resolvedEffect)
                    } catch (t: Throwable) {
                        reportTransitionFailure("image ready callback", t, attemptedItem = item)
                        recoverAfterSlideshowFailure(incoming, outgoing)
                    }
                }
                override fun onLoadCleared(placeholder: Drawable?) {
                    try {
                        if (imageTargets[incoming] !== this) return
                        imageTargets.remove(incoming)
                        cancelKenBurns(incoming)
                        incoming.setImageDrawable(null)
                    } catch (t: Throwable) {
                        reportTransitionFailure("image clear callback", t, attemptedItem = item)
                    }
                }
                override fun onLoadFailed(errorDrawable: Drawable?) {
                    try {
                        if (requestSequence != transitionSequence) return
                        if (imageTargets[incoming] !== this) return
                        imageTargets.remove(incoming)
                        if (!glideFailureReported) {
                            reportTransitionFailure(
                                "image load failure",
                                GlideException("Glide reported failure without an attached cause"),
                                attemptedItem = item
                            )
                        }
                        if (imageItems.isEmpty()) return
                        consecutiveLoadFailures++
                        val failedIndex = findImageItemIndex(item)
                        currentIndex = if (failedIndex >= 0) {
                            (failedIndex + 1) % imageItems.size
                        } else {
                            currentIndex.coerceIn(0, imageItems.lastIndex)
                        }
                        if (consecutiveLoadFailures < imageItems.size) {
                            val r = Runnable { showNextImage() }
                            retryRunnable = r
                            handler.postDelayed(r, 300L)
                        } else {
                            consecutiveLoadFailures = 0
                        }
                    } catch (t: Throwable) {
                        reportTransitionFailure("image failure callback", t, attemptedItem = item)
                    }
                }
            }
        val diagnosedRequest = request.listener(object : RequestListener<Drawable> {
            override fun onLoadFailed(
                e: GlideException?,
                model: Any?,
                target: Target<Drawable>,
                isFirstResource: Boolean
            ): Boolean {
                glideFailureReported = true
                reportTransitionFailure(
                    "image fetch/decode",
                    e ?: GlideException("Unknown Glide fetch/decode failure"),
                    attemptedItem = item
                )
                return false
            }

            override fun onResourceReady(
                resource: Drawable,
                model: Any,
                target: Target<Drawable>?,
                dataSource: DataSource,
                isFirstResource: Boolean
            ): Boolean = false
        })
        imageTargets[incoming] = target
        diagnosedRequest.into(target)
    }

    private fun findImageItemIndex(item: ImageItem): Int =
        imageItems.indexOfFirst { it.url == item.url && it.orientation == item.orientation }

    private fun imageKey(item: ImageItem): String = "${item.orientation}\u0000${item.url}"

    private fun scheduleNextSlide(prefs: SharedPreferences, effect: String) {
        val durationMs = prefs.getString(Prefs.SLIDE_DURATION, "10000")
            ?.toLongOrNull()?.coerceIn(1L, MAX_SLIDE_DURATION_MS) ?: 10_000L
        val delayMs = durationMs + when (effect) {
            "crossfade", "fade_black", "slide_left", "slide_right", "zoom_in", "zoom_out" -> transitionMs
            else -> 0L
        }
        slideshowRunnable?.let { handler.removeCallbacks(it) }
        slideshowRunnable = Runnable { showNextImage() }
        handler.postDelayed(slideshowRunnable!!, delayMs)
    }

    private fun clearImageTarget(view: ImageView) {
        val target = imageTargets[view] ?: return
        try {
            Glide.with(context).clear(target)
        } catch (e: Throwable) {
            reportTransitionFailure("image target clear", e)
        }
        if (imageTargets[view] === target) imageTargets.remove(view)
    }

    private fun resetSlideshowViews() {
        cancelActiveTransition()
        clearImageTarget(binding.imageView1)
        clearImageTarget(binding.imageView2)
        cancelAllKenBurns()
        resetViewState(binding.imageView1, clearDrawable = true)
        resetViewState(binding.imageView2, clearDrawable = true)
        binding.imageView1.alpha = 1f
        binding.imageView2.alpha = 0f
    }

    private fun cancelKenBurns(view: ImageView) {
        val animator = kenBurnsAnimators.remove(view) ?: return
        try {
            animator.removeAllUpdateListeners()
            animator.cancel()
        } catch (t: Throwable) {
            reportTransitionFailure("Ken Burns cancellation", t)
        }
    }

    private fun cancelAllKenBurns() {
        val animators = kenBurnsAnimators.values.toList()
        kenBurnsAnimators.clear()
        animators.forEach { animator ->
            try {
                animator.removeAllUpdateListeners()
                animator.cancel()
            } catch (t: Throwable) {
                reportTransitionFailure("Ken Burns cancellation", t)
            }
        }
    }

    // ── Ken Burns ─────────────────────────────────────────────────────────────

    private fun startKenBurns(view: ImageView, prefs: SharedPreferences) {
        // Keep portrait photos fully visible; scaling them would bring back edge cropping.
        if (isPortraitImage(view)) {
            view.scaleX = 1f
            view.scaleY = 1f
            view.translationX = 0f
            view.translationY = 0f
            return
        }
        val durationMs = prefs.getString(Prefs.SLIDE_DURATION, "10000")
            ?.toLongOrNull()?.coerceIn(1L, MAX_SLIDE_DURATION_MS) ?: 10_000L
        val preset = KB_PRESETS.random()
        val startScale = preset[0]; val endScale = preset[1]
        val startTxFrac = preset[2]; val startTyFrac = preset[3]
        val endTxFrac   = preset[4]; val endTyFrac   = preset[5]
        view.scaleX = startScale; view.scaleY = startScale
        view.translationX = 0f; view.translationY = 0f
        val anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            interpolator = LinearInterpolator()
            addUpdateListener { va ->
                if (kenBurnsAnimators[view] !== va) return@addUpdateListener
                try {
                    val t = va.animatedFraction
                    val w = view.width.toFloat().takeIf { it > 0 } ?: 1280f
                    val h = view.height.toFloat().takeIf { it > 0 } ?: 720f
                    view.scaleX = startScale + t * (endScale - startScale)
                    view.scaleY = view.scaleX
                    view.translationX = (startTxFrac + t * (endTxFrac - startTxFrac)) * w
                    view.translationY = (startTyFrac + t * (endTyFrac - startTyFrac)) * h
                } catch (t: Throwable) {
                    cancelKenBurns(view)
                    reportTransitionFailure("Ken Burns update", t)
                }
            }
        }
        kenBurnsAnimators[view] = anim
        anim.start()
    }

    // ── Intensity ─────────────────────────────────────────────────────────────

    private fun resetIntensity() {
        val vv = visualizerView ?: return
        val prefs = com.androsaver.Prefs.get(context)
        vv.renderer.beatGain = prefs.getString(Prefs.VISUALIZER_INTENSITY, "0.5")?.toFloatOrNull() ?: 0.5f
    }

    fun adjustIntensity(delta: Int) {
        val vv = visualizerView ?: return
        val current = vv.renderer.beatGain
        val idx = INTENSITY_STEPS.indexOfFirst { it >= current - 0.01f }.takeIf { it >= 0 } ?: 2
        val newIdx = (idx + delta).coerceIn(0, INTENSITY_STEPS.lastIndex)
        val newGain = INTENSITY_STEPS[newIdx]
        vv.renderer.beatGain = newGain
        com.androsaver.Prefs.get(context).edit()
            .putString(Prefs.VISUALIZER_INTENSITY, newGain.toString()).apply()
    }

    // ── Transitions ───────────────────────────────────────────────────────────

    private val transitionMs: Long get() =
        com.androsaver.Prefs.get(context)
            .getString(Prefs.TRANSITION_SPEED, "2000")?.toLongOrNull()
            ?.coerceIn(1L, MAX_TRANSITION_DURATION_MS) ?: 2000L

    private fun isPortraitImage(view: ImageView): Boolean {
        val drawable = view.drawable ?: return false
        return drawable.intrinsicHeight > drawable.intrinsicWidth
    }

    private fun applyTransition(
        incoming: ImageView,
        outgoing: ImageView,
        effect: String,
        prefs: SharedPreferences,
        sequence: Long
    ): String {
        prepareForTransition(incoming, outgoing)
        incoming.bringToFront()
        bringSlideshowOverlaysToFront()
        val incomingEndListener = incomingTransitionEndListener(incoming, prefs, sequence)
        val durationMs = transitionMs
        val requested = if (effect == "random") RANDOM_EFFECTS.random() else effect
        val normalized = requested.takeIf { it in RANDOM_EFFECTS } ?: "crossfade"
        // Zooming a portrait image crops it into a landscape-looking frame.
        val resolved = if (normalized == "zoom_in" || normalized == "zoom_out") {
            if (isPortraitImage(incoming) || isPortraitImage(outgoing)) "crossfade" else normalized
        } else {
            normalized
        }
        when (resolved) {
            "crossfade"   -> crossfade(incoming, outgoing, incomingEndListener, sequence, durationMs)
            "fade_black"  -> fadeBlack(incoming, outgoing, incomingEndListener, sequence, durationMs)
            "slide_left"  -> slide(incoming, outgoing, true, incomingEndListener, sequence, durationMs)
            "slide_right" -> slide(incoming, outgoing, false, incomingEndListener, sequence, durationMs)
            "zoom_in"     -> zoomIn(incoming, outgoing, incomingEndListener, sequence, durationMs)
            "zoom_out"    -> zoomOut(incoming, outgoing, incomingEndListener, sequence, durationMs)
        }
        return resolved
    }

    private fun applyTransitionWithFallback(
        incoming: ImageView,
        outgoing: ImageView,
        effect: String,
        prefs: SharedPreferences,
        sequence: Long
    ): String {
        try {
            return applyTransition(incoming, outgoing, effect, prefs, sequence)
        } catch (firstFailure: Throwable) {
            if (BuildConfig.DEBUG_LOGGING) {
                reportTransitionFailure(effect, firstFailure)
            }
        }

        return try {
            // Rebuild the animator state before retrying, since the first attempt may
            // have started one side of a two-view transition before failing.
            applyTransition(incoming, outgoing, "crossfade", prefs, sequence)
        } catch (secondFailure: Throwable) {
            if (BuildConfig.DEBUG_LOGGING) {
                reportTransitionFailure("$effect (crossfade fallback)", secondFailure)
            }
            try {
                showImageImmediately(incoming, outgoing, prefs)
            } catch (immediateFailure: Throwable) {
                reportTransitionFailure("$effect (crossfade fallback and immediate display)", immediateFailure)
            }
            "none"
        }
    }

    /**
     * Transition callbacks run later on the main looper, outside the try/catch
     * around animator creation. Keep dev builds alive long enough to show the
     * actual callback failure on the screensaver instead of only getting a
     * generic process crash.
     */
    private fun reportTransitionFailure(
        effect: String,
        throwable: Throwable,
        attemptedItem: ImageItem? = null,
        sourceName: String? = null
    ) {
        if (!BuildConfig.DEBUG_LOGGING) return
        devTransitionErrorVisible = true
        try {
            Log.e(TAG, "Slideshow failure: $effect", throwable)
        } catch (_: Throwable) {
            // On-screen reporting must not depend on logcat being available.
        }

        val details = try {
            val attempted = attemptedItem
            val current = displayedItem
            val resolvedSource = sourceName
                ?: attempted?.let { imageSourceNames[imageKey(it)] }
                ?: current?.let { imageSourceNames[imageKey(it)] }
                ?: "unknown"
            buildString {
                append("ANDROSAVER DEV ERROR — PERSISTENT\n\n")
                append("Stage/effect: ").append(effect).append('\n')
                append("Configured transition: ")
                    .append(com.androsaver.Prefs.get(context)
                        .getString(Prefs.TRANSITION_EFFECT, "crossfade"))
                    .append('\n')
                append("Image source: ").append(resolvedSource).append('\n')
                appendImageDiagnostic("Attempted image", attempted)
                appendImageDiagnostic("Displayed image", current)
                append("Thread: ").append(Thread.currentThread().name).append('\n')
                append("Session/sequence: ").append(slideshowSession).append('/')
                    .append(transitionSequence).append('\n')
                append("Indices: current=").append(currentIndex)
                    .append(", displayed=").append(displayedIndex)
                    .append(", count=").append(imageItems.size).append('\n')
                append("Views: active=").append(activeView)
                    .append(", root=").append(binding.root.width).append('x').append(binding.root.height)
                    .append(", image1=").append(binding.imageView1.width).append('x').append(binding.imageView1.height)
                    .append(", image2=").append(binding.imageView2.width).append('x').append(binding.imageView2.height)
                    .append("\n\n")

                append("Cause chain:\n")
                val seen = HashSet<Throwable>()
                var cause: Throwable? = throwable
                var depth = 0
                while (cause != null && seen.add(cause) && depth < 12) {
                    append(depth).append(": ").append(cause::class.java.name)
                    cause.message?.takeIf { it.isNotBlank() }?.let { append(": ").append(it) }
                    append('\n')
                    cause.suppressed.take(4).forEach {
                        append("   suppressed: ").append(it::class.java.name)
                        it.message?.takeIf(String::isNotBlank)?.let { message -> append(": ").append(message) }
                        append('\n')
                    }
                    cause = cause.cause
                    depth++
                }
                if (throwable is GlideException) {
                    append("\nGlide root causes:\n")
                    throwable.rootCauses.take(12).forEachIndexed { index, root ->
                        append(index).append(": ").append(root::class.java.name)
                        root.message?.takeIf { it.isNotBlank() }?.let { append(": ").append(it) }
                        append('\n')
                    }
                }
                append("\nStack trace:\n")
                append(Log.getStackTraceString(throwable)
                    .lineSequence()
                    .take(40)
                    .joinToString("\n"))
            }
        } catch (diagnosticFailure: Throwable) {
            try {
                "ANDROSAVER DEV ERROR — PERSISTENT\n\n" +
                    "Stage/effect: $effect\nImage source: ${sourceName ?: "unknown"}\n" +
                    "${throwable::class.java.name}: ${throwable.message}\n\n" +
                    "Diagnostic rendering also failed: ${diagnosticFailure::class.java.name}: " +
                    diagnosticFailure.message
            } catch (_: Throwable) {
                "ANDROSAVER DEV ERROR — PERSISTENT\n\nUnable to format failure details."
            }
        }

        val display = Runnable {
            try {
                val prior = binding.devErrorOverlay.text?.toString().orEmpty()
                binding.devErrorOverlay.text = when {
                    prior.isBlank() -> details
                    prior.length + details.length <= 64_000 ->
                        "$prior\n\n──────── ADDITIONAL FAILURE ────────\n\n$details"
                    else ->
                        prior.take(32_000) +
                            "\n\n──────── LATEST FAILURE ────────\n\n" +
                            details.takeLast(32_000)
                }
                binding.devErrorOverlay.alpha = 1f
                binding.devErrorOverlay.translationX = 0f
                binding.devErrorOverlay.translationY = 0f
                binding.devErrorOverlay.visibility = View.VISIBLE
                binding.devErrorOverlay.bringToFront()
                binding.devErrorOverlay.requestLayout()
                binding.devErrorOverlay.invalidate()
                // Re-raise after pending image/clock/weather callbacks have run.
                binding.root.post { binding.devErrorOverlay.bringToFront() }
            } catch (overlayFailure: Throwable) {
                try {
                    Log.e(TAG, "Dedicated dev error overlay failed", overlayFailure)
                } catch (_: Throwable) {
                    // Continue to the independent status-text fallback.
                }
                try {
                    binding.statusText.text = details
                    binding.statusText.setBackgroundColor(0xF0180000.toInt())
                    binding.statusText.visibility = View.VISIBLE
                    binding.statusText.bringToFront()
                } catch (statusFailure: Throwable) {
                    try {
                        Log.e(TAG, "Fallback dev error text failed", statusFailure)
                    } catch (_: Throwable) {
                        // No further UI fallback exists if both bound text views fail.
                    }
                }
            }
        }
        try {
            if (Looper.myLooper() == Looper.getMainLooper()) display.run()
            else handler.postAtFrontOfQueue(display)
        } catch (dispatchFailure: Throwable) {
            try {
                Log.e(TAG, "Could not dispatch dev error overlay", dispatchFailure)
            } catch (_: Throwable) {
                // Reporting must never become the crash.
            }
        }
    }

    private fun StringBuilder.appendImageDiagnostic(label: String, item: ImageItem?) {
        append(label).append(": ")
        if (item == null) {
            append("none\n")
            return
        }
        append(item.name.ifBlank { "(unnamed)" }).append('\n')
        append("  URL: ").append(diagnosticUrl(item.url)).append('\n')
        append("  Orientation: ").append(item.orientation).append('\n')
        append("  Header names: ")
            .append(item.headers.keys.sorted().joinToString().ifBlank { "none" })
            .append('\n')
    }

    private fun diagnosticUrl(url: String): String = try {
        val uri = android.net.Uri.parse(url)
        if (uri.scheme == "http" || uri.scheme == "https") {
            buildString {
                append(uri.scheme).append("://").append(uri.host ?: "(unknown-host)")
                append(uri.encodedPath ?: "")
                val keys = uri.queryParameterNames.sorted()
                if (keys.isNotEmpty()) append("?parameters=").append(keys.joinToString(","))
            }
        } else {
            url
        }
    } catch (_: Throwable) {
        url.substringBefore('?')
    }

    private fun bringSlideshowOverlaysToFront() {
        binding.clockOverlay.bringToFront()
        binding.weatherWidget.bringToFront()
        binding.statusText.bringToFront()
        if (devTransitionErrorVisible) binding.devErrorOverlay.bringToFront()
    }

    private fun prepareForTransition(incoming: ImageView, outgoing: ImageView) {
        cancelActiveTransition()
        incoming.animate().setListener(null).cancel()
        outgoing.animate().setListener(null).cancel()
        cancelKenBurns(outgoing)
        cancelKenBurns(incoming)

        resetIncomingViewState(incoming)
        resetOutgoingViewState(outgoing)
    }

    private fun incomingTransitionEndListener(
        view: ImageView,
        prefs: SharedPreferences,
        sequence: Long
    ) = object : AnimatorListenerAdapter() {
        private var canceled = false
        override fun onAnimationCancel(animation: Animator) {
            canceled = true
        }

        override fun onAnimationEnd(animation: Animator) {
            try {
                if (!canceled && sequence == transitionSequence &&
                    prefs.getBoolean(Prefs.KEN_BURNS_ENABLED, true) && view.drawable != null) {
                    startKenBurns(view, prefs)
                }
            } catch (t: Throwable) {
                reportTransitionFailure("incoming animation end", t)
            }
        }
    }

    private fun crossfade(
        incoming: ImageView,
        outgoing: ImageView,
        incomingEndListener: AnimatorListenerAdapter,
        sequence: Long,
        durationMs: Long
    ) {
        incoming.alpha = 0f
        runTransitionAnimator("crossfade", incoming, outgoing, incomingEndListener, sequence, durationMs) { t ->
            incoming.alpha = t
            outgoing.alpha = 1f - t
        }
    }

    private fun showImageImmediately(incoming: ImageView, outgoing: ImageView, prefs: SharedPreferences) {
        cancelActiveTransition()
        incoming.animate().setListener(null).cancel()
        outgoing.animate().setListener(null).cancel()
        cancelKenBurns(incoming)
        cancelKenBurns(outgoing)
        resetIncomingViewState(incoming)
        resetViewState(outgoing, clearDrawable = true)
        incoming.alpha = 1f
        if (prefs.getBoolean(Prefs.KEN_BURNS_ENABLED, true) && incoming.drawable != null) {
            startKenBurns(incoming, prefs)
        }
    }

    private fun fadeBlack(
        incoming: ImageView,
        outgoing: ImageView,
        incomingEndListener: AnimatorListenerAdapter,
        sequence: Long,
        durationMs: Long
    ) {
        incoming.alpha = 0f
        runTransitionAnimator("fade_black", incoming, outgoing, incomingEndListener, sequence, durationMs) { t ->
            if (t < 0.5f) {
                outgoing.alpha = 1f - t * 2f
                incoming.alpha = 0f
            } else {
                outgoing.alpha = 0f
                incoming.alpha = (t - 0.5f) * 2f
            }
        }
    }

    private fun slide(
        incoming: ImageView,
        outgoing: ImageView,
        fromRight: Boolean,
        incomingEndListener: AnimatorListenerAdapter,
        sequence: Long,
        durationMs: Long
    ) {
        val w = (binding.root.width.takeIf { it > 0 } ?: incoming.width.takeIf { it > 0 }
        ?: context.resources.displayMetrics.widthPixels).toFloat()
        incoming.alpha = 1f; incoming.translationX = if (fromRight) w else -w
        val outgoingEnd = if (fromRight) -w else w
        val incomingStart = -outgoingEnd
        runTransitionAnimator(
            if (fromRight) "slide_left" else "slide_right",
            incoming, outgoing, incomingEndListener, sequence, durationMs
        ) { t ->
            incoming.translationX = incomingStart * (1f - t)
            outgoing.translationX = outgoingEnd * t
        }
    }

    private fun zoomIn(
        incoming: ImageView,
        outgoing: ImageView,
        incomingEndListener: AnimatorListenerAdapter,
        sequence: Long,
        durationMs: Long
    ) {
        incoming.alpha = 0f; incoming.scaleX = 0.85f; incoming.scaleY = 0.85f
        runTransitionAnimator("zoom_in", incoming, outgoing, incomingEndListener, sequence, durationMs) { t ->
            incoming.alpha = t
            incoming.scaleX = 0.85f + 0.15f * t
            incoming.scaleY = incoming.scaleX
            outgoing.alpha = 1f - t
        }
    }

    private fun zoomOut(
        incoming: ImageView,
        outgoing: ImageView,
        incomingEndListener: AnimatorListenerAdapter,
        sequence: Long,
        durationMs: Long
    ) {
        incoming.alpha = 0f
        runTransitionAnimator("zoom_out", incoming, outgoing, incomingEndListener, sequence, durationMs) { t ->
            incoming.alpha = t
            outgoing.alpha = 1f - t
            outgoing.scaleX = 1f + 0.15f * t
            outgoing.scaleY = outgoing.scaleX
        }
    }

    private fun runTransitionAnimator(
        effect: String,
        incoming: ImageView,
        outgoing: ImageView,
        incomingEndListener: AnimatorListenerAdapter,
        sequence: Long,
        durationMs: Long,
        update: (Float) -> Unit
    ) {
        cancelActiveTransition()
        var failed = false
        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.duration = durationMs.coerceAtLeast(1L)
        animator.interpolator = LinearInterpolator()
        animator.addUpdateListener { valueAnimator ->
            if (failed || activeTransitionAnimator !== valueAnimator ||
                sequence != transitionSequence) return@addUpdateListener
            try {
                val fraction = valueAnimator.animatedValue as? Float
                    ?: valueAnimator.animatedFraction
                update(fraction.coerceIn(0f, 1f))
            } catch (t: Throwable) {
                failed = true
                reportTransitionFailure("$effect animation frame", t, attemptedItem = displayedItem)
                valueAnimator.removeAllUpdateListeners()
                valueAnimator.removeAllListeners()
                try {
                    valueAnimator.cancel()
                } catch (cancelFailure: Throwable) {
                    reportTransitionFailure("$effect animation cancellation", cancelFailure,
                        attemptedItem = displayedItem)
                }
                if (activeTransitionAnimator === valueAnimator) activeTransitionAnimator = null
                recoverAfterSlideshowFailure(incoming, outgoing)
            }
        }
        animator.addListener(object : AnimatorListenerAdapter() {
            private var canceled = false

            override fun onAnimationCancel(animation: Animator) {
                canceled = true
            }

            override fun onAnimationEnd(animation: Animator) {
                if (activeTransitionAnimator === animator) activeTransitionAnimator = null
                if (canceled || failed || sequence != transitionSequence) return
                try {
                    resetViewState(outgoing, clearDrawable = true)
                    resetIncomingViewState(incoming)
                    incomingEndListener.onAnimationEnd(animation)
                    bringSlideshowOverlaysToFront()
                } catch (t: Throwable) {
                    reportTransitionFailure("$effect animation completion", t,
                        attemptedItem = displayedItem)
                    recoverAfterSlideshowFailure(incoming, outgoing)
                }
            }
        })
        activeTransitionAnimator = animator
        try {
            animator.start()
        } catch (t: Throwable) {
            activeTransitionAnimator = null
            animator.removeAllUpdateListeners()
            animator.removeAllListeners()
            throw t
        }
    }

    private fun cancelActiveTransition() {
        val animator = activeTransitionAnimator ?: return
        activeTransitionAnimator = null
        try {
            animator.removeAllUpdateListeners()
            animator.removeAllListeners()
            animator.cancel()
        } catch (t: Throwable) {
            reportTransitionFailure("transition cancellation", t, attemptedItem = displayedItem)
        }
    }

    private fun recoverAfterSlideshowFailure(
        incoming: ImageView? = null,
        outgoing: ImageView? = null
    ) {
        try {
            cancelActiveTransition()
            val visible = incoming?.takeIf { it.drawable != null }
                ?: if (activeView == 1) binding.imageView1 else binding.imageView2
            val hidden = outgoing
                ?: if (visible === binding.imageView1) binding.imageView2 else binding.imageView1
            resetIncomingViewState(visible)
            resetViewState(hidden, clearDrawable = false)
            visible.bringToFront()
            bringSlideshowOverlaysToFront()
        } catch (recoveryFailure: Throwable) {
            reportTransitionFailure("slideshow recovery", recoveryFailure,
                attemptedItem = displayedItem)
        }
    }

    private fun resetIncomingViewState(view: ImageView) {
        view.alpha = 1f
        view.translationX = 0f
        view.translationY = 0f
        view.scaleX = 1f
        view.scaleY = 1f
    }

    private fun resetOutgoingViewState(view: ImageView) {
        view.alpha = 1f
        view.translationX = 0f
        view.translationY = 0f
        view.scaleX = 1f
        view.scaleY = 1f
    }

    private fun resetViewState(view: ImageView, clearDrawable: Boolean) {
        view.animate().setListener(null).cancel()
        view.alpha = 0f
        view.translationX = 0f
        view.translationY = 0f
        view.scaleX = 1f
        view.scaleY = 1f
        if (clearDrawable) view.setImageDrawable(null)
    }
}
