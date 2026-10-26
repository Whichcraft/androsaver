package com.androsaver.visualizer.modes

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw
import kotlin.math.*

/** psysuals 3.20 projected strip with slow smoothed geometry, separate light shiver and whole-strip viewport fitting. */
class MobiusMode : BaseMode() {

    override val name = "Mobius"

    private companion object {
        const val N_LAT  = 60
        const val N_U    = 120
        val TAU = (2.0 * PI).toFloat()
    }

    private var ry      = 0f
    private var rx      = 0f
    private var rw      = 0f
    private var hue     = 0.55f
    private var shiver  = 0f
    private var beatPrev = 0f

    // Pre-computed u values [0 .. 2π] with N_U+1 points
    private val uArr   = FloatArray(N_U + 1) { it / N_U.toFloat() * TAU }
    private val vArr   = FloatArray(N_LAT)  { (it / (N_LAT - 1f)) - 0.5f }   // -0.5 .. 0.5

    // Scratch arrays for projected screen points (2 floats per point)
    private var fit=1f
    private val projected=Array(N_LAT){FloatArray((N_U+1)*2)}
    private val pts2d  = FloatArray((N_U + 1) * 2)

    override fun reset() {
        fit=1f; ry = 0f; rx = 0f; rw = 0f; hue = 0.55f; shiver = 0f; beatPrev = 0f
    }

    /** Perspective-project a world point (wx, wy, wz) → screen (px, py). */
    private fun project(wx: Float, wy: Float, wz: Float, ww: Float, W: Float, H: Float,
                        cosy: Float, siny: Float, cosx: Float, sinx: Float): FloatArray {
        val cw = cos(rw); val sw = sin(rw)
        val xr4 = wx * cw - ww * sw
        val wr4 = wx * sw + ww * cw
        val depth4 = maxOf(0.35f, 3.35f - wr4)
        val xr =  (xr4 / depth4) * cosy + wz * siny
        val zr = -(xr4 / depth4) * siny + wz * cosy
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
            val p = project(x, y, z, v * sin(hu) * 0.35f, W, H, cosy, siny, cosx, sinx)
            pts2d[i * 2]     = p[0]
            pts2d[i * 2 + 1] = p[1]
        }
    }

    override fun draw(draw: GLDraw, audio: AudioData, tick: Int) {
        val W = draw.W.toFloat(); val H = draw.H.toFloat()
        val response=prepare(draw,audio);val step=response.step
        val bass=response.motion[0];val mid=response.motion[1];val high=response.motion[2]
        hue=(hue+(.0012f+mid*.002f)*step)%1f
        ry+=(.008f+bass*.006f+mid*.003f+high*.003f)*step*response.motionScale
        rx+=(.003f+bass*.002f)*step*response.motionScale
        rw+=(.004f+mid*.002f+high*.003f)*step*response.motionScale
        shiver=response.light[0]*response.accent*.4f
        val twist=1f+bass*.06f+mid*.06f
        draw.fadeBlackTimed(42f/255f,step)

        val cosy = cos(ry); val siny = sin(ry)
        val cosx = cos(rx); val sinx = sin(rx)

        var ex=1f;var ey=1f
        for(li in 0 until N_LAT) {
            fillLatLine(vArr[li],twist,W,H,cosy,siny,cosx,sinx)
            pts2d.copyInto(projected[li])
            for(i in 0..N_U){ex=maxOf(ex,abs(pts2d[i*2]-W*.5f));ey=maxOf(ey,abs(pts2d[i*2+1]-H*.5f))}
        }
        fit=minOf(fit+.001f*step,1f,W*.46f/ex,H*.46f/ey)
        // Latitude lines
        for (li in 0 until N_LAT) {
            val v = vArr[li]
            for(i in 0..N_U){pts2d[i*2]=W*.5f+(projected[li][i*2]-W*.5f)*fit;pts2d[i*2+1]=H*.5f+(projected[li][i*2+1]-H*.5f)*fit}
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
