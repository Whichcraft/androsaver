package com.androsaver.visualizer

/** Snapshot of processed audio state passed to each visualizer mode every frame. */
data class AudioData(
    /** Normalized waveform samples in -1..1, 512 elements. */
    val waveform: FloatArray = FloatArray(512),
    /** Smoothed log-magnitude FFT bins (0..~1), 512 elements. */
    val fft: FloatArray = FloatArray(512),
    /** Smoothed bass energy (0..~1). Used directly as "beat" reactivity. */
    val beat: Float = 0f,
    /** Effect-gain multiplier applied to beat before this snapshot was created (default 1.0). */
    val gain: Float = 1f
)
