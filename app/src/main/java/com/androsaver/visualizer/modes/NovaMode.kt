package com.androsaver.visualizer.modes

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw
import kotlin.math.*

/**
 * NovaMode — Waveform kaleidoscope with 7-fold mirror symmetry and 4 concentric layers.
 * Port of Python class `Nova`.
 */
class NovaMode : BaseMode() {

    override val name = "Nova"

    companion object {
        private const val N_SYM    = 7
        private const val N_LAYERS = 4
        private const val N_WAVE   = 120
        private const val TAU = (PI * 2).toFloat()
    }

    private var hue  = 0f
    private var time = 0f

    private val rot  = FloatArray(N_LAYERS)
    private val rvel = FloatArray(N_LAYERS)
    private val poff = FloatArray(N_LAYERS)
    private val pvel = FloatArray(N_LAYERS)
    private val bands = FloatArray(N_LAYERS)
    private val wave = FloatArray(N_WAVE)
    private val waveRev = FloatArray(N_WAVE)
    private val pts = FloatArray(N_WAVE * 2)
    private val triPts = FloatArray(6)
    private val colorScratch = FloatArray(4)

    override fun reset() {
        hue = 0f; time = 0f
        val signs = intArrayOf(1, -1, 1, -1)
        for (i in 0 until N_LAYERS) {
            rot[i]  = i.toFloat() / N_LAYERS * PI.toFloat()
            rvel[i] = signs[i] * (0.005f + i * 0.0022f)
            poff[i] = 0f
            pvel[i] = 0f
        }
    }

    override fun draw(draw: GLDraw, audio: AudioData, tick: Int) {
        draw.fadeBlack(0.11f)

        val beat     = audio.beat
        val waveform = audio.waveform
        val W        = draw.W
        val H        = draw.H

        val bass = beat
        val mid  = audio.mid
        val high = audio.treble

        hue  = (hue + 0.007f) % 1f
        time += 0.018f + bass * 0.025f + mid * 0.015f

        bands[0] = bass
        bands[1] = mid
        bands[2] = high
        bands[3] = (bass + mid + high) / 3f

        val cx    = W / 2f
        val cy    = H / 2f
        val maxR  = minOf(W, H) * 0.44f

        // Update layer physics
        for (i in 0 until N_LAYERS) {
            val e = bands[i].coerceIn(0f, 1f)
            pvel[i] += bass * (0.32f + e * 0.14f)
            pvel[i] += -poff[i] * 0.24f
            pvel[i] *= 0.63f
            poff[i] += pvel[i]
            rot[i]  += rvel[i] * (1f + e * 2.8f + bass * 1.2f + mid * 1.0f)
        }

        // Downsample waveform to N_WAVE points
        val step  = maxOf(1, waveform.size / N_WAVE)
        for (i in 0 until N_WAVE) {
            wave[i] = if (i * step < waveform.size) waveform[i * step] else 0f
            waveRev[i] = wave[N_WAVE - 1 - i]
        }

        val sector = TAU / N_SYM

        // Draw layers from back (N_LAYERS-1) to front (0)
        for (i in N_LAYERS - 1 downTo 0) {
            val e      = bands[i].coerceIn(0f, 1f)
            val baseR  = maxR * (0.22f + i.toFloat() / (N_LAYERS - 1) * 0.72f)
            val rOff   = poff[i] * baseR * 0.42f
            val h      = (hue + i.toFloat() / N_LAYERS * 0.45f) % 1f
            val bright = (0.44f + e * 0.30f + mid * 0.15f + bass * 0.10f).coerceIn(0f, 1f)
            val amp    = baseR * (0.14f + e * 0.20f + bass * 0.10f + high * 0.15f)

            GLDraw.hsl(h, 1f, bright, 1f, colorScratch)

            for (sym in 0 until N_SYM) {
                val wSlice   = if (sym % 2 == 0) wave else waveRev
                val angleOff = sym.toFloat() * sector + rot[i]
                for (j in wSlice.indices) {
                    val theta = j.toFloat() / wSlice.size * sector + angleOff
                    val rPt   = maxOf(2f, baseR + rOff + wSlice[j] * amp)
                    pts[j * 2]     = cx + cos(theta) * rPt
                    pts[j * 2 + 1] = cy + sin(theta) * rPt
                }
                if (wSlice.size >= 2) {
                    draw.lineStrip(pts, wSlice.size, colorScratch[0], colorScratch[1], colorScratch[2], 1f)
                }
            }

            // Concentric ring outline for each layer
            val ringR = maxOf(1f, baseR + rOff)
            GLDraw.hsl(h, 1f, bright * 0.28f, 1f, colorScratch)
            draw.circle(cx, cy, ringR,
                colorScratch[0], colorScratch[1], colorScratch[2], colorScratch[3],
                filled = false, segments = 64)
        }

        // Two counter-rotating triangle rings
        for (triIdx in 0 until 2) {
            val tRvel = if (triIdx == 0) 0.012f else -0.008f
            val tRFrac = if (triIdx == 0) 0.58f else 0.82f
            val tHOff = if (triIdx == 0) 0.25f else 0.55f
            val tRot  = rot[0] * tRvel / 0.005f + mid * 0.20f
            // Treble adds radial jitter to outer triangle positions
            val tR    = maxR * tRFrac * (1f + poff[0] * 0.25f) + sin(time * 8f + triIdx) * (high * 20f)
            val tH    = (hue + tHOff) % 1f
            val tL    = (0.50f + bass * 0.20f + mid * 0.15f + high * 0.15f).coerceIn(0f, 1f)
            GLDraw.hsl(tH, 1f, tL, 1f, colorScratch)

            for (sym in 0 until N_SYM) {
                val aMid  = sym.toFloat() / N_SYM * TAU + tRot
                val aL    = aMid - PI.toFloat() / N_SYM * 0.55f
                val aR    = aMid + PI.toFloat() / N_SYM * 0.55f

                val tipX    = cx + cos(aMid) * tR * 1.18f
                val tipY    = cy + sin(aMid) * tR * 1.18f
                val baseLX  = cx + cos(aL)  * tR * 0.82f
                val baseLY  = cy + sin(aL)  * tR * 0.82f
                val baseRX  = cx + cos(aR)  * tR * 0.82f
                val baseRY  = cy + sin(aR)  * tR * 0.82f

                triPts[0] = tipX; triPts[1] = tipY
                triPts[2] = baseLX; triPts[3] = baseLY
                triPts[4] = baseRX; triPts[5] = baseRY
                draw.polygon(triPts, colorScratch[0], colorScratch[1], colorScratch[2], 1f, filled = false)
            }
        }

        // Central 3 rotating triangles (two counter-rotating layers)
        val cRot1 =  time * 1.8f
        val cRot2 = -time * 1.2f + PI.toFloat() / 3f
        val cR = maxR * (0.06f + bass * 0.04f + mid * 0.03f)
        for (centralLayer in 0 until 2) {
            val cRot = if (centralLayer == 0) cRot1 else cRot2
            val hOff = if (centralLayer == 0) 0f else 0.5f
            val tH = (hue + hOff) % 1f
            val tL = (0.55f + bass * 0.25f + high * 0.20f).coerceIn(0f, 1f)
            GLDraw.hsl(tH, 1f, tL, 1f, colorScratch)
            for (k in 0 until 3) {
                val aMid = k.toFloat() / 3f * TAU + cRot
                val aL   = aMid - PI.toFloat() / 3f * 0.55f
                val aR   = aMid + PI.toFloat() / 3f * 0.55f
                triPts[0] = cx + cos(aMid) * cR * 1.25f; triPts[1] = cy + sin(aMid) * cR * 1.25f
                triPts[2] = cx + cos(aL) * cR * 0.75f; triPts[3] = cy + sin(aL) * cR * 0.75f
                triPts[4] = cx + cos(aR) * cR * 0.75f; triPts[5] = cy + sin(aR) * cR * 0.75f
                draw.polygon(triPts, colorScratch[0], colorScratch[1], colorScratch[2], 1f, filled = false)
            }
        }
    }

}
