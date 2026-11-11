package com.androsaver.visualizer.modes

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw
import kotlin.math.*

/** Fixed normalized simulation coordinates preserve phase/state across surface resizes. */
class LiquidLightMode : PsysualsFieldMode() {
    override val name = "LiquidLight"
    private val rowSine=FloatArray(rows)
    private val columnCosine=FloatArray(cols)
    override fun draw(draw: GLDraw, audio: AudioData, tick: Int) {
        clearTrail(draw)
        phase += 0.014f + audio.mid * 0.02f
        hue = (hue + 0.0015f) % 1f
        for(y in 0 until rows)rowSine[y]=sin(fieldY(y)*5f+phase)*2f
        for(x in 0 until cols)columnCosine[x]=cos(fieldX(x,draw)*6f-phase)*2f
        for (y in 0 until rows) for (x in 0 until cols) {
            val nx = fieldX(x, draw)
            val ny = fieldY(y)
            val swirl = sin(nx * 7f + rowSine[y] + phase)
            val wave = cos(ny * 9f - columnCosine[x] - phase * 0.8f)
            values[y * cols + x] = ((swirl + wave) * 0.25f + 0.5f).coerceIn(0f, 1f)
        }
        paint(draw, audio, 1.05f)
    }
}
