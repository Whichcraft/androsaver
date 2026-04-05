package com.androsaver.visualizer.modes

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw
import kotlin.math.*

/**
 * FlowField — 4 000 particles surfing a continuously-evolving noise field.
 *
 * Three layers of sine/cosine noise generate a smooth organic vector field.
 * Particles ride the field and paint vivid rainbow trails on a slow-fade
 * persistent buffer.  Bass warps field intensity and particle speed; beat
 * fires a phase jump that instantly reshapes all flow lines.
 *
 * Port of psysuals `FlowField` class (v2.3.0).
 * pygame BLEND_RGB_MULT(247/255) ≈ fadeBlack(8/255) on a dark background.
 */
class FlowFieldMode : BaseMode() {

    override val name = "FlowField"

    private companion object {
        const val N      = 4000
        const val FS     = 0.0022f   // spatial frequency of the noise field
        const val LAYERS = 3
    }

    private val px    = FloatArray(N)
    private val py    = FloatArray(N)
    private var hue   = 0f
    private var t     = 0f
    private var boost = 0f

    override fun reset() {
        hue = 0f; t = 0f; boost = 0f
        // randomise particle positions — filled in first draw when W/H known
    }

    private fun fieldAngle(i: Int, bass: Float): Float {
        var a = 0f
        val x = px[i]; val y = py[i]
        for (layer in 0 until LAYERS) {
            val f = 1.6f.pow(layer)
            a += sin(x * FS * f + t * (0.29f + layer * 0.08f)) *
                 cos(y * FS * f * 0.75f + t * (0.21f - layer * 0.06f))
        }
        return a * PI.toFloat() * (2.2f + bass * 1.8f)
    }

    override fun draw(draw: GLDraw, audio: AudioData, tick: Int) {
        val W = draw.W.toFloat(); val H = draw.H.toFloat()
        val fft  = audio.fft
        val beat = audio.beat
        val bass = fft.meanSlice(0, 6)
        val mids = fft.meanSlice(10, 40)

        // Seed particles on first frame
        if (tick == 0 || (px[0] == 0f && py[0] == 0f && px[1] == 0f)) {
            for (i in 0 until N) {
                px[i] = Math.random().toFloat() * W
                py[i] = Math.random().toFloat() * H
            }
        }

        hue  = (hue + 0.0013f + bass * 0.002f) % 1f
        t   += 0.007f + mids * 0.010f

        if (beat > 0.55f) {
            t     += 0.5f + beat * 0.4f   // phase jump reshapes all lines
            boost  = 2.0f + beat * 2.0f
        }
        boost = maxOf(0f, boost - 0.08f)

        // Slow fade — pygame BLEND_RGB_MULT(247/255) ≈ fadeBlack(8/255)
        draw.fadeBlack(8f / 255f)

        val spd = 1.6f + bass * 1.4f + boost

        // Move particles and draw them as tiny dots (additive)
        draw.setAdditiveBlend()
        for (i in 0 until N) {
            val ang = fieldAngle(i, bass)
            px[i] = ((px[i] + cos(ang) * spd) % W + W) % W
            py[i] = ((py[i] + sin(ang) * spd) % H + H) % H

            val h = (hue + px[i] / W * 0.30f + py[i] / H * 0.18f) % 1f
            val c = GLDraw.hsl(h, s = 0.90f, l = 0.62f)
            draw.circle(px[i], py[i], 1.5f, c[0], c[1], c[2], 0.85f, filled = true, segments = 4)
        }
        draw.setNormalBlend()
    }
}
