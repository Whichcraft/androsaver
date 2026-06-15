package com.androsaver.visualizer.modes

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw
import kotlin.math.*

/**
 * Aurora — Northern Lights curtains.
 * Port of psysuals Aurora (v2.9.0+).
 *
 * Five translucent sinusoidal ribbons undulate horizontally across the screen.
 *   Bass   → amplitude (curtains billow and swell)
 *   Treble → shimmer speed (phase velocity of all harmonics)
 *   Mid    → ribbon height / thickness
 *   Beat   → bloom flash + hue shift
 */
class AuroraMode : BaseMode() {

    override val name = "Aurora"

    private companion object {
        private const val TAU          = (Math.PI * 2).toFloat()
        private const val TRAIL_ALPHA  = 14f / 255f
        private const val N_RIBBONS    = 5
        private const val N_HARMS      = 3

        // Per ribbon: [y_frac, hue_off, k0, spd0, aw0, k1, spd1, aw1, k2, spd2, aw2]
        private val DEFS = arrayOf(
            floatArrayOf(0.20f, 0.00f,  1.0f, +0.50f, 1.00f,  2.3f, -0.85f, 0.55f,  5.1f, +0.30f, 0.25f),
            floatArrayOf(0.35f, 0.18f,  0.8f, -0.42f, 1.00f,  1.9f, +0.72f, 0.55f,  4.3f, -0.28f, 0.25f),
            floatArrayOf(0.50f, 0.36f,  1.2f, +0.58f, 1.00f,  2.7f, -0.60f, 0.55f,  3.8f, +0.38f, 0.25f),
            floatArrayOf(0.65f, 0.55f,  0.9f, -0.48f, 1.00f,  2.1f, +0.65f, 0.55f,  4.7f, -0.33f, 0.25f),
            floatArrayOf(0.80f, 0.74f,  1.1f, +0.45f, 1.00f,  2.5f, -0.78f, 0.55f,  3.5f, +0.35f, 0.25f),
        )
    }

    // Phases[ribbon][harmonic] — advance each frame
    private val phases = Array(N_RIBBONS) { FloatArray(N_HARMS) }
    // Wave-number per ribbon per harmonic — recomputed when W changes
    private val ks     = Array(N_RIBBONS) { FloatArray(N_HARMS) }

    private var hue      = 0.42f
    private var bloom    = 0f
    private var beatPrev = 0f

    // Cached screen width; triggers ks / xs recompute when changed
    private var cachedW  = 0
    private var xs       = FloatArray(0)

    // Saved top-edge points per ribbon for the bright edge line pass
    private val edgePts  = Array(N_RIBBONS) { FloatArray(0) }

    private fun initGeom(W: Int) {
        if (W == cachedW) return
        cachedW = W
        val Wf   = W.toFloat()
        val step = maxOf(6f, Wf / 160f)
        val pts  = ArrayList<Float>((W / step.toInt()) + 2)
        var x = 0f
        while (x < Wf) { pts.add(x); x += step }
        xs = pts.toFloatArray()
        val kUnit = TAU / Wf
        for (ri in 0 until N_RIBBONS) {
            val d = DEFS[ri]
            for (j in 0 until N_HARMS) {
                ks[ri][j] = kUnit * d[2 + j * 3]   // k_mult at offsets 2, 5, 8
            }
        }
    }

    override fun reset() {
        hue      = 0.42f
        bloom    = 0f
        beatPrev = 0f
        cachedW  = 0
        for (ri in 0 until N_RIBBONS) phases[ri].fill(0f)
    }

    override fun draw(draw: GLDraw, audio: AudioData, tick: Int) {
        val W  = draw.W.toFloat()
        val H  = draw.H.toFloat()
        val fft    = audio.fft
        val bass   = fft.meanSlice(0, 6)
        val mid    = fft.meanSlice(6, 30)
        val treble = fft.meanSlice(100, 256)
        val beat   = audio.beat

        draw.fadeBlack(TRAIL_ALPHA)
        initGeom(draw.W)

        hue = (hue + 0.003f + mid * 0.002f) % 1f

        if (beat > 0.8f && beatPrev <= 0.8f) {
            bloom = 1f
            hue   = (hue + 0.10f) % 1f
        }
        beatPrev = beat
        bloom    = maxOf(0f, bloom - 0.03f)

        val spd = 1f + treble * 4f + beat * 1.5f
        val amp = H * (0.04f + bass * 0.12f + bloom * 0.07f)
        val rh  = H * (0.09f + mid  * 0.05f)
        val pad = maxOf(2f, rh * 0.5f)

        val xsArr = xs
        val n     = xsArr.size

        draw.setAdditiveBlend()

        for (ri in 0 until N_RIBBONS) {
            val d     = DEFS[ri]
            val yFrac = d[0];  val hOff = d[1]
            val ph    = phases[ri]
            val kArr  = ks[ri]
            val ribbonHue = (hue + hOff) % 1f

            // Sum N_HARMS harmonics into displacement
            val wave = FloatArray(n)
            var totW = 0f
            for (j in 0 until N_HARMS) {
                val spdJ = d[3 + j * 3]   // speed at offsets 3, 6, 9
                val aw   = d[4 + j * 3]   // amp-weight at offsets 4, 7, 10
                ph[j] = (ph[j] + spdJ * spd * 0.016f) % TAU
                val phase = ph[j]
                val k     = kArr[j]
                for (i in 0 until n) wave[i] += sin(xsArr[i] * k + phase) * aw
                totW += aw
            }

            val wScale = amp / totW
            val cy     = yFrac * H
            val minY   = -H * 0.6f;  val maxY = H * 1.6f

            val yTop = FloatArray(n) { i -> (cy + wave[i] * wScale).coerceIn(minY, maxY) }
            val yBot = FloatArray(n) { i -> (cy + wave[i] * wScale + rh).coerceIn(minY, maxY) }

            // Core polygon: top L→R then bottom R→L
            val polyPts = FloatArray(n * 4)
            for (i in 0 until n) {
                polyPts[i * 2]     = xsArr[i]
                polyPts[i * 2 + 1] = yTop[i]
            }
            for (i in 0 until n) {
                val ri2 = n - 1 - i
                polyPts[(n + i) * 2]     = xsArr[ri2]
                polyPts[(n + i) * 2 + 1] = yBot[ri2]
            }

            // Glow polygon: padded outward on both edges
            val glowPts = FloatArray(n * 4)
            for (i in 0 until n) {
                glowPts[i * 2]     = xsArr[i]
                glowPts[i * 2 + 1] = yTop[i] - pad
            }
            for (i in 0 until n) {
                val ri2 = n - 1 - i
                glowPts[(n + i) * 2]     = xsArr[ri2]
                glowPts[(n + i) * 2 + 1] = yBot[ri2] + pad
            }

            val col = GLDraw.hsl(ribbonHue, s = 1f, l = 0.5f)
            val r = col[0]; val g = col[1]; val b = col[2]

            val gi = 0.10f + bloom * 0.08f
            draw.polygon(glowPts, r * gi, g * gi, b * gi, 1f, filled = true)

            val ci = 0.28f + bloom * 0.22f + bass * 0.08f
            draw.polygon(polyPts, r * ci, g * ci, b * ci, 1f, filled = true)

            // Save top edge for bright line pass after blend switch
            val ep = FloatArray(n * 2)
            for (i in 0 until n) { ep[i * 2] = xsArr[i]; ep[i * 2 + 1] = yTop[i] }
            edgePts[ri] = ep
        }

        draw.setNormalBlend()

        // Sharp bright edge lines drawn on top in normal blend
        for (ri in 0 until N_RIBBONS) {
            val hOff      = DEFS[ri][1]
            val ribbonHue = (hue + hOff) % 1f
            val bl        = minOf(0.80f + bloom * 0.18f, 0.98f)
            val ec        = GLDraw.hsl(ribbonHue, l = bl)
            draw.lineStrip(edgePts[ri], ec[0], ec[1], ec[2], 1f)
        }
    }
}
