package com.androsaver.visualizer.modes

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw
import kotlin.math.*

class MandelboxMode : PsysualsFieldMode() {
    override val name = "Mandelbox"
    override fun draw(draw: GLDraw, audio: AudioData, tick: Int) {
        clearTrail(draw)
        phase += 0.008f + audio.beat * 0.02f
        hue = (hue + 0.001f) % 1f
        for (y in 0 until rows) for (x in 0 until cols) {
            var zx = fieldX(x, draw) * 2.8f
            var zy = fieldY(y) * 2.2f
            var escaped = 0
            for (i in 0 until 14) {
                zx = (2f * zx).coerceIn(-1f, 1f) - zx
                zy = (2f * zy).coerceIn(-1f, 1f) - zy
                val r2 = zx * zx + zy * zy
                if (r2 < 0.25f) { zx *= 4f; zy *= 4f } else if (r2 < 1f) { zx /= r2; zy /= r2 }
                zx += sin(phase) * 0.18f; zy += cos(phase * 0.8f) * 0.12f
                if (zx * zx + zy * zy > 16f) { escaped = i + 1; break }
            }
            values[y * cols + x] = (escaped / 14f).coerceIn(0f, 1f)
        }
        paint(draw, audio)
    }
}
