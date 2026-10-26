package com.androsaver.visualizer.modes

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw

/** Common interface for all visualizer modes. */
abstract class BaseMode {

    protected val dynamics = com.androsaver.visualizer.EffectDynamics()
    internal fun resetDynamics() { dynamics.reset() }
    protected fun prepare(draw: GLDraw, audio: AudioData): com.androsaver.visualizer.EffectDynamics {
        dynamics.update(audio, draw.W)
        return dynamics
    }
    protected fun fitViewport(points: FloatArray, width: Int, height: Int, count: Int = points.size / 2) {
        var ex = 1f; var ey = 1f
        for (i in 0 until count) { ex = maxOf(ex, kotlin.math.abs(points[i*2]-width*.5f)); ey = maxOf(ey, kotlin.math.abs(points[i*2+1]-height*.5f)) }
        val fit = minOf(1f, width*.46f/ex, height*.46f/ey)
        for (i in 0 until count) { points[i*2] = width*.5f+(points[i*2]-width*.5f)*fit; points[i*2+1] = height*.5f+(points[i*2+1]-height*.5f)*fit }
    }

    /** Human-readable name shown in settings. */
    abstract val name: String

    /**
     * Called once when this mode becomes active (or when the GL surface is (re)created).
     * Use this to initialise per-mode state.
     */
    open fun reset() {}

    /**
     * Called after a new EGL context is created. Modes that own GL handles must
     * invalidate them here because numeric handles from the old context are no
     * longer usable.
     */
    open fun onSurfaceCreated() {
        reset()
    }

    /**
     * Draw one frame.
     *
     * @param draw  GL drawing utilities; coordinate space is actual screen pixels.
     * @param audio Current audio snapshot (waveform, FFT, beat energy).
     * @param tick  Frame counter (increments by 1 each frame).
     */
    abstract fun draw(draw: GLDraw, audio: AudioData, tick: Int)

    // ── Convenience: mean of an FFT slice ─────────────────────────────────────

    protected fun FloatArray.meanSlice(from: Int, to: Int): Float {
        if (size == 0) return 0f
        val lo = from.coerceIn(0, size - 1)
        val hi = (to).coerceIn(lo + 1, size)
        var sum = 0f
        for (i in lo until hi) sum += this[i]
        return sum / (hi - lo)
    }

}
