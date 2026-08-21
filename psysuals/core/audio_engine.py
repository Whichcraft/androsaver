from __future__ import annotations
import sys
import threading
import importlib
from collections import deque
import numpy as np
import config
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

class AudioEngine:
    def __init__(self):
        self._lock = threading.RLock()
        self._waveform = np.zeros(config.BLOCK_SIZE, dtype=np.float32)
        self._smooth_fft = None
        self._raw_beat_energy = 0.0
        self._mid_energy = 0.0
        self._treble_energy = 0.0
        self._audio_time = 0.0
        self._blackman_window = np.blackman(config.BLOCK_SIZE).astype(np.float32)

        self._prev_spectrum = np.zeros(config.BLOCK_SIZE // 2, dtype=np.float32)
        self._flux_avg = 0.01
        self._mid_avg = 0.1
        self._treble_avg = 0.1
        self._prev_beat_energy = 0.0
        self._first_frame = True

        self._beat_times = deque(maxlen=8)
        self._last_onset_time = 0.0
        self._bpm = 0.0

        self._n_fft_bins = config.BLOCK_SIZE // 2
        self._genre_weights = np.ones(self._n_fft_bins, dtype=np.float32)
        self._detect_accum = np.zeros(config.BLOCK_SIZE // 2, dtype=np.float32)
        self._detect_frames = 0
        self._DETECT_MIN = 300

        self.beat_tracker = LibrosaBeatTracker(
            sample_rate=config.SAMPLE_RATE,
            block_size=config.BLOCK_SIZE,
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

        sub_bass = self._band_mean(avg, 0.0, 0.02)
        bass = self._band_mean(avg, 0.0, 0.06)
        mids = self._band_mean(avg, 0.06, 0.40)
        sub_ratio = sub_bass / (bass + 1e-6)
        bass_ratio = bass / (bass + mids + 1e-6)

        if sub_ratio > 0.55 and bass_ratio > 0.50:
            return "electronic"
        if bass_ratio > 0.50 and sub_ratio < 0.45:
            return "rock"
        if bass_ratio < 0.35:
            return "classical"
        return "any"

    def _audio_cb(self, indata, frames, time_info, status) -> None:
        if status:
            print(f"AudioEngine: status={status}", file=sys.stderr)
        try:
            captured = np.asarray(indata[:, 0], dtype=np.float32).reshape(-1)
            captured_frames = min(max(int(frames), 0), captured.size)
            captured = captured[:captured_frames]
            adc_start = getattr(time_info, "inputBufferAdcTime", None)
            if adc_start is None:
                block_end_time = float(time_info.currentTime)
            else:
                block_end_time = float(adc_start) + float(captured_frames) / config.SAMPLE_RATE
            tracker_block = captured[-config.BLOCK_SIZE:] if captured.size > config.BLOCK_SIZE else captured
            self.beat_tracker.push_audio(tracker_block, block_end_time)

            mono = np.zeros(config.BLOCK_SIZE, dtype=np.float32)
            analysis = captured[-config.BLOCK_SIZE:]
            mono[:analysis.size] = analysis

            windowed = mono * self._blackman_window
            spectrum = np.abs(np.fft.rfft(windowed))[: config.BLOCK_SIZE // 2]
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
                self._mid_avg = self._mid_avg * 0.98 + mid * 0.02
                self._mid_energy = mid / (self._mid_avg + 1e-6)

                treble = self._band_mean(self._smooth_fft, 0.20, 0.50)
                self._treble_avg = self._treble_avg * 0.98 + treble * 0.02
                self._treble_energy = treble / (self._treble_avg + 1e-6)
        except Exception:
            import traceback
            traceback.print_exc(file=sys.stderr)

    def release(self):
        self.stop_input_stream()
        self.beat_tracker.release()

    def get_audio(self):
        """Return a thread-safe copy of the current audio analysis state."""
        with self._lock:
            return (
                self._waveform.copy(),
                self._smooth_fft.copy() if self._smooth_fft is not None else np.zeros(config.BLOCK_SIZE // 2, dtype=np.float32),
                self._raw_beat_energy,
                self._mid_energy,
                self._treble_energy,
                self._bpm,
                self._audio_time,
            )

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

        # Only fall back to the library default if every explicit device fails.
        candidates.append(None)
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
                samplerate=config.SAMPLE_RATE,
                blocksize=config.BLOCK_SIZE,
                channels=config.CHANNELS,
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
