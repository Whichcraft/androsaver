package com.androsaver

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw
import com.androsaver.visualizer.modes.HorizonMode
import com.androsaver.visualizer.modes.SpectrumLevels
import org.junit.Assert.*
import org.junit.Test

/** Behaviour contracts for the psysuals 3.23 Horizon port and the shared spectrum helper. */
class HorizonModeTest {
    private val loud = FloatArray(512) { 1f - 0.8f * it / 511f }

    private fun newMode(): HorizonMode = HorizonMode().also { it.reset() }

    @Test fun silenceStillShowsATravellingLandscape() {
        val mode = newMode()
        val draw = RecordingDraw(320, 240)
        mode.draw(draw, AudioData(), 0)
        assertTrue("ridges, lanes and the sun are solid polygons", draw.polygons > 500)
        assertTrue("sky bands and stars are rects", draw.rects > 100)
        val start = mode.time
        for (tick in 1 until 30) mode.draw(draw, AudioData(), tick)
        assertTrue(mode.time > start)
        assertTrue(mode.ridgeRows.max() > 0.1f)
    }

    @Test fun valleyStaysFlatAndMusicRaisesNewRidges() {
        val quiet = newMode(); val busy = newMode()
        val draw = RecordingDraw(320, 240)
        for (tick in 0 until 2000) {
            quiet.draw(draw, AudioData(), tick)
            busy.draw(draw, AudioData(fft = loud.copyOf(), mid = .4f, treble = .6f), tick)
        }
        val centre = HorizonMode.COLS / 2
        for (row in 0 until HorizonMode.ROW_SLOTS) {
            assertEquals(0f, busy.ridgeRows[row * HorizonMode.COLS + centre - 1], 0f)
            assertEquals(0f, busy.ridgeRows[row * HorizonMode.COLS + centre], 0f)
        }
        assertTrue(busy.ridgeRows.average() > quiet.ridgeRows.average() * 1.5)
    }

    @Test fun aRowKeepsItsShapeWhileItTravels() {
        val mode = newMode()
        val draw = RecordingDraw(320, 240)
        mode.draw(draw, AudioData(fft = loud.copyOf()), 0)
        val newest = mode.newestRow
        val start = (newest % HorizonMode.ROW_SLOTS).toInt() * HorizonMode.COLS
        val shape = mode.ridgeRows.copyOfRange(start, start + HorizonMode.COLS)
        for (tick in 1 until 120) mode.draw(draw, AudioData(), tick)
        assertTrue(mode.newestRow > newest)
        assertTrue(mode.newestRow - newest < HorizonMode.N_ROWS)
        assertArrayEquals(shape, mode.ridgeRows.copyOfRange(start, start + HorizonMode.COLS), 0f)
    }

    @Test fun rideFollowsBassAndRowsStayBoundedAfterAJump() {
        val quiet = newMode(); val driven = newMode()
        val draw = RecordingDraw(320, 240)
        for (tick in 0 until 30) {
            quiet.draw(draw, AudioData(), tick)
            driven.draw(draw, AudioData(beat = 1.5f), tick)
        }
        assertTrue(driven.time > quiet.time)
        driven.time += 10_000.0
        driven.draw(draw, AudioData(fft = loud.copyOf(), beat = 6f), 31)
        assertTrue(driven.newestRow > 10_000L)
        for (v in driven.ridgeRows) assertTrue(v.isFinite())
        assertEquals(HorizonMode.ROW_SLOTS * HorizonMode.COLS, driven.ridgeRows.size)
    }

    @Test fun nonFiniteEmptyAndShortSpectraRenderAsSilence() {
        val bad = loud.copyOf()
        bad[3] = Float.POSITIVE_INFINITY; bad[40] = Float.NEGATIVE_INFINITY; bad[41] = Float.NaN
        val spectra = listOf(bad, FloatArray(0), FloatArray(5) { 1f },
            FloatArray(512) { Float.POSITIVE_INFINITY }, FloatArray(512) { Float.NaN })
        for (fft in spectra) {
            val mode = newMode()
            val draw = RecordingDraw(320, 240)
            for (tick in 0 until 20) {
                mode.draw(draw, AudioData(fft = fft, beat = Float.NaN, mid = Float.POSITIVE_INFINITY), tick)
            }
            assertTrue(draw.polygons > 0)
            for (v in mode.levels) assertTrue(v.isFinite() && v in 0f..1.0001f)
        }
    }

    @Test fun spectrumLevelsReadNonFiniteBinsAsSilence() {
        val bad = loud.copyOf()
        bad[3] = Float.POSITIVE_INFINITY; bad[40] = Float.NEGATIVE_INFINITY; bad[41] = Float.NaN
        val out = FloatArray(22)
        SpectrumLevels.bands(bad, out)
        for (v in out) assertTrue(v.isFinite() && v >= 0f && v <= 1f)

        val infinite = FloatArray(6) { 9f }
        SpectrumLevels.bands(FloatArray(64) { Float.POSITIVE_INFINITY }, infinite)
        for (v in infinite) assertEquals(0f, v, 0f)

        val short = FloatArray(6) { 9f }
        SpectrumLevels.bands(FloatArray(SpectrumLevels.MIN_BINS - 1) { 1f }, short)
        for (v in short) assertEquals(0f, v, 0f)
        SpectrumLevels.bands(FloatArray(0), short)
        for (v in short) assertEquals(0f, v, 0f)

        val flat = FloatArray(22)
        SpectrumLevels.bands(FloatArray(512) { 0.5f }, flat)
        for (v in flat) assertEquals(0.5f, v, 1e-5f)
    }

    @Test fun oddViewportsAndExtremeBeatStayFiniteAndBounded() {
        for ((w, h) in listOf(400 to 1200, 1200 to 400, 16 to 16, 1200 to 300)) {
            val mode = newMode()
            val draw = RecordingDraw(w, h)
            mode.draw(draw, AudioData(fft = FloatArray(0), beat = 6f), 0)
            for (tick in 1 until 60) mode.draw(draw, AudioData(fft = loud.copyOf(), beat = 1.2f), tick)
            assertTrue("$w x $h draws geometry", draw.polygons > 0)
            val limit = 10f * maxOf(w, h)
            assertTrue(draw.minimumX > -limit && draw.maximumX < limit)
            assertTrue(draw.minimumY > -limit && draw.maximumY < limit)
            assertTrue("terrain reaches both side edges at $w x $h",
                draw.minimumX <= 0f && draw.maximumX >= w.toFloat())
        }
    }

    @Test fun twinkleFollowsElapsedTimeNotTheTickCounter() {
        fun stars(delta: Float, tick: Int): List<Float> {
            val mode = newMode()
            val draw = RecordingDraw(320, 240)
            mode.draw(draw, AudioData(deltaSeconds = delta), tick)
            return draw.stars
        }
        val reference = stars(1f / 60f, 0)
        assertEquals(HorizonMode.N_STARS * 2, reference.size)
        assertEquals("the tick counter does not matter", reference, stars(1f / 60f, 4321))
        assertNotEquals("elapsed time does", reference, stars(1f / 30f, 0))

        val frames = newMode(); val slow = newMode()
        val draw = RecordingDraw(320, 240)
        for (tick in 0 until 60) frames.draw(draw, AudioData(deltaSeconds = 1f / 60f), tick)
        for (tick in 0 until 30) slow.draw(draw, AudioData(deltaSeconds = 1f / 30f), tick)
        assertEquals("one second is one second at any frame rate", frames.clock, slow.clock, 1e-3)
    }

    private class RecordingDraw(width: Int, height: Int) : GLDraw(width, height) {
        var minimumX = Float.POSITIVE_INFINITY; var minimumY = Float.POSITIVE_INFINITY
        var maximumX = Float.NEGATIVE_INFINITY; var maximumY = Float.NEGATIVE_INFINITY
        var polygons = 0
        var rects = 0
        /** Red and green of every star-sized rect, in draw order. */
        val stars = ArrayList<Float>()

        private fun track(x: Float, y: Float) {
            assertTrue(x.isFinite() && y.isFinite())
            minimumX = minOf(minimumX, x); maximumX = maxOf(maximumX, x)
            minimumY = minOf(minimumY, y); maximumY = maxOf(maximumY, y)
        }

        override fun setAdditiveBlend() {}
        override fun setNormalBlend() {}
        override fun line(x1: Float, y1: Float, x2: Float, y2: Float, r: Float, g: Float, b: Float, a: Float) {}
        override fun antiAliasedLine(x1: Float, y1: Float, x2: Float, y2: Float, r: Float, g: Float, b: Float, a: Float, width: Float) {}
        override fun circle(cx: Float, cy: Float, radius: Float, r: Float, g: Float, b: Float, a: Float, filled: Boolean, segments: Int) {
            assertTrue(cx.isFinite() && cy.isFinite() && radius.isFinite())
            assertTrue(r.isFinite() && g.isFinite() && b.isFinite())
        }
        override fun rect(x: Float, y: Float, w: Float, h: Float, r: Float, g: Float, b: Float, a: Float) {
            rects++
            assertTrue(x.isFinite() && y.isFinite() && w.isFinite() && h.isFinite())
            assertTrue(r.isFinite() && g.isFinite() && b.isFinite())
            if (w == h && w <= 2f) { stars.add(r); stars.add(g) }
        }
        override fun polygon(pts: FloatArray, pointCount: Int, r: Float, g: Float, b: Float, a: Float, filled: Boolean) {
            polygons++
            assertTrue(r.isFinite() && g.isFinite() && b.isFinite())
            for (i in 0 until pointCount) track(pts[i * 2], pts[i * 2 + 1])
        }
    }
}
