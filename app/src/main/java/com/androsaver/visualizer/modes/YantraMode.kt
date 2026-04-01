package com.androsaver.visualizer.modes

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw
import kotlin.math.*

/**
 * Psychedelic sacred-geometry mandala.
 * Six concentric polygon rings, alternating rotation, web lines, neon spokes, central pulse.
 */
class YantraMode : BaseMode() {

    override val name = "Yantra"

    private companion object {
        const val N_RINGS  = 7
        const val N_SPOKES = 24
        const val TAU      = (Math.PI * 2).toFloat()
    }

    private val signs  = intArrayOf(1, -1, 1, -1, 1, -1, 1)
    private val rot    = FloatArray(N_RINGS)
    private val rvel   = FloatArray(N_RINGS)
    private val poff   = FloatArray(N_RINGS)
    private val pvel   = FloatArray(N_RINGS)
    private var hue    = 0f
    private var time   = 0f

    override fun reset() {
        hue = 0f; time = 0f
        for (i in 0 until N_RINGS) {
            rot[i] = 0f
            rvel[i] = signs[i] * (0.010f + i * 0.003f)
            poff[i] = 0f; pvel[i] = 0f
        }
    }

    private fun ringVerts(ring: Int, r: Float, cx: Float, cy: Float): FloatArray {
        val n = 3 + ring
        val angle = rot[ring]
        val pts = FloatArray(n * 2)
        for (v in 0 until n) {
            pts[v * 2]     = cx + cos(v.toFloat() / n * TAU + angle) * r
            pts[v * 2 + 1] = cy + sin(v.toFloat() / n * TAU + angle) * r
        }
        return pts
    }

    override fun draw(draw: GLDraw, audio: AudioData, tick: Int) {
        val fft  = audio.fft
        val beat = audio.beat
        hue += 0.005f; time += 0.02f + beat * 0.078f
        val cx = draw.W / 2f; val cy = draw.H / 2f
        val maxR  = minOf(draw.W, draw.H) * 0.46f
        val safeR = minOf(cx, cy) - 6f   // hard ceiling — nothing draws outside the screen

        val bass = fft.meanSlice(0, 6)
        val mid  = fft.meanSlice(6, 30)
        val high = fft.meanSlice(30, fft.size)

        // Trail length driven by energy — loud/fast = long trails, quiet = short
        val energy = (bass + mid + high).coerceIn(0f, 1.5f) / 1.5f
        draw.fadeBlack((0.17f - energy * 0.13f).coerceAtLeast(0.04f))

        val bands = floatArrayOf(
            bass, (bass + mid) * 0.5f, mid,
            (mid + high) * 0.5f, high, (bass + high) * 0.5f,
            (mid + high) * 0.5f
        )

        // Physics update — near-static without audio, driven hard by energy and beat
        for (i in 0 until N_RINGS) {
            val e = minOf(bands[i], 1f)
            pvel[i] += beat * (1.56f + e * 0.585f)
            pvel[i] += -poff[i] * 0.35f
            pvel[i] *= 0.72f
            poff[i] += pvel[i]
            rot[i]  += rvel[i] + signs[i].toFloat() * (e * 0.022f + beat * 0.035f)
        }

        // Collect ring vertices
        val allVerts = Array(N_RINGS) { i ->
            val baseR = maxR * (0.28f + i.toFloat() / (N_RINGS - 1) * 0.62f)
            val r = (baseR * (1f + poff[i] * 0.38f)).coerceAtMost(safeR)
            ringVerts(i, r, cx, cy)
        }

        // Web lines between adjacent rings
        for (i in 0 until N_RINGS - 1) {
            val vOut = allVerts[i + 1]; val nOut = vOut.size / 2
            val vIn  = allVerts[i];    val nIn  = vIn.size / 2
            val e = minOf(bands[i], 1f)
            val h = (hue + i.toFloat() / N_RINGS * 0.5f) % 1f
            val c = GLDraw.hsl(h, l = 0.18f + e * 0.28f)
            for (k in 0 until nOut) {
                val nearest = kotlin.math.round(k.toFloat() / nOut * nIn).toInt() % nIn
                draw.line(vOut[k * 2], vOut[k * 2 + 1],
                          vIn[nearest * 2], vIn[nearest * 2 + 1],
                          c[0], c[1], c[2], 0.6f)
            }
        }

        // Polygon rings outer → inner
        for (i in N_RINGS - 1 downTo 0) {
            val e = minOf(bands[i], 1f)
            val h = (hue + i.toFloat() / N_RINGS * 0.55f) % 1f
            val bright = 0.52f + e * 0.25f + beat * 0.50f
            val c = GLDraw.hsl(h, l = bright)
            draw.polygon(allVerts[i], c[0], c[1], c[2], 1f, filled = false)
            // Star connections (every 2nd vertex)
            val n = allVerts[i].size / 2
            if (n >= 5) {
                val sc = GLDraw.hsl(h, l = bright * 0.6f)
                for (k in 0 until n) {
                    val j = (k + 2) % n
                    draw.line(allVerts[i][k * 2], allVerts[i][k * 2 + 1],
                              allVerts[i][j * 2], allVerts[i][j * 2 + 1],
                              sc[0], sc[1], sc[2], 0.7f)
                }
            }
        }

        // Radial spokes
        val outerR = (maxR * (1.02f + beat * 1.079f)).coerceAtMost(safeR)
        for (s in 0 until N_SPOKES) {
            val a = s.toFloat() / N_SPOKES * TAU + time * 0.22f +
                    sin(time * 2.4f + s * 0.85f) * (0.05f + mid * 0.10f)
            val x2 = cx + cos(a) * outerR
            val y2 = cy + sin(a) * outerR
            val h = (hue + s.toFloat() / N_SPOKES * 0.35f + high * 0.2f) % 1f
            val lSpoke = 0.18f + beat * 0.975f + high * 0.18f
            if (beat > 0.05f) {
                val c = GLDraw.hsl(h, l = lSpoke)
                draw.line(cx, cy, x2, y2, c[0], c[1], c[2], minOf(1f, beat * 1.755f))
            }
        }

        // Central pulse — tiny accent dot, not dominant
        val cr = maxOf(1f, 3f + bass * 2f + beat * 2.6f)
        val cc = GLDraw.hsl(hue, l = 0.40f + beat * 0.078f)
        draw.circle(cx, cy, cr, cc[0], cc[1], cc[2], 0.7f, segments = 16)
        draw.circle(cx, cy, maxOf(1f, cr / 3f), 1f, 1f, 1f, 0.6f, segments = 10)
    }
}


