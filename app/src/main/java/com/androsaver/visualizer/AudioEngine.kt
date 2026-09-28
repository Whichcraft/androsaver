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
        private const val DETECT_SUB_END    = 10    // ~0-430 Hz
        private const val DETECT_BASS_END   = 41    // ~430-1760 Hz
        private const val DETECT_MIDS_END   = 205   // ~1760-8800 Hz
        private const val DETECT_HIGHS_END  = 461   // ~8800-19800 Hz
    }

    private var visualizer: Visualizer? = null
    private val smoothFft = FloatArray(FFT_BINS)
    private val waveScratch = FloatArray(FFT_BINS)
    private val fftScratch = FloatArray(FFT_BINS)
    private val genreWeights = FloatArray(FFT_BINS) { 1f }
    private val energyHistory = ArrayDeque<Float>()
    private var energySum = 0.0          // running sum for O(1) average
    private var midAvg    = 0.1f         // warm-start to avoid first-frame spikes
    private var trebleAvg = 0.1f         // warm-start to avoid first-frame spikes
    private val snapshots = arrayOf(AudioData(), AudioData())
    private val _data = AtomicReference(snapshots[0])
    private var snapshotIndex = 0

    // Long-term accumulator for genre detection
    private val detectAccum = FloatArray(FFT_BINS)
    private var detectFrames = 0
    @Volatile private var detectionEnabled = false

    // Latest waveform/fft bytes — combined when both arrive
    @Volatile private var lastWave: FloatArray? = null
    @Volatile private var lastFft: FloatArray? = null
    @Volatile private var running = false
    private var firstFrame = true
    private var fftPrimed = false

    val data: AudioData get() = _data.get()

    @Synchronized
    fun start() {
        if (visualizer != null) return
        try {
            val maxCap = Visualizer.getCaptureSizeRange()[1].coerceAtMost(1024)
            val v = Visualizer(0)
            try {
                v.captureSize = maxCap
                v.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(vis: Visualizer, bytes: ByteArray, rate: Int) {
                        synchronized(this@AudioEngine) {
                            if (!running) return
                            lastWave = bytes.toWaveform(waveScratch)
                            publish()
                        }
                    }
                    override fun onFftDataCapture(vis: Visualizer, bytes: ByteArray, rate: Int) {
                        synchronized(this@AudioEngine) {
                            if (!running) return
                            lastFft = bytes.toFftMagnitude(fftScratch)
                            publish()
                        }
                    }
                }, Visualizer.getMaxCaptureRate(), true, true)
                running = true
                v.enabled = true
                visualizer = v
            } catch (e: Exception) {
                running = false
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
            val previous = genreWeights.copyOf()
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
            if (!genreWeights.contentEquals(previous)) {
                // A genre change alters the weighted spectrum discontinuously.
                // Reset the rolling baseline so it cannot become a false beat.
                energyHistory.clear()
                energySum = 0.0
                firstFrame = true
            }
        }
    }

    fun setGenreDetectionEnabled(enabled: Boolean) {
        synchronized(this) {
            detectionEnabled = enabled
            if (!enabled) resetDetection()
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

            fun bandMean(start: Int, end: Int): Float {
                val limit = minOf(end, detectAccum.size)
                if (limit <= start) return 0f
                var sum = 0f
                for (i in start until limit) sum += detectAccum[i]
                return sum / (limit - start) / detectFrames
            }
            val subBass = bandMean(0, DETECT_SUB_END)
            val bass = bandMean(DETECT_SUB_END, DETECT_BASS_END)
            val mids = bandMean(DETECT_BASS_END, DETECT_MIDS_END)
            val highs = bandMean(DETECT_MIDS_END, DETECT_HIGHS_END)
            val total = subBass + bass + mids + highs + 1e-6f
            val subShare = subBass / total
            val bassShare = bass / total
            val highShare = highs / total
            val minBand = minOf(subBass, bass, mids, highs)
            val maxBand = maxOf(subBass, bass, mids, highs)

            resetDetection()

            if (maxBand <= minBand * 1.2f + 1e-6f) return "any"
            return when {
                subShare > 0.16f && bassShare > 0.18f && highShare > 0.12f -> "electronic"
                bassShare > 0.22f && subShare < 0.16f -> "rock"
                highShare > 0.30f && bassShare < 0.22f -> "classical"
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

    @Synchronized
    fun stop() {
        running = false
        detectionEnabled = false
        visualizer?.let { active ->
            // Best-effort teardown: a failure disabling capture must not skip
            // release, and repeated stop calls remain harmless.
            try { active.enabled = false } catch (_: Exception) {}
            try { active.release() } catch (_: Exception) {}
        }
        visualizer = null
        synchronized(this) {
            lastWave = null
            lastFft = null
            waveScratch.fill(0f)
            fftScratch.fill(0f)
            smoothFft.fill(0f)
            energyHistory.clear()
            energySum = 0.0
            midAvg = 0.1f
            trebleAvg = 0.1f
            firstFrame = true
            fftPrimed = false
            resetDetection()
            snapshots.forEach {
                it.waveform.fill(0f)
                it.fft.fill(0f)
                it.beat = 0f
                it.gain = 1f
            }
            snapshotIndex = 0
            _data.set(snapshots[0])
        }
    }

    private fun publish() {
        synchronized(this) {
            if (!running) return
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

            // Accumulate only while automatic genre detection is requested.
            if (detectionEnabled) {
                val accLen = minOf(rawFft.size, FFT_BINS)
                for (i in 0 until accLen) detectAccum[i] += rawFft[i]
                if (detectFrames < Int.MAX_VALUE) detectFrames++
            }

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

            val snapshot = snapshots[(snapshotIndex + 1) % snapshots.size]
            snapshotIndex = (snapshotIndex + 1) % snapshots.size
            snapshot.waveform.fill(0f)
            System.arraycopy(wave, 0, snapshot.waveform, 0, minOf(wave.size, FFT_BINS))
            System.arraycopy(smoothFft, 0, snapshot.fft, 0, FFT_BINS)
            snapshot.beat = beat
            snapshot.gain = 1f
            // mid/treble are immutable in the snapshot type, so publish them
            // through the constructor-free reusable buffer only after changing
            // the fields to mutable values.
            snapshot.mid = mid
            snapshot.treble = treble
            _data.set(snapshot)
        }
    }

    // ── Byte-array helpers ────────────────────────────────────────────────────

    /** Convert unsigned 8-bit waveform bytes to float -1..1. */
    private fun ByteArray.toWaveform(out: FloatArray): FloatArray {
        val count = minOf(size, FFT_BINS)
        for (i in 0 until count) out[i] = ((this[i].toInt() and 0xFF) - 128) / 128f
        for (i in count until FFT_BINS) out[i] = 0f
        return out
    }

    /**
     * Convert Android Visualizer FFT bytes to log-normalised magnitudes.
     * Format: fft[0]=DC real, fft[1]=Nyquist real, fft[2k]/fft[2k+1]=Re/Im for k=1..n/2-1.
     */
    private fun ByteArray.toFftMagnitude(out: FloatArray): FloatArray {
        val bins = (size / 2).coerceAtMost(FFT_BINS)
        out.fill(0f)
        if (bins == 0) return out
        // DC and Nyquist are real-only
        out[0] = abs(this[0].toFloat()) / 128f
        if (bins > 1) out[bins - 1] = abs(this[1].toFloat()) / 128f
        for (i in 1 until bins - 1) {
            val re = this[2 * i].toFloat()
            val im = if (2 * i + 1 < size) this[2 * i + 1].toFloat() else 0f
            val raw = sqrt(re * re + im * im) / 128f
            // log1p normalisation matching Python: log1p(spectrum) / 10
            out[i] = ln(1f + raw * 10f) / 10f
        }
        return out
    }
}
