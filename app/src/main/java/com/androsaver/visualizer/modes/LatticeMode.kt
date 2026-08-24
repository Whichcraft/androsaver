package com.androsaver.visualizer.modes

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw
import kotlin.math.*

/**
 * LatticeMode — Crystal grid of glowing nodes and beam lines with feedback.
 * Dynamic grid (14×9 / 18×12 / 22×14) with center-out frequency mapping
 * (center columns = bass, edge columns = treble).
 * Port of psysuals Lattice (v3.11.0).
 */
class LatticeMode : BaseMode() {

    override val name = "Lattice"

    private companion object {
        const val FFT_USE = 0.55f
        const val SHOCK_W = 22f
        const val IDLE    = 0.08f
        const val FFT_START_BIN = 3
    }

    private fun gridCols(W: Int) = when {
        W >= 2560 -> 22
        W >= 1600 -> 18
        else      -> 14
    }
    private fun gridRows(W: Int) = when {
        W >= 2560 -> 14
        W >= 1600 -> 12
        else      -> 9
    }

    private data class Node(
        val ox: Float, val oy: Float,
        val col: Int, val row: Int,
        val dist: Float,
        val hOff: Float
    )

    private val nodes = ArrayList<Node>(22 * 14)
    private var hue = 0.52f
    private var shockR = 9999f
    private var shockSpd = 0f
    private var beatPrev = 0f
    private var scale = 1f
    private var svel = 0f
    private var cx = 0f
    private var cy = 0f
    private var maxR = 1f
    private var lastW = 0
    private var lastH = 0
    private var colPeaks = FloatArray(14) { 0.2f }
    private var rawEnergies = FloatArray(14)
    private var scaledEnergies = FloatArray(14)
    private var sxArr = FloatArray(22 * 14)
    private var syArr = FloatArray(22 * 14)
    private var bright = FloatArray(22 * 14)
    private val colorScratch = FloatArray(4)

    override fun reset() {
        hue = 0.52f
        shockR = 9999f
        beatPrev = 0f
        scale = 1f
        svel = 0f
        lastW = 0
        lastH = 0
        nodes.clear()
        colPeaks = FloatArray(14) { 0.2f }
    }

    private fun getBin(col: Int, nCols: Int, fftLen: Int): Int {
        val start = FFT_START_BIN
        val end = minOf(fftLen - 1, (fftLen * FFT_USE).toInt())
        if (end <= start) return 0
        // Center-out mapping: center columns → low frequencies, edge columns → high
        val center = (nCols - 1) / 2.0f
        val normalized = abs(col - center) / maxOf(center, 1f)
        return start + (normalized * (end - start)).toInt()
    }

    override fun draw(draw: GLDraw, audio: AudioData, tick: Int) {
        val W = draw.W; val H = draw.H
        val nCols = gridCols(W)
        val nRows = gridRows(W)
        if (W != lastW || H != lastH) {
            lastW = W; lastH = H
            cx = W / 2f; cy = H / 2f
            maxR = hypot(cx, cy)
            shockSpd = 6f * (W / 640f)

            val x0 = W * 0.08f; val y0 = H * 0.10f
            val cw = W * 0.84f / maxOf(nCols - 1, 1)
            val ch = H * 0.80f / maxOf(nRows - 1, 1)

            nodes.clear()
            for (row in 0 until nRows) {
                for (col in 0 until nCols) {
                    val ox = x0 + col * cw
                    val oy = y0 + row * ch
                    val dist = hypot(ox - cx, oy - cy)
                    val hOff = dist / maxR * 0.55f
                    nodes.add(Node(ox, oy, col, row, dist, hOff))
                }
            }
            // Reset peaks when grid size changes
            if (colPeaks.size != nCols) colPeaks = FloatArray(nCols) { 0.2f }
            if (rawEnergies.size != nCols) rawEnergies = FloatArray(nCols)
            if (scaledEnergies.size != nCols) scaledEnergies = FloatArray(nCols)
            if (sxArr.size < nodes.size) sxArr = FloatArray(nodes.size)
            if (syArr.size < nodes.size) syArr = FloatArray(nodes.size)
            if (bright.size < nodes.size) bright = FloatArray(nodes.size)
        }

        val fft = audio.fft
        val beat = audio.beat
        val bass = beat
        val mid  = audio.mid
        val high = audio.treble
        val scaleFactor = minOf(W, H) / 640f

        hue = (hue + 0.0025f + mid * 0.005f + high * 0.002f) % 1f

        if (beat > 0.6f && beatPrev <= 0.6f) {
            shockR = 0f
        }
        beatPrev = beat
        shockR += shockSpd * (1f + bass * 2f + mid * 0.8f)

        svel += (1f + bass * 0.04f + high * 0.02f - scale) * 0.18f
        svel *= 0.70f
        scale = (scale + svel).coerceIn(0.90f, 1.12f)
        // Trail decay approximates the pygame feedback fade.
        draw.fadeBlack(48f / 255f)

        // Guard against size mismatch after a grid resize
        if (colPeaks.size != nCols) colPeaks = FloatArray(nCols) { 0.2f }

        // Dynamic frequency peak normalization with noise gate
        for (col in 0 until nCols) {
            rawEnergies[col] = maxOf(fft[getBin(col, nCols, fft.size)] - 0.015f, 0f)
        }
        for (col in 0 until nCols) {
            colPeaks[col] = maxOf(colPeaks[col] * 0.996f, rawEnergies[col])
            colPeaks[col] = maxOf(colPeaks[col], 0.08f)
        }
        // Node column brightness scaled by mids and raw energy
        for (col in 0 until nCols) {
            scaledEnergies[col] = (rawEnergies[col] / colPeaks[col]) * (0.50f + mid * 0.30f)
        }

        for (ni in nodes.indices) {
            val nd = nodes[ni]
            // Keep the TV grid geometry stable; the upstream hyperbolic
            // morph was removed because it over-expanded outer nodes.
            val sx = cx + (nd.ox - cx) * scale
            val sy = cy + (nd.oy - cy) * scale
            sxArr[ni] = sx
            syArr[ni] = sy
            val energy = scaledEnergies[nd.col] + IDLE
            val dist = hypot(sx - cx, sy - cy)
            val shockW = (SHOCK_W / 4f) * scaleFactor
            val shock = maxOf(0f, 1f - abs(dist - shockR) / shockW)
            bright[ni] = minOf(energy + shock * (0.6f + bass * 0.4f + high * 0.3f), 1.8f)
        }

        // Draw Beams (treble adds line width shimmer)
        draw.setAdditiveBlend()
        for (row in 0 until nRows) {
            for (col in 0 until nCols) {
                val ni = row * nCols + col
                val nd = nodes[ni]
                val nhue = (hue + nd.hOff) % 1f

                if (col < nCols - 1) {
                    val niR = ni + 1

                    // Base faint line
                    val cBase = hsl(nhue, s = 0.25f, l = 0.06f)
                    draw.line(sxArr[ni], syArr[ni], sxArr[niR], syArr[niR], cBase[0], cBase[1], cBase[2], 1f)

                    val avgB = (bright[ni] + bright[niR]) * 0.5f
                    if (avgB > 0.1f) {
                        // Glow line
                        val cOuter = hsl(nhue, l = minOf(avgB * 0.15f, 0.25f))
                        draw.line(sxArr[ni], syArr[ni], sxArr[niR], syArr[niR], cOuter[0], cOuter[1], cOuter[2], 1f)
                        // Core line
                        val cCore = hsl(nhue, l = minOf(avgB * 0.40f, 0.70f))
                        draw.line(sxArr[ni], syArr[ni], sxArr[niR], syArr[niR], cCore[0], cCore[1], cCore[2], 1f)
                    }
                }
                if (row < nRows - 1) {
                    val niD = ni + nCols

                    // Base faint line
                    val cBase = hsl(nhue, s = 0.25f, l = 0.06f)
                    draw.line(sxArr[ni], syArr[ni], sxArr[niD], syArr[niD], cBase[0], cBase[1], cBase[2], 1f)

                    val avgB = (bright[ni] + bright[niD]) * 0.5f
                    if (avgB > 0.1f) {
                        // Glow line
                        val cOuter = hsl(nhue, l = minOf(avgB * 0.15f, 0.25f))
                        draw.line(sxArr[ni], syArr[ni], sxArr[niD], syArr[niD], cOuter[0], cOuter[1], cOuter[2], 1f)
                        // Core line
                        val cCore = hsl(nhue, l = minOf(avgB * 0.40f, 0.70f))
                        draw.line(sxArr[ni], syArr[ni], sxArr[niD], syArr[niD], cCore[0], cCore[1], cCore[2], 1f)
                    }
                }
            }
        }

        // Draw Nodes (treble adds base radius shimmer)
        for (ni in nodes.indices) {
            val nd = nodes[ni]
            val nhue = (hue + nd.hOff) % 1f

            // Base faint node (shimmers with treble)
            val cBase = hsl(nhue, s = 0.25f, l = 0.08f)
            draw.circle(sxArr[ni], syArr[ni], (2f + high * 1.8f) * scaleFactor, cBase[0], cBase[1], cBase[2], 1f, filled = true, segments = 8)

            val b = bright[ni]
            if (b > 0.15f) {
                val baseR = (2f + b * 5f + high * 2.5f) * scaleFactor
                val rCore = maxOf(1f, baseR)
                val rMid = maxOf(2f, baseR * 1.8f)
                val rOuter = maxOf(3f, baseR * 3f)

                // Outer soft glow
                val cOuter = hsl(nhue, l = minOf(b * 0.15f + 0.02f, 0.25f))
                draw.circle(sxArr[ni], syArr[ni], rOuter, cOuter[0], cOuter[1], cOuter[2], 1f, filled = true, segments = 12)
                // Middle soft glow
                val cMid = hsl(nhue, l = minOf(b * 0.40f + 0.08f, 0.60f))
                draw.circle(sxArr[ni], syArr[ni], rMid, cMid[0], cMid[1], cMid[2], 1f, filled = true, segments = 12)
                // Core bright center
                val cCore = hsl(nhue, l = minOf(b * 0.75f + 0.15f + high * 0.05f, 0.95f))
                draw.circle(sxArr[ni], syArr[ni], rCore, cCore[0], cCore[1], cCore[2], 1f, filled = true, segments = 10)
            }
        }
        draw.setNormalBlend()
    }

    private fun hsl(h: Float, s: Float = 1f, l: Float = 0.5f): FloatArray {
        GLDraw.hsl(h, s, l, 1f, colorScratch)
        return colorScratch
    }
}
