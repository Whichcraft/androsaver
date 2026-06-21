package com.androsaver.visualizer.modes

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw
import kotlin.math.*

/**
 * Persistence of Vision — stroboscopic rotating geometric shapes.
 *
 * Multiple nested polygons rotate at subtly different speeds.  With long
 * trail persistence their ghost images accumulate and interfere, creating
 * wagon-wheel moiré illusions and mandala-like kaleidoscope patterns.
 *
 *   Bass   → rotation speed burst
 *   Mid    → number of shapes / polygon complexity
 *   Treble → strobe flash
 *   Beat   → speed spike + hue jump
 *
 * Port of psysuals `effects/persistence.py` (v3.8.0).
 * TRAIL_ALPHA=5 → fadeBlack(5f/255f) — very long persistence for moiré build-up.
 */
class PersistenceMode : BaseMode() {

    override val name = "Persistence"

    private companion object {
        const val MAX_SHAPES = 8
        val TAU = (2.0 * PI).toFloat()
    }

    private val rotX = FloatArray(MAX_SHAPES) { (Math.random() * 2 * PI).toFloat() }
    private val rotY = FloatArray(MAX_SHAPES) { (Math.random() * 2 * PI).toFloat() }
    private val rotZ = FloatArray(MAX_SHAPES) { (Math.random() * 2 * PI).toFloat() }

    private val speedsX = FloatArray(MAX_SHAPES) { 0.008f * (1f + it * 0.12f) }
    private val speedsY = FloatArray(MAX_SHAPES) { 0.012f * (1f + it * 0.08f) }
    private val speedsZ = FloatArray(MAX_SHAPES) { 0.015f * (1f + it * 0.15f) }

    private var hue      = 0f
    private var boost    = 0f
    private var beatPrev = 0f

    override fun reset() {
        hue = 0f; boost = 0f; beatPrev = 0f
        for (i in 0 until MAX_SHAPES) {
            rotX[i] = (Math.random() * 2 * PI).toFloat()
            rotY[i] = (Math.random() * 2 * PI).toFloat()
            rotZ[i] = (Math.random() * 2 * PI).toFloat()
        }
    }

    override fun draw(draw: GLDraw, audio: AudioData, tick: Int) {
        val W = draw.W.toFloat(); val H = draw.H.toFloat()
        val bass = audio.beat
        val mid  = audio.mid
        val high = audio.treble

        hue   = (hue + 0.003f + mid * 0.003f) % 1f
        boost = maxOf(0f, boost - 0.04f)

        if (bass > 0.75f && beatPrev <= 0.75f) boost = 1.5f + bass * 1.0f
        beatPrev = bass

        draw.fadeBlack(5f / 255f)

        val cx      = W / 2f; val cy = H / 2f
        val baseR   = minOf(W, H) * 0.38f
        val fov     = baseR
        val spdMul  = 1f + bass * 2.5f + boost
        val nShapes = maxOf(3, minOf(MAX_SHAPES, 3 + (mid * 5).toInt()))

        for (i in 0 until nShapes) {
            rotX[i] += speedsX[i] * spdMul
            rotY[i] += speedsY[i] * spdMul
            rotZ[i] += speedsZ[i] * spdMul

            val ax = rotX[i]
            val ay = rotY[i]
            val az = rotZ[i]

            val rLocal = 0.25f + 0.75f * (i + 1) / nShapes
            val sides  = 3 + i
            val h      = (hue + i.toFloat() / nShapes * 0.55f) % 1f
            val bright = 0.28f + bass * 0.25f + if (i == nShapes - 1) high * 0.12f else 0f

            val cxCos = cos(ax); val cxSin = sin(ax)
            val cyCos = cos(ay); val cySin = sin(ay)
            val czCos = cos(az); val czSin = sin(az)

            val projX = FloatArray(sides)
            val projY = FloatArray(sides)
            val depths = FloatArray(sides)

            for (s in 0 until sides) {
                val ang = s.toFloat() / sides * TAU
                val vx = cos(ang) * rLocal
                val vy = sin(ang) * rLocal
                val vz = 0f

                // Rotate around X
                val y1 = vy * cxCos - vz * cxSin
                val z1 = vy * cxSin + vz * cxCos

                // Rotate around Y
                val x2 = vx * cyCos + z1 * cySin
                val z2 = -vx * cySin + z1 * cyCos

                // Rotate around Z
                val x3 = x2 * czCos - y1 * czSin
                val y3 = x2 * czSin + y1 * czCos
                val z3 = z2

                val camZ = 2.2f
                val depth = camZ + z3

                projX[s] = cx + x3 * fov / depth
                projY[s] = cy + y3 * fov / depth
                depths[s] = z3
            }

            for (s in 0 until sides) {
                val nextS = (s + 1) % sides
                val avgZ = (depths[s] + depths[nextS]) / 2f
                var dFactor = (3.2f - (2.2f + avgZ)) / 2f
                dFactor = (0.2f + 0.8f * dFactor).coerceIn(0.15f, 1f)

                val col = GLDraw.hsl(h, l = bright * dFactor)
                val glow = GLDraw.hsl(h, l = bright * 0.30f * dFactor)

                // Double-pass line drawing for glow effect
                draw.line(projX[s], projY[s], projX[nextS], projY[nextS], glow[0], glow[1], glow[2], glow[3])
                draw.line(projX[s], projY[s], projX[nextS], projY[nextS], col[0], col[1], col[2], col[3])
            }
        }

        // Treble: brief radial flash ring
        if (high > 0.50f) {
            val flashR = minOf(W, H) * 0.48f * high
            val fc = GLDraw.hsl(hue, l = 0.15f * high)
            draw.circle(cx, cy, flashR, fc[0], fc[1], fc[2], fc[3], filled = false, segments = 40)
        }
    }
}
