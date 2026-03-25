package com.androsaver.visualizer.modes

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw
import kotlin.math.*

/**
 * CubeMode — Dual rotating wireframe cubes, each axis driven by a different band.
 * Port of Python class `Cube`.
 */
class CubeMode : BaseMode() {

    override val name = "Cube"

    // Unit cube vertices: 8 corners of [-1,1]^3
    private val vertsBase = arrayOf(
        floatArrayOf(-1f, -1f, -1f), floatArrayOf( 1f, -1f, -1f),
        floatArrayOf( 1f,  1f, -1f), floatArrayOf(-1f,  1f, -1f),
        floatArrayOf(-1f, -1f,  1f), floatArrayOf( 1f, -1f,  1f),
        floatArrayOf( 1f,  1f,  1f), floatArrayOf(-1f,  1f,  1f)
    )

    private val edges = arrayOf(
        0 to 1, 1 to 2, 2 to 3, 3 to 0,
        4 to 5, 5 to 6, 6 to 7, 7 to 4,
        0 to 4, 1 to 5, 2 to 6, 3 to 7
    )

    private var rx = 0f; private var ry = 0f; private var rz = 0f
    private var hue = 0f; private var fadeHue = 0f
    private var scale = 1f; private var svel = 0f
    private var rvx = 0f; private var rvy = 0f; private var rvz = 0f

    override fun reset() {
        rx = 0f; ry = 0f; rz = 0f
        hue = 0f; fadeHue = 0f
        scale = 1f; svel = 0f
        rvx = 0f; rvy = 0f; rvz = 0f
    }

    override fun draw(draw: GLDraw, audio: AudioData, tick: Int) {
        draw.fadeBlack(0.13f)

        val fft = audio.fft
        val beat = audio.beat

        fadeHue = (fadeHue + 0.0018f) % 1f

        val bass = fft.meanSlice(0, 5).coerceIn(0f, 1f)
        val mid  = fft.meanSlice(5, 25).coerceIn(0f, 1f)
        val high = fft.meanSlice(25, fft.size).coerceIn(0f, 1f)

        rvx += 0.00025f + mid  * 0.0015f + beat * 0.012f
        rvy += 0.00035f + bass * 0.0015f + beat * 0.016f
        rvz += 0.00018f + high * 0.0010f + beat * 0.006f
        rvx *= 0.86f; rvy *= 0.86f; rvz *= 0.86f
        rx += rvx; ry += rvy; rz += rvz

        svel += beat * 0.70f + bass * 0.30f
        svel += (1f - scale) * 0.20f
        svel *= 0.72f
        scale += svel
        // A rotated corner projects up to scale×√3 from centre; sz ≥ 2.8 at z=-1
        val maxScale = minOf(draw.W, draw.H) / 2f * 2.8f / (680f * 1.733f) * 0.90f
        scale = scale.coerceIn(0.5f, maxScale)

        // Draw two cubes: full scale and inner (45% scale, hue offset +0.5)
        val cubeParams = listOf(scale to 0f, scale * 0.45f to 0.5f)
        for ((baseScale, hueOff) in cubeParams) {
            val verts3d = Array(8) { vi ->
                rotateVertex(
                    vertsBase[vi][0] * baseScale,
                    vertsBase[vi][1] * baseScale,
                    vertsBase[vi][2] * baseScale,
                    rx, ry, rz
                )
            }
            val proj = Array(8) { vi -> project(verts3d[vi], draw.W, draw.H, fov = 680f) }

            for ((ei, edge) in edges.withIndex()) {
                val (a, b) = edge
                val h = (fadeHue + hueOff + ei.toFloat() / edges.size * 0.4f) % 1f
                val lightness = (0.40f + minOf(svel, 1f) * 0.25f).coerceIn(0f, 1f)
                val color = GLDraw.hsl(h, 1f, lightness)
                draw.line(
                    proj[a].first, proj[a].second,
                    proj[b].first, proj[b].second,
                    color[0], color[1], color[2], 1f
                )
            }
        }
    }

    /**
     * Apply Rx * Ry * Rz rotation to a vertex.
     * Returns FloatArray(x, y, z).
     */
    private fun rotateVertex(vx: Float, vy: Float, vz: Float, rx: Float, ry: Float, rz: Float): FloatArray {
        // Rotate around Z
        val cosZ = cos(rz); val sinZ = sin(rz)
        val x1 = vx * cosZ - vy * sinZ
        val y1 = vx * sinZ + vy * cosZ
        val z1 = vz

        // Rotate around Y
        val cosY = cos(ry); val sinY = sin(ry)
        val x2 = x1 * cosY + z1 * sinY
        val y2 = y1
        val z2 = -x1 * sinY + z1 * cosY

        // Rotate around X
        val cosX = cos(rx); val sinX = sin(rx)
        val x3 = x2
        val y3 = y2 * cosX - z2 * sinX
        val z3 = y2 * sinX + z2 * cosX

        return floatArrayOf(x3, y3, z3)
    }

    /**
     * Perspective projection.
     * Returns Pair(screenX, screenY).
     */
    private fun project(v: FloatArray, W: Int, H: Int, fov: Float): Pair<Float, Float> {
        val sz = maxOf(v[2] + 3.8f, 0.5f)
        val sx = (v[0] * fov / sz + W / 2f).coerceIn(0f, W.toFloat())
        val sy = (v[1] * fov / sz + H / 2f).coerceIn(0f, H.toFloat())
        return sx to sy
    }
}
