package com.androsaver.visualizer.modes

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw
import com.androsaver.visualizer.VisualizerRenderTuning
import kotlin.math.*

internal fun psysualsViewportScale(draw: GLDraw): Float =
    VisualizerRenderTuning.viewportScale(draw.W, draw.H)

/** Shared bounded scalar-field implementation for the psysuals field effects. */
abstract class PsysualsFieldMode : BaseMode() {
    protected val cols = 24
    protected val rows = 16
    protected val values = FloatArray(cols * rows)
    protected var phase = 0f
    protected var hue = 0f

    protected fun clearTrail(draw: GLDraw) = VisualizerRenderTuning.fadeTrail(draw)

    /** Keep field frequencies proportional on wide and portrait viewports. */
    protected fun fieldX(x: Int, draw: GLDraw): Float =
        (x / (cols - 1f) - 0.5f) * (draw.W.toFloat() / draw.H.coerceAtLeast(1))

    protected fun fieldY(y: Int): Float = y / (rows - 1f) - 0.5f

    protected fun paint(draw: GLDraw, audio: AudioData, gain: Float = 1f) {
        val renderCols = VisualizerRenderTuning.fieldColumns(draw.W, draw.H)
        val renderRows = VisualizerRenderTuning.fieldRows(draw.W, draw.H)
        val maxX = (cols - 1).coerceAtLeast(1)
        val maxY = (rows - 1).coerceAtLeast(1)
        val cw = draw.W.toFloat() / renderCols
        val ch = draw.H.toFloat() / renderRows
        for (y in 0 until renderRows) for (x in 0 until renderCols) {
            val sourceX = x.toFloat() / (renderCols - 1).coerceAtLeast(1) * maxX
            val sourceY = y.toFloat() / (renderRows - 1).coerceAtLeast(1) * maxY
            val x0 = sourceX.toInt().coerceIn(0, maxX)
            val y0 = sourceY.toInt().coerceIn(0, maxY)
            val x1 = (x0 + 1).coerceAtMost(maxX)
            val y1 = (y0 + 1).coerceAtMost(maxY)
            val tx = sourceX - x0
            val ty = sourceY - y0
            val top = values[y0 * cols + x0] * (1f - tx) + values[y0 * cols + x1] * tx
            val bottom = values[y1 * cols + x0] * (1f - tx) + values[y1 * cols + x1] * tx
            val v = (top * (1f - ty) + bottom * ty).coerceIn(0f, 1f)
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
