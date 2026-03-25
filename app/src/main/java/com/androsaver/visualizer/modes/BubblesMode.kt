package com.androsaver.visualizer.modes

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw
import kotlin.math.*

/** Translucent neon bubbles rising with physics; beat spawns more and makes them swell. */
class BubblesMode : BaseMode() {

    override val name = "Bubbles"

    private companion object {
        const val MAX = 700
        const val TAU = (Math.PI * 2).toFloat()
    }

    private data class Bubble(
        var x: Float, var y: Float, var r: Float,
        var vx: Float, var vy: Float,
        var hue: Float, var wobble: Float, var phase: Float
    )

    private val pool  = ArrayList<Bubble>(MAX)
    private val alive = ArrayList<Bubble>(MAX)   // reused each frame to avoid allocation
    private var hue   = 0f
    private var pulse = 0f
    private var pvel  = 0f

    private fun makeBubble(W: Int, H: Int, y: Float? = null): Bubble {
        val r = 8f + Math.random().toFloat() * 37f
        return Bubble(
            x       = W * 0.02f + Math.random().toFloat() * W * 0.96f,
            y       = y ?: (H + r),
            r       = r,
            vx      = (Math.random().toFloat() - 0.5f) * 0.8f,
            vy      = -(1.0f + Math.random().toFloat() * 2.2f),
            hue     = Math.random().toFloat(),
            wobble  = 0.025f + Math.random().toFloat() * 0.045f,
            phase   = Math.random().toFloat() * TAU
        )
    }

    override fun reset() {
        pool.clear(); hue = 0f; pulse = 0f; pvel = 0f
    }

    private fun init(W: Int, H: Int) {
        if (pool.isEmpty()) {
            repeat(400) { pool.add(makeBubble(W, H, Math.random().toFloat() * H)) }
        }
    }

    override fun draw(draw: GLDraw, audio: AudioData, tick: Int) {
        val W = draw.W; val H = draw.H
        init(W, H)

        val fft  = audio.fft
        val beat = audio.beat
        hue += 0.005f
        val bass = fft.meanSlice(0, 8)
        val mid  = fft.meanSlice(6, 30)

        // Global beat spring
        pvel  += beat * 0.75f
        pvel  += -pulse * 0.35f
        pvel  *= 0.52f
        pulse += pvel

        // Spawn on beat/bass
        val spawnCount = (2 + beat * 12 + bass * 6).toInt()
        repeat(spawnCount) {
            if (pool.size < MAX) {
                val b = makeBubble(W, H)
                b.vy  *= (1f + beat * 0.8f)
                b.r   *= (1f + bass * 1.2f)
                b.hue  = (hue + Math.random().toFloat() * 0.35f) % 1f
                pool.add(b)
            }
        }

        alive.clear()
        for (b in pool) {
            b.x  += b.vx + sin(tick * b.wobble + b.phase) * 0.9f
            b.y  += b.vy
            b.hue = (b.hue + 0.004f) % 1f
            if (b.y + b.r < 0) continue

            val life  = (b.y / H).coerceIn(0f, 1f)
            val r     = maxOf(2f, b.r * (1f + pulse * 0.90f + mid * 0.15f))
            val alpha = life * 0.63f  // matches pygame's 160/255

            // Multi-layer halos (outermost first)
            val halos = arrayOf(
                Triple(1.55f, 0.40f, 0.18f),
                Triple(1.28f, 0.52f, 0.35f),
                Triple(1.10f, 0.65f, 0.60f)
            )
            for ((gExp, gL, gAlphaMul) in halos) {
                val gr = maxOf(1f, r * gExp)
                val gc = GLDraw.hsl(b.hue, l = gL)
                draw.circle(b.x, b.y, gr, gc[0], gc[1], gc[2], alpha * gAlphaMul,
                            filled = false, segments = 24)
            }

            // Neon rim
            val rc = GLDraw.hsl(b.hue, l = 0.78f)
            draw.circle(b.x, b.y, r, rc[0], rc[1], rc[2], alpha, filled = false, segments = 24)

            // Translucent core
            val cc = GLDraw.hsl((b.hue + 0.5f) % 1f, l = 0.55f)
            draw.circle(b.x, b.y, maxOf(1f, r - 2f), cc[0], cc[1], cc[2], alpha * 0.22f,
                        filled = true, segments = 24)

            // Specular highlight
            val hr = maxOf(1f, r / 3f)
            draw.circle(b.x - r / 3f, b.y - r / 3f, hr, 1f, 1f, 1f, alpha * 0.55f,
                        filled = true, segments = 12)

            // Beat flash
            if (beat > 0.5f) {
                val fr = maxOf(1f, r * 1.4f)
                val fc = GLDraw.hsl((b.hue + 0.25f) % 1f, l = 0.88f)
                draw.circle(b.x, b.y, fr, fc[0], fc[1], fc[2], alpha * beat * 0.4f,
                            filled = false, segments = 20)
            }

            alive.add(b)
        }
        pool.clear()
        pool.addAll(if (alive.size > MAX) alive.takeLast(MAX) else alive)
    }
}
