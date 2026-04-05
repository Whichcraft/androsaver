package com.androsaver.visualizer.modes

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw
import kotlin.math.*

/**
 * Vortex — firework rockets launch from the bottom, arc under gravity, and
 * explode into 80-120 glowing embers at the apex.  Beat fires extra rockets.
 *
 * Port of psysuals `Vortex` class (v2.3.0).
 * The pygame pixel-feedback zoom-rotate wormhole is omitted (requires FBO);
 * replaced with a very slow fadeBlack (≈ 40-frame persistence) so ember trails
 * linger similarly long on a dark background.
 */
class VortexMode : BaseMode() {

    override val name = "Vortex"

    private companion object {
        const val GRAV            = 0.13f
        const val DRAG            = 0.991f
        const val LAUNCH_INTERVAL = 85
    }

    private data class Rocket(
        var x: Float, var y: Float,
        var vx: Float, var vy: Float,
        val hue: Float,
        val trail: ArrayDeque<Pair<Float, Float>> = ArrayDeque(15)
    )

    private data class Ember(
        var x: Float, var y: Float,
        var vx: Float, var vy: Float,
        val hue: Float, val radius: Float,
        var life: Int, val maxLife: Int
    )

    private val rockets = ArrayList<Rocket>(12)
    private val embers  = ArrayList<Ember>(200)
    private var hue     = 0f
    private var autoT   = 0

    override fun reset() {
        hue = 0f; autoT = (Math.random() * 40).toInt()
        rockets.clear(); embers.clear()
    }

    private fun launch(W: Float, H: Float) {
        val x  = W * (0.10f + Math.random().toFloat() * 0.80f)
        val vy = -15f + Math.random().toFloat() * 5f    // -15 to -10
        val vx = (Math.random().toFloat() * 4f - 2f)
        val h  = (hue + Math.random().toFloat() * 0.5f - 0.25f + 1f) % 1f
        rockets.add(Rocket(x, H, vx, vy, h))
    }

    private fun explode(x: Float, y: Float, rocketHue: Float) {
        val n = 80 + (Math.random() * 40).toInt()
        repeat(n) {
            val ang  = Math.random().toFloat() * (2f * PI.toFloat())
            val spd  = (Math.random() * 3.6f + Math.random() * 5.4f).toFloat()  // gauss approx
            val life = 50 + (Math.random() * 50).toInt()
            val h    = (rocketHue + Math.random().toFloat() * 0.18f - 0.09f + 1f) % 1f
            val r    = (2 + (Math.random() * 3).toInt()).toFloat()
            embers.add(Ember(
                x, y,
                cos(ang) * spd, sin(ang) * spd * 0.85f - Math.random().toFloat() * 1.5f,
                h, r, life, life
            ))
        }
    }

    override fun draw(draw: GLDraw, audio: AudioData, tick: Int) {
        val W = draw.W.toFloat(); val H = draw.H.toFloat()
        val fft  = audio.fft
        val beat = audio.beat
        val bass = fft.meanSlice(0, 6)

        hue = (hue + 0.0015f + bass * 0.002f) % 1f

        // Beat: extra rockets
        if (beat > 0.6f) {
            val n = 1 + (beat * 2f).toInt()
            repeat(n) { launch(W, H) }
        }

        // Auto-launch
        autoT++
        if (autoT >= LAUNCH_INTERVAL) { autoT = 0; launch(W, H) }

        // Slow fade — embers should linger ~40 frames (matches psysuals BLEND_RGB_MULT 240/255)
        draw.fadeBlack(15f / 255f)

        // ── Draw rocket trails + advance ──────────────────────────────────────
        val liveRockets = ArrayList<Rocket>(rockets.size)
        for (rk in rockets) {
            rk.vy += GRAV
            rk.vx *= DRAG
            rk.x += rk.vx; rk.y += rk.vy
            rk.trail.addLast(rk.x to rk.y)
            if (rk.trail.size > 14) rk.trail.removeFirst()

            val tLen = rk.trail.size
            for ((ti, pt) in rk.trail.withIndex()) {
                val frac = (ti + 1).toFloat() / tLen
                val c = GLDraw.hsl(rk.hue, l = 0.30f + frac * 0.55f)
                val r = maxOf(1f, 3f * frac)
                draw.circle(pt.first, pt.second, r, c[0], c[1], c[2], frac, filled = true, segments = 6)
            }

            if (rk.vy >= 0f || rk.y < -20f) {
                explode(rk.x, rk.y, rk.hue)
            } else if (rk.x in -20f..(W + 20f)) {
                liveRockets.add(rk)
            }
        }
        rockets.clear(); rockets.addAll(liveRockets)

        // ── Draw embers ───────────────────────────────────────────────────────
        draw.setAdditiveBlend()
        val liveEmbers = ArrayList<Ember>(embers.size)
        for (em in embers) {
            em.vy += GRAV
            em.vx *= DRAG; em.vy *= DRAG
            em.x += em.vx; em.y += em.vy
            em.life--
            if (em.life > 0 && em.x in -60f..(W + 60f) && em.y < H + 60f) {
                val brightness = 0.32f + (em.life.toFloat() / em.maxLife) * 0.52f
                val c = GLDraw.hsl(em.hue, l = brightness)
                draw.circle(em.x, em.y, em.radius, c[0], c[1], c[2], 1f, filled = true, segments = 6)
                liveEmbers.add(em)
            }
        }
        embers.clear(); embers.addAll(liveEmbers)
        draw.setNormalBlend()
    }
}
