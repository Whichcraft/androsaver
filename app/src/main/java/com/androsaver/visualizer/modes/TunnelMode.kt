package com.androsaver.visualizer.modes

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw
import kotlin.math.*

/**
 * First-person tunnel ride.
 * Rings scroll toward the camera with rotating star polygons at each ring centre.
 * Triangles are spawned continuously by bass/beat and fly toward the camera.
 * Port of psysuals Tunnel (v2.0.0 restored original).
 */
class TunnelMode : BaseMode() {

    override val name = "Tunnel"

    private companion object {
        const val N_RINGS = 30
        const val N_SIDES = 20
        const val TUBE_R  = 2.8f
        const val Z_FAR   = 10.0f
        const val Z_NEAR  = 0.18f
        const val TAU     = (Math.PI * 2).toFloat()
    }

    private data class Ring(var z: Float, var pt: Float)
    private data class Tri(
        var z: Float,
        var rot: Float, val rvel: Float,
        var hue: Float,
        val size: Float,   // pre-computed at spawn (includes bass at spawn time)
        val pt: Float
    )

    private val rings = ArrayList<Ring>(N_RINGS)
    private val tris  = ArrayList<Tri>(120)
    private var hue  = 0f
    private var time = 0f

    override fun reset() {
        hue = 0f; time = 0f
        rings.clear(); tris.clear()
        val spacing = (Z_FAR - Z_NEAR) / N_RINGS
        for (i in 0 until N_RINGS) {
            val z = Z_NEAR + i * spacing
            rings.add(Ring(z, z))
        }
    }

    private fun path(t: Float): Pair<Float, Float> =
        sin(t * 0.21f) * 0.8f to cos(t * 0.16f) * 0.6f

    private fun proj(wx: Float, wy: Float, wz: Float, W: Int, H: Int): Triple<Float, Float, Float> {
        val fov = minOf(W, H) * 0.75f
        val z   = maxOf(wz, 0.01f)
        return Triple(wx * fov / z + W / 2f, wy * fov / z + H / 2f, fov / z)
    }

    override fun draw(draw: GLDraw, audio: AudioData, tick: Int) {
        draw.fadeBlack(0.11f)

        val fft  = audio.fft
        val beat = audio.beat
        val bass = fft.meanSlice(0, 6)
        val mid  = fft.meanSlice(6, 30)
        hue += 0.006f

        val dt = 0.03f + bass * 0.09f + beat * 0.18f
        time += dt

        // ── Spawn triangles ───────────────────────────────────────────────────
        val spawnN = (bass * 4.0f + if (beat > 0.3f) beat * 6.0f else 0f).toInt()
        repeat(spawnN) {
            val spawnZ = Z_FAR * (0.65f + Math.random().toFloat() * 0.30f)
            val rvel   = (if (Math.random() < 0.5) 1f else -1f) *
                         (0.04f + Math.random().toFloat() * 0.08f)
            val size   = (0.45f + Math.random().toFloat() * 0.65f) * (1.0f + bass * 1.5f)
            tris.add(Tri(
                z    = spawnZ,
                rot  = (Math.random() * TAU).toFloat(),
                rvel = rvel,
                hue  = (hue + Math.random().toFloat() * 0.5f) % 1f,
                size = size,
                pt   = time + spawnZ
            ))
        }

        // ── Advance rings ─────────────────────────────────────────────────────
        for (r in rings) {
            r.z -= dt
            if (r.z < Z_NEAR) { r.z += Z_FAR; r.pt = time + r.z }
        }
        val ordered = rings.sortedByDescending { it.z }

        // ── Draw tunnel rings + interior stars ────────────────────────────────
        for (i in 0 until ordered.size - 1) {
            val r1 = ordered[i]; val r2 = ordered[i + 1]
            val (cx1, cy1) = path(r1.pt)
            val (sx1, sy1, sc1) = proj(cx1, cy1, r1.z, draw.W, draw.H)
            val (cx2, cy2) = path(r2.pt)
            val (_, _, sc2) = proj(cx2, cy2, r2.z, draw.W, draw.H)

            val sr1 = maxOf(1f, TUBE_R * sc1)
            val sr2 = maxOf(1f, TUBE_R * sc2)

            val nearT = maxOf(0f, 1f - r1.z / Z_FAR)
            val fi    = minOf((nearT * fft.size * 0.8f).toInt(), fft.size - 1)
            val h     = (hue + nearT) % 1f
            val bright = (0.06f + nearT * 0.70f + fft[fi] * 0.20f +
                          beat * nearT * 0.50f).coerceIn(0f, 1f)

            val (hr, hg, hb) = hsl3(h, l = bright * 0.35f)
            draw.circle(sx1, sy1, sr1 + 4f, hr, hg, hb, 0.55f, filled = false, segments = N_SIDES)
            val (cr, cg, cb) = hsl3(h, l = bright)
            draw.circle(sx1, sy1, sr1, cr, cg, cb, 1f, filled = false, segments = N_SIDES)

            for (side in 0 until N_SIDES) {
                val angle = side.toFloat() / N_SIDES * TAU
                val p1x = sx1 + cos(angle) * sr1; val p1y = sy1 + sin(angle) * sr1
                val p2x = sx1 + cos(angle) * sr2; val p2y = sy1 + sin(angle) * sr2
                val hs = (h + side.toFloat() / N_SIDES * 0.25f) % 1f
                val (wr, wg, wb) = hsl3(hs, l = bright * 0.55f)
                draw.line(p1x, p1y, p2x, p2y, wr, wg, wb, 0.7f)
            }

            // Interior rotating star polygon
            val nStar  = 3 + (i % 4)
            val sDir   = if (i % 2 == 0) 1f else -1f
            val sRot   = time * 0.45f * sDir + i * 0.52f
            val sR     = maxOf(2f, sr1 * 0.24f)
            val sH     = (h + 0.5f) % 1f
            val sL     = minOf(bright * 1.1f + mid * 0.2f, 0.92f)
            val sPts   = FloatArray(nStar * 2)
            for (v in 0 until nStar) {
                sPts[v * 2]     = sx1 + cos(v.toFloat() / nStar * TAU + sRot) * sR
                sPts[v * 2 + 1] = sy1 + sin(v.toFloat() / nStar * TAU + sRot) * sR
            }
            val (sr, sg, sb) = hsl3(sH, l = sL)
            draw.polygon(sPts, sr, sg, sb, 1f, filled = false)
        }

        // ── Advance and draw triangles ────────────────────────────────────────
        val liveTris = ArrayList<Tri>()
        for (tri in tris) {
            tri.z   -= dt
            tri.rot += tri.rvel
            if (tri.z < Z_NEAR) continue
            val (tcx, tcy) = path(tri.pt)
            val (tsx, tsy, tsc) = proj(tcx, tcy, tri.z, draw.W, draw.H)
            val nearT  = maxOf(0f, 1f - tri.z / Z_FAR)
            val tr     = maxOf(3f, tri.size * tsc)
            val h      = (tri.hue + nearT * 0.4f) % 1f
            val bright = (0.35f + nearT * 0.60f).coerceAtMost(0.95f)
            val pts = FloatArray(6)
            for (v in 0 until 3) {
                pts[v * 2]     = tsx + cos(tri.rot + v * TAU / 3f) * tr
                pts[v * 2 + 1] = tsy + sin(tri.rot + v * TAU / 3f) * tr
            }
            val (dr, dg, db) = hsl3(h, l = bright * 0.30f)
            draw.polygon(pts, dr, dg, db, 0.8f, filled = false)
            val (cr, cg, cb) = hsl3(h, l = bright)
            draw.polygon(pts, cr, cg, cb, 1f, filled = false)
            liveTris.add(tri)
        }
        tris.clear(); tris.addAll(if (liveTris.size > 120) liveTris.takeLast(120) else liveTris)
    }

    private fun hsl3(h: Float, s: Float = 1f, l: Float = 0.5f): Triple<Float, Float, Float> {
        val c = GLDraw.hsl(h, s, l); return Triple(c[0], c[1], c[2])
    }
}
