"""Morphogenesis — bounded Gray–Scott reaction-diffusion skin.

Two coupled chemical fields evolve into spots, coral, and cellular membranes.
The simulation is intentionally low-resolution and vectorized so it remains a
calm CPU effect rather than a per-pixel Python loop.
"""
import math

import numpy as np
import pygame
import pygame.surfarray as surfarray

import config
from .base import Effect


class Morphogenesis(Effect):
    TRAIL_ALPHA = 0
    RES_DIV = 4
    MAX_SUBSTEPS = 8
    MAX_DROPLETS = 6
    MAX_FIELD_VALUE = 1.0

    _PRESETS = ((0.025, 0.055), (0.036, 0.065), (0.055, 0.062))

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self._rng = np.random.default_rng(config.RNG_SEED)
        self._W = self._H = 1
        self._u = np.ones((1, 1), dtype=np.float32)
        self._v = np.zeros((1, 1), dtype=np.float32)
        self._lap_u = np.zeros((1, 1), dtype=np.float32)
        self._lap_v = np.zeros((1, 1), dtype=np.float32)
        self._field_surface = pygame.Surface((1, 1))
        self._scaled = pygame.Surface((1, 1))
        self._hue = 0.0
        self._beat_prev = 0.0
        self._reset_state()

    def _reset_state(self):
        W, H, _ = self._render_size()
        self._W, self._H = W, H
        self._u = np.ones((H, W), dtype=np.float32)
        self._v = np.zeros((H, W), dtype=np.float32)
        self._lap_u = np.empty_like(self._u)
        self._lap_v = np.empty_like(self._v)
        self._field_surface = pygame.Surface((W, H))
        self._scaled = pygame.Surface((max(1, config.WIDTH), max(1, config.HEIGHT)))
        yy, xx = np.ogrid[:H, :W]
        radius = max(2.0, min(W, H) * 0.10)
        cx, cy = W * 0.5, H * 0.5
        seed = (xx - cx) ** 2 + (yy - cy) ** 2 <= radius * radius
        self._v[seed] = 1.0
        self._u[seed] = 0.0
        self._hue = 0.0
        self._beat_prev = 0.0

    @staticmethod
    def _laplacian(field, out):
        out[:] = (
            np.roll(field, 1, axis=0) + np.roll(field, -1, axis=0)
            + np.roll(field, 1, axis=1) + np.roll(field, -1, axis=1)
            - field * 4.0
        )

    def _inject(self, count: int, strength: float):
        count = min(max(0, int(count)), self.MAX_DROPLETS)
        if count == 0:
            return
        radius = max(1, int(min(self._W, self._H) * 0.035))
        for _ in range(count):
            cx = int(self._rng.integers(radius, max(radius + 1, self._W - radius)))
            cy = int(self._rng.integers(radius, max(radius + 1, self._H - radius)))
            y0, y1 = max(0, cy - radius), min(self._H, cy + radius + 1)
            x0, x1 = max(0, cx - radius), min(self._W, cx + radius + 1)
            yy, xx = np.ogrid[y0:y1, x0:x1]
            mask = (xx - cx) ** 2 + (yy - cy) ** 2 <= radius * radius
            patch = self._v[y0:y1, x0:x1]
            patch[mask] = np.clip(patch[mask] + strength, 0.0, self.MAX_FIELD_VALUE)
            self._u[y0:y1, x0:x1][mask] = np.clip(
                self._u[y0:y1, x0:x1][mask] - strength * 0.7,
                0.0, self.MAX_FIELD_VALUE)

    def _render(self, surf):
        v = np.clip(self._v, 0.0, self.MAX_FIELD_VALUE)
        band = np.clip(v * 2.8, 0.0, 1.0)
        hue = (self._hue + band * 0.72 + self._u * 0.08) % 1.0
        r = np.clip(127.0 + 127.0 * np.sin(math.tau * hue), 0, 255)
        g = np.clip(127.0 + 127.0 * np.sin(math.tau * (hue + 0.333)), 0, 255)
        b = np.clip(127.0 + 127.0 * np.sin(math.tau * (hue + 0.667)), 0, 255)
        glow = np.clip(band * 1.8, 0.0, 1.0)
        colors = np.stack((r * glow, g * glow, b * glow), axis=-1).astype(np.uint8)
        pixels = surfarray.pixels3d(self._field_surface)
        try:
            pixels[:] = colors.transpose(1, 0, 2)
        finally:
            del pixels
        if self._field_surface.get_size() != surf.get_size():
            if self._scaled.get_size() != surf.get_size():
                self._scaled = pygame.Surface(surf.get_size())
            pygame.transform.smoothscale(self._field_surface, surf.get_size(), self._scaled)
            surf.blit(self._scaled, (0, 0), special_flags=pygame.BLEND_RGB_MAX)
        else:
            surf.blit(self._field_surface, (0, 0), special_flags=pygame.BLEND_RGB_MAX)

    def draw(self, surf, waveform, fft, beat, tick):
        W, H, _ = self._render_size()
        if (W, H) != (self._W, self._H):
            self._reset_state()
        bass = min(max(float(beat), 0.0), 1.5)
        mid = min(max(float(config.MID_ENERGY), 0.0), 4.0)
        high = min(max(float(config.TREBLE_ENERGY), 0.0), 4.0)
        self._hue = (self._hue + 0.002 + mid * 0.001 + high * 0.0005) % 1.0

        if bass > 0.7 and self._beat_prev <= 0.7:
            self._inject(2 + int(min(4.0, bass)), 0.35 + bass * 0.12)
        self._beat_prev = bass

        bpm = min(max(float(getattr(config, "BPM", 0.0)), 0.0), 240.0)
        morph_speed = max(0.15, bpm / 60.0)
        preset_pos = (tick * 0.002 * morph_speed + mid * 0.35) % len(self._PRESETS)
        preset = int(preset_pos)
        next_preset = (preset + 1) % len(self._PRESETS)
        mix = preset_pos - preset
        feed = (1.0 - mix) * self._PRESETS[preset][0] + mix * self._PRESETS[next_preset][0]
        kill = (1.0 - mix) * self._PRESETS[preset][1] + mix * self._PRESETS[next_preset][1]
        kill += high * 0.0015
        substeps = min(self.MAX_SUBSTEPS, 4 + int(high * 0.8))
        for _ in range(substeps):
            self._laplacian(self._u, self._lap_u)
            self._laplacian(self._v, self._lap_v)
            uvv = self._u * self._v * self._v
            self._u += 1.0 * self._lap_u - uvv + feed * (1.0 - self._u)
            self._v += 0.5 * self._lap_v + uvv - (feed + kill) * self._v
            np.clip(self._u, 0.0, self.MAX_FIELD_VALUE, out=self._u)
            np.clip(self._v, 0.0, self.MAX_FIELD_VALUE, out=self._v)

        self._render(surf)

    def release(self):
        self._field_surface = None
        self._scaled = None
        self._u = self._v = self._lap_u = self._lap_v = None
