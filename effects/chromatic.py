"""Chromatic — prismatic raindrop ripples with RGB-separated outlines."""
import math

import numpy as np
import pygame

import config
from .base import Effect

_MAX_RINGS = 14


class Chromatic(Effect):
    TRAIL_ALPHA = 0
    RES_DIV     = 1

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self._rng = np.random.default_rng(config.RNG_SEED)
        W = config.WIDTH
        H = config.HEIGHT
        self._trail = pygame.Surface((W, H))
        self._trail.fill((0, 0, 0))
        # Ring: [cx, cy, r, max_r, speed, intensity, phase]
        self._rings: list = []
        self._hue = 0.0
        self._beat_prev = 0.0
        self._auto_cd = 45

    def draw(self, surf, waveform, fft, beat, tick):
        W, H = surf.get_size()
        bass = beat
        mid = config.MID_ENERGY
        high = config.TREBLE_ENERGY

        if self._trail.get_width() != W or self._trail.get_height() != H:
            self._trail = pygame.Surface((W, H))
            self._trail.fill((0, 0, 0))

        self._hue = (self._hue + 0.004 + mid * 0.003) % 1.0

        if bass > 0.60 and self._beat_prev <= 0.60:
            self._spawn(bass, W, H)
        self._beat_prev = bass

        self._auto_cd -= 1
        if self._auto_cd <= 0 and len(self._rings) < 3:
            self._spawn(mid * 0.5, W, H)
            self._auto_cd = 55

        self._trail.fill((232, 228, 236), special_flags=pygame.BLEND_RGB_MULT)
        split = 2.0 + high * 12.0

        self._rings = [
            entry for entry in self._rings
            if entry[2] <= entry[3]
        ]
        for ring in self._rings:
            cx, cy, _, max_r, speed, intensity, phase = ring

            ring[2] += speed + bass * 3.0
            ring[6] += 0.08 + high * 0.22 + mid * 0.05
            r = ring[2]

            fade = max(0.0, 1.0 - r / max_r)
            bright = intensity * fade
            if bright < 0.04:
                continue

            thick = max(1, int(2 + fade * 4))
            warp = (2.5 + high * 11.0 + bass * 5.0) * (0.45 + fade * 0.9)
            base_r = max(1.0, r)
            channels = (
                ((255, 40, 80), -split, phase),
                ((60, 255, 80), 0.0, phase + 1.5),
                ((80, 120, 255), split, phase + 3.1),
            )
            for color, offset, ring_phase in channels:
                pts = self._wave_points(cx, cy, base_r + offset, warp, ring_phase)
                scaled = tuple(min(255, int(c * bright * 1.6)) for c in color)
                if len(pts) >= 3:
                    pygame.draw.lines(self._trail, scaled, True, pts, thick)

        surf.blit(self._trail, (0, 0), special_flags=pygame.BLEND_RGB_ADD)

    def _spawn(self, bass, W, H):
        if len(self._rings) >= _MAX_RINGS:
            return
        cx = W // 2 + int(self._rng.integers(-W // 6, W // 6 + 1))
        cy = H // 2 + int(self._rng.integers(-H // 6, H // 6 + 1))
        max_r = math.hypot(max(cx, W - cx), max(cy, H - cy)) * 1.1
        speed = 3.0 + bass * 5.0
        inten = 0.5 + bass * 0.5
        self._rings.append([cx, cy, 0.0, max_r, speed, inten, float(self._rng.random()) * math.tau])

    def _wave_points(self, cx, cy, radius, warp, phase):
        steps = 72
        a = np.arange(steps) / steps * math.tau
        rr = radius
        rr += np.sin(a * 3.0 + phase) * warp
        rr += np.sin(a * 7.0 - phase * 0.7) * warp * 0.38
        rr += np.cos(a * 2.0 + phase * 1.6) * warp * 0.18
        xs = np.round(cx + np.cos(a) * rr).astype(int)
        ys = np.round(cy + np.sin(a) * rr).astype(int)
        return list(zip(xs.tolist(), ys.tolist()))
