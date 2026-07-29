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
    private var started = false

    init {
        setEGLContextClientVersion(2)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    /** Names of effects enabled for Auto/Random cycling. Empty set = all enabled. */
    var enabledModeNames: Set<String> = emptySet()

    private fun enabledIndices(): List<Int> {
        val names = enabledModeNames
        if (names.isEmpty()) return renderer.modes.indices.toList()
        return renderer.modes.indices.filter { renderer.modes[it].name in names }
    }

    fun startVisualizer() {
        if (started) return
        started = true
        audio.start()
        onResume()
    }

    fun stopVisualizer() {
        if (!started) return
        started = false
        onPause()
        audio.stop()
    }

    /** Switch to the next enabled mode (wraps around). */
    fun nextMode() {
        val enabled = enabledIndices()
        if (enabled.isEmpty()) return
        val cur = renderer.modeIndex
        renderer.modeIndex = enabled.firstOrNull { it > cur } ?: enabled.first()
    }

    /** Switch to the previous enabled mode (wraps around). */
    fun previousMode() {
        val enabled = enabledIndices()
        if (enabled.isEmpty()) return
        val cur = renderer.modeIndex
        renderer.modeIndex = enabled.lastOrNull { it < cur } ?: enabled.last()
    }

    /** Switch to a random enabled mode. */
    fun randomMode() {
        val enabled = enabledIndices()
        if (enabled.isEmpty()) return
        renderer.modeIndex = enabled.random()
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
