package com.androsaver.visualizer.modes

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw
import kotlin.math.*

private const val TRAIL_FADE = 0.20f

private fun fadeTrail(draw: GLDraw) = draw.fadeBlack(TRAIL_FADE)

/** Scale fixed-size primitives against a 1080-pixel TV short edge. */
private fun viewportScale(draw: GLDraw): Float =
    (minOf(draw.W, draw.H).coerceAtLeast(1) / 1080f).coerceIn(0.5f, 2f)

/**
 * OpenGL ES adaptations of the v3.15 psysuals field effects.  The upstream
 * implementations render numpy/pygame surfaces; these versions keep the same
 * bounded field math and audio response while publishing the result as small
 * GL primitives so they remain safe on GLES 2.0 devices.
 */
abstract class ScalarFieldMode : BaseMode() {
    protected val cols = 24
    protected val rows = 16
    protected val values = FloatArray(cols * rows)
    protected val cell = FloatArray(8)
    protected var phase = 0f
    protected var hue = 0f

    protected fun clearTrail(draw: GLDraw) = draw.fadeBlack(TRAIL_FADE)

    /** Keep field frequencies proportional on wide and portrait viewports. */
    protected fun fieldX(x: Int, draw: GLDraw): Float =
        (x / (cols - 1f) - 0.5f) * (draw.W.toFloat() / draw.H.coerceAtLeast(1))

    protected fun fieldY(y: Int): Float = y / (rows - 1f) - 0.5f

    protected fun paint(draw: GLDraw, audio: AudioData, gain: Float = 1f) {
        val cw = draw.W.toFloat() / cols
        val ch = draw.H.toFloat() / rows
        for (y in 0 until rows) for (x in 0 until cols) {
            val v = values[y * cols + x].coerceIn(0f, 1f)
            val c = GLDraw.hsl((hue + v * 0.62f) % 1f, 1f,
                (0.12f + v * 0.48f + audio.beat * 0.12f).coerceIn(0f, 0.92f))
            draw.rect(x * cw, y * ch, cw + 1f, ch + 1f,
                c[0] * gain, c[1] * gain, c[2] * gain, 0.92f)
        }
    }

    override fun reset() {
        values.fill(0f)
        phase = 0f
        hue = 0f
    }
}

class MorphogenesisMode : ScalarFieldMode() {
    override val name = "Morphogenesis"
    private val u = FloatArray(cols * rows)
    private val v = FloatArray(cols * rows)
    private val nextU = FloatArray(cols * rows)
    private val nextV = FloatArray(cols * rows)

    override fun reset() {
        super.reset()
        u.fill(1f); v.fill(0f)
        for (y in rows / 2 - 1..rows / 2 + 1)
            for (x in cols / 2 - 1..cols / 2 + 1) v[y * cols + x] = 1f
    }

    override fun draw(draw: GLDraw, audio: AudioData, tick: Int) {
        clearTrail(draw)
        val feed = 0.028f + audio.treble * 0.002f
        val kill = 0.057f + audio.mid * 0.001f
        repeat(2) {
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
        phase += 0.01f + audio.mid * 0.01f
        hue = (hue + 0.001f + audio.treble * 0.002f) % 1f
        paint(draw, audio)
    }
}

class PhasonMode : ScalarFieldMode() {
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

class CymaticaMode : ScalarFieldMode() {
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
            draw.circle(x, y, (1.2f + audio.beat) * viewportScale(draw),
                0.75f, 0.9f, 1f, 0.7f, true, 5)
        }
        draw.setNormalBlend()
    }
}

class LiquidLightMode : ScalarFieldMode() {
    override val name = "LiquidLight"
    override fun draw(draw: GLDraw, audio: AudioData, tick: Int) {
        clearTrail(draw)
        phase += 0.014f + audio.mid * 0.02f
        hue = (hue + 0.0015f) % 1f
        for (y in 0 until rows) for (x in 0 until cols) {
            val nx = fieldX(x, draw)
            val ny = fieldY(y)
            val swirl = sin(nx * 7f + sin(ny * 5f + phase) * 2f + phase)
            val wave = cos(ny * 9f - cos(nx * 6f - phase) * 2f - phase * 0.8f)
            values[y * cols + x] = ((swirl + wave) * 0.25f + 0.5f).coerceIn(0f, 1f)
        }
        paint(draw, audio, 1.05f)
    }
}

class FerrofluidMode : BaseMode() {
    override val name = "Ferrofluid"
    private var phase = 0f
    private var hue = 0f
    private val ring = FloatArray(34)
    override fun reset() { phase = 0f; hue = 0f }
    override fun draw(draw: GLDraw, audio: AudioData, tick: Int) {
        fadeTrail(draw)
        phase += 0.012f + audio.beat * 0.02f
        hue = (hue + 0.001f) % 1f
        val cx = draw.W * 0.5f; val cy = draw.H * 0.5f
        for (pole in 0 until 5) {
            val ang = phase * (if (pole % 2 == 0) 1f else -0.7f) + pole * 1.256f
            val px = cx + cos(ang) * draw.W * 0.24f
            val py = cy + sin(ang) * draw.H * 0.24f
            for (level in 1..5) {
                val radius = level * minOf(draw.W, draw.H) * 0.035f +
                    audio.beat * 8f * viewportScale(draw)
                for (i in 0..16) { val a = i / 16f * 2f * PI.toFloat(); ring[i * 2] = px + cos(a) * radius; ring[i * 2 + 1] = py + sin(a) * radius * 0.62f }
                val c = GLDraw.hsl((hue + pole * 0.13f + level * 0.03f) % 1f, 1f, 0.2f + level * 0.07f)
                draw.lineStrip(ring, 17, c[0], c[1], c[2], 0.38f)
            }
        }
    }
}

class MandelboxMode : ScalarFieldMode() {
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

class HyperbolicMode : BaseMode() {
    override val name = "Hyperbolic"
    private var phase = 0f
    private var hue = 0f
    private val arc = FloatArray(18)
    override fun reset() { phase = 0f; hue = 0f }
    override fun draw(draw: GLDraw, audio: AudioData, tick: Int) {
        fadeTrail(draw)
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

class TesseractMode : BaseMode() {
    override val name = "Tesseract"
    private var phase = 0f
    private var hue = 0f
    private val pts = FloatArray(16 * 2)
    override fun reset() { phase = 0f; hue = 0f }
    override fun draw(draw: GLDraw, audio: AudioData, tick: Int) {
        fadeTrail(draw)
        phase += 0.01f + audio.beat * 0.025f; hue = (hue + 0.001f) % 1f
        val scale = minOf(draw.W, draw.H) * 0.27f
        for (i in 0 until 16) {
            var x = if (i and 1 == 0) -1f else 1f; var y = if (i and 2 == 0) -1f else 1f
            var z = if (i and 4 == 0) -1f else 1f; var w = if (i and 8 == 0) -1f else 1f
            val c = cos(phase); val s = sin(phase); val tx = x * c - w * s; w = x * s + w * c; x = tx
            val d = 3.4f + z * 0.4f + w * 0.35f
            pts[i * 2] = draw.W / 2f + x * scale / d
            pts[i * 2 + 1] = draw.H / 2f + y * scale / d
        }
        for (i in 0 until 16) for (bit in intArrayOf(1, 2, 4, 8)) if (i and bit == 0) {
            val j = i or bit
            if (j < 16) draw.line(pts[i * 2], pts[i * 2 + 1], pts[j * 2], pts[j * 2 + 1],
                0.35f, 0.7f, 1f, 0.38f + audio.beat * 0.22f)
        }
        for (i in 0 until 16) draw.circle(pts[i * 2], pts[i * 2 + 1], 2f + audio.beat * 2f,
            0.4f, 0.85f, 1f, 0.85f, true, 6)
    }
}
