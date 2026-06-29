package com.androsaver.visualizer.modes

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw
import kotlin.math.*

/**
 * Five translucent sinusoidal ribbons undulate horizontally across the screen.
 * Port of psysuals Aurora (v3.11.0).
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
    private var lastH = 0f

    private val phases = Array(DEFS.size) { FloatArray(3) }
    private val ks     = Array(DEFS.size) { FloatArray(3) }

    // Reusable buffers
    private val quadPts     = FloatArray(8)
    private val glowQuadPts = FloatArray(8)

    private var ytTmp  = FloatArray(0)
    private var ybTmp  = FloatArray(0)
    private var padTmp = FloatArray(0)

    override fun reset() {
        hue = 0.42f
        bloom = 0f
        beatPrev = 0f
        lastW = 0f
        lastH = 0f
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

        if (W != lastW || H != lastH) {
            lastW = W
            lastH = H
            val nPts = (W / STEP).toInt() + 1
            xs = FloatArray(nPts) { i -> minOf(i * STEP.toFloat(), W) }

            val kUnit = (2f * PI_F) / W
            for (ri in DEFS.indices) {
                val harms = DEFS[ri].harmonics
                for (j in harms.indices) {
                    ks[ri][j] = kUnit * harms[j].km
                }
            }

            ytTmp  = FloatArray(nPts)
            ybTmp  = FloatArray(nPts)
            padTmp = FloatArray(nPts)
        }

        val beat   = audio.beat
        val bass   = beat
        val mid    = audio.mid
        val treble = audio.treble

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
                wave = wave / maxOf(totW, 1e-6f) * amp
                ytTmp[i] = cy + wave
                ybTmp[i] = cy + wave + rh
                padTmp[i] = maxOf(2f, rh * 0.5f)
            }

            val baseColor = GLDraw.hsl(ribbonHue, 1f, 0.5f)

            // Outer glow (10-18% intensity)
            val gi = 0.10f + bloom * 0.08f
            // Core ribbon (28-55% intensity)
            val ci = 0.28f + bloom * 0.22f + bass * 0.08f

            val rGlow = baseColor[0] * gi; val gGlow = baseColor[1] * gi; val bGlow = baseColor[2] * gi
            val rCore = baseColor[0] * ci; val gCore = baseColor[1] * ci; val bCore = baseColor[2] * ci

            for (i in 0 until n - 1) {
                val x0 = xs[i]; val x1 = xs[i + 1]
                val yt0 = ytTmp[i]; val yt1 = ytTmp[i + 1]
                val yb0 = ybTmp[i]; val yb1 = ybTmp[i + 1]
                val pad0 = padTmp[i]; val pad1 = padTmp[i + 1]
                var fadeDiv = 1f
                if (yt0 < 0f) fadeDiv += 1f
                if (yt1 < 0f) fadeDiv += 1f
                if (yb0 > H) fadeDiv += 1f
                if (yb1 > H) fadeDiv += 1f
                val fadeMul = 1f / fadeDiv

                glowQuadPts[0] = x0;  glowQuadPts[1] = yt0 - pad0
                glowQuadPts[2] = x1;  glowQuadPts[3] = yt1 - pad1
                glowQuadPts[4] = x1;  glowQuadPts[5] = yb1 + pad1
                glowQuadPts[6] = x0;  glowQuadPts[7] = yb0 + pad0

                quadPts[0] = x0;  quadPts[1] = yt0
                quadPts[2] = x1;  quadPts[3] = yt1
                quadPts[4] = x1;  quadPts[5] = yb1
                quadPts[6] = x0;  quadPts[7] = yb0

                draw.polygon(glowQuadPts, rGlow * fadeMul, gGlow * fadeMul, bGlow * fadeMul, 1f, filled = true)
                draw.polygon(quadPts, rCore * fadeMul, gCore * fadeMul, bCore * fadeMul, 1f, filled = true)
            }
        }

        draw.setNormalBlend()
    }
}
