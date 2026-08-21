"""Magnetar — rotating magnetic dipole field with spiralling particles.

~6 000 particles ride the field lines of an analytical rotating magnetic
dipole.  Particles accumulate near the poles and trace glowing field lines.
Beat fires a shockwave that scatters particles outward from the equator.

  Bass   → field rotation speed + particle velocity
  Mid    → dipole tilt angle
  Treble → particle colour saturation burst
  Beat   → equatorial shockwave
"""
import math

import numpy as np
import pygame
import pygame.surfarray as surfarray

import config
from .base import Effect
from .utils import _hsl_batch


class Magnetar(Effect):
    TRAIL_ALPHA = 0
    RES_DIV     = 2
    _FADE_ALPHA = 24

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self._rng = np.random.default_rng(config.RNG_SEED)
        self._hue   = 0.62
        self._rot   = 0.0
        self._boost = 0.0
        self._trail = pygame.Surface((1, 1))
        self._scaled = pygame.Surface((1, 1))
        self._fade = pygame.Surface((1, 1), pygame.SRCALPHA)
        self._grid_x = self._grid_y = None
        self._contour = None
        self._contour_scaled = None
        self._reset_particles()

    def _reset_particles(self):
        W, H, RD = self._render_size()
        area = W * H
        self._n = max(6000, min(40000, int(10000 * area / (960 * 540))))
        self._px = self._rng.uniform(0, W, self._n).astype(np.float32)
        self._py = self._rng.uniform(0, H, self._n).astype(np.float32)
        self._trail = pygame.Surface((W, H))
        self._trail.fill((0, 0, 0))
        self._fade = pygame.Surface((W, H), pygame.SRCALPHA)
        self._fade.fill((0, 0, 0, self._FADE_ALPHA))
        self._scaled = pygame.Surface((config.WIDTH, config.HEIGHT))
        xs = np.linspace(-1.0, 1.0, W, dtype=np.float32)
        ys = np.linspace(-1.0, 1.0, H, dtype=np.float32)
        self._grid_x, self._grid_y = np.meshgrid(xs, ys)
        self._contour = np.zeros((H, W), dtype=np.float32)
        self._contour_scaled = pygame.Surface((config.WIDTH, config.HEIGHT))

    def _draw_contours(self, surf, mx, my, bass, high):
        rx, ry = self._grid_x, self._grid_y
        r2 = rx * rx + ry * ry + 0.04
        r = np.sqrt(r2)
        r3 = r2 * r + 0.1
        dot = rx * mx + ry * my
        bx = (3.0 * dot * rx / r2 - mx) / r3
        by = (3.0 * dot * ry / r2 - my) / r3
        magnitude = np.sqrt(bx * bx + by * by)
        self._contour[:] = np.tanh(magnitude * 0.20)
        bands = np.abs(np.sin(self._contour * 6.0 + self._rot * 0.2))
        hue = (self._hue + self._contour * 0.18 + high * 0.03) % 1.0
        glow = np.clip(bands * (0.20 + bass * 0.10), 0.0, 1.0)
        red = np.clip((np.sin(hue * math.tau) * 127 + 128) * glow, 0, 255)
        green = np.clip((np.sin((hue + 0.333) * math.tau) * 127 + 128) * glow, 0, 255)
        blue = np.clip((np.sin((hue + 0.667) * math.tau) * 127 + 128) * glow, 0, 255)
        pixels = surfarray.pixels3d(self._trail)
        try:
            pixels[:] = np.stack((red, green, blue), axis=-1).astype(np.uint8).transpose(1, 0, 2)
        finally:
            del pixels
        if self._contour_scaled.get_size() != surf.get_size():
            self._contour_scaled = pygame.Surface(surf.get_size())
        pygame.transform.scale(self._trail, surf.get_size(), self._contour_scaled)
        surf.blit(self._contour_scaled, (0, 0), special_flags=pygame.BLEND_RGB_ADD)

    def draw(self, surf, waveform, fft, beat, tick):
        W, H, RD = self._render_size()
        bass     = beat
        mid      = config.MID_ENERGY
        high     = config.TREBLE_ENERGY

        if self._trail.get_width() != W or self._trail.get_height() != H:
            self._reset_particles()
            W = self._trail.get_width()
            H = self._trail.get_height()
        if self._scaled.get_width() != surf.get_width() or self._scaled.get_height() != surf.get_height():
            self._scaled = pygame.Surface(surf.get_size())

        self._hue  = (self._hue + 0.0008 + high * 0.001) % 1.0
        self._rot += 0.008 + bass * 0.025 + mid * 0.012

        if bass > 0.7:
            self._boost = 2.5 + bass * 2.0
        self._boost = max(0.0, self._boost - 0.08)

        # Normalise particle coords to [-1, 1]
        cx = W / 2.0
        cy = H / 2.0
        half = min(W, H) * 0.5
        rx = (self._px - cx) / half
        ry = (self._py - cy) / half

        # Dipole axis direction (rotating in the XY plane)
        tilt = math.pi * 0.12 + mid * 0.30
        mx   = math.cos(self._rot) * math.cos(tilt)
        my   = math.sin(self._rot) * math.cos(tilt)
        self._draw_contours(surf, mx, my, bass, high)

        r2  = rx * rx + ry * ry + 0.04
        r   = np.sqrt(r2)
        r3  = r2 * r + 0.1
        dot = rx * mx + ry * my
        # Dipole field B ∝ (3(m·r̂)r̂ − m) / r³
        bx  = (3.0 * dot * rx / r2 - mx) / r3
        by_ = (3.0 * dot * ry / r2 - my) / r3

        bmag = np.sqrt(bx * bx + by_ * by_) + 1e-6
        spd  = 1.2 + bass * 1.8 + self._boost * 0.5
        vx   = (bx  / bmag) * spd
        vy   = (by_ / bmag) * spd

        # Beat shockwave from equator
        if self._boost > 0.1:
            push = (self._boost * 1.5
                    * np.sign(self._py - cy)
                    * np.exp(-np.abs(self._py - cy) / (H * 0.3)))
            vy  += push * 0.5

        self._px = (self._px + vx) % W
        self._py = (self._py + vy) % H

        # Fade with alpha overlay instead of multiplicative darkening, which
        # preserves hue better over long trail runs.
        self._trail.blit(self._fade, (0, 0))

        # Colour by angle to dipole axis
        ang_to_axis = (np.arctan2(ry, rx) - self._rot) / math.tau
        h_arr       = ((self._hue + ang_to_axis) % 1.0).astype(np.float32)
        rgb    = _hsl_batch(h_arr, l=0.35 + bass * 0.25 + high * 0.12)
        colors = (rgb[:, 0].astype(np.uint32) << 16
                  | rgb[:, 1].astype(np.uint32) << 8
                  | rgb[:, 2].astype(np.uint32))

        ix  = np.clip(self._px.astype(np.int32), 0, W - 1)
        iy  = np.clip(self._py.astype(np.int32), 0, H - 1)
        pix = surfarray.pixels2d(self._trail)
        try:
            pix[ix, iy] = colors
        finally:
            del pix

        if surf.get_size() != self._trail.get_size():
            pygame.transform.scale(self._trail,
                                   surf.get_size(),
                                   self._scaled)
            surf.blit(self._scaled, (0, 0), special_flags=pygame.BLEND_RGB_MAX)
        else:
            surf.blit(self._trail, (0, 0), special_flags=pygame.BLEND_RGB_MAX)
