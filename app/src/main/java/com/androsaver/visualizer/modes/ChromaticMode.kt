package com.androsaver.visualizer.modes

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw
import kotlin.math.*

/**
 * Chromatic — raindrop ripples whose rims split into a clean spectral band.
 * Port of the psysuals 3.22 `Chromatic` rework.
 *
 * Every ripple is one closed outline drawn as contiguous spectral bands that
 * share the same shape, so the colours sit side by side like light through a
 * prism instead of crossing each other. Drops stay local and land away from
 * the ripples already on screen, which keeps separate drops from interfering.
 *
 *   Bass   → expansion speed, drop strength
 *   Mid    → hue drift, idle drops
 *   Treble → width of the spectral split
 *   Beat   → rising edges spawn drops
 */
class ChromaticMode : BaseMode() {

    override val name = "Chromatic"
    private val rng = kotlin.random.Random(0xC4A0)

    private companion object {
        const val MAX_RINGS = 9     // user-requested Android cap; upstream keeps five
        const val STEPS = 60
        const val CANDIDATES = 12
        val TAU = (2.0 * Math.PI).toFloat()
        // Inner to outer edge of a ripple: red through violet.
        val BAND_HUES = floatArrayOf(0.0f, 0.09f, 0.30f, 0.52f, 0.66f, 0.78f)
    }

    private class Ring(var cx: Float, var cy: Float, var r: Float,
                       var maxR: Float, val speed: Float, val intensity: Float, var phase: Float)

    private val rings    = ArrayList<Ring>(MAX_RINGS)
    private var hue      = 0f
    private var beatPrev = 0f
    private var autoCd   = 45f
    private var lastW = 0; private var lastH = 0

    private val cosA = FloatArray(STEPS) { cos(it / STEPS.toFloat() * TAU) }
    private val sinA = FloatArray(STEPS) { sin(it / STEPS.toFloat() * TAU) }
    private val shape = FloatArray(STEPS)
    private val quad = FloatArray(8)
    private val col = FloatArray(4)

    override fun reset() {
        rings.clear(); hue = 0f; beatPrev = 0f; autoCd = 45f
    }

    /** Live drops as (cx, cy, maxR) triples, for JVM contract checks. */
    internal fun ringGeometry(): FloatArray {
        val out = FloatArray(rings.size * 3)
        for ((i, ring) in rings.withIndex()) { out[i * 3] = ring.cx; out[i * 3 + 1] = ring.cy; out[i * 3 + 2] = ring.maxR }
        return out
    }

    internal fun spawn(bass: Float, W: Float, H: Float) {
        if (rings.size >= MAX_RINGS) return
        // Drops stay local and land where the frame is emptiest, so the
        // spectral rims of separate drops rarely cross.
        val maxR = minOf(W, H) * (0.23f + minOf(1.5f, bass) * 0.10f)
        var bestX = W * 0.5f; var bestY = H * 0.5f
        var bestGap = Float.NEGATIVE_INFINITY
        for (attempt in 0 until CANDIDATES) {
            val cx = W * (0.10f + rng.nextFloat() * 0.80f)
            val cy = H * (0.10f + rng.nextFloat() * 0.80f)
            var gap = Float.POSITIVE_INFINITY
            for (ring in rings) gap = minOf(gap, hypot(cx - ring.cx, cy - ring.cy) - ring.maxR)
            if (gap > bestGap) { bestX = cx; bestY = cy; bestGap = gap }
            if (gap >= maxR) break
        }
        rings.add(Ring(bestX, bestY, 0f, maxR, 1.6f + bass * 2.2f, 0.6f + bass * 0.4f,
            rng.nextFloat() * TAU))
    }

    /** One ripple: six contiguous spectral bands around a shared wobbling outline. */
    private fun ripple(draw: GLDraw, ring: Ring, radius: Float, bandW: Float, bright: Float) {
        if (radius < bandW * 2f || bright < 0.04f) return
        // One gentle wobble shared by every band keeps the bands parallel.
        val wobble = minOf(radius * 0.04f, ring.maxR * 0.018f)
        for (i in 0 until STEPS) {
            val a = i / STEPS.toFloat() * TAU
            shape[i] = radius + wobble * (sin(a * 3f + ring.phase) + 0.45f * cos(a * 2f - ring.phase * 1.3f))
        }
        val bands = BAND_HUES.size
        for (band in 0 until bands) {
            val inner = (band - bands / 2f) * bandW
            val outer = inner + bandW
            GLDraw.hsl(hue * 0.15f + BAND_HUES[band], 1f, 0.5f * bright, 1f, col)
            for (i in 0 until STEPS) {
                val j = (i + 1) % STEPS
                quad[0] = ring.cx + cosA[i] * (shape[i] + inner); quad[1] = ring.cy + sinA[i] * (shape[i] + inner)
                quad[2] = ring.cx + cosA[j] * (shape[j] + inner); quad[3] = ring.cy + sinA[j] * (shape[j] + inner)
                quad[4] = ring.cx + cosA[j] * (shape[j] + outer); quad[5] = ring.cy + sinA[j] * (shape[j] + outer)
                quad[6] = ring.cx + cosA[i] * (shape[i] + outer); quad[7] = ring.cy + sinA[i] * (shape[i] + outer)
                draw.polygon(quad, 4, col[0], col[1], col[2], 1f, true)
            }
        }
    }

    override fun draw(draw: GLDraw, audio: AudioData, tick: Int) {
        val W = draw.W.toFloat(); val H = draw.H.toFloat()
        val response = prepare(draw, audio); val step = response.step
        val bass = response.motion[0]; val mid = response.motion[1]
        val displayScale = minOf(W, H) / 720f
        if (lastW > 0 && (lastW != draw.W || lastH != draw.H)) {
            val sx = W / lastW; val sy = H / lastH
            for (ring in rings) { ring.cx *= sx; ring.cy *= sy; ring.r *= minOf(sx, sy); ring.maxR *= minOf(sx, sy) }
        }
        lastW = draw.W; lastH = draw.H

        hue = (hue + (0.0015f + mid * 0.002f) * step) % 1f

        if (audio.beat > 0.60f && beatPrev <= 0.60f) spawn(minOf(1.5f, audio.beat), W, H)
        beatPrev = audio.beat

        autoCd -= step
        if (autoCd <= 0 && rings.size < 3) { spawn(mid * 0.5f, W, H); autoCd = 55f }

        // A short wake behind each rim; a long one smeared the bands together.
        draw.fadeBlackTimed(135f / 255f, step)

        val bandW = maxOf(1f, minOf(W, H) / 360f * (1.1f + response.light[2] * 1.1f * response.accent))

        var kept = 0
        for (index in 0 until rings.size) {
            val ring = rings[index]
            ring.r += (ring.speed + bass) * displayScale * step * response.motionScale
            ring.phase += (0.012f + mid * 0.012f) * step
            if (ring.r > ring.maxR) continue
            rings[kept++] = ring

            val ratio = ring.r / ring.maxR
            val fade = maxOf(0f, 1f - ratio * ratio)
            val bright = minOf(1f, ring.intensity * fade * (1f + response.light[0] * 0.35f * response.accent))
            if (bright < 0.04f) continue
            val width = bandW * (0.55f + 0.45f * fade)
            // The outer rim, then a fainter ripple following inside it.
            ripple(draw, ring, ring.r, width, bright)
            ripple(draw, ring, ring.r * 0.58f, width, bright * 0.5f)
        }
        while (rings.size > kept) rings.removeAt(rings.size - 1)
    }
}
