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

_STEP_FRAC = 0.004   # fraction of width between polygon vertices


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
        self._hue       = 0.42          # start in cyan-green range
        self._bloom     = 0.0
        self._beat_prev = 0.0
        self._tmp       = pygame.Surface((config.WIDTH, config.HEIGHT))
        self._xs        = np.arange(0, config.WIDTH, max(3, int(config.WIDTH * _STEP_FRAC)), dtype=np.float32)

        k_unit = math.tau / config.WIDTH
        self._phases: list[list[float]] = []
        self._ks:     list[list[float]] = []
        self._wave = np.zeros(len(self._xs), dtype=np.float32)
        for _, _, harms in self._DEFS:
            self._phases.append([0.0] * len(harms))
            self._ks.append([k_unit * km for km, *_ in harms])

    def draw(self, surf, waveform, fft, beat, tick):
        W, H   = surf.get_size()
        bass   = beat
        mid    = config.MID_ENERGY
        treble = config.TREBLE_ENERGY

        if self._tmp.get_width() != W or self._tmp.get_height() != H:
            self._tmp = pygame.Surface((W, H))
            self._xs = np.arange(0, W, max(3, int(W * _STEP_FRAC)), dtype=np.float32)
            self._wave = np.zeros(len(self._xs), dtype=np.float32)
            k_unit = math.tau / W
            self._ks = []
            self._phases = []
            for _, _, harms in self._DEFS:
                self._ks.append([k_unit * km for km, *_ in harms])
                self._phases.append([0.0] * len(harms))

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
            wave  = self._wave
            wave[:] = 0.0
            tot_w = 0.0
            for j, (_, spd_j, aw) in enumerate(harms):
                phases[j] = (phases[j] + spd_j * spd * 0.016) % math.tau
                wave  += np.sin(xs * ks[j] + phases[j]).astype(np.float32) * aw
                tot_w += aw
            wave = wave / max(tot_w, 1e-6) * amp

            cy  = y_frac * H
            y_t = cy + wave
            y_b = cy + wave + rh

            xs_i = xs.astype(np.int32)
            yt_i = np.clip(y_t, -H * 0.6, H * 1.6).astype(np.int32)
            yb_i = np.clip(y_b, -H * 0.6, H * 1.6).astype(np.int32)
            # Soft-fade extreme Y values to avoid hard clip lines
            fade_top = yt_i < 0
            fade_bot = yb_i > H

            # Pre-compute fade factor per vertex
            n_fade_arr = np.maximum(1, fade_top[:-1].astype(np.int32) + fade_top[1:]
                                      + fade_bot[:-1].astype(np.int32) + fade_bot[1:])
            fade_mul = 1.0 / (n_fade_arr + 1)

            pad = max(2, int(rh * 0.5))

            # Pure saturated hue for scaling
            r, g, b = hsl(hue, s=1.0, l=0.5)

            # Outer glow: ~10-18% intensity
            gi = 0.10 + self._bloom * 0.08
            # Core ribbon: 28-55% intensity
            ci = 0.28 + self._bloom * 0.22 + bass * 0.08
            glow_col = np.array([r * gi, g * gi, b * gi])
            core_col = np.array([r * ci, g * ci, b * ci])

            # Successive quads avoid Android fill artifacts that can create
            # hard straight spokes through otherwise smooth bands.
            for i in range(len(xs_i) - 1):
                x0, x1 = int(xs_i[i]), int(xs_i[i + 1])
                yt0, yt1 = int(yt_i[i]), int(yt_i[i + 1])
                yb0, yb1 = int(yb_i[i]), int(yb_i[i + 1])
                glow_quad = [
                    (x0, yt0 - pad),
                    (x1, yt1 - pad),
                    (x1, yb1 + pad),
                    (x0, yb0 + pad),
                ]
                core_quad = [
                    (x0, yt0),
                    (x1, yt1),
                    (x1, yb1),
                    (x0, yb0),
                ]
                fm = fade_mul[i]
                g_fade = tuple((glow_col * fm).astype(np.int32))
                c_fade = tuple((core_col * fm).astype(np.int32))
                pygame.draw.polygon(self._tmp, g_fade, glow_quad)
                pygame.draw.polygon(self._tmp, c_fade, core_quad)

        # Blit all ribbons to screen additively (overlapping ribbons bloom together)
        surf.blit(self._tmp, (0, 0), special_flags=pygame.BLEND_RGB_ADD)
