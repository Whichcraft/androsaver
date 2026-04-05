package com.androsaver.visualizer

import android.opengl.GLSurfaceView
import com.androsaver.visualizer.modes.*
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * OpenGL ES 2.0 renderer for the music visualizer.
 * Manages the mode list, audio data, and the per-frame draw loop.
 */
class VisualizerRenderer(private val audio: AudioEngine) : GLSurfaceView.Renderer {

    private val draw = GLDraw(1280, 720)
    private var tick = 0

    /** Index into [modes] of the currently active mode. */
    var modeIndex: Int = 0
        set(v) { field = v % modes.size; resetPending = true }

    // Written on main thread, read on GL thread — volatile ensures visibility.
    // Because resetPending is written *after* modeIndex, the GL thread is guaranteed
    // to see the updated modeIndex when it observes resetPending == true (JMM).
    @Volatile private var resetPending = false

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
        VortexMode(),
        BarsMode(),
        WaterfallMode()
    )

    val modeNames: List<String> get() = modes.map { it.name }

    /** Beat-response gain (0.0 = no reaction, 1.0 = normal, 2.0 = intense). Matches psysuals effect_gain. */
    @Volatile var beatGain: Float = 1.0f

    // ── GLSurfaceView.Renderer ─────────────────────────────────────────────────

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        draw.onSurfaceCreated()
        modes.forEach { it.reset() }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        draw.onSurfaceChanged(width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        if (resetPending) {
            modes[modeIndex].reset()
            resetPending = false
            tick = 0
        }
        val audio = audio.data
        val mode  = modes[modeIndex]

        val scaledAudio = if (beatGain == 1.0f) audio
                          else audio.copy(beat = (audio.beat * beatGain).coerceIn(0f, 2f))
        draw.beginFrame()
        mode.draw(draw, scaledAudio, tick)
        draw.endFrame()
        tick++
    }
}
