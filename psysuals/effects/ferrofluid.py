"""Ferrofluid Oracle — bounded magnetic potential contour field."""
import math

import numpy as np
import pygame
import pygame.surfarray as surfarray

import config
from .base import Effect


class Ferrofluid(Effect):
    TRAIL_ALPHA = 40
    RES_DIV = 4
    MAX_POLES = 5
    MAX_CONTOURS = 6
    MAX_FIELD = 8.0

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self._rng = np.random.default_rng(config.RNG_SEED)
        self._W = self._H = 1
        self._x = self._y = None
        self._field = None
        self._field_dx = self._field_dy = None
        self._poles = None
        self._strength = None
        self._surface = pygame.Surface((1, 1))
        self._scaled = pygame.Surface((1, 1))
        self._hue = float(self._rng.random())
        self._beat_prev = 0.0
        self._invert_timer = 0
        self._reset_state()

    def _reset_state(self):
        W, H, _ = self._render_size()
        self._W, self._H = W, H
        xx = np.linspace(-1.0, 1.0, W, dtype=np.float32)
        yy = np.linspace(-1.0, 1.0, H, dtype=np.float32)
        self._x, self._y = np.meshgrid(xx, yy)
        self._field = np.zeros((H, W), dtype=np.float32)
        self._field_dx = np.zeros_like(self._field)
        self._field_dy = np.zeros_like(self._field)
        self._poles = self._rng.uniform(-0.65, 0.65, (self.MAX_POLES, 2)).astype(np.float32)
        self._strength = self._rng.uniform(0.6, 1.2, self.MAX_POLES).astype(np.float32)
        self._surface = pygame.Surface((W, H))
        self._scaled = pygame.Surface((max(1, config.WIDTH), max(1, config.HEIGHT)))
        self._hue = float(self._rng.random())
        self._beat_prev = 0.0
        self._invert_timer = 0

    def _render(self, surf, high):
        field = np.clip(self._field, -self.MAX_FIELD, self.MAX_FIELD)
        bands = np.abs(np.sin(field * self.MAX_CONTOURS * 0.45))
        intensity = np.clip(0.18 + bands * 0.75 + np.maximum(field, 0.0) * 0.04, 0.0, 1.0)
        hue = (self._hue + field * 0.035 + high * 0.04) % 1.0
        red = np.clip((np.sin(hue * math.tau) * 127 + 128) * intensity, 0, 255)
        green = np.clip((np.sin((hue + 0.333) * math.tau) * 127 + 128) * intensity, 0, 255)
        blue = np.clip((np.sin((hue + 0.667) * math.tau) * 127 + 128) * intensity, 0, 255)
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
        if bass > 0.7 and self._beat_prev <= 0.7:
            self._invert_timer = 12
        self._beat_prev = bass
        self._invert_timer = max(0, self._invert_timer - 1)
        angle = tick * (0.006 + mid * 0.001)
        cos_a, sin_a = math.cos(angle), math.sin(angle)
        px = self._poles[:, 0] * cos_a - self._poles[:, 1] * sin_a
        py = self._poles[:, 0] * sin_a + self._poles[:, 1] * cos_a
        field = np.zeros_like(self._field)
        for index in range(self.MAX_POLES):
            dx = self._x - px[index]
            dy = self._y - py[index]
            distance = np.sqrt(dx * dx + dy * dy + 0.0125)
            sign = -1.0 if self._invert_timer and index == 0 else 1.0
            field += sign * self._strength[index] / distance
        self._field[:] = np.clip(field, -self.MAX_FIELD, self.MAX_FIELD)
        self._field_dx[:, 1:-1] = (self._field[:, 2:] - self._field[:, :-2]) * 0.5
        self._field_dy[1:-1, :] = (self._field[2:, :] - self._field[:-2, :]) * 0.5
        self._field_dx[:, 0] = self._field[:, 1] - self._field[:, 0]
        self._field_dx[:, -1] = self._field[:, -1] - self._field[:, -2]
        self._field_dy[0, :] = self._field[1, :] - self._field[0, :]
        self._field_dy[-1, :] = self._field[-1, :] - self._field[-2, :]
        self._hue = (self._hue + 0.001 + high * 0.0008) % 1.0
        self._render(surf, high)

    def get_motion_field(self):
        return self._field_dx, self._field_dy

    def release(self):
        self._surface = None
        self._scaled = None
        self._x = self._y = self._field = self._field_dx = self._field_dy = None
        self._poles = self._strength = None
