package com.androsaver

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw
import com.androsaver.visualizer.modes.ChromaticMode
import com.androsaver.visualizer.modes.CorridorMode
import com.androsaver.visualizer.modes.MobiusMode
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

/** Behaviour contracts for the psysuals 3.22 Corridor, Mobius and Chromatic ports. */
class Psysuals322Test {
    @Test fun corridorPaintsSolidGeometryAndBeatPulsesRunAway() {
        val draw = RecordingDraw(320, 240)
        val mode = CorridorMode(); mode.reset()
        mode.draw(draw, AudioData(beat = 1.2f, mid = .4f, treble = .6f), 0)
        assertEquals(1, mode.activePulses)
        assertTrue("panels, lights and ribs fill the corridor", draw.polygons > 100)
        for (tick in 1..6) mode.draw(draw, AudioData(beat = 1.2f, mid = .4f, treble = .6f), tick)
        assertEquals("only rising beat edges launch a pulse", 1, mode.activePulses)
        for (tick in 7..66) mode.draw(draw, AudioData(mid = .4f, treble = .6f), tick)
        assertEquals(0, mode.activePulses)
    }

    @Test fun corridorRideFollowsBassAndSurvivesOddViewports() {
        val quiet = CorridorMode(); val loud = CorridorMode()
        quiet.reset(); loud.reset()
        val draw = RecordingDraw(320, 240)
        for (tick in 0 until 30) {
            quiet.draw(draw, AudioData(), tick)
            loud.draw(draw, AudioData(beat = 1.5f), tick)
        }
        assertTrue(loud.travelled > quiet.travelled)
        loud.draw(RecordingDraw(400, 1200), AudioData(beat = 6f), 31)
        loud.draw(RecordingDraw(1200, 400), AudioData(beat = 6f), 32)
    }

    @Test fun mobiusStaysBroadAndInsideTheViewport() {
        val draw = RecordingDraw(320, 240)
        val mode = MobiusMode(); mode.reset()
        for (tick in 0 until 900) {
            mode.draw(draw, AudioData(beat = .5f, mid = .4f, treble = .6f), tick)
            assertTrue("never edge-on horizontally at tick $tick", mode.spanX > 320 * .35f)
            assertTrue("never edge-on vertically at tick $tick", mode.spanY > 240 * .25f)
        }
        assertTrue(draw.minimumX >= 320 * .04f - 1f); assertTrue(draw.maximumX <= 320 * .96f + 1f)
        assertTrue(draw.minimumY >= 240 * .04f - 1f); assertTrue(draw.maximumY <= 240 * .96f + 1f)
    }

    @Test fun chromaticDropsSpreadOverTheFrameAndStayCapped() {
        val mode = ChromaticMode(); mode.reset()
        repeat(20) { mode.spawn(.5f, 320f, 240f) }
        val rings = mode.ringGeometry()
        assertEquals("the Android cap is nine drops", 9 * 3, rings.size)
        var spreadX = 0f; var spreadY = 0f
        for (i in 0 until 9) {
            spreadX = maxOf(spreadX, abs(rings[i * 3] - 160f))
            spreadY = maxOf(spreadY, abs(rings[i * 3 + 1] - 120f))
        }
        assertTrue(spreadX > 40f); assertTrue(spreadY > 30f)

        val draw = RecordingDraw(320, 240)
        for (tick in 0 until 240) {
            val beat = if (tick % 20 < 3) 1.2f else .1f
            mode.draw(draw, AudioData(beat = beat, mid = .4f, treble = .6f), tick)
            assertTrue(mode.ringGeometry().size <= 9 * 3)
        }
        assertTrue(draw.polygons > 0)
    }

    private class RecordingDraw(width: Int, height: Int) : GLDraw(width, height) {
        var minimumX = Float.POSITIVE_INFINITY; var minimumY = Float.POSITIVE_INFINITY
        var maximumX = Float.NEGATIVE_INFINITY; var maximumY = Float.NEGATIVE_INFINITY
        var polygons = 0
        override fun setAdditiveBlend() {}
        override fun setNormalBlend() {}
        override fun rect(x: Float, y: Float, w: Float, h: Float, r: Float, g: Float, b: Float, a: Float) {}
        override fun line(x1: Float, y1: Float, x2: Float, y2: Float, r: Float, g: Float, b: Float, a: Float) {}
        override fun circle(cx: Float, cy: Float, radius: Float, r: Float, g: Float, b: Float, a: Float, filled: Boolean, segments: Int) {}
        override fun antiAliasedLine(x1: Float, y1: Float, x2: Float, y2: Float, r: Float, g: Float, b: Float, a: Float, width: Float) {
            assertTrue(x1.isFinite() && y1.isFinite() && x2.isFinite() && y2.isFinite())
        }
        override fun polygon(pts: FloatArray, pointCount: Int, r: Float, g: Float, b: Float, a: Float, filled: Boolean) {
            polygons++
            assertTrue(r.isFinite() && g.isFinite() && b.isFinite())
            for (i in 0 until pointCount) {
                assertTrue(pts[i * 2].isFinite()); assertTrue(pts[i * 2 + 1].isFinite())
                minimumX = minOf(minimumX, pts[i * 2]); maximumX = maxOf(maximumX, pts[i * 2])
                minimumY = minOf(minimumY, pts[i * 2 + 1]); maximumY = maxOf(maximumY, pts[i * 2 + 1])
            }
        }
    }
}
