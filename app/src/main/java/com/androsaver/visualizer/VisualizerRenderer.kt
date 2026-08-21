package com.androsaver.visualizer

import android.opengl.GLSurfaceView
import com.androsaver.visualizer.modes.*
import java.util.concurrent.atomic.AtomicInteger
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * OpenGL ES 2.0 renderer for the music visualizer.
 * Manages the mode list, audio data, and the per-frame draw loop.
 */
class VisualizerRenderer(private val audio: AudioEngine) : GLSurfaceView.Renderer {

    private val draw = GLDraw(1280, 720)
    private var tick = 0
    private var lastFrameNs = 0L
    private var clearFrameCount = 0
    private var renderError: Throwable? = null
    private var renderErrorMode = "unknown"
    private var renderErrorReported = false
    private var surfaceReady = false

    /** Set by the host so render failures are visible outside Logcat. */
    var onRenderError: ((Throwable) -> Unit)? = null

    /** Exponential moving average of frame render time in milliseconds (EMA α=0.1). */
    var frameTimeMs = 0f
        private set

    private var activeModeIndex = 0
    @Volatile private var requestedModeIndex = 0
    private val pendingModeIndex = AtomicInteger(-1)
    private val modeRequestLock = Any()

    /** Index requested by the UI thread; applied atomically by the GL thread. */
    var modeIndex: Int
        get() = requestedModeIndex
        set(v) {
            // Avoid Math.floorMod(), which is unavailable on Android API 21–23.
            val normalized = ((v % modes.size) + modes.size) % modes.size
            synchronized(modeRequestLock) {
                requestedModeIndex = normalized
                pendingModeIndex.set(normalized)
            }
        }

    val modes = listOf(
        YantraMode(),
        CubeMode(),
        TriFluxMode(),
        LissajousMode(),
        TunnelMode(),
        CorridorMode(),
        NovaMode(),
        SpiralMode(),
        BubblesMode(),
        PlasmaMode(),
        BranchesMode(),
        ButterfliesMode(),
        FlowFieldMode(),
        FireworksMode(),
        AuroraMode(),
        LatticeMode(),
        MyceliumMode(),
        MagnetarMode(),
        SlimeMoldMode(),
        MobiusMode(),
        ChromaticMode(),
        PersistenceMode(),
        SynapseMode(),
        HeartbeatMode(),
        MorphogenesisMode(),
        HyperbolicMode(),
        LiquidLightMode(),
        CymaticaMode(),
        PhasonMode(),
        TesseractMode(),
        FerrofluidMode(),
        MandelboxMode(),
        BarsMode(),
        WaterfallMode()
    )

    val modeNames: List<String> get() = modes.map { it.name }

    /** Beat-response gain (0.0 = no reaction, 1.0 = normal, 2.0 = intense). Matches psysuals effect_gain. */
    @Volatile var beatGain: Float = 1.0f

    // ── GLSurfaceView.Renderer ─────────────────────────────────────────────────

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        surfaceReady = false
        renderError = null
        renderErrorMode = "unknown"
        renderErrorReported = false
        try {
            draw.onSurfaceCreated()
            modes.forEach { it.onSurfaceCreated() }
            surfaceReady = true
        } catch (t: Throwable) {
            failRender("surface creation", t)
        }
        synchronized(modeRequestLock) {
            activeModeIndex = requestedModeIndex
            pendingModeIndex.set(-1)
        }
        tick = 0
        lastFrameNs = 0L
        clearFrameCount = 2
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        try {
            draw.onSurfaceChanged(width, height)
        } catch (t: Throwable) {
            failRender("surface resize", t)
        }
    }

    override fun onDrawFrame(gl: GL10?) {
        if (!surfaceReady) return
        val frameStart = System.nanoTime()
        if (lastFrameNs > 0L) {
            val elapsed = (frameStart - lastFrameNs) / 1_000_000f
            frameTimeMs = frameTimeMs * 0.9f + elapsed * 0.1f
        }
        lastFrameNs = frameStart

        val requested = pendingModeIndex.getAndSet(-1)
        if (requested >= 0) {
            try {
                modes[requested].reset()
                renderError = null
                renderErrorReported = false
            } catch (t: Throwable) {
                failRender(modes[requested].name, t)
            }
            activeModeIndex = requested
            clearFrameCount = 2
            tick = 0
        }
        synchronized(audio) {
            val snapshot = audio.data
            val mode  = modes[activeModeIndex]
            val originalBeat = snapshot.beat
            val originalGain = snapshot.gain
            if (beatGain != 1.0f) {
                snapshot.beat = (originalBeat * beatGain).coerceIn(0f, 2f)
                snapshot.gain = beatGain
            }
            try {
                val clear = clearFrameCount > 0
                draw.beginFrame(clear)
                if (clear) {
                    clearFrameCount--
                    draw.endFrame()
                } else if (renderError != null) {
                    draw.rect(0f, 0f, draw.W.toFloat(), draw.H.toFloat(), 0.18f, 0f, 0f, 1f)
                    draw.endFrame()
                } else {
                    mode.draw(draw, snapshot, tick)
                    draw.endFrame()
                }
            } catch (t: Throwable) {
                failRender(mode.name, t)
                try {
                    draw.beginFrame(true)
                    draw.rect(0f, 0f, draw.W.toFloat(), draw.H.toFloat(), 0.18f, 0f, 0f, 1f)
                    draw.endFrame()
                } catch (fallbackFailure: Throwable) {
                    failRender("GL fallback after ${mode.name}", fallbackFailure)
                }
            } finally {
                snapshot.beat = originalBeat
                snapshot.gain = originalGain
            }
        }
        tick++
    }

    private fun failRender(modeName: String, throwable: Throwable) {
        renderError = throwable
        renderErrorMode = modeName
        if (renderErrorReported) return
        renderErrorReported = true
        val wrapped = IllegalStateException("Visualizer mode '$renderErrorMode' failed", throwable)
        android.util.Log.e("VisualizerRenderer", wrapped.message, wrapped)
        onRenderError?.invoke(wrapped)
    }
}
