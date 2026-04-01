package com.androsaver.visualizer.modes

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw
import kotlin.math.*

/**
 * BranchesMode — recursive fractal lightning tree.
 * Six neon arms radiate from screen centre, each splitting recursively to
 * depth 6. Mid frequencies jitter branch angles live; bass drives trunk
 * length; beat fires extra arms with a brightness burst.
 * Port of psysuals `Branches` class (v2.0.0).
 */
class BranchesMode : BaseMode() {

    override val name = "Branches"

    private companion object {
        const val MAX_DEPTH = 6
        const val BASE_ARMS = 6
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

        val jitter = (sin(time * 2.3f + depth * 1.7f + angle) * mid * 0.55f
                    + cos(time * 1.1f + depth * 2.9f)         * mid * 0.25f)
        val drawLen = if (depth == MAX_DEPTH) length * 0.15f else length
        val ex = x + cos(angle + jitter) * drawLen
        val ey = y + sin(angle + jitter) * drawLen

        val depthT = depth.toFloat() / MAX_DEPTH
        val h      = (hue + (1f - depthT) * 0.45f) % 1f
        val bright = (0.25f + depthT * 0.50f + high * 0.20f + beatFlash * 0.30f).coerceIn(0f, 1f)
        val color  = GLDraw.hsl(h, 1f, bright)
        draw.line(x, y, ex, ey, color[0], color[1], color[2], 1f)

        val spread = PI.toFloat() / 2.8f + mid * 0.45f
        val ratio  = 0.64f + high * 0.08f
        branch(draw, ex, ey, angle - spread / 2f, length * ratio, depth - 1,
               hue, time, mid, high, beatFlash)
        branch(draw, ex, ey, angle + spread / 2f, length * ratio, depth - 1,
               hue, time, mid, high, beatFlash)
        if (depth == MAX_DEPTH) {
            // Extra central branch at trunk level — creates Y-fork at base
            branch(draw, ex, ey, angle, length * ratio * 0.72f, depth - 1,
                   hue, time, mid, high, beatFlash)
        }
    }

    override fun draw(draw: GLDraw, audio: AudioData, tick: Int) {
        draw.fadeBlack(16f / 255f)

        val fft  = audio.fft
        val beat = audio.beat
        val bass = fft.meanSlice(0, 6)
        val mid  = fft.meanSlice(6, 30)
        val high = fft.meanSlice(30, fft.size)

        hue       += 0.005f
        time      += 0.018f + bass * 0.04f + beat * 0.08f
        beatFlash  = beatFlash * 0.75f + beat * 0.25f

        val cx = draw.W / 2f
        val cy = draw.H / 2f

        val trunk = minOf(draw.W, draw.H) * 0.18f * (1f + bass * 0.70f + beat * 0.40f)
        val nArms = BASE_ARMS + (minOf(beat, 2.5f) * 1.5f).toInt()

        val baseRot = time * 0.06f

        for (i in 0 until nArms) {
            val angle  = baseRot + i.toFloat() / nArms * TAU
            val armHue = (hue + i.toFloat() / nArms * 0.35f) % 1f
            branch(draw, cx, cy, angle, trunk, MAX_DEPTH, armHue, time, mid, high, beatFlash)
        }
    }
}
