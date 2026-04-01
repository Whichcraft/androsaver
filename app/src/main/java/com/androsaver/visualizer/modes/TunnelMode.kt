package com.androsaver.visualizer.modes

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw
import kotlin.math.*

/**
 * First-person tunnel ride.
 * Rings scroll toward the camera; bass expands tube radius and drives ring
 * brightness + line weight.  Sparks (triangles) are spawned continuously by
 * bass/beat and drawn with additive blending on top of tunnel geometry so
 * ring lines can never overwrite spark trails.
 * Port of psysuals Tunnel (v1.4.3).
 */
class TunnelMode : BaseMode() {

    override val name = "Tunnel"

    private companion object {
        const val N_RINGS = 30
        const val N_SIDES = 20
        const val TUBE_R  = 2.0f
        const val Z_FAR   = 10.0f
        const val Z_NEAR  = 0.18f
        const val TAU     = (Math.PI * 2).toFloat()
        // Spark trail ring-buffer length — longer than main trail for persistent glow
        const val SPARK_TRAIL_LEN = 40
    }

    private data class Ring(var z: Float, var pt: Float)
    private data class Tri(
        var z: Float,
        var rot: Float, val rvel: Float,
        var hue: Float, val sizeFrac: Float,
        val pt: Float
    )

    // Spark trail: ring buffer of (sx, sy, r, h, bright) per frame
    private data class SparkDot(val x: Float, val y: Float, val r: Float, val h: Float, val bright: Float)
    private val sparkTrail = Array(SPARK_TRAIL_LEN) { ArrayList<SparkDot>() }
    private var sparkTrailHead = 0

    private val rings = ArrayList<Ring>(N_RINGS)
    private val tris  = ArrayList<Tri>(60)
    private var hue  = 0f
    private var time = 0f

    override fun reset() {
        hue = 0f; time = 0f
        rings.clear(); tris.clear()
        for (i in 0 until SPARK_TRAIL_LEN) sparkTrail[i].clear()
        sparkTrailHead = 0
        val spacing = (Z_FAR - Z_NEAR) / N_RINGS
        for (i in 0 until N_RINGS) {
            val z = Z_NEAR + i * spacing
            rings.add(Ring(z, z))
        }
    }

    private fun path(t: Float): Pair<Float, Float> =
        sin(t * 0.21f) * 0.9f to cos(t * 0.16f) * 0.65f

    private fun proj(wx: Float, wy: Float, wz: Float, W: Int, H: Int): Triple<Float, Float, Float> {
        val fov = minOf(W, H) * 0.75f
        val z = maxOf(wz, 0.01f)
        return Triple(wx * fov / z + W / 2f, wy * fov / z + H / 2f, fov / z)
    }

    override fun draw(draw: GLDraw, audio: AudioData, tick: Int) {
        draw.fadeBlack(0.11f)

        val fft  = audio.fft
        val beat = audio.beat
        val bass = fft.meanSlice(0, 6)
        val mid  = fft.meanSlice(6, 30)
        hue += 0.006f

        // Variable speed: bass and beat drive scroll rate (psysuals v1.4.3)
        val dt = 0.03f + bass * 0.14f + beat * 0.22f
        time += dt

        // ── Continuous spark spawning (psysuals v1.4.3 style) ─────────────────
        val spawnN = (bass * 0.6f + if (beat > 0.4f) beat * 1.5f else 0f).toInt()
        repeat(spawnN) {
            val spawnZ = Z_FAR * (0.65f + Math.random().toFloat() * 0.30f)
            val rvel   = (if (Math.random() < 0.5) 1 else -1) *
                         (0.04f + Math.random().toFloat() * 0.10f)
            val sizeFrac = 0.5f + Math.random().toFloat() * 0.7f
            tris.add(Tri(
                z        = spawnZ,
                rot      = (Math.random() * TAU).toFloat(),
                rvel     = rvel,
                hue      = (hue + 0.3f + Math.random().toFloat() * 0.5f) % 1f,
                sizeFrac = sizeFrac,
                pt       = time + spawnZ
            ))
        }

        // ── Advance rings ─────────────────────────────────────────────────────
        for (r in rings) {
            r.z -= dt
            if (r.z < Z_NEAR) { r.z += Z_FAR; r.pt = time + r.z }
        }
        val ordered = rings.sortedByDescending { it.z }

        // ── Draw tunnel rings (normal blend) ──────────────────────────────────
        for (i in 0 until ordered.size - 1) {
            val r1 = ordered[i]; val r2 = ordered[i + 1]
            val (cx1, cy1) = path(r1.pt)
            val (sx1, sy1, sc1) = proj(cx1, cy1, r1.z, draw.W, draw.H)
            val (cx2, cy2) = path(r2.pt)
            val (_, _, sc2) = proj(cx2, cy2, r2.z, draw.W, draw.H)

            // Bass expands tube radius
            val sr1 = maxOf(1f, (TUBE_R + bass * 0.6f) * sc1)
            val sr2 = maxOf(1f, (TUBE_R + bass * 0.6f) * sc2)

            val nearT = maxOf(0f, 1f - r1.z / Z_FAR)
            val fi    = minOf((nearT * fft.size * 0.8f).toInt(), fft.size - 1)
            val h     = (hue + nearT) % 1f
            // Bass boosts ring brightness and line weight
            val bright = (0.06f + nearT * 0.70f + fft[fi] * 0.20f +
                          beat * nearT * 0.45f + bass * nearT * 0.40f).coerceIn(0f, 1f)

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
        }

        // ── Advance sparks, collect current-frame dots ────────────────────────
        val currentDots = ArrayList<SparkDot>()
        val liveTris = ArrayList<Tri>()
        for (tri in tris) {
            tri.z   -= dt
            tri.rot += tri.rvel
            if (tri.z < Z_NEAR) continue
            val (tcx, tcy) = path(tri.pt)
            val (tsx, tsy, tsc) = proj(tcx, tcy, tri.z, draw.W, draw.H)
            val nearT  = maxOf(0f, 1f - tri.z / Z_FAR)
            val tr     = maxOf(4f, TUBE_R * tsc * tri.sizeFrac * (1f + bass * 3.0f + beat * 1.5f))
            val h      = (tri.hue + nearT * 0.4f) % 1f
            val bright = (0.40f + nearT * 0.55f + bass * 0.30f).coerceAtMost(0.95f)
            val pts = FloatArray(6)
            for (v in 0 until 3) {
                pts[v * 2]     = tsx + cos(tri.rot + v * TAU / 3f) * tr
                pts[v * 2 + 1] = tsy + sin(tri.rot + v * TAU / 3f) * tr
            }
            // Store for trail buffer (centre + radius approximation)
            currentDots.add(SparkDot(tsx, tsy, tr, h, bright))
            liveTris.add(tri)
        }
        tris.clear(); tris.addAll(if (liveTris.size > 60) liveTris.takeLast(60) else liveTris)

        // Store current dots in trail ring buffer
        sparkTrail[sparkTrailHead].clear()
        sparkTrail[sparkTrailHead].addAll(currentDots)
        sparkTrailHead = (sparkTrailHead + 1) % SPARK_TRAIL_LEN

        // ── Draw spark trail + current sparks with additive blend ─────────────
        draw.setAdditiveBlend()

        // Older trail frames — very low alpha (persistent glow)
        for (age in SPARK_TRAIL_LEN - 1 downTo 1) {
            val frameIdx = (sparkTrailHead - 1 - age + SPARK_TRAIL_LEN * 2) % SPARK_TRAIL_LEN
            val alphaFade = (1f - age.toFloat() / SPARK_TRAIL_LEN) * 0.12f
            for (dot in sparkTrail[frameIdx]) {
                val (gr, gg, gb) = hsl3(dot.h, l = dot.bright * 0.25f)
                draw.circle(dot.x, dot.y, dot.r + 4f, gr, gg, gb, alphaFade, filled = false, segments = 12)
            }
        }

        // Current frame — full brightness sparks (triangles)
        for (tri in liveTris) {
            val (tcx, tcy) = path(tri.pt)
            val (tsx, tsy, tsc) = proj(tcx, tcy, tri.z, draw.W, draw.H)
            val nearT  = maxOf(0f, 1f - tri.z / Z_FAR)
            val tr     = maxOf(4f, TUBE_R * tsc * tri.sizeFrac * (1f + bass * 3.0f + beat * 1.5f))
            val h      = (tri.hue + nearT * 0.4f) % 1f
            val bright = (0.40f + nearT * 0.55f + bass * 0.30f).coerceAtMost(0.95f)
            val pts = FloatArray(6)
            for (v in 0 until 3) {
                pts[v * 2]     = tsx + cos(tri.rot + v * TAU / 3f) * tr
                pts[v * 2 + 1] = tsy + sin(tri.rot + v * TAU / 3f) * tr
            }
            val (gr, gg, gb) = hsl3(h, l = bright * 0.30f)
            draw.polygon(pts, gr, gg, gb, 0.65f, filled = false)
            val (cr, cg, cb) = hsl3(h, l = bright)
            draw.polygon(pts, cr, cg, cb, 1f, filled = false)
        }

        draw.setNormalBlend()
    }

    private fun hsl3(h: Float, s: Float = 1f, l: Float = 0.5f): Triple<Float, Float, Float> {
        val c = GLDraw.hsl(h, s, l); return Triple(c[0], c[1], c[2])
    }
}
