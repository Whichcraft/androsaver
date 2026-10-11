package com.androsaver.visualizer.modes

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw
import com.androsaver.visualizer.VisualizerRenderTuning
import kotlin.math.*

/** Magnetic potential field port of psysuals `effects/ferrofluid.py`. */
class FerrofluidMode : BaseMode() {
    override val name = "Ferrofluid"

    private companion object {
        const val COLS = 128
        const val ROWS = 72
        const val POLES = 5
        const val CONTOURS = 7
    }

    private val field = FloatArray(COLS * ROWS)
    private val poleX = FloatArray(POLES)
    private val poleY = FloatArray(POLES)
    private val poleStrength = FloatArray(POLES)
    private val crossings = FloatArray(8)
    private val color = FloatArray(4)
    private val rng = kotlin.random.Random(0xF3A0)
    private var hue = 0f
    private var beatPrevious = 0f
    private var invertFrames = 0

    init {
        for (i in 0 until POLES) {
            poleX[i] = rng.nextFloat() * 1.3f - 0.65f
            poleY[i] = rng.nextFloat() * 1.3f - 0.65f
            poleStrength[i] = 0.6f + rng.nextFloat() * 0.6f
        }
    }

    override fun reset() {
        field.fill(0f)
        hue = 0f
        beatPrevious = 0f
        invertFrames = 0
    }

    override fun draw(draw: GLDraw, audio: AudioData, tick: Int) {
        VisualizerRenderTuning.fadeTrail(draw)
        val bass = audio.beat.coerceIn(0f, 1.5f)
        val mid = audio.mid.coerceIn(0f, 4f)
        val high = audio.treble.coerceIn(0f, 4f)
        if (bass > 0.7f && beatPrevious <= 0.7f) invertFrames = 12
        beatPrevious = bass
        if (invertFrames > 0) invertFrames--
        val angle = tick * (0.006f + mid * 0.001f)
        val ca = cos(angle); val sa = sin(angle)
        hue = (hue + 0.001f + high * 0.0008f) % 1f

        for (y in 0 until ROWS) {
            val py = y / (ROWS - 1f) * 2f - 1f
            for (x in 0 until COLS) {
                val px = x / (COLS - 1f) * 2f - 1f
                var potential = 0f
                for (pole in 0 until POLES) {
                    val rx = poleX[pole] * ca - poleY[pole] * sa
                    val ry = poleX[pole] * sa + poleY[pole] * ca
                    val dx = px - rx
                    val dy = py - ry
                    val sign = if (invertFrames > 0 && pole == 0) -1f else 1f
                    potential += sign * poleStrength[pole] / sqrt(dx * dx + dy * dy + 0.0125f)
                }
                field[y * COLS + x] = potential.coerceIn(-8f, 8f)
            }
        }

        // The desktop effect colors the whole potential field, then its
        // contour bands emerge from the same magnetic surface.
        val cellW = draw.W.toFloat() / COLS
        val cellH = draw.H.toFloat() / ROWS
        for (y in 0 until ROWS) for (x in 0 until COLS) {
            val value = field[y * COLS + x]
            val bands = abs(sin(value * 6f * 0.45f))
            val lightness = (0.18f + bands * 0.58f + maxOf(value, 0f) * 0.025f).coerceIn(0f, 0.92f)
            GLDraw.hsl((hue + value * 0.035f + high * 0.04f) % 1f, 1f, lightness, 1f, color)
            draw.rect(x * cellW, y * cellH, cellW + 1f, cellH + 1f, color[0], color[1], color[2], 0.88f)
        }

        val scaleX = draw.W.toFloat() / (COLS - 1)
        val scaleY = draw.H.toFloat() / (ROWS - 1)
        for (contour in 0 until CONTOURS) {
            val level = -6f + contour * 2f
            GLDraw.hsl((hue + contour * 0.11f) % 1f, 1f, 0.48f + contour * 0.045f, 0.7f, color)
            for (y in 0 until ROWS - 1) for (x in 0 until COLS - 1) {
                val x0 = x * scaleX; val x1 = (x + 1) * scaleX
                val y0 = y * scaleY; val y1 = (y + 1) * scaleY
                val a = field[y * COLS + x]
                val b = field[y * COLS + x + 1]
                val c = field[(y + 1) * COLS + x + 1]
                val d = field[(y + 1) * COLS + x]
                var n = 0
                n = crossing(x0, y0, a, x1, y0, b, level, n)
                n = crossing(x1, y0, b, x1, y1, c, level, n)
                n = crossing(x1, y1, c, x0, y1, d, level, n)
                n = crossing(x0, y1, d, x0, y0, a, level, n)
                var i = 0
                while (i + 1 < n) {
                    draw.line(crossings[i * 2], crossings[i * 2 + 1],
                        crossings[(i + 1) * 2], crossings[(i + 1) * 2 + 1],
                        color[0], color[1], color[2], color[3])
                    i += 2
                }
            }
        }
    }

    private fun crossing(
        x0: Float, y0: Float, v0: Float,
        x1: Float, y1: Float, v1: Float,
        level: Float, count: Int
    ): Int {
        if ((v0 < level) == (v1 < level) || abs(v1 - v0) < 1e-6f || count >= 4) return count
        val t = ((level - v0) / (v1 - v0)).coerceIn(0f, 1f)
        crossings[count * 2] = x0 + (x1 - x0) * t
        crossings[count * 2 + 1] = y0 + (y1 - y0) * t
        return count + 1
    }
}
