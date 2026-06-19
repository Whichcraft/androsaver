package com.androsaver.visualizer.modes

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw
import kotlin.math.*

/**
 * Möbius — a 3-D Möbius strip rendered with perspective projection.
 *
 * Latitude lines (constant v, varying u) form a wireframe.
 * Rotates slowly in 3-D; beat fires a "shiver" that briefly widens the twist.
 *
 *   Bass   → rotation speed
 *   Mid    → roll (tilt) speed
 *   Beat   → shiver: twist amplitude spike
 *
 * Port of psysuals `effects/mobius.py` (v3.9.0).
 * TRAIL_ALPHA=15 → fadeBlack(15f/255f).
 * 3-D rotation and perspective projection ported from numpy to Kotlin FloatArrays.
 */
class MobiusMode : BaseMode() {

    override val name = "Mobius"

    private companion object {
        const val N_LAT  = 60
        const val N_U    = 120
        val TAU = (2.0 * PI).toFloat()
    }

    private var ry      = 0f
    private var rx      = 0f
    private var hue     = 0.55f
    private var uOff    = 0f
    private var shiver  = 0f
    private var beatPrev = 0f

    // Pre-computed u values [0 .. 2π] with N_U+1 points
    private val uArr   = FloatArray(N_U + 1) { it / N_U.toFloat() * TAU }
    private val vArr   = FloatArray(N_LAT)  { (it / (N_LAT - 1f)) - 0.5f }   // -0.5 .. 0.5

    // Scratch arrays for projected screen points (2 floats per point)
    private val pts2d  = FloatArray((N_U + 1) * 2)

    override fun reset() {
        ry = 0f; rx = 0f; hue = 0.55f; uOff = 0f; shiver = 0f; beatPrev = 0f
    }

    /** Perspective-project a world point (wx, wy, wz) → screen (px, py). */
    private fun project(wx: Float, wy: Float, wz: Float, W: Float, H: Float,
                        cosy: Float, siny: Float, cosx: Float, sinx: Float): FloatArray {
        val xr =  wx * cosy + wz * siny
        val zr = -wx * siny + wz * cosy
        val yr  =  wy * cosx - zr * sinx
        val zr2 =  wy * sinx + zr * cosx
        val z2  = maxOf(zr2, -3f + 0.2f)
        val sc  = 3f / (3f + z2)
        val half = minOf(W, H) * 0.42f
        return floatArrayOf(xr * sc * half + W / 2f, yr * sc * half + H / 2f)
    }

    /** Möbius 3-D point for fixed v, varying u array → fills pts2d. */
    private fun fillLatLine(v: Float, twist: Float, W: Float, H: Float,
                            cosy: Float, siny: Float, cosx: Float, sinx: Float) {
        for (i in uArr.indices) {
            val u = uArr[i]
            val hu = u / 2f
            val cohu = cos(hu) * twist
            val x = (1f + v * cohu) * cos(u)
            val y = (1f + v * cohu) * sin(u)
            val z = v * sin(hu) * twist
            val p = project(x, y, z, W, H, cosy, siny, cosx, sinx)
            pts2d[i * 2]     = p[0]
            pts2d[i * 2 + 1] = p[1]
        }
    }

    override fun draw(draw: GLDraw, audio: AudioData, tick: Int) {
        val W = draw.W.toFloat(); val H = draw.H.toFloat()
        val bass = audio.beat
        val mid  = audio.mid
        val high = audio.treble

        hue    = (hue  + 0.0012f + mid  * 0.002f) % 1f
        ry    += 0.008f + bass * 0.025f + mid * 0.012f
        rx    += 0.003f + bass * 0.008f
        uOff   = (uOff + 0.006f + high * 0.020f) % TAU

        if (bass > 0.75f && beatPrev <= 0.75f) shiver = 1f
        beatPrev = bass
        shiver   = maxOf(0f, shiver - 0.04f)

        val twist = 1f + shiver * 0.30f + mid * 0.15f

        draw.fadeBlack(15f / 255f)

        val cosy = cos(ry); val siny = sin(ry)
        val cosx = cos(rx); val sinx = sin(rx)

        // Latitude lines
        for (li in 0 until N_LAT) {
            val v = vArr[li]
            fillLatLine(v, twist, W, H, cosy, siny, cosx, sinx)
            val h      = (hue + (v + 0.5f)) % 1f
            val bright = 0.18f + abs(v) * 0.30f + bass * 0.20f + shiver * 0.25f
            val c = GLDraw.hsl(h, l = bright)
            for (i in 0 until N_U) {
                draw.line(pts2d[i * 2], pts2d[i * 2 + 1],
                          pts2d[(i + 1) * 2], pts2d[(i + 1) * 2 + 1],
                          c[0], c[1], c[2], c[3])
            }
        }
    }
}
