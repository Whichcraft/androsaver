package com.androsaver.visualizer.modes

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw
import kotlin.math.*

/**
 * AuroraMode — Northern Lights curtains.
 * Five translucent sinusoidal ribbons undulate horizontally across the screen.
 * Port of psysuals Aurora (v3.2.0).
 */
class AuroraMode : BaseMode() {

    override val name = "Aurora"

    private companion object {
        const val STEP = 5
        const val PI_F = Math.PI.toFloat()
    }

    private class Harmonic(val km: Float, val speed: Float, val weight: Float)
    private class RibbonDef(val yFraction: Float, val hueOffset: Float, val harmonics: List<Harmonic>)

    private val DEFS = listOf(
        RibbonDef(0.20f, 0.00f, listOf(Harmonic(1.0f, 0.50f, 1.00f), Harmonic(2.3f, -0.85f, 0.55f), Harmonic(5.1f, 0.30f, 0.25f))),
        RibbonDef(0.35f, 0.18f, listOf(Harmonic(0.8f, -0.42f, 1.00f), Harmonic(1.9f, 0.72f, 0.55f), Harmonic(4.3f, -0.28f, 0.25f))),
        RibbonDef(0.50f, 0.36f, listOf(Harmonic(1.2f, 0.58f, 1.00f), Harmonic(2.7f, -0.60f, 0.55f), Harmonic(3.8f, 0.38f, 0.25f))),
        RibbonDef(0.65f, 0.55f, listOf(Harmonic(0.9f, -0.48f, 1.00f), Harmonic(2.1f, 0.65f, 0.55f), Harmonic(4.7f, -0.33f, 0.25f))),
        RibbonDef(0.80f, 0.74f, listOf(Harmonic(1.1f, 0.45f, 1.00f), Harmonic(2.5f, -0.78f, 0.55f), Harmonic(3.5f, 0.35f, 0.25f)))
    )

    private var hue = 0.42f
    private var bloom = 0f
    private var beatPrev = 0f
    
    private var xs = FloatArray(0)
    private var lastW = 0f

    private val phases = Array(DEFS.size) { FloatArray(3) }
    private val ks     = Array(DEFS.size) { FloatArray(3) }

    private val savedTopPts = Array(DEFS.size) { FloatArray(0) }
    private val savedHues   = FloatArray(DEFS.size)

    // Reusable buffers
    private var polyPts = FloatArray(0)
    private var glowPts = FloatArray(0)
    private var topPts  = FloatArray(0)

    private var ytTmp  = FloatArray(0)
    private var ybTmp  = FloatArray(0)
    private var padTmp = FloatArray(0)

    override fun reset() {
        hue = 0.42f
        bloom = 0f
        beatPrev = 0f
        lastW = 0f
        xs = FloatArray(0)
        ytTmp = FloatArray(0)
        ybTmp = FloatArray(0)
        padTmp = FloatArray(0)
        for (ri in DEFS.indices) {
            phases[ri].fill(0f)
            ks[ri].fill(0f)
        }
    }

    override fun draw(draw: GLDraw, audio: AudioData, tick: Int) {
        val W = draw.W.toFloat(); val H = draw.H.toFloat()

        // Slowly fade out previous frame's trails
        // Python uses TRAIL_ALPHA=14 -> 14/255 ≈ 0.055f alpha
        draw.fadeBlack(14f / 255f)

        if (W != lastW) {
            lastW = W
            val nPts = (W / STEP).toInt() + 1
            xs = FloatArray(nPts) { i -> minOf(i * STEP.toFloat(), W) }

            val kUnit = (2f * PI_F) / W
            for (ri in DEFS.indices) {
                val harms = DEFS[ri].harmonics
                for (j in harms.indices) {
                    ks[ri][j] = kUnit * harms[j].km
                }
                savedTopPts[ri] = FloatArray(nPts * 2)
            }

            polyPts = FloatArray(nPts * 4)
            glowPts = FloatArray(nPts * 4)
            topPts  = FloatArray(nPts * 2)

            ytTmp  = FloatArray(nPts)
            ybTmp  = FloatArray(nPts)
            padTmp = FloatArray(nPts)
        }

        val fft    = audio.fft
        val beat   = audio.beat
        val bass   = fft.meanSlice(0, 6)
        val mid    = fft.meanSlice(6, 30)
        val treble = fft.meanSlice(100, 256)

        hue = (hue + 0.003f + mid * 0.002f) % 1f

        if (beat > 0.8f && beatPrev <= 0.8f) {
            bloom = 1.0f
            hue = (hue + 0.10f) % 1f
        }
        beatPrev = beat
        bloom = maxOf(0f, bloom - 0.03f)

        val spd = 1.0f + treble * 4.0f + beat * 1.5f
        val amp = H * (0.04f + bass * 0.12f + bloom * 0.07f)
        val rh  = H * (0.09f + mid  * 0.05f)

        val n = xs.size
        draw.setAdditiveBlend()

        for (ri in DEFS.indices) {
            val def = DEFS[ri]
            val harms = def.harmonics
            val cy = def.yFraction * H
            val ribbonHue = (hue + def.hueOffset) % 1f

            // Update phases once per frame for all harmonics of this ribbon
            for (j in harms.indices) {
                phases[ri][j] = (phases[ri][j] + harms[j].speed * spd * 0.016f) % (2f * PI_F)
            }

            // Compute ribbon wave values in a single forward pass
            for (i in 0 until n) {
                val x = xs[i]
                var wave = 0f
                var totW = 0f
                for (j in harms.indices) {
                    wave += sin(x * ks[ri][j] + phases[ri][j]) * harms[j].weight
                    totW += harms[j].weight
                }
                wave = wave / totW * amp
                ytTmp[i] = cy + wave
                ybTmp[i] = cy + wave + rh
                padTmp[i] = maxOf(2f, rh * 0.5f)
            }

            // Assemble polygons and lines using the pre-computed arrays
            for (i in 0 until n) {
                val revIdx = n - 1 - i

                polyPts[i * 2] = xs[i]
                polyPts[i * 2 + 1] = ytTmp[i]
                polyPts[n * 2 + i * 2] = xs[revIdx]
                polyPts[n * 2 + i * 2 + 1] = ybTmp[revIdx]

                glowPts[i * 2] = xs[i]
                glowPts[i * 2 + 1] = ytTmp[i] - padTmp[i]
                glowPts[n * 2 + i * 2] = xs[revIdx]
                glowPts[n * 2 + i * 2 + 1] = ybTmp[revIdx] + padTmp[revIdx]

                topPts[i * 2] = xs[i]
                topPts[i * 2 + 1] = ytTmp[i]
            }

            val baseColor = GLDraw.hsl(ribbonHue, 1f, 0.5f)

            // Outer glow (10-18% intensity)
            val gi = 0.10f + bloom * 0.08f
            draw.polygon(glowPts, baseColor[0] * gi, baseColor[1] * gi, baseColor[2] * gi, 1f, filled = true)

            // Core ribbon (28-55% intensity)
            val ci = 0.28f + bloom * 0.22f + bass * 0.08f
            draw.polygon(polyPts, baseColor[0] * ci, baseColor[1] * ci, baseColor[2] * ci, 1f, filled = true)

            System.arraycopy(topPts, 0, savedTopPts[ri], 0, topPts.size)
            savedHues[ri] = ribbonHue
        }

        // Draw bright sharp edge lines on top with normal blend
        draw.setNormalBlend()
        val bl = minOf(0.80f + bloom * 0.18f, 0.98f)
        for (ri in DEFS.indices) {
            val c = GLDraw.hsl(savedHues[ri], 1f, bl)
            draw.lineStrip(savedTopPts[ri], c[0], c[1], c[2], 1f)
        }
    }
}
