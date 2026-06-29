package com.androsaver.visualizer.modes

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw
import kotlin.math.*

/**
 * Clifford — strange attractor with audio-morphing parameters.
 *
 * 8 000 parallel walkers are iterated through the Clifford map each frame.
 * Updated through v3.11.0 with preset-driven framing and multi-pass density accumulation.
 *
 * Port of psysuals `effects/clifford.py` (v3.11.0).
 */
class CliffordMode : BaseMode() {

    override val name = "Clifford"

    private companion object {
        const val N = 8_000
        const val STEPS = 4
        val TAU = (2.0 * Math.PI).toFloat()
        val PI_F = Math.PI.toFloat()

        val PRESETS = arrayOf(
            floatArrayOf(-1.40f, 1.60f, 1.00f, 0.70f),
            floatArrayOf(-1.70f, 1.80f, -1.90f, -0.40f),
            floatArrayOf(1.30f, -1.70f, 1.80f, 1.30f),
            floatArrayOf(-1.20f, 1.90f, -0.90f, 1.70f),
            floatArrayOf(1.60f, 1.10f, -1.50f, 0.90f)
        )
    }

    private val xs = FloatArray(N)
    private val ys = FloatArray(N)
    private val allX = FloatArray(N * STEPS)
    private val allY = FloatArray(N * STEPS)
    private val sampleX = FloatArray((N * STEPS + 7) / 8)
    private val sampleY = FloatArray((N * STEPS + 7) / 8)

    private var a = -1.4f; private var b = 1.6f
    private var c =  1.0f; private var d = 0.7f
    private var ta = -1.4f; private var tb = 1.6f
    private var tc =  1.0f; private var td = 0.7f

    private var xmin = -2.0f; private var xmax = 2.0f
    private var ymin = -2.0f; private var ymax = 2.0f

    private var hue      = 0f
    private var beatPrev = 0f

    override fun reset() {
        a = -1.4f; b = 1.6f; c = 1.0f; d = 0.7f
        xmin = -2.0f; xmax = 2.0f; ymin = -2.0f; ymax = 2.0f
        newParams(force = true)
        a = ta; b = tb; c = tc; d = td
        for (i in 0 until N) {
            xs[i] = (Math.random().toFloat() * 3.2f - 1.6f)
            ys[i] = (Math.random().toFloat() * 3.2f - 1.6f)
        }
        hue = Math.random().toFloat(); beatPrev = 0f
    }

    private fun newParams(force: Boolean = false) {
        val base = PRESETS.random()
        val jitter = if (force) 0f else 0.18f
        ta = base[0] + (Math.random().toFloat() * 2f - 1f) * jitter
        tb = base[1] + (Math.random().toFloat() * 2f - 1f) * jitter
        tc = base[2] + (Math.random().toFloat() * 2f - 1f) * jitter
        td = base[3] + (Math.random().toFloat() * 2f - 1f) * jitter
    }

    override fun draw(draw: GLDraw, audio: AudioData, tick: Int) {
        val W    = draw.W.toFloat(); val H = draw.H.toFloat()
        val bass = audio.beat
        val mid  = audio.mid
        val high = audio.treble

        if (tick == 0) reset()

        hue = (hue + 0.0025f + high * 0.003f) % 1f

        if (bass > 0.8f && beatPrev <= 0.8f) newParams()
        beatPrev = bass

        val spd = 0.008f + mid * 0.012f + bass * 0.006f
        a += (ta - a) * spd; b += (tb - b) * spd
        c += (tc - c) * spd; d += (td - d) * spd

        // Check if any points are NaN or infinite
        var hasInvalid = false
        for (i in 0 until N) {
            if (!xs[i].isFinite() || !ys[i].isFinite()) {
                hasInvalid = true
                break
            }
        }
        if (hasInvalid) {
            reset()
            return
        }

        draw.fadeBlack(18f / 255f)

        draw.setAdditiveBlend()
        // Run 4 steps per frame and retain samples for smoother framing.
        for (step in 0 until STEPS) {
            val base = step * N
            for (i in 0 until N) {
                val nx = sin(a * ys[i]) - cos(b * xs[i])
                val ny = sin(c * xs[i]) - cos(d * ys[i])
                xs[i] = nx; ys[i] = ny
                allX[base + i] = nx
                allY[base + i] = ny
            }
        }

        val sampleCount = sampleX.size
        for (si in 0 until sampleCount) {
            val src = si * 8
            sampleX[si] = allX[src]
            sampleY[si] = allY[src]
        }
        sampleX.sort()
        sampleY.sort()
        val loIdx = (sampleCount * 0.01f).toInt().coerceIn(0, sampleCount - 1)
        val hiIdx = (sampleCount * 0.99f).toInt().coerceIn(0, sampleCount - 1)
        val xminVal = sampleX[loIdx]
        val xmaxVal = sampleX[hiIdx]
        val yminVal = sampleY[loIdx]
        val ymaxVal = sampleY[hiIdx]

        if (xmaxVal - xminVal < 0.10f || ymaxVal - yminVal < 0.10f) {
            reset()
            draw.setNormalBlend()
            return
        }

        xmin += (xminVal - xmin) * 0.12f
        xmax += (xmaxVal - xmax) * 0.12f
        ymin += (yminVal - ymin) * 0.12f
        ymax += (ymaxVal - ymax) * 0.12f

        val xSpan = maxOf(0.01f, xmax - xmin)
        val ySpan = maxOf(0.01f, ymax - ymin)

        for (i in allX.indices) {
            val px = (allX[i] - xmin) / xSpan * W
            val py = (allY[i] - ymin) / ySpan * H
            if (px >= 0f && px < W && py >= 0f && py < H) {
                val ang = (atan2(allY[i], allX[i]) / (2f * PI_F) + 0.5f)
                val rad = (sqrt(allX[i] * allX[i] + allY[i] * allY[i]) * 0.18f).coerceIn(0f, 1f)
                val h = (hue + ang * 0.55f + rad * 0.20f) % 1f
                val bright = 0.32f + bass * 0.20f + (1f - rad) * 0.18f
                val cArr = GLDraw.hsl(h, l = bright)
                draw.particle(px, py, 1.5f, cArr[0], cArr[1], cArr[2], 0.6f)
            }
        }
        draw.setNormalBlend()
    }
}
