package com.androsaver.visualizer.modes

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw
import kotlin.math.*

/**
 * First-person tunnel ride.
 * Rings scroll toward the camera with rotating star polygons at each ring centre.
 * Triangles are spawned continuously by bass/beat and fly toward the camera.
 * Port of psysuals Tunnel (v3.13.0).
 */
class TunnelMode : BaseMode() {

    override val name = "Tunnel"

    private companion object {
        const val N_RINGS = 30
        const val N_SIDES = 20
        const val TUBE_R  = 2.8f
        const val Z_FAR   = 10.0f
        const val Z_NEAR  = 0.06f
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
    private val ordered = ArrayList<Ring>(N_RINGS)
    private val pathScratch = FloatArray(2)
    private val projScratch = FloatArray(3)
    private val starPts = FloatArray(12)
    private val triPts = FloatArray(6)
    private val colorScratch = FloatArray(4)
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

    private fun path(t: Float, treble: Float = 0f, out: FloatArray = pathScratch) {
        val scale = 1f + treble * 0.45f
        out[0] = sin(t * 0.21f) * 0.8f * scale
        out[1] = cos(t * 0.16f) * 0.6f * scale
    }

    private fun proj(wx: Float, wy: Float, wz: Float, W: Int, H: Int, out: FloatArray = projScratch) {
        val fov = minOf(W, H) * 0.75f
        val z   = maxOf(wz, 0.01f)
        out[0] = wx * fov / z + W / 2f
        out[1] = wy * fov / z + H / 2f
        out[2] = fov / z
    }

    override fun draw(draw: GLDraw, audio: AudioData, tick: Int) {
        draw.fadeBlack(0.11f)

        val beat = audio.beat
        val bass = beat
        val mid  = audio.mid
        val high = audio.treble
        val motion = displayMotionScale(draw)
        val bassM = bass * motion
        val midM = mid * motion
        val highM = high * motion
        hue += 0.006f

        val dt = 0.018f + bassM * 0.09f + midM * 0.04f + highM * 0.03f
        time += dt

        // ── Spawn triangles ───────────────────────────────────────────────────
        val spawnN = (bassM * 1.2f + if (midM > 0.5f) midM * 1.5f else 0f).toInt()
        repeat(spawnN) {
            val spawnZ = Z_FAR * (0.80f + Math.random().toFloat() * 0.18f)
            val rvel   = (if (Math.random() < 0.5) 1f else -1f) *
                         (0.04f + Math.random().toFloat() * 0.08f) * (1f + midM * 1.5f)
            val size   = (0.45f + Math.random().toFloat() * 0.65f) * (1.0f + bassM * 1.5f + highM * 0.5f)
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
        ordered.clear()
        // Insertion-sort the fixed 30-ring list in place; this avoids creating a
        // comparator/iterator object on every frame while preserving draw order.
        for (ring in rings) {
            var insertAt = ordered.size
            while (insertAt > 0 && ordered[insertAt - 1].z < ring.z) insertAt--
            ordered.add(insertAt, ring)
        }

        // ── Draw tunnel rings + interior stars ────────────────────────────────
        for (i in 0 until ordered.size - 1) {
            val r1 = ordered[i]; val r2 = ordered[i + 1]
            path(r1.pt, highM)
            val cx1 = pathScratch[0]; val cy1 = pathScratch[1]
            proj(cx1, cy1, r1.z, draw.W, draw.H)
            val sx1 = projScratch[0]; val sy1 = projScratch[1]; val sc1 = projScratch[2]
            path(r2.pt, highM)
            val cx2 = pathScratch[0]; val cy2 = pathScratch[1]
            proj(cx2, cy2, r2.z, draw.W, draw.H)
            val sx2 = projScratch[0]; val sy2 = projScratch[1]; val sc2 = projScratch[2]

            val sr1 = maxOf(1f, TUBE_R * sc1)
            val sr2 = maxOf(1f, TUBE_R * sc2)

            val nearT = maxOf(0f, 1f - r1.z / Z_FAR)
            val h     = (hue + nearT) % 1f
            val bright = (0.06f + nearT * 0.60f + midM * 0.15f * nearT + bassM * nearT * 0.40f).coerceIn(0f, 1f)

            val glowColor = hsl3(h, l = bright * 0.35f)
            val hr = glowColor[0]; val hg = glowColor[1]; val hb = glowColor[2]
            draw.circle(sx1, sy1, sr1 + 4f, hr, hg, hb, 0.55f, filled = false, segments = N_SIDES)
            val coreColor = hsl3(h, l = bright)
            val cr = coreColor[0]; val cg = coreColor[1]; val cb = coreColor[2]
            draw.circle(sx1, sy1, sr1, cr, cg, cb, 1f, filled = false, segments = N_SIDES)

            for (side in 0 until N_SIDES) {
                val angle = side.toFloat() / N_SIDES * TAU
                val p1x = sx1 + cos(angle) * sr1; val p1y = sy1 + sin(angle) * sr1
                val p2x = sx2 + cos(angle) * sr2; val p2y = sy2 + sin(angle) * sr2
                val hs = (h + side.toFloat() / N_SIDES * 0.25f + highM * 0.10f) % 1f
                val sideColor = hsl3(hs, l = bright * 0.55f)
                val wr = sideColor[0]; val wg = sideColor[1]; val wb = sideColor[2]
                draw.line(p1x, p1y, p2x, p2y, wr, wg, wb, 0.7f)
            }

            // Interior rotating star polygon
            val nStar  = 3 + (i % 4)
            val sDir   = if (i % 2 == 0) 1f else -1f
            val sRot   = time * 0.45f * sDir + i * 0.52f + midM * 0.15f
            val sR     = maxOf(2f, sr1 * (0.24f + highM * 0.12f))
            val sH     = (h + 0.5f) % 1f
            val sL     = minOf(bright * 1.1f + midM * 0.25f + highM * 0.15f, 0.95f)
            for (v in 0 until nStar) {
                starPts[v * 2]     = sx1 + cos(v.toFloat() / nStar * TAU + sRot) * sR
                starPts[v * 2 + 1] = sy1 + sin(v.toFloat() / nStar * TAU + sRot) * sR
            }
            val starColor = hsl3(sH, l = sL)
            draw.polygon(starPts, nStar, starColor[0], starColor[1], starColor[2], 1f, filled = false)
        }

        // ── Advance and draw triangles ────────────────────────────────────────
        var writeIndex = 0
        for (readIndex in tris.indices) {
            val tri = tris[readIndex]
            tri.z   -= dt
            tri.rot += tri.rvel * (1f + midM * 1.5f)
            if (tri.z < Z_NEAR) continue
            path(tri.pt, highM)
            val tcx = pathScratch[0]; val tcy = pathScratch[1]
            proj(tcx, tcy, tri.z, draw.W, draw.H)
            val tsx = projScratch[0]; val tsy = projScratch[1]; val tsc = projScratch[2]
            val nearT  = maxOf(0f, 1f - tri.z / Z_FAR)
            val tr     = maxOf(3f, tri.size * tsc)
            val h      = (tri.hue + nearT * 0.4f) % 1f
            val bright = (0.35f + nearT * 0.60f).coerceAtMost(0.95f)
            for (v in 0 until 3) {
                triPts[v * 2]     = tsx + cos(tri.rot + v * TAU / 3f) * tr
                triPts[v * 2 + 1] = tsy + sin(tri.rot + v * TAU / 3f) * tr
            }
            val dimColor = hsl3(h, l = bright * 0.30f)
            draw.polygon(triPts, dimColor[0], dimColor[1], dimColor[2], 0.8f, filled = false)
            val triColor = hsl3(h, l = bright)
            draw.polygon(triPts, triColor[0], triColor[1], triColor[2], 1f, filled = false)
            if (writeIndex != readIndex) tris[writeIndex] = tri
            writeIndex++
        }
        while (tris.size > writeIndex) tris.removeAt(tris.lastIndex)
        if (tris.size > 30) {
            val drop = tris.size - 30
            repeat(drop) { tris.removeAt(0) }
        }
    }

    private fun hsl3(h: Float, s: Float = 1f, l: Float = 0.5f): FloatArray {
        GLDraw.hsl(h, s, l, 1f, colorScratch)
        return colorScratch
    }
}
