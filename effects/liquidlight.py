"""Liquid Light — bounded semi-Lagrangian fluorescent dye simulation."""
import math

import numpy as np
import pygame
import pygame.surfarray as surfarray

import config
from .base import Effect


class LiquidLight(Effect):
    TRAIL_ALPHA = 0
    RES_DIV = 4
    MAX_SUBSTEPS = 3
    MAX_VELOCITY = 5.0
    MAX_DENSITY = 1.0
    MAX_VORTICES = 2

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self._rng = np.random.default_rng(config.RNG_SEED)
        self._W = self._H = 1
        self._x = self._y = None
        self._back_x = self._back_y = None
        self._vx = self._vy = None
        self._published_vx = self._published_vy = None
        self._vx_next = self._vy_next = None
        self._dye = self._dye_next = None
        self._surface = pygame.Surface((1, 1))
        self._scaled = pygame.Surface((1, 1))
        self._hue = 0.0
        self._beat_prev = 0.0
        self._reset_state()

    def _reset_state(self):
        W, H, _ = self._render_size()
        self._W, self._H = W, H
        self._x, self._y = np.meshgrid(
            np.arange(W, dtype=np.float32), np.arange(H, dtype=np.float32))
        self._back_x = np.empty((H, W), dtype=np.int32)
        self._back_y = np.empty((H, W), dtype=np.int32)
        self._vx = np.zeros((H, W), dtype=np.float32)
        self._vy = np.zeros((H, W), dtype=np.float32)
        self._published_vx = np.zeros_like(self._vx)
        self._published_vy = np.zeros_like(self._vy)
        self._vx_next = np.empty_like(self._vx)
        self._vy_next = np.empty_like(self._vy)
        self._dye = np.zeros((H, W, 3), dtype=np.float32)
        self._dye_next = np.empty_like(self._dye)
        self._surface = pygame.Surface((W, H))
        self._scaled = pygame.Surface((max(1, config.WIDTH), max(1, config.HEIGHT)))
        self._dye[..., 2] = 0.04
        self._hue = 0.0
        self._beat_prev = 0.0

    def _inject_vortex(self, cx, cy, sign, strength, channel):
        radius = max(2.0, min(self._W, self._H) * 0.18)
        dx = self._x - cx
        dy = self._y - cy
        dist2 = dx * dx + dy * dy
        weight = np.exp(-dist2 / max(1.0, radius * radius)).astype(np.float32)
        self._vx += np.clip(-dy * weight * sign * strength / radius, -0.8, 0.8)
        self._vy += np.clip(dx * weight * sign * strength / radius, -0.8, 0.8)
        self._dye[..., channel] = np.clip(
            self._dye[..., channel] + weight * min(0.6, strength * 0.22),
            0.0, self.MAX_DENSITY)

    def _advect(self):
        self._back_x[:] = np.clip((self._x - self._vx * 0.85).astype(np.int32), 0, self._W - 1)
        self._back_y[:] = np.clip((self._y - self._vy * 0.85).astype(np.int32), 0, self._H - 1)
        self._vx_next[:] = self._vx[self._back_y, self._back_x] * 0.985
        self._vy_next[:] = self._vy[self._back_y, self._back_x] * 0.985
        self._dye_next[:] = self._dye[self._back_y, self._back_x] * 0.994
        self._vx, self._vx_next = self._vx_next, self._vx
        self._vy, self._vy_next = self._vy_next, self._vy
        self._dye, self._dye_next = self._dye_next, self._dye
        np.clip(self._vx, -self.MAX_VELOCITY, self.MAX_VELOCITY, out=self._vx)
        np.clip(self._vy, -self.MAX_VELOCITY, self.MAX_VELOCITY, out=self._vy)
        self._published_vx[:] = self._vx
        self._published_vy[:] = self._vy
        np.clip(self._dye, 0.0, self.MAX_DENSITY, out=self._dye)

    def _render(self, surf):
        dye = np.clip(self._dye, 0.0, self.MAX_DENSITY)
        total = np.clip(dye.sum(axis=2), 0.0, 1.0)
        hue = (self._hue + dye[..., 1] * 0.45 - dye[..., 2] * 0.22) % 1.0
        glow = np.clip(total * 1.8, 0.0, 1.0)
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
            surf.blit(self._scaled, (0, 0), special_flags=pygame.BLEND_RGB_ADD)
        else:
            surf.blit(self._surface, (0, 0), special_flags=pygame.BLEND_RGB_ADD)

    def draw(self, surf, waveform, fft, beat, tick):
        W, H, _ = self._render_size()
        if (W, H) != (self._W, self._H):
            self._reset_state()
        bass = min(max(float(beat), 0.0), 1.5)
        mid = min(max(float(config.MID_ENERGY), 0.0), 4.0)
        high = min(max(float(config.TREBLE_ENERGY), 0.0), 4.0)
        self._hue = (self._hue + 0.001 + mid * 0.001 + high * 0.0007) % 1.0
        cx = self._W * (0.5 + 0.18 * math.sin(tick * 0.008 + mid))
        cy = self._H * (0.5 + 0.18 * math.cos(tick * 0.006 + high))
        self._inject_vortex(cx, cy, 1.0, 0.035 + mid * 0.012, 1)
        if bass > 0.7 and self._beat_prev <= 0.7:
            self._inject_vortex(self._W - cx, self._H - cy, -1.0, 0.8 + bass * 0.35, 0)
        self._beat_prev = bass
        if high > 0.8:
            self._inject_vortex(self._W * 0.5, self._H * 0.5, 1.0, high * 0.04, 2)
        for _ in range(min(self.MAX_SUBSTEPS, 1 + int(high * 0.5))):
            self._advect()
        self._render(surf)

    def get_motion_field(self):
        return self._published_vx, self._published_vy

    def release(self):
        self._surface = None
        self._scaled = None
        self._x = self._y = None
        self._back_x = self._back_y = None
        self._vx = self._vy = self._vx_next = self._vy_next = None
        self._published_vx = self._published_vy = None
        self._dye = self._dye_next = None
