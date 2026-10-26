package com.androsaver

import com.androsaver.visualizer.AudioCaptureMonitor
import com.androsaver.visualizer.AudioEngine
import org.junit.Assert.*
import org.junit.Test

class AudioCaptureMonitorTest {
    @Test fun noSignalIsDifferentFromAnActiveSignalAndFromAnUnavailableBackend() {
        var now = 0L
        val state = AudioCaptureMonitor { now }
        state.starting(); state.attached(); state.sample(false)
        assertEquals(AudioCaptureMonitor.Status.NO_SIGNAL, state.status())
        state.sample(true)
        assertEquals(AudioCaptureMonitor.Status.ACTIVE, state.status())
        now += 4_000_000_000L
        assertEquals(AudioCaptureMonitor.Status.NO_SIGNAL, state.status())
        state.unavailable(); state.sample(true)
        assertEquals(AudioCaptureMonitor.Status.UNAVAILABLE, state.status())
        state.stopped()
        assertEquals(AudioCaptureMonitor.Status.STOPPED, state.status())
    }
    @Test fun missingPermissionDoesNotAttemptToCreateANativeVisualizer() {
        val audio = AudioEngine()
        audio.start(permissionGranted = false)
        assertEquals(AudioCaptureMonitor.Status.PERMISSION_REQUIRED, audio.captureStatus)
        audio.stop()
        assertEquals(AudioCaptureMonitor.Status.STOPPED, audio.captureStatus)
    }
}
