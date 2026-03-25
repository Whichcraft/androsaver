package com.androsaver.visualizer.modes

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw
import kotlin.math.*

/**
 * 3-D Lissajous knot with trefoil symmetry and neon trail glow.
 * Beat explodes the scale, hue jumps on each kick.
 */
class LissajousMode : BaseMode() {

    override val name = "Lissajous"

    private companion object {
        const val TRAIL = 1400
        const val N_SYM = 3
        const val TAU   = (Math.PI * 2).toFloat()
    }

    private val hist = ArrayDeque<Triple<Float, Float, Float>>(TRAIL)  // (x,y,z) on knot
    private var hue  = 0f
    private var t    = 0f
    private var rx   = 0f; private var ry   = 0f
    private var rvx  = 0.006f; private var rvy = 0.009f
    private var dx   = 0f; private var dz   = PI.toFloat() / 4f
    private val dy   = PI.toFloat() / 2f
    private var scale = 1f; private var svel = 0f

    override fun reset() {
        hist.clear()
        hue = 0f; t = 0f; rx = 0f; ry = 0f
        rvx = 0.006f; rvy = 0.009f
        dx = 0f; dz = PI.toFloat() / 4f
        scale = 1f; svel = 0f
    }

    /** Rotate point (x,y,z) around X then Y axes. */
    private fun rot(x: Float, y: Float, z: Float): Triple<Float, Float, Float> {
        val cx = cos(rx); val sx = sin(rx)
        val cy = cos(ry); val sy = sin(ry)
        val y2 =  y * cx - z * sx
        val z2 =  y * sx + z * cx
        val x3 =  x * cy + z2 * sy
        val z3 = -x * sy + z2 * cy
        return Triple(x3, y2, z3)
    }

    /** Orthographic-ish projection: returns (px, py) relative to screen centre. */
    private fun proj(x: Float, y: Float, z: Float, W: Int, H: Int): Pair<Float, Float> {
        val fov  = minOf(W, H) * 0.40f
        val zcam = maxOf(z + 2.8f, 0.05f)
        return Pair(x * fov / zcam, y * fov / zcam)
    }

    override fun draw(draw: GLDraw, audio: AudioData, tick: Int) {
        draw.fadeBlack(0.08f)

        val fft  = audio.fft
        val beat = audio.beat
        hue += 0.006f

        val bass = fft.meanSlice(0, 6)
        val mid  = fft.meanSlice(6, 30)
        val high = fft.meanSlice(30, fft.size)

        val ax = 3.0f + bass * 1.3f
        val ay = 2.0f + mid  * 1.3f
        val az = 5.0f + high * 1.3f

        dx += 0.001f  + bass * 0.003f
        dz += 0.0008f + high * 0.002f
        t  += 0.022f  + beat * 0.06f

        if (hist.size >= TRAIL) hist.removeFirst()
        hist.addLast(Triple(sin(ax * t + dx), sin(ay * t + dy), sin(az * t + dz)))

        // Spring scale burst
        svel  += beat * 0.55f
        svel  += (1f - scale) * 0.26f
        svel  *= 0.60f
        scale += svel
        scale  = maxOf(0.35f, scale)

        hue += beat * 0.12f

        // Rotation inertia
        rvx += beat * 0.016f + 0.0002f; rvx *= 0.97f; rx += rvx
        rvy += beat * 0.019f + 0.0003f; rvy *= 0.97f; ry += rvy

        val s = scale
        val raw = Array(hist.size) { i ->
            val (px, py, pz) = hist[i]
            val (rx3, ry3, rz3) = rot(px * s, py * s, pz * s)
            proj(rx3, ry3, rz3, draw.W, draw.H)
        }

        val cx = draw.W / 2f; val cy = draw.H / 2f
        val n = raw.size
        if (n < 2) return

        val l1Bright = minOf(0.90f + beat * 0.08f, 0.98f)

        // Two glow passes: (lw_factor, l_tail, l_head)
        val passes = arrayOf(Pair(0.08f, 0.22f), Pair(0.50f, l1Bright))

        for (sym in 0 until N_SYM) {
            val ang = sym.toFloat() / N_SYM * TAU
            val ca = cos(ang); val sa = sin(ang)

            val pts    = FloatArray(n * 2)
            val colors = FloatArray(n * 4)
            for (j in 0 until n) {
                val (px, py) = raw[j]
                pts[j * 2]     = cx + px * ca - py * sa
                pts[j * 2 + 1] = cy + px * sa + py * ca
            }

            for ((lTail, lHead) in passes) {
                for (j in 0 until n) {
                    val tFrac = j.toFloat() / n
                    val h     = (hue + sym.toFloat() / N_SYM * 0.33f + tFrac * 0.55f) % 1f
                    val l     = lTail + tFrac * (lHead - lTail)
                    val c     = GLDraw.hsl(h, l = l)
                    colors[j * 4]     = c[0]
                    colors[j * 4 + 1] = c[1]
                    colors[j * 4 + 2] = c[2]
                    colors[j * 4 + 3] = if (lTail < 0.15f) 0.30f else 1f
                }
                draw.colorLineStrip(pts, colors)
            }
        }

        // Head dot on current knot position
        val (hpx, hpy) = raw.last()
        for (sym in 0 until N_SYM) {
            val ang = sym.toFloat() / N_SYM * TAU
            val ca = cos(ang); val sa = sin(ang)
            val sx = cx + hpx * ca - hpy * sa
            val sy_ = cy + hpx * sa + hpy * ca
            val r = maxOf(3f, 7f + beat * 22f)
            val c = GLDraw.hsl((hue + sym * 0.33f) % 1f, l = 0.88f)
            draw.circle(sx, sy_, r, c[0], c[1], c[2], 1f, segments = 16)
            draw.circle(sx, sy_, r / 3f, 1f, 1f, 1f, 1f, segments = 12)
            if (beat > 0.5f) {
                val hc = GLDraw.hsl((hue + sym * 0.33f + 0.5f) % 1f, l = 0.45f + beat * 0.25f)
                draw.circle(sx, sy_, r * 1.8f, hc[0], hc[1], hc[2], 0.5f, filled = false, segments = 16)
            }
        }
    }
}
