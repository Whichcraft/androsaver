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
import com.androsaver.source.GoogleDriveSource
import com.androsaver.source.ImageItem
import com.androsaver.source.ImageSource
import com.androsaver.source.LocalStorageSource
import com.androsaver.source.DropboxSource
import com.androsaver.source.ImmichSource
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
import com.bumptech.glide.request.transition.Transition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
        private val RANDOM_EFFECTS = listOf("crossfade","fade_black","slide_left","slide_right","zoom_in","zoom_out")
        val INTENSITY_STEPS = floatArrayOf(0.0f, 0.5f, 1.0f, 1.5f, 2.0f)
        // [startScale, endScale, startTxFrac, startTyFrac, endTxFrac, endTyFrac]
        // All presets end at (0,0) so the image is always centered at rest.
        // Scale 1.04 min → (1.04-1)/2 = 0.02 overhang per side; translations ≤ 0.015 to stay within bounds.
        private val KB_PRESETS = listOf(
            floatArrayOf(1.04f, 1.08f, -0.015f, -0.01f, 0f, 0f),  // zoom in,  upper-left  → center
            floatArrayOf(1.04f, 1.08f,  0.015f,  0.01f, 0f, 0f),  // zoom in,  lower-right → center
            floatArrayOf(1.08f, 1.04f,  0.015f, -0.01f, 0f, 0f),  // zoom out, upper-right → center
            floatArrayOf(1.08f, 1.04f, -0.015f,  0.01f, 0f, 0f)   // zoom out, lower-left  → center
        )
    }

    private val handler = Handler(Looper.getMainLooper())
    private val imageItems = mutableListOf<ImageItem>()
    private var currentIndex = 0
    private var activeView = 1
    private var slideshowRunnable: Runnable? = null
    private var imageRefreshRunnable: Runnable? = null
    private var consecutiveLoadFailures = 0
    private var visualizerView: VisualizerView? = null
    private var overlayVisualizerView: VisualizerView? = null
    private var vizCycleRunnable: Runnable? = null
    private var vizCycleMs: Long = 0L
    private var clockRunnable: Runnable? = null
    private var weatherRunnable: Runnable? = null
    private val kenBurnsAnimators = mutableMapOf<ImageView, ValueAnimator>()
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
        if (mode == Prefs.MODE_VISUALIZER) {
            startVisualizerMode(prefs)
        } else {
            startSlideshowMode(prefs)
        }

        if (prefs.getBoolean(Prefs.SHOW_CLOCK, false)) startClock()
        if (prefs.getBoolean(Prefs.WEATHER_ENABLED, false)) startWeather(prefs)
    }

    fun stop() {
        stopSlideshow()
        stopImageRefresh()
        stopVisualizerMode()
        stopClock()
        stopWeather()
        overlayVisualizerView?.stopVisualizer()
        overlayVisualizerView = null
        binding.vizOverlayContainer.visibility = View.GONE
        kenBurnsAnimators.values.forEach { it.cancel() }
        kenBurnsAnimators.clear()
        Glide.with(context).onStop()
    }

    fun handleKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return true
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
        // currentIndex already points to the *next* image to show, so offset accordingly
        currentIndex = ((currentIndex + delta - 1) % imageItems.size + imageItems.size) % imageItems.size
        showNextImage()
        // Reset the auto-advance timer so the new image gets a full duration
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val durationMs = prefs.getString(Prefs.SLIDE_DURATION, "10000")?.toLongOrNull() ?: 10_000L
        slideshowRunnable?.let { handler.removeCallbacks(it); handler.postDelayed(it, durationMs) }
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
        if (modePref != "auto") vv.setMode(modePref)
        vv.renderer.beatGain = prefs.getString(Prefs.VISUALIZER_INTENSITY, "0.5")?.toFloatOrNull() ?: 0.5f
        val genre = prefs.getString(Prefs.AUDIO_GENRE, "any") ?: "any"
        vv.audio.applyGenreHint(genre)

        binding.visualizerContainer.addView(vv)
        binding.visualizerContainer.visibility = View.VISIBLE
        binding.imageView1.visibility = View.GONE
        binding.imageView2.visibility = View.GONE
        vv.startVisualizer()

        if (modePref == "auto") {
            vizCycleMs = prefs.getString(Prefs.VIZ_CYCLE_INTERVAL, "120000")?.toLongOrNull() ?: 120_000L
            if (vizCycleMs > 0L) {
                vizCycleRunnable = object : Runnable {
                    override fun run() { vv.nextMode(); handler.postDelayed(this, vizCycleMs) }
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
        visualizerView?.stopVisualizer()
        visualizerView = null
    }

    // ── Slideshow mode ────────────────────────────────────────────────────────

    private fun startSlideshowMode(prefs: SharedPreferences) {
        // Restore image views in case a previous run was in visualizer mode (which hides them)
        binding.imageView1.visibility = View.VISIBLE
        binding.imageView2.visibility = View.VISIBLE
        if (prefs.getBoolean(Prefs.VIZ_OVERLAY_ENABLED, false)) {
            val overlay = VisualizerView(context)
            overlayVisualizerView = overlay
            val opacity = prefs.getString(Prefs.VIZ_OVERLAY_OPACITY, "0.3")?.toFloatOrNull() ?: 0.3f
            overlay.alpha = opacity
            binding.vizOverlayContainer.addView(overlay)
            binding.vizOverlayContainer.visibility = View.VISIBLE
            overlay.startVisualizer()
        }
        loadImages(prefs)
    }

    private fun loadImages(prefs: SharedPreferences) {
        binding.statusText.visibility = View.VISIBLE
        binding.statusText.text = context.getString(R.string.loading_images)

        val sources = getConfiguredSources(prefs)
        if (sources.isEmpty()) {
            tryFallbackCache()
            return
        }

        scope.launch {
            val items = mutableListOf<ImageItem>()
            for (src in sources) {
                try {
                    val urls = withTimeoutOrNull(60_000L) { src.getImageUrls() }
                    if (urls != null) items.addAll(urls)
                    else if (BuildConfig.DEBUG_LOGGING) Log.w(TAG, "${src.name} timed out")
                } catch (e: Exception) { if (BuildConfig.DEBUG_LOGGING) Log.e(TAG, "Error from ${src.name}", e) }
            }
            if (items.isEmpty()) {
                tryFallbackCache()
            } else {
                // Cache in background
                scope.launch(Dispatchers.IO) { imageCache.saveImages(items, "mixed") }
                binding.statusText.visibility = View.GONE
                imageItems.clear()
                imageItems.addAll(items.shuffled())
                startSlideshow(prefs)
            }
        }
    }

    private fun tryFallbackCache() {
        val cached = imageCache.getCachedItems()
        if (cached.isNotEmpty()) {
            binding.statusText.text = context.getString(R.string.cache_fallback_notice)
            handler.postDelayed({ binding.statusText.visibility = View.GONE }, 3000)
            imageItems.clear()
            imageItems.addAll(cached.shuffled())
            startSlideshow(PreferenceManager.getDefaultSharedPreferences(context))
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
    }

    private fun startSlideshow(prefs: SharedPreferences) {
        val durationMs = prefs.getString(Prefs.SLIDE_DURATION, "10000")?.toLongOrNull() ?: 10_000L
        showNextImage()
        slideshowRunnable = object : Runnable {
            override fun run() { showNextImage(); handler.postDelayed(this, durationMs) }
        }
        handler.postDelayed(slideshowRunnable!!, durationMs)
        scheduleImageRefresh(prefs)
    }

    private fun stopSlideshow() {
        slideshowRunnable?.let { handler.removeCallbacks(it) }
        slideshowRunnable = null
    }

    // ── Periodic image refresh ────────────────────────────────────────────────
    // Re-fetches all sources every 25 minutes so Synology SIDs (which expire at ~30 min)
    // and other session-scoped credentials stay fresh without any blackout.

    private fun scheduleImageRefresh(prefs: SharedPreferences) {
        imageRefreshRunnable = object : Runnable {
            override fun run() {
                scope.launch {
                    val sources = getConfiguredSources(prefs)
                    if (sources.isEmpty()) return@launch
                    val fresh = mutableListOf<ImageItem>()
                    for (src in sources) {
                        try {
                            val urls = withTimeoutOrNull(60_000L) { src.getImageUrls() }
                            if (urls != null) fresh.addAll(urls)
                            else if (BuildConfig.DEBUG_LOGGING) Log.w(TAG, "Refresh: ${src.name} timed out")
                        } catch (e: Exception) {
                            if (BuildConfig.DEBUG_LOGGING) Log.e(TAG, "Refresh error from ${src.name}", e)
                        }
                    }
                    if (fresh.isNotEmpty()) {
                        imageItems.clear()
                        imageItems.addAll(fresh.shuffled())
                        currentIndex = 0
                        scope.launch(Dispatchers.IO) { imageCache.saveImages(fresh, "mixed") }
                        if (BuildConfig.DEBUG_LOGGING) Log.d(TAG, "Image list refreshed: ${fresh.size} items")
                    }
                }
                handler.postDelayed(this, IMAGE_REFRESH_INTERVAL_MS)
            }
        }
        handler.postDelayed(imageRefreshRunnable!!, IMAGE_REFRESH_INTERVAL_MS)
    }

    private fun stopImageRefresh() {
        imageRefreshRunnable?.let { handler.removeCallbacks(it) }
        imageRefreshRunnable = null
    }

    private fun showNextImage() {
        if (imageItems.isEmpty()) return
        val item = imageItems[currentIndex]
        currentIndex = (currentIndex + 1) % imageItems.size

        val incoming = if (activeView == 1) binding.imageView2 else binding.imageView1
        val outgoing  = if (activeView == 1) binding.imageView1 else binding.imageView2
        activeView = if (activeView == 1) 2 else 1

        val glideUrl: Any = if (item.url.startsWith("content://")) {
            android.net.Uri.parse(item.url)
        } else if (item.headers.isNotEmpty()) {
            val b = LazyHeaders.Builder()
            item.headers.forEach { (k, v) -> b.addHeader(k, v) }
            GlideUrl(item.url, b.build())
        } else {
            GlideUrl(item.url)
        }

        val glideRequest = Glide.with(context).load(glideUrl)
        // Apply explicit EXIF rotation for local/cached images where orientation is pre-read.
        // Remote HTTP images (orientation == 0) are handled by Glide's Downsampler.
        val request = if (item.orientation != 0 && item.orientation != ExifInterface.ORIENTATION_NORMAL)
            glideRequest.apply(RequestOptions().transform(ExifRotationTransformation(item.orientation)))
        else
            glideRequest

        request.into(object : CustomTarget<Drawable>(
                binding.imageView1.width.takeIf { it > 0 } ?: context.resources.displayMetrics.widthPixels,
                binding.imageView1.height.takeIf { it > 0 } ?: context.resources.displayMetrics.heightPixels
            ) {
                override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                    consecutiveLoadFailures = 0
                    kenBurnsAnimators[incoming]?.cancel()
                    incoming.setImageDrawable(resource)
                    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
                    if (prefs.getBoolean(Prefs.KEN_BURNS_ENABLED, true)) startKenBurns(incoming, prefs)
                    val effect = prefs.getString(Prefs.TRANSITION_EFFECT, "crossfade") ?: "crossfade"
                    applyTransition(incoming, outgoing, effect)
                }
                override fun onLoadCleared(placeholder: Drawable?) {
                    kenBurnsAnimators[incoming]?.cancel()
                    incoming.setImageDrawable(null)
                }
                override fun onLoadFailed(errorDrawable: Drawable?) {
                    if (BuildConfig.DEBUG_LOGGING) Log.w(TAG, "Failed: ${item.url}")
                    consecutiveLoadFailures++
                    if (consecutiveLoadFailures < imageItems.size) {
                        handler.postDelayed({ showNextImage() }, 300L)
                    } else {
                        if (BuildConfig.DEBUG_LOGGING) Log.e(TAG, "All images failed to load")
                        consecutiveLoadFailures = 0
                    }
                }
            })
    }

    // ── Ken Burns ─────────────────────────────────────────────────────────────

    private fun startKenBurns(view: ImageView, prefs: SharedPreferences) {
        val durationMs = prefs.getString(Prefs.SLIDE_DURATION, "10000")?.toLongOrNull() ?: 10_000L
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
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        vv.renderer.beatGain = prefs.getString(Prefs.VISUALIZER_INTENSITY, "0.5")?.toFloatOrNull() ?: 0.5f
    }

    fun adjustIntensity(delta: Int) {
        val vv = visualizerView ?: return
        val current = vv.renderer.beatGain
        val idx = INTENSITY_STEPS.indexOfFirst { it >= current - 0.01f }.takeIf { it >= 0 } ?: 2
        val newIdx = (idx + delta).coerceIn(0, INTENSITY_STEPS.lastIndex)
        val newGain = INTENSITY_STEPS[newIdx]
        vv.renderer.beatGain = newGain
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putString(Prefs.VISUALIZER_INTENSITY, newGain.toString()).apply()
    }

    // ── Transitions ───────────────────────────────────────────────────────────

    private val transitionMs: Long get() =
        PreferenceManager.getDefaultSharedPreferences(context)
            .getString(Prefs.TRANSITION_SPEED, "1500")?.toLongOrNull() ?: 1500L

    private fun applyTransition(incoming: ImageView, outgoing: ImageView, effect: String) {
        incoming.bringToFront()
        incoming.translationX = 0f; incoming.translationY = 0f
        outgoing.translationX = 0f; outgoing.translationY = 0f
        val resolved = if (effect == "random") RANDOM_EFFECTS.random() else effect
        when (resolved) {
            "crossfade"   -> crossfade(incoming, outgoing)
            "fade_black"  -> fadeBlack(incoming, outgoing)
            "slide_left"  -> slide(incoming, outgoing, true)
            "slide_right" -> slide(incoming, outgoing, false)
            "zoom_in"     -> zoomIn(incoming, outgoing)
            "zoom_out"    -> zoomOut(incoming, outgoing)
            else          -> crossfade(incoming, outgoing)
        }
    }

    private fun crossfade(incoming: ImageView, outgoing: ImageView) {
        incoming.alpha = 0f
        incoming.animate().alpha(1f).setDuration(transitionMs).setListener(null).start()
        outgoing.animate().alpha(0f).setDuration(transitionMs).setListener(resetOnEnd(outgoing)).start()
    }

    private fun fadeBlack(incoming: ImageView, outgoing: ImageView) {
        val half = transitionMs / 2
        incoming.alpha = 0f
        outgoing.animate().alpha(0f).setDuration(half).setListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                outgoing.setImageDrawable(null); outgoing.alpha = 0f
                incoming.animate().alpha(1f).setDuration(half).setListener(null).start()
            }
        }).start()
    }

    private fun slide(incoming: ImageView, outgoing: ImageView, fromRight: Boolean) {
        val w = context.resources.displayMetrics.widthPixels.toFloat()
        incoming.alpha = 1f; incoming.translationX = if (fromRight) w else -w
        incoming.animate().translationX(0f).setDuration(transitionMs).setListener(null).start()
        outgoing.animate().translationX(if (fromRight) -w else w).setDuration(transitionMs).setListener(resetOnEnd(outgoing)).start()
    }

    private fun zoomIn(incoming: ImageView, outgoing: ImageView) {
        incoming.alpha = 0f; incoming.scaleX = 0.85f; incoming.scaleY = 0.85f
        incoming.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(transitionMs).setListener(null).start()
        outgoing.animate().alpha(0f).setDuration(transitionMs).setListener(resetOnEnd(outgoing)).start()
    }

    private fun zoomOut(incoming: ImageView, outgoing: ImageView) {
        incoming.alpha = 0f
        incoming.animate().alpha(1f).setDuration(transitionMs).setListener(null).start()
        outgoing.animate().alpha(0f).scaleX(1.15f).scaleY(1.15f).setDuration(transitionMs).setListener(resetOnEnd(outgoing)).start()
    }

    private fun resetOnEnd(view: ImageView) = object : AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: Animator) {
            kenBurnsAnimators[view]?.cancel()
            view.setImageDrawable(null); view.alpha = 0f
            view.translationX = 0f; view.translationY = 0f; view.scaleX = 1f; view.scaleY = 1f
        }
    }
}
