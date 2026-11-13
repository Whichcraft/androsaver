package com.androsaver.visualizer.modes

import android.opengl.GLES20
import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw
import com.androsaver.visualizer.VisualizerRenderTuning
import java.nio.Buffer
import java.nio.ByteBuffer
import kotlin.math.*

/**
 * LiquidLight — fluorescent dye carried by a bounded liquid.
 *
 * A coarse grid holds a velocity field plus a dye and a heat field. Every
 * frame the fields are advected with a semi-Lagrangian backtrace, beats inject
 * vortices, bass injects dye and treble adds heat. The grid is uploaded as a
 * texture and shaded, so the dye reads as moving liquid instead of a tiled
 * pattern. No framebuffer readback occurs during playback.
 */
class LiquidLightMode : FullscreenEffectMode() {
    override val name = "LiquidLight"

    private companion object {
        const val GRID_W = 96
        const val MIN_GRID_H = 40
        const val MAX_GRID_H = 96
        const val MAX_VORTICES = 3
        const val VORTEX_RADIUS = 0.18f
    }

    private var gridW = 0
    private var gridH = 0
    private var vx = FloatArray(0)
    private var vy = FloatArray(0)
    private var vxNext = FloatArray(0)
    private var vyNext = FloatArray(0)
    private var dye = FloatArray(0)
    private var dyeNext = FloatArray(0)
    private var heat = FloatArray(0)
    private var heatNext = FloatArray(0)
    private var upload = ByteBuffer.allocateDirect(1)
    private var texture = 0
    private var textureW = 0
    private var textureH = 0
    private var hue = 0f
    private var beatPrev = 0f
    private var spin = 0f
    private val rng = kotlin.random.Random(0x11D)
    private val hueUniform = FloatArray(1)
    private val uniforms = mapOf("u_hue" to hueUniform)

    override fun onSurfaceCreated() {
        super.onSurfaceCreated()
        texture = 0; textureW = 0; textureH = 0
        reset()
    }

    override fun reset() {
        hue = 0f
        beatPrev = 0f
        spin = 0f
        vx.fill(0f); vy.fill(0f); dye.fill(0f); heat.fill(0f)
        seedDye()
    }

    /** Start with a soft bloom of dye so the effect is never blank. */
    private fun seedDye() {
        if (gridW <= 0 || gridH <= 0) return
        for (y in 0 until gridH) {
            val row = y * gridW
            val ny = (y + 0.5f) / gridH - 0.5f
            for (x in 0 until gridW) {
                val nx = (x + 0.5f) / gridW - 0.5f
                val radius = sqrt(nx * nx + ny * ny)
                if (radius < 0.5f) {
                    val amount = (0.5f - radius) * 1.2f
                    dye[row + x] = minOf(1f, amount)
                    heat[row + x] = minOf(1f, amount * 0.5f)
                }
            }
        }
    }

    /** Match the grid to the viewport aspect so the dye is not stretched. */
    private fun ensureGrid(width: Int, height: Int) {
        val h = (GRID_W * height.toFloat() / width.coerceAtLeast(1)).roundToInt()
            .coerceIn(MIN_GRID_H, MAX_GRID_H)
        if (gridW == GRID_W && gridH == h) return
        gridW = GRID_W
        gridH = h
        val size = gridW * gridH
        vx = FloatArray(size); vy = FloatArray(size)
        vxNext = FloatArray(size); vyNext = FloatArray(size)
        dye = FloatArray(size); dyeNext = FloatArray(size)
        heat = FloatArray(size); heatNext = FloatArray(size)
        upload = ByteBuffer.allocateDirect(size * 3)
        seedDye()
    }

    /** Bilinear sample of a grid field at fractional cell coordinates. */
    private fun sample(field: FloatArray, x: Float, y: Float): Float {
        val fx = x.coerceIn(0f, gridW - 1.001f)
        val fy = y.coerceIn(0f, gridH - 1.001f)
        val x0 = fx.toInt(); val y0 = fy.toInt()
        val tx = fx - x0; val ty = fy - y0
        val row = y0 * gridW
        val rowNext = (y0 + 1).coerceAtMost(gridH - 1) * gridW
        val x1 = (x0 + 1).coerceAtMost(gridW - 1)
        val top = field[row + x0] * (1f - tx) + field[row + x1] * tx
        val bottom = field[rowNext + x0] * (1f - tx) + field[rowNext + x1] * tx
        return top * (1f - ty) + bottom * ty
    }

    private fun injectVortex(cx: Float, cy: Float, sign: Float, strength: Float) {
        val radius = maxOf(2f, minOf(gridW, gridH) * VORTEX_RADIUS)
        val r2 = radius * radius
        for (y in 0 until gridH) {
            val row = y * gridW
            val fy = y + 0.5f
            for (x in 0 until gridW) {
                val dx = x + 0.5f - cx
                val dy = fy - cy
                val d2 = dx * dx + dy * dy
                if (d2 > r2 * 4f) continue
                val weight = exp(-d2 / r2)
                val idx = row + x
                vx[idx] += (-dy * weight * sign * strength / radius).coerceIn(-3f, 3f)
                vy[idx] += (dx * weight * sign * strength / radius).coerceIn(-3f, 3f)
                dye[idx] = minOf(1f, dye[idx] + weight * 0.35f)
                heat[idx] = minOf(1f, heat[idx] + weight * 0.20f)
            }
        }
    }

    override fun draw(draw: GLDraw, audio: AudioData, tick: Int) {
        ensureGrid(draw.W, draw.H)
        val response = prepare(draw, audio)
        val step = response.step
        val bass = response.motion[0]
        val mid = response.motion[1]
        val high = response.motion[2]

        hue = (hue + 0.0016f + mid * 0.0026f + high * 0.0012f) % 1f
        spin += (0.05f + mid * 0.10f) * step

        // Advect the velocity field. Cell coordinates are used directly.
        val scale = step * 0.55f
        for (y in 0 until gridH) {
            val row = y * gridW
            for (x in 0 until gridW) {
                val idx = row + x
                val backX = x - vx[idx] * scale
                val backY = y - vy[idx] * scale
                vxNext[idx] = sample(vx, backX, backY) * 0.985f
                vyNext[idx] = sample(vy, backX, backY) * 0.985f
            }
        }
        System.arraycopy(vxNext, 0, vx, 0, vx.size)
        System.arraycopy(vyNext, 0, vy, 0, vy.size)

        // Advect the dye and heat with the current flow.
        for (y in 0 until gridH) {
            val row = y * gridW
            for (x in 0 until gridW) {
                val idx = row + x
                val backX = x - vx[idx] * scale
                val backY = y - vy[idx] * scale
                dyeNext[idx] = sample(dye, backX, backY) * 0.9945f
                heatNext[idx] = sample(heat, backX, backY) * 0.988f
            }
        }
        System.arraycopy(dyeNext, 0, dye, 0, dye.size)
        System.arraycopy(heatNext, 0, heat, 0, heat.size)

        // A slow background swirl keeps the liquid alive between beats.
        val swirlStrength = 0.05f + mid * 0.10f
        for (y in 0 until gridH) {
            val row = y * gridW
            val ny = (y + 0.5f) / gridH - 0.5f
            for (x in 0 until gridW) {
                val idx = row + x
                val nx = (x + 0.5f) / gridW - 0.5f
                val angle = atan2(ny, nx) + spin
                vx[idx] += (-sin(angle) * swirlStrength * (0.4f + bass * 0.9f)) * step
                vy[idx] += (cos(angle) * swirlStrength * (0.4f + bass * 0.9f)) * step
            }
        }

        // Beats inject vortices that carry the dye.
        if (audio.beat > 0.6f && beatPrev <= 0.6f) {
            val count = 1 + (rng.nextFloat() * 2f).toInt().coerceAtMost(MAX_VORTICES - 1)
            repeat(count) {
                val cx = rng.nextFloat() * gridW
                val cy = rng.nextFloat() * gridH
                val sign = if (rng.nextBoolean()) 1f else -1f
                injectVortex(cx, cy, sign, 0.9f + audio.beat * 1.4f)
            }
        }
        beatPrev = audio.beat

        // Bass feeds dye into the flow, treble adds heat where the liquid moves.
        for (y in 0 until gridH) {
            val row = y * gridW
            val ny = (y + 0.5f) / gridH - 0.5f
            for (x in 0 until gridW) {
                val idx = row + x
                val nx = (x + 0.5f) / gridW - 0.5f
                val radius = sqrt(nx * nx + ny * ny)
                if (radius < 0.45f) {
                    val feed = (0.45f - radius) * bass * 0.16f * step
                    dye[idx] = minOf(1f, dye[idx] + feed)
                }
                val speed = sqrt(vx[idx] * vx[idx] + vy[idx] * vy[idx])
                heat[idx] = minOf(1f, heat[idx] + speed * high * 0.05f * step)
            }
        }

        // Upload dye, heat and flow speed for the shader.
        (upload as Buffer).clear()
        for (i in dye.indices) {
            val speed = sqrt(vx[i] * vx[i] + vy[i] * vy[i])
            upload.put((dye[i].coerceIn(0f, 1f) * 255f).toInt().toByte())
            upload.put((heat[i].coerceIn(0f, 1f) * 255f).toInt().toByte())
            upload.put((minOf(1f, speed * 0.35f) * 255f).toInt().toByte())
        }
        (upload as Buffer).flip()
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        if (texture == 0) {
            val ids = IntArray(1); GLES20.glGenTextures(1, ids, 0); texture = ids[0]
            check(texture != 0) { "Cannot allocate LiquidLight field texture" }
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        } else GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1)
        if (gridW != textureW || gridH != textureH) {
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGB, gridW, gridH,
                0, GLES20.GL_RGB, GLES20.GL_UNSIGNED_BYTE, upload)
            textureW = gridW; textureH = gridH
        } else GLES20.glTexSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, gridW, gridH,
            GLES20.GL_RGB, GLES20.GL_UNSIGNED_BYTE, upload)
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 4)

        hueUniform[0] = hue
        VisualizerRenderTuning.fadeTrail(draw)
        draw.setAdditiveBlend()
        // GLES2 samplers default to texture unit zero, bound above.
        renderField(draw, uniforms)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        draw.setNormalBlend()
    }

    override val fragmentShader = """
        varying vec2 vUV;
        uniform sampler2D u_field;
        uniform float u_hue;
        void main() {
            vec3 f = texture2D(u_field, vec2(vUV.x, 1.0-vUV.y)).rgb;
            float dye = f.r;
            float heat = f.g;
            float flow = f.b;
            // Hot dye drifts towards the violet end, cool dye stays in the
            // base hue; the flow adds a thin bright rim to fast liquid.
            float hue = fract(u_hue + heat*0.45 + dye*0.12);
            vec3 colour = 0.5+0.5*cos(6.2831853*(hue+vec3(0.0,0.3333,0.6667)));
            float light = clamp(dye*1.5 + heat*0.35 + flow*0.30, 0.0, 1.0);
            gl_FragColor = vec4(clamp(colour*light, 0.0, 1.0), 1.0);
        }
    """.trimIndent()
}
