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
        CliffordMode(),
        MobiusMode(),
        ChromaticMode(),
        PersistenceMode(),
        SynapseMode(),
        HeartbeatMode(),
        BarsMode(),
        WaterfallMode()
    )

    val modeNames: List<String> get() = modes.map { it.name }

    /** Beat-response gain (0.0 = no reaction, 1.0 = normal, 2.0 = intense). Matches psysuals effect_gain. */
    @Volatile var beatGain: Float = 1.0f

    // ── GLSurfaceView.Renderer ─────────────────────────────────────────────────

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        draw.onSurfaceCreated()
        modes.forEach { it.onSurfaceCreated() }
        synchronized(modeRequestLock) {
            activeModeIndex = requestedModeIndex
            pendingModeIndex.set(-1)
        }
        tick = 0
        lastFrameNs = 0L
        clearFrameCount = 2
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        draw.onSurfaceChanged(width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        val frameStart = System.nanoTime()
        if (lastFrameNs > 0L) {
            val elapsed = (frameStart - lastFrameNs) / 1_000_000f
            frameTimeMs = frameTimeMs * 0.9f + elapsed * 0.1f
        }
        lastFrameNs = frameStart

        val requested = pendingModeIndex.getAndSet(-1)
        if (requested >= 0) {
            modes[requested].reset()
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
                } else {
                    mode.draw(draw, snapshot, tick)
                    draw.endFrame()
                }
            } finally {
                snapshot.beat = originalBeat
                snapshot.gain = originalGain
            }
        }
        tick++
    }
}
