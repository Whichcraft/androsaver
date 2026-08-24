"""Cymatica — analytic Chladni-like nodal plates with bounded sand."""
import math

import numpy as np
import pygame
import pygame.surfarray as surfarray

import config
from .base import Effect


class Cymatica(Effect):
    TRAIL_ALPHA = 40
    RES_DIV = 4
    MAX_PARTICLES = 700
    MAX_MODES = 6
    MAX_FIELD_VALUE = 1.0

    _MODES = ((1, 2), (2, 3), (3, 4), (4, 5), (2, 5), (3, 6))

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self._rng = np.random.default_rng(config.RNG_SEED)
        self._W = self._H = 1
        self._x = self._y = None
        self._field = None
        self._mode_a = self._mode_b = None
        self._px = self._py = None
        self._surface = pygame.Surface((1, 1))
        self._scaled = pygame.Surface((1, 1))
        self._hue = float(self._rng.random())
        self._beat_prev = 0.0
        self._reset_state()

    def _reset_state(self):
        W, H, _ = self._render_size()
        self._W, self._H = W, H
        xx = np.linspace(-1.0, 1.0, W, dtype=np.float32)
        yy = np.linspace(-1.0, 1.0, H, dtype=np.float32)
        self._x, self._y = np.meshgrid(xx, yy)
        self._field = np.zeros((H, W), dtype=np.float32)
        self._mode_a = np.zeros_like(self._field)
        self._mode_b = np.zeros_like(self._field)
        self._surface = pygame.Surface((W, H))
        self._scaled = pygame.Surface((max(1, config.WIDTH), max(1, config.HEIGHT)))
        self._px = self._rng.uniform(-1.0, 1.0, self.MAX_PARTICLES).astype(np.float32)
        self._py = self._rng.uniform(-1.0, 1.0, self.MAX_PARTICLES).astype(np.float32)
        self._hue = float(self._rng.random())
        self._beat_prev = 0.0

    def _mode_field(self, mode, out):
        m, n = mode
        out[:] = np.cos(math.pi * m * self._x) * np.cos(math.pi * n * self._y)

    def _update_particles(self, mix, bass, high):
        strength = 0.004 + bass * 0.008
        # A cheap analytic drift keeps particles near low-amplitude nodal lines.
        gx = np.sin(math.pi * self._mode_a.shape[1] * self._px)
        gy = np.sin(math.pi * self._mode_a.shape[0] * self._py)
        self._px += -gx * strength + np.sin(self._py * 5.0 + mix) * high * 0.001
        self._py += -gy * strength + np.cos(self._px * 4.0 + mix) * high * 0.001
        self._px = ((self._px + 1.0) % 2.0) - 1.0
        self._py = ((self._py + 1.0) % 2.0) - 1.0

    def _render(self, surf, high):
        line = np.clip(np.exp(-np.abs(self._field) * (12.0 - high * 1.5)), 0.0, 1.0)
        hue = (self._hue + line * 0.72) % 1.0
        r = np.clip((np.sin(hue * math.tau) * 127 + 128) * line, 0, 255)
        g = np.clip((np.sin((hue + 0.333) * math.tau) * 127 + 128) * line, 0, 255)
        b = np.clip((np.sin((hue + 0.667) * math.tau) * 127 + 128) * line, 0, 255)
        colors = np.stack((r, g, b), axis=-1).astype(np.uint8)
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
        for x, y in zip(self._px[::2], self._py[::2]):
            pygame.draw.circle(
                surf, (220, 245, 255),
                (int((x + 1.0) * surf.get_width() * 0.5),
                 int((y + 1.0) * surf.get_height() * 0.5)), 1)

    def draw(self, surf, waveform, fft, beat, tick):
        W, H, _ = self._render_size()
        if (W, H) != (self._W, self._H):
            self._reset_state()
        bass = min(max(float(beat), 0.0), 1.5)
        mid = min(max(float(config.MID_ENERGY), 0.0), 4.0)
        high = min(max(float(config.TREBLE_ENERGY), 0.0), 4.0)
        self._hue = (self._hue + 0.001 + high * 0.001) % 1.0
        fft_arr = np.asarray(fft).reshape(-1)
        if fft_arr.size:
            dominant = int(np.argmax(np.abs(fft_arr[: min(fft_arr.size, 128)])))
        else:
            dominant = 0
        a_index = dominant % self.MAX_MODES
        b_index = (a_index + 1 + int(mid)) % self.MAX_MODES
        phase = (tick * (0.004 + max(0.0, float(getattr(config, "BPM", 0.0))) * 0.00001) + mid * 0.12) % 1.0
        self._mode_field(self._MODES[a_index], self._mode_a)
        self._mode_field(self._MODES[b_index], self._mode_b)
        self._field[:] = self._mode_a * (1.0 - phase) + self._mode_b * phase
        self._update_particles(phase, bass, high)
        self._render(surf, high)
        self._beat_prev = bass

    def release(self):
        self._surface = None
        self._scaled = None
        self._x = self._y = self._field = None
        self._mode_a = self._mode_b = None
        self._px = self._py = None
