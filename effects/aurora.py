"""Aurora — Northern Lights curtains.

Five translucent sinusoidal ribbons undulate horizontally across the screen.
Each ribbon is built from two to three harmonics at different wavelengths and
drift speeds.

  Bass   → amplitude (curtains billow and swell)
  Treble → shimmer speed (phase velocity of all harmonics)
  Mid    → ribbon height / thickness
  Beat   → bloom flash + hue shift
"""
import math

import numpy as np
import pygame

import config
from .utils import hsl
from .base import Effect

try:
    import pygame as _pg
    HAS_PYGAME = True
except ImportError:
    HAS_PYGAME = False

_STEP = 5   # pixels between polygon vertices (lower → smoother, slower)


class Aurora(Effect):
    TRAIL_ALPHA = 14

    # (y_fraction, hue_offset, [(k_multiplier, speed_rad_per_frame, amp_weight)])
    # k_mult is relative to tau/W (one full wave = k_mult 1.0)
    _DEFS = [
        (0.20, 0.00, [(1.0, +0.50, 1.00), (2.3, -0.85, 0.55), (5.1, +0.30, 0.25)]),
        (0.35, 0.18, [(0.8, -0.42, 1.00), (1.9, +0.72, 0.55), (4.3, -0.28, 0.25)]),
        (0.50, 0.36, [(1.2, +0.58, 1.00), (2.7, -0.60, 0.55), (3.8, +0.38, 0.25)]),
        (0.65, 0.55, [(0.9, -0.48, 1.00), (2.1, +0.65, 0.55), (4.7, -0.33, 0.25)]),
        (0.80, 0.74, [(1.1, +0.45, 1.00), (2.5, -0.78, 0.55), (3.5, +0.35, 0.25)]),
    ]

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        W = config.WIDTH
        self._hue       = 0.42          # start in cyan-green range
        self._bloom     = 0.0
        self._beat_prev = 0.0
        self._tmp       = pygame.Surface((W, config.HEIGHT))
        self._xs        = np.arange(0, W, _STEP, dtype=np.float32)

        k_unit = math.tau / W
        self._phases: list[list[float]] = []
        self._ks:     list[list[float]] = []
        for _, _, harms in self._DEFS:
            self._phases.append([0.0] * len(harms))
            self._ks.append([k_unit * km for km, *_ in harms])

    def draw(self, surf, waveform, fft, beat, tick):
        W, H   = config.WIDTH, config.HEIGHT
        bass   = float(np.mean(fft[:6]))
        mid    = float(np.mean(fft[6:30]))
        treble = config.TREBLE_ENERGY

        self._hue = (self._hue + 0.003 + mid * 0.002) % 1.0

        # Beat: bloom flash + slight hue nudge
        if beat > 0.8 and self._beat_prev <= 0.8:
            self._bloom = 1.0
            self._hue   = (self._hue + 0.10) % 1.0
        self._beat_prev = beat
        self._bloom     = max(0.0, self._bloom - 0.03)

        spd = 1.0 + treble * 4.0 + beat * 1.5
        amp = H * (0.04 + bass * 0.12 + self._bloom * 0.07)
        rh  = H * (0.09  + mid  * 0.05)

        xs = self._xs
        self._tmp.fill((0, 0, 0))


        for ri, (y_frac, h_off, harms) in enumerate(self._DEFS):
            phases = self._phases[ri]
            ks     = self._ks[ri]
            hue    = (self._hue + h_off) % 1.0

            # Sum harmonics → displacement curve
            wave  = np.zeros(len(xs), dtype=np.float32)
            tot_w = 0.0
            for j, (_, spd_j, aw) in enumerate(harms):
                phases[j] = (phases[j] + spd_j * spd * 0.016) % math.tau
                wave  += np.sin(xs * ks[j] + phases[j]).astype(np.float32) * aw
                tot_w += aw
            wave = wave / tot_w * amp

            cy  = y_frac * H
            y_t = cy + wave
            y_b = cy + wave + rh

            xs_i = xs.astype(np.int32)
            yt_i = np.clip(y_t, -H * 0.6, H * 1.6).astype(np.int32)
            yb_i = np.clip(y_b, -H * 0.6, H * 1.6).astype(np.int32)

            top = np.column_stack([xs_i, yt_i])                      # (N,2)
            bot = np.column_stack([xs_i[::-1], yb_i[::-1]])          # (N,2) reversed
            poly = np.vstack([top, bot])

            # Wider glow polygon (padded outward on both sides)
            pad = max(2, int(rh * 0.5))
            glow_poly = np.vstack([
                np.column_stack([xs_i,       yt_i - pad]),
                np.column_stack([xs_i[::-1], yb_i[::-1] + pad]),
            ])

            # Pure saturated hue for scaling
            r, g, b = hsl(hue, s=1.0, l=0.5)

            # Outer glow: ~10-18% intensity
            gi = 0.10 + self._bloom * 0.08
            pygame.draw.polygon(self._tmp,
                                (int(r * gi), int(g * gi), int(b * gi)),
                                glow_poly)

            # Core ribbon: 28-55% intensity
            ci = 0.28 + self._bloom * 0.22 + bass * 0.08
            pygame.draw.polygon(self._tmp,
                                (int(r * ci), int(g * ci), int(b * ci)),
                                poly)

        # Blit all ribbons to screen additively (overlapping ribbons bloom together)
        surf.blit(self._tmp, (0, 0), special_flags=pygame.BLEND_ADD)
