package com.androsaver.visualizer.modes

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw
import kotlin.math.*

class PhasonMode : PsysualsFieldMode() {
    override val name = "Phason"
    private val angles = FloatArray(11) { it * 2f * PI.toFloat() / 11f }
    private val phases = FloatArray(11)
    private var beatPrevious = 0f
    private var pulse = 0f

    override fun reset() {
        super.reset()
        for (i in angles.indices) angles[i] = i * 2f * PI.toFloat() / angles.size
        phases.fill(0f)
        beatPrevious = 0f
        pulse = 0f
    }

    override fun draw(draw: GLDraw, audio: AudioData, tick: Int) {
        clearTrail(draw)
        val bass = audio.beat.coerceIn(0f, 1.5f)
        val mid = audio.mid.coerceIn(0f, 4f)
        val high = audio.treble.coerceIn(0f, 4f)
        if (bass > 0.7f && beatPrevious <= 0.7f) pulse = minOf(1f, pulse + 0.75f)
        beatPrevious = bass
        pulse *= 0.94f
        val waveCount = minOf(angles.size, 5 + 2 * mid.toInt())
        val speed = 0.002f + high * 0.001f
        for (i in 0 until waveCount) {
            angles[i] += 0.0007f + speed
            phases[i] += speed + pulse * 0.008f
        }
        hue = (hue + 0.001f + mid * 0.0007f) % 1f
        val tau = 2f * PI.toFloat()
        for (y in 0 until rows) for (x in 0 until cols) {
            val nx = (x / (cols - 1f) - 0.5f) * 2f * tau
            val ny = (y / (rows - 1f) - 0.5f) * 2f * tau
            var field = 0f
            for (i in 0 until waveCount) {
                val a = angles[i]
                field += cos(nx * cos(a) + ny * sin(a) + phases[i])
            }
            field /= waveCount.toFloat()
            field = (field + pulse * cos(nx * 0.5f + ny * 0.5f)).coerceIn(-1f, 1f)
            values[y * cols + x] = (field + 1f) * 0.5f
        }
        paint(draw, audio)
    }
}
