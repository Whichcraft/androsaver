package com.androsaver

import com.androsaver.visualizer.AudioBandLevel
import org.junit.Assert.*
import org.junit.Test
import java.util.Random
import kotlin.math.exp
import kotlin.math.ln

/** Mirrors the psysuals 3.23 BandLevel contracts from tests/test_bug_sweep.py. */
class AudioBandLevelTest {
    private val blocksPerSecond = 44100f / 1024f

    private class Part(val label: String, val low: Float, val high: Float, val seconds: Int, val noise: Boolean)

    /** Ratios per part; room noise is log-normal around 0.002 like a laptop microphone. */
    private fun run(level: AudioBandLevel, rng: Random, vararg parts: Part): Map<String, FloatArray> =
        parts.associate { part ->
            val blocks = (part.seconds * blocksPerSecond).toInt()
            part.label to FloatArray(blocks) {
                val value = if (part.noise) exp(ln(.002) + rng.nextGaussian() * ln(1.8)).toFloat()
                            else part.low + rng.nextFloat() * (part.high - part.low)
                level.update(value)
                level.ratio
            }
        }

    private fun FloatArray.median(): Float = sorted().let { it[it.size / 2] }
    private fun FloatArray.tail(n: Int) = copyOfRange(maxOf(0, size - n), size)

    @Test fun quietMusicPauseHoldsAndResumesCleanly() {
        val out = run(AudioBandLevel(blocksPerSecond), Random(1),
            Part("music", .010f, .021f, 15, false), Part("pause", 0f, 0f, 120, true),
            Part("resume", .010f, .021f, 10, false))
        assertTrue("room noise must not read as music", out.getValue("pause").tail(200).median() < .3f)
        assertTrue("returning music must not spike", out.getValue("resume").max() < 3f)
        assertEquals(1f, out.getValue("resume").median(), .15f)
    }

    @Test fun quieterPassageIsFollowedOnceTheRoomWasHeard() {
        val out = run(AudioBandLevel(blocksPerSecond), Random(2),
            Part("room", 0f, 0f, 10, true), Part("music", .015f, .025f, 15, false),
            Part("quiet", .004f, .006f, 30, false))
        assertTrue(out.getValue("quiet").tail(200).median() > .8f)
    }

    @Test fun averageCatchesUpAfterASilentStartAndStaysClipped() {
        val out = run(AudioBandLevel(blocksPerSecond), Random(3),
            Part("silence", 0f, 0f, 10, true), Part("music", .010f, .021f, 10, false))
        assertTrue(out.getValue("music").copyOfRange(43, out.getValue("music").size).max() < 3f)
        val single = AudioBandLevel(1f)
        repeat(4) { single.update(.01f) }
        assertTrue(single.update(1e6f) <= AudioBandLevel.MAX_RATIO - 1f)
    }

    @Test fun startUpTransientDoesNotSeedTheAverage() {
        val level = AudioBandLevel(blocksPerSecond)
        val rng = Random(4)
        level.update(.07f)
        run(level, rng, Part("room", 0f, 0f, 20, true))
        val music = run(level, rng, Part("music", .010f, .021f, 3, false)).getValue("music")
        assertEquals(1f, music.copyOfRange(43, music.size).median(), .3f)
    }

    @Test fun nonFiniteInputReadsAsSilenceAndResetForgetsTheBand() {
        val level = AudioBandLevel(blocksPerSecond)
        repeat(100) { level.update(.02f) }
        assertTrue(level.update(Float.NaN).isFinite())
        assertTrue(level.update(Float.POSITIVE_INFINITY).isFinite())
        assertTrue(level.average.isFinite() && level.average > 0f)
        level.reset()
        assertTrue(level.average < 0f)
        assertEquals("nothing is reported before the first second", 0f, level.update(.5f), 0f)
    }
}
