package com.androsaver.visualizer

/** Snapshot of processed audio state passed to each visualizer mode every frame. */
data class AudioData(
    /** Normalized waveform samples in -1..1, 512 elements. */
    val waveform: FloatArray = FloatArray(512),
    /** Smoothed log-magnitude FFT bins (0..~1), 512 elements. */
    val fft: FloatArray = FloatArray(512),
    /** Normalized beat onset energy (0..~1). Bass-weighted spectral flux normalized by running average. */
    var beat: Float = 0f,
    /** Normalized mid-band energy (bins 20–99, ~860–4300 Hz), normalized by running average (~0..~1.5). */
    var mid: Float = 0f,
    /** Normalized treble-band energy (bins 100–255, ~4300–11000 Hz), normalized by running average (~0..~1.5). */
    var treble: Float = 0f,
    /** Effect-gain multiplier applied to beat before this snapshot was created (default 1.0). */
    var gain: Float = 1f
)

/** Allocation-free mean of a half-open FFT range for render-thread consumers. */
fun AudioData.fftMean(start: Int, end: Int): Float {
    val first = start.coerceIn(0, fft.size)
    val last = end.coerceIn(first, fft.size)
    if (last == first) return 0f
    var sum = 0f
    for (i in first until last) sum += fft[i]
    return sum / (last - first)
}
