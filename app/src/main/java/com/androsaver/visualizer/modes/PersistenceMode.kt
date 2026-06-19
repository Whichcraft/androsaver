package com.androsaver.visualizer.modes

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw
import kotlin.math.*

/**
 * Persistence of Vision — stroboscopic rotating geometric shapes.
 *
 * Multiple nested polygons rotate at subtly different speeds.  With long
 * trail persistence their ghost images accumulate and interfere, creating
 * wagon-wheel moiré illusions and mandala-like kaleidoscope patterns.
 *
 *   Bass   → rotation speed burst
 *   Mid    → number of shapes / polygon complexity
 *   Treble → strobe flash
 *   Beat   → speed spike + hue jump
 *
 * Port of psysuals `effects/persistence.py` (v3.8.0).
 * TRAIL_ALPHA=5 → fadeBlack(5f/255f) — very long persistence for moiré build-up.
 */
class PersistenceMode : BaseMode() {

    override val name = "Persistence"

    private companion object {
        const val MAX_SHAPES = 8
        val TAU = (2.0 * PI).toFloat()
    }

    private val angles = FloatArray(MAX_SHAPES) { it / MAX_SHAPES.toFloat() * TAU }
    private val speeds = FloatArray(MAX_SHAPES) { 0.014f * (1f + it * 0.17f) }
    private var hue      = 0f
    private var boost    = 0f
    private var beatPrev = 0f

    override fun reset() {
        hue = 0f; boost = 0f; beatPrev = 0f
        for (i in 0 until MAX_SHAPES) angles[i] = i / MAX_SHAPES.toFloat() * TAU
    }

    override fun draw(draw: GLDraw, audio: AudioData, tick: Int) {
        val W = draw.W.toFloat(); val H = draw.H.toFloat()
        val bass = audio.beat
        val mid  = audio.mid
        val high = audio.treble

        hue   = (hue + 0.003f + mid * 0.003f) % 1f
        boost = maxOf(0f, boost - 0.04f)

        if (bass > 0.75f && beatPrev <= 0.75f) boost = 1.5f + bass * 1.0f
        beatPrev = bass

        draw.fadeBlack(5f / 255f)

        val cx      = W / 2f; val cy = H / 2f
        val baseR   = minOf(W, H) * 0.36f
        val spdMul  = 1f + bass * 2.5f + boost
        val nShapes = maxOf(3, minOf(MAX_SHAPES, 3 + (mid * 5).toInt()))

        for (i in 0 until nShapes) {
            angles[i] += speeds[i] * spdMul

            val r      = baseR * (0.25f + 0.75f * (i + 1) / nShapes)
            val sides  = 3 + i
            val h      = (hue + i.toFloat() / nShapes * 0.55f) % 1f
            val bright = 0.28f + bass * 0.25f + if (i == nShapes - 1) high * 0.12f else 0f

            val pts = FloatArray(sides * 2)
            for (s in 0 until sides) {
                val a = angles[i] + s.toFloat() / sides * TAU
                pts[s * 2]     = cx + cos(a) * r
                pts[s * 2 + 1] = cy + sin(a) * r
            }

            val col  = GLDraw.hsl(h, l = bright)
            val glow = GLDraw.hsl(h, l = bright * 0.30f)
            draw.polygon(pts, glow[0], glow[1], glow[2], glow[3], filled = false)
            draw.polygon(pts, col[0],  col[1],  col[2],  col[3],  filled = false)
        }

        // Treble: brief radial flash ring
        if (high > 0.50f) {
            val flashR = minOf(W, H) * 0.48f * high
            val fc = GLDraw.hsl(hue, l = 0.15f * high)
            draw.circle(cx, cy, flashR, fc[0], fc[1], fc[2], fc[3], filled = false, segments = 40)
        }
    }
}
