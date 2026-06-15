package com.androsaver.visualizer.modes

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw
import kotlin.math.*

/**
 * BranchesMode — recursive fractal lightning tree (psychedelic edition).
 * Nine neon arms radiate from screen centre, each splitting recursively to
 * depth 7. Triple-fork at trunk AND first split level for dense inner canopy.
 * Every segment drawn twice (wide dim glow + bright core) for neon flare.
 * Mid frequencies jitter all branch angles; bass drives trunk length; beat
 * fires extra arms with a brightness burst.
 * Port of psysuals `Branches` class (v3.7.0).
 */
class BranchesMode : BaseMode() {

    override val name = "Branches"

    private companion object {
        const val MAX_DEPTH = 7
        const val BASE_ARMS = 9
        const val TAU = (Math.PI * 2).toFloat()
    }

    private var hue       = 0f
    private var time      = 0f
    private var beatFlash = 0f

    override fun reset() {
        hue = 0f; time = 0f; beatFlash = 0f
    }

    private fun branch(
        draw: GLDraw,
        x: Float, y: Float,
        angle: Float, length: Float,
        depth: Int,
        hue: Float, time: Float,
        mid: Float, high: Float, beatFlash: Float
    ) {
        if (depth == 0 || length < 1.5f) return

        // Mids drive medium sway, treble drives high-frequency shiver
        val jitter = (sin(time * 2.3f + depth * 1.7f + angle) * mid * 0.45f
                    + cos(time * 1.1f + depth * 2.9f)         * mid * 0.22f
                    + sin(time * 6.5f + angle * 3.3f)         * high * 0.18f)

        // Trunk segment drawn very short; children still get full length
        val drawLen = if (depth == MAX_DEPTH) length * 0.015f else length
        val ex = x + cos(angle + jitter) * drawLen
        val ey = y + sin(angle + jitter) * drawLen

        val depthT = depth.toFloat() / MAX_DEPTH
        val h      = (hue + (1f - depthT) * 0.80f) % 1f
        val bright = (0.28f + depthT * 0.52f + high * 0.22f + beatFlash * 0.35f).coerceIn(0f, 1f)

        // Neon glow: wide dim halo first, then bright core on top
        val glowC = GLDraw.hsl(h, 1f, bright * 0.30f)
        draw.line(x, y, ex, ey, glowC[0], glowC[1], glowC[2], 0.55f)
        val coreC = GLDraw.hsl(h, 1f, bright)
        draw.line(x, y, ex, ey, coreC[0], coreC[1], coreC[2], 1f)

        // Mids and treble increase branch spread angle and branch decay ratio
        val spread = PI.toFloat() / 2.6f + mid * 0.28f + high * 0.10f
        val ratio  = 0.62f + high * 0.06f
        branch(draw, ex, ey, angle - spread / 2f, length * ratio, depth - 1,
               hue, time, mid, high, beatFlash)
        branch(draw, ex, ey, angle + spread / 2f, length * ratio, depth - 1,
               hue, time, mid, high, beatFlash)

        // Triple-fork at trunk AND first split level — dense inner canopy
        if (depth >= MAX_DEPTH - 1) {
            branch(draw, ex, ey, angle, length * ratio * 0.72f, depth - 1,
                   hue, time, mid, high, beatFlash)
        }
    }

    override fun draw(draw: GLDraw, audio: AudioData, tick: Int) {
        draw.fadeBlack(10f / 255f)

        val beat = audio.beat
        val bass = beat
        val mid  = audio.mid
        val high = audio.treble

        hue       += 0.012f
        time      += 0.025f + bass * 0.06f + mid * 0.04f + high * 0.02f
        beatFlash  = beatFlash * 0.72f + bass * 0.28f

        val cx = draw.W / 2f
        val cy = draw.H / 2f

        val sc    = minOf(draw.W, draw.H).toFloat()
        val trunk = minOf(sc * 0.22f * (1f + bass * 0.70f + mid * 0.25f), sc * 0.27f)
        // Extra arms on strong beats (up to +5); treble has minimal influence
        val nArms = BASE_ARMS + (minOf(bass, 2.5f) * 2.2f + high * 0.8f).toInt()

        val baseRot = time * 0.06f

        for (i in 0 until nArms) {
            val angle  = baseRot + i.toFloat() / nArms * TAU
            val armHue = (hue + i.toFloat() / nArms * 0.75f) % 1f
            branch(draw, cx, cy, angle, trunk, MAX_DEPTH, armHue, time, mid, high, beatFlash)
        }
    }
}
