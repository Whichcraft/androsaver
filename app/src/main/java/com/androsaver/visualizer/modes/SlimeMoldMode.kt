package com.androsaver.visualizer.modes

import android.opengl.GLES20
import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.FieldResize
import com.androsaver.visualizer.GLDraw
import java.nio.ByteBuffer
import java.nio.Buffer
import java.util.Random
import kotlin.math.*

/** Desktop-reference chemical trails, uploaded once and coloured by GLES2.
 * The simulation keeps up to 1080p detail; linear texture filtering replaces
 * enlarged rectangle cells. No framebuffer readback occurs during playback.
 */
class SlimeMoldMode : FullscreenEffectMode() {
    override val name = "SlimeMold"
    private companion object {
        const val MAX_AGENTS = 26_000
        const val BASE_SD = 7f
        val BASE_SA = (PI / 6).toFloat()
        val TAU = (PI * 2).toFloat()
    }
    private val rng = Random(7)
    private val px = FloatArray(MAX_AGENTS)
    private val py = FloatArray(MAX_AGENTS)
    private val ang = FloatArray(MAX_AGENTS)
    private var agentCount = 0
    private var gridW = 0
    private var gridH = 0
    private var trail = FloatArray(0)
    private var diffuseTrail = FloatArray(0)
    private var left = IntArray(0)
    private var right = IntArray(0)
    private var upload = ByteBuffer.allocateDirect(1)
    private var texture = 0
    private var textureW = 0
    private var textureH = 0
    private var hue = .30f
    private var initialized = false
    private val hueUniform = FloatArray(1)
    private val uniforms = mapOf("u_hue" to hueUniform)

    override fun reset() {
        initialized = false
        hue = .30f
        trail.fill(0f)
        diffuseTrail.fill(0f)
    }
    override fun onSurfaceCreated() {
        super.onSurfaceCreated()
        texture = 0; textureW = 0; textureH = 0
        reset()
    }
    private fun init(width: Int, height: Int) {
        // At 4K each sample spans only two physical pixels, not six-pixel tiles.
        val scale = minOf(1f, 1920f / width, 1080f / height)
        val newW = maxOf(1, (width * scale).roundToInt())
        val newH = maxOf(1, (height * scale).roundToInt())
        if (initialized && newW == gridW && newH == gridH) return
        if (initialized) {
            val sx = newW.toFloat() / gridW; val sy = newH.toFloat() / gridH
            for (i in 0 until agentCount) {
                px[i] = (px[i] * sx).coerceIn(0f, newW - 1f)
                py[i] = (py[i] * sy).coerceIn(0f, newH - 1f)
            }
            trail = FieldResize.resize(trail, gridW, gridH, newW, newH)
        } else {
            agentCount = (13_000L * newW * newH / (640 * 360)).toInt().coerceIn(5_000, MAX_AGENTS)
            trail = FloatArray(newW * newH)
            // Seed across the whole frame so the colony uses the full screen
            // instead of hugging the centre.
            for (i in 0 until agentCount) {
                px[i] = rng.nextFloat() * newW
                py[i] = rng.nextFloat() * newH
                ang[i] = rng.nextFloat() * TAU
            }
        }
        gridW = newW; gridH = newH
        diffuseTrail = FloatArray(gridW * gridH)
        left = IntArray(gridW) { if (it == 0) gridW - 1 else it - 1 }
        right = IntArray(gridW) { if (it == gridW - 1) 0 else it + 1 }
        upload = ByteBuffer.allocateDirect(gridW * gridH)
        initialized = true
    }
    private fun sense(x: Float, y: Float): Float =
        trail[x.toInt().coerceIn(0, gridW - 1) + y.toInt().coerceIn(0, gridH - 1) * gridW]

    override fun draw(draw: GLDraw, audio: AudioData, tick: Int) {
        init(draw.W, draw.H)
        val bass = audio.beat; val mid = audio.mid; val high = audio.treble
        hue = (hue + .004f + mid * .008f + high * .004f) % 1f
        val sa = BASE_SA + high * .40f
        val cs = cos(sa); val ss = sin(sa)
        val physicalScale = gridW.toFloat() / draw.W
        val sd = (BASE_SD + bass * 5f) * physicalScale
        val speed = (.7f + bass * 1.5f) * physicalScale
        val turn = .18f + high * .15f
        val deposit = .8f + bass * .6f
        if (bass > .7f) {
            repeat(agentCount / 20) {
                val i = rng.nextInt(agentCount)
                px[i] = rng.nextFloat() * gridW
                py[i] = rng.nextFloat() * gridH
                ang[i] = rng.nextFloat() * TAU
            }
        }
        for (i in 0 until agentCount) {
            val c = cos(ang[i]); val s = sin(ang[i])
            val forward = sense(px[i] + c * sd, py[i] + s * sd)
            val a = sense(px[i] + (c * cs + s * ss) * sd, py[i] + (s * cs - c * ss) * sd)
            val b = sense(px[i] + (c * cs - s * ss) * sd, py[i] + (s * cs + c * ss) * sd)
            val delta = when {
                a > forward && a >= b -> -turn
                b > forward && b > a -> turn
                rng.nextFloat() < .05f -> (rng.nextFloat() * 2f - 1f) * turn
                else -> 0f
            }
            ang[i] += delta
            px[i] = ((px[i] + (if (delta == 0f) c else cos(ang[i])) * speed) % gridW + gridW) % gridW
            py[i] = ((py[i] + (if (delta == 0f) s else sin(ang[i])) * speed) % gridH + gridH) % gridH
        }
        // All sensors read the same pre-deposit field, as in desktop NumPy.
        for (i in 0 until agentCount) {
            val x = px[i].toInt().coerceIn(0, gridW - 1)
            val y = py[i].toInt().coerceIn(0, gridH - 1)
            trail[y * gridW + x] += deposit
        }
        val decay = .94f - bass * .01f
        for (y in 0 until gridH) {
            val row = y * gridW
            val up = (if (y == 0) gridH - 1 else y - 1) * gridW
            val down = (if (y == gridH - 1) 0 else y + 1) * gridW
            for (x in 0 until gridW) {
                diffuseTrail[row + x] = (trail[up + x] + trail[down + x] +
                    trail[row + left[x]] + trail[row + right[x]] + trail[row + x] * 4f) / 8f * decay
            }
        }
        val old = trail; trail = diffuseTrail; diffuseTrail = old
        (upload as Buffer).clear()
        for (value in trail) upload.put(((value / 5f).coerceIn(0f, 1f) * 255f).toInt().toByte())
        (upload as Buffer).flip()
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        if (texture == 0) {
            val ids = IntArray(1); GLES20.glGenTextures(1, ids, 0); texture = ids[0]
            check(texture != 0) { "Cannot allocate SlimeMold field texture" }
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        } else GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1)
        if (gridW != textureW || gridH != textureH) {
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE, gridW, gridH,
                0, GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, upload)
            textureW = gridW; textureH = gridH
        } else GLES20.glTexSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, gridW, gridH,
            GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, upload)
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 4)
        hueUniform[0] = hue
        draw.setNormalBlend()
        // GLES2 samplers default to texture unit zero, bound above.
        renderField(draw, uniforms)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
    }
    override val fragmentShader = """
        varying vec2 vUV;
        uniform sampler2D u_trail;
        uniform float u_hue;
        void main() {
            float intensity = texture2D(u_trail, vec2(vUV.x, 1.0-vUV.y)).r;
            // Stronger coupling to the trail intensity: fresh veins change hue
            // quickly against the older network.
            float hue = fract(u_hue + intensity * 0.85);
            vec3 colour = (sin(6.2831853*(hue + vec3(0.0,0.333,0.667)))*127.0+128.0)/255.0;
            gl_FragColor = vec4(clamp(colour * intensity * 2.0, 0.0, 1.0), 1.0);
        }
    """.trimIndent()
}
