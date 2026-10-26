"""Chromatic — prismatic raindrop ripples with RGB-separated outlines."""
import math

import numpy as np
import pygame

import config
from .base import Effect

_MAX_RINGS = 5


class Chromatic(Effect):
    TIME_BASED = True
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

        self._hue = (self._hue + (0.004 + mid * 0.003) * step) % 1.0

        if beat > 0.60 and self._beat_prev <= 0.60:
            self._spawn(min(1.5, beat), W, H)
        self._beat_prev = beat

        self._auto_cd -= step
        if self._auto_cd <= 0 and len(self._rings) < 3:
            self._spawn(mid * 0.5, W, H)
            self._auto_cd = 55

        self._trail.fill(tuple(int(round(c ** step / 255 ** (step - 1))) for c in (210, 206, 214)), special_flags=pygame.BLEND_RGB_MULT)
        split = (2.0 + light[2] * 12.0 * accent) * display_scale

        self._rings = [
            entry for entry in self._rings
            if entry[2] <= entry[3]
        ]
        for ring in self._rings:
            cx, cy, _, max_r, speed, intensity, phase = ring

            ring[2] += (speed + bass * 1.0) * display_scale * step * motion_scale
            ring[6] += (0.025 + high * 0.035 + mid * 0.015) * step
            r = ring[2]

            fade = max(0.0, 1.0 - r / max_r)
            bright = min(1.0, intensity * fade * (1.0 + light[0] * 0.35 * accent))
            if bright < 0.04:
                continue

            thick = max(1, int((2 + fade * 4) * display_scale))
            warp = display_scale * (2.5 + high * 11.0 + bass * 5.0) * (0.45 + fade * 0.9)
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
