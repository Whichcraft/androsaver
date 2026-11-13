package com.androsaver.visualizer.modes

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.EffectDynamics
import com.androsaver.visualizer.GLDraw
import kotlin.math.*
import kotlin.random.Random

/**
 * HorizonMode — a landscape built from the music.
 * Port of the psysuals 3.23 `Horizon` effect.
 *
 * Rows of solid mountain ridges scroll toward the camera. Every row is a
 * snapshot of the spectrum taken when the row appeared on the horizon, so the
 * music's recent past rolls by as terrain. Rows are painted far to near and
 * each ridge hides the ones behind it. A flat valley with lane lines runs down
 * the middle toward a sun on the horizon, under a sky of stars.
 *
 *   Spectrum → shape of each new ridge: bass beside the valley, treble outside
 *   Bass     → ride speed, near ridges swell, sun and sky glow
 *   Mid      → ride speed, hue drift
 *   Treble   → stars twinkle, far ridge lines glitter
 *   Beat     → the sun flares and every ridge line flashes
 *
 * Android adaptations:
 *  - Bass, mid and treble come from [EffectDynamics] (beat, mid and treble
 *    clamped to 0..1.5, `motion` smoothed slowly, `light` quickly), which is the
 *    same split upstream uses; no extra rescaling is needed.
 *  - The 512-bin log-normalised FFT is split with [SpectrumLevels] (squared band
 *    edges, non-finite bins read as silence) and normalised by a decaying peak.
 *  - Twinkle and ridge glitter follow [clock], which advances by the elapsed
 *    time in 60 Hz frame units (`step`), never by the frame counter.
 *  - [GLDraw.polygon] fills a fan around the vertex centroid, which is wrong for
 *    a ridge outline, so each ridge is filled as vertical trapezoid columns and
 *    ridge and lane lines are thin filled quads. No scissor is used: the sun
 *    disc is shaded as horizontal trapezoid slices instead of clipped circles.
 *  - Every frame is fully repainted by the sky, so the mode keeps no trails.
 */
class HorizonMode : BaseMode() {

    override val name = "Horizon"

    companion object {
        internal const val N_ROWS   = 26
        internal const val N_SIDE   = 22              // mountain columns on each side of the valley
        internal const val COLS     = 2 * (N_SIDE + 1)
        internal const val ROW_SLOTS = N_ROWS + 2
        internal const val N_STARS  = 110
        private const val Z_NEAR    = 0.9f
        private const val Z_FAR     = 22f
        private const val CAMERA_H  = 1.0f
        private const val VALLEY    = 1.3f            // half width of the flat valley, in world units
        private const val HORIZON   = 0.50f           // horizon height as a fraction of the frame
        private const val SKY_BANDS = 20
        private const val HALO      = 14
        private const val SLICES    = 14
        private const val SEED      = 0x4852L
        private const val STAR_SEED = 0x5354L
        private val LANES = floatArrayOf(-1f, -0.5f, 0f, 0.5f, 1f)
        private val TAU = 2.0 * PI
    }

    private var hue = 0.62f
    private var peak = 1e-3f
    private var rng = Random(SEED)
    private var newestId = -1L

    /** Rows travelled; exposed for JVM contract checks. */
    internal var time = 0.0

    /** Elapsed time in 60 Hz frames, drives twinkle and glitter. */
    internal var clock = 0.0
        private set

    /** Smoothed spectrum levels (0..1) used to shape new ridges. */
    internal val levels = FloatArray(N_SIDE)

    /** Ridge heights by row identity modulo [ROW_SLOTS]; a row keeps its shape while it travels. */
    internal val ridgeRows = FloatArray(ROW_SLOTS * COLS)

    /** Identity of the newest row created so far. */
    internal val newestRow: Long get() = newestId

    // Fixed geometry along a ridge: 0 at the valley edge, 1 at the outer edge.
    private val tAxis = FloatArray(COLS)
    private val sideAxis = FloatArray(COLS)
    private val riseAxis = FloatArray(COLS)
    private val stars = FloatArray(N_STARS * 3)

    // Scratch storage; nothing is allocated while drawing.
    private val rawBands = FloatArray(N_SIDE)
    private val hillsA = FloatArray(COLS)
    private val hillsB = FloatArray(COLS)
    private val pts = FloatArray(COLS * 2)
    private val quad = FloatArray(8)
    private val col = FloatArray(4)
    private val skyColour = FloatArray(3)

    init {
        for (c in 0 until COLS) {
            if (c <= N_SIDE) {
                tAxis[c] = 1f - c.toFloat() / N_SIDE; sideAxis[c] = -1f
            } else {
                tAxis[c] = (c - N_SIDE - 1).toFloat() / N_SIDE; sideAxis[c] = 1f
            }
            val r = (tAxis[c] / 0.30f).coerceIn(0f, 1f)
            riseAxis[c] = r * r * (3f - 2f * r)
        }
        reset()
    }

    override fun reset() {
        hue = 0.62f; peak = 1e-3f
        time = 0.0; clock = 0.0
        levels.fill(0f)
        rng = Random(SEED)
        newestId = -1L
        fillRows((N_ROWS + 1).toLong())
        val starRng = Random(STAR_SEED)
        for (i in stars.indices) stars[i] = starRng.nextFloat()
    }

    // ── Ridges ────────────────────────────────────────────────────────────────

    /** Three-tap smoothing with zero padding, like numpy's `convolve(..., mode="same")`. */
    private fun smooth3(src: FloatArray, dst: FloatArray) {
        for (i in 0 until COLS) {
            val before = if (i > 0) src[i - 1] else 0f
            val after = if (i < COLS - 1) src[i + 1] else 0f
            dst[i] = 0.25f * before + 0.5f * src[i] + 0.25f * after
        }
    }

    /** Create the ridge for [slot] from the current spectrum plus resting hills. */
    private fun makeRidge(slot: Int) {
        var mean = 0f
        for (i in 0 until N_SIDE) mean += levels[i]
        mean /= N_SIDE
        for (c in 0 until COLS) hillsA[c] = rng.nextFloat()
        smooth3(hillsA, hillsB)
        for (c in 0 until COLS) {
            val level = when {
                c < N_SIDE     -> levels[N_SIDE - 1 - c]
                c < N_SIDE + 2 -> 0f
                else           -> levels[c - N_SIDE - 2]
            }
            val h = hillsB[c]
            hillsA[c] = 0.22f + 0.30f * h + (0.45f * mean + 0.75f * level) * (0.6f + 0.8f * h)
        }
        smooth3(hillsA, hillsB)
        val base = slot * COLS
        for (c in 0 until COLS) ridgeRows[base + c] = riseAxis[c] * hillsB[c]
    }

    /** Create every row up to identity [newest]; bounded by the buffer size. */
    private fun fillRows(newest: Long) {
        val slots = ROW_SLOTS.toLong()
        var id = maxOf(newestId + 1, newest - slots + 1)
        while (id <= newest) {
            makeRidge((id % slots).toInt())
            id++
        }
        newestId = maxOf(newestId, newest)
    }

    private fun updateSpectrum(audio: AudioData, response: EffectDynamics) {
        SpectrumLevels.bands(audio.fft, rawBands)
        var loudest = 0f
        for (i in 0 until N_SIDE) loudest = maxOf(loudest, rawBands[i])
        peak = maxOf(1e-3f, response.smooth(peak, loudest, 0.01f), loudest)
        for (i in 0 until N_SIDE) {
            levels[i] = response.smooth(levels[i], sqrt(rawBands[i] / peak), 0.3f)
        }
    }

    // ── Drawing helpers ───────────────────────────────────────────────────────

    private fun colour(h: Float, s: Float, l: Float) {
        GLDraw.hsl(h, s, l.coerceIn(0f, 1f), 1f, col)
    }

    private fun depthAt(s: Float): Float = Z_NEAR * (Z_FAR / Z_NEAR).pow(s.coerceIn(0f, 1f))

    /** Sky gradient, stars and the sun. Leaves the horizon sky colour in [skyColour]. */
    private fun drawSky(draw: GLDraw, W: Float, H: Float, horizonY: Float, minDim: Float,
                        bass: Float, flash: Float, high: Float) {
        for (i in 0 until SKY_BANDS) {
            val d = (i + 0.5f) / SKY_BANDS
            colour(hue + 0.10f + 0.22f * d, 0.85f,
                0.015f + (0.16f + 0.10f * bass + 0.06f * flash) * d.pow(2.2f))
            val y0 = horizonY * i / SKY_BANDS
            val y1 = horizonY * (i + 1) / SKY_BANDS
            draw.rect(0f, y0, W, y1 - y0, col[0], col[1], col[2], 1f)
        }
        skyColour[0] = col[0]; skyColour[1] = col[1]; skyColour[2] = col[2]
        draw.rect(0f, horizonY, W, H - horizonY, col[0], col[1], col[2], 1f)

        val size = maxOf(1f, (minDim / 540f).roundToInt().toFloat())
        for (i in 0 until N_STARS) {
            val sx = stars[i * 3]; val sy = stars[i * 3 + 1]; val phase = stars[i * 3 + 2]
            val twinkle = 0.5f + 0.5f * sin(clock * 0.07 + phase.toDouble() * TAU * 3.0).toFloat()
            val light = ((0.25f + 0.75f * twinkle * (0.4f + high)) * (1f - sy).pow(0.6f)).coerceIn(0f, 1f)
            val g = light * 235f / 255f
            draw.rect(sx * W, sy * horizonY * 0.92f, size, size, g, g, minOf(1f, g + 20f / 255f), 1f)
        }

        val radius = minDim * 0.15f * (1f + 0.10f * flash + 0.05f * bass)
        val cx = W / 2f
        val cy = horizonY - radius * 0.25f
        // A soft halo: many faint discs, each a little smaller and brighter.
        for (i in 0 until HALO) {
            val frac = (i + 1f) / HALO
            val lum = 0.03f + 0.17f * frac * frac * (1f + 0.6f * flash)
            colour(hue + 0.34f + 0.06f * frac, 0.9f, lum)
            draw.circle(cx, cy, radius * (2.3f - 1.25f * frac), col[0], col[1], col[2], 1f, true, 48)
        }
        // The disc is shaded in horizontal slices, bright on top.
        val top = cy - radius
        for (i in 0 until SLICES) {
            val y0 = top + 2f * radius * i / SLICES
            val y1 = top + 2f * radius * (i + 1) / SLICES
            val d0 = y0 - cy; val d1 = y1 - cy
            val w0 = sqrt(maxOf(0f, radius * radius - d0 * d0))
            val w1 = sqrt(maxOf(0f, radius * radius - d1 * d1))
            quad[0] = cx - w0; quad[1] = y0
            quad[2] = cx + w0; quad[3] = y0
            quad[4] = cx + w1; quad[5] = y1
            quad[6] = cx - w1; quad[7] = y1
            val frac = i / (SLICES - 1f)
            colour(hue + 0.52f - 0.14f * frac, 1f, minOf(0.9f, 0.72f - 0.22f * frac + 0.12f * flash))
            draw.polygon(quad, 4, col[0], col[1], col[2], 1f, true)
        }
    }

    // ── Frame ─────────────────────────────────────────────────────────────────

    override fun draw(draw: GLDraw, audio: AudioData, tick: Int) {
        val W = draw.W.toFloat(); val H = draw.H.toFloat()
        val minDim = minOf(W, H)
        val response = prepare(draw, audio); val step = response.step
        val bass = response.motion[0]; val mid = response.motion[1]
        val flash = response.light[0] * response.accent
        val high = response.light[2]

        clock += step.toDouble()
        hue = (hue + (0.0006f + mid * 0.0010f) * step) % 1f
        time += ((0.028f + bass * 0.035f + mid * 0.02f) * step * response.motionScale).toDouble()
        updateSpectrum(audio, response)

        val horizonY = H * HORIZON
        drawSky(draw, W, H, horizonY, minDim, bass, flash, high)

        // Row k keeps identity first + k and slides from the horizon to the
        // camera. Depth is exponential in the row coordinate, which spaces the
        // rows evenly on screen and lets them speed up as they approach.
        val n = N_ROWS
        val first = floor(time).toLong()
        fillRows(first + n)
        val frac = (time - first).toFloat()
        val focal = minDim * 0.9f
        val bottom = H + 4f

        for (k in n downTo 0) {
            val s = (k - frac) / n
            if (s < 0f) continue
            val z = depthAt(s)
            val near = 1f - s.coerceIn(0f, 1f)
            val id = first + k
            val base = (id % ROW_SLOTS).toInt() * COLS
            val scale = focal / z

            // The outer edge always reaches past the sides of the frame, so wide
            // screens show terrain to the edges instead of bare sky.
            val edge = maxOf(VALLEY + 1.5f + 1.15f * z, W * 0.55f * z / focal)
            val swell = (0.9f + 0.22f * z) * (1f + 0.35f * bass * near)
            for (c in 0 until COLS) {
                val x = sideAxis[c] * (VALLEY + tAxis[c] * (edge - VALLEY))
                val y = ridgeRows[base + c] * swell
                pts[c * 2] = W / 2f + x * scale
                pts[c * 2 + 1] = horizonY + (CAMERA_H - y) * scale
            }

            // A new row fades in out of the sky on the horizon instead of
            // popping up: both its fill and its ridge line start in the sky colour.
            val arrive = ((1f - s) * n / 1.5f).coerceIn(0f, 1f)
            colour(hue + 0.08f + 0.10f * near, 0.75f, 0.025f + 0.09f * near.pow(1.5f))
            val fr = skyColour[0] + (col[0] - skyColour[0]) * arrive
            val fg = skyColour[1] + (col[1] - skyColour[1]) * arrive
            val fb = skyColour[2] + (col[2] - skyColour[2]) * arrive
            for (c in 0 until COLS - 1) {
                quad[0] = pts[c * 2];     quad[1] = pts[c * 2 + 1]
                quad[2] = pts[c * 2 + 2]; quad[3] = pts[c * 2 + 3]
                quad[4] = pts[c * 2 + 2]; quad[5] = bottom
                quad[6] = pts[c * 2];     quad[7] = bottom
                draw.polygon(quad, 4, fr, fg, fb, 1f, true)
            }

            val glitter = (0.5 + 0.5 * sin(clock * 0.31 + id * 1.7)).toFloat()
            val lineL = (0.14f + 0.46f * near.pow(0.8f) + 0.20f * flash +
                0.25f * high * glitter * (1f - near)) * arrive
            colour(hue + 0.30f - 0.32f * near, 1f, minOf(lineL, 0.85f))
            val width = maxOf(1f, minDim / 540f * (1f + 2.2f * near * near))
            val half = width / 2f
            for (c in 0 until COLS - 1) {
                val x0 = pts[c * 2]; val y0 = pts[c * 2 + 1]
                val x1 = pts[c * 2 + 2]; val y1 = pts[c * 2 + 3]
                quad[0] = x0; quad[1] = y0 - half
                quad[2] = x1; quad[3] = y1 - half
                quad[4] = x1; quad[5] = y1 + half
                quad[6] = x0; quad[7] = y0 + half
                draw.polygon(quad, 4, col[0], col[1], col[2], 1f, true)
            }

            // Lane lines run on the valley floor from this row to the next one.
            if (k > 0) {
                val zNext = depthAt((k - 1 - frac) / n)
                val scaleNext = focal / zNext
                val groundY = horizonY + CAMERA_H * scale
                val nextY = minOf(horizonY + CAMERA_H * scaleNext, bottom)
                colour(hue + 0.50f, 0.9f, (0.10f + 0.30f * near) * (1f + 0.4f * flash))
                for (j in LANES.indices) {
                    val a = W / 2f + LANES[j] * VALLEY * scale
                    val b = W / 2f + LANES[j] * VALLEY * scaleNext
                    quad[0] = a - half; quad[1] = groundY
                    quad[2] = a + half; quad[3] = groundY
                    quad[4] = b + half; quad[5] = nextY
                    quad[6] = b - half; quad[7] = nextY
                    draw.polygon(quad, 4, col[0], col[1], col[2], 1f, true)
                }
            }
        }
    }
}
