package com.androsaver.visualizer.modes

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw
import kotlin.math.*

/**
 * Möbius — a solid, shaded Möbius ribbon with perspective projection.
 * Port of the psysuals 3.22 `Mobius` rework.
 *
 * The strip is drawn as depth-sorted filled bands, so the near side hides the
 * far side. A wireframe of the same surface showed both sides at once and its
 * lines crossed into moiré noise wherever the strip turned edge-on.
 *
 *   Bass   → spin speed, tilt wobble speed, twist
 *   Mid    → spin speed, hue drift
 *   Treble → spin speed contribution
 *   Beat   → shiver: the ribbon and its rim flare
 */
class MobiusMode : BaseMode() {

    override val name = "Mobius"

    private companion object {
        const val N_V = 6      // colour bands across the ribbon width
        const val N_U = 96     // segments around the strip
        const val HALF_BAND = 0.36f
        const val FOV = 3f
        const val COLS = N_U + 1
        const val GRID = (N_V + 1) * COLS
        const val QUADS = N_V * N_U
        val TAU = (2.0 * PI).toFloat()
    }

    private var ry      = 0f   // spin of the strip in its own plane
    private var rx      = 0f   // phase of the tilt wobble
    private var rw      = 0f   // phase of the rocking fourth-dimensional rotation
    private var hue     = 0.55f
    private var shiver  = 0f
    private var fit     = 1f

    // Camera-space and screen-space grid, row-major over (v, u).
    private val camX = FloatArray(GRID)
    private val camY = FloatArray(GRID)
    private val camZ = FloatArray(GRID)
    private val scrX = FloatArray(GRID)
    private val scrY = FloatArray(GRID)
    private val depth = FloatArray(QUADS)
    private val order = LongArray(QUADS)
    private val quad = FloatArray(8)
    private val fill = FloatArray(4)
    private val rim = FloatArray(4)

    override fun reset() {
        fit = 1f; ry = 0f; rx = 0f; rw = 0f; hue = 0.55f; shiver = 0f
    }

    /** Horizontal and vertical extent of the fitted strip, for JVM contract checks. */
    internal var spanX = 0f; private set
    internal var spanY = 0f; private set

    /** Rotate in XW, spin and tilt every grid point; fill camera and screen arrays. */
    private fun projectGrid(twist: Float, W: Float, H: Float) {
        // Rock into the fourth axis; a full XW turn would flatten the strip.
        val angle4 = 0.5f * sin(rw)
        val cw = cos(angle4); val sw = sin(angle4)
        // The strip stands upright in the plane of the screen and spins in
        // that plane, with a small wobble for depth. A free tumble turned it
        // edge-on, where it shrank to a dark sliver.
        val cs = cos(ry); val ss = sin(ry)
        val tilt = 0.35f * sin(rx * 2.5f)
        val yaw = 0.45f * sin(rx * 1.5f + 1f)
        val ct = cos(tilt); val st = sin(tilt)
        val cy = cos(yaw); val sy = sin(yaw)
        val half = minOf(W, H) * 0.50f
        for (i in 0..N_V) {
            val v = (i / N_V.toFloat() * 2f - 1f) * HALF_BAND
            for (j in 0..N_U) {
                val u = j / N_U.toFloat() * TAU
                val hu = u / 2f
                val cohu = cos(hu) * twist
                val x4 = (1f + v * cohu) * cos(u)
                val y4 = (1f + v * cohu) * sin(u)
                val z4 = v * sin(hu) * twist
                val w4 = v * sin(hu) * 0.35f
                // Perspective from the fourth axis scales the whole solid, not one axis.
                val scale4 = (FOV + 0.35f) / maxOf(0.35f, FOV + 0.35f - (x4 * sw + w4 * cw))
                val x = (x4 * cw - w4 * sw) * scale4
                val y = y4 * scale4
                val z = z4 * scale4
                val xs = x * cs - y * ss
                val ys = x * ss + y * cs
                val yr = ys * ct - z * st
                val zt = ys * st + z * ct
                val xr = xs * cy + zt * sy
                val zr = maxOf(-xs * sy + zt * cy, -FOV + 0.2f)
                val sc = FOV / (FOV + zr)
                val k = i * COLS + j
                camX[k] = xr; camY[k] = yr; camZ[k] = zr
                scrX[k] = xr * sc * half + W / 2f
                scrY[k] = yr * sc * half + H / 2f
            }
        }
    }

    override fun draw(draw: GLDraw, audio: AudioData, tick: Int) {
        val W = draw.W.toFloat(); val H = draw.H.toFloat()
        val response = prepare(draw, audio); val step = response.step
        val bass = response.motion[0]; val mid = response.motion[1]; val high = response.motion[2]
        hue = (hue + (.0012f + mid * .002f) * step) % 1f
        ry += (.008f + bass * .006f + mid * .003f + high * .003f) * step * response.motionScale
        rx += (.003f + bass * .002f) * step * response.motionScale
        rw += (.004f + mid * .002f + high * .003f) * step * response.motionScale
        shiver = response.light[0] * response.accent * .4f
        val twist = 1f + bass * .06f + mid * .06f
        draw.fadeBlackTimed(150f / 255f, step)

        projectGrid(twist, W, H)

        // Fit the entire strip together: preserve shape and leave a glow margin.
        var ex = 1f; var ey = 1f
        for (k in 0 until GRID) {
            ex = maxOf(ex, abs(scrX[k] - W * .5f)); ey = maxOf(ey, abs(scrY[k] - H * .5f))
        }
        fit = minOf(fit + .001f * step, 1f, W * .46f / ex, H * .46f / ey)
        var minX = Float.POSITIVE_INFINITY; var maxX = Float.NEGATIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY; var maxY = Float.NEGATIVE_INFINITY
        for (k in 0 until GRID) {
            scrX[k] = W * .5f + (scrX[k] - W * .5f) * fit
            scrY[k] = H * .5f + (scrY[k] - H * .5f) * fit
            minX = minOf(minX, scrX[k]); maxX = maxOf(maxX, scrX[k])
            minY = minOf(minY, scrY[k]); maxY = maxOf(maxY, scrY[k])
        }
        spanX = maxX - minX; spanY = maxY - minY

        // One quad per (band, segment), painted far to near. The sort key holds
        // the negated depth in its high half and the quad index in its low half.
        for (i in 0 until N_V) {
            for (j in 0 until N_U) {
                val k = i * COLS + j
                val d = (camZ[k] + camZ[k + 1] + camZ[k + COLS] + camZ[k + COLS + 1]) * .25f
                val q = i * N_U + j
                depth[q] = d
                val bits = java.lang.Float.floatToIntBits(-d)
                val sortable = if (bits < 0) bits xor Int.MAX_VALUE else bits
                order[q] = (sortable.toLong() shl 32) or q.toLong()
            }
        }
        java.util.Arrays.sort(order)

        val rimWidth = maxOf(1f, minOf(W, H) / 360f)
        for (n in 0 until QUADS) {
            val q = (order[n] and 0xffffffffL).toInt()
            val i = q / N_U; val j = q % N_U
            val k = i * COLS + j
            val k01 = k + 1; val k10 = k + COLS; val k11 = k + COLS + 1

            // The strip has one side, so only the tilt toward the viewer matters.
            val ax = camX[k01] - camX[k]; val ay = camY[k01] - camY[k]; val az = camZ[k01] - camZ[k]
            val bx = camX[k10] - camX[k]; val by = camY[k10] - camY[k]; val bz = camZ[k10] - camZ[k]
            val nx = ay * bz - az * by; val ny = az * bx - ax * bz; val nz = ax * by - ay * bx
            val facing = abs(nz) / maxOf(sqrt(nx * nx + ny * ny + nz * nz), 1e-9f)
            val near = (0.5f - depth[q] / 3f).coerceIn(0f, 1f)

            // A colour field that is continuous on the strip: crossing the seam
            // flips the width coordinate, and v * cos(u / 2) flips with it.
            val v = (i + 0.5f) / N_V - 0.5f
            val u = (j + 0.5f) / N_U * TAU
            val h = hue + 0.34f * v * cos(u / 2f) + 0.16f * sin(u)
            var lum = (0.10f + 0.28f * facing.pow(1.5f)) * (0.45f + 0.75f * near)
            lum = lum * (1f + bass * 0.35f) + shiver * 0.12f * facing
            GLDraw.hsl(h, 0.9f, minOf(lum, 0.62f).coerceAtLeast(0f), 1f, fill)

            quad[0] = scrX[k];   quad[1] = scrY[k]
            quad[2] = scrX[k01]; quad[3] = scrY[k01]
            quad[4] = scrX[k11]; quad[5] = scrY[k11]
            quad[6] = scrX[k10]; quad[7] = scrY[k10]
            draw.polygon(quad, 4, fill[0], fill[1], fill[2], 1f, true)

            // The single boundary curve of the strip, as a bright rim.
            if (i == 0 || i == N_V - 1) {
                GLDraw.hsl(h, 0.55f, (0.30f + 0.45f * near + shiver * 0.3f).coerceIn(0f, 0.92f), 1f, rim)
                if (i == 0) draw.antiAliasedLine(quad[0], quad[1], quad[2], quad[3], rim[0], rim[1], rim[2], 1f, rimWidth)
                else draw.antiAliasedLine(quad[6], quad[7], quad[4], quad[5], rim[0], rim[1], rim[2], 1f, rimWidth)
            }
        }
    }
}
