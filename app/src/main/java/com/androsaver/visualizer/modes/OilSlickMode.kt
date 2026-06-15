package com.androsaver.visualizer.modes

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw
import kotlin.math.*

/**
 * OilSlick — iridescent thin-film interference pattern.
 *
 * Two families of sine waves interfere across a coarse pixel grid,
 * simulating the rainbow shimmer of an oil film on water.  The interference
 * value maps continuously to hue, producing flowing prismatic colours.
 *
 *   Bass   → radial ripple amplitude
 *   Mid    → spatial frequency (zoom)
 *   Treble → temporal shimmer speed
 *   Beat   → phase jump + hue shift
 *
 * Port of psysuals `effects/oilslick.py` (v3.8.0).
 * RES_DIV=4 surfarray replaced with a 120×68 grid of coloured rects.
 * Each cell is approx. 16×16 pixels on a 1920×1080 display.
 */
class OilSlickMode : BaseMode() {

    override val name = "OilSlick"

    private companion object {
        const val COLS = 120
        const val ROWS = 68
        val TAU = (2.0 * PI).toFloat()
    }

    private var t     = 0f
    private var hue   = 0f
    private var phase = 0f

    override fun reset() { t = 0f; hue = 0f; phase = 0f }

    override fun draw(draw: GLDraw, audio: AudioData, tick: Int) {
        val W = draw.W.toFloat(); val H = draw.H.toFloat()
        val bass = audio.beat
        val mid  = audio.mid
        val high = audio.treble

        hue   = (hue   + 0.005f + mid * 0.005f) % 1f
        t    += 0.04f + high * 0.12f + bass * 0.06f
        phase = (phase + bass * 0.5f) % TAU

        val cellW = W / COLS
        val cellH = H / ROWS
        val freq  = 1f + mid * 1.5f
        val cx    = (COLS - 1) / 2f
        val cy    = (ROWS - 1) / 2f

        for (row in 0 until ROWS) {
            for (col in 0 until COLS) {
                // Map grid cell to wave-space coords [0, 4π]
                val xw = col.toFloat() / COLS * TAU * 2f * freq
                val yw = row.toFloat() / ROWS * TAU * 2f * freq

                val w1 = sin(xw + t) * cos(yw * 0.7f - t * 0.8f)
                val w2 = cos(xw * 1.3f - t * 0.6f + phase) * sin(yw * 1.1f + t * 0.5f)
                var interference = (w1 + w2) * 0.5f  // ≈ -1..1

                // Radial ripple from bass
                val dx = col - cx; val dy = row - cy
                val rDist = sqrt(dx * dx + dy * dy)
                interference += sin(rDist * 0.2f - t * 2f) * bass * 0.35f

                val h = ((interference * 0.5f + 0.5f + hue) % 1f + 1f) % 1f
                val l = (0.15f + abs(interference) * 0.40f + bass * 0.12f).coerceIn(0f, 1f)
                val c = GLDraw.hsl(h, l = l)
                draw.rect(col * cellW, row * cellH, cellW + 1f, cellH + 1f,
                          c[0], c[1], c[2], 1f)
            }
        }
    }
}
