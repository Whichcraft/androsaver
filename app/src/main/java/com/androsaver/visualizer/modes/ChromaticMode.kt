package com.androsaver.visualizer.modes

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw
import kotlin.math.*

/**
 * Chromatic — cascading chromatic aberration wave rings.
 *
 * Each beat spawns an expanding ring that splits red, green, and blue channels
 * outward by a pixel offset proportional to wave intensity, creating prismatic
 * rainbow halos as rings overlap and interfere.
 *
 *   Bass   → wave expansion speed + intensity
 *   Mid    → hue drift speed
 *   Treble → RGB split radius (aberration amount)
 *   Beat   → spawn new ring
 *
 * Port of psysuals `effects/chromatic.py` (v3.8.0).
 * TRAIL_ALPHA=0 (self-managed) → fadeBlack(approx) not used;
 * uses per-frame full-clear + additive ring compositing.
 */
class ChromaticMode : BaseMode() {

    override val name = "Chromatic"

    private companion object {
        const val MAX_RINGS = 14
    }

    // Ring: cx, cy, r, maxR, speed, intensity
    private data class Ring(val cx: Float, val cy: Float, var r: Float,
                            val maxR: Float, val speed: Float, val intensity: Float)

    private val rings    = ArrayList<Ring>(MAX_RINGS)
    private var hue      = 0f
    private var beatPrev = 0f
    private var autoCd   = 45

    override fun reset() {
        rings.clear(); hue = 0f; beatPrev = 0f; autoCd = 45
    }

    private fun spawn(bass: Float, W: Float, H: Float) {
        if (rings.size >= MAX_RINGS) return
        val cx    = W / 2f + (Math.random().toFloat() - 0.5f) * W / 3f
        val cy    = H / 2f + (Math.random().toFloat() - 0.5f) * H / 3f
        val maxR  = hypot(maxOf(cx, W - cx), maxOf(cy, H - cy)) * 1.1f
        val speed = 3f + bass * 5f
        val inten = 0.5f + bass * 0.5f
        rings.add(Ring(cx, cy, 0f, maxR, speed, inten))
    }

    override fun draw(draw: GLDraw, audio: AudioData, tick: Int) {
        val W    = draw.W.toFloat(); val H = draw.H.toFloat()
        val bass = audio.beat
        val mid  = audio.mid
        val high = audio.treble

        hue = (hue + 0.004f + mid * 0.003f) % 1f

        if (bass > 0.60f && beatPrev <= 0.60f) spawn(bass, W, H)
        beatPrev = bass

        autoCd--
        if (autoCd <= 0 && rings.size < 3) { spawn(mid * 0.5f, W, H); autoCd = 55 }

        draw.fadeBlack(18f / 255f)

        // Chromatic split: R inner, G centre, B outer
        val split = maxOf(1, (3 + high * 14).toInt())

        val dead = ArrayList<Ring>()
        for (ring in rings) {
            if (ring.r > ring.maxR) { dead.add(ring); continue }
            ring.r += ring.speed + bass * 3f

            val fade  = maxOf(0f, 1f - ring.r / ring.maxR)
            val alpha = ring.intensity * fade
            if (alpha < 0.02f) continue

            val ri = maxOf(1f, ring.r)

            // R channel (inner)
            val splitF = split.toFloat()
            draw.circle(ring.cx, ring.cy, maxOf(1f, ri - splitF),
                        1f, 0f, 0f, minOf(1f, alpha * 2f), filled = false, segments = 40)
            // G channel (centre)
            draw.circle(ring.cx, ring.cy, ri,
                        0f, 1f, 0f, minOf(1f, alpha * 2f), filled = false, segments = 40)
            // B channel (outer)
            draw.circle(ring.cx, ring.cy, ri + splitF,
                        0f, 0f, 1f, minOf(1f, alpha * 2f), filled = false, segments = 40)
        }
        rings.removeAll(dead)
    }
}
