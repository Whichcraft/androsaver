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
        set(v) { field = v % modes.size; modes[field].reset() }

    val modes = listOf(
        YantraMode(),
        CubeMode(),
        PlasmaMode(),
        TunnelMode(),
        LissajousMode(),
        NovaMode(),
        SpiralMode(),
        BubblesMode(),
        BarsMode(),
        WaterfallMode()
    )

    val modeNames: List<String> get() = modes.map { it.name }

    // ── GLSurfaceView.Renderer ─────────────────────────────────────────────────

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        draw.onSurfaceCreated()
        modes.forEach { it.reset() }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        draw.onSurfaceChanged(width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        val audio = audio.data
        val mode  = modes[modeIndex]

        draw.beginFrame()
        mode.draw(draw, audio, tick)
        draw.endFrame()
        tick++
    }
}
