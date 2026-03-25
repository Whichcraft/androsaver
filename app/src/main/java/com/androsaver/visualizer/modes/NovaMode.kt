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
        val fft      = audio.fft
        val waveform = audio.waveform
        val W        = draw.W
        val H        = draw.H

        hue  = (hue + 0.007f) % 1f
        time += 0.018f + beat * 0.025f

        val bass = fft.meanSlice(0, 6)
        val mid  = fft.meanSlice(6, 30)
        val high = fft.meanSlice(30, fft.size)
        val bands = floatArrayOf(bass, mid, high, (bass + mid + high) / 3f)

        val cx    = W / 2f
        val cy    = H / 2f
        val maxR  = minOf(W, H) * 0.44f

        // Update layer physics
        for (i in 0 until N_LAYERS) {
            val e = bands[i].coerceIn(0f, 1f)
            pvel[i] += beat * (0.32f + e * 0.14f)
            pvel[i] += -poff[i] * 0.24f
            pvel[i] *= 0.63f
            poff[i] += pvel[i]
            rot[i]  += rvel[i] * (1f + e * 2.8f + bass * 1.2f)
        }

        // Downsample waveform to N_WAVE points
        val step  = maxOf(1, waveform.size / N_WAVE)
        val wave  = FloatArray(N_WAVE) { i ->
            if (i * step < waveform.size) waveform[i * step] else 0f
        }
        val waveRev = wave.reversedArray()

        val sector = TAU / N_SYM

        // Draw layers from back (N_LAYERS-1) to front (0)
        for (i in N_LAYERS - 1 downTo 0) {
            val e      = bands[i].coerceIn(0f, 1f)
            val baseR  = maxR * (0.22f + i.toFloat() / (N_LAYERS - 1) * 0.72f)
            val rOff   = poff[i] * baseR * 0.42f
            val h      = (hue + i.toFloat() / N_LAYERS * 0.45f) % 1f
            val bright = (0.44f + e * 0.44f + beat * 0.10f).coerceIn(0f, 1f)
            val amp    = baseR * (0.14f + e * 0.20f + beat * 0.10f)

            val color = GLDraw.hsl(h, 1f, bright)

            for (sym in 0 until N_SYM) {
                val wSlice   = if (sym % 2 == 0) wave else waveRev
                val angleOff = sym.toFloat() * sector + rot[i]
                val pts      = FloatArray(wSlice.size * 2)
                for (j in wSlice.indices) {
                    val theta = j.toFloat() / wSlice.size * sector + angleOff
                    val rPt   = maxOf(2f, baseR + rOff + wSlice[j] * amp)
                    pts[j * 2]     = cx + cos(theta) * rPt
                    pts[j * 2 + 1] = cy + sin(theta) * rPt
                }
                if (pts.size >= 4) {
                    draw.lineStrip(pts, color[0], color[1], color[2], 1f)
                }
            }

            // Concentric ring outline for each layer
            val ringR = maxOf(1f, baseR + rOff)
            val ringColor = GLDraw.hsl(h, 1f, bright * 0.28f)
            draw.circle(cx, cy, ringR,
                ringColor[0], ringColor[1], ringColor[2], ringColor[3],
                filled = false, segments = 64)
        }

        // Two counter-rotating triangle rings
        data class TriLayer(val tRvel: Float, val tRFrac: Float, val tHOff: Float)
        val triLayers = listOf(
            TriLayer( 0.012f, 0.58f, 0.25f),
            TriLayer(-0.008f, 0.82f, 0.55f)
        )
        for (triLayer in triLayers) {
            val tRot  = rot[0] * triLayer.tRvel / 0.005f
            val tR    = maxR * triLayer.tRFrac * (1f + poff[0] * 0.25f)
            val tH    = (hue + triLayer.tHOff) % 1f
            val tL    = (0.50f + bass * 0.30f + beat * 0.18f).coerceIn(0f, 1f)
            val tColor = GLDraw.hsl(tH, 1f, tL)

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

                val triPts = floatArrayOf(
                    tipX,   tipY,
                    baseLX, baseLY,
                    baseRX, baseRY
                )
                draw.polygon(triPts, tColor[0], tColor[1], tColor[2], 1f, filled = false)
            }
        }

        // Central pulse
        val cr = maxOf(2f, 10f + bass * 22f + beat * 40f)
        val pulseColor = GLDraw.hsl(hue, 1f, (0.55f + beat * 0.35f).coerceIn(0f, 1f))
        draw.circle(cx, cy, cr, pulseColor[0], pulseColor[1], pulseColor[2], 1f, filled = true)

        // White inner flash
        draw.circle(cx, cy, maxOf(1f, cr / 4f), 1f, 1f, 1f, 1f, filled = true)
    }

    private fun FloatArray.reversedArray(): FloatArray {
        val out = FloatArray(size)
        for (i in indices) out[i] = this[size - 1 - i]
        return out
    }
}
