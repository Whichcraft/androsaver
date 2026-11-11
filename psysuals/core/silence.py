"""Silence hysteresis driven only by fresh audio publications."""
from dataclasses import dataclass

import numpy as np


@dataclass(frozen=True)
class SilenceThresholds:
    rms_enter: float = .0035
    rms_exit: float = .006
    fft_enter: float = .0015
    fft_exit: float = .003
    enter_seconds: float = .14
    exit_blocks: int = 2


class SilenceGate:
    def __init__(self, thresholds: SilenceThresholds, block_seconds: float,
                 is_silent=True):
        self.thresholds = thresholds
        self.block_seconds = block_seconds
        self.is_silent = is_silent
        self.last_generation = None
        self.last_audio_time = None
        self.quiet_seconds = 0.0
        self.loud_blocks = 0

    def update(self, rms, fft_mean, audio_time, generation,
               music_activity=0.0):
        """Update the silence gate once per newly published audio block."""
        if generation == self.last_generation:
            return
        self.last_generation = generation

        block_seconds = self.block_seconds
        try:
            current_audio_time = float(audio_time)
        except (TypeError, ValueError):
            current_audio_time = float("nan")
        if (
            self.last_audio_time is not None
            and np.isfinite(current_audio_time)
            and np.isfinite(self.last_audio_time)
            and current_audio_time > self.last_audio_time
        ):
            elapsed = min(current_audio_time - self.last_audio_time, 1.0)
        else:
            elapsed = block_seconds
        self.last_audio_time = current_audio_time

        finite = np.isfinite(rms) and np.isfinite(fft_mean)
        if not finite:
            self.quiet_seconds = 0.0
            self.loud_blocks = 0
            return

        if self.is_silent:
            # A quiet, sustained track can sit below the absolute loudness
            # thresholds while still having meaningful spectral energy.
            structured_now = (
                fft_mean >= self.thresholds.fft_enter * 0.5
                and music_activity >= 1.15
            )
            loud_now = (
                rms >= self.thresholds.rms_exit
                or fft_mean >= self.thresholds.fft_exit
                or structured_now
            )
            if loud_now:
                self.loud_blocks += 1
                self.quiet_seconds = 0.0
                if self.loud_blocks >= self.thresholds.exit_blocks:
                    self.is_silent = False
                    self.loud_blocks = 0
            else:
                self.loud_blocks = 0
            return

        structured_now = (
            fft_mean >= self.thresholds.fft_enter * 0.5
            and music_activity >= 1.15
        )
        quiet_now = (
            rms < self.thresholds.rms_enter
            and fft_mean < self.thresholds.fft_enter
            and not structured_now
        )
        if quiet_now:
            self.quiet_seconds += elapsed
            if self.quiet_seconds >= self.thresholds.enter_seconds:
                self.is_silent = True
                self.loud_blocks = 0
        else:
            self.quiet_seconds = 0.0
