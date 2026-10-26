package com.androsaver

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
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
import androidx.core.content.ContextCompat
import com.androsaver.databinding.DreamLayoutBinding
import com.androsaver.source.ImageItem
import com.androsaver.source.ImageSource
import com.androsaver.source.ImageSourceResult
import com.androsaver.source.ImageSourceRegistry
import com.androsaver.visualizer.VisualizerView
import com.androsaver.visualizer.PerformanceOverlay
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.request.transition.Transition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import android.text.format.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class ScreensaverEngine(
    private val context: Context,
    private val binding: DreamLayoutBinding,
    private val scope: CoroutineScope,
    private val sourceProvider: (() -> List<ImageSource>)? = null,
    private val eligibleScopesProvider: (() -> Set<String>)? = null,
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
        private val sessionJournal = SessionJournal()
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

    private val sessionWork = ScreensaverSession(scope)
    private val workScope get() = sessionWork.scope
    private var diagnosticMode = "unknown"
    private var diagnosticSession: Long? = null
    private var journalSession: Long? = null
    private var previousSessionInterrupted = false
    private val postmortemFile get() = File(context.noBackupFilesDir, "screensaver-postmortem.txt")

    private fun persistDiagnostic(details: String) {
        if (BuildConfig.DEBUG_LOGGING) ScreensaverPostmortem.writeRecoverable(postmortemFile, details)
    }

    private val handler = Handler(Looper.getMainLooper())
    private val imageItems = mutableListOf<ImageItem>()
    private var currentIndex = 0
    private var activeView = 1
    private var slideshowRunnable: Runnable? = null
    private var imageRefreshRunnable: Runnable? = null
    private var healthRunnable: Runnable? = null
    private var nextSourceRefreshAt: Long? = null
    private val refreshMutex = Mutex()
    private var networkCallback: android.net.ConnectivityManager.NetworkCallback? = null
    private var retryRunnable: Runnable? = null
    private var finishRunnable: Runnable? = null
    private var scheduleFinishRunnable: Runnable? = null
    private var timeChangeReceiver: BroadcastReceiver? = null
    private val playback = SlideshowPlayback()
    private val sourceRefresh: SourceRefreshCoordinator = SourceRefreshCoordinator { source, result ->
        renderSourceHealth()
        SourceHealthStore.record(context, source.name, result, sourceSnapshots.health[source.name])
        if (result is ImageSourceResult.Failure) {
            reportImageSourceFailure("source enumeration", source, result.kind.name.lowercase(Locale.ROOT), null)
            workScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                ScreensaverPostmortem.write(File(postmortemFile.parentFile, "${postmortemFile.name}.sources"),
                    "SOURCE FAILURE\n${Date()}\n${source.name}: ${result.kind}\n" +
                        result.exceptionTypes.joinToString(" → ") + (result.httpStatus?.let { "\nHTTP $it" } ?: ""))
            }
        }
    }
    private val sourceSnapshots: SourceSnapshots get() = sourceRefresh.snapshots
    private var usingCachedImages = false
    private var cacheSnapshot: ImageCache.Snapshot? = null
    private var displayedIndex = -1
    private var pendingManualSkipIndex: Int? = null
    private var transitionSequence = 0L
    private var slideshowSession = 0L
    private var devTransitionErrorVisible = false
    private var diagnosticDismissKey: Int? = null
    private var activeTransitionAnimator: ValueAnimator? = null
    private var displayedItem: ImageItem? = null
    private val imageSourceNames = mutableMapOf<String, String>()
    private var blankMode = false
    private var visualizerView: VisualizerView? = null
    private var audioStatusRunnable: Runnable? = null
    private var vizCycleRunnable: Runnable? = null
    private var vizCycleMs: Long = 0L
    private var genreDetectRunnable: Runnable? = null
    private var lastDetectedGenre = ""
    private var clockRunnable: Runnable? = null
    private var weatherRunnable: Runnable? = null
    private var weatherRequestJob: Job? = null
    private var weatherRequestGeneration = 0L
    private val kenBurnsAnimators = mutableMapOf<ImageView, ValueAnimator>()
    private val imageTargets = mutableMapOf<ImageView, Target<Drawable>>()
    // The engine explicitly clears every target on stop. An Activity-scoped
    // RequestManager would also pause/resume requests through its hidden Fragment,
    // racing engine restart and invalidating the newly installed target ownership.
    private val imageRequests by lazy { Glide.with(context.applicationContext) }
    private val imageCache by lazy { ImageCache(context) }
    private val weatherFetcher by lazy { WeatherFetcher(context) }
    private val timeFmt  = DateFormat.getTimeFormat(context)
    private val dateFmt  = SimpleDateFormat("EEE, d MMM", Locale.getDefault())

    fun start(prefs: SharedPreferences) {
        stop()
        diagnosticMode = prefs.getString(Prefs.SCREENSAVER_MODE, Prefs.MODE_SLIDESHOW)
            ?.takeIf { it in setOf(Prefs.MODE_STATIC, Prefs.MODE_BLANK, Prefs.MODE_VISUALIZER) }
            ?: Prefs.MODE_SLIDESHOW
        sessionWork.start(diagnosticMode)
        workScope.launch {
            try { ImageCachePolicy.purgeLegacyDiskCache(context.applicationContext) }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { /* Retried next session; disk caching is already disabled. */ }
        }
        val journal = sessionJournal.begin(postmortemFile, diagnosticMode)
        journalSession = journal.id
        previousSessionInterrupted = journal.previousInterruption != null
        // Snapshot saved evidence before startup can report a new failure.
        diagnosticSession = ScreensaverPostmortem.register(postmortemFile, diagnosticMode)
        workScope.launch(kotlinx.coroutines.Dispatchers.IO) { ProcessExitDiagnostics.collect(context.applicationContext) }
        val previousReport = if (BuildConfig.DEBUG_LOGGING) {
            try {
                ScreensaverPostmortem.readReports(postmortemFile, includeSourceFailures = false)
            } catch (error: Exception) {
                Log.w(TAG, "Could not read screensaver postmortem", error)
                null
            }
        } else null
        devTransitionErrorVisible = false
        diagnosticDismissKey = null
        binding.devErrorOverlay.scrollTo(0, 0)
        binding.devErrorOverlay.text = ""
        binding.devErrorOverlay.visibility = View.GONE
        try {
            startMode(prefs)
            renderSourceHealth()
        } catch (error: Throwable) {
            if (!BuildConfig.DEBUG_LOGGING) throw error
            reportTransitionFailure("mode startup", error)
        }
        if (!previousReport.isNullOrBlank()) {
            val startupReport = binding.devErrorOverlay.text?.toString().orEmpty()
            binding.devErrorOverlay.text = ""
            devTransitionErrorVisible = true
            displayDiagnostic("PREVIOUS SCREENSAVER FAILURE\n\n$previousReport")
            if (startupReport.isNotBlank()) displayDiagnostic(startupReport)
        }
    }

    private fun startMode(prefs: SharedPreferences) {
        finishRunnable?.let { handler.removeCallbacks(it) }
        finishRunnable = null
        binding.imageView1.alpha = 1f
        binding.imageView2.alpha = 0f

        if (!checkSchedule(prefs)) {
            binding.root.setBackgroundColor(0xFF000000.toInt())
            finishRunnable = Runnable {
                finishRunnable = null
                onRequestFinish()
            }.also { handler.postDelayed(it, 500) }
            return
        }

        val mode = sessionWork.mode
        blankMode = mode == Prefs.MODE_BLANK
        when (mode) {
            Prefs.MODE_VISUALIZER -> startVisualizerMode(prefs)
            Prefs.MODE_BLANK -> startBlankMode()
            Prefs.MODE_STATIC -> startStaticImageMode(prefs)
            else -> startSlideshowMode(prefs)
        }

        if (mode != Prefs.MODE_BLANK) {
            if (prefs.getBoolean(Prefs.SHOW_CLOCK, false)) startClock()
            if (prefs.getBoolean(Prefs.WEATHER_ENABLED, false)) startWeather(prefs)
        }
        monitorTimeChanges(prefs)
        refreshSchedule(prefs, finishOutsideWindow = true)
    }

    /** Pause the visualizer audio + GL when the host goes to background (e.g. Home press). */
    fun pauseVisualizer() { visualizerView?.stopVisualizer() }

    /** Resume the visualizer audio + GL when the host comes back to foreground. */
    fun resumeVisualizer() { visualizerView?.startVisualizer() }

    fun stop() {
        val failures = CleanupActions.run(
            "time monitoring" to { stopTimeMonitoring() },
            "session work" to { sessionWork.stop() },
            "callbacks" to {
                blankMode = false
                handler.removeCallbacksAndMessages(null)
                finishRunnable = null
                scheduleFinishRunnable = null
            },
            "slideshow" to { stopSlideshow() },
            "image refresh" to { stopImageRefresh() },
            "visualizer" to { stopVisualizerMode() },
            "clock" to { stopClock() },
            "weather" to { stopWeather() },
            "image motion" to { cancelAllKenBurns() }
        ).toMutableList()
        // Leases and recorder ownership must be retired even if a view cleanup failed.
        failures += CleanupActions.run(
            "cache lease" to { cacheSnapshot?.close(); cacheSnapshot = null },
            "diagnostic handler" to {
                try { diagnosticSession?.let { ScreensaverPostmortem.unregister(it) } }
                finally { diagnosticSession = null }
            }
        )
        try { journalSession?.let { sessionJournal.end(it, clean = failures.isEmpty()) } }
        finally { journalSession = null }
        if (failures.isNotEmpty()) {
            val error = IllegalStateException("Screensaver cleanup failed: ${failures.joinToString { it.phase }}")
            failures.forEach { error.addSuppressed(it.error) }
            persistDiagnostic("SCREENSAVER CLEANUP FAILURE\nMode: $diagnosticMode\n${error.stackTraceToString()}")
            throw error
        }
    }

    fun handleDiagnosticKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_UP && event.keyCode == diagnosticDismissKey) {
            diagnosticDismissKey = null
            return true
        }
        if (!devTransitionErrorVisible || binding.devErrorOverlay.visibility != View.VISIBLE) return false
        val action = DiagnosticControls.action(event.keyCode)
        if (action == DiagnosticControls.Action.PASS) return false
        if (event.action != KeyEvent.ACTION_DOWN) return true
        if (action == DiagnosticControls.Action.DISMISS) {
            diagnosticDismissKey = event.keyCode
            devTransitionErrorVisible = false
            binding.devErrorOverlay.visibility = View.GONE
        } else {
            val overlay = binding.devErrorOverlay
            val viewport = (overlay.height - overlay.totalPaddingTop - overlay.totalPaddingBottom).coerceAtLeast(1)
            val content = overlay.layout?.height ?: overlay.lineCount * overlay.lineHeight
            overlay.scrollTo(0, DiagnosticControls.scrollPosition(overlay.scrollY, viewport, content, action))
        }
        return true
    }

    fun handleKeyEvent(event: KeyEvent): Boolean {
        if (handleDiagnosticKeyEvent(event)) return true
        if (sessionWork.mode == null) return false
        if (RemoteInputPolicy.passThrough(blankMode, event.keyCode)) return false
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
        } else if (sessionWork.mode == Prefs.MODE_SLIDESHOW && imageItems.isNotEmpty()) {
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
            val baseIndex = pendingManualSkipIndex
                ?: displayedIndex.takeIf { it in imageItems.indices }
                ?: currentIndex
            currentIndex = ((baseIndex + delta) % imageItems.size + imageItems.size) % imageItems.size
            pendingManualSkipIndex = currentIndex
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

    private fun monitorTimeChanges(prefs: SharedPreferences) {
        stopTimeMonitoring()
        val generation = sessionWork.generation
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (generation != sessionWork.generation || sessionWork.mode == null) return
                if (intent?.action != Intent.ACTION_TIME_CHANGED && intent?.action != Intent.ACTION_TIMEZONE_CHANGED) return
                if (!refreshSchedule(prefs, finishOutsideWindow = true)) return
                if (clockRunnable != null) startClock()
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter().apply {
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        timeChangeReceiver = receiver
    }

    private fun stopTimeMonitoring() {
        timeChangeReceiver?.let { receiver ->
            try { context.unregisterReceiver(receiver) } catch (_: IllegalArgumentException) { }
        }
        timeChangeReceiver = null
    }

    private fun refreshSchedule(prefs: SharedPreferences, finishOutsideWindow: Boolean): Boolean {
        scheduleFinishRunnable?.let { handler.removeCallbacks(it) }
        scheduleFinishRunnable = null
        val decision = ScheduleWindow.evaluate(Calendar.getInstance(), scheduleStart(prefs), scheduleEnd(prefs),
            prefs.getBoolean(Prefs.SCHEDULE_ENABLED, false))
        if (!decision.active) {
            if (finishOutsideWindow) onRequestFinish()
            return false
        }
        decision.endDelayMs?.let { delay ->
            val generation = sessionWork.generation
            scheduleFinishRunnable = Runnable {
                if (generation == sessionWork.generation && sessionWork.mode != null) {
                    refreshSchedule(prefs, finishOutsideWindow = true)
                }
            }.also { handler.postDelayed(it, delay) }
        }
        return true
    }

    private fun checkSchedule(prefs: SharedPreferences): Boolean {
        val start = scheduleStart(prefs)
        val end = scheduleEnd(prefs)
        val hour  = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return ScheduleWindow.isActive(hour, start, end, enabled = prefs.getBoolean(Prefs.SCHEDULE_ENABLED, false))
    }

    private fun scheduleStart(prefs: SharedPreferences) =
        prefs.getString(Prefs.SCHEDULE_START_HR, "8")?.toIntOrNull()?.coerceIn(0, 23) ?: 8

    private fun scheduleEnd(prefs: SharedPreferences) =
        prefs.getString(Prefs.SCHEDULE_END_HR, "22")?.toIntOrNull()?.coerceIn(0, 23) ?: 22

    // ── Clock ─────────────────────────────────────────────────────────────────

    private fun startClock() {
        timeFmt.timeZone = TimeZone.getDefault()
        dateFmt.timeZone = TimeZone.getDefault()
        clockRunnable?.let { handler.removeCallbacks(it) }
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
        stopWeather()
        val city   = prefs.getString(Prefs.WEATHER_CITY, "") ?: ""
        val apiKey = prefs.getString(Prefs.WEATHER_API_KEY, "") ?: ""
        if (city.isBlank() || apiKey.isBlank()) return
        val generation = weatherRequestGeneration
        refreshWeather(city, apiKey, generation)
        weatherRunnable = object : Runnable {
            override fun run() {
                refreshWeather(city, apiKey, generation)
                handler.postDelayed(this, 30 * 60 * 1000L)
            }
        }
        handler.postDelayed(weatherRunnable!!, 30 * 60 * 1000L)
    }

    private fun refreshWeather(city: String, apiKey: String, generation: Long) {
        weatherRequestJob?.cancel()
        weatherRequestJob = workScope.launch {
            val data = weatherFetcher.getWeather(city, apiKey)
            if (generation != weatherRequestGeneration) return@launch
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
        weatherRequestGeneration++
        weatherRunnable?.let { handler.removeCallbacks(it) }
        weatherRunnable = null
        weatherRequestJob?.cancel()
        weatherRequestJob = null
        binding.weatherWidget.visibility = View.GONE
    }

    // ── Visualizer mode ───────────────────────────────────────────────────────

    private fun startVisualizerMode(prefs: SharedPreferences) {
        val modePref = prefs.getString(Prefs.VISUALIZER_MODE, "auto") ?: "auto"
        val vv = VisualizerView(context)
        visualizerView = vv
        val visualizerSession = sessionWork.generation
        vv.onRenderError = { error ->
            if (visualizerSession == sessionWork.generation) {
                reportTransitionFailure("visualizer render", error, sourceName = "visualizer", forceShow = true)
            }
        }

        if (BuildConfig.DEBUG_LOGGING) {
            val performance = PerformanceOverlay()
            vv.onPerformanceUpdate = { sample ->
                if (visualizerSession == sessionWork.generation && performance.shouldPublish(System.nanoTime())) {
                    handler.post {
                        if (visualizerSession == sessionWork.generation && visualizerView === vv &&
                            sessionWork.mode == Prefs.MODE_VISUALIZER) {
                            binding.devPerformanceOverlay.text = performance.text(sample)
                            binding.devPerformanceOverlay.visibility = View.VISIBLE
                            binding.devPerformanceOverlay.bringToFront()
                            if (devTransitionErrorVisible) binding.devErrorOverlay.bringToFront()
                        }
                    }
                }
            }
        }

        val allNames = vv.renderer.modeNames.toSet()
        val storedModes = prefs.getStringSet(Prefs.VIZ_ENABLED_MODES, null)
        val knownModes = prefs.getStringSet(Prefs.VIZ_KNOWN_MODES, null)
        val enabledModes = when {
            storedModes == null || storedModes.isEmpty() -> allNames
            else -> (storedModes + (allNames - (knownModes ?: allNames))).intersect(allNames)
        }
        vv.enabledModeNames = enabledModes
        prefs.edit()
            .putStringSet(Prefs.VIZ_KNOWN_MODES, allNames)
            .putStringSet(Prefs.VIZ_ENABLED_MODES, enabledModes)
            .apply()

        vv.selectInitialMode(modePref)

        vv.renderer.beatGain = prefs.getString(Prefs.VISUALIZER_INTENSITY, "0.5")?.toFloatOrNull() ?: 0.5f
        val genre = prefs.getString(Prefs.AUDIO_GENRE, "any") ?: "any"
        if (genre == "auto") {
            vv.audio.setGenreDetectionEnabled(true)
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
                                    ?: enabledNames.firstOrNull()
                                target?.let { vv.setMode(it) }
                                resetVizCycleTimer()
                            }
                        }
                    }
                    handler.postDelayed(this, 30_000L)
                }
            }
            handler.postDelayed(genreDetectRunnable!!, 30_000L)
        } else {
            vv.audio.setGenreDetectionEnabled(false)
            vv.audio.applyGenreHint(genre)
        }

        binding.visualizerContainer.addView(vv)
        binding.visualizerContainer.visibility = View.VISIBLE
        binding.imageView1.visibility = View.GONE
        binding.imageView2.visibility = View.GONE
        vv.startVisualizer()
        audioStatusRunnable = object : Runnable {
            override fun run() {
                if (visualizerView !== vv || visualizerSession != sessionWork.generation) return
                val message = when (vv.audioStatus) {
                    com.androsaver.visualizer.AudioCaptureMonitor.Status.ACTIVE -> R.string.audio_status_active
                    com.androsaver.visualizer.AudioCaptureMonitor.Status.PERMISSION_REQUIRED -> R.string.audio_status_permission
                    com.androsaver.visualizer.AudioCaptureMonitor.Status.UNAVAILABLE -> R.string.audio_status_unavailable
                    com.androsaver.visualizer.AudioCaptureMonitor.Status.NO_SIGNAL -> R.string.audio_status_no_signal
                    com.androsaver.visualizer.AudioCaptureMonitor.Status.STARTING -> R.string.audio_status_starting
                    else -> null
                }
                binding.audioCaptureStatus.text = message?.let { context.getString(it) }.orEmpty()
                binding.audioCaptureStatus.visibility = if (message == null) View.GONE else View.VISIBLE
                handler.postDelayed(this, 1000)
            }
        }.also { handler.post(it) }

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
        audioStatusRunnable?.let { handler.removeCallbacks(it) }
        audioStatusRunnable = null
        binding.audioCaptureStatus.visibility = View.GONE
        vizCycleRunnable?.let { handler.removeCallbacks(it) }
        vizCycleRunnable = null
        genreDetectRunnable?.let { handler.removeCallbacks(it) }
        genreDetectRunnable = null
        val view = visualizerView
        visualizerView = null
        try {
            view?.onPerformanceUpdate = null
            view?.onRenderError = null
            view?.stopVisualizer()
        } finally {
            if (view != null) binding.visualizerContainer.removeView(view)
            binding.devPerformanceOverlay.visibility = View.GONE
            binding.devPerformanceOverlay.text = ""
            binding.visualizerContainer.visibility = View.GONE
        }
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

    private fun startStaticImageMode(prefs: SharedPreferences) {
        applyImageBackground(prefs, Prefs.STATIC_BACKGROUND_MODE, Prefs.STATIC_BACKGROUND_COLOR, null)
        binding.visualizerContainer.visibility = View.GONE
        binding.imageView1.visibility = View.VISIBLE
        binding.imageView2.visibility = View.GONE
        binding.statusText.visibility = View.GONE
        binding.devErrorOverlay.visibility = View.GONE
        resetSlideshowViews()

        val imageSource = StaticImageFiles.selectedFile(
            File(context.filesDir, "static_background"),
            prefs.getString(Prefs.STATIC_IMAGE_LOCAL_PATH, null)
        )
        if (imageSource == null) {
            binding.statusText.text = context.getString(R.string.static_image_not_selected)
            binding.statusText.visibility = View.VISIBLE
            return
        }

        binding.imageView1.scaleType = imageScaleType(
            ImageBehavior.resolve(Prefs.MODE_STATIC, false,
                prefs.getString(Prefs.STATIC_IMAGE_SCALE, null),
                prefs.getString(Prefs.STATIC_IMAGE_SCALE_PORTRAIT, null))
        )
        val imageView = binding.imageView1
        val target = object : CustomTarget<Drawable>(
            imageView.width.takeIf { it > 0 } ?: context.resources.displayMetrics.widthPixels,
            imageView.height.takeIf { it > 0 } ?: context.resources.displayMetrics.heightPixels
        ) {
            override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                if (imageTargets[imageView] !== this) return
                try {
                    imageView.setImageDrawable(resource)
                    imageView.scaleType = imageScaleTypeForDrawable(
                        prefs,
                        resource,
                        Prefs.STATIC_IMAGE_SCALE,
                        Prefs.STATIC_IMAGE_SCALE_PORTRAIT
                    )
                    applyImageBackground(prefs, Prefs.STATIC_BACKGROUND_MODE, Prefs.STATIC_BACKGROUND_COLOR, resource)
                    imageView.alpha = 1f
                } catch (error: Throwable) {
                    reportTransitionFailure("static image ready/background", error)
                }
            }

            override fun onLoadCleared(placeholder: Drawable?) {
                if (imageTargets[imageView] === this) imageTargets.remove(imageView)
                imageView.setImageDrawable(null)
            }

            override fun onLoadFailed(errorDrawable: Drawable?) {
                if (imageTargets[imageView] !== this) return
                imageTargets.remove(imageView)
                imageView.setImageDrawable(errorDrawable)
                binding.statusText.text = context.getString(R.string.static_image_load_failed)
                binding.statusText.visibility = View.VISIBLE
            }
        }
        imageTargets[imageView] = target
        val staticRequest = imageRequests.load(imageSource)
            .downsample(com.bumptech.glide.load.resource.bitmap.DownsampleStrategy.AT_MOST)
            .disallowHardwareConfig()
        staticRequest.listener(object : RequestListener<Drawable> {
            override fun onLoadFailed(
                e: GlideException?, model: Any?, target: Target<Drawable>, isFirstResource: Boolean
            ): Boolean {
                reportTransitionFailure("static image fetch/decode",
                    e ?: GlideException("Unknown static image load failure"))
                return false
            }

            override fun onResourceReady(
                resource: Drawable, model: Any, target: Target<Drawable>?,
                dataSource: DataSource, isFirstResource: Boolean
            ): Boolean = false
        }).into(target)
    }

    private fun startSlideshowMode(prefs: SharedPreferences) {
        applyImageBackground(prefs, Prefs.SLIDESHOW_BACKGROUND_MODE, Prefs.SLIDESHOW_BACKGROUND_COLOR, null)
        binding.visualizerContainer.visibility = View.GONE
        binding.imageView1.visibility = View.VISIBLE
        binding.imageView2.visibility = View.VISIBLE
        val defaultScale = ImageBehavior.defaultFor(Prefs.MODE_SLIDESHOW, false)
        binding.imageView1.scaleType = imageScaleType(prefs.getString(Prefs.SLIDESHOW_IMAGE_SCALE, defaultScale))
        binding.imageView2.scaleType = imageScaleType(prefs.getString(Prefs.SLIDESHOW_IMAGE_SCALE, defaultScale))
        slideshowSession++
        resetSlideshowViews()
        startSourceRecovery(slideshowSession)
        loadImages(prefs, slideshowSession)
    }

    private fun healthTime(at: Long): String =
        android.text.format.DateFormat.getDateFormat(context).format(Date(at)) + " " + timeFmt.format(Date(at))

    private fun renderSourceHealth() {
        val lines = sourceSnapshots.health.mapNotNull { (name, health) ->
            val message = when {
                health.failure == ImageSourceResult.FailureKind.AUTHENTICATION -> R.string.source_health_auth
                health.failure == ImageSourceResult.FailureKind.PERMISSION -> R.string.source_health_permission
                health.failure == ImageSourceResult.FailureKind.TLS_EXPIRED -> R.string.source_health_tls_expired
                health.failure == ImageSourceResult.FailureKind.TLS_NOT_YET_VALID -> R.string.source_health_tls_date
                health.failure in setOf(ImageSourceResult.FailureKind.TLS_UNTRUSTED, ImageSourceResult.FailureKind.TLS_IDENTITY, ImageSourceResult.FailureKind.TLS) -> R.string.source_health_tls
                health.failure != null || health.partial -> R.string.source_health_unavailable
                health.empty -> R.string.source_health_empty
                else -> null
            }
            message?.let {
                val details = mutableListOf(context.getString(it, name))
                health.lastSuccessAt?.let { at -> details += context.getString(R.string.source_last_success, healthTime(at)) }
                nextSourceRefreshAt?.let { at -> details += context.getString(R.string.source_next_check, healthTime(at)) }
                details.joinToString("\n")
            }
        }.toMutableList()
        if (previousSessionInterrupted) lines.add(0, context.getString(R.string.session_interrupted_notice))
        if (usingCachedImages) lines.add(0, context.getString(R.string.source_health_cache))
        binding.sourceHealth.text = lines.joinToString("\n")
        binding.sourceHealth.visibility = if (lines.isEmpty()) View.GONE else View.VISIBLE
    }

    /** A configured source being offline is recoverable; it must not block fallback images. */
    private fun reportImageSourceFailure(
        phase: String,
        source: ImageSource,
        kind: String,
        throwable: Throwable?
    ) {
        if (!BuildConfig.DEBUG_LOGGING) return
        if (throwable == null) {
            Log.w(TAG, "$phase: ${source.name} reported $kind")
        } else {
            Log.w(TAG, "$phase: ${source.name} failed ($kind)", throwable)
        }
    }

    private fun loadImages(prefs: SharedPreferences, session: Long) {
        if (!devTransitionErrorVisible) {
            binding.statusText.visibility = View.VISIBLE
            binding.statusText.text = context.getString(R.string.loading_images)
        }

        val sources = getConfiguredSources(prefs)
        if (sources.isEmpty()) {
            workScope.launch { tryFallbackCache(session) }
            return
        }

        workScope.launch {
            try {
                ProgressiveSources.load(
                    sources,
                    fetch = { source -> sourceRefresh.fetch(source) },
                    onItems = merge@ { source, items ->
                        if (session != slideshowSession) return@merge
                        if (usingCachedImages) {
                            // Prefer newly working URLs over paths that cache refresh may retire.
                            applySourceSnapshots()
                            return@merge
                        }
                        val known = imageItems.map(::imageKey).toHashSet()
                        val added = items.filter { known.add(imageKey(it)) }
                        if (added.isEmpty()) return@merge
                        val previousCount = imageItems.size
                        val firstSource = previousCount == 0
                        added.forEach { imageSourceNames[imageKey(it)] = source.name }
                        // Append later sources without resetting the displayed image,
                        // in-flight Glide request, current index or slide timer.
                        imageItems.addAll(added.shuffled())
                        if (!firstSource && displayedIndex == previousCount - 1 && currentIndex == 0) {
                            currentIndex = previousCount
                        }
                        if (firstSource) {
                            if (!devTransitionErrorVisible) binding.statusText.visibility = View.GONE
                            startSlideshow(prefs, session)
                        } else if (playback.resumeWithNewItems()) {
                            currentIndex = previousCount
                            handler.post {
                                if (session == slideshowSession) showNextImage()
                            }
                        }
                    }
                )
                if (session == slideshowSession) {
                    if (imageItems.isEmpty()) tryFallbackCache(session)
                    if (imageRefreshRunnable == null) scheduleImageRefresh(prefs, session)
                    warmImageCache(session)
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                if (session != slideshowSession) return@launch
                reportTransitionFailure("slideshow loading pipeline", error)
                if (imageItems.isEmpty()) tryFallbackCache(session)
            }
        }
    }

    private suspend fun tryFallbackCache(session: Long, requireStalled: Boolean = false) {
        try {
            if (session != slideshowSession) return
            val snapshot = imageCache.acquireSnapshot(eligibleCacheScopes())
            if (session != slideshowSession) { snapshot.close(); return }
            val cached = snapshot.items
            if (requireStalled && !playback.isStalled) {
                snapshot.close()
                playback.fallbackFinished(used = false)
                return
            }
            playback.fallbackFinished(used = true)
            cacheSnapshot?.close()
            cacheSnapshot = snapshot
            if (cached.isNotEmpty()) {
                usingCachedImages = true
                renderSourceHealth()
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
            if (session != slideshowSession) return
            playback.fallbackFinished(used = true)
            reportTransitionFailure("fallback image cache", t, sourceName = "fallback image cache")
        }
    }

    private fun eligibleCacheScopes(): Set<String> =
        (eligibleScopesProvider?.invoke() ?: ImageSourceRegistry.eligibleScopes(context)) - sourceSnapshots.emptyScopes()

    private fun getConfiguredSources(prefs: SharedPreferences): List<ImageSource> {
        return try {
            sourceProvider?.invoke() ?: ImageSourceRegistry.configured(context, includeBundledFallback = true)
        } catch (t: Throwable) {
            reportTransitionFailure("image source initialization", t)
            emptyList()
        }
    }

    private fun startSlideshow(prefs: SharedPreferences, session: Long) {
        if (session != slideshowSession) return
        stopImageRefresh()
        activeView = 1
        currentIndex = 0
        displayedIndex = -1
        pendingManualSkipIndex = null
        displayedItem = null
        transitionSequence++
        resetSlideshowViews()
        showNextImage()
        scheduleImageRefresh(prefs, session)
    }

    private fun stopSlideshow() {
        networkCallback?.let { callback ->
            try { (context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager).unregisterNetworkCallback(callback) }
            catch (_: Exception) { }
        }
        networkCallback = null
        slideshowSession++
        imageItems.clear()
        imageSourceNames.clear()
        playback.reset()
        sourceSnapshots.clear()
        usingCachedImages = false
        binding.sourceHealth.visibility = View.GONE
        slideshowRunnable?.let { handler.removeCallbacks(it) }
        slideshowRunnable = null
        retryRunnable?.let { handler.removeCallbacks(it) }
        retryRunnable = null
        transitionSequence++
        displayedIndex = -1
        pendingManualSkipIndex = null
        displayedItem = null
        activeView = 1
        currentIndex = 0
        resetSlideshowViews()
        cacheSnapshot?.close()
        cacheSnapshot = null
    }

    // ── Periodic image refresh ────────────────────────────────────────────────
    // Re-fetches all sources every 25 minutes so Synology SIDs (which expire at ~30 min)
    // and other session-scoped credentials stay fresh without any blackout.

    private fun scheduleImageRefresh(prefs: SharedPreferences, session: Long) {
        imageRefreshRunnable = object : Runnable {
            override fun run() {
                if (session != slideshowSession) return
                workScope.launch {
                    try {
                        if (session != slideshowSession) return@launch
                        refreshSourcesNow()
                    } catch (t: Throwable) {
                        if (t is CancellationException) throw t
                        reportTransitionFailure("image refresh pipeline", t)
                    }
                }
                if (session == slideshowSession) {
                    nextSourceRefreshAt = System.currentTimeMillis() + IMAGE_REFRESH_INTERVAL_MS
                    SourceHealthStore.schedule(context, nextSourceRefreshAt)
                    handler.postDelayed(this, IMAGE_REFRESH_INTERVAL_MS)
                }
            }
        }
        nextSourceRefreshAt = System.currentTimeMillis() + IMAGE_REFRESH_INTERVAL_MS
        SourceHealthStore.schedule(context, nextSourceRefreshAt)
        handler.postDelayed(imageRefreshRunnable!!, IMAGE_REFRESH_INTERVAL_MS)
        startHealthUpdates(session)
    }

    internal suspend fun refreshSourcesNow() = refreshMutex.withLock {
        if (sessionWork.mode != Prefs.MODE_SLIDESHOW) return@withLock
        val session = slideshowSession
        sourceRefresh.refresh(getConfiguredSources(Prefs.get(context)),
            onUpdated = { available -> if (session == slideshowSession) applySourceSnapshots(available) },
            onItems = { _, _ -> })
        if (session == slideshowSession) warmImageCache(session)
    }

    private suspend fun retrySourcesSafely() {
        try { refreshSourcesNow() }
        catch (error: CancellationException) { throw error }
        catch (error: Exception) { reportTransitionFailure("source retry", error) }
    }

    private fun startSourceRecovery(session: Long) {
        workScope.launch {
            SourceRefreshSignals.events.collect {
                if (session == slideshowSession) retrySourcesSafely()
            }
        }
        try {
            val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val offline = java.util.concurrent.atomic.AtomicBoolean(manager.activeNetworkInfo?.isConnected != true)
            val callback = object : android.net.ConnectivityManager.NetworkCallback() {
                override fun onLost(network: android.net.Network) { offline.set(true) }
                override fun onAvailable(network: android.net.Network) {
                    if (offline.getAndSet(false)) handler.post {
                        if (session == slideshowSession) workScope.launch { retrySourcesSafely() }
                    }
                }
            }
            if (android.os.Build.VERSION.SDK_INT >= 24) manager.registerDefaultNetworkCallback(callback)
            else manager.registerNetworkCallback(android.net.NetworkRequest.Builder()
                .addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET).build(), callback)
            networkCallback = callback
        } catch (_: Exception) { /* Scheduled and manual retries remain available. */ }
    }

    private fun warmImageCache(session: Long) {
        val groups = sourceSnapshots.groups()
        val emptyScopes = sourceSnapshots.emptyScopes()
        val prefs = Prefs.get(context)
        workScope.launch {
            try { imageCache.warm(groups) { com.androsaver.source.SourceIdentity.eligible(prefs, it.sourceScope) && it.sourceScope !in emptyScopes } }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { if (session == slideshowSession) reportTransitionFailure("image cache warming", error) }
        }
    }

    private fun startHealthUpdates(session: Long) {
        healthRunnable?.let { handler.removeCallbacks(it) }
        healthRunnable = object : Runnable {
            override fun run() {
                if (session != slideshowSession) return
                renderSourceHealth()
                handler.postDelayed(this, 60_000L)
            }
        }.also { handler.post(it) }
    }

    private fun applySourceSnapshots(allowLeavingCache: Boolean = true) {
        if (usingCachedImages) {
            val allowed = eligibleCacheScopes()
            val removed = imageItems.removeAll { it.sourceScope !in allowed }
            if (imageItems.isEmpty()) enterEmptySlideshow()
            else {
                if (removed) {
                    transitionSequence++
                    resetSlideshowViews()
                    displayedItem = null
                    displayedIndex = -1
                    currentIndex = 0
                    showNextImage()
                }
                if (!allowLeavingCache) return
            }
        }
        val fresh = sourceSnapshots.items()
        // A temporary outage must not replace an already active offline fallback.
        if (fresh.isEmpty() && usingCachedImages) return
        val current = displayedItem
        imageItems.clear()
        imageItems.addAll(fresh.shuffled())
        imageSourceNames.clear()
        imageSourceNames.putAll(sourceSnapshots.sourceNames())
        usingCachedImages = false
        renderSourceHealth()
        pendingManualSkipIndex = null
        displayedIndex = current?.let { old -> imageItems.indexOfFirst { it.stableId == old.stableId } } ?: -1
        displayedItem = imageItems.getOrNull(displayedIndex)
        currentIndex = if (displayedIndex >= 0 && imageItems.isNotEmpty()) (displayedIndex + 1) % imageItems.size else 0
        if (fresh.isEmpty()) {
            enterEmptySlideshow()
        } else {
            if (!devTransitionErrorVisible) binding.statusText.visibility = View.GONE
            if (current != null && displayedIndex < 0) {
                transitionSequence++
                resetSlideshowViews()
            }
            if (playback.resumeWithNewItems() || current == null || displayedIndex < 0) showNextImage()
        }
    }

    private fun enterEmptySlideshow() {
        transitionSequence++
        imageItems.clear()
        imageSourceNames.clear()
        slideshowRunnable?.let { handler.removeCallbacks(it) }
        retryRunnable?.let { handler.removeCallbacks(it) }
        slideshowRunnable = null
        retryRunnable = null
        resetSlideshowViews()
        cacheSnapshot?.close()
        cacheSnapshot = null
        usingCachedImages = false
        displayedItem = null
        displayedIndex = -1
        pendingManualSkipIndex = null
        currentIndex = 0
        playback.empty()
        if (!devTransitionErrorVisible) {
            binding.statusText.text = context.getString(R.string.no_images_found)
            binding.statusText.visibility = View.VISIBLE
        }
        renderSourceHealth()
    }

    private fun stopImageRefresh() {
        healthRunnable?.let { handler.removeCallbacks(it) }
        healthRunnable = null
        nextSourceRefreshAt = null
        SourceHealthStore.schedule(context, null)
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
        playback.loading()
        val incoming = if (activeView == 1) binding.imageView2 else binding.imageView1
        val outgoing  = if (activeView == 1) binding.imageView1 else binding.imageView2
        val itemIndex = currentIndex.takeIf { it in imageItems.indices } ?: 0
        currentIndex = itemIndex
        val item = imageItems[itemIndex]
        val requestSequence = ++transitionSequence
        clearImageTarget(incoming)

        val glideModel = ImageModels.forItem(item)

        val prefs = Prefs.get(context)
        val request = imageRequests.load(glideModel)
            .downsample(com.bumptech.glide.load.resource.bitmap.DownsampleStrategy.AT_MOST)
            .disallowHardwareConfig()
            .apply {
                signature(ImageCachePolicy.signature(item))
            }

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
                            // Glide forbids clear()/into() while it is dispatching a target
                            // callback, even when the request has become unusable.
                            handler.post {
                                if (imageTargets[incoming] === this) clearImageTarget(incoming)
                            }
                            return
                        }
                        val resolvedItemIndex = findImageItemIndex(item)
                        if (resolvedItemIndex < 0) {
                            // The periodic refresh removed this item while it was loading.
                            handler.post {
                                // Glide forbids clear()/into() while it is dispatching a target
                                // callback. Defer both operations until this callback returns.
                                if (imageTargets[incoming] === this) clearImageTarget(incoming)
                                if (requestSequence == transitionSequence) showNextImage()
                            }
                            return
                        }
                        playback.ready()
                        cancelKenBurns(incoming)
                        val prefs = com.androsaver.Prefs.get(context)
                        incoming.setImageDrawable(resource)
                        // Keep the old file generation alive until a replacement has
                        // actually loaded; source enumeration alone does not retire IO.
                        if (!usingCachedImages && cacheSnapshot?.items?.none { it.url == item.url } == true) {
                            cacheSnapshot?.close()
                            cacheSnapshot = null
                        }
                        incoming.scaleType = imageScaleTypeForDrawable(
                            prefs,
                            resource,
                            Prefs.SLIDESHOW_IMAGE_SCALE,
                            Prefs.SLIDESHOW_IMAGE_SCALE_PORTRAIT
                        )
                        applyImageBackground(prefs, Prefs.SLIDESHOW_BACKGROUND_MODE, Prefs.SLIDESHOW_BACKGROUND_COLOR, resource)
                        activeView = if (activeView == 1) 2 else 1
                        displayedIndex = resolvedItemIndex
                        displayedItem = imageItems[resolvedItemIndex]
                        currentIndex = (resolvedItemIndex + 1) % imageItems.size
                        pendingManualSkipIndex = null
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
                        val failedIndex = findImageItemIndex(item)
                        currentIndex = if (failedIndex >= 0) {
                            (failedIndex + 1) % imageItems.size
                        } else {
                            currentIndex.coerceIn(0, imageItems.lastIndex)
                        }
                        if (playback.failed(imageItems.size)) {
                            val r = Runnable { showNextImage() }
                            retryRunnable = r
                            handler.postDelayed(r, 300L)
                        } else if (playback.requestFallback()) {
                            val failedSession = slideshowSession
                            // Glide forbids new loads/clears inside its callbacks.
                            handler.post {
                                if (failedSession == slideshowSession && playback.isStalled) {
                                    workScope.launch { tryFallbackCache(failedSession, requireStalled = true) }
                                } else if (failedSession == slideshowSession) {
                                    playback.fallbackFinished(used = false)
                                }
                            }
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

    private fun imageScaleType(value: String?): ImageView.ScaleType = when (value) {
        "fit" -> ImageView.ScaleType.FIT_CENTER
        "center" -> ImageView.ScaleType.CENTER
        "stretch" -> ImageView.ScaleType.FIT_XY
        else -> ImageView.ScaleType.CENTER_CROP
    }

    private fun imageScaleTypeForDrawable(
        prefs: SharedPreferences,
        drawable: Drawable,
        landscapeKey: String,
        portraitKey: String
    ): ImageView.ScaleType {
        val isPortrait = drawable.intrinsicHeight > drawable.intrinsicWidth
        val key = if (isPortrait) portraitKey else landscapeKey
        val mode = if (landscapeKey == Prefs.STATIC_IMAGE_SCALE) Prefs.MODE_STATIC else Prefs.MODE_SLIDESHOW
        return imageScaleType(ImageBehavior.resolve(
            mode,
            isPortrait,
            prefs.getString(landscapeKey, null),
            prefs.getString(portraitKey, null)
        ))
    }

    private fun parseBackgroundColor(prefs: SharedPreferences, key: String): Int {
        val value = prefs.getString(key, "#000000") ?: "#000000"
        return try {
            android.graphics.Color.parseColor(value.trim().let {
                if (it.startsWith("#")) it else "#$it"
            })
        } catch (_: IllegalArgumentException) {
            0xFF000000.toInt()
        }
    }

    private fun applyImageBackground(
        prefs: SharedPreferences,
        modeKey: String,
        colorKey: String,
        drawable: Drawable?
    ) {
        ImageBackground.apply(
            binding.root,
            drawable,
            prefs.getString(modeKey, ImageBackground.AUTO),
            parseBackgroundColor(prefs, colorKey)
        )
    }

    private fun findImageItemIndex(item: ImageItem): Int =
        imageItems.indexOfFirst { it.stableId == item.stableId }

    private fun imageKey(item: ImageItem): String = item.stableId

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
            imageRequests.clear(target)
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
        val behavior = if (view === binding.imageView1 || view === binding.imageView2) {
            val drawable = view.drawable
            val portrait = drawable != null && drawable.intrinsicHeight > drawable.intrinsicWidth
            val p = com.androsaver.Prefs.get(context)
            ImageBehavior.resolve(Prefs.MODE_SLIDESHOW, portrait,
                p.getString(Prefs.SLIDESHOW_IMAGE_SCALE, null),
                p.getString(Prefs.SLIDESHOW_IMAGE_SCALE_PORTRAIT, null))
        } else ImageBehavior.CROP
        // Motion is only safe for cropped images; Fit/Center must remain complete.
        if (behavior != ImageBehavior.CROP || isPortraitImage(view)) {
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
        sourceName: String? = null,
        forceShow: Boolean = false
    ) {
        if (!BuildConfig.DEBUG_LOGGING && !forceShow) return
        if (Looper.myLooper() != Looper.getMainLooper()) {
            val generation = sessionWork.generation
            handler.post {
                if (generation == sessionWork.generation && sessionWork.mode != null) {
                    reportTransitionFailure(effect, throwable, attemptedItem, sourceName, forceShow)
                }
            }
            return
        }
        devTransitionErrorVisible = true
        try {
            Log.e(TAG, "Screensaver failure ($diagnosticMode): $effect", throwable)
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
                append("Time: ").append(Date()).append('\n')
                append("Mode: ").append(diagnosticMode).append('\n')
                visualizerView?.let { append("Audio capture: ").append(it.audioStatus.name).append('\n') }
                append("Stage/effect: ").append(effect).append('\n')
                if (diagnosticMode == Prefs.MODE_STATIC) {
                    val prefs = com.androsaver.Prefs.get(context)
                    val file = prefs.getString(Prefs.STATIC_IMAGE_LOCAL_PATH, null)?.let { File(it) }
                    append("Static image: exists=").append(file?.isFile ?: false)
                        .append(", bytes=").append(file?.length() ?: 0).append('\n')
                    append("Static scale: ").append(prefs.getString(Prefs.STATIC_IMAGE_SCALE, null))
                        .append(", portrait=").append(prefs.getString(Prefs.STATIC_IMAGE_SCALE_PORTRAIT, null))
                        .append("; background=").append(prefs.getString(Prefs.STATIC_BACKGROUND_MODE, null))
                        .append('\n')
                }
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

        persistDiagnostic(details)
        displayDiagnostic(details)
    }

    private fun displayDiagnostic(details: String) {
        val display = Runnable {
            try {
                val prior = binding.devErrorOverlay.text?.toString().orEmpty()
                binding.devErrorOverlay.text = when {
                    prior.isBlank() -> context.getString(R.string.diagnostic_controls) + "\n\n" + details
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
        binding.devPerformanceOverlay.bringToFront()
        binding.clockOverlay.bringToFront()
        binding.weatherWidget.bringToFront()
        binding.statusText.bringToFront()
        binding.playbackNotices.bringToFront()
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
