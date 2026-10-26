package com.androsaver.visualizer.modes

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw
import kotlin.math.*

class CymaticaMode : PsysualsFieldMode() {
    override val name = "Cymatica"
    private val modes = arrayOf(1 to 2, 2 to 3, 3 to 4, 4 to 5, 2 to 5, 3 to 6)
    private val modeA = FloatArray(cols * rows)
    private val modeB = FloatArray(cols * rows)
    private val particleX = FloatArray(700) { kotlin.random.Random(0xC7A + it).nextFloat() * 2f - 1f }
    private val particleY = FloatArray(700) { kotlin.random.Random(0xD11 + it).nextFloat() * 2f - 1f }

    override fun draw(draw: GLDraw, audio: AudioData, tick: Int) {
        clearTrail(draw)
        val bass = audio.beat.coerceIn(0f, 1.5f)
        val mid = audio.mid.coerceIn(0f, 4f)
        val high = audio.treble.coerceIn(0f, 4f)
        hue = (hue + 0.001f + high * 0.001f) % 1f
        var dominant = 0
        for (i in 1 until minOf(audio.fft.size, 128)) {
            if (abs(audio.fft[i]) > abs(audio.fft[dominant])) dominant = i
        }
        val aIndex = dominant % modes.size
        val bIndex = (aIndex + 1 + mid.toInt()) % modes.size
        val mix = (tick * 0.004f + mid * 0.12f) % 1f
        fillMode(modeA, modes[aIndex].first, modes[aIndex].second)
        fillMode(modeB, modes[bIndex].first, modes[bIndex].second)
        for (y in 0 until rows) for (x in 0 until cols) {
            val i = y * cols + x
            val field = modeA[i] * (1f - mix) + modeB[i] * mix
            values[i] = exp((-abs(field) * (12f - high * 1.5f)).toDouble()).toFloat().coerceIn(0f, 1f)
        }
        paint(draw, audio)

        draw.setAdditiveBlend()
        val strength = 0.004f + bass * 0.008f
        val radius = psysualsViewportScale(draw)
        for (i in particleX.indices step 2) {
            val px = particleX[i]
            val py = particleY[i]
            val gx = sin(PI.toFloat() * cols * px)
            val gy = sin(PI.toFloat() * rows * py)
            particleX[i] = wrap(px - gx * strength + sin(py * 5f + mix) * high * 0.001f)
            particleY[i] = wrap(py - gy * strength + cos(px * 4f + mix) * high * 0.001f)
            val x = (particleX[i] + 1f) * draw.W * 0.5f
            val y = (particleY[i] + 1f) * draw.H * 0.5f
            draw.circle(x, y, radius, 0.86f, 0.96f, 1f, 0.88f, true, 6)
        }
        draw.setNormalBlend()
    }

    override fun reset() {
        super.reset()
        modeA.fill(0f); modeB.fill(0f)
    }

    private fun fillMode(out: FloatArray, m: Int, n: Int) {
        for (y in 0 until rows) for (x in 0 until cols) {
            val px = x / (cols - 1f) * 2f - 1f
            val py = y / (rows - 1f) * 2f - 1f
            out[y * cols + x] = cos(PI.toFloat() * m * px) * cos(PI.toFloat() * n * py)
        }
    }

    private fun wrap(value: Float): Float = ((value + 1f) % 2f + 2f) % 2f - 1f
}
