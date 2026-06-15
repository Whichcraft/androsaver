package com.androsaver.visualizer.modes

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw
import kotlin.math.*

/**
 * Mycelium — spreading fungal hyphal network.
 *
 * Active tips grow outward leaving decaying filament segments behind.
 * New branches sprout probabilistically; beat fires a central bloom burst.
 *
 *   Bass   → growth speed + filament reach
 *   Mid    → branching probability + angle spread
 *   Treble → tip respawn rate
 *   Beat   → explosive central bloom
 *
 * Port of psysuals `effects/mycelium.py` (v3.8.0).
 * TRAIL_ALPHA=8 → fadeBlack(8f/255f).
 * Double-draw segments (dark wide + bright thin) matches psysuals double pygame.draw.line.
 */
class MyceliumMode : BaseMode() {

    override val name = "Mycelium"

    private companion object {
        const val MAX_SEGS = 500
        const val MAX_TIPS = 150
    }

    // Segment: x1, y1, x2, y2, age, maxAge, hueOff
    private data class Seg(val x1: Float, val y1: Float, val x2: Float, val y2: Float,
                           var age: Int, val maxAge: Int, val hueOff: Float)

    // Tip: x, y, angle, depth, hueOff
    private data class Tip(val x: Float, val y: Float, val angle: Float,
                           val depth: Int, val hueOff: Float)

    private val segs = ArrayList<Seg>(MAX_SEGS)
    private val tips = ArrayList<Tip>(MAX_TIPS)
    private var hue  = 0f

    override fun reset() {
        segs.clear(); tips.clear()
        hue = Math.random().toFloat()
    }

    private fun reseed(cx: Float, cy: Float) {
        tips.clear()
        for (i in 0 until 7)
            tips.add(Tip(cx, cy, i / 7f * TAU, 0, i / 7f * 0.4f))
    }

    override fun draw(draw: GLDraw, audio: AudioData, tick: Int) {
        val W = draw.W.toFloat(); val H = draw.H.toFloat()
        val cx = W / 2f; val cy = H / 2f
        val bass = audio.beat
        val mid  = audio.mid
        val high = audio.treble

        if (tick == 0) reseed(cx, cy)

        hue = (hue + 0.003f + mid * 0.002f) % 1f

        // Beat bloom from center
        if (bass > 0.65f && tips.size < MAX_TIPS) {
            val count = (3 + bass * 5).toInt()
            repeat(count) {
                val angle = Math.random().toFloat() * TAU
                tips.add(Tip(cx, cy, angle, 0, Math.random().toFloat() * 0.5f))
            }
        }

        val speed   = 12f + bass * 35f
        val spread  = 0.35f + mid * 0.45f
        val branchP = 0.06f + high * 0.10f + mid * 0.04f
        val segLife = 80 + (bass * 40).toInt()

        val nextTips = ArrayList<Tip>(MAX_TIPS)
        for ((tx, ty, ta, depth, hOff) in tips) {
            if (depth >= 14) continue
            val ta2 = ta + (Math.random().toFloat() * 2f - 1f) * spread * 0.25f
            val length = speed * (0.8f + Math.random().toFloat() * 0.8f)
            val ex = tx + cos(ta2) * length
            val ey = ty + sin(ta2) * length
            if (segs.size < MAX_SEGS) {
                val maxAge = segLife + (-20..40).random()
                segs.add(Seg(tx, ty, ex, ey, 0, maxAge, hOff))
            }
            if (ex in 0f..W && ey in 0f..H && nextTips.size < MAX_TIPS)
                nextTips.add(Tip(ex, ey, ta2, depth + 1, hOff))
            if (Math.random() < branchP && depth < 10 && nextTips.size < MAX_TIPS) {
                val sign = if (Math.random() < 0.5) -1f else 1f
                val bAng = ta2 + sign * (0.3f + Math.random().toFloat() * spread)
                nextTips.add(Tip(ex, ey, bAng, depth + 1, (hOff + 0.12f) % 1f))
            }
        }
        tips.clear(); tips.addAll(nextTips)

        if (tips.isEmpty()) reseed(cx, cy)

        // Age and cull segments
        segs.removeAll { it.age >= it.maxAge }
        for (s in segs) s.age++

        // Fade trail
        draw.fadeBlack(8f / 255f)

        // Draw segments: dark wide stroke then bright thin stroke
        draw.setAdditiveBlend()
        for ((x1, y1, x2, y2, age, maxAge, hOff) in segs) {
            val fade  = maxOf(0f, 1f - age.toFloat() / maxAge)
            val h     = (hue + hOff) % 1f
            val bright = (0.20f + bass * 0.15f) * fade
            val c = GLDraw.hsl(h, l = bright * 0.3f)
            draw.line(x1, y1, x2, y2, c[0], c[1], c[2], c[3])
            val c2 = GLDraw.hsl(h, l = bright)
            draw.line(x1, y1, x2, y2, c2[0], c2[1], c2[2], c2[3])
        }
        draw.setNormalBlend()
    }

    private val TAU = (2.0 * PI).toFloat()
}
