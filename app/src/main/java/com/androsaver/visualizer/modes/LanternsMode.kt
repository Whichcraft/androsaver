package com.androsaver.visualizer.modes

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Lanterns — paper lanterns rising over a dark lake (port of psysuals `lanterns.py`).
 *
 * Warm lanterns drift up into a deep blue night. Each glows, sways in the wind
 * and is mirrored in the water as a shimmering column of short strokes. Near
 * lanterns are larger, brighter and faster; far ones are small gold points near
 * the horizon.
 *
 *  - Bass: the flames flicker brighter and the glow swells.
 *  - Mid: wind, the lanterns sway and drift sideways.
 *  - Treble: sparks drift up from the lanterns and the reflections shimmer.
 *  - Beat: new lanterns are released from the water (rising edge only).
 *
 * Android adaptations:
 *  - The effect uses [AudioData.beat], [AudioData.mid] and [AudioData.treble] through the
 *    shared smoothed [com.androsaver.visualizer.EffectDynamics] responses (motion for
 *    geometry, light for brightness). Mid and treble are deviations above their running
 *    average, so they are 0 at steady music and positive on peaks; they are used with the
 *    upstream scalings unchanged and clamped to 0..1.5. The FFT and waveform are not used.
 *  - Lanterns and sparks live in fixed preallocated parallel arrays (42 and 160, as
 *    upstream); nothing is allocated while drawing. Removal is swap-with-last.
 *  - Motion uses elapsed time (dynamics step = seconds * 60), never the tick counter.
 *  - The per-pixel sky/water gradient is drawn as horizontal colour bands; the cached glow
 *    sprites are three concentric additive circles; the stars are 1-2 px rects.
 *  - Lantern positions are stored relative to the frame (x, and height above the water), so
 *    a resize needs no re-layout. Sparks are in pixels and are dropped on a resize.
 *  - Lanterns are drawn in pool order rather than sorted by depth, so that all
 *    reflections/glows share one additive pass, all bodies one normal pass and all sparks
 *    one additive pass (blend switches flush the batch).
 */
class LanternsMode : BaseMode() {

    override val name = "Lanterns"

    companion object {
        const val MAX_LANTERNS = 42
        const val MAX_SPARKS = 160
        /** Horizon height as a fraction of the frame, measured from the top. */
        const val HORIZON = 0.64f
        private const val INITIAL_LANTERNS = 14
        private const val SKY_BANDS = 28
        private const val WATER_BANDS = 20
        private const val STARS = 90
        private const val STROKES = 12
        private const val TAU = (2.0 * PI)
        private const val SEED = 0x1A57
    }

    private var rng = Random(SEED)

    // Lantern pool (parallel arrays). x is 0..1 across, y is height above the water 0..1.
    private val lx = FloatArray(MAX_LANTERNS)
    private val ly = FloatArray(MAX_LANTERNS)
    private val ldepth = FloatArray(MAX_LANTERNS)
    private val lhue = FloatArray(MAX_LANTERNS)
    private val lphase = FloatArray(MAX_LANTERNS)
    private val lflick = FloatArray(MAX_LANTERNS)
    private var lanternCount = 0

    // Per-frame layout of each lantern in pixels.
    private val px = FloatArray(MAX_LANTERNS)
    private val py = FloatArray(MAX_LANTERNS)
    private val psize = FloatArray(MAX_LANTERNS)
    private val pbright = FloatArray(MAX_LANTERNS)

    // Spark pool, pixels.
    private val sx = FloatArray(MAX_SPARKS)
    private val sy = FloatArray(MAX_SPARKS)
    private val svx = FloatArray(MAX_SPARKS)
    private val svy = FloatArray(MAX_SPARKS)
    private val slife = FloatArray(MAX_SPARKS)
    private val shue = FloatArray(MAX_SPARKS)
    private var sparkCount = 0

    private val stars = FloatArray(STARS * 3)
    private var time = 0.0
    private var beatPrev = 0f
    private var idle = 0f
    private var lastW = 0
    private var lastH = 0

    private val col = FloatArray(4)
    private val col2 = FloatArray(4)
    private val shape = FloatArray(16)

    /** Number of lanterns currently alive. */
    val activeLanterns: Int get() = lanternCount
    /** Number of sparks currently alive. */
    val activeSparks: Int get() = sparkCount

    init { seed() }

    override fun reset() { seed() }

    /** Mean height above the water (0..1) of the live lanterns; 0 when there are none. */
    internal fun meanHeight(): Float {
        if (lanternCount == 0) return 0f
        var sum = 0f
        for (i in 0 until lanternCount) sum += ly[i]
        return sum / lanternCount
    }

    private fun seed() {
        rng = Random(SEED)
        time = 0.0; beatPrev = 0f; idle = 0f
        lanternCount = 0; sparkCount = 0
        lastW = 0; lastH = 0
        for (i in 0 until STARS * 3) stars[i] = rng.nextFloat()
        for (i in 0 until INITIAL_LANTERNS) release(rng.nextFloat())
    }

    private fun uniform(lo: Float, hi: Float): Float = lo + (hi - lo) * rng.nextFloat()

    private fun release(height: Float) {
        if (lanternCount >= MAX_LANTERNS) return
        val i = lanternCount++
        lx[i] = uniform(0.04f, 0.96f)
        ly[i] = height
        ldepth[i] = uniform(0.25f, 1f)
        lhue[i] = uniform(0.02f, 0.11f)
        lphase[i] = uniform(0f, TAU.toFloat())
        lflick[i] = uniform(0f, TAU.toFloat())
    }

    private fun removeLantern(i: Int) {
        val last = --lanternCount
        if (i != last) {
            lx[i] = lx[last]; ly[i] = ly[last]; ldepth[i] = ldepth[last]
            lhue[i] = lhue[last]; lphase[i] = lphase[last]; lflick[i] = lflick[last]
        }
    }

    private fun removeSpark(i: Int) {
        val last = --sparkCount
        if (i != last) {
            sx[i] = sx[last]; sy[i] = sy[last]; svx[i] = svx[last]
            svy[i] = svy[last]; slife[i] = slife[last]; shue[i] = shue[last]
        }
    }

    private fun wave(x: Double): Float = sin(x).toFloat()

    override fun draw(draw: GLDraw, audio: AudioData, tick: Int) {
        val w = draw.W.coerceAtLeast(1).toFloat()
        val h = draw.H.coerceAtLeast(1).toFloat()
        if (draw.W != lastW || draw.H != lastH) {
            // Lanterns are relative; only pixel-space sparks need dropping.
            sparkCount = 0
            lastW = draw.W; lastH = draw.H
        }

        val response = prepare(draw, audio)
        val step = response.step
        val seconds = step / 60f
        val bass = response.motion[0].coerceIn(0f, 1.5f)
        val mid = response.motion[1].coerceIn(0f, 1.5f)
        val high = response.motion[2].coerceIn(0f, 1.5f)
        val motionScale = response.motionScale
        val flash = response.light[0].coerceIn(0f, 1.5f) * response.accent
        time += seconds.toDouble()

        val beat = if (audio.beat.isFinite()) audio.beat.coerceIn(0f, 1.5f) else 0f
        if (beat > 0.6f && beatPrev <= 0.6f) {
            repeat(1 + (min(1.5f, beat) * 2f).toInt()) { release(0f) }
        }
        beatPrev = beat
        idle += seconds
        if (idle > 1.6f) { // a gentle stream in silence
            idle = 0f
            release(0f)
        }

        val wind = (0.010f + 0.10f * mid) * wave(time * 0.11)
        var i = 0
        while (i < lanternCount) {
            val d = ldepth[i]
            ly[i] += (0.020f + 0.040f * d) * seconds * motionScale
            val sway = (0.004f + 0.060f * mid) * wave(time * (0.7 + 0.8 * mid) + lphase[i])
            lx[i] += (wind * d + sway) * seconds * motionScale
            lflick[i] += (5f + 6f * bass) * seconds
            if (ly[i] < 1.15f && lx[i] > -0.1f && lx[i] < 1.1f) i++ else removeLantern(i)
        }

        val horizon = h * HORIZON
        val unit = min(w, h)

        // Layout and brightness of every lantern, shared by all passes.
        for (n in 0 until lanternCount) {
            val d = ldepth[n]
            px[n] = lx[n] * w
            py[n] = horizon - ly[n] * horizon * (0.35f + 0.65f * d)
            psize[n] = unit * (0.008f + 0.026f * d)
            val fl = lflick[n]
            val flame = 0.80f + (0.12f + 0.15f * bass) * sin(fl) + 0.08f * sin(fl * 2.7f)
            var bright = min(1f, (0.35f + 0.45f * d) * flame * (1f + 0.45f * bass + 0.60f * flash))
            val fade = min(1f, (1.12f - ly[n]) * 4f)
            val arrive = min(1f, ly[n] * 12f + 0.15f)
            bright *= fade * arrive
            pbright[n] = if (bright.isFinite()) bright else 0f
        }

        // Sparks ride up from the flame with the treble.
        if (high > 0.05f) {
            for (n in 0 until lanternCount) {
                if (sparkCount >= MAX_SPARKS) break
                val d = ldepth[n]
                if (pbright[n] < 0.02f || rng.nextFloat() >= 2f * high * d * step) continue
                val k = sparkCount++
                val scale = unit / 540f
                sx[k] = px[n]
                sy[k] = py[n] - psize[n] * 0.725f
                svx[k] = (rng.nextFloat() + rng.nextFloat() + rng.nextFloat() - 1.5f) * 8f * scale * 2f
                svy[k] = -uniform(20f, 45f) * (0.5f + d) * scale
                slife[k] = 1f
                shue[k] = lhue[n] + 0.05f
            }
        }

        drawBackground(draw, w, h, horizon, unit)

        // Pass 1 (additive): reflections and glows.
        draw.setAdditiveBlend()
        for (n in 0 until lanternCount) {
            val bright = pbright[n]
            if (bright < 0.02f) continue
            val size = psize[n]
            val x = px[n]
            val y = py[n]
            GLDraw.hsl(lhue[n], 0.95f, 0.30f + 0.32f * bright, 1f, col)

            val mirror = horizon + (horizon - y) * 0.30f
            if (mirror < h) {
                val thick = maxOf(1f, size * 0.22f)
                for (s in 0 until STROKES) {
                    val t = s / (STROKES - 1).toFloat()
                    val ry = mirror + (t - 0.3f) * size * 5f
                    if (!(horizon + 1f < ry && ry < h)) continue
                    val jitter = wave(time * 2.1 + s * 1.7 + lphase[n]) * size * (0.15f + 0.35f * mid) * (0.4f + t)
                    val half = size * (0.35f + 0.55f * t) * (1f + 0.3f * wave(time * 3.3 + s * 2.1))
                    val shimmer = 1f + 0.9f * high * (0.5f + 0.5f * wave(time * 9.0 + s * 2.3 + lphase[n]))
                    val a = (0.75f * bright * shimmer * (1f - 0.8f * t)).coerceIn(0f, 1f)
                    draw.rect(x + jitter - half, ry, half * 2f, thick, col[0], col[1], col[2], a)
                }
            }

            GLDraw.hsl(lhue[n] + 0.02f, 1f, 0.5f, 1f, col2)
            val radius = size * (2.8f + 2.6f * bass + 1.5f * flash)
            draw.circle(x, y, radius, col2[0], col2[1], col2[2], 0.10f * bright, true, 16)
            draw.circle(x, y, radius * 0.62f, col2[0], col2[1], col2[2], 0.16f * bright, true, 16)
            draw.circle(x, y, radius * 0.32f, col2[0], col2[1], col2[2], 0.26f * bright, true, 12)
        }

        // Pass 2 (normal): paper bodies, cores and rims.
        draw.setNormalBlend()
        for (n in 0 until lanternCount) {
            val bright = pbright[n]
            if (bright < 0.02f) continue
            val size = psize[n]
            val x = px[n]
            val y = py[n]
            val bw = maxOf(2f, size * 1.05f)
            val bh = maxOf(3f, size * 1.45f)
            val l = x - bw / 2f; val r = x + bw / 2f
            val t = y - bh / 2f; val b = y + bh / 2f
            val c = minOf(maxOf(1f, bw / 3f), bw / 2f, bh / 2f)
            val p = shape
            p[0] = l + c; p[1] = t;  p[2] = r - c; p[3] = t
            p[4] = r;     p[5] = t + c; p[6] = r;  p[7] = b - c
            p[8] = r - c; p[9] = b;  p[10] = l + c; p[11] = b
            p[12] = l;    p[13] = b - c; p[14] = l; p[15] = t + c
            GLDraw.hsl(lhue[n], 0.95f, 0.30f + 0.32f * bright, 1f, col)
            draw.polygon(p, 8, col[0], col[1], col[2], 1f, true)

            // Core of the flame: a small ellipse.
            GLDraw.hsl(lhue[n] + 0.04f, 1f, min(0.92f, 0.55f + 0.40f * bright), 1f, col2)
            val cy = y + bh / 8f
            for (k in 0 until 8) {
                val ang = (k * TAU / 8.0).toFloat()
                p[k * 2] = x + kotlin.math.cos(ang) * bw / 4f
                p[k * 2 + 1] = cy + sin(ang) * bh / 3f
            }
            draw.polygon(p, 8, col2[0], col2[1], col2[2], 1f, true)

            // The dark rim at the top of the paper shade.
            draw.line(l + 1f, t, r - 2f, t, col[0] * 0.35f, col[1] * 0.35f, col[2] * 0.35f, 1f)
        }

        // Pass 3 (additive): sparks.
        draw.setAdditiveBlend()
        val sparkSize = 2f + (unit / 400f).toInt()
        var k = 0
        while (k < sparkCount) {
            sx[k] += svx[k] * seconds
            sy[k] += svy[k] * seconds
            slife[k] -= 0.45f * seconds
            if (slife[k] > 0f) {
                GLDraw.hsl(shue[k], 1f, 0.25f + 0.5f * slife[k], 1f, col)
                draw.rect(sx[k], sy[k], sparkSize, sparkSize, col[0], col[1], col[2], minOf(1f, slife[k] * 1.5f))
                k++
            } else removeSpark(k)
        }
        draw.setNormalBlend()
    }

    /** Night sky above the horizon and dark water below, as colour bands. */
    private fun drawBackground(draw: GLDraw, w: Float, h: Float, horizon: Float, unit: Float) {
        draw.setNormalBlend()
        for (b in 0 until SKY_BANDS) {
            val t0 = b / SKY_BANDS.toFloat()
            val t1 = (b + 1) / SKY_BANDS.toFloat()
            val tc = (t0 + t1) * 0.5f
            val f = Math.pow(tc.toDouble(), 1.6).toFloat()
            draw.rect(0f, horizon * t0, w, horizon * (t1 - t0) + 1f,
                (6f + 34f * f) / 255f, (8f + 24f * f) / 255f, (30f + 40f * f) / 255f, 1f)
        }
        val waterH = h - horizon
        for (b in 0 until WATER_BANDS) {
            val t0 = b / WATER_BANDS.toFloat()
            val t1 = (b + 1) / WATER_BANDS.toFloat()
            val tc = (t0 + t1) * 0.5f
            val f = Math.pow(tc.toDouble(), 0.7).toFloat()
            draw.rect(0f, horizon + waterH * t0, w, waterH * (t1 - t0) + 1f,
                (22f - 19f * f) / 255f, (20f - 16f * f) / 255f, (46f - 32f * f) / 255f, 1f)
        }

        // Faint twinkling stars, only in the sky.
        val starSize = maxOf(1f, unit / 500f)
        for (s in 0 until STARS) {
            val sxn = stars[s * 3]
            val syn = stars[s * 3 + 1]
            val twinkle = 0.55f + 0.45f * wave(time * 1.3 + stars[s * 3 + 2] * 40.0)
            val g = (40f + 90f * twinkle * (1f - syn)) / 255f
            draw.rect(sxn * w, syn * horizon * 0.95f, starSize, starSize, g, g, min(1f, g + 30f / 255f), 1f)
        }
    }
}
