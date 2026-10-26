package com.androsaver.visualizer

import java.util.Locale

/** GL-thread throttle; the host dispatches accepted immutable samples to Main. */
internal class PerformanceOverlay {
    private var lastSampleNs: Long? = null

    fun shouldPublish(nowNs: Long): Boolean {
        val last = lastSampleNs
        if (last != null && nowNs >= last && nowNs - last < 1_000_000_000L) return false
        lastSampleNs = nowNs
        return true
    }

    fun text(sample: VisualizerPerformance): String {
        val ms = sample.frameTimeMs.takeIf { it.isFinite() && it > 0f } ?: 0f
        val fps = if (ms > 0f) 1000f / ms else 0f
        return String.format(Locale.US, "%s | %d x %d\n%.1f ms | %.1f FPS\n%d vertices | bloom %s\n%.1f MiB vertex staging",
            sample.mode, sample.width, sample.height, ms, fps, sample.submittedVertices,
            if (sample.bloomEnabled) "on" else "off", sample.vertexStagingBytes / (1024f * 1024f))
    }
}
