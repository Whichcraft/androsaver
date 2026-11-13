package com.androsaver

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.GLDraw
import com.androsaver.visualizer.modes.CausticsMode
import org.junit.Assert.*
import org.junit.Test

class CausticsModeTest {
    private val draw = GLDraw(320, 180)

    private fun frames(mode: CausticsMode, audio: AudioData, count: Int, beat: (Int) -> Float = { audio.beat }) {
        for (t in 0 until count) { audio.beat = beat(t); mode.advance(draw, audio) }
    }

    @Test fun nameIsCaustics() = assertEquals("Caustics", CausticsMode().name)

    @Test fun beatsDropBoundedRipplesThatFade() {
        val mode = CausticsMode()
        val audio = AudioData()
        var peak = 0
        for (t in 0 until 120) {
            audio.beat = if (t % 6 < 2) 1.2f else 0f
            mode.advance(draw, audio)
            peak = maxOf(peak, mode.rippleCount)
            assertTrue(mode.rippleCount <= CausticsMode.MAX_RIPPLES)
        }
        assertTrue(peak > 0)
        assertTrue(mode.rippleCount <= CausticsMode.MAX_RIPPLES)
        frames(mode, audio, 400) { 0f }
        assertEquals(0, mode.rippleCount)
    }

    @Test fun onlyRisingEdgeSpawnsRipple() {
        val mode = CausticsMode()
        val audio = AudioData()
        frames(mode, audio, 30) { 1.2f }
        assertEquals(1, mode.rippleCount)
        frames(mode, audio, 1) { 0f }
        frames(mode, audio, 1) { 1.2f }
        assertEquals(2, mode.rippleCount)
    }

    @Test fun silenceStillAnimatesWithFiniteUniforms() {
        val mode = CausticsMode()
        val audio = AudioData()
        frames(mode, audio, 1)
        val before = mode.phase.copyOf()
        frames(mode, audio, 40)
        assertTrue(mode.phase.indices.any { mode.phase[it] != before[it] })
        assertEquals(0, mode.rippleCount)
        assertTrue(mode.uniforms.values.all { v -> v.all { it.isFinite() } })
    }

    @Test fun phasesStayFiniteAndDoNotJumpWithAudio() {
        val mode = CausticsMode()
        val audio = AudioData(beat = 1.5f, mid = 1.5f, treble = 1.5f)
        val tau = 2f * Math.PI.toFloat()
        var prev = mode.phase.copyOf()
        for (t in 0 until 600) {
            audio.mid = if (t % 2 == 0) 1.5f else 0f
            audio.beat = if (t % 3 == 0) 1.5f else 0f
            mode.advance(draw, audio)
            for (i in prev.indices) {
                assertTrue(mode.phase[i].isFinite())
                assertTrue(mode.phase[i] in 0f..tau)
                var d = Math.abs(mode.phase[i] - prev[i])
                if (d > tau / 2) d = tau - d
                assertTrue("wave $i jumped by $d", d < .5f)
            }
            prev = mode.phase.copyOf()
        }
    }

    @Test fun zeroElapsedTimeFreezesPhases() {
        val mode = CausticsMode()
        val audio = AudioData()
        frames(mode, audio, 10)
        val before = mode.phase.copyOf()
        audio.deltaSeconds = 0f
        frames(mode, audio, 5)
        assertArrayEquals(before, mode.phase, 0f)
    }
}
