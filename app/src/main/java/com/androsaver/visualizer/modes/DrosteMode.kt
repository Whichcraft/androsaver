package com.androsaver.visualizer.modes

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw
import kotlin.math.*

/**
 * Droste — infinite self-similar recursive zoom portal.
 *
 * Spiralling geometric shapes are drawn each frame with a long-persistence
 * trail fade creating the illusion of recursive inward zoom.  The
 * zoom-rotate framebuffer feedback of the pygame original
 * (pygame.transform.rotozoom) is approximated with a slow fadeBlack combined
 * with increasing shape density and size modulation on beat.
 *
 *   Bass   → shape brightness + size pulse
 *   Mid    → rotation speed + shape complexity
 *   Treble → overlay brightness
 *   Beat   → speed/size spike
 *
 * Port of psysuals `effects/droste.py` (v3.8.0).
 * The rotozoom framebuffer loop is not ported (no FBO blit available per-mode);
 * replaced with fadeBlack(4f/255f) + accelerated rotation on beat.
 */
class DrosteMode : BaseMode() {

    override val name = "Droste"

    private companion object {
        val TAU = (2.0 * PI).toFloat()
    }

    private var hue     = 0f
    private var rotAcc  = 0f
    private var beatT   = 0

    override fun reset() {
        hue = Math.random().toFloat(); rotAcc = 0f; beatT = 0
    }

    override fun draw(draw: GLDraw, audio: AudioData, tick: Int) {
        val W = draw.W.toFloat(); val H = draw.H.toFloat()
        val bass = audio.beat
        val mid  = audio.mid
        val high = audio.treble

        hue = (hue + 0.003f + mid * 0.004f) % 1f

        if (bass > 0.75f) beatT = 40
        beatT = maxOf(0, beatT - 1)
        val bt = beatT / 40f

        val rot = 0.20f + mid * 0.60f + bt * 2.0f
        rotAcc += rot * 0.016f

        draw.fadeBlack(4f / 255f)

        val cx = W / 2f; val cy = H / 2f
        val nShapes = 3 + (mid * 3).toInt() + (high * 2).toInt()

        for (i in 0 until nShapes) {
            val rBase  = minOf(W, H) * (0.05f + 0.11f * (i + 1))
            val ang    = rotAcc * (1f + i * 0.38f) + i.toFloat() * TAU / nShapes
            val px     = cx + cos(ang) * rBase * 0.45f
            val py     = cy + sin(ang) * rBase * 0.45f
            val h      = (hue + i.toFloat() / nShapes * 0.6f) % 1f
            val bright = 0.30f + bass * 0.28f + bt * 0.20f
            val size   = maxOf(2f, rBase * 0.15f * (1f + bass * 0.5f))
            val sides  = 3 + i

            val pts = FloatArray(sides * 2)
            for (s in 0 until sides) {
                val a = ang + s.toFloat() / sides * TAU
                pts[s * 2]     = px + cos(a) * size
                pts[s * 2 + 1] = py + sin(a) * size
            }
            if (sides >= 3) {
                val cFill = GLDraw.hsl(h, l = bright)
                val cEdge = GLDraw.hsl(h, l = bright * 0.35f)
                draw.polygon(pts, cFill[0], cFill[1], cFill[2], cFill[3], filled = true)
                draw.polygon(pts, cEdge[0], cEdge[1], cEdge[2], cEdge[3], filled = false)
            }
        }
    }
}
