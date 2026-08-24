package com.androsaver.visualizer.modes

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw
import kotlin.math.*

class PhasonMode : PsysualsFieldMode() {
    override val name = "Phason"
    override fun draw(draw: GLDraw, audio: AudioData, tick: Int) {
        clearTrail(draw)
        phase += 0.018f + audio.beat * 0.04f
        hue = (hue + 0.002f) % 1f
        for (y in 0 until rows) for (x in 0 until cols) {
            val nx = fieldX(x, draw)
            val ny = fieldY(y)
            val a = sin(nx * 19f + phase) + sin(ny * 23f - phase * 0.8f)
            val b = sin((nx + ny) * 31f + phase * 0.6f + audio.mid)
            values[y * cols + x] = ((a + b) * 0.25f + 0.5f).coerceIn(0f, 1f)
        }
        paint(draw, audio)
    }
}
