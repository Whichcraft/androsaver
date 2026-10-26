package com.androsaver

import com.androsaver.visualizer.AudioData
import com.androsaver.visualizer.AudioEngine
import com.androsaver.visualizer.copyFrom
import org.junit.Assert.*
import org.junit.Test

class AudioSnapshotTest {
    @Test fun renderSnapshotOwnsItsArraysAndCopiesAllBands() {
        val capture = AudioData(floatArrayOf(0.2f), floatArrayOf(0.3f),
            beat = 0.4f, mid = 0.5f, treble = 0.6f, gain = 1.5f)
        val render = AudioData()
        render.waveform.fill(1f)
        render.fft.fill(1f)
        render.copyFrom(capture)
        capture.waveform[0] = 0.9f
        render.beat = 2f
        assertEquals(0.2f, render.waveform[0], 0f)
        assertEquals(0.3f, render.fft[0], 0f)
        assertEquals(0f, render.waveform[1], 0f)
        assertEquals(0f, render.fft[1], 0f)
        assertEquals(0.4f, capture.beat, 0f)
        assertEquals(0.5f, render.mid, 0f)
        assertEquals(0.6f, render.treble, 0f)
        assertEquals(1.5f, render.gain, 0f)
    }

    @Test fun pausingPreservesGenreConfigurationAndClearsAudio() {
        val audio = AudioEngine()
        audio.setGenreDetectionEnabled(true)
        audio.stop()
        assertTrue(audio.genreDetectionEnabled)
        val render = AudioData(beat = 1f)
        audio.copyDataTo(render)
        assertEquals(0f, render.beat, 0f)
        assertTrue(render.fft.all { it == 0f })
        audio.setGenreDetectionEnabled(false)
        audio.stop()
        assertFalse(audio.genreDetectionEnabled)
    }
}
