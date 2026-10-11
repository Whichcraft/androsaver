package com.androsaver.visualizer.modes

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw
import kotlin.math.*

/** Poincare-disk tiling port of psysuals `effects/hyperbolic.py`. */
class HyperbolicMode : BaseMode() {
    override val name = "Hyperbolic"

    private data class Tile(val x: Float, val y: Float, val radius: Float, val generation: Int)
    private data class Point(val x: Float, val y: Float)

    private val tiles = ArrayList<Tile>(126).apply {
        for (generation in 0..5) {
            val count = 6 * (generation + 1)
            val radial = 0.10f + generation * 0.135f
            val radius = 0.105f / (1f + generation * 0.62f)
            for (index in 0 until count) {
                val angle = 2f * PI.toFloat() * index / count + generation * 0.13f
                add(Tile(cos(angle) * radial, sin(angle) * radial, radius, generation))
            }
        }
    }

    private var hue = 0f
    private var shock = 0f
    private var beatPrevious = 0f

    override fun reset() { hue = 0f; shock = 0f; beatPrevious = 0f }

    override fun draw(draw: GLDraw, audio: AudioData, tick: Int) {
        // The desktop effect paints a fresh dark disk each frame; it has no trail.
        draw.rect(0f, 0f, draw.W.toFloat(), draw.H.toFloat(), 2f / 255f, 1f / 255f, 8f / 255f, 1f)
        val bass = audio.beat.coerceIn(0f, 1.5f)
        val mid = audio.mid.coerceIn(0f, 4f)
        val high = audio.treble.coerceIn(0f, 4f)
        if (bass > 0.7f && beatPrevious <= 0.7f) shock = minOf(1f, shock + 0.65f)
        beatPrevious = bass
        shock *= 0.93f
        hue = (hue + 0.001f + mid * 0.0008f) % 1f

        val offsetRadius = minOf(0.28f, 0.035f + mid * 0.025f + shock * 0.07f + 0.01f)
        val offsetAngle = tick * 0.004f
        val ox = cos(offsetAngle) * offsetRadius
        val oy = sin(offsetAngle) * offsetRadius
        val cx = draw.W * 0.5f
        val cy = draw.H * 0.5f
        val scale = minOf(draw.W, draw.H) * 0.47f

        val disk = FloatArray(192)
        for (i in 0 until 96) {
            val a = i * (2f * PI.toFloat() / 96f)
            disk[i * 2] = cx + cos(a) * scale
            disk[i * 2 + 1] = cy + sin(a) * scale
        }
        draw.polygon(disk, 0.22f, 0.08f, 0.38f, 1f, filled = false)

        for (tile in tiles) {
            val phase = tick * (0.003f + tile.generation * 0.0007f) + tile.generation * 0.4f
            val points = ArrayList<Point>(31)
            var previous: Point? = null
            for (side in 0..6) {
                val angle = phase + 2f * PI.toFloat() * side / 6f
                val vx = tile.x + tile.radius * cos(angle)
                val vy = tile.y + tile.radius * sin(angle)
                val transformed = mobius(vx, vy, ox, oy) ?: continue
                if (side > 0 && previous != null) {
                    for (segment in 0 until 4) {
                        val t = segment / 4f
                        val x = previous.x * (1f - t) + transformed.x * t
                        val y = previous.y * (1f - t) + transformed.y * t
                        if (x * x + y * y < 1.1025f) points.add(Point(x, y))
                    }
                }
                previous = transformed
            }
            if (points.size < 2) continue
            val brightness = (0.30f + high * 0.08f + shock * 0.35f - tile.generation * 0.025f)
                .coerceIn(0.22f, 0.95f)
            val color = GLDraw.hsl((hue + tile.generation * 0.075f) % 1f, 1f, brightness)
            val pts = FloatArray(points.size * 2)
            for (i in points.indices) {
                pts[i * 2] = cx + points[i].x * scale
                pts[i * 2 + 1] = cy + points[i].y * scale
            }
            draw.lineStrip(pts, color[0], color[1], color[2], 1f)
        }
    }

    private fun mobius(x: Float, y: Float, ox: Float, oy: Float): Point? {
        // (z - offset) / (1 - conjugate(offset) * z)
        val numeratorX = x - ox
        val numeratorY = y - oy
        val denominatorX = 1f - (ox * x + oy * y)
        val denominatorY = oy * x - ox * y
        val denominator = denominatorX * denominatorX + denominatorY * denominatorY
        if (denominator < 1e-8f) return null
        return Point(
            (numeratorX * denominatorX + numeratorY * denominatorY) / denominator,
            (numeratorY * denominatorX - numeratorX * denominatorY) / denominator
        )
    }
}
