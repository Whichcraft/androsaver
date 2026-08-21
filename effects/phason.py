"""Phason Bloom — bounded quasiperiodic wave interference."""
import math

import numpy as np
import pygame
import pygame.surfarray as surfarray

import config
from .base import Effect


class Phason(Effect):
    TRAIL_ALPHA = 8
    RES_DIV = 4
    MAX_WAVES = 11
    MAX_FIELD = 1.0

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self._W = self._H = 1
        self._x = self._y = None
        self._field = None
        self._field_work = None
        self._phases = np.zeros(self.MAX_WAVES, dtype=np.float32)
        self._angles = np.zeros(self.MAX_WAVES, dtype=np.float32)
        self._surface = pygame.Surface((1, 1))
        self._scaled = pygame.Surface((1, 1))
        self._hue = 0.0
        self._beat_prev = 0.0
        self._pulse = 0.0
        self._reset_state()

    def _reset_state(self):
        W, H, _ = self._render_size()
        self._W, self._H = W, H
        xx = np.linspace(-math.tau, math.tau, W, dtype=np.float32)
        yy = np.linspace(-math.tau, math.tau, H, dtype=np.float32)
        self._x, self._y = np.meshgrid(xx, yy)
        self._field = np.zeros((H, W), dtype=np.float32)
        self._field_work = np.zeros_like(self._field)
        self._surface = pygame.Surface((W, H))
        self._scaled = pygame.Surface((max(1, config.WIDTH), max(1, config.HEIGHT)))
        self._angles = np.arange(self.MAX_WAVES, dtype=np.float32) * (math.tau / self.MAX_WAVES)
        self._phases.fill(0.0)
        self._hue = 0.0
        self._beat_prev = 0.0
        self._pulse = 0.0

    def _render(self, surf, high):
        field = np.clip(self._field, -self.MAX_FIELD, self.MAX_FIELD)
        intensity = np.clip((field + 1.0) * 0.5, 0.0, 1.0)
        hue = (self._hue + intensity * 0.72 + high * 0.03) % 1.0
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
        bpm = min(max(float(getattr(config, "BPM", 0.0)), 0.0), 240.0)
        if bass > 0.7 and self._beat_prev <= 0.7:
            self._pulse = min(1.0, self._pulse + 0.75)
        self._beat_prev = bass
        self._pulse *= 0.94
        wave_count = min(self.MAX_WAVES, 5 + 2 * int(mid))
        speed = 0.002 + bpm * 0.000004 + high * 0.001
        self._angles[:wave_count] += 0.0007 + speed
        self._phases[:wave_count] = self._phases[:wave_count] + speed + self._pulse * 0.008
        field = self._field_work
        field.fill(0.0)
        for index in range(wave_count):
            angle = self._angles[index]
            projection = self._x * math.cos(float(angle)) + self._y * math.sin(float(angle))
            field += np.cos(projection + self._phases[index])
        field /= float(wave_count)
        self._field[:] = np.clip(field + self._pulse * np.cos(self._x * 0.5 + self._y * 0.5), -1.0, 1.0)
        self._hue = (self._hue + 0.001 + mid * 0.0007) % 1.0
        self._render(surf, high)

    def release(self):
        self._surface = None
        self._scaled = None
        self._x = self._y = self._field = None
        self._field_work = None
