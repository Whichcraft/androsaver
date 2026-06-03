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
        const val N_MAX  = 16000
        const val FS     = 0.0022f   // spatial frequency of the noise field
        const val LAYERS = 2
    }

    private var px    = FloatArray(0)
    private var py    = FloatArray(0)
    private var n     = 0
    private var hue   = 0f
    private var t     = 0f
    private var boost = 0f

    override fun reset() {
        hue = 0f; t = 0f; boost = 0f
        px = FloatArray(0)
        py = FloatArray(0)
        n = 0
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
        val bass   = fft.meanSlice(0, 6)
        val mids   = fft.meanSlice(10, 40)
        val treble = fft.meanSlice(100, 256)

        // Seed particles on first frame
        if (tick == 0 || px.isEmpty() || px[0] == 0f && py[0] == 0f && px[1] == 0f) {
            n = (4000 * W * H / (1920 * 1080)).toInt().coerceIn(1000, N_MAX)
            px = FloatArray(n)
            py = FloatArray(n)
            for (i in 0 until n) {
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
        boost = maxOf(0f, boost - 0.12f)

        // Slow fade — pygame BLEND_RGB_MULT(247/255) ≈ fadeBlack(8/255)
        draw.fadeBlack(8f / 255f)

        val spd = 1.8f + bass * 1.6f + boost

        val scatter = treble * 3.2f

        // Move particles and draw them as tiny dots (additive)
        draw.setAdditiveBlend()
        for (i in 0 until n) {
            val ang = fieldAngle(i, bass)

            // Bass gravity: pull toward screen centre
            val attractX = (W * 0.5f - px[i]) * (bass * 0.0018f)
            val attractY = (H * 0.5f - py[i]) * (bass * 0.0018f)

            // Treble scatter: random kick in any direction
            val scatterX = (Math.random().toFloat() * 2f - 1f) * scatter
            val scatterY = (Math.random().toFloat() * 2f - 1f) * scatter

            px[i] = ((px[i] + cos(ang) * spd + attractX + scatterX) % W + W) % W
            py[i] = ((py[i] + sin(ang) * spd + attractY + scatterY) % H + H) % H

            val h = (hue + px[i] / W * 0.30f + py[i] / H * 0.18f) % 1f
            val c = GLDraw.hsl(h, s = 0.90f, l = 0.62f)
            draw.circle(px[i], py[i], 1.5f, c[0], c[1], c[2], 0.85f, filled = true, segments = 4)
        }
        draw.setNormalBlend()
    }
}
