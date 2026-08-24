package com.androsaver.visualizer.modes

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw
import kotlin.math.*

class TesseractMode : BaseMode() {
    override val name = "Tesseract"
    private var phase = 0f
    private var hue = 0f
    private var scale = 1f
    private var scaleVelocity = 0f
    private var bounceY = 0f
    private var bounceVelocity = 0f
    private val pts = FloatArray(16 * 2)
    override fun reset() {
        phase = 0f; hue = 0f
        scale = 1f; scaleVelocity = 0f
        bounceY = 0f; bounceVelocity = 0f
    }
    override fun draw(draw: GLDraw, audio: AudioData, tick: Int) {
        fadePsysualsTrail(draw)
        val bass = audio.beat.coerceIn(0f, 1.5f)
        val mid = audio.mid.coerceIn(0f, 4f)
        val high = audio.treble.coerceIn(0f, 4f)
        phase += 0.01f + bass * 0.025f + mid * 0.001f + high * 0.001f
        hue = (hue + 0.001f + high * 0.001f) % 1f
        scaleVelocity += bass * 0.28f + (1f - scale) * 0.14f
        scaleVelocity *= 0.70f
        scale = (scale + scaleVelocity).coerceIn(0.62f, 1.28f)
        bounceVelocity += bass * 0.035f - bounceY * 0.08f
        bounceVelocity *= 0.88f
        bounceY = (bounceY + bounceVelocity).coerceIn(-0.22f, 0.22f)

        val focalScale = minOf(draw.W, draw.H) * 0.52f * scale
        for (i in 0 until 16) {
            var x = if (i and 1 == 0) -1f else 1f; var y = if (i and 2 == 0) -1f else 1f
            var z = if (i and 4 == 0) -1f else 1f; var w = if (i and 8 == 0) -1f else 1f
            val c = cos(phase); val s = sin(phase); val tx = x * c - w * s; w = x * s + w * c; x = tx
            val d = 3.4f + z * 0.4f + w * 0.35f
            pts[i * 2] = draw.W / 2f + x * focalScale / d
            pts[i * 2 + 1] = draw.H / 2f + (y * focalScale + bounceY * focalScale) / d
        }
        for (i in 0 until 16) for (bit in intArrayOf(1, 2, 4, 8)) if (i and bit == 0) {
            val j = i or bit
            if (j < 16) draw.line(pts[i * 2], pts[i * 2 + 1], pts[j * 2], pts[j * 2 + 1],
                0.35f, 0.7f, 1f, 0.38f + bass * 0.22f)
        }
        val nodeRadius = (2f + bass * 2f) * psysualsViewportScale(draw)
        for (i in 0 until 16) draw.circle(pts[i * 2], pts[i * 2 + 1], nodeRadius,
            0.4f, 0.85f, 1f, 0.85f, true, 6)
    }
}
