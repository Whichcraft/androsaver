package com.androsaver.visualizer.modes

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw
import kotlin.math.*

/**
 * WaterfallMode — Scrolling frequency waterfall with glow squares.
 * Port of Python class `GlowSquares`.
 */
class WaterfallMode : BaseMode() {

    override val name = "Waterfall"

    companion object {
        private const val ROWS = 100
        private const val COLS = 80
    }

    private var hue = 0f
    private lateinit var edges: IntArray
    private var cols = 0
    private lateinit var counts: FloatArray   // pre-computed bin widths
    private val buf = ArrayDeque<FloatArray>()   // newest at front (index 0)

    override fun reset() {
        hue = 0f
        buf.clear()
        // Log-spaced indices from 2 to 434 (85% of 512), COLS+1 = 81 entries, deduplicated.
        val raw = geomSpaceInt(2.0, 434.0, COLS + 1)
        edges  = raw.map { it.coerceIn(1, 511) }.distinct().sorted().toIntArray()
        cols   = edges.size - 1
        counts = FloatArray(cols) { i -> (edges[i + 1] - edges[i]).toFloat().coerceAtLeast(1f) }
    }

    override fun draw(draw: GLDraw, audio: AudioData, tick: Int) {
        val beat = audio.beat
        hue = (hue + 0.003f + beat * 0.015f) % 1f

        val fft = audio.fft
        val W = draw.W.toFloat()
        val H = draw.H.toFloat()

        // Single-pass accumulation into bins (matches np.add.reduceat)
        val row = FloatArray(cols)
        var bi = 0
        for (k in edges[0] until edges[cols]) {
            while (bi + 1 < cols && k >= edges[bi + 1]) bi++
            row[bi] += fft[k]
        }
        for (i in 0 until cols) row[i] /= counts[i]
        val maxVal = row.max().coerceAtLeast(1e-6f)
        for (i in 0 until cols) row[i] /= maxVal

        // Push to front, trim to ROWS
        buf.addFirst(row)
        while (buf.size > ROWS) buf.removeLast()

        val bw = maxOf(1f, W / cols)
        val bh = maxOf(4f, H / ROWS)
        val flash = beat * 0.35f

        for (ri in buf.indices) {
            val r = buf[ri]
            val y = ri * bh
            if (y > H) break
            val age = ri.toFloat() / maxOf(buf.size, 1)
            val rowBoost = flash * maxOf(0f, 1f - age * 6f)
            for (ci in 0 until cols) {
                val e = r[ci]
                val x = ci * bw
                val colHue = (hue + ci.toFloat() / cols * 0.75f) % 1f
                val lit = ((1f - age * 0.7f) * (0.15f + e * 0.85f) + rowBoost).coerceIn(0.04f, 0.95f)
                val color = GLDraw.hsl(colHue, 1f, lit)
                draw.rect(x, y, bw - 1f, bh - 1f, color[0], color[1], color[2], 1f)
            }
        }
    }

    private fun geomSpaceInt(start: Double, end: Double, num: Int): List<Int> {
        if (num <= 1) return listOf(start.roundToInt())
        val logStart = ln(start)
        val logEnd = ln(end)
        return (0 until num).map { i ->
            exp(logStart + i.toDouble() / (num - 1) * (logEnd - logStart)).roundToInt()
        }
    }

    private fun Double.roundToInt(): Int = kotlin.math.round(this).toInt()
}
