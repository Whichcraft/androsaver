package com.androsaver.visualizer.modes

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw
import com.androsaver.visualizer.VisualizerRenderTuning
import kotlin.math.*

class FerrofluidMode : BaseMode() {
    override val name = "Ferrofluid"
    private var phase = 0f
    private var hue = 0f
    private val ring = FloatArray(34)
    override fun reset() { phase = 0f; hue = 0f }
    override fun draw(draw: GLDraw, audio: AudioData, tick: Int) {
        VisualizerRenderTuning.fadeTrail(draw)
        phase += 0.012f + audio.beat * 0.02f
        hue = (hue + 0.001f) % 1f
        val cx = draw.W * 0.5f; val cy = draw.H * 0.5f
        for (pole in 0 until 5) {
            val ang = phase * (if (pole % 2 == 0) 1f else -0.7f) + pole * 1.256f
            val px = cx + cos(ang) * draw.W * 0.24f
            val py = cy + sin(ang) * draw.H * 0.24f
            for (level in 1..5) {
                val radius = level * minOf(draw.W, draw.H) * 0.035f +
                    audio.beat * 8f * psysualsViewportScale(draw)
                for (i in 0..16) { val a = i / 16f * 2f * PI.toFloat(); ring[i * 2] = px + cos(a) * radius; ring[i * 2 + 1] = py + sin(a) * radius * 0.62f }
                val c = GLDraw.hsl((hue + pole * 0.13f + level * 0.03f) % 1f, 1f, 0.2f + level * 0.07f)
                draw.lineStrip(ring, 17, c[0], c[1], c[2], 0.38f)
            }
        }
    }
}
