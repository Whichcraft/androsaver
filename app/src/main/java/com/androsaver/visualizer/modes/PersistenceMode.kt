package com.androsaver.visualizer.modes

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw
import kotlin.math.*

/**
 * Persistence of Vision — stroboscopic rotating geometric shapes.
 *
 * Multiple nested polygons rotate at subtly different speeds.  With long
 * trail persistence their ghost images accumulate and interfere, creating
 * wagon-wheel moiré illusions and mandala-like kaleidoscope patterns.
 *
 *   Bass   → rotation speed burst
 *   Mid    → number of shapes / polygon complexity
 *   Treble → strobe flash
 *   Beat   → speed spike + hue jump
 *
 * Port of psysuals `effects/persistence.py` (v3.13.0 lineage).
 * TRAIL_ALPHA=5 → fadeBlack(5f/255f) — very long persistence for moiré build-up.
*/
private class Model(val verts: Array<FloatArray>, val edges: List<Pair<Int, Int>>)

class PersistenceMode : BaseMode() {

    override val name = "Persistence"
    private val rng = kotlin.random.Random(0x513E)

    private companion object {
        const val MAX_SHAPES = 8
        val TAU = (2.0 * PI).toFloat()
        private val models: List<Model>

        init {
            val phi = ((1.0 + sqrt(5.0)) / 2.0).toFloat()
            val invPhi = 1.0f / phi

            val rawModels = arrayOf(
                // 1. Tetrahedron
                arrayOf(
                    floatArrayOf(1f, 1f, 1f),
                    floatArrayOf(-1f, -1f, 1f),
                    floatArrayOf(-1f, 1f, -1f),
                    floatArrayOf(1f, -1f, -1f)
                ),
                // 2. Octahedron
                arrayOf(
                    floatArrayOf(1f, 0f, 0f), floatArrayOf(-1f, 0f, 0f),
                    floatArrayOf(0f, 1f, 0f), floatArrayOf(0f, -1f, 0f),
                    floatArrayOf(0f, 0f, 1f), floatArrayOf(0f, 0f, -1f)
                ),
                // 3. Cube
                arrayOf(
                    floatArrayOf(-1f, -1f, -1f), floatArrayOf(1f, -1f, -1f), floatArrayOf(1f, 1f, -1f), floatArrayOf(-1f, 1f, -1f),
                    floatArrayOf(-1f, -1f, 1f), floatArrayOf(1f, -1f, 1f), floatArrayOf(1f, 1f, 1f), floatArrayOf(-1f, 1f, 1f)
                ),
                // 4. Icosahedron
                arrayOf(
                    floatArrayOf(0f, -1f, -phi), floatArrayOf(0f, -1f, phi), floatArrayOf(0f, 1f, -phi), floatArrayOf(0f, 1f, phi),
                    floatArrayOf(-1f, -phi, 0f), floatArrayOf(-1f, phi, 0f), floatArrayOf(1f, -phi, 0f), floatArrayOf(1f, phi, 0f),
                    floatArrayOf(-phi, 0f, -1f), floatArrayOf(-phi, 0f, 1f), floatArrayOf(phi, 0f, -1f), floatArrayOf(phi, 0f, 1f)
                ),
                // 5. Dodecahedron
                arrayOf(
                    floatArrayOf(-1f, -1f, -1f), floatArrayOf(1f, -1f, -1f), floatArrayOf(1f, 1f, -1f), floatArrayOf(-1f, 1f, -1f),
                    floatArrayOf(-1f, -1f, 1f), floatArrayOf(1f, -1f, 1f), floatArrayOf(1f, 1f, 1f), floatArrayOf(-1f, 1f, 1f),
                    floatArrayOf(0f, -invPhi, -phi), floatArrayOf(0f, -invPhi, phi), floatArrayOf(0f, invPhi, -phi), floatArrayOf(0f, invPhi, phi),
                    floatArrayOf(-invPhi, -phi, 0f), floatArrayOf(-invPhi, phi, 0f), floatArrayOf(invPhi, -phi, 0f), floatArrayOf(invPhi, phi, 0f),
                    floatArrayOf(-phi, 0f, -invPhi), floatArrayOf(-phi, 0f, invPhi), floatArrayOf(phi, 0f, -invPhi), floatArrayOf(phi, 0f, invPhi)
                )
            )

            models = rawModels.map { rm ->
                val verts = Array(rm.size) { FloatArray(3) }
                for (idx in rm.indices) {
                    val x = rm[idx][0]
                    val y = rm[idx][1]
                    val z = rm[idx][2]
                    val len = sqrt(x * x + y * y + z * z)
                    val norm = if (len == 0f) 1f else len
                    verts[idx][0] = x / norm
                    verts[idx][1] = y / norm
                    verts[idx][2] = z / norm
                }

                val nVerts = verts.size
                val dists = ArrayList<Triple<Float, Int, Int>>()
                for (i in 0 until nVerts) {
                    for (j in i + 1 until nVerts) {
                        val dx = verts[i][0] - verts[j][0]
                        val dy = verts[i][1] - verts[j][1]
                        val dz = verts[i][2] - verts[j][2]
                        val d = sqrt(dx * dx + dy * dy + dz * dz)
                        dists.add(Triple(d, i, j))
                    }
                }
                var minDist = Float.MAX_VALUE
                for (d in dists) {
                    val distVal = d.first
                    if (distVal < minDist) {
                        minDist = distVal
                    }
                }
                val threshold = minDist * 1.05f
                val edges = dists.filter { it.first <= threshold }.map { Pair(it.second, it.third) }

                Model(verts, edges)
            }
        }
    }

    private val rotX = FloatArray(MAX_SHAPES) { rng.nextFloat() * TAU }
    private val rotY = FloatArray(MAX_SHAPES) { rng.nextFloat() * TAU }
    private val rotZ = FloatArray(MAX_SHAPES) { rng.nextFloat() * TAU }

    private val speedsX = FloatArray(MAX_SHAPES) { 0.008f * (1f + it * 0.12f) }
    private val speedsY = FloatArray(MAX_SHAPES) { 0.012f * (1f + it * 0.08f) }
    private val speedsZ = FloatArray(MAX_SHAPES) { 0.015f * (1f + it * 0.15f) }

    private var hue      = 0f
    private var boost    = 0f
    private var beatPrev = 0f
    private val rotOut   = FloatArray(3)

    override fun reset() {
        hue = 0f; boost = 0f; beatPrev = 0f
        for (i in 0 until MAX_SHAPES) {
            rotX[i] = rng.nextFloat() * TAU
            rotY[i] = rng.nextFloat() * TAU
            rotZ[i] = rng.nextFloat() * TAU
        }
    }

    private fun rotate(x: Float, y: Float, z: Float, ax: Float, ay: Float, az: Float, out: FloatArray) {
        val cx = cos(ax); val sx = sin(ax)
        val cy = cos(ay); val sy = sin(ay)
        val cz = cos(az); val sz = sin(az)

        val x1 = cz * x - sz * y
        val y1 = sz * x + cz * y
        val z1 = z

        val x2 = cy * x1 + sy * z1
        val y2 = y1
        val z2 = -sy * x1 + cy * z1

        out[0] = x2
        out[1] = cx * y2 - sx * z2
        out[2] = sx * y2 + cx * z2
    }

    override fun draw(draw: GLDraw, audio: AudioData, tick: Int) {
        val W = draw.W.toFloat(); val H = draw.H.toFloat()
        val bass = audio.beat
        val mid  = audio.mid
        val high = audio.treble

        hue   = (hue + 0.003f + mid * 0.003f) % 1f
        boost = maxOf(0f, boost - 0.04f)

        if (bass > 0.75f && beatPrev <= 0.75f) boost = 1.5f + bass * 1.0f
        beatPrev = bass

        draw.fadeBlack(5f / 255f)

        val cx      = W / 2f; val cy = H / 2f
        // Camera focal scale: the old value left the perspective solids
        // undersized after depth projection.
        val baseR   = H * 0.95f
        val fov     = baseR
        val spdMul  = 1f + bass * 2.5f + boost
        val nShapes = maxOf(3, minOf(MAX_SHAPES, 3 + (mid * 5).toInt()))

        for (i in 0 until nShapes) {
            rotX[i] += speedsX[i] * spdMul
            rotY[i] += speedsY[i] * spdMul
            rotZ[i] += speedsZ[i] * spdMul

            val ax = rotX[i]
            val ay = rotY[i]
            val az = rotZ[i]

            val rLocal = 0.2f + 0.8f * (i + 1) / nShapes
            val h      = (hue + i.toFloat() / nShapes * 0.55f) % 1f
            val bright = 0.28f + bass * 0.25f + if (i == nShapes - 1) high * 0.12f else 0f

            val modelIdx = i % models.size
            val model = models[modelIdx]
            val uVerts = model.verts
            val edges = model.edges

            val projX = FloatArray(uVerts.size)
            val projY = FloatArray(uVerts.size)
            val depths = FloatArray(uVerts.size)

            for (vIdx in uVerts.indices) {
                val uVert = uVerts[vIdx]
                val sxVal = uVert[0] * rLocal
                val syVal = uVert[1] * rLocal
                val szVal = uVert[2] * rLocal

                rotate(sxVal, syVal, szVal, ax, ay, az, rotOut)
                val rx = rotOut[0]
                val ry = rotOut[1]
                val rz = rotOut[2]

                val camZ = 2.2f
                var depth = camZ + rz
                if (depth <= 0.1f) {
                    depth = 0.1f
                }

                projX[vIdx] = cx + rx * fov / depth
                projY[vIdx] = cy + ry * fov / depth
                depths[vIdx] = rz
            }

            for (edge in edges) {
                val a = edge.first
                val b = edge.second
                val avgZ = (depths[a] + depths[b]) / 2f
                val zNorm = avgZ / rLocal
                var dFactor = 0.15f + 0.85f * (1f - (zNorm + 1f) / 2f)
                dFactor = dFactor.coerceIn(0.15f, 1f)

                val col = GLDraw.hsl(h, l = bright * dFactor)
                val glow = GLDraw.hsl(h, l = bright * 0.30f * dFactor)

                // Double-pass line drawing for glow effect
                draw.line(projX[a], projY[a], projX[b], projY[b], glow[0], glow[1], glow[2], glow[3])
                draw.line(projX[a], projY[a], projX[b], projY[b], col[0], col[1], col[2], col[3])
            }
        }

    }
}
