package com.androsaver.visualizer.modes

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw
import kotlin.math.*

/** First-person ride through a neon curving tube with beat-spawned flying triangles. */
class TunnelMode : BaseMode() {

    override val name = "Tunnel"

    private companion object {
        const val N_RINGS    = 30
        const val N_SIDES    = 20
        const val TUBE_R_MIN = 1.3f   // raised so viewer stays mostly inside the tube
        const val TUBE_R_MAX = 3.0f
        const val Z_FAR      = 10.0f
        const val Z_NEAR     = 0.18f
    }

    private data class Ring(var z: Float, var pt: Float)
    private data class Triangle(
        var z: Float, var pt: Float,
        var rot: Float, var rvel: Float,
        var size: Float, var hue: Float
    )

    private val rings    = ArrayList<Ring>(N_RINGS)
    private val tris     = ArrayList<Triangle>(120)
    private var hue      = 0f
    private var time     = 0f
    private var bassPulse = 0f
    private var bassVel   = 0f

    override fun reset() {
        hue = 0f; time = 0f; bassPulse = 0f; bassVel = 0f
        rings.clear(); tris.clear()
        val spacing = (Z_FAR - Z_NEAR) / N_RINGS
        for (i in 0 until N_RINGS) {
            val z = Z_NEAR + i * spacing
            rings.add(Ring(z, z))
        }
    }

    private fun path(t: Float): Pair<Float, Float> =
        Pair(sin(t * 0.21f) * 0.9f, cos(t * 0.16f) * 0.65f)

    private fun proj(wx: Float, wy: Float, wz: Float, W: Int, H: Int): Triple<Float, Float, Float> {
        val fov = minOf(W, H) * 0.75f
        val z = maxOf(wz, 0.01f)
        return Triple(wx * fov / z + W / 2f, wy * fov / z + H / 2f, fov / z)
    }

    override fun draw(draw: GLDraw, audio: AudioData, tick: Int) {
        draw.fadeBlack(0.11f)

        val fft = audio.fft
        val beat = audio.beat
        hue += 0.006f
        val bass = fft.meanSlice(0, 6)
        val mid  = fft.meanSlice(6, 30)
        val dt = 0.008f + bass * 0.04f + beat * 0.05f
        time += dt

        // Bass spring — drives triangle burst size
        bassVel   += bass * 2.5f - bassPulse * 0.5f
        bassVel   *= 0.68f
        bassPulse += bassVel

        // Spawn beat triangles — threshold and count scale with beat (i.e. with intensity)
        val tubeR = TUBE_R_MIN + (TUBE_R_MAX - TUBE_R_MIN) * (beat + bass * 0.5f).coerceIn(0f, 1f)
        if (beat > 1.3f) {
            val count = ((beat - 1.3f) * 10f).toInt().coerceAtLeast(1)
            repeat(count) {
                tris.add(Triangle(
                    z    = Z_FAR * (0.65f + Math.random().toFloat() * 0.30f),
                    pt   = time + Z_FAR * 0.8f,
                    rot  = (Math.random() * TAU).toFloat(),
                    rvel = (if (Math.random() < 0.5) 1 else -1) * (0.04f + Math.random().toFloat() * 0.08f),
                    size = 0.20f + Math.random().toFloat() * 0.35f,
                    hue  = (hue + Math.random().toFloat() * 0.5f) % 1f
                ))
            }
        }

        // Advance rings
        for (r in rings) {
            r.z -= dt
            if (r.z < Z_NEAR) { r.z += Z_FAR; r.pt = time + r.z }
        }
        val ordered = rings.sortedByDescending { it.z }

        // Draw ring pairs
        for (i in 0 until ordered.size - 1) {
            val r1 = ordered[i]; val r2 = ordered[i + 1]
            val (cx1, cy1) = path(r1.pt); val (sx1, sy1, sc1) = proj(cx1, cy1, r1.z, draw.W, draw.H)
            val (cx2, cy2) = path(r2.pt); val (sx2, sy2, sc2) = proj(cx2, cy2, r2.z, draw.W, draw.H)
            val sr1 = maxOf(1f, tubeR * sc1); val sr2 = maxOf(1f, tubeR * sc2)
            val nearT = maxOf(0f, 1f - r1.z / Z_FAR)
            val fi = minOf((nearT * fft.size * 0.8f).toInt(), fft.size - 1)
            val h = (hue + nearT) % 1f
            val bright = 0.06f + nearT * 0.70f + fft[fi] * 0.20f + beat * nearT * 0.50f

            val (hr, hg, hb) = hsl3(h, l = bright * 0.35f)
            draw.circle(sx1, sy1, sr1 + 4, hr, hg, hb, 0.6f, filled = false, segments = N_SIDES)
            val (cr, cg, cb) = hsl3(h, l = bright)
            draw.circle(sx1, sy1, sr1, cr, cg, cb, 1f, filled = false, segments = N_SIDES)

            // Longitudinal wall lines
            for (side in 0 until N_SIDES) {
                val angle = side.toFloat() / N_SIDES * TAU
                val p1x = sx1 + cos(angle) * sr1; val p1y = sy1 + sin(angle) * sr1
                val p2x = sx2 + cos(angle) * sr2; val p2y = sy2 + sin(angle) * sr2
                val hs = (h + side.toFloat() / N_SIDES * 0.25f) % 1f
                val (wr, wg, wb) = hsl3(hs, l = bright * 0.55f)
                draw.line(p1x, p1y, p2x, p2y, wr, wg, wb, 0.7f)
            }

            // Rotating inner polygon
            val nStar = 3 + (i % 4)
            val sDir = if (i % 2 == 0) 1f else -1f
            val sRot = time * 0.45f * sDir + i * 0.52f
            val sR = maxOf(2f, sr1 * 0.52f)
            val sH = (h + 0.5f) % 1f
            val sL = minOf(bright * 1.1f + mid * 0.2f, 0.92f)
            val sPts = FloatArray(nStar * 2)
            for (v in 0 until nStar) {
                val ang = v.toFloat() / nStar * TAU + sRot
                sPts[v * 2]     = sx1 + cos(ang) * sR
                sPts[v * 2 + 1] = sy1 + sin(ang) * sR
            }
            val (pr, pg, pb) = hsl3(sH, l = sL)
            draw.polygon(sPts, pr, pg, pb, 1f, filled = false)
        }

        // Beat triangles
        val liveTris = ArrayList<Triangle>()
        for (tri in tris) {
            tri.z   -= dt
            tri.rot += tri.rvel
            if (tri.z < Z_NEAR) continue
            val (tcx, tcy) = path(tri.pt)
            val (tsx, tsy, tsc) = proj(tcx, tcy, tri.z, draw.W, draw.H)
            val nearT = maxOf(0f, 1f - tri.z / Z_FAR)
            val tr = maxOf(3f, tri.size * tsc * (1f + bassPulse * 0.9f))
            val h  = (tri.hue + nearT * 0.4f) % 1f
            val bright = 0.35f + nearT * 0.60f
            val triPts = FloatArray(6)
            for (v in 0 until 3) {
                triPts[v * 2]     = tsx + cos(tri.rot + v * TAU / 3f) * tr
                triPts[v * 2 + 1] = tsy + sin(tri.rot + v * TAU / 3f) * tr
            }
            val (hr, hg, hb) = hsl3(h, l = bright * 0.30f)
            draw.polygon(triPts, hr, hg, hb, 0.7f, filled = false)
            val (cr, cg, cb) = hsl3(h, l = bright)
            draw.polygon(triPts, cr, cg, cb, 1f, filled = false)
            liveTris.add(tri)
        }
        tris.clear(); tris.addAll(liveTris.takeLast(120))
    }

    private fun hsl3(h: Float, s: Float = 1f, l: Float = 0.5f): Triple<Float, Float, Float> {
        val c = GLDraw.hsl(h, s, l); return Triple(c[0], c[1], c[2])
    }
}

private const val TAU = (Math.PI * 2).toFloat()
