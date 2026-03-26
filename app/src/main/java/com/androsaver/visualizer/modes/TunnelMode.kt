package com.androsaver.visualizer.modes

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw
import kotlin.math.*

/** First-person ride through a neon curving tube with beat-spawned inner circles. */
class TunnelMode : BaseMode() {

    override val name = "Tunnel"

    private companion object {
        const val N_RINGS    = 30
        const val N_SIDES    = 20
        const val TUBE_R_MIN = 1.3f
        const val TUBE_R_MAX = 3.0f
        const val Z_FAR      = 10.0f
        const val Z_NEAR     = 0.18f
        const val DT         = 0.018f  // constant speed — smooth ride always
    }

    private data class Ring(var z: Float, var pt: Float)
    private data class BeatCircle(
        var z: Float, var pt: Float,
        var hue: Float, var radiusFrac: Float
    )

    private val rings       = ArrayList<Ring>(N_RINGS)
    private val beatCircles = ArrayList<BeatCircle>(60)
    private var hue      = 0f
    private var time     = 0f
    private var prevBeat = 0f

    override fun reset() {
        hue = 0f; time = 0f; prevBeat = 0f
        rings.clear(); beatCircles.clear()
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
        time += DT

        val tubeR = TUBE_R_MIN + (TUBE_R_MAX - TUBE_R_MIN) * (beat + bass * 0.5f).coerceIn(0f, 1f)

        // Spawn beat circles on rising beat edge
        if (beat > 0.45f && prevBeat <= 0.45f) {
            beatCircles.add(BeatCircle(
                z          = Z_FAR,
                pt         = time + Z_FAR,
                hue        = (hue + 0.25f + Math.random().toFloat() * 0.4f) % 1f,
                radiusFrac = 0.25f + Math.random().toFloat() * 0.30f
            ))
        }
        prevBeat = beat

        // Advance rings
        for (r in rings) {
            r.z -= DT
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
        }

        // Beat circles — spawned on beat, travel toward viewer independently
        val liveBeatCircles = ArrayList<BeatCircle>()
        for (bc in beatCircles) {
            bc.z -= DT
            if (bc.z < Z_NEAR) continue
            val (bcx, bcy) = path(bc.pt)
            val (bsx, bsy, bsc) = proj(bcx, bcy, bc.z, draw.W, draw.H)
            val nearT = maxOf(0f, 1f - bc.z / Z_FAR)
            val bsr = maxOf(2f, tubeR * bsc * bc.radiusFrac)
            val bh = (bc.hue + nearT * 0.3f) % 1f
            val bright = 0.40f + nearT * 0.55f
            val (br, bg, bb) = hsl3(bh, l = bright)
            draw.circle(bsx, bsy, bsr, br, bg, bb, 0.85f, filled = false, segments = N_SIDES)
            liveBeatCircles.add(bc)
        }
        beatCircles.clear(); beatCircles.addAll(liveBeatCircles.takeLast(60))
    }

    private fun hsl3(h: Float, s: Float = 1f, l: Float = 0.5f): Triple<Float, Float, Float> {
        val c = GLDraw.hsl(h, s, l); return Triple(c[0], c[1], c[2])
    }
}

private const val TAU = (Math.PI * 2).toFloat()
