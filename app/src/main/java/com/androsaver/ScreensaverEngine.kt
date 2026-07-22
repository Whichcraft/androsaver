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
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.request.transition.Transition
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
    private var displayedItem: ImageItem? = null
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
        kenBurnsAnimators.values.forEach { it.cancel() }
        kenBurnsAnimators.clear()
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
        if (imageItems.isEmpty()) return
        val baseIndex = displayedIndex.takeIf { it in imageItems.indices } ?: currentIndex
        currentIndex = ((baseIndex + delta) % imageItems.size + imageItems.size) % imageItems.size
        slideshowRunnable?.let { handler.removeCallbacks(it) }
        slideshowRunnable = null
        showNextImage()
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
    }

    private fun startSlideshowMode(prefs: SharedPreferences) {
        binding.visualizerContainer.visibility = View.GONE
        binding.imageView1.visibility = View.VISIBLE
        binding.imageView2.visibility = View.VISIBLE
        slideshowSession++
        resetSlideshowViews()
        loadImages(prefs, slideshowSession)
    }

    private fun loadImages(prefs: SharedPreferences, session: Long) {
        binding.statusText.visibility = View.VISIBLE
        binding.statusText.text = context.getString(R.string.loading_images)

        val sources = getConfiguredSources(prefs)
        if (sources.isEmpty()) {
            tryFallbackCache(session)
            return
        }

        scope.launch {
            val items = mutableListOf<ImageItem>()
            val deferreds = sources.map { src ->
                async {
                    try {
                        val urls = withTimeoutOrNull(60_000L) { src.getImageUrls() }
                        if (urls == null && BuildConfig.DEBUG_LOGGING) {
                            Log.w(TAG, "${src.name} timed out")
                        }
                        urls
                    } catch (e: Exception) {
                        if (BuildConfig.DEBUG_LOGGING) Log.e(TAG, "Error from ${src.name}", e)
                        null
                    }
                }
            }
            val results = deferreds.awaitAll()
            if (session != slideshowSession) return@launch
            for (res in results) {
                if (res != null) items.addAll(res)
            }

            if (items.isEmpty()) {
                tryFallbackCache(session)
            } else {
                // Cache in background
                scope.launch(Dispatchers.IO) { imageCache.saveImages(items, "mixed") }
                if (session != slideshowSession) return@launch
                binding.statusText.visibility = View.GONE
                imageItems.clear()
                imageItems.addAll(items.shuffled())
                startSlideshow(prefs, session)
            }
        }
    }

    private fun tryFallbackCache(session: Long) {
        if (session != slideshowSession) return
        val cached = imageCache.getCachedItems()
        if (cached.isNotEmpty()) {
            binding.statusText.text = context.getString(R.string.cache_fallback_notice)
            handler.postDelayed({ binding.statusText.visibility = View.GONE }, 3000)
            imageItems.clear()
            imageItems.addAll(cached.shuffled())
            startSlideshow(com.androsaver.Prefs.get(context), session)
        } else {
            binding.statusText.text = context.getString(R.string.no_images_found)
        }
    }

    private fun getConfiguredSources(prefs: SharedPreferences): List<ImageSource> = buildList {
        if (prefs.getBoolean(Prefs.ENABLE_GOOGLE_DRIVE, false)) add(GoogleDriveSource(context))
        if (prefs.getBoolean(Prefs.ENABLE_ONEDRIVE, false)) add(OneDriveSource(context))
        if (prefs.getBoolean(Prefs.ENABLE_DROPBOX, false)) add(DropboxSource(context))
        if (prefs.getBoolean(Prefs.ENABLE_IMMICH, false)) add(ImmichSource(context))
        if (prefs.getBoolean(Prefs.ENABLE_NEXTCLOUD, false)) add(NextcloudSource(context))
        if (prefs.getBoolean(Prefs.ENABLE_SYNOLOGY, false)) add(SynologySource(context))
        if (prefs.getBoolean(Prefs.ENABLE_LOCAL_STORAGE, false)) add(LocalStorageSource(context))
        // Bundled fallback — used automatically when nothing else is configured
        if (isEmpty()) add(DefaultImagesSource(context))
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
        clearImageTarget(binding.imageView1)
        clearImageTarget(binding.imageView2)
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
                    if (session != slideshowSession) return@launch
                    val sources = getConfiguredSources(prefs)
                    if (sources.isEmpty()) return@launch
                    val fresh = mutableListOf<ImageItem>()
                    val deferreds = sources.map { src ->
                        async {
                            try {
                                val urls = withTimeoutOrNull(60_000L) { src.getImageUrls() }
                                if (urls == null && BuildConfig.DEBUG_LOGGING) {
                                    Log.w(TAG, "Refresh: ${src.name} timed out")
                                }
                                urls
                            } catch (e: Exception) {
                                if (BuildConfig.DEBUG_LOGGING) Log.e(TAG, "Refresh error from ${src.name}", e)
                                null
                            }
                        }
                    }
                    val results = deferreds.awaitAll()
                    if (session != slideshowSession) return@launch
                    for (res in results) {
                        if (res != null) fresh.addAll(res)
                    }

                    if (fresh.isNotEmpty()) {
                        if (session != slideshowSession) return@launch
                        val currentDisplayed = displayedItem
                        imageItems.clear()
                        imageItems.addAll(fresh.shuffled())
                        displayedIndex = currentDisplayed?.let { current ->
                            imageItems.indexOfFirst { it.url == current.url && it.orientation == current.orientation }
                        } ?: -1
                        displayedItem = displayedIndex.takeIf { it >= 0 }?.let { imageItems[it] }
                        currentIndex = when {
                            imageItems.isEmpty() -> 0
                            displayedIndex >= 0 -> (displayedIndex + 1) % imageItems.size
                            else -> 0
                        }
                        scope.launch(Dispatchers.IO) { imageCache.saveImages(fresh, "mixed") }
                        if (BuildConfig.DEBUG_LOGGING) Log.d(TAG, "Image list refreshed: ${fresh.size} items")
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
        retryRunnable?.let { handler.removeCallbacks(it) }
        retryRunnable = null
        if (imageItems.isEmpty()) return
        val incoming = if (activeView == 1) binding.imageView2 else binding.imageView1
        val outgoing  = if (activeView == 1) binding.imageView1 else binding.imageView2
        val itemIndex = currentIndex
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

        val target = object : CustomTarget<Drawable>(
                binding.imageView1.width.takeIf { it > 0 } ?: context.resources.displayMetrics.widthPixels,
                binding.imageView1.height.takeIf { it > 0 } ?: context.resources.displayMetrics.heightPixels
            ) {
                override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                    if (requestSequence != transitionSequence) return
                    if (imageTargets[incoming] === this) imageTargets.remove(incoming)
                    consecutiveLoadFailures = 0
                    kenBurnsAnimators[incoming]?.cancel()
                    incoming.setImageDrawable(resource)
                    activeView = if (activeView == 1) 2 else 1
                    displayedIndex = itemIndex
                    displayedItem = item
                    if (imageItems.isEmpty() || itemIndex !in imageItems.indices) return
                    currentIndex = (itemIndex + 1) % imageItems.size
                    val prefs = com.androsaver.Prefs.get(context)
                    val effect = prefs.getString(Prefs.TRANSITION_EFFECT, "crossfade") ?: "crossfade"
                    val resolvedEffect = applyTransitionWithFallback(
                        incoming, outgoing, effect, prefs, requestSequence
                    )
                    scheduleNextSlide(prefs, resolvedEffect)
                }
                override fun onLoadCleared(placeholder: Drawable?) {
                    kenBurnsAnimators[incoming]?.cancel()
                    incoming.setImageDrawable(null)
                    if (imageTargets[incoming] === this) imageTargets.remove(incoming)
                }
                override fun onLoadFailed(errorDrawable: Drawable?) {
                    if (requestSequence != transitionSequence) return
                    if (imageTargets[incoming] === this) imageTargets.remove(incoming)
                    if (BuildConfig.DEBUG_LOGGING) Log.w(TAG, "Failed: ${item.url}")
                    consecutiveLoadFailures++
                    currentIndex = (itemIndex + 1) % imageItems.size
                    if (consecutiveLoadFailures < imageItems.size) {
                        val r = Runnable { showNextImage() }
                        retryRunnable = r
                        handler.postDelayed(r, 300L)
                    } else {
                        if (BuildConfig.DEBUG_LOGGING) Log.e(TAG, "All images failed to load")
                        consecutiveLoadFailures = 0
                    }
                }
            }
        imageTargets[incoming] = target
        request.into(target)
    }

    private fun scheduleNextSlide(prefs: SharedPreferences, effect: String) {
        val durationMs = prefs.getString(Prefs.SLIDE_DURATION, "10000")
            ?.toLongOrNull()?.coerceAtLeast(1L) ?: 10_000L
        val delayMs = durationMs + when (effect) {
            "crossfade", "fade_black", "slide_left", "slide_right", "zoom_in", "zoom_out" -> transitionMs
            else -> 0L
        }
        slideshowRunnable?.let { handler.removeCallbacks(it) }
        slideshowRunnable = Runnable { showNextImage() }
        handler.postDelayed(slideshowRunnable!!, delayMs)
    }

    private fun clearImageTarget(view: ImageView) {
        val target = imageTargets.remove(view) ?: return
        try {
            Glide.with(context).clear(target)
        } catch (_: Exception) {}
    }

    private fun resetSlideshowViews() {
        resetViewState(binding.imageView1, clearDrawable = true)
        resetViewState(binding.imageView2, clearDrawable = true)
        binding.imageView1.alpha = 1f
        binding.imageView2.alpha = 0f
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
            ?.toLongOrNull()?.coerceAtLeast(1L) ?: 10_000L
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
                val t = va.animatedFraction
                val w = view.width.toFloat().takeIf { it > 0 } ?: 1280f
                val h = view.height.toFloat().takeIf { it > 0 } ?: 720f
                view.scaleX = startScale + t * (endScale - startScale)
                view.scaleY = view.scaleX
                view.translationX = (startTxFrac + t * (endTxFrac - startTxFrac)) * w
                view.translationY = (startTyFrac + t * (endTyFrac - startTyFrac)) * h
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
            .getString(Prefs.TRANSITION_SPEED, "2000")?.toLongOrNull()?.coerceAtLeast(1L) ?: 2000L

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
        val incomingEndListener = incomingTransitionEndListener(incoming, prefs, sequence)
        val requested = if (effect == "random") RANDOM_EFFECTS.random() else effect
        // Zooming a portrait image crops it into a landscape-looking frame.
        val resolved = if (requested == "zoom_in" || requested == "zoom_out") {
            if (isPortraitImage(incoming) || isPortraitImage(outgoing)) "crossfade" else requested
        } else {
            requested
        }
        when (resolved) {
            "crossfade"   -> crossfade(incoming, outgoing, incomingEndListener, sequence)
            "fade_black"  -> fadeBlack(incoming, outgoing, incomingEndListener, sequence)
            "slide_left"  -> slide(incoming, outgoing, true, incomingEndListener, sequence)
            "slide_right" -> slide(incoming, outgoing, false, incomingEndListener, sequence)
            "zoom_in"     -> zoomIn(incoming, outgoing, incomingEndListener, sequence)
            "zoom_out"    -> zoomOut(incoming, outgoing, incomingEndListener, sequence)
            else           -> crossfade(incoming, outgoing, incomingEndListener, sequence)
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
        } catch (firstFailure: RuntimeException) {
            if (BuildConfig.DEBUG_LOGGING) Log.e(TAG, "Transition failed: $effect; retrying crossfade", firstFailure)
        }

        return try {
            // Rebuild the animator state before retrying, since the first attempt may
            // have started one side of a two-view transition before failing.
            applyTransition(incoming, outgoing, "crossfade", prefs, sequence)
        } catch (secondFailure: RuntimeException) {
            if (BuildConfig.DEBUG_LOGGING) Log.e(TAG, "Crossfade fallback failed; showing image immediately", secondFailure)
            showImageImmediately(incoming, outgoing, prefs)
            "none"
        }
    }

    private fun prepareForTransition(incoming: ImageView, outgoing: ImageView) {
        incoming.animate().setListener(null).cancel()
        outgoing.animate().setListener(null).cancel()
        kenBurnsAnimators[outgoing]?.cancel()
        kenBurnsAnimators[incoming]?.cancel()

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
            if (!canceled && sequence == transitionSequence &&
                prefs.getBoolean(Prefs.KEN_BURNS_ENABLED, true) && view.drawable != null) {
                try {
                    startKenBurns(view, prefs)
                } catch (e: RuntimeException) {
                    if (BuildConfig.DEBUG_LOGGING) Log.e(TAG, "Ken Burns start failed", e)
                }
            }
        }
    }

    private fun crossfade(
        incoming: ImageView,
        outgoing: ImageView,
        incomingEndListener: AnimatorListenerAdapter,
        sequence: Long
    ) {
        incoming.alpha = 0f
        incoming.animate().alpha(1f).setDuration(transitionMs).setListener(incomingEndListener).start()
        outgoing.animate().alpha(0f).setDuration(transitionMs).setListener(resetOnEnd(outgoing, sequence)).start()
    }

    private fun showImageImmediately(incoming: ImageView, outgoing: ImageView, prefs: SharedPreferences) {
        incoming.animate().setListener(null).cancel()
        outgoing.animate().setListener(null).cancel()
        kenBurnsAnimators[incoming]?.cancel()
        kenBurnsAnimators[outgoing]?.cancel()
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
        sequence: Long
    ) {
        val half = (transitionMs / 2).coerceAtLeast(1L)
        incoming.alpha = 0f
        outgoing.animate().alpha(0f).setDuration(half).setListener(object : AnimatorListenerAdapter() {
            private var finished = false
            private var canceled = false
            override fun onAnimationEnd(animation: Animator) {
                if (finished) return
                finished = true
                resetOnEnd(outgoing, sequence).onAnimationEnd(animation)
                if (canceled || sequence != transitionSequence) return
                incoming.animate().alpha(1f).setDuration(half).setListener(incomingEndListener).start()
            }

            override fun onAnimationCancel(animation: Animator) {
                canceled = true
                onAnimationEnd(animation)
            }
        }).start()
    }

    private fun slide(
        incoming: ImageView,
        outgoing: ImageView,
        fromRight: Boolean,
        incomingEndListener: AnimatorListenerAdapter,
        sequence: Long
    ) {
        val w = (binding.root.width.takeIf { it > 0 } ?: incoming.width.takeIf { it > 0 }
        ?: context.resources.displayMetrics.widthPixels).toFloat()
        incoming.alpha = 1f; incoming.translationX = if (fromRight) w else -w
        incoming.animate().translationX(0f).setDuration(transitionMs).setListener(incomingEndListener).start()
        outgoing.animate().translationX(if (fromRight) -w else w).setDuration(transitionMs).setListener(resetOnEnd(outgoing, sequence)).start()
    }

    private fun zoomIn(
        incoming: ImageView,
        outgoing: ImageView,
        incomingEndListener: AnimatorListenerAdapter,
        sequence: Long
    ) {
        incoming.alpha = 0f; incoming.scaleX = 0.85f; incoming.scaleY = 0.85f
        incoming.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(transitionMs).setListener(incomingEndListener).start()
        outgoing.animate().alpha(0f).setDuration(transitionMs).setListener(resetOnEnd(outgoing, sequence)).start()
    }

    private fun zoomOut(
        incoming: ImageView,
        outgoing: ImageView,
        incomingEndListener: AnimatorListenerAdapter,
        sequence: Long
    ) {
        incoming.alpha = 0f
        incoming.animate().alpha(1f).setDuration(transitionMs).setListener(incomingEndListener).start()
        outgoing.animate().alpha(0f).scaleX(1.15f).scaleY(1.15f).setDuration(transitionMs).setListener(resetOnEnd(outgoing, sequence)).start()
    }

    private fun resetOnEnd(view: ImageView, sequence: Long) = object : AnimatorListenerAdapter() {
        private var finished = false
        override fun onAnimationEnd(animation: Animator) {
            if (finished) return
            finished = true
            if (sequence != transitionSequence) return
            kenBurnsAnimators[view]?.cancel()
            resetViewState(view, clearDrawable = true)
        }

        override fun onAnimationCancel(animation: Animator) {
            onAnimationEnd(animation)
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
