package com.androsaver.visualizer.modes

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw
import kotlin.math.*

/**
 * Mycelium — psychedelic hyphae colonies with swirling spores.
 *
 * Active tips grow outward leaving decaying filament segments behind.
 * Rewritten in v3.9.0 to support multiple colonies (cores) with a swirling
 * growth pattern for fuller screen coverage.
 *
 * Port of psysuals `effects/mycelium.py` (v3.11.0).
 */
class MyceliumMode : BaseMode() {

    override val name = "Mycelium"

    private companion object {
        const val MAX_SEGS = 900
        const val MAX_TIPS = 240
        const val CORE_COUNT = 5
        const val MAX_SPORES = 300
        val PI_F = Math.PI.toFloat()
        val TAU = (2.0 * PI).toFloat()
    }

    private data class Core(val x: Float, val y: Float, val hOff: Float)

    private data class Tip(
        val x: Float, val y: Float, val angle: Float,
        val depth: Int, val hueOff: Float, val coreIdx: Int
    )

    private data class Seg(
        val x1: Float, val y1: Float, val x2: Float, val y2: Float,
        var age: Int, val maxAge: Int, val depth: Int, val hueOff: Float
    )

    private val cores = ArrayList<Core>(CORE_COUNT)
    private val tips = ArrayList<Tip>(MAX_TIPS)
    private val segs = ArrayList<Seg>(MAX_SEGS)

    private data class Spore(
        var x: Float, var y: Float,
        var vx: Float, var vy: Float,
        val hueOff: Float,
        var life: Int, val maxLife: Int,
        val size: Float
    )

    private val spores = ArrayList<Spore>()

    private var hue = 0f
    private var phase = 0f
    private var pulse = 0f
    private var lastW = 0
    private var lastH = 0

    override fun reset() {
        cores.clear()
        tips.clear()
        segs.clear()
        spores.clear()
        hue = Math.random().toFloat()
        phase = 0f
        pulse = 0f
        lastW = 0
        lastH = 0
    }

    private fun buildCores(W: Float, H: Float) {
        cores.clear()
        val cx = W / 2f
        val cy = H / 2f
        val rad = minOf(W, H) * 0.22f
        val base = FloatArray(CORE_COUNT) { i -> i * (TAU / CORE_COUNT) }
        for (i in 0 until CORE_COUNT) {
            val ang = base[i] + (Math.random().toFloat() * 0.44f - 0.22f)
            val dist = rad * (0.55f + Math.random().toFloat() * 0.60f)
            cores.add(
                Core(
                    cx + cos(ang) * dist,
                    cy + sin(ang) * dist,
                    i.toFloat() / CORE_COUNT * 0.45f
                )
            )
        }
    }

    private fun seedTips(W: Float, H: Float, count: Int, coreIdx: Int? = null) {
        if (cores.isEmpty()) buildCores(W, H)
        val n = minOf(count, MAX_TIPS - tips.size)
        if (n <= 0) return
        val minDim = minOf(W, H)
        for (i in 0 until n) {
            val idx = coreIdx ?: (0 until CORE_COUNT).random()
            val ang = Math.random().toFloat() * TAU
            val radius = 4f + Math.random().toFloat() * (minDim * 0.035f - 4f)
            val hoff = Math.random().toFloat() * 0.15f
            val core = cores[idx]
            tips.add(
                Tip(
                    core.x + cos(ang) * radius,
                    core.y + sin(ang) * radius,
                    ang,
                    0,
                    (core.hOff + hoff) % 1f,
                    idx
                )
            )
        }
    }

    override fun draw(draw: GLDraw, audio: AudioData, tick: Int) {
        val W = draw.W.toFloat()
        val H = draw.H.toFloat()
        val bass = audio.beat
        val mid = audio.mid
        val high = audio.treble

        if (cores.isEmpty() || lastW != draw.W || lastH != draw.H) {
            lastW = draw.W
            lastH = draw.H
            buildCores(W, H)
            tips.clear()
            segs.clear()
            spores.clear()
            seedTips(W, H, 28)
        }

        hue = (hue + 0.003f + mid * 0.002f) % 1f
        phase += 0.010f + mid * 0.012f + high * 0.006f
        pulse = maxOf(0f, pulse - 0.03f)

        if (bass > 0.65f) {
            if (tips.size < MAX_TIPS) {
                pulse = 1f
                val launchCycles = 1 + (bass * 2.5f).toInt()
                repeat(launchCycles) {
                    val seedCount = (5f + bass * 6f).toInt()
                    seedTips(W, H, seedCount, (0 until CORE_COUNT).random())
                }
            }
            for (core in cores) {
                val count = (2..5).random()
                repeat(count) {
                    val ang = Math.random().toFloat() * TAU
                    val spd = 1f + Math.random().toFloat() * 2.5f
                    spores.add(
                        Spore(
                            core.x, core.y,
                            cos(ang) * spd, sin(ang) * spd,
                            core.hOff,
                            (120..240).random(),
                            240,
                            2f + Math.random().toFloat() * 2.5f
                        )
                    )
                }
            }
        }

        val speed = 6.5f + bass * 18.0f + mid * 5.0f
        val spread = 0.20f + mid * 0.30f + high * 0.10f
        val branchP = 0.07f + mid * 0.10f + high * 0.08f
        val segLife = 90 + (bass * 55f + high * 20f).toInt()

        val nextTips = ArrayList<Tip>(MAX_TIPS)
        val tipCount = tips.size
        if (tipCount > 0) {
            for (idx in 0 until tipCount) {
                val tip = tips[idx]
                if (tip.depth >= 18) continue

                val core = cores[tip.coreIdx]
                val swirl = atan2(tip.y - core.y, tip.x - core.x) + PI_F * 0.5f
                val field = sin(tip.x * 0.013f + phase + tip.coreIdx * 0.7f) * 0.55f +
                        cos(tip.y * 0.011f - phase * 1.2f + tip.depth * 0.25f) * 0.35f

                val gauss = gaussianRandom() * spread * 0.16f
                val ta2 = tip.angle + (swirl + field - tip.angle) * 0.22f + gauss

                val lenMul = 0.72f + Math.random().toFloat() * 0.50f
                val length = maxOf(2.5f, speed * (1.0f - tip.depth * 0.030f) * lenMul)

                val ex = ((tip.x + cos(ta2) * length) % W + W) % W
                val ey = ((tip.y + sin(ta2) * length) % H + H) % H

                if (segs.size < MAX_SEGS) {
                    val segJitter = (-16..56).random()
                    segs.add(Seg(tip.x, tip.y, ex, ey, 0, segLife + segJitter, tip.depth, tip.hueOff))
                }

                // Tip grows, occasionally releases a spore
                if (Math.random() < 0.12 && spores.size < MAX_SPORES) {
                    val rx = Math.random().toFloat() - 0.5f
                    val ry = Math.random().toFloat() - 0.5f
                    spores.add(
                        Spore(
                            ex, ey,
                            cos(ta2 + PI_F) * 0.5f + rx,
                            sin(ta2 + PI_F) * 0.5f + ry,
                            tip.hueOff,
                            (80..160).random(),
                            160,
                            1.5f + Math.random().toFloat() * 2f
                        )
                    )
                }

                if (nextTips.size < MAX_TIPS) {
                    nextTips.add(Tip(ex, ey, ta2, tip.depth + 1, tip.hueOff, tip.coreIdx))
                }

                if (Math.random() < branchP && tip.depth < 13 && nextTips.size < MAX_TIPS) {
                    val sign = if (Math.random() < 0.5) -1f else 1f
                    val branchAngle = 0.35f + Math.random().toFloat() * (0.60f + spread)
                    val bAng = ta2 + sign * branchAngle
                    val bCore = if (Math.random() > 0.18) tip.coreIdx else (0 until CORE_COUNT).random()
                    nextTips.add(Tip(ex, ey, bAng, tip.depth + 1, (tip.hueOff + 0.10f) % 1f, bCore))
                }
            }
        }

        tips.clear()
        tips.addAll(nextTips.take(MAX_TIPS))

        if (tips.isEmpty()) {
            buildCores(W, H)
            seedTips(W, H, 28)
        }

        segs.removeAll { it.age >= it.maxAge }
        for (s in segs) s.age++

        // Update Spores
        val nextSpores = ArrayList<Spore>()
        for (s in spores) {
            var nearestCore: Core? = null
            var minD = 99999f
            for (core in cores) {
                val d = hypot(s.x - core.x, s.y - core.y)
                if (d < minD) {
                    minD = d
                    nearestCore = core
                }
            }

            if (nearestCore != null) {
                val cx = nearestCore.x
                val cy = nearestCore.y
                val dx = s.x - cx
                val dy = s.y - cy
                val d = maxOf(1f, minD)
                val ox = -dy / d
                val oy = dx / d
                val px = -dx / d
                val py = -dy / d

                var fOrbit = 0.4f + mid * 0.8f
                var fPull = 0.05f + bass * 0.1f
                if (d > 200f) {
                    fOrbit *= 0.5f
                    fPull *= 2f
                } else if (d < 50f) {
                    fOrbit *= 1.5f
                    fPull = -0.1f
                }

                val rx = (Math.random().toFloat() * 0.3f - 0.15f)
                val ry = (Math.random().toFloat() * 0.3f - 0.15f)

                s.vx += ox * fOrbit + px * fPull + rx
                s.vy += oy * fOrbit + py * fPull + ry
            }

            s.vx *= 0.94f
            s.vy *= 0.94f

            s.x = ((s.x + s.vx) % W + W) % W
            s.y = ((s.y + s.vy) % H + H) % H

            s.life--
            if (s.life > 0) {
                nextSpores.add(s)
            }
        }
        spores.clear()
        spores.addAll(if (nextSpores.size > MAX_SPORES) nextSpores.take(MAX_SPORES) else nextSpores)

        // Fade trail
        draw.fadeBlack(8f / 255f)

        // Draw cores
        draw.setAdditiveBlend()
        for (i in cores.indices) {
            val core = cores[i]
            val corePhase = phase * (0.7f + i * 0.08f)
            val wobble = 10.0f + sin(corePhase) * 8.0f
            val coreHue = (hue + core.hOff + sin(corePhase * 0.6f) * 0.05f) % 1f
            val glow = 0.06f + pulse * 0.12f + bass * 0.05f

            val cGlow = GLDraw.hsl(coreHue, l = glow * 0.35f)
            val cCore = GLDraw.hsl(coreHue, l = glow)
            draw.circle(core.x, core.y, 22f + wobble, cGlow[0], cGlow[1], cGlow[2], 0.85f, filled = true, segments = 16)
            draw.circle(core.x, core.y, 7f + bass * 8f, cCore[0], cCore[1], cCore[2], 0.85f, filled = true, segments = 12)

            // Rotating ring of satellite nodes around each core
            val numDots = 6
            val rRing = 15f + wobble * 0.5f + bass * 12f
            for (d in 0 until numDots) {
                val ang = phase * 0.8f + d * (TAU / numDots)
                val dx = core.x + cos(ang) * rRing
                val dy = core.y + sin(ang) * rRing
                val cDot = GLDraw.hsl(coreHue, l = minOf(0.9f, glow * 1.8f))
                draw.circle(dx, dy, 3f + bass * 2f, cDot[0], cDot[1], cDot[2], 0.85f, filled = true, segments = 8)
            }
        }

        // Draw segments
        for (s in segs) {
            val fade = maxOf(0f, 1f - s.age.toFloat() / s.maxAge)
            val segHue = (hue + s.hueOff + s.depth * 0.015f) % 1f
            val bright = (0.22f + bass * 0.10f + high * 0.08f + pulse * 0.14f) * fade

            val cGlow = GLDraw.hsl(segHue, s = 0.90f, l = bright * 0.30f)
            val cCore = GLDraw.hsl(segHue, s = 0.40f, l = minOf(0.95f, bright * 1.5f))

            draw.line(s.x1, s.y1, s.x2, s.y2, cGlow[0], cGlow[1], cGlow[2], 0.8f)
            draw.line(s.x1, s.y1, s.x2, s.y2, cCore[0], cCore[1], cCore[2], 1.0f)

            if (s.depth > 5 && s.age < s.maxAge * 0.25f && (s.depth + s.age) % 5 == 0) {
                val cDot = GLDraw.hsl(segHue, l = bright * 0.85f)
                val dotR = maxOf(1f, 3f - s.depth / 8f)
                draw.circle(s.x2, s.y2, dotR, cDot[0], cDot[1], cDot[2], 0.8f, filled = true, segments = 6)
            }
        }

        // Draw Spores
        for (s in spores) {
            val fade = s.life.toFloat() / s.maxLife
            val sporeHue = (s.hueOff + hue) % 1f
            val valL = minOf(0.95f, 0.3f + high * 0.5f) * fade
            val r = maxOf(1f, s.size * fade * (1f + high * 0.8f))
            val cSpore = GLDraw.hsl(sporeHue, l = valL)
            draw.circle(s.x, s.y, r, cSpore[0], cSpore[1], cSpore[2], 0.85f, filled = true, segments = 8)
        }
        draw.setNormalBlend()
    }

    private fun gaussianRandom(): Float {
        var u1 = 0f
        var u2 = 0f
        while (u1 == 0f) u1 = Math.random().toFloat()
        while (u2 == 0f) u2 = Math.random().toFloat()
        return sqrt(-2f * ln(u1)) * cos(TAU * u2)
    }
}
