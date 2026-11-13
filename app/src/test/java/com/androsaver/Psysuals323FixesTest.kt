package com.androsaver

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw
import com.androsaver.visualizer.modes.AuroraMode
import com.androsaver.visualizer.modes.BarsMode
import com.androsaver.visualizer.modes.BubblesMode
import com.androsaver.visualizer.modes.ButterfliesMode
import com.androsaver.visualizer.modes.ChromaWeaveMode
import com.androsaver.visualizer.modes.FlowFieldMode
import com.androsaver.visualizer.modes.MagnetarMode
import com.androsaver.visualizer.modes.SpiralMode
import com.androsaver.visualizer.modes.WaterfallMode
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs

/** Contracts for the psysuals 3.23.0 effect bug fixes that apply to the Android port. */
class Psysuals323FixesTest {
    @Test fun magnetarReseedsParticlesReachingTheCoreAndSpreadsStackedOnes() {
        val mode = MagnetarMode()
        val w = 640f; val h = 360f; val cx = w / 2f; val cy = h / 2f; val half = h / 2f
        mode.px.fill(cx); mode.py.fill(cy)
        mode.reseedParticles(w, h, cx, cy, half)
        val core = half * 0.16f
        var stillInCore = 0
        for (i in mode.px.indices) {
            val dx = mode.px[i] - cx; val dy = mode.py[i] - cy
            if (dx * dx + dy * dy < core * core) stillInCore++
        }
        assertTrue("core particles start again elsewhere ($stillInCore left)", stillInCore < mode.px.size / 20)

        // A small share is re-seeded every frame even away from the core.
        mode.px.fill(10f); mode.py.fill(10f)
        mode.reseedParticles(w, h, cx, cy, half)
        val moved = mode.px.indices.count { mode.px[it] != 10f }
        assertTrue("some but few particles move ($moved)", moved in 1..(mode.px.size / 20))
    }

    @Test fun flowFieldReseedsASmallShareEveryFrame() {
        val mode = FlowFieldMode()
        mode.adjustParticles(0, 640f, 360f)
        mode.px.fill(5f); mode.py.fill(5f)
        repeat(200) { mode.reseedParticles(640f, 360f) }
        val spread = mode.px.indices.count { mode.px[it] != 5f }
        // 1 - (1 - 0.003)^200 is about 45 %.
        assertTrue("stacked particles spread out ($spread of ${mode.px.size})", spread > mode.px.size / 4)
        assertTrue(mode.px.all { it in 0f..640f } && mode.py.all { it in 0f..360f })
    }

    @Test fun chromaWeavePhaseAccumulatesInsteadOfScalingTotalTime() {
        val mode = ChromaWeaveMode()
        val draw = GLDraw(320, 240)
        val advance = ChromaWeaveMode::class.java.getDeclaredMethod("advance", GLDraw::class.java, AudioData::class.java).apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val uniforms = ChromaWeaveMode::class.java.getDeclaredField("uniforms").apply { isAccessible = true }.get(mode) as Map<String, FloatArray>
        val audio = AudioData(mid = 1f)
        repeat(20_000) { advance.invoke(mode, draw, audio) }
        val before = uniforms.getValue("u_weave")[0]
        assertTrue(before in 0f..(2f * PI.toFloat()))
        audio.mid = 0f
        advance.invoke(mode, draw, audio)
        val after = uniforms.getValue("u_weave")[0]
        val jump = abs(after - before).let { minOf(it, 2f * PI.toFloat() - it) }
        assertTrue("a mid change moves the phase by one frame step only ($jump)", jump < .05f)
        assertTrue(uniforms.values.all { it[0].isFinite() })
    }

    @Test fun butterflySizesSurviveResizeRoundTrips() {
        val mode = ButterfliesMode()
        var size = 1f
        repeat(5) {
            size *= mode.resizeSizeFactor(2f, 1f)                  // 2:1 wider
            size *= mode.resizeSizeFactor(.5f, 1f)                      // and back
            size *= mode.resizeSizeFactor(1.5f, .75f)
            size *= mode.resizeSizeFactor(1f / 1.5f, 1f / .75f)
        }
        assertEquals(1f, size, 1e-4f)
    }

    @Test fun bubblesStayBoundedUnderLoudHighGainBeats() {
        val draw = RecordingDraw(320, 240)
        val mode = BubblesMode(); mode.reset()
        val loud = AudioData(fft = FloatArray(512) { 40f }, beat = 40f, mid = 40f, treble = 40f)
        repeat(60) { mode.draw(draw, loud, it) }
        // Radius cap is min(W, H) / 2; the outermost ring is 2.3 times the bubble.
        assertTrue("largest ring ${draw.maxRadius}", draw.maxRadius <= 120f * 2.3f + 1f)
        assertTrue("vertex count per frame stays bounded (${draw.circles})", draw.circles / 60 <= 700 * 9)
    }

    @Test fun spectrumWaterfallSpiralAndBubblesTolerateShortAndEmptyArrays() {
        val draw = RecordingDraw(320, 240)
        val shapes = listOf(0, 1, 7, 100, 300)
        val bars = BarsMode(); bars.reset()
        val waterfall = WaterfallMode(); waterfall.reset()
        val spiral = SpiralMode(); spiral.reset()
        val bubbles = BubblesMode(); bubbles.reset()
        var tick = 0
        for (size in shapes) {
            val audio = AudioData(waveform = FloatArray(size), fft = FloatArray(size) { .5f }, beat = .5f, mid = .3f, treble = .3f)
            bars.draw(draw, audio, tick); waterfall.draw(draw, audio, tick)
            spiral.draw(draw, audio, tick); bubbles.draw(draw, audio, tick)
            tick++
        }
        assertTrue(draw.rects > 0)
    }

    @Test fun nonFiniteSpectrumValuesDoNotReachGeometry() {
        val draw = RecordingDraw(320, 240)
        val fft = FloatArray(512) { if (it % 3 == 0) Float.NaN else if (it % 3 == 1) Float.POSITIVE_INFINITY else .4f }
        val wave = FloatArray(512) { if (it % 2 == 0) Float.NaN else .2f }
        val audio = AudioData(waveform = wave, fft = fft, beat = .5f, mid = .3f, treble = .3f)
        val bars = BarsMode(); bars.reset()
        val waterfall = WaterfallMode(); waterfall.reset()
        val spiral = SpiralMode(); spiral.reset()
        repeat(3) {
            bars.draw(draw, audio, it); waterfall.draw(draw, audio, it); spiral.draw(draw, audio, it)
        }
        assertEquals(0, draw.nonFinite)
    }

    @Test fun auroraRendersOnVeryNarrowSurfaces() {
        for (width in 1..5) {
            val draw = RecordingDraw(width, 240)
            val mode = AuroraMode(); mode.reset()
            mode.draw(draw, AudioData(beat = .5f), 0)
            assertTrue("width $width draws ribbons", draw.polygons > 0)
            assertEquals(0, draw.nonFinite)
        }
    }

    private class RecordingDraw(width: Int, height: Int) : GLDraw(width, height) {
        var rects = 0; var polygons = 0; var circles = 0; var nonFinite = 0
        var maxRadius = 0f
        private fun check(vararg v: Float) { if (v.any { !it.isFinite() }) nonFinite++ }
        override fun setAdditiveBlend() {}
        override fun setNormalBlend() {}
        override fun rect(x: Float, y: Float, w: Float, h: Float, r: Float, g: Float, b: Float, a: Float) {
            rects++; check(x, y, w, h)
        }
        override fun line(x1: Float, y1: Float, x2: Float, y2: Float, r: Float, g: Float, b: Float, a: Float) { check(x1, y1, x2, y2) }
        override fun lineStrip(pts: FloatArray, pointCount: Int, r: Float, g: Float, b: Float, a: Float) {
            for (i in 0 until minOf(pointCount * 2, pts.size)) check(pts[i])
        }
        override fun circle(cx: Float, cy: Float, radius: Float, r: Float, g: Float, b: Float, a: Float, filled: Boolean, segments: Int) {
            circles++; check(cx, cy, radius)
            if (radius > maxRadius) maxRadius = radius
        }
        override fun antiAliasedLine(x1: Float, y1: Float, x2: Float, y2: Float, r: Float, g: Float, b: Float, a: Float, width: Float) {
            check(x1, y1, x2, y2)
        }
        override fun polygon(pts: FloatArray, pointCount: Int, r: Float, g: Float, b: Float, a: Float, filled: Boolean) {
            polygons++
            for (i in 0 until minOf(pointCount * 2, pts.size)) check(pts[i])
        }
    }
}
