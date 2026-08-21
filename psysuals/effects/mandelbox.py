"""Mandelbox Temple — bounded CPU fractal escape-field architecture."""
import math

import numpy as np
import pygame
import pygame.surfarray as surfarray

import config
from .base import Effect


class Mandelbox(Effect):
    TRAIL_ALPHA = 14
    RES_DIV = 5
    MAX_ITERATIONS = 18
    MAX_FIELD = 1.0

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self._W = self._H = 1
        self._x = self._y = None
        self._field = None
        self._orbit = None
        self._surface = pygame.Surface((1, 1))
        self._scaled = pygame.Surface((1, 1))
        self._hue = 0.0
        self._reset_state()

    def _reset_state(self):
        W, H, _ = self._render_size()
        self._W, self._H = W, H
        xx = np.linspace(-1.4, 1.4, W, dtype=np.float32)
        yy = np.linspace(-1.0, 1.0, H, dtype=np.float32)
        self._x, self._y = np.meshgrid(xx, yy)
        self._field = np.zeros((H, W), dtype=np.float32)
        self._orbit = np.ones((H, W), dtype=np.float32)
        self._surface = pygame.Surface((W, H))
        self._scaled = pygame.Surface((max(1, config.WIDTH), max(1, config.HEIGHT)))
        self._hue = 0.0

    def _render(self, surf, high):
        intensity = np.clip(self._field, 0.0, self.MAX_FIELD)
        hue = (self._hue + self._orbit * 0.22 + high * 0.04) % 1.0
        glow = np.clip(intensity * 1.7, 0.0, 1.0)
        red = np.clip((np.sin(hue * math.tau) * 127 + 128) * glow, 0, 255)
        green = np.clip((np.sin((hue + 0.333) * math.tau) * 127 + 128) * glow, 0, 255)
        blue = np.clip((np.sin((hue + 0.667) * math.tau) * 127 + 128) * glow, 0, 255)
        colors = np.stack((red, green, blue), axis=-1).astype(np.uint8)
        pixels = surfarray.pixels3d(self._surface)
        try:
            pixels[:] = colors.transpose(1, 0, 2)
        finally:
            del pixels
        if self._surface.get_size() != surf.get_size():
            if self._scaled.get_size() != surf.get_size():
                self._scaled = pygame.Surface(surf.get_size())
            pygame.transform.smoothscale(self._surface, surf.get_size(), self._scaled)
            surf.blit(self._scaled, (0, 0), special_flags=pygame.BLEND_RGB_MAX)
        else:
            surf.blit(self._surface, (0, 0), special_flags=pygame.BLEND_RGB_MAX)

    def draw(self, surf, waveform, fft, beat, tick):
        W, H, _ = self._render_size()
        if (W, H) != (self._W, self._H):
            self._reset_state()
        bass = min(max(float(beat), 0.0), 1.5)
        mid = min(max(float(config.MID_ENERGY), 0.0), 4.0)
        high = min(max(float(config.TREBLE_ENERGY), 0.0), 4.0)
        fold = 1.72 + bass * 0.14 + mid * 0.025
        angle = tick * (0.004 + high * 0.001)
        c, s = math.cos(angle), math.sin(angle)
        x = self._x * c - self._y * s
        y = self._x * s + self._y * c
        orbit = np.full_like(self._field, 1.0)
        escaped = np.zeros_like(self._field, dtype=bool)
        for iteration in range(self.MAX_ITERATIONS):
            x = np.clip(np.abs(x * fold - 0.5) - 0.35, -2.0, 2.0)
            y = np.clip(np.abs(y * fold + 0.25) - 0.35, -2.0, 2.0)
            radius2 = x * x + y * y
            orbit = np.minimum(orbit, np.abs(x) + np.abs(y))
            newly_escaped = (~escaped) & (radius2 > 4.0)
            self._field[newly_escaped] = iteration / float(self.MAX_ITERATIONS)
            escaped |= newly_escaped
            x = x * 0.94 + self._x * 0.04
            y = y * 0.94 + self._y * 0.04
        self._field[~escaped] = 1.0
        self._orbit[:] = np.clip(orbit, 0.0, 1.0)
        self._hue = (self._hue + 0.001 + mid * 0.0008) % 1.0
        self._render(surf, high)

    def release(self):
        self._surface = None
        self._scaled = None
        self._x = self._y = self._field = self._orbit = None
