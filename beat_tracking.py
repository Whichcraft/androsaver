"""Librosa-backed beat tracking helpers with a safe fallback path."""

from __future__ import annotations

from collections import deque
from concurrent.futures import ThreadPoolExecutor
import importlib
import sys
import threading
import time as _time

import numpy as np

_LIBROSA = None
_LIBROSA_FAILED = False
_LIBROSA_ERROR = None


def _get_librosa():
    global _LIBROSA, _LIBROSA_FAILED, _LIBROSA_ERROR
    if _LIBROSA is None and not _LIBROSA_FAILED:
        try:
            _LIBROSA = importlib.import_module("librosa")
        except Exception as exc:  # pragma: no cover - optional dependency at runtime
            _LIBROSA_FAILED = True
            _LIBROSA_ERROR = exc
            print(f"BeatTracking: librosa unavailable: {exc}", file=sys.stderr)
    return _LIBROSA


def _scalar(value) -> float:
    arr = np.asarray(value)
    if arr.size == 0:
        return 0.0
    return float(arr.reshape(-1)[0])


class LibrosaBeatTracker:
    def __init__(
        self,
        sample_rate: int,
        block_size: int,
        history_seconds: float = 6.0,
        min_seconds: float = 2.5,
        analysis_interval: float = 0.35,
        hop_length: int = 512,
    ) -> None:
        self.sample_rate = sample_rate
        self.block_size = block_size
        self.hop_length = hop_length
        self.analysis_interval = analysis_interval
        self._min_samples = max(block_size * 2, int(sample_rate * min_seconds))
        self._blocks = deque(
            maxlen=max(8, int(np.ceil(sample_rate * history_seconds / block_size)))
        )
        self._lock = threading.Lock()
        self._block_end_time = 0.0
        self._last_analysis = 0.0
        self._last_beat_time = None
        self._beat_interval = None
        self._last_onset_time = None
        self._last_onset_strength = 0.0
        self._bpm = 0.0
        self._analysis_running = False
        self._released = False
        self._executor = ThreadPoolExecutor(max_workers=1)

    def release(self) -> None:
        with self._lock:
            if self._released:
                return
            self._released = True
        self._executor.shutdown(wait=True)

    @property
    def enabled(self) -> bool:
        return _get_librosa() is not None

    def push_audio(self, block: np.ndarray, end_time: float) -> None:
        with self._lock:
            if self._released:
                return
        chunk = np.asarray(block, dtype=np.float32).copy()
        with self._lock:
            self._blocks.append(chunk)
            self._block_end_time = float(end_time)

    def analyze(self, fallback_bpm: float = 0.0) -> float:
        with self._lock:
            if self._released:
                return self._bpm or fallback_bpm
        if _get_librosa() is None:
            return fallback_bpm

        now = _time.monotonic()
        with self._lock:
            if self._released:
                return self._bpm or fallback_bpm
            if now - self._last_analysis < self.analysis_interval:
                return self._bpm or fallback_bpm
            if not self._blocks:
                self._last_analysis = now
                return self._bpm or fallback_bpm
            if self._analysis_running:
                return self._bpm or fallback_bpm
            blocks = tuple(self._blocks)
            block_end_time = self._block_end_time
            self._last_analysis = now
            self._analysis_running = True

        try:
            self._executor.submit(
                self._run_analysis,
                blocks, block_end_time, fallback_bpm,
            )
        except RuntimeError:
            with self._lock:
                self._analysis_running = False
        return self._bpm or fallback_bpm

    def _run_analysis(self, blocks, block_end_time: float, fallback_bpm: float) -> None:
        try:
            self._analyze_blocks(blocks, block_end_time, fallback_bpm)
        except Exception as exc:
            print(f"BeatTracking: analysis worker failed: {exc}", file=sys.stderr)
        finally:
            with self._lock:
                self._analysis_running = False

    def _analyze_blocks(self, blocks, block_end_time: float, fallback_bpm: float) -> None:
        librosa = _get_librosa()
        if librosa is None:
            return
        y = np.concatenate(blocks).astype(np.float32, copy=False)
        if y.size < self._min_samples:
            return

        start_bpm = fallback_bpm if fallback_bpm > 0 else (self._bpm or 140.0)
        try:
            onset_env = librosa.onset.onset_strength(
                y=y,
                sr=self.sample_rate,
                hop_length=self.hop_length,
                center=False,
                aggregate=np.mean,
            )
            if onset_env.size < 8:
                with self._lock:
                    bpm = self._bpm or fallback_bpm
                    if bpm:
                        self._bpm = bpm
                    return

            tempo, beat_frames = librosa.beat.beat_track(
                onset_envelope=onset_env,
                sr=self.sample_rate,
                hop_length=self.hop_length,
                start_bpm=start_bpm,
                tightness=100,
                trim=False,
            )
            onset_frames = librosa.onset.onset_detect(
                onset_envelope=onset_env,
                sr=self.sample_rate,
                hop_length=self.hop_length,
                units="frames",
                backtrack=False,
            )
        except Exception as exc:
            print(f"BeatTracking: analysis error: {exc}", file=sys.stderr)
            return

        frame_seconds = self.hop_length / float(self.sample_rate)
        buffer_start_time = float(block_end_time) - (y.size / float(self.sample_rate))

        bpm = _scalar(tempo)
        if bpm:
            bpm = max(60.0, min(200.0, bpm))

        beat_frames = np.asarray(beat_frames).reshape(-1)
        last_beat_time = None
        beat_interval = None
        if beat_frames.size:
            beat_times = buffer_start_time + beat_frames.astype(np.float64) * frame_seconds
            last_beat_time = float(beat_times[-1])
            if beat_times.size >= 2:
                beat_interval = float(np.median(np.diff(beat_times[-8:])))
            elif bpm > 0:
                beat_interval = 60.0 / bpm

        onset_frames = np.asarray(onset_frames).reshape(-1)
        last_onset_time = None
        if onset_frames.size:
            last_onset_time = buffer_start_time + float(onset_frames[-1]) * frame_seconds

        tail = onset_env[-min(64, onset_env.size) :]
        recent = onset_env[-min(4, onset_env.size) :]
        baseline = float(np.median(tail) + 1e-6)
        onset_strength = max(0.0, min(3.0, float(np.max(recent) / baseline) - 1.0))

        if (not bpm) and beat_interval and 0.30 <= beat_interval <= 1.0:
            bpm = max(60.0, min(200.0, 60.0 / beat_interval))

        with self._lock:
            if bpm:
                self._bpm = bpm
            if last_beat_time is not None and beat_interval and 0.30 <= beat_interval <= 1.0:
                self._last_beat_time = last_beat_time
                self._beat_interval = beat_interval
            elif last_beat_time is not None:
                self._last_beat_time = last_beat_time
            if last_onset_time is not None:
                self._last_onset_time = last_onset_time
            self._last_onset_strength = onset_strength

    def refine_beat(self, raw_beat: float, current_time: float) -> float:
        if _get_librosa() is None:
            return raw_beat

        with self._lock:
            last_onset_time = self._last_onset_time
            onset_strength = self._last_onset_strength
            last_beat_time = self._last_beat_time
            beat_interval = self._beat_interval

        refined = max(0.0, raw_beat)

        recent_onset = (
            last_onset_time is not None
            and 0.0 <= current_time - last_onset_time <= 0.18
        )

        near_grid = False
        if last_beat_time is not None and beat_interval:
            nearest_step = round((current_time - last_beat_time) / beat_interval)
            nearest_time = last_beat_time + nearest_step * beat_interval
            beat_window = min(0.14, max(0.07, beat_interval * 0.22))
            near_grid = abs(current_time - nearest_time) <= beat_window

        if recent_onset:
            refined = max(refined, onset_strength)
            if near_grid:
                refined = max(refined, raw_beat * 1.25, onset_strength * 1.10)
        elif near_grid:
            refined = max(refined, raw_beat * 1.10)
        else:
            refined *= 0.85

        return max(0.0, min(3.0, refined))
