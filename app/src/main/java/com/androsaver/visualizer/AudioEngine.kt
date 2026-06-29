package com.androsaver.visualizer

import android.media.audiofx.Visualizer
import android.util.Log
import com.androsaver.BuildConfig
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
        private const val DETECT_MIN_FRAMES = 300   // ~20 s at max capture rate
        private const val DETECT_SUB_BINS   = 5     // bins 0-4  (~0-215 Hz at 44100/1024)
        private const val DETECT_BASS_BINS  = 15    // bins 0-14 (~0-645 Hz)
        private const val DETECT_MID_BINS   = 100   // bins 15-99 (~645-4300 Hz)
    }

    private var visualizer: Visualizer? = null
    private val smoothFft = FloatArray(FFT_BINS)
    private val genreWeights = FloatArray(FFT_BINS) { 1f }
    private val energyHistory = ArrayDeque<Float>()
    private var energySum = 0.0          // running sum for O(1) average
    private var midAvg    = 0.1f         // warm-start to avoid first-frame spikes
    private var trebleAvg = 0.1f         // warm-start to avoid first-frame spikes
    private val _data = AtomicReference(AudioData())

    // Long-term accumulator for genre detection
    private val detectAccum = FloatArray(FFT_BINS)
    private var detectFrames = 0

    // Latest waveform/fft bytes — combined when both arrive
    @Volatile private var lastWave: FloatArray? = null
    @Volatile private var lastFft: FloatArray? = null
    private var firstFrame = true
    private var fftPrimed = false

    val data: AudioData get() = _data.get()

    fun start() {
        if (visualizer != null) return
        try {
            val maxCap = Visualizer.getCaptureSizeRange()[1].coerceAtMost(1024)
            val v = Visualizer(0)
            try {
                v.captureSize = maxCap
                v.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(vis: Visualizer, bytes: ByteArray, rate: Int) {
                        lastWave = bytes.toWaveform()
                        publish()
                    }
                    override fun onFftDataCapture(vis: Visualizer, bytes: ByteArray, rate: Int) {
                        lastFft = bytes.toFftMagnitude()
                        publish()
                    }
                }, Visualizer.getMaxCaptureRate(), true, true)
                v.enabled = true
                visualizer = v
            } catch (e: Exception) {
                try { v.release() } catch (_: Exception) {}
                throw e
            }
            if (BuildConfig.DEBUG_LOGGING) Log.d(TAG, "Visualizer started, captureSize=$maxCap")
        } catch (e: Exception) {
            if (BuildConfig.DEBUG_LOGGING) Log.w(TAG, "Visualizer unavailable: ${e.message}")
        }
    }

    fun applyGenreHint(genre: String) {
        synchronized(this) {
            genreWeights.fill(1f)
            val n = genreWeights.size
            when (genre) {
                "electronic" -> {
                    for (i in 0 until (n / 4)) genreWeights[i] = 1.5f
                    for (i in (n / 2) until n) genreWeights[i] = 0.7f
                }
                "rock" -> {
                    val start = n / 8
                    val end = (n / 3).coerceAtMost(n)
                    for (i in start until end) genreWeights[i] = 1.3f
                }
                "classical" -> {
                    for (i in 0 until (n / 4)) genreWeights[i] = 0.6f
                    for (i in (n / 4) until n) genreWeights[i] = 1.4f
                }
            }
        }
    }

    /**
     * Returns a detected genre string once enough frames have been accumulated,
     * or null if still collecting data. Resets the accumulator after each detection
     * so the genre can adapt if the music changes.
     */
    fun detectGenre(): String? {
        synchronized(this) {
            if (detectFrames < DETECT_MIN_FRAMES) return null

            var subSum = 0f
            for (i in 0 until DETECT_SUB_BINS) subSum += detectAccum[i]
            val subBass = subSum / DETECT_SUB_BINS

            var bassSum = 0f
            for (i in 0 until DETECT_BASS_BINS) bassSum += detectAccum[i]
            val bass = bassSum / DETECT_BASS_BINS

            var midSum = 0f
            for (i in DETECT_BASS_BINS until DETECT_BASS_BINS + DETECT_MID_BINS) midSum += detectAccum[i]
            val mids = midSum / DETECT_MID_BINS

            // Normalise by frame count
            val normSub  = subBass / detectFrames
            val normBass = bass    / detectFrames
            val normMids = mids    / detectFrames

            val subRatio  = normSub  / (normBass + 0.001f)
            val bassRatio = normBass / (normBass + normMids + 0.001f)

            resetDetection()

            return when {
                subRatio  > 0.55f && bassRatio > 0.50f -> "electronic"
                bassRatio > 0.50f && subRatio  < 0.45f -> "rock"
                bassRatio < 0.35f                       -> "classical"
                else                                    -> "any"
            }
        }
    }

    fun resetDetection() {
        synchronized(this) {
            detectAccum.fill(0f)
            detectFrames = 0
        }
    }

    fun stop() {
        visualizer?.apply {
            try { enabled = false; release() } catch (_: Exception) {}
        }
        visualizer = null
        synchronized(this) {
            lastWave = null
            lastFft = null
            smoothFft.fill(0f)
            energyHistory.clear()
            energySum = 0.0
            midAvg = 0.1f
            trebleAvg = 0.1f
            firstFrame = true
            fftPrimed = false
            resetDetection()
            _data.set(AudioData())
        }
    }

    private fun publish() {
        synchronized(this) {
            val wave = lastWave ?: return
            val rawFft = lastFft ?: return

            // Atomically clear buffers upon consumption to prevent desynchronization
            lastWave = null
            lastFft = null

            val fftLen = minOf(rawFft.size, FFT_BINS)
            if (!fftPrimed) {
                for (i in 0 until fftLen) smoothFft[i] = rawFft[i]
                for (i in fftLen until FFT_BINS) smoothFft[i] = 0f
                fftPrimed = true
            } else {
                // Smooth FFT: 50% old + 50% new — faster reaction while avoiding flicker
                for (i in 0 until fftLen) {
                    smoothFft[i] = smoothFft[i] * 0.50f + rawFft[i] * 0.50f
                }
                for (i in fftLen until FFT_BINS) smoothFft[i] = 0f
            }

            // Accumulate for genre detection (raw, unweighted)
            val accLen = minOf(rawFft.size, FFT_BINS)
            for (i in 0 until accLen) detectAccum[i] += rawFft[i]
            detectFrames++

            // Beat energy = mean of bass bins 0..19 (≈ 0–860 Hz with 512 bins at 44100 Hz)
            val bassBins = minOf(20, fftLen)
            var bassSum = 0f
            for (i in 0 until bassBins) bassSum += smoothFft[i] * genreWeights[i]
            val bassEnergy = if (bassBins > 0) bassSum / bassBins else 0f

            // Rolling history for normalisation — running sum avoids iterating the deque each frame
            if (energyHistory.size >= 15) energySum -= energyHistory.removeFirst().toDouble()
            energyHistory.addLast(bassEnergy)
            energySum += bassEnergy.toDouble()
            val avgEnergy = (energySum / energyHistory.size).toFloat()
            val beat = if (firstFrame) {
                firstFrame = false
                0f
            } else {
                (bassEnergy / (avgEnergy + 0.001f) - 0.6f).coerceIn(0f, 1f)
            }

            // Mid energy: bins 20–99 (~860–4300 Hz).
            // Output is deviation above rolling average: 0 at steady-state music, positive on peaks.
            // This matches beat's normalization so multipliers in effects work correctly at all volumes.
            var midSum = 0f
            for (i in 20 until 100) midSum += smoothFft[i]
            val midEnergy = midSum / 80f
            midAvg = midAvg * 0.98f + midEnergy * 0.02f
            val mid = maxOf(0f, midEnergy / (midAvg + 0.001f) - 1f)

            // Treble energy: bins 100–255 (~4300–11000 Hz).
            // Same deviation normalization as mid.
            var trebleSum = 0f
            for (i in 100 until 256) trebleSum += smoothFft[i]
            val trebleEnergy = trebleSum / 156f
            trebleAvg = trebleAvg * 0.98f + trebleEnergy * 0.02f
            val treble = maxOf(0f, trebleEnergy / (trebleAvg + 0.001f) - 1f)

            _data.set(AudioData(
                waveform = wave.copyInto(FloatArray(FFT_BINS)),
                fft = smoothFft.copyOf(),
                beat = beat,
                mid = mid,
                treble = treble
            ))
        }
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
