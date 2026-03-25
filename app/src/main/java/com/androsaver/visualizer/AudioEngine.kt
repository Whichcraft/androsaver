package com.androsaver.visualizer

import android.media.audiofx.Visualizer
import android.util.Log
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.*

/**
 * Captures system audio via the Visualizer API (session 0 = global mix).
 * Processes waveform + FFT and computes beat energy matching the psysuals algorithm.
 *
 * Falls back to silent mode (all-zeros) if the Visualizer API is unavailable
 * (e.g. permission denied, no audio playing).
 */
class AudioEngine {

    companion object {
        private const val TAG = "VisualizerAudio"
        const val FFT_BINS = 512
    }

    private var visualizer: Visualizer? = null
    private val smoothFft = FloatArray(FFT_BINS)
    private val energyHistory = ArrayDeque<Float>()
    private val _data = AtomicReference(AudioData())

    // Latest waveform/fft bytes — combined when both arrive
    @Volatile private var lastWave: FloatArray? = null
    @Volatile private var lastFft: FloatArray? = null

    val data: AudioData get() = _data.get()

    fun start() {
        if (visualizer != null) return
        try {
            val maxCap = Visualizer.getCaptureSizeRange()[1].coerceAtMost(1024)
            visualizer = Visualizer(0).apply {
                captureSize = maxCap
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: Visualizer, bytes: ByteArray, rate: Int) {
                        lastWave = bytes.toWaveform()
                        publish()
                    }
                    override fun onFftDataCapture(v: Visualizer, bytes: ByteArray, rate: Int) {
                        lastFft = bytes.toFftMagnitude()
                        publish()
                    }
                }, Visualizer.getMaxCaptureRate() / 2, true, true)
                enabled = true
            }
            Log.d(TAG, "Visualizer started, captureSize=$maxCap")
        } catch (e: Exception) {
            Log.w(TAG, "Visualizer unavailable: ${e.message}")
        }
    }

    fun stop() {
        visualizer?.apply {
            try { enabled = false; release() } catch (_: Exception) {}
        }
        visualizer = null
    }

    private fun publish() {
        val wave = lastWave ?: return
        val rawFft = lastFft ?: return

        // Smooth FFT: 75% old + 25% new (matching Python _smooth_fft)
        for (i in 0 until minOf(rawFft.size, FFT_BINS)) {
            smoothFft[i] = smoothFft[i] * 0.75f + rawFft[i] * 0.25f
        }

        // Beat energy = mean of bass bins 0..19 (≈ 0–860 Hz with 512 bins at 44100 Hz)
        var bassSum = 0f
        for (i in 0 until 20) bassSum += smoothFft[i]
        val bassEnergy = bassSum / 20f

        // Rolling history for normalisation (not a hard threshold — matches Python behaviour)
        if (energyHistory.size >= 30) energyHistory.removeFirst()
        energyHistory.addLast(bassEnergy)
        val avgEnergy = energyHistory.average().toFloat()
        val beat = (bassEnergy / (avgEnergy + 0.001f) - 0.6f).coerceIn(0f, 1f)

        _data.set(AudioData(
            waveform = wave.copyInto(FloatArray(FFT_BINS)),
            fft = smoothFft.copyOf(),
            beat = beat
        ))
    }

    // ── Byte-array helpers ────────────────────────────────────────────────────

    /** Convert unsigned 8-bit waveform bytes to float -1..1. */
    private fun ByteArray.toWaveform(): FloatArray =
        FloatArray(minOf(size, FFT_BINS)) { i -> ((this[i].toInt() and 0xFF) - 128) / 128f }

    /**
     * Convert Android Visualizer FFT bytes to log-normalised magnitudes.
     * Format: fft[0]=DC real, fft[1]=Nyquist real, fft[2k]/fft[2k+1]=Re/Im for k=1..n/2-1.
     */
    private fun ByteArray.toFftMagnitude(): FloatArray {
        val bins = (size / 2).coerceAtMost(FFT_BINS)
        val mag = FloatArray(bins)
        // DC and Nyquist are real-only
        mag[0] = abs(this[0].toFloat()) / 128f
        if (bins > 1) mag[bins - 1] = abs(this[1].toFloat()) / 128f
        for (i in 1 until bins - 1) {
            val re = this[2 * i].toFloat()
            val im = if (2 * i + 1 < size) this[2 * i + 1].toFloat() else 0f
            val raw = sqrt(re * re + im * im) / 128f
            // log1p normalisation matching Python: log1p(spectrum) / 10
            mag[i] = ln(1f + raw * 10f) / 10f
        }
        return mag
    }
}
