package com.androsaver.visualizer.modes

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw
import kotlin.math.*

/**
 * Coral — bioluminescent fractal coral growing from the bottom edge.
 *
 * Iterative branch-tip growth produces organic upward-sweeping structures
 * with depth-tapered thickness and hue.  Beat fires a bioluminescent pulse
 * that illuminates the whole colony at once.
 *
 *   Bass   → growth speed + stem length
 *   Mid    → branching angle spread
 *   Treble → tip respawn rate
 *   Beat   → bioluminescent bloom pulse
 *
 * Port of psysuals `effects/coral.py` (v3.8.0).
 * TRAIL_ALPHA=6 → fadeBlack(6f/255f).
 */
class CoralMode : BaseMode() {

    override val name = "Coral"

    private companion object {
        const val MAX_SEGS = 600
        const val MAX_TIPS = 200
    }

    private data class Seg(val x1: Float, val y1: Float, val x2: Float, val y2: Float,
                           var age: Int, val maxAge: Int, val depth: Int, val hueOff: Float)

    private data class Tip(val x: Float, val y: Float, val angle: Float,
                           val depth: Int, val hueOff: Float)

    private val segs = ArrayList<Seg>(MAX_SEGS)
    private val tips = ArrayList<Tip>(MAX_TIPS)
    private var hue      = 0.50f
    private var pulse    = 0f
    private var beatPrev = 0f

    override fun reset() {
        segs.clear(); tips.clear()
        hue = 0.50f; pulse = 0f; beatPrev = 0f
    }

    private fun reseed(W: Float, H: Float) {
        tips.clear()
        val n = 5
        for (i in 0 until n) {
            val x = W * (0.10f + 0.80f * i.toFloat() / (n - 1)) +
                    (Math.random().toFloat() * 2f - 1f) * W * 0.04f
            tips.add(Tip(x, H * 0.95f, -PI.toFloat() / 2f +
                    (Math.random().toFloat() * 2f - 1f) * 0.15f,
                    0, i.toFloat() / n * 0.30f))
        }
    }

    override fun draw(draw: GLDraw, audio: AudioData, tick: Int) {
        val W = draw.W.toFloat(); val H = draw.H.toFloat()
        val bass = audio.beat
        val mid  = audio.mid
        val high = audio.treble

        if (tick == 0) reseed(W, H)

        hue = (hue + 0.002f + mid * 0.002f) % 1f

        if (bass > 0.65f && beatPrev <= 0.65f) pulse = 1f
        beatPrev = bass
        pulse    = maxOf(0f, pulse - 0.04f)

        val speed   = 8f + bass * 25f
        val spread  = 0.25f + mid * 0.35f
        val branchP = 0.10f + high * 0.08f
        val segLife = 200 + (bass * 100).toInt()

        val nextTips = ArrayList<Tip>(MAX_TIPS)
        for ((tx, ty, ta, depth, hOff) in tips) {
            if (depth >= 10 || ty < H * 0.05f) continue
            val ta2    = ta + (Math.random().toFloat() * 2f - 1f) * spread * 0.20f
            val length = maxOf(3f, speed * (1f - depth * 0.07f) *
                    (0.70f + Math.random().toFloat() * 0.60f))
            val ex = tx + cos(ta2) * length
            val ey = ty + sin(ta2) * length
            if (segs.size < MAX_SEGS) {
                val maxAge = maxOf(30, segLife - depth * 15)
                segs.add(Seg(tx, ty, ex, ey, 0, maxAge, depth, hOff))
            }
            if (nextTips.size < MAX_TIPS)
                nextTips.add(Tip(ex, ey, ta2, depth + 1, hOff))
            if (Math.random() < branchP && depth < 8 && nextTips.size < MAX_TIPS) {
                val sign = if (Math.random() < 0.5) -1f else 1f
                val bAng = ta2 + sign * (0.30f + Math.random().toFloat() * spread)
                nextTips.add(Tip(ex, ey, bAng, depth + 1, (hOff + 0.10f) % 1f))
            }
        }
        tips.clear(); tips.addAll(nextTips)
        if (tips.isEmpty()) reseed(W, H)

        segs.removeAll { it.age >= it.maxAge }
        for (s in segs) s.age++

        draw.fadeBlack(6f / 255f)

        draw.setAdditiveBlend()
        for ((x1, y1, x2, y2, age, maxAge, _, hOff) in segs) {
            val fade   = maxOf(0f, 1f - age.toFloat() / maxAge)
            val h      = (hue + hOff) % 1f
            val bright = (0.18f + bass * 0.12f + pulse * 0.35f) * fade
            // Dark thick outer stroke
            val cdark = GLDraw.hsl(h, l = bright * 0.25f)
            draw.line(x1, y1, x2, y2, cdark[0], cdark[1], cdark[2], cdark[3])
            // Bright thin inner stroke
            val cbright = GLDraw.hsl(h, l = bright)
            draw.line(x1, y1, x2, y2, cbright[0], cbright[1], cbright[2], cbright[3])
        }
        draw.setNormalBlend()
    }
}
