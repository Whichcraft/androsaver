"""Chromatic — raindrop ripples whose rims split into a clean spectral band.

Every ripple is one closed outline drawn as contiguous spectral strokes that
share the same shape, so the colours sit side by side like light through a
prism instead of crossing each other. Drops stay local and land away from
the ripples already on screen, which keeps separate drops from interfering.

  Bass   → expansion speed, drop strength
  Mid    → hue drift, idle drops
  Treble → width of the spectral split
  Beat   → rising edges spawn drops
"""
import math

import numpy as np
import pygame

from .base import Effect
from .utils import hsl

_MAX_RINGS = 5
_STEPS = 96
# Inner to outer edge of a ripple: red through violet.
_BAND_HUES = (0.0, 0.09, 0.30, 0.52, 0.66, 0.78)


class Chromatic(Effect):
    TIME_BASED = True
    TRAIL_ALPHA = 0
    RES_DIV     = 1

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self._rng = self.rng
        W = self.frame.width
        H = self.frame.height
        self._trail = pygame.Surface((W, H))
        self._trail.fill((0, 0, 0))
        # Ring: [cx, cy, r, max_r, speed, intensity, phase]
        self._rings: list = []
        self._hue = 0.0
        self._beat_prev = 0.0
        self._auto_cd = 45
        angle = np.arange(_STEPS) / _STEPS * math.tau
        self._angle, self._cos, self._sin = angle, np.cos(angle), np.sin(angle)

    def _draw(self, surf, waveform, fft, beat, tick):
        W, H = surf.get_size()
        display_scale = max(1.0, min(W, H) / 1080.0)
        step = self._frame_step()
        motion, light = self._audio_response(beat)
        bass, mid, high = motion
        motion_scale, accent = self._profile_response()

        if self._trail.get_width() != W or self._trail.get_height() != H:
            old_w, old_h = self._trail.get_size()
            sx,sy = W/old_w,H/old_h
            self._trail = pygame.transform.smoothscale(self._trail,(W,H))
            for ring in self._rings:
                ring[0] *= sx; ring[1] *= sy
                ring[2] *= min(sx,sy); ring[3] *= min(sx,sy)

        self._hue = (self._hue + (0.0015 + mid * 0.002) * step) % 1.0

        if beat > 0.60 and self._beat_prev <= 0.60:
            self._spawn(min(1.5, beat), W, H)
        self._beat_prev = beat

        self._auto_cd -= step
        if self._auto_cd <= 0 and len(self._rings) < 3:
            self._spawn(mid * 0.5, W, H)
            self._auto_cd = 55

        # A short wake behind each rim; a long one smeared the bands together.
        self._trail.fill(tuple(int(round(c ** step / 255 ** (step - 1))) for c in (120, 120, 128)), special_flags=pygame.BLEND_RGB_MULT)
        # Integer multiplication never reaches black; clear the residue.
        self._trail.fill((2, 2, 2), special_flags=pygame.BLEND_RGB_SUB)
        unit = min(W, H) / 360.0
        band_w = max(1.0, unit * (1.1 + light[2] * 1.1 * accent))

        self._rings = [
            entry for entry in self._rings
            if entry[2] <= entry[3]
        ]
        for ring in self._rings:
            cx, cy, _, max_r, speed, intensity, phase = ring

            ring[2] += (speed + bass * 1.0) * display_scale * step * motion_scale
            ring[6] += (0.012 + mid * 0.012) * step
            r = ring[2]

            fade = max(0.0, 1.0 - (r / max_r) ** 2)
            bright = min(1.0, intensity * fade * (1.0 + light[0] * 0.35 * accent))
            if bright < 0.04:
                continue

            # The outer rim, then a fainter ripple following inside it.
            for scale, gain in ((1.0, 1.0), (0.58, 0.5)):
                self._ripple(cx, cy, r * scale, max_r, phase, band_w * (0.55 + 0.45 * fade),
                             bright * gain)

        surf.blit(self._trail, (0, 0), special_flags=pygame.BLEND_RGB_ADD)

    def _ripple(self, cx, cy, radius, max_r, phase, band_w, bright):
        if radius < band_w * 2 or bright < 0.04:
            return
        # One gentle wobble shared by every band keeps the strokes parallel.
        wobble = min(radius * 0.04, max_r * 0.018)
        shape = radius + wobble * (np.sin(self._angle * 3.0 + phase)
                                   + 0.45 * np.cos(self._angle * 2.0 - phase * 1.3))
        width = int(math.ceil(band_w)) + 1
        middle = (len(_BAND_HUES) - 1) / 2.0
        for index, hue in enumerate(_BAND_HUES):
            rr = shape + (index - middle) * band_w
            xs = np.rint(cx + self._cos * rr).astype(int)
            ys = np.rint(cy + self._sin * rr).astype(int)
            color = hsl(self._hue * 0.15 + hue, 1.0, 0.5 * bright)
            pygame.draw.lines(self._trail, color, True, list(zip(xs.tolist(), ys.tolist())), width)

    def _spawn(self, bass, W, H):
        if len(self._rings) >= _MAX_RINGS:
            return
        # Drops stay local and land where the frame is emptiest, so the
        # spectral rims of separate drops rarely cross.
        max_r = min(W, H) * (0.23 + min(1.5, bass) * 0.10)
        best, best_gap = None, -math.inf
        for _ in range(12):
            cx = W * (0.10 + self._rng.random() * 0.80)
            cy = H * (0.10 + self._rng.random() * 0.80)
            gap = min((math.hypot(cx - ring[0], cy - ring[1]) - ring[3] for ring in self._rings),
                      default=math.inf)
            if gap > best_gap:
                best, best_gap = (cx, cy), gap
            if gap >= max_r:
                break
        speed = 1.6 + bass * 2.2
        inten = 0.6 + bass * 0.4
        self._rings.append([int(best[0]), int(best[1]), 0.0, max_r, speed, inten,
                            float(self._rng.random()) * math.tau])
