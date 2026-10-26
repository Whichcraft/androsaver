package com.androsaver.visualizer.modes

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw
import kotlin.math.*

class MandelboxMode : PsysualsFieldMode() {
    override val name = "Mandelbox"
    override fun draw(draw: GLDraw, audio: AudioData, tick: Int) {
        clearTrail(draw)
        val bass = audio.beat.coerceIn(0f, 1.5f)
        val mid = audio.mid.coerceIn(0f, 4f)
        val high = audio.treble.coerceIn(0f, 4f)
        val fold = 1.72f + bass * 0.14f + mid * 0.025f
        phase = tick * (0.004f + high * 0.001f)
        hue = (hue + 0.001f + mid * 0.0008f) % 1f
        val c = cos(phase)
        val s = sin(phase)
        for (y in 0 until rows) for (x in 0 until cols) {
            val px = x / (cols - 1f) * 2.8f - 1.4f
            val py = y / (rows - 1f) * 2f - 1f
            val initX = px * c - py * s
            val initY = px * s + py * c
            var zx = initX
            var zy = initY
            var orbit = 1f
            var escapedAt = 18
            var escaped = false
            for (i in 0 until 18) {
                zx = (abs(zx * fold - 0.5f) - 0.35f).coerceIn(-2f, 2f)
                zy = (abs(zy * fold + 0.25f) - 0.35f).coerceIn(-2f, 2f)
                val radius2 = zx * zx + zy * zy
                orbit = minOf(orbit, abs(zx) + abs(zy))
                if (!escaped && radius2 > 4f) {
                    escapedAt = i
                    escaped = true
                }
                zx = zx * 0.94f + initX * 0.04f
                zy = zy * 0.94f + initY * 0.04f
            }
            // Mix escape depth with orbit-trap brightness to reveal the folded bands.
            values[y * cols + x] = (if (escaped) escapedAt / 18f else 1f) * 0.72f +
                (1f - orbit.coerceIn(0f, 1f)) * 0.28f
        }
        paint(draw, audio)
    }
}
