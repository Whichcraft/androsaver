package com.androsaver

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw
import com.androsaver.visualizer.modes.LanternsMode
import org.junit.Assert.*
import org.junit.Test

/** Behaviour contracts for the psysuals Lanterns port. */
class LanternsModeTest {

    @Test fun lanternsRiseAndBeatsReleaseMoreOnTheRisingEdgeOnly() {
        val mode = LanternsMode(); mode.reset()
        val draw = RecordingDraw(320, 240)
        val start = mode.meanHeight()
        val initial = mode.activeLanterns
        assertTrue(initial > 0)
        for (tick in 0 until 30) mode.draw(draw, AudioData(), tick)
        assertEquals("no release or loss within half a second of silence", initial, mode.activeLanterns)
        assertTrue(mode.meanHeight() > start)
        val before = mode.activeLanterns
        mode.draw(draw, AudioData(beat = 1.4f), 30)
        val released = mode.activeLanterns - before
        assertTrue("a beat releases lanterns", released >= 1)
        for (tick in 31 until 40) mode.draw(draw, AudioData(beat = 1.4f), tick)
        assertEquals("a held beat does not release again", before + released, mode.activeLanterns)
    }

    @Test fun lanternAndSparkPoolsStayBounded() {
        val mode = LanternsMode(); mode.reset()
        val draw = RecordingDraw(320, 240)
        for (tick in 0 until 900) {
            val beat = if (tick % 4 < 2) 1.5f else 0f
            mode.draw(draw, AudioData(beat = beat, mid = 1.5f, treble = 1.5f), tick)
            assertTrue(mode.activeLanterns <= LanternsMode.MAX_LANTERNS)
            assertTrue(mode.activeSparks <= LanternsMode.MAX_SPARKS)
        }
        assertTrue("treble sends sparks up", mode.activeSparks > 0)
    }

    @Test fun silenceStillRendersTheSceneAndAStream() {
        val mode = LanternsMode(); mode.reset()
        val draw = RecordingDraw(320, 240)
        mode.draw(draw, AudioData(), 0)
        assertTrue("lantern bodies are drawn", draw.polygons > 0)
        assertTrue("glows are drawn", draw.circles > 0)
        assertTrue("sky and water bands are drawn", draw.rects > 20)
    }

    @Test fun reflectionsAreDrawnBelowTheHorizon() {
        val mode = LanternsMode(); mode.reset()
        val draw = RecordingDraw(320, 240)
        mode.draw(draw, AudioData(treble = .5f), 0)
        val horizon = 240 * LanternsMode.HORIZON
        assertTrue("shimmering strokes appear in the water", draw.additiveRectsBelow(horizon) > 0)
        assertEquals("no reflection starts below the frame", 0, draw.additiveRectsBelow(240f))
    }

    @Test fun survivesResizeAndDegenerateAudio() {
        val mode = LanternsMode(); mode.reset()
        val sizes = arrayOf(320 to 240, 400 to 1200, 1200 to 400, 16 to 16, 1 to 1, 97 to 53)
        var tick = 0
        for ((w, h) in sizes) {
            val draw = RecordingDraw(w, h)
            mode.draw(draw, AudioData(beat = 6f, mid = 3f, treble = 3f), tick++)
            mode.draw(draw, AudioData(fft = FloatArray(0), waveform = FloatArray(0)), tick++)
            mode.draw(draw, AudioData(fft = FloatArray(3)), tick++)
            mode.draw(draw, AudioData(fft = FloatArray(512) { Float.NaN }, beat = Float.NaN,
                mid = Float.NaN, treble = Float.NaN, deltaSeconds = Float.NaN), tick++)
            assertTrue(mode.activeLanterns in 0..LanternsMode.MAX_LANTERNS)
            assertTrue(draw.rects > 0)
        }
    }

    private class RecordingDraw(width: Int, height: Int) : GLDraw(width, height) {
        var polygons = 0
        var circles = 0
        var rects = 0
        private var additive = false
        private var additiveRectTops = FloatArray(4096)
        private var additiveRectCount = 0

        fun additiveRectsBelow(horizon: Float): Int {
            var n = 0
            for (i in 0 until additiveRectCount) if (additiveRectTops[i] > horizon) n++
            return n
        }

        private fun finite(vararg v: Float) { for (f in v) assertTrue("non-finite draw argument", f.isFinite()) }

        override fun setAdditiveBlend() { additive = true }
        override fun setNormalBlend() { additive = false }
        override fun rect(x: Float, y: Float, w: Float, h: Float, r: Float, g: Float, b: Float, a: Float) {
            finite(x, y, w, h, r, g, b, a)
            rects++
            if (additive && additiveRectCount < additiveRectTops.size) additiveRectTops[additiveRectCount++] = y
        }
        override fun line(x1: Float, y1: Float, x2: Float, y2: Float, r: Float, g: Float, b: Float, a: Float) {
            finite(x1, y1, x2, y2, r, g, b, a)
        }
        override fun circle(cx: Float, cy: Float, radius: Float, r: Float, g: Float, b: Float, a: Float, filled: Boolean, segments: Int) {
            finite(cx, cy, radius, r, g, b, a)
            circles++
        }
        override fun polygon(pts: FloatArray, pointCount: Int, r: Float, g: Float, b: Float, a: Float, filled: Boolean) {
            finite(r, g, b, a)
            polygons++
            for (i in 0 until pointCount) finite(pts[i * 2], pts[i * 2 + 1])
        }
    }
}
