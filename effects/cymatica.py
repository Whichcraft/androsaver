"""Cymatica — analytic Chladni-like nodal plates with bounded sand."""
import math

import numpy as np
import pygame
import pygame.surfarray as surfarray

import config
from .base import Effect


class Cymatica(Effect):
    TIME_BASED = True
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
        self._mode_weights = np.zeros(self.MAX_MODES)
        self._mode_weights[0] = 1.0
        self._audio = np.zeros(3)
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
        # Descend the actual plate amplitude, independent of pixel resolution.
        field = np.zeros_like(self._px)
        gx = np.zeros_like(field)
        gy = np.zeros_like(field)
        for weight, (m, n) in zip(self._mode_weights, self._MODES):
            x, y = math.pi * m * self._px, math.pi * n * self._py
            field += weight * np.cos(x) * np.cos(y)
            gx -= weight * math.pi * m * np.sin(x) * np.cos(y)
            gy -= weight * math.pi * n * np.cos(x) * np.sin(y)
        self._px -= np.clip(field * gx * 0.0008 * self._frame_step(), -0.002 * self._frame_step(), 0.002 * self._frame_step())
        self._py -= np.clip(field * gy * 0.0008 * self._frame_step(), -0.002 * self._frame_step(), 0.002 * self._frame_step())
        np.clip(self._px, -0.995, 0.995, out=self._px)
        np.clip(self._py, -0.995, 0.995, out=self._py)

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
        self._audio, light = self._audio_response(beat)
        bass, mid, high = self._audio
        self._hue = (self._hue + (0.0004 + high * 0.0002) * self._frame_step()) % 1.0
        # Smoothed band weights avoid argmax switches and sawtooth resets.
        spectrum = np.abs(np.asarray(fft).reshape(-1)[:128])
        energies = np.array([float(np.mean(b)) if len(b) else 0.0
                             for b in np.array_split(spectrum, self.MAX_MODES)])
        target = energies + 0.05
        target /= target.sum()
        self._mode_weights = self._smooth(self._mode_weights, target, 0.003)
        self._field.fill(0)
        for weight, mode in zip(self._mode_weights, self._MODES):
            self._mode_field(mode, self._mode_a)
            self._field += weight * self._mode_a
        self._update_particles(0.0, bass, high)
        self._render(surf, high)
        self._beat_prev = bass

    def release(self):
        self._surface = None
        self._scaled = None
        self._x = self._y = self._field = None
        self._mode_a = self._mode_b = None
        self._px = self._py = None
