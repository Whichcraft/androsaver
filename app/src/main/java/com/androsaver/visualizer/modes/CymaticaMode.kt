package com.androsaver.visualizer.modes

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw
import com.androsaver.visualizer.VisualizerRenderTuning
import kotlin.math.*

/**
 * Cymatica — analytic Chladni-like nodal plates with bounded sand.
 *
 * The plate field is evaluated per pixel in a GLES2 shader, so the nodal lines
 * stay smooth instead of showing the field grid the shared painter used.
 * Audio drives the six mode weights; sand grains descend the plate amplitude
 * and settle on the nodal lines.
 */
class CymaticaMode : FullscreenEffectMode() {
    override val name = "Cymatica"

    private companion object {
        const val MAX_PARTICLES = 700
        val PI_F = Math.PI.toFloat()
    }

    // Mode pairs (m, n) of the plate basis: (1,2) (2,3) (3,4) (4,5) (2,5) (3,6)
    private val modeM = intArrayOf(1, 2, 3, 4, 2, 3)
    private val modeN = intArrayOf(2, 3, 4, 5, 5, 6)
    private val weights = FloatArray(6)
    private val energies = FloatArray(6)
    private val weightUniform = FloatArray(6)
    private val particleX = FloatArray(MAX_PARTICLES) { kotlin.random.Random(0xC7A + it).nextFloat() * 2f - 1f }
    private val particleY = FloatArray(MAX_PARTICLES) { kotlin.random.Random(0xD11 + it).nextFloat() * 2f - 1f }
    private val hueUniform = FloatArray(1)
    private val highUniform = FloatArray(1)
    private var hue = 0f

    override fun reset() {
        weights.fill(0f); weights[0] = 1f; energies.fill(0f)
        hue = 0f
        hueUniform[0] = 0f; highUniform[0] = 0f
    }

    override fun draw(draw: GLDraw, audio: AudioData, tick: Int) {
        val response = prepare(draw, audio)
        val step = response.step
        val high = response.motion[2]
        hue = (hue + (0.0004f + high * 0.0002f) * step) % 1f

        // Six smoothed band weights select the plate modes.
        val count = minOf(128, audio.fft.size)
        for (i in energies.indices) {
            val from = i * count / 6
            val to = (i + 1) * count / 6
            var total = 0f
            for (j in from until to) total += abs(audio.fft[j])
            energies[i] = 0.05f + if (to > from) total / (to - from) else 0f
        }
        val sum = energies.sum()
        for (i in weights.indices) weights[i] = response.smooth(weights[i], energies[i] / sum, 0.003f)
        for (i in weights.indices) weightUniform[i] = weights[i]

        hueUniform[0] = hue
        highUniform[0] = high

        // The plate fades toward black; the nodal lines are added on top and
        // leave a short glow behind them.
        VisualizerRenderTuning.fadeTrail(draw)
        draw.setAdditiveBlend()
        renderField(draw, mapOf(
            "u_hue" to hueUniform,
            "u_high" to highUniform,
            "u_weights" to weightUniform
        ))

        // Sand grains descend the plate amplitude and settle on the nodes.
        val radius = VisualizerRenderTuning.viewportScale(draw.W, draw.H)
        for (i in particleX.indices step 2) {
            val px = particleX[i]
            val py = particleY[i]
            var field = 0f
            var gx = 0f
            var gy = 0f
            for (j in modeM.indices) {
                val m = modeM[j] * PI_F
                val n = modeN[j] * PI_F
                val cosX = cos(m * px); val cosY = cos(n * py)
                val sinX = sin(m * px); val sinY = sin(n * py)
                field += weights[j] * cosX * cosY
                gx -= weights[j] * m * sinX * cosY
                gy -= weights[j] * n * cosX * sinY
            }
            particleX[i] = (px - (field * gx * 0.0008f * step).coerceIn(-0.002f * step, 0.002f * step)).coerceIn(-0.995f, 0.995f)
            particleY[i] = (py - (field * gy * 0.0008f * step).coerceIn(-0.002f * step, 0.002f * step)).coerceIn(-0.995f, 0.995f)
            val x = (particleX[i] + 1f) * draw.W * 0.5f
            val y = (particleY[i] + 1f) * draw.H * 0.5f
            draw.circle(x, y, radius, 0.86f, 0.96f, 1f, 0.88f, true, 6)
        }
        draw.setNormalBlend()
    }

    override val fragmentShader = """
        uniform vec2 u_resolution;
        uniform float u_hue, u_high;
        uniform float u_weights[6];

        #define PI 3.14159265

        float mode(float m, float n, vec2 p) {
            return cos(PI*m*p.x)*cos(PI*n*p.y);
        }

        void main() {
            // The plate uses an independent [-1,1] grid per axis, as upstream.
            vec2 p = (gl_FragCoord.xy/u_resolution*2.0-1.0);
            p.y = -p.y;
            // The plate amplitude: the six weighted basis fields.
            float field = 0.0;
            field += u_weights[0]*mode(1.0,2.0,p);
            field += u_weights[1]*mode(2.0,3.0,p);
            field += u_weights[2]*mode(3.0,4.0,p);
            field += u_weights[3]*mode(4.0,5.0,p);
            field += u_weights[4]*mode(2.0,5.0,p);
            field += u_weights[5]*mode(3.0,6.0,p);
            // Nodal lines where the plate amplitude crosses zero.
            float line = clamp(exp(-abs(field)*(12.0-u_high*1.5)),0.0,1.0);
            float hue = fract(u_hue + line*0.72);
            vec3 colour = 0.5+0.5*cos(6.2831853*(hue+vec3(0.0,0.3333,0.6667)));
            gl_FragColor = vec4(colour*line, 1.0);
        }
    """.trimIndent()
}
