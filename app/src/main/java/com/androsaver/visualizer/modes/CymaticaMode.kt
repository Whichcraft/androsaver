package com.androsaver.visualizer.modes

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw
import kotlin.math.*

class CymaticaMode : PsysualsFieldMode() {
    override val name = "Cymatica"
    override fun draw(draw: GLDraw, audio: AudioData, tick: Int) {
        clearTrail(draw)
        phase += 0.012f + audio.beat * 0.025f
        hue = (hue + 0.001f) % 1f
        val m = 1 + (tick / 180) % 5
        val n = m + 1
        for (y in 0 until rows) for (x in 0 until cols) {
            val px = fieldX(x, draw) * 2f
            val py = fieldY(y) * 2f
            val nodal = cos(PI * m * px + phase) * cos(PI * n * py - phase * 0.7f)
            values[y * cols + x] = exp(-abs(nodal) * (8f - audio.treble).toDouble()).toFloat().coerceIn(0f, 1f)
        }
        paint(draw, audio, 0.92f)
        draw.setAdditiveBlend()
        for (i in 0 until 80) {
            val x = (sin(i * 12.7f + phase * 2f) * 0.5f + 0.5f) * draw.W
            val y = (sin(i * 7.3f + phase * 1.3f) * 0.5f + 0.5f) * draw.H
            draw.circle(x, y, (1.2f + audio.beat) * psysualsViewportScale(draw),
                0.75f, 0.9f, 1f, 0.7f, true, 5)
        }
        draw.setNormalBlend()
    }
}
