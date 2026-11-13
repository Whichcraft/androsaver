"""Caustics — light on the floor of a pool.

Sunlight passes through a moving water surface and gathers on the sand below
into a shifting net of bright lines. The surface is a sum of travelling
waves plus ripples from drops; the brightness on the floor follows how the
surface focuses the light (the inverse of the Jacobian of the refracted
light map), which produces the characteristic sharp, bright caustic lines.

  Bass   → wave height: the net sharpens and brightens
  Mid    → wind: the waves travel faster
  Treble → glints on the brightest crossings
  Beat   → a drop lands and rings outward
"""
import math

import numpy as np
import pygame

from .base import Effect
from .utils import hsl

_GRID_W = 256            # internal grid width; height follows the aspect ratio
_MAX_RIPPLES = 6


class Caustics(Effect):
    TIME_BASED  = True
    TRAIL_ALPHA = 255       # every frame is fully repainted; no trails

    # (wavelength in world units, relative height, direction offset, speed)
    _WAVES = ((0.62, 1.00, 0.00, 0.55), (0.41, 0.70, 1.10, 0.62),
              (0.29, 0.45, 2.30, 0.78), (0.53, 0.60, 3.70, 0.50),
              (0.22, 0.30, 4.90, 0.90))

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.time = 0.0
        self._phase = np.zeros(len(self._WAVES))
        self.hue = 0.50
        self._wind = 0.0
        self._ripples = []          # [x, y, age, strength]
        self._beat_prev = 0.0
        self._grid_size = None
        self._surface = self._scaled = None

    # ------------------------------------------------------------------

    def _grid(self, W, H):
        gw = min(_GRID_W, max(8, W))
        gh = max(6, round(gw * H / max(1, W)))
        if self._grid_size != (gw, gh):
            self._grid_size = (gw, gh)
            aspect = W / max(1, H)
            self._x = np.linspace(0.0, 2.0 * aspect, gw)[None, :]
            self._y = np.linspace(0.0, 2.0, gh)[:, None]
            self._dx = self._x[0, 1] - self._x[0, 0] if gw > 1 else 1.0
            self._dy = self._y[1, 0] - self._y[0, 0] if gh > 1 else 1.0
            # Static sand: soft blotches and a fine grain, fixed per size.
            rng = np.random.default_rng(self.options.seed + 7)
            grain = rng.random((gh, gw))
            blotch = sum(np.sin(self._x * f + p) * np.cos(self._y * g + q)
                         for f, g, p, q in rng.uniform(1.5, 6.0, (4, 4)))
            self._sand = 0.78 + 0.10 * blotch / 4.0 + 0.10 * (grain - 0.5)
        return gw, gh

    def _height(self, bass):
        """Water surface height on the grid."""
        x, y = self._x, self._y
        h = np.zeros((len(y), x.shape[1]))
        for (length, amp, offset, _), phase in zip(self._WAVES, self._phase):
            k = math.tau / length
            angle = self._wind + offset
            h += amp * np.sin(k * (x * math.cos(angle) + y * math.sin(angle)) - phase)
        h *= 0.0075 * (1.0 + 1.3 * bass)
        for rx, ry, age, strength in self._ripples:
            r = np.sqrt((x - rx) ** 2 + (y - ry) ** 2)
            front = r - 0.55 * age
            h += (0.010 * strength * math.exp(-age / 1.4)
                  * np.exp(-(front / 0.22) ** 2) * np.cos(front * 14.0))
        return h

    def _light(self, h, focus):
        """Brightness on the floor: inverse Jacobian of the refracted light map."""
        hy, hx = np.gradient(h, self._dy, self._dx)
        hxy, hxx = np.gradient(hx, self._dy, self._dx)
        hyy, _ = np.gradient(hy, self._dy, self._dx)
        jac = (1.0 + focus * hxx) * (1.0 + focus * hyy) - (focus * hxy) ** 2
        level = np.clip(1.0 / (np.abs(jac) + 0.22), 0.0, 4.5)
        # A separable [1, 2, 1] blur: the brightest folds are thinner than a
        # grid cell and otherwise break up into dots when scaled up.
        level = (np.roll(level, 1, 0) + 2.0 * level + np.roll(level, -1, 0)) * 0.25
        return (np.roll(level, 1, 1) + 2.0 * level + np.roll(level, -1, 1)) * 0.25

    # ------------------------------------------------------------------

    def _draw(self, surf, waveform, fft, beat, tick):
        W, H = surf.get_size()
        step = self._frame_step()
        seconds = step / 60.0
        motion, light = self._audio_response(beat)
        bass, mid, high = (float(v) for v in motion)
        motion_scale, accent = self._profile_response()
        flash = float(light[0]) * accent

        self.time += seconds
        self.hue = (self.hue + (0.004 + mid * 0.004) * seconds) % 1.0
        self._wind = (self._wind + (0.02 + mid * 0.05) * seconds * motion_scale) % math.tau
        speeds = np.array([w[3] for w in self._WAVES])
        lengths = np.array([w[0] for w in self._WAVES])
        self._phase = (self._phase + math.tau / lengths * speeds
                       * (0.18 + 0.30 * mid) * seconds * motion_scale) % math.tau

        gw, gh = self._grid(W, H)
        aspect = W / max(1, H)
        if beat > 0.6 and self._beat_prev <= 0.6 and len(self._ripples) < _MAX_RIPPLES:
            self._ripples.append([float(self.rng.uniform(0.15, 2.0 * aspect - 0.15)),
                                  float(self.rng.uniform(0.15, 1.85)), 0.0, min(1.5, beat)])
        self._beat_prev = beat
        for ripple in self._ripples:
            ripple[2] += seconds
        self._ripples = [r for r in self._ripples if r[2] < 5.0]

        level = self._light(self._height(bass), focus=1.0 + 0.4 * bass)
        net = np.clip((level - 1.0) / 2.2, 0.0, 1.0) ** 0.8

        # Water tint deepens toward the top of the frame; the sand stays warm.
        depth = (0.55 + 0.45 * self._y / 2.0)
        floor = np.array(hsl(self.hue + 0.04, 0.75, 0.40), dtype=float) / 255.0
        sand = np.array(hsl(0.11, 0.40, 0.55), dtype=float) / 255.0
        ray = np.array(hsl(self.hue - 0.03, 0.80, 0.78), dtype=float) / 255.0
        base = (self._sand * depth)[..., None] * (0.65 * floor + 0.35 * sand)
        glow = 0.70 + 0.40 * bass + 0.70 * flash
        rgb = base * (0.70 + 0.30 * net[..., None]) + net[..., None] * ray * glow
        if high > 0.05:
            # Treble glints on the bright crossings, and the light net
            # shimmers in a cooler tint with the highs.
            sparkle = (level > 2.8) & (self.rng.random(level.shape) < 0.30 * high)
            rgb[sparkle] = 1.0
            rgb += net[..., None] * np.array(hsl(self.hue + 0.12, 0.9, 0.5), dtype=float) / 255.0 * 0.9 * high

        pixels = np.clip(rgb * 255.0, 0, 255).astype(np.uint8)
        if self._surface is None or self._surface.get_size() != (gw, gh):
            self._surface = pygame.Surface((gw, gh))
        pygame.surfarray.blit_array(self._surface, pixels.transpose(1, 0, 2))
        if (gw, gh) == (W, H):
            surf.blit(self._surface, (0, 0))
            return
        if self._scaled is None or self._scaled.get_size() != (W, H):
            self._scaled = pygame.Surface((W, H))
        pygame.transform.smoothscale(self._surface, (W, H), self._scaled)
        surf.blit(self._scaled, (0, 0))
