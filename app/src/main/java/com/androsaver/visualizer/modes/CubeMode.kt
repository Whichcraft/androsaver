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
    private var spinDir  = 1f   // +1 or -1, flips on bass punch
    private var prevBass = 0f

    // Main cube trail ring buffer
    private val TRAIL_LEN = 14
    private val trailProj  = Array(TRAIL_LEN) { Array(2) { Array(8) { 0f to 0f } } }
    private var trailHead  = 0
    private var trailCount = 0

    // Satellite trail: slower fade (longer persistence), drawn with additive blend
    private val SAT_TRAIL_LEN = 30
    // Each slot: list of (proj array, nSats) so variable count is handled correctly
    private val satTrailProj  = Array(SAT_TRAIL_LEN) { Array(6) { Array(8) { 0f to 0f } } }
    private val satTrailNSats = IntArray(SAT_TRAIL_LEN)
    private var satTrailHead  = 0
    private var satTrailCount = 0

    override fun reset() {
        rx = 0f; ry = 0f; rz = 0f
        fadeHue = 0f
        scale = 1f; svel = 0f
        rvx = 0f; rvy = 0f; rvz = 0f
        orbAngle = 0f; spinDir = 1f; prevBass = 0f
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

        // Flip spin direction on each bass punch (rising edge)
        if (bass > 0.4f && prevBass <= 0.4f) spinDir = -spinDir
        prevBass = bass

        rvx += spinDir * (0.00025f + mid  * 0.0015f + beat * 0.0156f)
        rvy += spinDir * (0.00035f + bass * 0.0015f + beat * 0.0208f)
        rvz += spinDir * (0.00018f + high * 0.0010f + beat * 0.0078f)
        rvx *= 0.86f; rvy *= 0.86f; rvz *= 0.86f
        rx += rvx; ry += rvy; rz += rvz

        svel += beat * 1.69f + bass * 0.40f
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

        // ── Orbiting satellite cubes ──────────────────────────────────────────
        // Count scales with beat intensity: 2 at baseline, up to 6 at max beat
        val nSats    = 2 + (beat.coerceAtMost(2f) * 2).toInt()   // 2 … 6
        val satScale = scale * 0.28f
        val baseOrbR = 2.6f
        val sz0      = 3.8f  // approximate z-depth (matches project() offset)
        orbAngle    += 0.012f + beat * 0.04f

        // Screen-space half-extent of a rotated satellite cube corner
        val satHalfPx  = satScale * 1.5f * fov / sz0
        val maxOrbitPx = minOf(draw.W, draw.H) / 2f - satHalfPx - 8f

        // Compute current satellite projections
        val currentSatProj = Array(nSats) { si ->
            val effectiveOrbR = if (maxOrbitPx > 0f) minOf(baseOrbR, maxOrbitPx * sz0 / fov) else 0f
            val theta = orbAngle + si.toFloat() / nSats * (2f * PI.toFloat())
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
            projectSat(verts3d, ox, oy, satScale, draw.W, draw.H, fov)
        }

        // Store into satellite trail ring buffer
        for (si in 0 until minOf(nSats, 6)) {
            for (vi in 0..7) satTrailProj[satTrailHead][si][vi] = currentSatProj[si][vi]
        }
        satTrailNSats[satTrailHead] = nSats
        if (satTrailCount < SAT_TRAIL_LEN) satTrailCount++
        satTrailHead = (satTrailHead + 1) % SAT_TRAIL_LEN

        // ── Draw satellite trail + current satellites (additive blend) ─────────
        draw.setAdditiveBlend()

        for (age in satTrailCount - 1 downTo 1) {
            val frameIdx = (satTrailHead - 1 - age + SAT_TRAIL_LEN * 2) % SAT_TRAIL_LEN
            val alpha    = (1f - age.toFloat() / satTrailCount.toFloat()) * 0.06f
            val n        = satTrailNSats[frameIdx]
            for (si in 0 until minOf(n, 6)) {
                val hOff = si.toFloat() / n * 0.6f
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
        for (si in 0 until nSats) {
            val hOff = si.toFloat() / nSats * 0.6f
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
     * Orbit centre projected once with uniform 2-D scale (satellites orbit at z=0,
     * so effective depth = 3.8); all vertices offset from that screen centre.
     * Centre clamped so the satellite never leaves the screen.
     * Returns array of 8 screen-space Pairs.
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
