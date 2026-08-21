import math

import numpy as np
import pygame

import config
from .utils import hsl


from .base import Effect

class Branches(Effect):
    """Recursive fractal lightning tree — psychedelic edition.

    Nine neon arms radiate from screen centre, each splitting recursively to
    depth 7.  Triple-fork at the trunk AND first split level creates a dense
    inner canopy.  Every segment is drawn twice (wide dim glow + bright core)
    for a neon flare effect.  Mid frequencies jitter all branch angles live;
    bass drives trunk length; beat fires extra arms with a brightness burst.
    """

    TRAIL_ALPHA = 10
    MAX_DEPTH   = 7
    BASE_ARMS   = 9

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.hue  = 0.0
        self.time = 0.0
        self.beat_flash = 0.0
        self.max_depth = 7
        self.base_arms = 9
        if getattr(config, "LOW_SPEC", False):
            self.max_depth = 5
            self.base_arms = 6

    # ------------------------------------------------------------------
    def _branch(self, surf, x, y, angle, length, depth,
                hue, time, mid, high, beat_flash):
        if depth == 0 or length < 1.5:
            return

        # Per-branch angle jitter — mids drive medium sway, treble drives high-frequency shiver
        jitter = (math.sin(time * 2.3 + depth * 1.7 + angle) * mid * 0.45
                  + math.cos(time * 1.1 + depth * 2.9) * mid * 0.22
                  + math.sin(time * 6.5 + angle * 3.3) * high * 0.18)

        # Trunk segment drawn short; children still get full length
        draw_len = length * 0.015 if depth == self.max_depth else length
        ex = x + math.cos(angle + jitter) * draw_len
        ey = y + math.sin(angle + jitter) * draw_len

        # Colour: trunk warm, tips cool, wide hue sweep across depth
        depth_t = depth / self.max_depth
        h       = (hue + (1.0 - depth_t) * 0.80) % 1.0
        bright  = 0.28 + depth_t * 0.52 + high * 0.22 + beat_flash * 0.35
        # Segment thickness/glow reacts to high-frequency transients
        lw      = max(1, depth // 2) + int(high * 1.5)

        # Neon glow: wide dim halo first, then bright core on top
        pygame.draw.line(surf, hsl(h, l=bright * 0.30),
                         (int(x), int(y)), (int(ex), int(ey)), lw * 3 + 2)
        pygame.draw.line(surf, hsl(h, l=bright),
                         (int(x), int(y)), (int(ex), int(ey)), lw)

        # Mids and treble increase branch spread angle and branch decay ratio
        spread = math.pi / 2.6 + mid * 0.28 + high * 0.10
        ratio  = 0.62 + high * 0.06
        self._branch(surf, ex, ey, angle - spread / 2,
                     length * ratio, depth - 1,
                     hue, time, mid, high, beat_flash)
        self._branch(surf, ex, ey, angle + spread / 2,
                     length * ratio, depth - 1,
                     hue, time, mid, high, beat_flash)

        # Triple-fork (extra centre child) at trunk AND first split level
        if depth >= self.max_depth - 1:
            self._branch(surf, ex, ey, angle,
                         length * ratio * 0.72, depth - 1,
                         hue, time, mid, high, beat_flash)

    # ------------------------------------------------------------------
    def draw(self, surf, waveform, fft, beat, tick):
        self.hue  += 0.012
        W, H = surf.get_size()
        bass = beat
        mid  = config.MID_ENERGY
        high = config.TREBLE_ENERGY

        self.time       += 0.025 + bass * 0.06 + mid * 0.04 + high * 0.02
        self.beat_flash  = self.beat_flash * 0.72 + bass * 0.28

        cx = W // 2
        cy = H // 2

        sc    = min(W, H)
        trunk = min(sc * 0.22 * (1.0 + bass * 0.70 + mid * 0.25), sc * 0.27)

        # Extra arms on strong beats (up to +5); treble has minimal influence
        n_arms = self.base_arms + int(min(bass, 2.5) * 2.2 + high * 0.8)

        # Slow rotation of the whole tree; hue spread across all arms
        base_rot = self.time * 0.06

        for i in range(n_arms):
            angle   = base_rot + i / n_arms * math.tau
            arm_hue = (self.hue + i / n_arms * 0.75) % 1.0
            self._branch(surf, cx, cy, angle, trunk,
                         self.max_depth, arm_hue,
                         self.time, mid, high, self.beat_flash)
