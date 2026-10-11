package com.androsaver.visualizer.modes

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw

class MorphogenesisMode : PsysualsFieldMode() {
    override val name = "Morphogenesis"
    private val presets = arrayOf(0.025f to 0.055f, 0.036f to 0.065f, 0.055f to 0.062f)
    private val u = FloatArray(cols * rows)
    private val v = FloatArray(cols * rows)
    private val nextU = FloatArray(cols * rows)
    private val nextV = FloatArray(cols * rows)
    private var beatPrevious = 0f
    private val rng = kotlin.random.Random(0x6D6F7270)

    override fun reset() {
        super.reset()
        u.fill(1f); v.fill(0f)
        beatPrevious = 0f
        val radius = maxOf(2f, minOf(cols, rows) * 0.10f)
        for (y in 0 until rows) for (x in 0 until cols) {
            val dx = x - cols * 0.5f
            val dy = y - rows * 0.5f
            if (dx * dx + dy * dy <= radius * radius) {
                val i = y * cols + x
                u[i] = 0f
                v[i] = 1f
            }
        }
    }

    override fun draw(draw: GLDraw, audio: AudioData, tick: Int) {
        clearTrail(draw)
        val bass = audio.beat.coerceIn(0f, 1.5f)
        val mid = audio.mid.coerceIn(0f, 4f)
        val high = audio.treble.coerceIn(0f, 4f)
        if (bass > 0.7f && beatPrevious <= 0.7f) inject(2 + minOf(4, bass.toInt()), 0.35f + bass * 0.12f)
        beatPrevious = bass

        val presetPos = (tick * 0.002f + mid * 0.35f) % presets.size
        val preset = presetPos.toInt()
        val nextPreset = (preset + 1) % presets.size
        val mix = presetPos - preset
        val feed = presets[preset].first * (1f - mix) + presets[nextPreset].first * mix
        val kill = presets[preset].second * (1f - mix) + presets[nextPreset].second * mix + high * 0.0015f
        val substeps = minOf(8, 4 + (high * 0.8f).toInt())
        repeat(substeps) {
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
        hue = (hue + 0.002f + mid * 0.001f + high * 0.0005f) % 1f
        paint(draw, audio)
    }

    private fun inject(count: Int, strength: Float) {
        val radius = maxOf(1, (minOf(cols, rows) * 0.035f).toInt())
        repeat(count.coerceAtMost(6)) {
            val cx = rng.nextInt(radius, maxOf(radius + 1, cols - radius))
            val cy = rng.nextInt(radius, maxOf(radius + 1, rows - radius))
            for (y in maxOf(0, cy - radius)..minOf(rows - 1, cy + radius)) {
                for (x in maxOf(0, cx - radius)..minOf(cols - 1, cx + radius)) {
                    if ((x - cx) * (x - cx) + (y - cy) * (y - cy) > radius * radius) continue
                    val i = y * cols + x
                    v[i] = (v[i] + strength).coerceIn(0f, 1f)
                    u[i] = (u[i] - strength * 0.7f).coerceIn(0f, 1f)
                }
            }
        }
    }
}
