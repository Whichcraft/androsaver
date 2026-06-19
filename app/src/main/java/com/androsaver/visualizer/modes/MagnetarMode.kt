package com.androsaver.visualizer.modes

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw
import kotlin.math.*

/**
 * Magnetar — rotating magnetic dipole field with spiralling particles.
 *
 * ~4 000 particles ride the field lines of an analytical rotating magnetic
 * dipole.  Particles accumulate near the poles and trace glowing field lines.
 * Beat fires a shockwave that scatters particles outward from the equator.
 *
 *   Bass   → field rotation speed + particle velocity
 *   Mid    → dipole tilt angle
 *   Treble → particle colour saturation burst
 *   Beat   → equatorial shockwave
 *
 * Port of psysuals `effects/magnetar.py` (v3.9.0).
 * surfarray replaced: each particle drawn as a tiny circle with additive blend.
 * Particle count reduced from 6 000 to 4 000 for Android performance.
 * Trail: fadeBlack(24f/255f) approximates BLEND_RGB_MULT alpha overlay.
 */
class MagnetarMode : BaseMode() {

    override val name = "Magnetar"

    private companion object {
        const val N = 4_000
    }

    private val px    = FloatArray(N)
    private val py    = FloatArray(N)
    private var hue   = 0.62f
    private var rot   = 0f
    private var boost = 0f
    private var initialized = false

    override fun reset() {
        hue = 0.62f; rot = 0f; boost = 0f; initialized = false
    }

    override fun draw(draw: GLDraw, audio: AudioData, tick: Int) {
        val W = draw.W.toFloat(); val H = draw.H.toFloat()
        val bass = audio.beat
        val mid  = audio.mid
        val high = audio.treble

        if (!initialized) {
            for (i in 0 until N) {
                px[i] = Math.random().toFloat() * W
                py[i] = Math.random().toFloat() * H
            }
            initialized = true
        }

        hue  = (hue + 0.0008f + high * 0.001f) % 1f
        rot += 0.008f + bass * 0.025f + mid * 0.012f

        if (bass > 0.7f) boost = 2.5f + bass * 2.0f
        boost = maxOf(0f, boost - 0.08f)

        val cx   = W / 2f; val cy = H / 2f
        val half = minOf(W, H) * 0.5f

        // Dipole axis direction
        val tilt = PI.toFloat() * 0.12f + mid * 0.30f
        val mx   = cos(rot) * cos(tilt)
        val my   = sin(rot) * cos(tilt)

        val spd = 1.2f + bass * 1.8f + boost * 0.5f

        for (i in 0 until N) {
            val rx = (px[i] - cx) / half
            val ry = (py[i] - cy) / half
            val r2  = rx * rx + ry * ry + 0.04f
            val r   = sqrt(r2)
            val dot = rx * mx + ry * my
            val bx  = (3f * dot * rx / r2 - mx) / (r2 * r + 0.1f)
            val by_ = (3f * dot * ry / r2 - my) / (r2 * r + 0.1f)
            val bmag = sqrt(bx * bx + by_ * by_) + 1e-6f
            var vx = (bx  / bmag) * spd
            var vy = (by_ / bmag) * spd

            // Beat shockwave from equator
            if (boost > 0.1f) {
                val sign = if (py[i] > cy) 1f else -1f
                val distY = abs(py[i] - cy)
                val push  = boost * 1.5f * sign * exp(-distY / (H * 0.3f))
                vy += push * 0.5f
            }

            px[i] = ((px[i] + vx) % W + W) % W
            py[i] = ((py[i] + vy) % H + H) % H
        }

        draw.fadeBlack(24f / 255f)

        draw.setAdditiveBlend()
        for (i in 0 until N) {
            // Colour by angle to dipole axis
            val rx = (px[i] - cx) / half
            val ry = (py[i] - cy) / half
            val angToAxis = (atan2(ry, rx) - rot) / (2f * PI.toFloat())
            val h      = ((hue + angToAxis) % 1f + 1f) % 1f
            val bright = 0.35f + bass * 0.25f + high * 0.12f
            val c = GLDraw.hsl(h, l = bright)
            draw.circle(px[i], py[i], 1.5f, c[0], c[1], c[2], 0.7f, filled = true, segments = 4)
        }
        draw.setNormalBlend()
    }
}
