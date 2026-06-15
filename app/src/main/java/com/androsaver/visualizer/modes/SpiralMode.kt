package com.androsaver.visualizer.modes

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw
import kotlin.math.*

/**
 * SpiralMode — 3D particle spiral arms with depth and symmetry.
 * Port of Python class `Spiral`.
 */
class SpiralMode : BaseMode() {

    override val name = "Spiral"

    companion object {
        private const val N_ARMS   = 6
        private const val N_PTS    = 80
        private const val Z_FAR    = 10f
        private const val Z_NEAR   = 0.12f
        private const val RADIUS   = 1f
        private const val SPIN     = 1.6f
        private const val N_SYM    = 3
        private const val RING_STEP = 8
        private const val TAU = (PI * 2).toFloat()
    }

    data class Particle(var arm: Int, var z: Float, var pt: Float)

    private var hue    = 0f
    private var time   = 0f
    private var scale  = 1f
    private var svel   = 0f

    private lateinit var particles: List<Particle>

    override fun reset() {
        hue = 0f; time = 0f; scale = 1f; svel = 0f
        val spacing = (Z_FAR - Z_NEAR) / N_PTS
        particles = buildList {
            for (arm in 0 until N_ARMS) {
                for (j in 0 until N_PTS) {
                    val z = Z_NEAR + j * spacing
                    add(Particle(arm = arm, z = z, pt = z))
                }
            }
        }
    }

    /** Gentle Lissajous path offset for the spiral center. Mids increase wobble amplitude. */
    private fun pathXY(t: Float, mid: Float = 0f): Pair<Float, Float> {
        val s = 1f + mid * 0.50f
        return sin(t * 0.18f) * 0.25f * s to cos(t * 0.13f) * 0.20f * s
    }

    /** Perspective projection → (screenX, screenY, scale). */
    private fun proj(wx: Float, wy: Float, wz: Float, W: Int, H: Int): Triple<Float, Float, Float> {
        val fov = minOf(W, H) * 0.72f
        val z = maxOf(wz, 0.01f)
        return Triple(wx * fov / z + W / 2f, wy * fov / z + H / 2f, fov / z)
    }

    override fun draw(draw: GLDraw, audio: AudioData, tick: Int) {
        draw.fadeBlack(0.11f)

        val beat = audio.beat
        val W    = draw.W
        val H    = draw.H

        val bass = beat
        val mid  = audio.mid
        val high = audio.treble

        hue = (hue + 0.007f + beat * 0.04f) % 1f
        val dt   = 0.038f + bass * 0.10f + mid * 0.08f + high * 0.04f
        time += dt

        svel += bass * 0.48f
        svel += (1f - scale) * 0.25f
        svel *= 0.81f
        scale = maxOf(0.4f, scale + svel)

        // Advance particles
        for (p in particles) {
            p.z -= dt
            if (p.z < Z_NEAR) {
                p.z += Z_FAR
                p.pt = time + p.z
            }
        }

        // Group particles by arm, sort far → near (largest z first)
        val byArm: List<List<Particle>> = List(N_ARMS) { arm ->
            particles.filter { it.arm == arm }.sortedByDescending { it.z }
        }

        // Precompute projected points per arm
        data class ProjPoint(val sx: Float, val sy: Float, val h: Float,
                             val bright: Float, val sc: Float, val nearT: Float)

        // Treble dynamically twists the spiral arms
        val spinVal = SPIN * (1f + high * 0.40f)

        val armSegs: List<List<ProjPoint>> = byArm.mapIndexed { armIdx, armPts ->
            armPts.map { p ->
                val nearT = maxOf(0f, 1f - p.z / Z_FAR)
                // Helix radius reacts to mids and treble
                val rMod = mid * 0.70f + high * 0.40f
                val angle = p.pt * spinVal + armIdx.toFloat() / N_ARMS * TAU
                val (pcx, pcy) = pathXY(p.pt, mid = mid)
                val r = (RADIUS + rMod) * scale
                val wx = pcx + r * cos(angle)
                val wy = pcy + r * sin(angle)
                val (sx, sy, sc) = proj(wx, wy, p.z, W, H)
                val h = (hue + armIdx.toFloat() / N_ARMS * 0.5f + nearT * 1.3f) % 1f
                val bright = nearT.pow(1.15f)
                ProjPoint(sx, sy, h, bright, sc, nearT)
            }
        }

        val cx0 = W / 2f
        val cy0 = H / 2f

        draw.setAdditiveBlend()

        for (sym in 0 until N_SYM) {
            val ang = sym.toFloat() / N_SYM * TAU
            val ca  = cos(ang); val sa = sin(ang)

            fun rot(sx: Float, sy: Float): Pair<Float, Float> {
                val dx = sx - cx0; val dy = sy - cy0
                return (cx0 + dx * ca - dy * sa) to (cy0 + dx * sa + dy * ca)
            }

            // Draw individual arm particles (dots with halo + core)
            for (segs in armSegs) {
                for (pp in segs) {
                    val (rx, ry) = rot(pp.sx, pp.sy)
                    val rDot = maxOf(1f, minOf(pp.sc * (0.028f + high * 0.015f), 12f))

                    // Halo pass (large, low alpha)
                    val cHalo = GLDraw.hsl(pp.h, 1f, pp.bright * 0.20f + mid * 0.08f)
                    draw.circle(rx, ry, rDot * 3f + 1f,
                        cHalo[0], cHalo[1], cHalo[2], cHalo[3], filled = true)

                    // Core pass (small, full brightness)
                    val cCore = GLDraw.hsl(pp.h, 1f, minOf(pp.bright * 0.90f + 0.08f + high * 0.05f, 0.95f))
                    draw.circle(rx, ry, rDot,
                        cCore[0], cCore[1], cCore[2], cCore[3], filled = true)

                    // Beat/treble flash for nearby particles
                    if (pp.nearT > 0.80f && (bass > 0.35f || high > 0.45f)) {
                        val fr = maxOf(3f, rDot * (2.2f + bass * 0.9f + high * 1.2f))
                        val cFlash = GLDraw.hsl(pp.h, 1f, 0.96f)
                        draw.circle(rx, ry, fr,
                            cFlash[0], cFlash[1], cFlash[2], cFlash[3], filled = true)
                    }
                }
            }

            // Ring connectors at regular depth intervals
            val n = if (armSegs.isNotEmpty()) armSegs[0].size else 0
            var j = 0
            while (j < n) {
                for (aSegs in armSegs) {
                    if (j < aSegs.size) {
                        val pp = aSegs[j]
                        val (rx, ry) = rot(pp.sx, pp.sy)
                        val rDot = maxOf(1f, minOf(pp.sc * (0.022f + high * 0.012f), 10f))

                        val cHalo = GLDraw.hsl(pp.h, 1f, pp.bright * 0.35f)
                        draw.circle(rx, ry, rDot * 3f,
                            cHalo[0], cHalo[1], cHalo[2], cHalo[3], filled = true)

                        val cCore = GLDraw.hsl(pp.h, 1f, minOf(pp.bright * 0.85f + 0.10f, 0.92f))
                        draw.circle(rx, ry, rDot + 1f,
                            cCore[0], cCore[1], cCore[2], cCore[3], filled = true)
                    }
                }
                j += RING_STEP
            }
        }

        draw.setNormalBlend()
    }

    private fun Float.pow(exp: Float): Float = this.toDouble().pow(exp.toDouble()).toFloat()
}
