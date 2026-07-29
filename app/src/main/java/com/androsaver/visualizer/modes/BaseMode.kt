package com.androsaver.visualizer.modes

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw

/** Common interface for all visualizer modes. */
abstract class BaseMode {

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

    /**
     * Keep high-energy perspective motion comfortable on smaller displays.
     * TV-sized surfaces retain full motion while smaller surfaces are damped.
     */
    protected fun displayMotionScale(draw: GLDraw): Float {
        val shortest = minOf(draw.W.coerceAtLeast(1), draw.H.coerceAtLeast(1))
        return (shortest / 1080f).coerceIn(0.55f, 1f)
    }
}
