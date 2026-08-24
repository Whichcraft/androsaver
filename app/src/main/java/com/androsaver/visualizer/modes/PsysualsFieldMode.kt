package com.androsaver.visualizer.modes

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw
import kotlin.math.*

internal const val PSYSUALS_TRAIL_FADE = 0.20f

internal fun fadePsysualsTrail(draw: GLDraw) = draw.fadeBlack(PSYSUALS_TRAIL_FADE)

/** Scale fixed-size primitives against a 1080-pixel TV short edge. */
internal fun psysualsViewportScale(draw: GLDraw): Float =
    (minOf(draw.W, draw.H).coerceAtLeast(1) / 1080f).coerceIn(0.5f, 2f)

/** Shared bounded scalar-field implementation for the psysuals field effects. */
abstract class PsysualsFieldMode : BaseMode() {
    protected val cols = 24
    protected val rows = 16
    protected val values = FloatArray(cols * rows)
    protected var phase = 0f
    protected var hue = 0f

    protected fun clearTrail(draw: GLDraw) = draw.fadeBlack(PSYSUALS_TRAIL_FADE)

    /** Keep field frequencies proportional on wide and portrait viewports. */
    protected fun fieldX(x: Int, draw: GLDraw): Float =
        (x / (cols - 1f) - 0.5f) * (draw.W.toFloat() / draw.H.coerceAtLeast(1))

    protected fun fieldY(y: Int): Float = y / (rows - 1f) - 0.5f

    protected fun paint(draw: GLDraw, audio: AudioData, gain: Float = 1f) {
        val cw = draw.W.toFloat() / cols
        val ch = draw.H.toFloat() / rows
        for (y in 0 until rows) for (x in 0 until cols) {
            val v = values[y * cols + x].coerceIn(0f, 1f)
            val c = GLDraw.hsl((hue + v * 0.62f) % 1f, 1f,
                (0.12f + v * 0.48f + audio.beat * 0.12f).coerceIn(0f, 0.92f))
            draw.rect(x * cw, y * ch, cw + 1f, ch + 1f,
                c[0] * gain, c[1] * gain, c[2] * gain, 0.92f)
        }
    }

    override fun reset() {
        values.fill(0f)
        phase = 0f
        hue = 0f
    }
}
