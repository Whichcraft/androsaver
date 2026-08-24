package com.androsaver.visualizer.modes

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw
import kotlin.math.*

/**
 * Heartbeat — rhythmic pressure waves from a pulsing centre.
 *
 * Each beat spawns concentric rings that expand outward.  The ring outline
 * smoothly morphs between a circle (high n_sides) and a polygon (low n_sides)
 * based on bass intensity at spawn time.  Multiple overlapping rings create
 * moiré interference patterns.
 *
 *   Bass   → wave speed + polygon morphing
 *   Mid    → propagation speed modifier
 *   Treble → ring shimmer
 *   Beat   → spawn new ring(s)
 *
 * Port of psysuals `effects/heartbeat.py` (v3.11.0).
 * Upstream TRAIL_ALPHA=20 is clamped by GLDraw to the shared 48/255 minimum.
 */
class HeartbeatMode : BaseMode() {

    override val name = "Heartbeat"

    private companion object {
        const val MAX_RINGS = 16
        const val N_PTS     = 120
        val TAU = (2.0 * PI).toFloat()
    }

    // Ring: r, maxR, speed, intensity, hue, nSides, phase
    private data class Ring(var r: Float, val maxR: Float, val speed: Float,
                            val intensity: Float, val hue: Float,
                            val nSides: Int, val phase: Float)

    private val rings    = ArrayList<Ring>(MAX_RINGS)
    private var hue      = 0f
    private var beatPrev = 0f
    private var autoCd   = 45

    override fun reset() { rings.clear(); hue = 0f; beatPrev = 0f; autoCd = 45 }

    private fun spawn(bass: Float, offsetHue: Float, W: Float, H: Float) {
        if (rings.size >= MAX_RINGS) return
        val maxR   = maxOf(W, H) * 0.75f
        val speed  = 2.5f + bass * 4.5f
        val inten  = 0.60f + bass * 0.40f
        val rHue   = (hue + offsetHue) % 1f
        val nSides = maxOf(3, 12 - (bass * 9).toInt())
        rings.add(Ring(1f, maxR, speed, inten, rHue, nSides, 0f))
    }

    override fun draw(draw: GLDraw, audio: AudioData, tick: Int) {
        val W = draw.W.toFloat(); val H = draw.H.toFloat()
        val bass = audio.beat
        val mid  = audio.mid
        val high = audio.treble

        hue = (hue + 0.005f + mid * 0.004f) % 1f

        if (bass > 0.60f && beatPrev <= 0.60f) {
            spawn(bass, 0f, W, H)
            if (bass > 0.85f) spawn(bass * 0.7f, 0.25f, W, H)
        }
        beatPrev = bass

        autoCd--
        if (autoCd <= 0 && rings.size < 4) { spawn(mid * 0.5f, 0f, W, H); autoCd = 50 }

        draw.fadeBlack(20f / 255f)

        val spdMul = 1f + mid * 0.8f
        val cx = W / 2f; val cy = H / 2f

        val live = ArrayList<Ring>(rings.size)
        for (ring in rings) {
            val newR = ring.r + ring.speed * spdMul
            if (newR <= ring.maxR) {
                ring.r = newR
                live.add(ring)
            }
        }
        rings.clear()
        rings.addAll(live)

        val pts = FloatArray(N_PTS * 2)
        for ((r, maxR, _, inten, rHue, nSides, phase) in rings) {
            val fade = maxOf(0f, 1f - r / maxR) * inten
            if (fade < 0.01f) continue
            val bright = 0.20f + fade * 0.50f + high * 0.12f
            val col  = GLDraw.hsl(rHue, l = bright)
            val glow = GLDraw.hsl(rHue, l = bright * 0.28f)

            for (i in 0 until N_PTS) {
                val a = i.toFloat() / N_PTS * TAU + phase
                val polyM  = cos(a * nSides) * (0.04f + (1f - nSides / 12f) * 0.14f)
                val rActual = r * (1f + polyM)
                pts[i * 2]     = cx + cos(a) * rActual
                pts[i * 2 + 1] = cy + sin(a) * rActual
            }

            draw.polygon(pts, glow[0], glow[1], glow[2], glow[3], filled = false)
            draw.polygon(pts, col[0],  col[1],  col[2],  col[3],  filled = false)
        }
    }
}
