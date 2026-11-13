from __future__ import annotations
import sys
import threading
import importlib
from collections import deque
from dataclasses import dataclass
import numpy as np
from core.context import AudioConfig
from beat_tracking import LibrosaBeatTracker

sd = None
_sd_lock = threading.Lock()
_sd_loading = False
_sd_error = None


def _load_sounddevice() -> None:
    global sd, _sd_loading, _sd_error
    try:
        sd = importlib.import_module("sounddevice")
    except Exception as exc:  # Optional audio backend.
        _sd_error = exc
    finally:
        _sd_loading = False


def _get_sounddevice():
    """Start loading PortAudio without blocking visualizer startup."""
    global _sd_loading
    if sd is not None:
        return sd
    with _sd_lock:
        if sd is None and not _sd_loading and _sd_error is None:
            _sd_loading = True
            thread = threading.Thread(target=_load_sounddevice, daemon=True)
            thread.start()
    return sd

class BandLevel:
    """Running average of one band and its level relative to that average.

    The average must hold through a pause, or the first block of returning
    music reads about forty times its normal level, but it must follow a
    genuinely quieter passage. A fixed ratio cannot tell the two apart: with
    quiet music through a laptop microphone, room noise (0.002-0.005) sat at
    10-30 % of the music's average (0.015-0.02).

    So the room's noise floor is tracked too: the 20th percentile of the
    last second, remembered as its minimum and allowed to rise only over
    minutes. Single blocks of room noise vary too much to judge, so a pause
    is judged on the median of the last second: it starts within
    ``PAUSE_ENTER`` times the floor and lasts until the median is clearly
    above the noise (``PAUSE_EXIT``). The average holds throughout. A level
    below 30 % of the average but above the noise is held for at most
    ``HOLD_SECONDS`` and then followed; this also bridges the first second
    of a pause, before the window has seen it. When the band jumps far above the
    average (music after a pause or a silent start) the average catches up
    quickly. The level is clipped like the envelopes.
    """

    HOLD_SECONDS = 5.0
    PAUSE_ENTER = 3.0            # median within this factor of the floor
    PAUSE_EXIT = 5.0             # ... and clearly above it again
    FLOOR_RISE = 1.00005         # per block: the floor doubles in about 5 minutes

    def __init__(self, blocks_per_second: float):
        self.max_held = max(1, int(round(self.HOLD_SECONDS * blocks_per_second)))
        self.window = deque(maxlen=max(4, int(round(blocks_per_second))))
        self.reset()

    def reset(self) -> None:
        self.average = None
        self.floor = None
        self.held = 0
        self.paused = False
        self.window.clear()

    def update(self, value: float) -> float:
        self.window.append(value)
        if self.average is None:
            # The first blocks of a new stream carry a start-up transient
            # (measured at about 30x the room level); seeding the average from
            # it kept every later level far too low. Seed it from the median
            # of the first second instead and report nothing until then.
            if len(self.window) < self.window.maxlen:
                return 0.0
            self.average = max(float(np.median(self.window)), 1e-4)
        if len(self.window) == self.window.maxlen:
            recent = np.asarray(self.window)
            low = max(float(np.percentile(recent, 20)), 1e-6)
            self.floor = low if self.floor is None else min(self.floor * self.FLOOR_RISE, low)
            median = float(np.median(recent))
            if self.paused:
                self.paused = median < self.PAUSE_EXIT * self.floor
            else:
                # Only a floor well below the average is a real noise floor;
                # a start in the middle of music seeds it at the music level.
                self.paused = (self.floor < 0.3 * self.average
                               and median < self.PAUSE_ENTER * self.floor
                               and median < 0.3 * self.average)
        if self.paused:
            self.held = 0                        # hold for as long as it lasts
        elif value < 0.3 * self.average and self.held < self.max_held:
            self.held += 1                       # much quieter: hold briefly
        else:
            if value >= 0.3 * self.average:
                self.held = 0
            if value > 3.0 * self.average:
                self.average = self.average * 0.8 + value * 0.2
            else:
                self.average = max(self.average * 0.98 + value * 0.02, 1e-6)
        return float(np.clip(value / (self.average + 1e-6), 0.0, 6.0))


@dataclass(frozen=True)
class AudioSnapshot:
    """One callback publication; array copies are owned by the reader."""

    waveform: np.ndarray
    fft: np.ndarray
    raw_beat: float
    mid_energy: float
    treble_energy: float
    bpm: float
    audio_time: float
    generation: int
    envelopes: tuple[float, float, float]
    onset_time: float


class AudioEngine:
    def __init__(self, audio_config: AudioConfig | None = None):
        self.audio_config = audio_config or AudioConfig()
        self._lock = threading.RLock()
        self._waveform = np.zeros(self.audio_config.block_size, dtype=np.float32)
        self._smooth_fft = None
        self._raw_beat_energy = 0.0
        self._mid_energy = 0.0
        self._treble_energy = 0.0
        self._bass_envelope = 0.0
        self._mid_envelope = 0.0
        self._treble_envelope = 0.0
        self._bass_avg = 0.1
        self._envelope_time = 0.0
        self._audio_time = 0.0
        self._audio_generation = 0
        self._blackman_window = np.blackman(self.audio_config.block_size).astype(np.float32)

        self._prev_spectrum = np.zeros(self.audio_config.block_size // 2, dtype=np.float32)
        self._flux_avg = 0.01
        self._mid_level = BandLevel(self.audio_config.sample_rate / self.audio_config.block_size)
        self._treble_level = BandLevel(self.audio_config.sample_rate / self.audio_config.block_size)
        self._dc_offset = None    # input DC offset, tracked per block
        self._prev_beat_energy = 0.0
        self._first_frame = True

        self._beat_times = deque(maxlen=8)
        self._last_onset_time = 0.0
        self._bpm = 0.0

        self._n_fft_bins = self.audio_config.block_size // 2
        self._genre_weights = np.ones(self._n_fft_bins, dtype=np.float32)
        self._detect_accum = np.zeros(self.audio_config.block_size // 2, dtype=np.float32)
        self._detect_frames = 0
        self._DETECT_MIN = 300

        self.beat_tracker = LibrosaBeatTracker(
            sample_rate=self.audio_config.sample_rate,
            block_size=self.audio_config.block_size,
        )
        self.stream = None
        self.active_dev = None
        self.last_error = None
        self._initial_stream_pending = True

    @property
    def initial_stream_pending(self) -> bool:
        return self._initial_stream_pending

    def _band_bounds(self, size: int, start_frac: float, end_frac: float) -> tuple[int, int]:
        start = max(0, min(size - 1, int(size * start_frac))) if size else 0
        end = max(start + 1, min(size, int(size * end_frac))) if size else 0
        return start, end

    def _band_mean(self, arr: np.ndarray, start_frac: float, end_frac: float) -> float:
        if arr.size == 0:
            return 0.0
        start, end = self._band_bounds(arr.size, start_frac, end_frac)
        return float(arr[start:end].mean()) if end > start else 0.0

    def _follow_envelope(self, current, target, elapsed):
        tau = (self.audio_config.envelope_attack_seconds
               if target > current else self.audio_config.envelope_release_seconds)
        coefficient = 1.0 - np.exp(-elapsed / max(tau, 1e-4))
        return float(current + coefficient * (target - current))

    def apply_genre_weights(self, genre: str) -> None:
        n = self._n_fft_bins
        weights = np.ones(n, dtype=np.float32)
        if genre == "electronic":
            weights[:n//4] = 1.5
            weights[n//2:] = 0.7
        elif genre == "rock":
            weights[n//8:n//3] = 1.3
        elif genre == "classical":
            weights[:n//4] = 0.6
            weights[n//4:] = 1.4
        with self._lock:
            if np.array_equal(self._genre_weights, weights):
                return
            self._genre_weights[:] = weights
            # The weighted spectrum changed discontinuously.  Start a fresh
            # flux baseline on the next callback instead of treating the
            # genre transition as an audio onset.
            self._first_frame = True
            self._raw_beat_energy = 0.0
            self._prev_beat_energy = 0.0

    def detect_genre(self) -> str | None:
        """Return detected genre string once enough frames are collected."""
        with self._lock:
            if self._detect_frames < self._DETECT_MIN:
                return None
            avg = self._detect_accum / self._detect_frames
            self._detect_accum[:] = 0
            self._detect_frames = 0

        # Classify spectral shape, not absolute loudness.  Slow/quiet tracks
        # otherwise look like silence or get classified from tiny numerical
        # differences in the raw FFT magnitudes.
        sub_bass = self._band_mean(avg, 0.0, 0.02)
        bass = self._band_mean(avg, 0.02, 0.08)
        mids = self._band_mean(avg, 0.08, 0.40)
        highs = self._band_mean(avg, 0.40, 0.90)
        total = sub_bass + bass + mids + highs + 1e-6
        sub_share = sub_bass / total
        bass_share = bass / total
        high_share = highs / total

        band_values = np.array([sub_bass, bass, mids, highs], dtype=np.float32)
        if float(band_values.max()) <= float(band_values.min()) * 1.2 + 1e-6:
            return "any"

        if sub_share > 0.16 and bass_share > 0.18 and high_share > 0.12:
            return "electronic"
        if bass_share > 0.22 and sub_share < 0.16:
            return "rock"
        if high_share > 0.30 and bass_share < 0.22:
            return "classical"
        return "any"

    def _audio_cb(self, indata, frames, time_info, status) -> None:
        if status:
            print(f"AudioEngine: status={status}", file=sys.stderr)
        try:
            captured = np.asarray(indata[:, 0], dtype=np.float32).reshape(-1)
            captured_frames = min(max(int(frames), 0), captured.size)
            captured = captured[:captured_frames]
            if not np.isfinite(captured).all():
                # One NaN or inf sample used to turn every running average
                # into NaN for the rest of the session.
                captured = np.nan_to_num(captured, nan=0.0, posinf=0.0, neginf=0.0)
            if captured.size:
                # Some microphones deliver a constant DC offset (a laptop mic
                # measured +0.20). It hid every pause from the RMS silence
                # gate, misled auto-gain and shifted drawn waveforms, so a
                # slowly tracked offset is removed before any analysis.
                block_dc = float(captured.mean())
                self._dc_offset = block_dc if self._dc_offset is None else self._dc_offset * 0.9 + block_dc * 0.1
                captured = captured - np.float32(self._dc_offset)
            adc_start = getattr(time_info, "inputBufferAdcTime", None)
            if adc_start is None:
                block_end_time = float(time_info.currentTime)
            else:
                block_end_time = float(adc_start) + float(captured_frames) / self.audio_config.sample_rate
            tracker_block = captured[-self.audio_config.block_size:] if captured.size > self.audio_config.block_size else captured
            self.beat_tracker.push_audio(tracker_block, block_end_time)

            mono = np.zeros(self.audio_config.block_size, dtype=np.float32)
            analysis = captured[-self.audio_config.block_size:]
            mono[:analysis.size] = analysis

            windowed = mono * self._blackman_window
            spectrum = np.abs(np.fft.rfft(windowed))[: self.audio_config.block_size // 2]
            spectrum = spectrum.astype(np.float32, copy=False)

            np.log1p(spectrum, out=spectrum)
            spectrum /= 10.0

            with self._lock:
                self._audio_time = block_end_time
                self._waveform[:] = mono

                if self._smooth_fft is None:
                    self._smooth_fft = spectrum.copy()
                else:
                    alpha = 0.50
                    self._smooth_fft = (1.0 - alpha) * self._smooth_fft + alpha * spectrum

                self._detect_accum[:] += spectrum
                self._detect_frames += 1

                weights = self._genre_weights

                beat_end = min(len(spectrum), len(weights))
                weighted = spectrum[:beat_end] * weights[:beat_end]
                if self._first_frame:
                    self._prev_spectrum[:] = weighted
                    self._first_frame = False
                else:
                    diff = weighted - self._prev_spectrum[:beat_end]
                    flux = float(np.mean(np.maximum(0.0, diff)))

                    self._flux_avg = self._flux_avg * 0.95 + flux * 0.05
                    self._raw_beat_energy = flux / (self._flux_avg + 1e-6)

                    t_now = block_end_time
                    if (
                        self._raw_beat_energy > 2.2
                        and self._prev_beat_energy <= 2.2
                        and t_now - self._last_onset_time > 0.22
                    ):
                        self._beat_times.append(t_now)
                        self._last_onset_time = t_now
                        if len(self._beat_times) >= 2:
                            intervals = np.diff(self._beat_times)
                            median_interval = float(np.median(intervals))
                            if 0.25 <= median_interval <= 1.2:
                                self._bpm = 60.0 / median_interval

                    self._prev_beat_energy = self._raw_beat_energy

                # Flux is frame-to-frame, not against the first frame.
                self._prev_spectrum[:beat_end] = weighted

                mid = self._band_mean(self._smooth_fft, 0.04, 0.20)
                self._mid_energy = self._mid_level.update(mid)

                treble = self._band_mean(self._smooth_fft, 0.20, 0.50)
                self._treble_energy = self._treble_level.update(treble)
                bass = self._band_mean(self._smooth_fft, 0.0, 0.04)
                self._bass_avg = self._bass_avg * 0.98 + bass * 0.02
                elapsed = block_end_time - self._envelope_time
                if not np.isfinite(elapsed) or elapsed <= 0.0:
                    elapsed = self.audio_config.block_size / self.audio_config.sample_rate
                elapsed = min(max(float(elapsed), 1e-4), 1.0)
                self._envelope_time = block_end_time
                self._bass_envelope = self._follow_envelope(
                    self._bass_envelope, np.clip(bass / (self._bass_avg + 1e-6), 0.0, 6.0), elapsed
                )
                self._mid_envelope = self._follow_envelope(
                    self._mid_envelope, np.clip(self._mid_energy, 0.0, 6.0), elapsed
                )
                self._treble_envelope = self._follow_envelope(
                    self._treble_envelope, np.clip(self._treble_energy, 0.0, 6.0), elapsed
                )
                self._audio_generation += 1
        except Exception:
            import traceback
            traceback.print_exc(file=sys.stderr)

    def release(self):
        self.stop_input_stream()
        self.beat_tracker.release()

    def get_snapshot(self) -> AudioSnapshot:
        """Copy all render-facing analysis fields under one lock."""
        with self._lock:
            return AudioSnapshot(
                waveform=self._waveform.copy(),
                fft=(self._smooth_fft.copy() if self._smooth_fft is not None
                     else np.zeros(self.audio_config.block_size // 2, dtype=np.float32)),
                raw_beat=self._raw_beat_energy,
                mid_energy=self._mid_energy,
                treble_energy=self._treble_energy,
                bpm=self._bpm,
                audio_time=self._audio_time,
                generation=self._audio_generation,
                envelopes=(self._bass_envelope, self._mid_envelope, self._treble_envelope),
                onset_time=self._last_onset_time,
            )

    def get_audio(self):
        """Return a thread-safe copy of the current audio analysis state."""
        with self._lock:
            return (
                self._waveform.copy(),
                self._smooth_fft.copy() if self._smooth_fft is not None else np.zeros(self.audio_config.block_size // 2, dtype=np.float32),
                self._raw_beat_energy,
                self._mid_energy,
                self._treble_energy,
                self._bpm,
                self._audio_time,
            )

    def get_audio_generation(self) -> int:
        """Return the count of successfully published callback blocks."""
        with self._lock:
            return self._audio_generation

    def get_beat_timing(self) -> tuple[float, float]:
        """Return fallback BPM and the most recent audio-clock onset time."""
        with self._lock:
            return self._bpm, self._last_onset_time

    def get_envelopes(self) -> tuple[float, float, float]:
        """Return bounded attack/release envelopes for bass, mids, treble."""
        with self._lock:
            return self._bass_envelope, self._mid_envelope, self._treble_envelope

    def is_active(self) -> bool:
        """Return True if the audio stream is currently running."""
        return self.stream is not None and self.stream.active

    def input_devices(self) -> list[tuple[int, str]]:
        """Return list of (index, name) for all input-capable devices."""
        sounddevice = _get_sounddevice()
        if sounddevice is None:
            return []
        try:
            queried = sounddevice.query_devices()
        except Exception:
            return []

        devices = []
        for idx, device in enumerate(queried):
            if device["max_input_channels"] > 0:
                devices.append((idx, device["name"]))
        return devices

    def _device_priority(self, name: str) -> tuple[int, str]:
        low = name.lower()
        if "pipewire" in low or "pulse" in low:
            return (0, low)
        if any(token in low for token in ("default", "sysdefault", "lavrate", "samplerate", "speexrate", "upmix", "vdownmix", "speex")):
            return (3, low)
        return (1, low)

    def preferred_input_candidates(self, preferred_idx: int | None = None) -> list[int | None]:
        devices = self.input_devices()
        ordered = sorted(devices, key=lambda item: self._device_priority(item[1]))
        candidates: list[int | None] = []

        if preferred_idx is not None:
            candidates.append(preferred_idx)

        for idx, _ in ordered:
            if idx not in candidates:
                candidates.append(idx)

        # Do not open PortAudio's implicit ``None`` device here.  On Linux
        # that asks ALSA to construct its ``default`` PCM, which can emit a
        # burst of low-level errors when no capture device exists.  Every
        # usable default route is already represented by an input-capable
        # device returned from ``query_devices()``; if none are available,
        # remain in the app's normal silent mode without probing ALSA again.
        return candidates

    def start_input_stream(self, device_idx: int | None):
        sounddevice = _get_sounddevice()
        if sounddevice is None:
            self.last_error = _sd_error or RuntimeError("sounddevice is still loading or unavailable")
            return None

        self._initial_stream_pending = False
        old_stream = self.stream
        old_dev = self.active_dev
        stream = None
        try:
            stream = sounddevice.InputStream(
                samplerate=self.audio_config.sample_rate,
                blocksize=self.audio_config.block_size,
                channels=self.audio_config.channels,
                device=device_idx,
                callback=self._audio_cb,
            )
            stream.start()
            if old_stream is not None:
                try:
                    old_stream.stop()
                except Exception as exc:
                    self.last_error = exc
                try:
                    old_stream.close()
                except Exception as exc:
                    self.last_error = exc
            self.stream = stream
            if device_idx != old_dev:
                # A different device has its own level, offset and tempo;
                # the old device's state muted a quieter one for minutes.
                self.reset_analysis()
            self.active_dev = device_idx
            self.last_error = None
            return self.stream
        except Exception as exc:
            self.stream = old_stream
            self.active_dev = old_dev
            self.last_error = exc
            if stream is not None:
                try:
                    stream.close()
                except Exception as close_exc:
                    self.last_error = close_exc
            return None

    def reset_analysis(self) -> None:
        """Forget everything learned from the previous input."""
        with self._lock:
            self._mid_level.reset()
            self._treble_level.reset()
            self._dc_offset = None
            self._smooth_fft = None
            self._first_frame = True
            self._flux_avg = 0.01
            self._bass_avg = 0.1
            self._raw_beat_energy = self._prev_beat_energy = 0.0
            self._mid_energy = self._treble_energy = 0.0
            self._bass_envelope = self._mid_envelope = self._treble_envelope = 0.0
            self._beat_times.clear()
            self._last_onset_time = 0.0
            self._bpm = 0.0
            self._detect_accum[:] = 0.0
            self._detect_frames = 0
            self._waveform[:] = 0.0
            self._prev_spectrum[:] = 0.0
            self._audio_generation += 1
        self.beat_tracker.reset()

    def mark_stream_lost(self) -> None:
        """Close a stream that stopped delivering audio and retry later.

        The published audio is cleared, so effects and the silence gate see
        silence instead of the last block repeated forever.
        """
        self.stop_input_stream()
        self.reset_analysis()
        self._initial_stream_pending = True

    def stop_input_stream(self) -> None:
        stream = self.stream
        self.stream = None
        self.active_dev = None
        if stream is None:
            return
        try:
            stream.stop()
        except Exception as exc:
            self.last_error = exc
        try:
            stream.close()
        except Exception as exc:
            self.last_error = exc

    def open_input_stream(self, *candidates):
        seen: list[int | None] = []
        expanded: list[int | None] = []
        for candidate in candidates:
            if candidate is None:
                expanded.extend(self.preferred_input_candidates(candidate))
            else:
                expanded.append(candidate)
        for resolved in expanded:
            if resolved in seen:
                continue
            seen.append(resolved)
            stream = self.start_input_stream(resolved)
            if stream:
                return stream, resolved
        return None, None
