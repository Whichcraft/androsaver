package com.androsaver.visualizer.modes

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw
import com.androsaver.visualizer.VisualizerRenderTuning
import kotlin.math.*

class HyperbolicMode : BaseMode() {
    override val name = "Hyperbolic"
    private var phase = 0f
    private var hue = 0f
    private val arc = FloatArray(18)
    override fun reset() { phase = 0f; hue = 0f }
    override fun draw(draw: GLDraw, audio: AudioData, tick: Int) {
        VisualizerRenderTuning.fadeTrail(draw)
        phase += 0.01f + audio.mid * 0.018f; hue = (hue + 0.001f) % 1f
        val cx = draw.W / 2f; val cy = draw.H / 2f; val scale = minOf(draw.W, draw.H) * 0.46f
        for (ringIdx in 1..6) {
            val radius = scale * (ringIdx / 6f).pow(0.72f)
            for (i in 0..8) { val a = i / 8f * 2f * PI.toFloat() + phase * (if (ringIdx % 2 == 0) -1 else 1); arc[i * 2] = cx + cos(a) * radius; arc[i * 2 + 1] = cy + sin(a) * radius }
            val c = GLDraw.hsl((hue + ringIdx * 0.08f) % 1f, 1f, 0.18f + ringIdx * 0.06f)
            draw.lineStrip(arc, 9, c[0], c[1], c[2], 0.65f)
        }
        for (i in 0 until 12) {
            val a = phase * 0.5f + i * PI.toFloat() / 6f
            draw.line(cx, cy, cx + cos(a) * scale, cy + sin(a) * scale, 0.2f, 0.5f, 1f, 0.35f)
        }
    }
}
