package com.androsaver.visualizer.modes

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw
import kotlin.math.*

/**
 * Clifford — strange attractor with audio-morphing parameters.
 *
 * 8 000 parallel walkers are iterated through the Clifford map each frame:
 *   x' = sin(a·y) - cos(b·x)
 *   y' = sin(c·x) - cos(d·y)
 *
 * Parameters (a, b, c, d) drift slowly at rest and snap to new values on
 * strong beats.  Particles are drawn with additive blending.
 *
 *   Bass   → parameter morph speed + point brightness
 *   Mid    → morph amplitude
 *   Treble → colour cycle speed
 *   Beat   → jump to new attractor parameters
 *
 * Port of psysuals `effects/clifford.py` (v3.8.0).
 * Walker count reduced from 40 000 to 8 000 for Android performance.
 * TRAIL_ALPHA=0 → fadeBlack(14f/255f) approximates (228,225,232)/255 MULT decay.
 */
class CliffordMode : BaseMode() {

    override val name = "Clifford"

    private companion object {
        const val N = 8_000
        val TAU = (2.0 * PI).toFloat()
    }

    private val xs = FloatArray(N)
    private val ys = FloatArray(N)

    private var a = -1.4f; private var b = 1.6f
    private var c =  1.0f; private var d = 0.7f
    private var ta = -1.4f; private var tb = 1.6f
    private var tc =  1.0f; private var td = 0.7f

    private var hue      = 0f
    private var beatPrev = 0f

    override fun reset() {
        a = -1.4f; b = 1.6f; c = 1.0f; d = 0.7f
        ta = a; tb = b; tc = c; td = d
        for (i in 0 until N) {
            xs[i] = (Math.random().toFloat() * 4f - 2f)
            ys[i] = (Math.random().toFloat() * 4f - 2f)
        }
        hue = Math.random().toFloat(); beatPrev = 0f
    }

    private fun newParams() {
        ta = Math.random().toFloat() * 4f - 2f
        tb = Math.random().toFloat() * 4f - 2f
        tc = Math.random().toFloat() * 4f - 2f
        td = Math.random().toFloat() * 4f - 2f
    }

    override fun draw(draw: GLDraw, audio: AudioData, tick: Int) {
        val W    = draw.W.toFloat(); val H = draw.H.toFloat()
        val bass = audio.beat
        val mid  = audio.mid
        val high = audio.treble

        if (tick == 0) reset()

        hue = (hue + 0.002f + high * 0.003f) % 1f

        if (bass > 0.8f && beatPrev <= 0.8f) newParams()
        beatPrev = bass

        val spd = 0.008f + mid * 0.012f + bass * 0.006f
        a += (ta - a) * spd; b += (tb - b) * spd
        c += (tc - c) * spd; d += (td - d) * spd

        // Iterate Clifford map for all walkers
        for (i in 0 until N) {
            val nx = sin(a * ys[i]) - cos(b * xs[i])
            val ny = sin(c * xs[i]) - cos(d * ys[i])
            xs[i] = nx; ys[i] = ny
        }

        draw.fadeBlack(14f / 255f)

        val scale  = minOf(W, H) * 0.24f
        val bright = 0.40f + bass * 0.30f

        draw.setAdditiveBlend()
        for (i in 0 until N) {
            val px = xs[i] * scale + W * 0.5f
            val py = ys[i] * scale + H * 0.5f
            if (px < 0f || px >= W || py < 0f || py >= H) continue

            val ang  = (atan2(ys[i], xs[i]) / TAU + 0.5f)
            val h    = (hue + ang * 0.7f) % 1f
            val c    = GLDraw.hsl(h, l = bright * 0.5f)
            draw.circle(px, py, 1.5f, c[0], c[1], c[2], 0.6f, filled = true, segments = 4)
        }
        draw.setNormalBlend()
    }
}
