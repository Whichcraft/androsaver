package com.androsaver.visualizer.modes

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw
import com.androsaver.visualizer.VisualizerRenderTuning
import kotlin.math.*

/** Audio-reactive wireframe projection of a rotating four-dimensional cube. */
class TesseractMode : BaseMode() {
    override val name = "Tesseract"

    private val projected = FloatArray(16 * 2)
    private var angleXY = 0f
    private var angleXW = 0f
    private var angleYZ = 0f
    private var hue = 0f
    private var beatPrevious = 0f
    private var pulse = 0f

    override fun reset() {
        angleXY = 0f
        angleXW = 0f
        angleYZ = 0f
        hue = 0f
        beatPrevious = 0f
        pulse = 0f
    }

    override fun draw(draw: GLDraw, audio: AudioData, tick: Int) {
        VisualizerRenderTuning.fadeTrail(draw)
        val bass = audio.beat.coerceIn(0f, 1.5f)
        val mid = audio.mid.coerceIn(0f, 4f)
        val treble = audio.treble.coerceIn(0f, 4f)
        if (bass > 0.7f && beatPrevious <= 0.7f) pulse = 1f
        beatPrevious = bass
        pulse = maxOf(0f, pulse - 0.035f)

        // The two rotation planes respond independently to bass and mids.
        angleXY += 0.008f + bass * 0.035f + treble * 0.004f
        angleXW += 0.005f + mid * 0.018f + bass * 0.012f
        angleYZ += 0.003f + treble * 0.012f
        hue = (hue + 0.0015f + treble * 0.0015f) % 1f

        val size = minOf(draw.W, draw.H) * (0.31f + pulse * 0.025f)
        val perspective = 3.6f + mid * 0.12f
        for (i in 0 until 16) {
            var x = if (i and 1 == 0) -1f else 1f
            var y = if (i and 2 == 0) -1f else 1f
            var z = if (i and 4 == 0) -1f else 1f
            var w = if (i and 8 == 0) -1f else 1f

            var rotated = rotate(x, y, angleXY); x = rotated.first; y = rotated.second
            rotated = rotate(y, z, angleYZ); y = rotated.first; z = rotated.second
            rotated = rotate(x, w, angleXW); x = rotated.first; w = rotated.second

            // Project through W first, then use Z for ordinary 3D depth.
            val wScale = perspective / (perspective - w * 0.38f)
            x *= wScale; y *= wScale; z *= wScale
            val depth = 3.4f + z * 0.55f
            projected[i * 2] = draw.W * 0.5f + x * size / depth
            projected[i * 2 + 1] = draw.H * 0.5f + y * size / depth
        }

        val color = GLDraw.hsl(hue, 0.82f, 0.62f + pulse * 0.12f)
        for (i in 0 until 16) {
            for (bit in intArrayOf(1, 2, 4, 8)) {
                if (i and bit != 0) continue
                val j = i or bit
                val dimensionHue = (hue + when (bit) { 1 -> 0f; 2 -> 0.12f; 4 -> 0.25f; else -> 0.48f }) % 1f
                val edge = GLDraw.hsl(dimensionHue, 0.9f, 0.58f + pulse * 0.18f)
                draw.line(projected[i * 2], projected[i * 2 + 1],
                    projected[j * 2], projected[j * 2 + 1], edge[0], edge[1], edge[2], 0.72f)
            }
        }
        val radius = (2.2f + bass * 2.4f + pulse * 1.2f) * psysualsViewportScale(draw)
        for (i in 0 until 16) {
            draw.circle(projected[i * 2], projected[i * 2 + 1], radius,
                color[0], color[1], color[2], 0.9f, true, 8)
        }
    }

    private fun rotate(a: Float, b: Float, angle: Float): Pair<Float, Float> {
        val c = cos(angle)
        val s = sin(angle)
        return (a * c - b * s) to (a * s + b * c)
    }
}
