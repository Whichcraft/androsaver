package com.androsaver.visualizer.modes

import kotlin.math.abs

/**
 * Spectrum band levels shared by modes that map the FFT onto a fixed number of
 * slots. Equivalent to the psysuals `spectrum_bands` helper: band edges are
 * squared so the low end gets more bands, and non-finite bins read as silence.
 */
internal object SpectrumLevels {
    /** Spectra shorter than this are too small to split and read as silence. */
    const val MIN_BINS = 8

    /** Share of the spectrum the bands cover; the top is mostly empty. */
    private const val COVERAGE = 0.6f

    /**
     * Fill [out] with the mean magnitude of [fft] in `out.size` bands. NaN and
     * infinite bins count as 0, and an empty or too short spectrum yields zeros.
     * Allocation-free: the caller owns [out].
     */
    fun bands(fft: FloatArray, out: FloatArray) {
        val count = out.size
        out.fill(0f)
        val n = fft.size
        if (count == 0 || n < MIN_BINS) return
        val span = n * COVERAGE
        for (k in 0 until count) {
            val a = k.toFloat() / count
            val b = (k + 1).toFloat() / count
            val lo = (a * a * span).toInt().coerceIn(0, n - 1)
            val hi = maxOf(lo + 1, (b * b * span).toInt()).coerceAtMost(n)
            var sum = 0f
            for (i in lo until hi) {
                val v = abs(fft[i])
                if (v.isFinite()) sum += v
            }
            out[k] = sum / (hi - lo)
        }
    }
}
