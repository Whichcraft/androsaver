package com.androsaver.visualizer.modes

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw

class MorphogenesisMode : PsysualsFieldMode() {
    override val name = "Morphogenesis"
    private val u = FloatArray(cols * rows)
    private val v = FloatArray(cols * rows)
    private val nextU = FloatArray(cols * rows)
    private val nextV = FloatArray(cols * rows)

    override fun reset() {
        super.reset()
        u.fill(1f); v.fill(0f)
        for (y in rows / 2 - 1..rows / 2 + 1)
            for (x in cols / 2 - 1..cols / 2 + 1) v[y * cols + x] = 1f
    }

    override fun draw(draw: GLDraw, audio: AudioData, tick: Int) {
        clearTrail(draw)
        val feed = 0.028f + audio.treble * 0.002f
        val kill = 0.057f + audio.mid * 0.001f
        repeat(2) {
            for (y in 0 until rows) for (x in 0 until cols) {
                val i = y * cols + x
                val l = u[y * cols + ((x + cols - 1) % cols)]
                val r = u[y * cols + ((x + 1) % cols)]
                val t = u[((y + rows - 1) % rows) * cols + x]
                val b = u[((y + 1) % rows) * cols + x]
                val lv = v[y * cols + ((x + cols - 1) % cols)]
                val rv = v[y * cols + ((x + 1) % cols)]
                val tv = v[((y + rows - 1) % rows) * cols + x]
                val bv = v[((y + 1) % rows) * cols + x]
                val lapU = l + r + t + b - 4f * u[i]
                val lapV = lv + rv + tv + bv - 4f * v[i]
                val uvv = u[i] * v[i] * v[i]
                nextU[i] = (u[i] + 0.16f * lapU - uvv + feed * (1f - u[i])).coerceIn(0f, 1f)
                nextV[i] = (v[i] + 0.08f * lapV + uvv - (kill + feed) * v[i]).coerceIn(0f, 1f)
            }
            for (i in values.indices) { u[i] = nextU[i]; v[i] = nextV[i]; values[i] = v[i] }
        }
        phase += 0.01f + audio.mid * 0.01f
        hue = (hue + 0.001f + audio.treble * 0.002f) % 1f
        paint(draw, audio)
    }
}
