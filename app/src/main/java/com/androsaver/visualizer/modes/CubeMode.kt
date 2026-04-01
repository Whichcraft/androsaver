package com.androsaver.visualizer.modes

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw
import kotlin.math.*

/**
 * CubeMode — Dual rotating wireframe cubes, each axis driven by a different band.
 * Port of psysuals `Cube` class (v2.0.0).
 *
 * v2.0.0 changes vs v1.4.x:
 *  - Always 2 satellites fixed 180° apart (no variable beat-driven count)
 *  - Satellites rotate independently (sat_rx, sat_ry) — never tied to main cube
 *  - satScale capped at 0.55 to prevent oversized cubes
 *  - Updated rotation damping and svel physics
 *  - spinDir flip on bass removed
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

    // Independent satellite rotation (v2.0.0)
    private var satRx = 0f; private var satRy = 0f

    // Main cube trail ring buffer
    private val TRAIL_LEN = 14
    private val trailProj  = Array(TRAIL_LEN) { Array(2) { Array(8) { 0f to 0f } } }
    private var trailHead  = 0
    private var trailCount = 0

    // Satellite trail: halved from 30→15 frames (v2.0.2: double _SAT_FADE → shorter persistence)
    // Always 2 sats (v2.0.0); ring buffer still used for per-frame replay
    private val SAT_TRAIL_LEN = 15
    private val satTrailProj  = Array(SAT_TRAIL_LEN) { Array(2) { Array(8) { 0f to 0f } } }
    private var satTrailHead  = 0
    private var satTrailCount = 0

    override fun reset() {
        rx = 0f; ry = 0f; rz = 0f
        fadeHue = 0f
        scale = 1f; svel = 0f
        rvx = 0f; rvy = 0f; rvz = 0f
        orbAngle = 0f
        satRx = 0f; satRy = 0f
        trailHead = 0; trailCount = 0
        satTrailHead = 0; satTrailCount = 0
    }

    override fun draw(draw: GLDraw, audio: AudioData, tick: Int) {
        val fft  = audio.fft
        val beat = audio.beat
        val fov  = 680f

        fadeHue = (fadeHue + 0.0018f) % 1f

        val bass = fft.meanSlice(0, 5).coerceIn(0f, 1f)
        val mid  = fft.meanSlice(5, 25).coerceIn(0f, 1f)
        val high = fft.meanSlice(25, fft.size).coerceIn(0f, 1f)

        // Base idle terms from v1.4.x (calm on TV at default intensity);
        // audio-reactive multipliers and damping from v2.0.0.
        rvx += 0.00025f + mid  * 0.012f + beat * 0.10f
        rvy += 0.00035f + bass * 0.015f + beat * 0.12f
        rvz += 0.00018f + high * 0.008f + beat * 0.05f
        rvx *= 0.94f; rvx = rvx.coerceIn(-0.08f, 0.08f)
        rvy *= 0.94f; rvy = rvy.coerceIn(-0.08f, 0.08f)
        rvz *= 0.94f; rvz = rvz.coerceIn(-0.05f, 0.05f)
        rx += rvx; ry += rvy; rz += rvz

        // v2.0.0 scale physics
        svel += beat * 0.32f + bass * 0.20f
        svel += (1f - scale) * 0.18f
        svel *= 0.68f
        scale = (scale + svel).coerceIn(0.5f, 1.25f)

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
            val alpha    = (1f - age.toFloat() / trailCount.toFloat()) * 0.75f
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

        // ── Orbiting satellite cubes (v2.0.0: always 2, independent rotation) ─
        val satScale = minOf(scale * 0.28f, 0.55f)
        val ORB_R    = 2.6f
        orbAngle += 0.012f + beat * 0.04f

        // Independent slow rotation so satellites never look distorted at high intensity
        satRx += 0.018f; satRy += 0.026f

        // Compute current satellite projections (2 sats, 180° apart)
        val currentSatProj = Array(2) { si ->
            val theta = orbAngle + si * PI.toFloat()   // always 180° apart
            val ox    = ORB_R * cos(theta)
            val oy    = ORB_R * sin(theta)
            val verts3d = Array(8) { vi ->
                rotateVertex(
                    vertsBase[vi][0] * satScale,
                    vertsBase[vi][1] * satScale,
                    vertsBase[vi][2] * satScale,
                    satRx, satRy, 0f   // independent rotation, no Z
                )
            }
            projectSat(verts3d, ox, oy, satScale, draw.W, draw.H, fov)
        }

        // Store into satellite trail ring buffer
        for (si in 0..1) for (vi in 0..7) satTrailProj[satTrailHead][si][vi] = currentSatProj[si][vi]
        if (satTrailCount < SAT_TRAIL_LEN) satTrailCount++
        satTrailHead = (satTrailHead + 1) % SAT_TRAIL_LEN

        // ── Draw satellite trail + current satellites (additive blend) ─────────
        draw.setAdditiveBlend()

        for (age in satTrailCount - 1 downTo 1) {
            val frameIdx = (satTrailHead - 1 - age + SAT_TRAIL_LEN * 2) % SAT_TRAIL_LEN
            val alpha    = (1f - age.toFloat() / satTrailCount.toFloat()) * 0.06f
            for (si in 0..1) {
                val hOff = si.toFloat() * 0.5f
                val proj = satTrailProj[frameIdx][si]
                for ((ei, edge) in edges.withIndex()) {
                    val (a, b) = edge
                    val h     = (fadeHue + hOff + ei.toFloat() / edges.size * 0.4f) % 1f
                    val color = GLDraw.hsl(h, 1f, 0.18f + minOf(svel, 1f) * 0.28f)
                    draw.line(proj[a].first, proj[a].second, proj[b].first, proj[b].second,
                        color[0], color[1], color[2], alpha)
                }
            }
        }

        // Current satellites at full brightness
        val satL = (0.18f + minOf(svel, 1f) * 0.28f).coerceIn(0f, 1f)
        for (si in 0..1) {
            val hOff = si.toFloat() * 0.5f
            val proj = currentSatProj[si]
            for ((ei, edge) in edges.withIndex()) {
                val (a, b) = edge
                val h     = (fadeHue + hOff + ei.toFloat() / edges.size * 0.4f) % 1f
                val color = GLDraw.hsl(h, 1f, satL)
                draw.line(proj[a].first, proj[a].second, proj[b].first, proj[b].second,
                    color[0], color[1], color[2], 1f)
            }
        }

        draw.setNormalBlend()
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

    /**
     * Project satellite cube without distortion.
     * Orbit centre projected once with uniform 2-D scale; all vertices offset
     * from that screen centre. Centre clamped so satellite never leaves screen.
     */
    private fun projectSat(verts3d: Array<FloatArray>, ox: Float, oy: Float,
                           satScale: Float, W: Int, H: Int, fov: Float): Array<Pair<Float, Float>> {
        val z      = 3.8f
        val scaleS = fov / z
        var cxS    = ox * scaleS + W / 2f
        var cyS    = oy * scaleS + H / 2f
        val extent = satScale * scaleS + 2f
        cxS = cxS.coerceIn(extent, W - extent)
        cyS = cyS.coerceIn(extent, H - extent)
        return Array(verts3d.size) { vi ->
            (cxS + verts3d[vi][0] * scaleS) to (cyS + verts3d[vi][1] * scaleS)
        }
    }
}
