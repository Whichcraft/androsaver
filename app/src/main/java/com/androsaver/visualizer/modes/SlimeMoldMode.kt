package com.androsaver.visualizer.modes

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw
import kotlin.math.*

/**
 * SlimeMold — Physarum-style multi-agent slime simulation.
 *
 * Agents deposit chemical trails, sense ahead-left / ahead / ahead-right,
 * and steer toward the strongest signal.  The result is a self-organising
 * vein network that pulses and reforms with the music.
 *
 *   Bass   → agent speed + trail deposit strength
 *   Mid    → sensor sensitivity (sharper gradient response)
 *   Treble → sensor angle (wider = more meandering)
 *   Beat   → teleport burst + trail strength spike
 *
 * Port of psysuals `effects/slimemold.py` (v3.9.0).
 * Agent count reduced from 10 000 to 2 500 for Android performance.
 * Trail grid runs at RES_DIV=8 (≈240×135 for 1080p), rendered as small rects.
 * NumPy vectorised ops replaced with scalar Kotlin loops over FloatArrays.
 */
class SlimeMoldMode : BaseMode() {

    override val name = "SlimeMold"

    private companion object {
        const val N       = 2_500
        const val RES_DIV = 8
        const val BASE_SA = 0.5236f   // 30 degrees
        const val BASE_SD = 7
        val TAU = (2.0 * PI).toFloat()
    }

    private val px    = FloatArray(N)
    private val py    = FloatArray(N)
    private val ang   = FloatArray(N)

    private var gridW = 0; private var gridH = 0
    private var trail = FloatArray(0)   // [gridW × gridH], indexed trail[ix + iy * gridW]
    private var hue   = 0.30f
    private var initialized = false

    override fun reset() {
        initialized = false; hue = 0.30f
        if (trail.isNotEmpty()) trail.fill(0f)
    }

    private fun init(W: Float, H: Float) {
        gridW = maxOf(1, (W / RES_DIV).toInt())
        gridH = maxOf(1, (H / RES_DIV).toInt())
        trail = FloatArray(gridW * gridH)
        val cx = gridW / 2f; val cy = gridH / 2f
        val rSpread = minOf(gridW, gridH) * 0.25f
        for (i in 0 until N) {
            px[i]  = (cx + (Math.random().toFloat() * 2f - 1f) * rSpread * 0.3f)
                     .coerceIn(0f, gridW - 1f)
            py[i]  = (cy + (Math.random().toFloat() * 2f - 1f) * rSpread * 0.3f)
                     .coerceIn(0f, gridH - 1f)
            ang[i] = Math.random().toFloat() * TAU
        }
        initialized = true
    }

    private fun sense(i: Int, offsetAng: Float, dist: Float): Float {
        val sx = ((px[i] + cos(ang[i] + offsetAng) * dist).toInt())
                 .coerceIn(0, gridW - 1)
        val sy = ((py[i] + sin(ang[i] + offsetAng) * dist).toInt())
                 .coerceIn(0, gridH - 1)
        return trail[sx + sy * gridW]
    }

    override fun draw(draw: GLDraw, audio: AudioData, tick: Int) {
        val W = draw.W.toFloat(); val H = draw.H.toFloat()
        val bass = audio.beat
        val mid  = audio.mid
        val high = audio.treble

        if (!initialized || (gridW != (W / RES_DIV).toInt())) init(W, H)

        hue = (hue + 0.002f + mid * 0.003f) % 1f

        val sa  = BASE_SA + high * 0.40f
        val sd  = BASE_SD + bass * 5f
        val spd = 0.7f + bass * 1.5f
        val rot = 0.18f + high * 0.15f
        val dep = 0.8f + bass * 0.6f

        // Beat: teleport 5% of agents toward center
        if (bass > 0.7f) {
            val nTp = N / 20
            val cx = gridW / 2f; val cy = gridH / 2f
            val rSpread = minOf(gridW, gridH) * 0.20f
            repeat(nTp) {
                val idx = (Math.random() * N).toInt().coerceIn(0, N - 1)
                px[idx]  = (cx + (Math.random().toFloat() * 2f - 1f) * rSpread).coerceIn(0f, gridW - 1f)
                py[idx]  = (cy + (Math.random().toFloat() * 2f - 1f) * rSpread).coerceIn(0f, gridH - 1f)
                ang[idx] = Math.random().toFloat() * TAU
            }
        }

        // Agent update
        for (i in 0 until N) {
            val fwd   = sense(i, 0f,  sd)
            val left  = sense(i, -sa, sd)
            val right = sense(i, +sa, sd)

            ang[i] += when {
                left  > fwd && left  >= right -> -rot
                right > fwd && right >  left  -> +rot
                Math.random() < 0.05          -> (Math.random().toFloat() * 2f - 1f) * rot
                else -> 0f
            }

            px[i] = ((px[i] + cos(ang[i]) * spd) % gridW + gridW) % gridW
            py[i] = ((py[i] + sin(ang[i]) * spd) % gridH + gridH) % gridH

            // Deposit trail
            val ix = px[i].toInt().coerceIn(0, gridW - 1)
            val iy = py[i].toInt().coerceIn(0, gridH - 1)
            trail[ix + iy * gridW] += dep
        }

        // Diffuse + decay
        val decay = 0.94f - bass * 0.01f
        val newTrail = FloatArray(trail.size)
        for (iy in 0 until gridH) {
            for (ix in 0 until gridW) {
                val ixm = maxOf(0, ix - 1); val ixp = minOf(gridW - 1, ix + 1)
                val iym = maxOf(0, iy - 1); val iyp = minOf(gridH - 1, iy + 1)
                val diffused = (trail[ixm + iy  * gridW] + trail[ixp + iy  * gridW] +
                                trail[ix  + iym * gridW] + trail[ix  + iyp * gridW] +
                                trail[ix  + iy  * gridW] * 4f) / 8f
                newTrail[ix + iy * gridW] = diffused * decay
            }
        }
        trail = newTrail

        // Render trail grid
        val cellW = W / gridW; val cellH = H / gridH
        for (iy in 0 until gridH) {
            for (ix in 0 until gridW) {
                val tNorm = (trail[ix + iy * gridW] / 5f).coerceIn(0f, 1f)
                if (tNorm < 0.02f) continue
                val h = ((hue + tNorm * 0.5f) % 1f + 1f) % 1f
                val c = GLDraw.hsl(h, s = 0.9f, l = tNorm * 0.5f)
                draw.rect(ix * cellW, iy * cellH, cellW + 1f, cellH + 1f, c[0], c[1], c[2], 1f)
            }
        }
    }
}
