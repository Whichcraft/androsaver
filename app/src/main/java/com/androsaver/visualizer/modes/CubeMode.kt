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
    private var fadeHue = 0f
    private var scale = 1f; private var svel = 0f
    private var rvx = 0f; private var rvy = 0f; private var rvz = 0f
    private var orbAngle = 0f

    // Trail: ring buffer of projected vertex positions for main (ci=0) and inner (ci=1) cubes.
    // The screen is cleared every frame by the renderer, so we re-draw past positions explicitly
    // with decreasing alpha rather than relying on framebuffer persistence.
    private val TRAIL_LEN = 8
    private val trailProj  = Array(TRAIL_LEN) { Array(2) { Array(8) { 0f to 0f } } }
    private var trailHead  = 0
    private var trailCount = 0

    override fun reset() {
        rx = 0f; ry = 0f; rz = 0f
        fadeHue = 0f
        scale = 1f; svel = 0f
        rvx = 0f; rvy = 0f; rvz = 0f
        orbAngle = 0f
        trailHead = 0; trailCount = 0
    }

    override fun draw(draw: GLDraw, audio: AudioData, tick: Int) {
        val fft  = audio.fft
        val beat = audio.beat
        val fov  = 680f

        fadeHue = (fadeHue + 0.0018f) % 1f

        val bass = fft.meanSlice(0, 5).coerceIn(0f, 1f)
        val mid  = fft.meanSlice(5, 25).coerceIn(0f, 1f)
        val high = fft.meanSlice(25, fft.size).coerceIn(0f, 1f)

        rvx += 0.00025f + mid  * 0.0015f + beat * 0.012f
        rvy += 0.00035f + bass * 0.0015f + beat * 0.016f
        rvz += 0.00018f + high * 0.0010f + beat * 0.006f
        rvx *= 0.86f; rvy *= 0.86f; rvz *= 0.86f
        rx += rvx; ry += rvy; rz += rvz

        svel += beat * 1.30f + bass * 0.40f
        svel += (1f - scale) * 0.20f
        svel *= 0.72f
        scale += svel
        val maxScale = minOf(draw.W, draw.H) / 2f * 2.8f / (fov * 1.733f) * 0.90f
        scale = scale.coerceIn(0.5f, maxScale)

        // ── Compute projections for main (ci=0) and inner (ci=1) cubes ────────
        val cubeScales = floatArrayOf(scale, scale * 0.45f)
        val currentProj = Array(2) { ci ->
            val s = cubeScales[ci]
            val verts3d = Array(8) { vi ->
                rotateVertex(vertsBase[vi][0] * s, vertsBase[vi][1] * s, vertsBase[vi][2] * s, rx, ry, rz)
            }
            Array(8) { vi -> project(verts3d[vi], draw.W, draw.H, fov) }
        }

        // ── Update trail ring buffer ───────────────────────────────────────────
        for (ci in 0..1) for (vi in 0..7) trailProj[trailHead][ci][vi] = currentProj[ci][vi]
        if (trailCount < TRAIL_LEN) trailCount++
        trailHead = (trailHead + 1) % TRAIL_LEN

        // ── Draw echo trail (oldest → newest-1, progressively brighter) ───────
        for (age in trailCount - 1 downTo 1) {
            val frameIdx = (trailHead - 1 - age + TRAIL_LEN * 2) % TRAIL_LEN
            val alpha    = (1f - age.toFloat() / trailCount.toFloat()) * 0.55f
            for (ci in 0..1) {
                val hueOff = if (ci == 1) 0.5f else 0f
                val proj   = trailProj[frameIdx][ci]
                for ((ei, edge) in edges.withIndex()) {
                    val (a, b) = edge
                    val h     = (fadeHue + hueOff + ei.toFloat() / edges.size * 0.4f) % 1f
                    val color = GLDraw.hsl(h, 1f, 0.32f)
                    draw.line(proj[a].first, proj[a].second, proj[b].first, proj[b].second,
                        color[0], color[1], color[2], alpha)
                }
            }
        }

        // ── Draw current frame (full brightness) ──────────────────────────────
        val lightness = (0.40f + minOf(svel, 1f) * 0.25f).coerceIn(0f, 1f)
        for (ci in 0..1) {
            val hueOff = if (ci == 1) 0.5f else 0f
            val proj   = currentProj[ci]
            for ((ei, edge) in edges.withIndex()) {
                val (a, b) = edge
                val h     = (fadeHue + hueOff + ei.toFloat() / edges.size * 0.4f) % 1f
                val color = GLDraw.hsl(h, 1f, lightness)
                draw.line(proj[a].first, proj[a].second, proj[b].first, proj[b].second,
                    color[0], color[1], color[2], 1f)
            }
        }

        // ── Orbiting satellite cubes ──────────────────────────────────────────
        // N_MAX is the maximum possible nSats value; angular slots are fixed so
        // existing satellites never jump when the active count changes.
        val N_MAX    = 6
        val nSats    = 2 + (beat.coerceAtMost(2f) * 2f).toInt()
        val satScale = scale * 0.28f
        val orbR     = 2.6f
        orbAngle    += 0.012f + beat * 0.04f

        // Clamp orbit radius so the satellite cube stays fully on screen.
        // A rotated satellite corner extends ~1.5× satScale from the orbit centre.
        val sz0        = 3.8f  // approximate z-depth of cube centre (matches project() offset)
        val satHalfPx  = satScale * 1.5f * fov / sz0
        val maxOrbitPx = minOf(draw.W, draw.H) / 2f - satHalfPx - 8f
        val effectiveOrbR = if (maxOrbitPx > 0f) minOf(orbR, maxOrbitPx * sz0 / fov) else 0f

        for (si in 0 until nSats) {
            val theta = orbAngle + si.toFloat() / N_MAX * (2f * PI.toFloat())
            val ox    = effectiveOrbR * cos(theta)
            val oy    = effectiveOrbR * sin(theta)
            val verts3d = Array(8) { vi ->
                rotateVertex(
                    vertsBase[vi][0] * satScale,
                    vertsBase[vi][1] * satScale,
                    vertsBase[vi][2] * satScale,
                    rx, ry, rz
                )
            }
            val proj   = Array(8) { vi -> projectOffset(verts3d[vi], ox, oy, draw.W, draw.H, fov) }
            val hOff   = si.toFloat() / nSats * 0.6f
            val satL   = (0.38f + minOf(svel, 1f) * 0.20f).coerceIn(0f, 1f)
            for ((ei, edge) in edges.withIndex()) {
                val (a, b) = edge
                val h     = (fadeHue + hOff + ei.toFloat() / edges.size * 0.4f) % 1f
                val color = GLDraw.hsl(h, 1f, satL)
                draw.line(proj[a].first, proj[a].second, proj[b].first, proj[b].second,
                    color[0], color[1], color[2], 1f)
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

    /** Perspective projection with XY world-space offset (for satellite cubes). */
    private fun projectOffset(v: FloatArray, ox: Float, oy: Float, W: Int, H: Int, fov: Float): Pair<Float, Float> {
        val sz = maxOf(v[2] + 3.8f, 0.5f)
        val sx = ((v[0] + ox) * fov / sz + W / 2f).coerceIn(0f, W.toFloat())
        val sy = ((v[1] + oy) * fov / sz + H / 2f).coerceIn(0f, H.toFloat())
        return sx to sy
    }
}
