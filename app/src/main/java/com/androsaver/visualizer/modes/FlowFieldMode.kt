package com.androsaver.visualizer.modes

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw
import kotlin.math.*

/**
 * FlowField — thousands of particles surfing a continuously-evolving noise field.
 *
 * Three layers of sine/cosine noise generate a smooth organic vector field.
 * Particles ride the field and paint vivid rainbow trails on a slow-fade
 * persistent buffer.  Bass warps field intensity and particle speed; beat
 * fires a phase jump that instantly reshapes all flow lines.
 *
 * Port of psysuals `FlowField` class (v3.13.0).
 * pygame BLEND_RGB_MULT(247/255) ≈ fadeBlack(8/255) on a dark background.
 */
class FlowFieldMode : BaseMode() {

    override val name = "FlowField"
    private val rng = kotlin.random.Random(0xF10F)

    private companion object {
        const val N_MAX  = 40000
        const val FS     = 0.0022f   // spatial frequency of the noise field
        const val LAYERS = 2
    }

    private var px    = FloatArray(0)
    private var py    = FloatArray(0)
    private var n     = 0
    private var hue   = 0f
    private var t     = 0f
    private var boost = 0f
    private var lastW = 0f
    private var lastH = 0f

    override fun reset() {
        hue = 0f; t = 0f; boost = 0f
        px = FloatArray(0)
        py = FloatArray(0)
        n = 0
        lastW = 0f
        lastH = 0f
    }

    private fun allocateParticles(count: Int, W: Float, H: Float, preserve: Boolean) {
        val safeW = W.coerceAtLeast(1f)
        val safeH = H.coerceAtLeast(1f)
        val oldPx = px
        val oldPy = py
        n = count.coerceIn(8000, N_MAX)
        px = FloatArray(n) { rng.nextFloat() * safeW }
        py = FloatArray(n) { rng.nextFloat() * safeH }
        if (preserve) {
            val keep = minOf(oldPx.size, oldPy.size, n)
            for (i in 0 until keep) {
                px[i] = ((oldPx[i] % safeW) + safeW) % safeW
                py[i] = ((oldPy[i] % safeH) + safeH) % safeH
            }
        }
        lastW = safeW
        lastH = safeH
    }

    private fun initParticles(W: Float, H: Float, preserve: Boolean = false) {
        val target = (25000 * W * H / (1920 * 1080)).toInt().coerceIn(8000, N_MAX)
        allocateParticles(target, W, H, preserve)
    }

    /** Adjust density in the same fixed 2,000-particle steps as psysuals. */
    fun adjustParticles(delta: Int = 2000, W: Float = lastW, H: Float = lastH) {
        if (W <= 0f || H <= 0f) return
        val target = (n + delta).coerceIn(8000, N_MAX)
        if (target != n) allocateParticles(target, W, H, preserve = true)
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

        // Seed particles on first frame and rebuild on viewport changes.
        if (tick == 0 || px.isEmpty() || W != lastW || H != lastH ||
            (px.size > 1 && px[0] == 0f && py[0] == 0f && px[1] == 0f)) {
            initParticles(W, H, preserve = px.isNotEmpty())
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
            val scatterX = (rng.nextFloat() * 2f - 1f) * scatter
            val scatterY = (rng.nextFloat() * 2f - 1f) * scatter

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
            draw.particle(px[i], py[i], 1.5f, c[0], c[1], c[2], 0.85f)
        }
        draw.setNormalBlend()
    }
}
