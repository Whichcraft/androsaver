package com.androsaver.visualizer.modes

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw
import kotlin.math.*

/**
 * BarsMode — Classic spectrum bars with peak markers and a waveform overlay.
 * Port of Python class `Bars`.
 */
class BarsMode : BaseMode() {

    override val name = "Spectrum"

    private var hue = 0f
    private lateinit var edges: IntArray
    private var n = 0
    private lateinit var peaks: FloatArray
    private lateinit var counts: FloatArray   // pre-computed bin widths
    private lateinit var display: FloatArray  // smoothed heights shown on screen

    override fun reset() {
        hue = 0f
        // Log-spaced indices from 2 to 434 (85% of 512), 81 entries, deduplicated.
        val raw = geomSpaceInt(2.0, 434.0, 81)
        edges = raw.map { it.coerceIn(1, 511) }.distinct().sorted().toIntArray()
        n = edges.size - 1
        peaks   = FloatArray(n)
        counts  = FloatArray(n) { i -> (edges[i + 1] - edges[i]).toFloat().coerceAtLeast(1f) }
        display = FloatArray(n)
    }

    override fun draw(draw: GLDraw, audio: AudioData, tick: Int) {
        hue = (hue + 0.003f) % 1f

        val fft = audio.fft
        val beat = audio.beat
        val waveform = audio.waveform
        val W = draw.W.toFloat()
        val H = draw.H.toFloat()

        // Single-pass accumulation into bars (matches np.add.reduceat)
        val heights = FloatArray(n)
        var bi = 0
        for (k in edges[0] until edges[n]) {
            while (bi + 1 < n && k >= edges[bi + 1]) bi++
            heights[bi] += fft[k]
        }
        for (i in 0 until n) heights[i] /= counts[i]
        val maxH = heights.max().coerceAtLeast(1e-6f)
        for (i in 0 until n) heights[i] /= maxH

        // Lerp display heights: base speed 0.25, scales up with beat (beat is × beatGain)
        val lerpSpeed = (0.25f + beat * 0.55f).coerceIn(0.05f, 1.0f)
        for (i in 0 until n) {
            display[i] = display[i] + (heights[i] - display[i]) * lerpSpeed
        }

        // Update peaks from smoothed display heights
        for (i in 0 until n) {
            peaks[i] = maxOf(peaks[i] * 0.94f, display[i])
        }

        val barW = W / n

        // Draw bars and peak markers
        for (i in 0 until n) {
            val h = display[i]
            val barH = h * H * 0.82f
            val peak = peaks[i] * H * 0.82f
            val x = i * barW
            val hue = (this.hue + i.toFloat() / n) % 1f

            // Bar fill: lightness 0.38 + h*0.42
            val barColor = GLDraw.hsl(hue, 1f, (0.38f + h * 0.42f).coerceIn(0f, 1f))
            draw.rect(
                x, H - barH,
                barW - 2f, barH,
                barColor[0], barColor[1], barColor[2], 1f
            )

            // Peak marker: lightness 0.9
            val peakColor = GLDraw.hsl(hue, 1f, 0.9f)
            draw.rect(
                x, H - peak - 3f,
                barW - 2f, 3f,
                peakColor[0], peakColor[1], peakColor[2], 1f
            )
        }

        // Waveform overlay
        val wLen = waveform.size
        val step = maxOf(1, wLen / draw.W)
        val pts = mutableListOf<Float>()
        val count = wLen / step
        for (i in 0 until count) {
            val sx = i.toFloat() * W / count
            val sy = H / 2f + waveform[i * step] * (120f * H / 720f)
            pts.add(sx)
            pts.add(sy)
        }
        if (pts.size >= 4) {
            val waveColor = GLDraw.hsl(this.hue, 1f, 0.75f)
            draw.lineStrip(pts.toFloatArray(), waveColor[0], waveColor[1], waveColor[2], 1f)
        }
    }

    // Generate n log-spaced doubles from start to end, round to Int
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
