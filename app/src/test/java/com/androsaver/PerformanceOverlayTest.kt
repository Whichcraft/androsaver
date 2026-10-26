package com.androsaver

import com.androsaver.visualizer.PerformanceOverlay
import com.androsaver.visualizer.VisualizerPerformance
import org.junit.Assert.*
import org.junit.Test

class PerformanceOverlayTest {
    @Test fun updatesAreLimitedToOnePerSecondAndClockResetDoesNotSuppressThem() {
        val overlay = PerformanceOverlay()
        assertTrue(overlay.shouldPublish(0))
        assertFalse(overlay.shouldPublish(500_000_000))
        assertTrue(overlay.shouldPublish(1_000_000_000))
        assertTrue(overlay.shouldPublish(0))
    }

    @Test fun displayIncludesViewportTimingGeometryBloomAndAllocatedStaging() {
        val sample = VisualizerPerformance("Nova", 1920, 1080, 20f, 1234, true, 12 * 1024 * 1024)
        val text = PerformanceOverlay().text(sample)
        listOf("Nova", "1920 x 1080", "20.0 ms", "50.0 FPS", "1234 vertices", "bloom on", "12.0 MiB").forEach {
            assertTrue(text.contains(it))
        }
        assertFalse(PerformanceOverlay().text(sample.copy(frameTimeMs = Float.NaN)).contains("NaN"))
    }
}
