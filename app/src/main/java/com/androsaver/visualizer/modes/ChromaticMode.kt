package com.androsaver.visualizer.modes

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw
import kotlin.math.*

/**
 * Chromatic — prismatic raindrop ripples with RGB-separated outlines.
 *
 * Port of psysuals `effects/chromatic.py` (v3.9.0).
 */
class ChromaticMode : BaseMode() {

    override val name = "Chromatic"

    private companion object {
        const val MAX_RINGS = 14
        val PI_F = Math.PI.toFloat()
        val TAU = (2.0 * Math.PI).toFloat()
    }

    // Ring: cx, cy, r, maxR, speed, intensity, phase
    private data class Ring(val cx: Float, val cy: Float, var r: Float,
                             val maxR: Float, val speed: Float, val intensity: Float, var phase: Float)

    private val rings    = ArrayList<Ring>(MAX_RINGS)
    private var hue      = 0f
    private var beatPrev = 0f
    private var autoCd   = 45

    // Reusable buffer to hold 72 points (144 floats) for wavePoints drawing
    private val ptsScratch = FloatArray(144)

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
        rings.add(Ring(cx, cy, 0f, maxR, speed, inten, Math.random().toFloat() * TAU))
    }

    private fun wavePoints(cx: Float, cy: Float, radius: Float, warp: Float, phase: Float, outPts: FloatArray) {
        val steps = 72
        val step = TAU / steps
        for (i in 0 until steps) {
            val a = i * step
            var rr = radius
            rr += sin(a * 3f + phase) * warp
            rr += sin(a * 7f - phase * 0.7f) * warp * 0.38f
            rr += cos(a * 2f + phase * 1.6f) * warp * 0.18f
            outPts[i * 2] = cx + cos(a) * rr
            outPts[i * 2 + 1] = cy + sin(a) * rr
        }
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

        // Trail decay
        draw.fadeBlack(24f / 255f)

        // Chromatic split amount driven by treble
        val split = 2.0f + high * 12.0f

        val dead = ArrayList<Ring>()
        for (ring in rings) {
            if (ring.r > ring.maxR) { dead.add(ring); continue }
            ring.r += ring.speed + bass * 3f
            ring.phase += 0.08f + high * 0.22f + mid * 0.05f

            val fade  = maxOf(0f, 1f - ring.r / ring.maxR)
            val bright = ring.intensity * fade
            if (bright < 0.04f) continue

            val thick = maxOf(1f, 2f + fade * 4f)
            val warp = (2.5f + high * 11.0f + bass * 5.0f) * (0.45f + fade * 0.9f)
            val baseR = maxOf(1f, ring.r)

            val mult = bright * 1.6f

            // R channel (inner)
            wavePoints(ring.cx, ring.cy, baseR - split, warp, ring.phase, ptsScratch)
            // Wait, draw.polygon is width-based if filled=false but it draws a single line width-1 in OpenGL batch.
            // In Android/GLDraw we only draw lines. We can draw the polygon using normal blend or additive blend.
            // The python code uses pygame.draw.lines which draws with thickness 'thick'.
            // In Android GLDraw, draw.polygon does not support line thickness natively, but it is drawn additively
            // which looks great on high-res screen.
            draw.polygon(ptsScratch, 1f * mult, (40f / 255f) * mult, (80f / 255f) * mult, 1f, filled = false)

            // G channel (centre)
            wavePoints(ring.cx, ring.cy, baseR, warp, ring.phase + 1.5f, ptsScratch)
            draw.polygon(ptsScratch, (60f / 255f) * mult, 1f * mult, (80f / 255f) * mult, 1f, filled = false)

            // B channel (outer)
            wavePoints(ring.cx, ring.cy, baseR + split, warp, ring.phase + 3.1f, ptsScratch)
            draw.polygon(ptsScratch, (80f / 255f) * mult, (120f / 255f) * mult, 1f * mult, 1f, filled = false)
        }
        rings.removeAll(dead)
    }
}
