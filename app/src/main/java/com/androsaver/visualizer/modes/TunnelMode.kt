package com.androsaver.visualizer.modes

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw
import kotlin.math.*

/** Smooth ride through a neon tunnel; bass punches spawn triangles on the center line. */
class TunnelMode : BaseMode() {

    override val name = "Tunnel"

    private companion object {
        const val N_RINGS    = 30
        const val N_SIDES    = 20
        const val TUBE_R_MIN = 1.3f
        const val TUBE_R_MAX = 3.0f
        const val Z_FAR      = 10.0f
        const val Z_NEAR     = 0.18f
        const val DT         = 0.018f   // constant speed — smooth ride always
        const val BASS_THRESH = 0.35f   // bass level that triggers a triangle spawn
        const val SPAWN_COOLDOWN = 8    // minimum frames between spawns
    }

    private data class Ring(var z: Float, var pt: Float)
    private data class BassTri(
        var z: Float,
        var pt: Float,      // path-time — keeps triangle on the tunnel's center line
        var rot: Float,
        val rvel: Float,
        var hue: Float,
        val sizeFrac: Float // triangle radius as fraction of tube radius at its z
    )

    private val rings = ArrayList<Ring>(N_RINGS)
    private val tris  = ArrayList<BassTri>(60)
    private var hue       = 0f
    private var time      = 0f
    private var prevBass  = 0f
    private var cooldown  = 0

    override fun reset() {
        hue = 0f; time = 0f; prevBass = 0f; cooldown = 0
        rings.clear(); tris.clear()
        val spacing = (Z_FAR - Z_NEAR) / N_RINGS
        for (i in 0 until N_RINGS) {
            val z = Z_NEAR + i * spacing
            rings.add(Ring(z, z))
        }
    }

    /** Tunnel center path — gentle S-curve in XY. */
    private fun path(t: Float): Pair<Float, Float> =
        Pair(sin(t * 0.21f) * 0.9f, cos(t * 0.16f) * 0.65f)

    /** Perspective projection. Returns (screenX, screenY, scale). */
    private fun proj(wx: Float, wy: Float, wz: Float, W: Int, H: Int): Triple<Float, Float, Float> {
        val fov = minOf(W, H) * 0.75f
        val z = maxOf(wz, 0.01f)
        return Triple(wx * fov / z + W / 2f, wy * fov / z + H / 2f, fov / z)
    }

    override fun draw(draw: GLDraw, audio: AudioData, tick: Int) {
        draw.fadeBlack(0.10f)

        val fft  = audio.fft
        val beat = audio.beat
        val bass = fft.meanSlice(0, 6)
        val mid  = fft.meanSlice(6, 30)
        hue += 0.005f
        time += DT
        if (cooldown > 0) cooldown--

        // Tube radius breathes slightly with beat — still smooth
        val tubeR = TUBE_R_MIN + (TUBE_R_MAX - TUBE_R_MIN) * (beat * 0.6f + bass * 0.4f).coerceIn(0f, 1f)

        // Spawn a triangle on each bass punch (rising edge + cooldown)
        if (bass > BASS_THRESH && prevBass <= BASS_THRESH && cooldown == 0) {
            val spin = (if (Math.random() < 0.5) 1 else -1) * (0.015f + Math.random().toFloat() * 0.025f)
            tris.add(BassTri(
                z        = Z_FAR,
                pt       = time + Z_FAR,
                rot      = (Math.random() * TAU).toFloat(),
                rvel     = spin,
                hue      = (hue + 0.4f + Math.random().toFloat() * 0.3f) % 1f,
                sizeFrac = 0.40f + Math.random().toFloat() * 0.25f
            ))
            cooldown = SPAWN_COOLDOWN
        }
        prevBass = bass

        // Advance rings
        for (r in rings) {
            r.z -= DT
            if (r.z < Z_NEAR) { r.z += Z_FAR; r.pt = time + r.z }
        }
        val ordered = rings.sortedByDescending { it.z }

        // Draw tunnel rings + wall lines
        for (i in 0 until ordered.size - 1) {
            val r1 = ordered[i]; val r2 = ordered[i + 1]
            val (cx1, cy1) = path(r1.pt); val (sx1, sy1, sc1) = proj(cx1, cy1, r1.z, draw.W, draw.H)
            val (cx2, cy2) = path(r2.pt); val (sx2, sy2, sc2) = proj(cx2, cy2, r2.z, draw.W, draw.H)
            val sr1 = maxOf(1f, tubeR * sc1); val sr2 = maxOf(1f, tubeR * sc2)
            val nearT = maxOf(0f, 1f - r1.z / Z_FAR)
            val fi = minOf((nearT * fft.size * 0.8f).toInt(), fft.size - 1)
            val h = (hue + nearT) % 1f
            val bright = 0.06f + nearT * 0.70f + fft[fi] * 0.20f + beat * nearT * 0.40f

            val (hr, hg, hb) = hsl3(h, l = bright * 0.35f)
            draw.circle(sx1, sy1, sr1 + 4, hr, hg, hb, 0.55f, filled = false, segments = N_SIDES)
            val (cr, cg, cb) = hsl3(h, l = bright)
            draw.circle(sx1, sy1, sr1, cr, cg, cb, 1f, filled = false, segments = N_SIDES)

            for (side in 0 until N_SIDES) {
                val angle = side.toFloat() / N_SIDES * TAU
                val p1x = sx1 + cos(angle) * sr1; val p1y = sy1 + sin(angle) * sr1
                val p2x = sx2 + cos(angle) * sr2; val p2y = sy2 + sin(angle) * sr2
                val hs = (h + side.toFloat() / N_SIDES * 0.25f) % 1f
                val (wr, wg, wb) = hsl3(hs, l = bright * 0.55f)
                draw.line(p1x, p1y, p2x, p2y, wr, wg, wb, 0.7f)
            }
        }

        // Draw bass triangles — centered on tunnel path, growing toward viewer
        val liveTris = ArrayList<BassTri>()
        for (tri in tris) {
            tri.z   -= DT
            tri.rot += tri.rvel
            if (tri.z < Z_NEAR) continue
            val (tcx, tcy) = path(tri.pt)
            val (tsx, tsy, tsc) = proj(tcx, tcy, tri.z, draw.W, draw.H)
            val nearT = maxOf(0f, 1f - tri.z / Z_FAR)
            // Triangle radius in screen pixels — scales with perspective, matches tube fraction
            val tr = maxOf(4f, tubeR * tsc * tri.sizeFrac)
            val h  = (tri.hue + nearT * 0.2f) % 1f
            val bright = 0.35f + nearT * 0.60f
            val pts = FloatArray(6)
            for (v in 0 until 3) {
                pts[v * 2]     = tsx + cos(tri.rot + v * TAU / 3f) * tr
                pts[v * 2 + 1] = tsy + sin(tri.rot + v * TAU / 3f) * tr
            }
            val (gr, gg, gb) = hsl3(h, l = bright * 0.25f)
            draw.polygon(pts, gr, gg, gb, 0.65f, filled = false)
            val (cr, cg, cb) = hsl3(h, l = bright)
            draw.polygon(pts, cr, cg, cb, 1f, filled = false)
            liveTris.add(tri)
        }
        tris.clear(); tris.addAll(liveTris.takeLast(60))
    }

    private fun hsl3(h: Float, s: Float = 1f, l: Float = 0.5f): Triple<Float, Float, Float> {
        val c = GLDraw.hsl(h, s, l); return Triple(c[0], c[1], c[2])
    }
}

private const val TAU = (Math.PI * 2).toFloat()
