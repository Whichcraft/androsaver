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
 * Port of psysuals `FlowField` class (v3.9.0).
 * pygame BLEND_RGB_MULT(247/255) ≈ fadeBlack(8/255) on a dark background.
 */
class FlowFieldMode : BaseMode() {

    override val name = "FlowField"

    private companion object {
        const val N_MAX  = 100000
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
        val beat = audio.beat
        val bass = beat
        val mid  = audio.mid
        val high = audio.treble

        // Seed particles on first frame — baseline 25000 for 1080p (v3.4.0)
        if (tick == 0 || px.isEmpty() || (px.size > 1 && px[0] == 0f && py[0] == 0f && px[1] == 0f)) {
            n = (25000 * W * H / (1920 * 1080)).toInt().coerceIn(8000, N_MAX)
            px = FloatArray(n)
            py = FloatArray(n)
            for (i in 0 until n) {
                px[i] = Math.random().toFloat() * W
                py[i] = Math.random().toFloat() * H
            }
        }

        hue  = (hue + 0.0013f + bass * 0.002f + high * 0.001f) % 1f
        t   += 0.007f + mid * 0.010f + high * 0.005f

        if (beat > 0.55f) {
            t     += 0.5f + beat * 0.4f
            boost  = 2.0f + beat * 2.0f
        }
        boost = maxOf(0f, boost - 0.12f)

        // Slow fade — pygame BLEND_RGB_MULT(247/255) ≈ fadeBlack(8/255)
        draw.fadeBlack(8f / 255f)

        val spd    = 1.8f + bass * 1.6f + mid * 1.2f + boost
        val scatter = high * 3.2f

        // Treble burst: push particles outward from center when high > 0.45
        val doBurst = high > 0.45f

        // Move particles and draw them as tiny dots (additive)
        draw.setAdditiveBlend()
        val cx = W * 0.5f; val cy = H * 0.5f
        val minDim = minOf(W, H)
        for (i in 0 until n) {
            val ang = fieldAngle(i, bass)

            // Bass gravity: pull toward screen centre
            val attractX = (cx - px[i]) * (bass * 0.0018f)
            val attractY = (cy - py[i]) * (bass * 0.0018f)

            // Treble scatter: random kick in any direction
            val scatterX = (Math.random().toFloat() * 2f - 1f) * scatter
            val scatterY = (Math.random().toFloat() * 2f - 1f) * scatter

            var nx = px[i] + cos(ang) * spd + attractX + scatterX
            var ny = py[i] + sin(ang) * spd + attractY + scatterY

            // Treble burst: radial push from center
            if (doBurst) {
                val dx = px[i] - cx; val dy = py[i] - cy
                val dist = sqrt(dx * dx + dy * dy) + 1e-5f
                val push = high * 22f * (1f - (dist / (minDim * 0.5f)).coerceAtMost(1f))
                nx += (dx / dist) * push
                ny += (dy / dist) * push
            }

            px[i] = ((nx % W) + W) % W
            py[i] = ((ny % H) + H) % H

            val h = (hue + px[i] / W * 0.30f + py[i] / H * 0.18f) % 1f
            val c = GLDraw.hsl(h, s = 0.90f, l = 0.62f)
            draw.circle(px[i], py[i], 1.5f, c[0], c[1], c[2], 0.85f, filled = true, segments = 4)
        }
        draw.setNormalBlend()
    }
}
