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
 * Particle port with the psysuals 3.20 dipole-contour layer restored.
 * surfarray replaced: each particle drawn as a tiny circle with additive blend.
 * Particle count reduced from 6 000 to 4 000 for Android performance.
 * Field and particles share one level: the scene fades toward black and both
 * the contours and the particles are added into that same buffer.
 */
class MagnetarMode : FullscreenEffectMode() {

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
    private var lastW = 0f
    private var lastH = 0f

    private val particleColour = FloatArray(4)
    private val contourAxis = FloatArray(2)
    private val contourRotation = FloatArray(1)
    private val contourHue = FloatArray(1)
    private val contourBass = FloatArray(1)
    private val contourHigh = FloatArray(1)
    private val contourUniforms = mapOf(
        "u_axis" to contourAxis,
        "u_rotation" to contourRotation,
        "u_hue" to contourHue,
        "u_bass" to contourBass,
        "u_high" to contourHigh,
        "u_intensity" to floatArrayOf(0.10f)
    )

    override fun onSurfaceCreated() {
        super.onSurfaceCreated()
        reset()
    }

    override fun reset() {
        hue = 0.62f; rot = 0f; boost = 0f; initialized = false
        lastW = 0f; lastH = 0f
    }

    override fun draw(draw: GLDraw, audio: AudioData, tick: Int) {
        val W = draw.W.toFloat(); val H = draw.H.toFloat()
        val step = prepare(draw, audio).step
        val bass = audio.beat
        val mid  = audio.mid
        val high = audio.treble

        if (!initialized || W != lastW || H != lastH) {
            lastW = W
            lastH = H
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
            val r3  = r2 * r + 0.1f
            val dot = rx * mx + ry * my
            val bx  = (3f * dot * rx / r2 - mx) / r3
            val by_ = (3f * dot * ry / r2 - my) / r3
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

        // One level: fade the retained scene toward black like upstream's
        // particle trail, then add the dipole contours and the particles into
        // that same buffer. The field is light, not a translucent plate drawn
        // over the scene, so nothing reads as a separate background image.
        draw.fadeBlackTimed(48f / 255f, step)
        contourAxis[0] = mx; contourAxis[1] = my
        contourRotation[0] = rot
        contourHue[0] = hue
        contourBass[0] = bass
        contourHigh[0] = high
        draw.setAdditiveBlend()
        renderField(draw, contourUniforms)

        for (i in 0 until N) {
            // Colour by angle to dipole axis
            val rx = (px[i] - cx) / half
            val ry = (py[i] - cy) / half
            val angToAxis = (atan2(ry, rx) - rot) / (2f * PI.toFloat())
            val h      = ((hue + angToAxis) % 1f + 1f) % 1f
            val bright = 0.35f + bass * 0.25f + high * 0.12f
            GLDraw.hsl(h, 1f, bright, 1f, particleColour)
            val c = particleColour
            draw.particle(px[i], py[i], 1.5f, c[0], c[1], c[2], 0.7f)
        }
        draw.setNormalBlend()
    }

    override val fragmentShader = """
        uniform vec2 u_resolution, u_axis;
        uniform float u_rotation, u_hue, u_bass, u_high, u_intensity;

        void main() {
            // Match upstream's independent [-1,1] contour grid on each axis.
            vec2 p = gl_FragCoord.xy / u_resolution * 2.0 - 1.0;
            p.y = -p.y;
            float r2 = dot(p, p) + 0.04;
            float r3 = r2 * sqrt(r2) + 0.1;
            vec2 field = (3.0 * dot(p, u_axis) * p / r2 - u_axis) / r3;
            // tanh(0.20 * |B|), expressed in GLES2 without positive exp overflow.
            float e = exp(-0.40 * length(field));
            float contour = (1.0 - e) / (1.0 + e);
            // Slow band drift: the background field only shimmers slightly.
            float bands = abs(sin(contour * 6.0 + u_rotation * 0.05));
            float h = fract(u_hue + contour * 0.18 + u_high * 0.03);
            float glow = clamp(bands * (0.20 + u_bass * 0.05), 0.0, 1.0);
            vec3 colour = (sin(6.2831853 * (h + vec3(0.0, 0.333, 0.667))) * 127.0 + 128.0) / 255.0;
            gl_FragColor = vec4(clamp(colour * glow, 0.0, 1.0), u_intensity);
        }
    """.trimIndent()
}
