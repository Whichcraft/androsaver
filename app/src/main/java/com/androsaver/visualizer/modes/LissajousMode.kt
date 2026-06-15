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

    // Reusable flat buffer: [rawX0,rawY0, rawX1,rawY1, ...] — avoids per-frame allocation
    private var rawBuf = FloatArray(TRAIL * 2)

    override fun draw(draw: GLDraw, audio: AudioData, tick: Int) {
        draw.fadeBlack(0.18f)

        val fft  = audio.fft
        val beat = audio.beat
        hue += 0.006f

        val bass = fft.meanSlice(0, 6)
        val mid  = fft.meanSlice(6, 30)
        val high = fft.meanSlice(30, fft.size)

        val ax = 3.0f + bass * 0.05f
        val ay = 2.0f + mid  * 0.05f
        val az = 5.0f + high * 0.05f

        dx += 0.0003f + bass * 0.0001f
        dz += 0.0002f + high * 0.0001f
        t  += 0.010f  + beat * 0.008f

        if (hist.size >= TRAIL) hist.removeFirst()
        hist.addLast(Triple(sin(ax * t + dx), sin(ay * t + dy), sin(az * t + dz)))

        // Spring scale burst
        svel  += beat * 0.08f
        svel  += (1f - scale) * 0.26f
        svel  *= 0.60f
        scale += svel
        scale  = maxOf(0.35f, scale)

        hue += beat * 0.006f

        // Rotation inertia
        rvx += beat * 0.003f + 0.00005f; rvx *= 0.97f; rx += rvx
        rvy += beat * 0.004f + 0.00007f; rvy *= 0.97f; ry += rvy

        val n = hist.size
        if (n < 2) return

        // Precompute rotation elements once (avoids 1400 Triple+Pair allocations per frame)
        val s    = scale
        val cxR  = cos(rx);  val sxR = sin(rx)
        val cyR  = cos(ry);  val syR = sin(ry)
        val fovL = minOf(draw.W, draw.H) * 0.52f
        if (rawBuf.size < n * 2) rawBuf = FloatArray(n * 2)
        val raw = rawBuf
        for (i in 0 until n) {
            val (px, py, pz) = hist[i]
            val x   = px * s;  val y  = py * s;  val z  = pz * s
            val y2  = y * cxR  - z  * sxR
            val z2  = y * sxR  + z  * cxR
            val x3  = x * cyR  + z2 * syR
            val z3  = -x * syR + z2 * cyR
            val zcam = maxOf(z3 + 2.8f, 0.05f)
            raw[i * 2]     = x3 * fovL / zcam
            raw[i * 2 + 1] = y2 * fovL / zcam
        }

        val cx = draw.W / 2f; val cy = draw.H / 2f

        // Treble brightens the glow: hi-hat energy makes the knot shimmer whiter
        val l1Bright = minOf(0.90f + beat * 0.08f + high * 0.14f, 0.98f)

        // Two glow passes: (lw_factor, l_tail, l_head)
        val passes = arrayOf(Pair(0.08f, 0.22f), Pair(0.50f, l1Bright))

        for (sym in 0 until N_SYM) {
            val ang = sym.toFloat() / N_SYM * TAU
            val ca = cos(ang); val sa = sin(ang)

            val pts    = FloatArray(n * 2)
            val colors = FloatArray(n * 4)
            for (j in 0 until n) {
                val px = raw[j * 2]; val py = raw[j * 2 + 1]
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

            // Head dot at the tip of each arm
            val hpx = pts[(n - 1) * 2]; val hpy = pts[(n - 1) * 2 + 1]
            val r   = maxOf(3f, 7f + beat * 3.66f)
            val c   = GLDraw.hsl((hue + sym.toFloat() / N_SYM * 0.33f) % 1f, l = 0.88f)
            draw.circle(hpx, hpy, r,       c[0], c[1], c[2], 1f, segments = 16)
            draw.circle(hpx, hpy, r / 3f,  1f,   1f,   1f,   1f, segments = 12)
            if (beat > 0.5f) {
                val hc = GLDraw.hsl((hue + sym.toFloat() / N_SYM * 0.33f + 0.5f) % 1f, l = 0.45f + beat * 0.25f)
                draw.circle(hpx, hpy, r * 1.8f, hc[0], hc[1], hc[2], 0.5f, filled = false, segments = 16)
            }
        }

    }
}
