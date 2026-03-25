package com.androsaver.visualizer

import android.content.Context
import android.opengl.GLSurfaceView

/**
 * GLSurfaceView that hosts the music visualizer.
 * Manages the AudioEngine and VisualizerRenderer lifecycle.
 */
class VisualizerView(context: Context) : GLSurfaceView(context) {

    internal val audio   = AudioEngine()
    val renderer         = VisualizerRenderer(audio)

    init {
        setEGLContextClientVersion(2)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    fun startVisualizer() {
        audio.start()
        onResume()
    }

    fun stopVisualizer() {
        onPause()
        audio.stop()
    }

    /** Switch to the next mode (wraps around). */
    fun nextMode() {
        renderer.modeIndex = (renderer.modeIndex + 1) % renderer.modes.size
    }

    /** Switch to the previous mode (wraps around). */
    fun previousMode() {
        renderer.modeIndex = (renderer.modeIndex - 1 + renderer.modes.size) % renderer.modes.size
    }

    /** Switch to a mode by its name. No-op if name not found. */
    fun setMode(name: String) {
        val idx = renderer.modeNames.indexOfFirst { it.equals(name, ignoreCase = true) }
        if (idx >= 0) renderer.modeIndex = idx
    }

    /** Switch to a mode by its index. */
    fun setModeIndex(index: Int) {
        renderer.modeIndex = index
    }
}
