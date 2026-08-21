"""Heartbeat — rhythmic pressure waves from a pulsing centre.

Each beat spawns one or more concentric rings that expand outward.
The ring outline smoothly morphs between a circle (high n_sides) and a
polygon (low n_sides) based on bass intensity at spawn time.
Multiple overlapping rings create moiré interference patterns.

  Bass   → wave speed + polygon morphing
  Mid    → propagation speed modifier
  Treble → ring shimmer
  Beat   → spawn new ring(s)
"""
import math
from typing import NamedTuple

import pygame
import numpy as np
import pygame.surfarray as surfarray

import config
from .base import Effect
from .utils import hsl

_MAX_RINGS = 16
_N_PTS     = 120   # polygon resolution

_Ring = NamedTuple("_Ring", [
    ("r", float), ("max_r", float), ("speed", float),
    ("inten", float), ("hue", float), ("n_sides", int), ("phase", float),
])


class Heartbeat(Effect):
    TRAIL_ALPHA = 20

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self._hue  = 0.0
        self._rings: list[_Ring] = []
        self._beat_prev = 0.0
        self._auto_cd   = 45
        self._plate_w = self._plate_h = 0
        self._plate_x = self._plate_y = None
        self._plate_field = None
        self._plate_surface = None
        self._plate_scaled = None
        self._plate_flash = 0.0

    def _reset_plate(self, W, H):
        self._plate_w = max(32, W // 4)
        self._plate_h = max(24, H // 4)
        xs = np.linspace(-1.0, 1.0, self._plate_w, dtype=np.float32)
        ys = np.linspace(-1.0, 1.0, self._plate_h, dtype=np.float32)
        self._plate_x, self._plate_y = np.meshgrid(xs, ys)
        self._plate_field = np.zeros((self._plate_h, self._plate_w), dtype=np.float32)
        self._plate_surface = pygame.Surface((self._plate_w, self._plate_h))
        self._plate_scaled = pygame.Surface((W, H))

    def _draw_plate(self, surf, fft, bass, mid, high, tick):
        W, H = surf.get_size()
        if self._plate_surface is None or self._plate_surface.get_size() != (max(32, W // 4), max(24, H // 4)):
            self._reset_plate(W, H)
        values = np.asarray(fft).reshape(-1)
        dominant = int(np.argmax(np.abs(values[:128]))) if values.size else 0
        mode = 1 + dominant % 5
        next_mode = mode + 1
        mix = (tick * 0.006 + mid * 0.08) % 1.0
        mode_a = np.cos(math.pi * mode * self._plate_x) * np.cos(math.pi * (mode + 1) * self._plate_y)
        mode_b = np.cos(math.pi * next_mode * self._plate_x) * np.cos(math.pi * (next_mode + 1) * self._plate_y)
        self._plate_field[:] = mode_a * (1.0 - mix) + mode_b * mix
        nodal = np.clip(np.exp(-np.abs(self._plate_field) * 14.0), 0.0, 1.0)
        opacity = min(0.55, 0.08 + self._plate_flash * 0.35 + bass * 0.08 + high * 0.025)
        hue = (self._hue + nodal * 0.65) % 1.0
        red = np.clip((np.sin(hue * math.tau) * 127 + 128) * nodal * opacity, 0, 255)
        green = np.clip((np.sin((hue + 0.333) * math.tau) * 127 + 128) * nodal * opacity, 0, 255)
        blue = np.clip((np.sin((hue + 0.667) * math.tau) * 127 + 128) * nodal * opacity, 0, 255)
        pixels = surfarray.pixels3d(self._plate_surface)
        try:
            pixels[:] = np.stack((red, green, blue), axis=-1).astype(np.uint8).transpose(1, 0, 2)
        finally:
            del pixels
        pygame.transform.smoothscale(self._plate_surface, surf.get_size(), self._plate_scaled)
        surf.blit(self._plate_scaled, (0, 0), special_flags=pygame.BLEND_RGB_ADD)

    # ------------------------------------------------------------------

    def _spawn(self, bass, W, H, offset_hue=0.0):
        if len(self._rings) >= _MAX_RINGS:
            return
        max_r  = max(W, H) * 0.75
        speed  = 2.5 + bass * 4.5
        inten  = 0.60 + bass * 0.40
        hue    = (self._hue + offset_hue) % 1.0
        # n_sides: low bass → near-circle (12 sides), high bass → triangle (3)
        n_sides = max(3, 12 - int(bass * 9))
        self._rings.append(_Ring(1.0, max_r, speed, inten, hue, n_sides, 0.0))

    # ------------------------------------------------------------------

    def draw(self, surf, waveform, fft, beat, tick):
        W, H = surf.get_size()
        bass = beat
        mid  = config.MID_ENERGY
        high = config.TREBLE_ENERGY

        self._hue = (self._hue + 0.005 + mid * 0.004) % 1.0

        if bass > 0.60 and self._beat_prev <= 0.60:
            self._plate_flash = min(1.0, self._plate_flash + 0.75)
            self._spawn(bass, W, H)
            if bass > 0.85:
                self._spawn(bass * 0.7, W, H, offset_hue=0.25)
        self._beat_prev = bass
        self._plate_flash *= 0.92

        self._auto_cd -= 1
        if self._auto_cd <= 0 and len(self._rings) < 4:
            self._spawn(mid * 0.5, W, H)
            self._auto_cd = 50

        spd_mul = 1.0 + mid * 0.8
        cx, cy  = W // 2, H // 2

        self._draw_plate(surf, fft, bass, mid, high, tick)

        live = []
        for ring in self._rings:
            new_r = ring.r + ring.speed * spd_mul
            if new_r <= ring.max_r:
                live.append(ring._replace(r=new_r))
        self._rings = live

        for ring in self._rings:
            fade = max(0.0, 1.0 - ring.r / ring.max_r) * ring.inten
            if fade < 0.01:
                continue
            bright = 0.20 + fade * 0.50 + high * 0.12
            col    = hsl(ring.hue, l=bright)
            glow   = hsl(ring.hue, l=bright * 0.28)

            pts = []
            for i in range(_N_PTS):
                a       = i / _N_PTS * math.tau + ring.phase
                # Polygon modulation: more pronounced at low n_sides
                poly_m  = math.cos(a * ring.n_sides) * (
                    0.04 + (1 - ring.n_sides / 12.0) * 0.14)
                r_actual = ring.r * (1.0 + poly_m)
                pts.append((cx + int(math.cos(a) * r_actual),
                            cy + int(math.sin(a) * r_actual)))

            if len(pts) >= 3:
                lw = max(1, int(3 * fade + bass))
                pygame.draw.polygon(surf, glow, pts, lw + 3)
                pygame.draw.polygon(surf, col,  pts, lw)
