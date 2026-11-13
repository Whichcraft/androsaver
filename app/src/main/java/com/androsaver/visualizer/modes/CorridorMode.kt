package com.androsaver.visualizer.modes

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.EffectDynamics
import com.androsaver.visualizer.GLDraw
import kotlin.math.*

/**
 * CorridorMode — a first-person ride down a solid neon corridor.
 * Port of the psysuals 3.22 `Corridor` rebuild.
 *
 * Wall, floor and ceiling panels are drawn in true perspective and painted far
 * to near, so the walls close the view. Neon ribs are set into the walls;
 * between them run light bars on both walls, a broken light strip along the
 * ceiling and two lane lines along the floor. The passage bends gently, fades
 * into fog and ends in a glowing doorway.
 *
 *   Bass     → ride speed, wall and doorway glow
 *   Mid      → ride speed, hue drift
 *   Spectrum → wall light bars by depth: bass near the camera, treble far away
 *   Beat     → a pulse of light shoots down the corridor, flaring each rib
 *
 * Every frame is fully repainted, so the mode keeps no trails.
 */
class CorridorMode : BaseMode() {

    override val name = "Corridor"

    private companion object {
        const val N_SEGMENTS = 32
        const val Z_FAR      = 13f
        const val Z_NEAR     = 0.42f
        const val HALF_W     = 1.7f
        const val HALF_H     = 1.0f
        const val RIB_DEPTH  = 0.10f   // depth of a neon rib, in world units
        const val RIB_EVERY  = 2       // segments per rib
        const val GATE_EVERY = 8       // segments per wider, brighter gate
        const val MAX_PULSES = 3
        const val SPACING    = Z_FAR / N_SEGMENTS

        // Cross-section points as fractions of the half extents (y down):
        // corners TL TR BR BL, ceiling light strip, floor lane lines, wall bars.
        const val POINTS = 14
        val SECTION_X = floatArrayOf(-1f, 1f, 1f, -1f, -0.20f, 0.20f,
            -0.58f, -0.52f, 0.52f, 0.58f, -1f, -1f, 1f, 1f)
        val SECTION_Y = floatArrayOf(-1f, -1f, 1f, 1f, -1f, -1f,
            1f, 1f, 1f, 1f, -0.16f, 0.16f, -0.16f, 0.16f)
    }

    private var hue  = 0f
    private var time = 0.0          // distance travelled, in world units
    private var beatPrev = 0f
    private var peak = 1e-3f
    private val spectrum = FloatArray(N_SEGMENTS + 1)

    // Beat pulses running away from the camera.
    private val pulseZ = FloatArray(MAX_PULSES)
    private val pulseStrength = FloatArray(MAX_PULSES)
    private var pulseCount = 0

    // Scratch storage; nothing is allocated while drawing.
    private val zRaw  = FloatArray(N_SEGMENTS + 1)
    private val zs    = FloatArray(N_SEGMENTS + 1)
    private val ribZ  = FloatArray(N_SEGMENTS + 1)
    private val pts   = FloatArray((N_SEGMENTS + 1) * POINTS * 2)
    private val rib   = FloatArray((N_SEGMENTS + 1) * 4 * 2)
    private val quad  = FloatArray(8)
    private val col   = FloatArray(4)

    override fun reset() {
        hue = 0f; time = 0.0; beatPrev = 0f; peak = 1e-3f
        spectrum.fill(0f); pulseCount = 0
    }

    /** Distance travelled by the camera; exposed for JVM contract checks. */
    internal val travelled: Double get() = time
    internal val activePulses: Int get() = pulseCount

    private fun pathX(s: Double): Double = 2.0 * sin(s * 0.10) + 0.35 * sin(s * 0.23 + 1.0)
    private fun pathY(s: Double): Double = 0.7 * sin(s * 0.13 + 2.0)

    /**
     * Project the first [count] cross-section points at depth [z] into [out].
     * The offset is relative to the camera with the local heading removed, so
     * the view always looks along the corridor and bends appear ahead.
     */
    private fun project(z: Float, out: FloatArray, offset: Int, count: Int,
                        W: Float, H: Float, cosRoll: Float, sinRoll: Float) {
        val s = time
        val headingX = 0.2 * cos(s * 0.10) + 0.0805 * cos(s * 0.23 + 1.0)
        val headingY = 0.091 * cos(s * 0.13 + 2.0)
        val ox = (pathX(s + z) - pathX(s) - headingX * z).toFloat()
        val oy = (pathY(s + z) - pathY(s) - headingY * z).toFloat()
        val scale = minOf(W, H) * 0.74f / z
        for (i in 0 until count) {
            val x = SECTION_X[i] * HALF_W + ox
            val y = SECTION_Y[i] * HALF_H + oy
            out[offset + i * 2]     = (x * cosRoll - y * sinRoll) * scale + W / 2f
            out[offset + i * 2 + 1] = (x * sinRoll + y * cosRoll) * scale + H / 2f
        }
    }

    private fun updateSpectrum(audio: AudioData, response: EffectDynamics) {
        // Squared band edges give the bass more of the corridor's near end.
        SpectrumLevels.bands(audio.fft, zRaw)
        var loudest = 0f
        for (k in 0..N_SEGMENTS) loudest = maxOf(loudest, zRaw[k])
        peak = maxOf(1e-3f, response.smooth(peak, loudest, 0.01f), loudest)
        for (k in 0..N_SEGMENTS) {
            spectrum[k] = response.smooth(spectrum[k], sqrt(zRaw[k] / peak), 0.3f)
        }
    }

    private fun colour(h: Float, s: Float, l: Float) {
        GLDraw.hsl(h, s, l.coerceIn(0f, 1f), 1f, col)
    }

    /** Fill the quad spanned by points [i] and [j] of a near and a far slice. */
    private fun span(draw: GLDraw, near: FloatArray, nearBase: Int,
                     far: FloatArray, farBase: Int, i: Int, j: Int) {
        quad[0] = near[nearBase + i * 2]; quad[1] = near[nearBase + i * 2 + 1]
        quad[2] = near[nearBase + j * 2]; quad[3] = near[nearBase + j * 2 + 1]
        quad[4] = far[farBase + j * 2];   quad[5] = far[farBase + j * 2 + 1]
        quad[6] = far[farBase + i * 2];   quad[7] = far[farBase + i * 2 + 1]
        draw.polygon(quad, 4, col[0], col[1], col[2], 1f, true)
    }

    override fun draw(draw: GLDraw, audio: AudioData, tick: Int) {
        val W = draw.W.toFloat(); val H = draw.H.toFloat()
        val response = prepare(draw, audio); val step = response.step
        val bass = response.motion[0]; val mid = response.motion[1]; val high = response.motion[2]
        val flash = response.light[0] * response.accent

        hue = (hue + (0.0012f + mid * 0.0015f) * step) % 1f
        time += ((0.045f + bass * 0.05f + mid * 0.03f + high * 0.01f) * step * response.motionScale).toDouble()
        updateSpectrum(audio, response)

        if (audio.beat > 0.60f && beatPrev <= 0.60f && pulseCount < MAX_PULSES) {
            pulseZ[pulseCount] = 0f
            pulseStrength[pulseCount] = minOf(1.5f, audio.beat)
            pulseCount++
        }
        beatPrev = audio.beat
        var kept = 0
        for (i in 0 until pulseCount) {
            val z = pulseZ[i] + 0.45f * step
            if (z < Z_FAR + 2f) {
                pulseZ[kept] = z; pulseStrength[kept] = pulseStrength[i]; kept++
            }
        }
        pulseCount = kept

        // Slice k sits at the camera-side edge of segment k.
        val first = floor(time / SPACING).toLong()
        val offset = (time - first * SPACING.toDouble()).toFloat()
        val roll = 0.09f * sin(time * 0.07).toFloat()
        val cosRoll = cos(roll); val sinRoll = sin(roll)
        for (k in 0..N_SEGMENTS) {
            zRaw[k] = k * SPACING - offset
            zs[k] = maxOf(zRaw[k], Z_NEAR)
            val gate = (first + k) % GATE_EVERY == 0L
            ribZ[k] = zRaw[k] + (if (gate) 2.2f else 1f) * RIB_DEPTH
            project(zs[k], pts, k * POINTS * 2, POINTS, W, H, cosRoll, sinRoll)
            project(maxOf(ribZ[k], Z_NEAR), rib, k * 8, 4, W, H, cosRoll, sinRoll)
        }

        val glow = 1f + flash * 0.5f + bass * 0.4f

        // The whole frame is repainted; clear whatever the last mode left.
        draw.rect(0f, 0f, W, H, 0f, 0f, 0f, 1f)

        // The doorway at the far end, glowing with the bass.
        val farBase = N_SEGMENTS * POINTS * 2
        var doorX = 0f; var doorY = 0f
        for (i in 0 until 4) { doorX += pts[farBase + i * 2]; doorY += pts[farBase + i * 2 + 1] }
        doorX /= 4f; doorY /= 4f
        val doorHue = hue + ((first + N_SEGMENTS) % 2000L) * 0.035f + 0.5f
        val doorL = minOf(0.6f, 0.22f + bass * 0.25f + flash * 0.15f)
        for (layer in 0 until 3) {
            val shrink = 1f - layer * 0.28f
            for (i in 0 until 4) {
                quad[i * 2]     = doorX + (pts[farBase + i * 2] - doorX) * shrink
                quad[i * 2 + 1] = doorY + (pts[farBase + i * 2 + 1] - doorY) * shrink
            }
            colour(doorHue, 0.75f, doorL + layer * 0.15f)
            draw.polygon(quad, 4, col[0], col[1], col[2], 1f, true)
        }

        for (k in N_SEGMENTS - 1 downTo 0) {
            if (zs[k + 1] <= Z_NEAR) continue
            val id = first + k
            val a = k * POINTS * 2            // near edge of the segment
            val b = (k + 1) * POINTS * 2      // far edge of the segment
            val fog = (1f - zs[k] / Z_FAR).coerceIn(0f, 1f).pow(1.6f)
            val stripe = if ((id / RIB_EVERY) % 2 == 0L) 1f else 0.6f
            val h = hue + (id % 2000L) * 0.035f
            val level = spectrum[k]
            var pulse = 0f
            for (i in 0 until pulseCount) {
                val d = (zs[k] - pulseZ[i]) / 0.8f
                pulse += pulseStrength[i] * exp(-d * d)
            }
            pulse = minOf(pulse, 1.5f) * (0.35f + 0.65f * fog)

            colour(h - 0.04f, 0.8f, fog * stripe * 0.035f * glow)
            span(draw, pts, a, pts, b, 0, 1)                         // ceiling
            colour(h + 0.04f, 0.8f, fog * stripe * (0.04f + 0.05f * level) * glow)
            span(draw, pts, a, pts, b, 3, 2)                         // floor
            colour(h, 0.85f, fog * stripe * 0.075f * glow)
            span(draw, pts, a, pts, b, 0, 3)                         // left wall
            span(draw, pts, a, pts, b, 1, 2)                         // right wall

            if (id % RIB_EVERY == 1L) {                              // lit segment
                colour(h, 0.35f, minOf(fog * 0.70f + pulse * 0.2f, 0.95f))
                span(draw, pts, a, pts, b, 4, 5)                     // ceiling lamp
                colour(h + 0.12f, 1f, minOf(fog * (0.10f + 0.55f * level) * glow + pulse * 0.25f, 0.85f))
                span(draw, pts, a, pts, b, 10, 11)                   // wall bars
                span(draw, pts, a, pts, b, 12, 13)
            }
            colour(h + 0.5f, 0.9f, fog * 0.34f * glow)
            span(draw, pts, a, pts, b, 6, 7)                         // lane lines
            span(draw, pts, a, pts, b, 8, 9)

            // The neon rib set into this segment's camera-side edge.
            if (id % RIB_EVERY == 0L && ribZ[k] > Z_NEAR) {
                val gate = id % GATE_EVERY == 0L
                colour(h, 1f, minOf(fog * (if (gate) 0.52f else 0.30f) * (1f + flash * 0.3f) + pulse * 0.4f, 0.9f))
                val r = k * 8
                for (i in 0 until 4) span(draw, pts, a, rib, r, i, (i + 1) % 4)
                // A hairline keeps distant gates visible once they get thin.
                if (gate) {
                    for (i in 0 until 4) {
                        val j = (i + 1) % 4
                        draw.antiAliasedLine(rib[r + i * 2], rib[r + i * 2 + 1],
                            rib[r + j * 2], rib[r + j * 2 + 1], col[0], col[1], col[2], 1f, 1f)
                    }
                }
            }
        }
    }
}
