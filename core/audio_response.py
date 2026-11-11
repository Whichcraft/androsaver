"""Audio-to-visual response without devices or display ownership."""
from collections import deque
from dataclasses import dataclass
import math

import numpy as np

from core.silence import SilenceGate


@dataclass(frozen=True)
class ResponseValues:
    beat: float
    draw_beat: float
    mid: float
    treble: float
    envelopes: tuple[float, float, float]
    bpm: float
    phase: float
    is_silent: bool
    using_tap: bool


class AudioResponse:
    def __init__(self, gate: SilenceGate, *, beat_floor=.015,
                 mid_floor=.020, treble_floor=.018):
        self.gate = gate
        self.beat_floor, self.mid_floor, self.treble_floor = beat_floor, mid_floor, treble_floor
        self.is_silent = gate.is_silent
        self.energy_hist = deque(maxlen=40)
        self.energy_sum = self.beat_decay = 0.
        self.target_rms = .05
        self.rms_buf = deque([self.target_rms], maxlen=30)
        self._visual_seconds = 0.
        self._phase_last_onset = self._phase_anchor_time = 0.

    def compute_draw_beat(self, beat, auto_gain, gain):
        if auto_gain and self.rms_buf:
            cur_rms = float(np.mean(self.rms_buf)) + 1e-9
            auto_scale = max(.5, min(self.target_rms / cur_rms, 2.))
            return beat * auto_scale * gain
        return beat * gain

    def phase(self, audio_time, bpm, onset_time, *, tick, fps,
              is_silent, using_tap, tap_anchor_tick):
        """Publish a bounded phase that reaches zero at predicted beat times."""
        try:
            now = float(audio_time)
            tempo = float(bpm)
            onset = float(onset_time)
        except (TypeError, ValueError):
            now = tempo = onset = float("nan")

        if is_silent:
            phase = (tick / max(1.0, fps) * 0.25) % 1.0
        elif using_tap and np.isfinite(tempo) and tempo > 0:
            phase = ((tick - tap_anchor_tick) * tempo /
                     (60.0 * max(1.0, fps))) % 1.0
        elif (
            np.isfinite(now) and np.isfinite(tempo) and np.isfinite(onset)
            and 60.0 <= tempo <= 200.0 and onset > 0.0
        ):
            if onset != self._phase_last_onset or self._phase_anchor_time <= 0.0:
                self._phase_last_onset = onset
                self._phase_anchor_time = onset
            if now < self._phase_anchor_time:
                self._phase_anchor_time = now
            phase = ((now - self._phase_anchor_time) * tempo / 60.0) % 1.0
        else:
            phase = (tick / max(1.0, fps) * 0.25) % 1.0
        return float(phase if np.isfinite(phase) else 0.0)

    def update(self, snapshot, *, dt, tick, fps, tap_bpm, tap_expiry,
               tap_anchor_tick, now, previous_bpm, gain, auto_gain):
        step_seconds = max(0., min(.1, dt))
        self._visual_seconds += step_seconds
        raw_beat, mid_e, treble_e = snapshot.raw_beat, snapshot.mid_energy, snapshot.treble_energy
        bpm, audio_time = snapshot.bpm, snapshot.audio_time
        generation, envelopes = snapshot.generation, snapshot.envelopes
        rms = float(np.sqrt(np.mean(snapshot.waveform ** 2)))
        fft_mean = float(np.mean(snapshot.fft)) if len(snapshot.fft) else 0.0

        was_silent = self.is_silent
        music_activity = max(
            float(mid_e), float(treble_e), float(envelopes[0]),
            float(envelopes[1]), float(envelopes[2]),
        )
        self.gate.update(rms, fft_mean, audio_time, generation, music_activity)
        self.is_silent = self.gate.is_silent
        if was_silent and not self.is_silent:
            self._phase_last_onset = 0.0
            self._phase_anchor_time = 0.0

        if self.is_silent:
            raw_beat = 0.0
            # Gentle breathing/LFO modulation to keep the visuals alive and dynamic in no-input/silent mode
            t_sec = self._visual_seconds
            lfo_mid = 0.5 + 0.5 * math.sin(t_sec * 1.2)
            lfo_treble = 0.5 + 0.5 * math.cos(t_sec * 0.9)
            lfo_beat = 0.5 + 0.5 * math.sin(t_sec * 2.0)

            mid_e = self.mid_floor * (0.8 + 0.4 * lfo_mid)
            treble_e = self.treble_floor * (0.8 + 0.4 * lfo_treble)

            # Simulate a very gentle ambient pulse at the previous BPM (or fallback 120)
            bpm_rate = (previous_bpm or 120.0) / 60.0
            pulse = max(0.0, math.sin(t_sec * math.pi * bpm_rate)) ** 4
            silence_beat_floor = self.beat_floor * (0.7 + 0.6 * pulse + 0.2 * lfo_beat)

            self.energy_hist.clear()
            self.energy_sum = 0.0
        else:
            silence_beat_floor = 0.0
            mid_e = max(mid_e, self.mid_floor * 0.5)
            treble_e = max(treble_e, self.treble_floor * 0.5)

        output_envelopes = ((0., 0., 0.) if self.is_silent else
                            tuple(float(np.clip(e, 0., 6.)) for e in envelopes))
        if tap_bpm > 0 and now < tap_expiry:
            self.bpm = tap_bpm
            self.using_tap = True
        else:
            self.bpm = bpm
            self.using_tap = False

        phase = self.phase(audio_time, self.bpm, snapshot.onset_time,
                           tick=tick, fps=fps, is_silent=self.is_silent,
                           using_tap=self.using_tap, tap_anchor_tick=tap_anchor_tick)

        if len(self.energy_hist) == self.energy_hist.maxlen:
            self.energy_sum -= self.energy_hist[0]
        self.energy_hist.append(raw_beat)
        self.energy_sum += raw_beat
        avg = self.energy_sum / len(self.energy_hist) if self.energy_hist else 1e-6
        impulse = max(0.0, min(raw_beat / (avg + 1e-6) - 1.0, 3.0))
        self.beat_decay = max(impulse, self.beat_decay * (0.82 if self.is_silent else 0.90) ** (step_seconds * 60))
        self.beat = max(self.beat_decay, silence_beat_floor if self.is_silent else 0.0)

        self.rms_buf.append(rms)
        self.draw_beat = self.compute_draw_beat(self.beat, auto_gain, gain)

        return ResponseValues(self.beat, self.draw_beat, mid_e, treble_e,
                              output_envelopes, self.bpm, phase,
                              self.is_silent, self.using_tap)
