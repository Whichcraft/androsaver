"""FlowField — 8 000+ particles surfing a continuously-evolving noise field.

Particles ride a multi-layered noise field and paint vivid rainbow trails.
Optimized for high particle count and high frame rates using NumPy surfarray vectorisation.
"""
import math
import numpy as np
import pygame
import pygame.surfarray as surfarray
import config
from .base import Effect

_FS = 0.0022
_LAYERS = 2

class FlowField(Effect):
    TRAIL_ALPHA = 0 # we manage the surface
    RES_DIV = 3 # 2 levels faster than default 1

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self._rng = np.random.default_rng(config.RNG_SEED)
        W, H, RD = self._render_size()
        self._scaled = pygame.Surface((config.WIDTH, config.HEIGHT))
        # Scale particle count dynamically based on screen area.
        # Baseline: 25000 particles for a 1920x1080 (1080p) screen.
        area = config.WIDTH * config.HEIGHT
        self._n = int(max(8000, min(100000, 25000 * area / (1920 * 1080))))
        if getattr(config, "LOW_SPEC", False):
            self._n = max(4000, self._n // 2)
        self._allocate_particles(self._n, W, H)
        self._hue = float(self._rng.random())
        self._t = 0.0
        self._boost = 0.0
        # Internal trail is always 24-bit for BLEND_RGB_MULT speed
        self._trail = pygame.Surface((W, H))
        self._trail.fill((0, 0, 0))

    def _allocate_particles(self, count, W, H):
        """Allocate particle state, preserving existing particles when possible."""
        old_px = getattr(self, "_px", None)
        old_py = getattr(self, "_py", None)
        old_n = len(old_px) if old_px is not None else 0
        self._n = int(count)
        self._px = self._rng.uniform(0, W, self._n).astype(np.float32)
        self._py = self._rng.uniform(0, H, self._n).astype(np.float32)
        if old_n:
            keep = min(old_n, self._n)
            self._px[:keep] = old_px[:keep] % W
            self._py[:keep] = old_py[:keep] % H
        self._angles = np.empty(self._n, dtype=np.float32)
        self._ir = np.empty(self._n, dtype=np.uint32)
        self._ig = np.empty(self._n, dtype=np.uint32)
        self._ib = np.empty(self._n, dtype=np.uint32)
        self._colors_arr = np.empty(self._n, dtype=np.uint32)

    def adjust_particles(self, delta=2000):
        """Add or remove particles in fixed steps for interactive tuning."""
        minimum = 4000 if getattr(config, "LOW_SPEC", False) else 8000
        target = max(minimum, min(100000, self._n + int(delta)))
        if target != self._n:
            W, H, _ = self._render_size()
            self._allocate_particles(target, W, H)

    def _field_angles(self, bass):
        x, y, t = self._px, self._py, self._t
        a = self._angles
        a[:] = 0.0
        for i in range(_LAYERS):
            f = 1.6 ** i
            a += (np.sin(x * _FS * f + t * (0.29 + i * 0.08)) *
                  np.cos(y * _FS * f * 0.75 + t * (0.21 - i * 0.06)))
        return a * (math.pi * (2.2 + bass * 1.8))

    def draw(self, surf, waveform, fft, beat, tick):
        W, H, RD = self._render_size()
        if self._trail.get_width() != W or self._trail.get_height() != H:
            self._trail = pygame.Surface((W, H))
            self._trail.fill((0, 0, 0))
            area = config.WIDTH * config.HEIGHT
            self._n = int(max(8000, min(100000, 25000 * area / (1920 * 1080))))
            if getattr(config, "LOW_SPEC", False):
                self._n = max(4000, self._n // 2)
            self._allocate_particles(self._n, W, H)
        sw, sh = surf.get_size()
        if self._scaled.get_width() != sw or self._scaled.get_height() != sh:
            self._scaled = pygame.Surface((sw, sh))

        bass = beat
        mid  = config.MID_ENERGY
        high = config.TREBLE_ENERGY
        
        self._hue = (self._hue + 0.0013 + bass * 0.002 + high * 0.001) % 1.0
        self._t += 0.007 + mid * 0.010 + high * 0.005

        if beat > 0.55:
            self._t += 0.5 + beat * 0.4
            self._boost = 2.0 + beat * 2.0

        self._boost = max(0.0, self._boost - 0.12)

        angles = self._field_angles(bass)
        spd = 1.8 + bass * 1.6 + mid * 1.2 + self._boost
        
        self._px = (self._px + np.cos(angles) * spd) % W
        self._py = (self._py + np.sin(angles) * spd) % H

        # Treble transient spikes push particles outward from screen center
        if high > 0.45:
            cx, cy = W / 2.0, H / 2.0
            dx = self._px - cx
            dy = self._py - cy
            dist = np.hypot(dx, dy) + 1e-5
            # Push is stronger closer to center and scales with treble intensity
            push = (high * 22.0) * (1.0 - np.minimum(dist / (min(W, H) * 0.5), 1.0))
            self._px = (self._px + (dx / dist) * push) % W
            self._py = (self._py + (dy / dist) * push) % H

        # Decay trail (RGB mult is fast on 24-bit)
        self._trail.fill((214, 214, 214), special_flags=pygame.BLEND_RGB_MULT)

        # Fast color calc
        hx = (self._hue + self._px / W * 0.30) % 1.0
        self._ir[:] = (np.sin(hx * math.tau) * 127 + 128).astype(np.uint32)
        self._ig[:] = (np.sin((hx + 0.33) * math.tau) * 127 + 128).astype(np.uint32)
        self._ib[:] = (np.sin((hx + 0.66) * math.tau) * 127 + 128).astype(np.uint32)
        np.bitwise_or(self._ir << 16, self._ig << 8, out=self._colors_arr)
        np.bitwise_or(self._colors_arr, self._ib, out=self._colors_arr)
        colors = self._colors_arr

        ix = np.clip(self._px.astype(np.int32), 0, W - 1)
        iy = np.clip(self._py.astype(np.int32), 0, H - 1)
        
        # NumPy vectorized pixel assignment is extremely fast
        pixels = surfarray.pixels2d(self._trail)
        try:
            pixels[ix, iy] = colors
        finally:
            del pixels

        if surf.get_size() != self._trail.get_size():
            if self._scaled.get_size() != surf.get_size():
                self._scaled = pygame.Surface(surf.get_size())
            pygame.transform.scale(self._trail, surf.get_size(), self._scaled)
            surf.blit(self._scaled, (0, 0), special_flags=pygame.BLEND_RGB_MAX)
        else:
            surf.blit(self._trail, (0, 0), special_flags=pygame.BLEND_RGB_MAX)
