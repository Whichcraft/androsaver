import math
from collections import deque

import numpy as np
import pygame

import config
from .utils import hsl, _hsl_batch


from .base import Effect

class Lissajous(Effect):
    """Psytrance 3-D Lissajous knot — trefoil (3-fold) symmetry, neon glow,
    beat-driven spring bursts.

    Three copies of the knot are drawn at 0 / 120 / 240 ° around the centre.
    Each trail is rendered in two passes (wide dim halo + thin bright core)
    for a neon bloom effect.  Beat explodes the scale outward then snaps back.
    """

    TRAIL = 1400
    N_SYM = 3

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.hue   = 0.0
        self.t     = 0.0
        self.rx    = 0.0
        self.ry    = 0.0
        self.rvx   = 0.006
        self.rvy   = 0.009
        self.hist  = deque(maxlen=self.TRAIL)
        self.dx    = 0.0
        self.dy    = math.pi / 2
        self.dz    = math.pi / 4
        self.scale = 1.0
        self.svel  = 0.0

    def draw(self, surf, waveform, fft, beat, tick):
        self.hue += 0.006
        W, H = surf.get_size()
        bass = beat
        mid  = config.MID_ENERGY
        high = config.TREBLE_ENERGY

        # Creative shape distortion mapping
        ax = 3.0 + bass * 0.20
        ay = 2.0 + mid  * 0.18
        az = 5.0 + high * 0.22

        self.dx += 0.0003 + bass * 0.0002
        self.dz += 0.0002 + high * 0.0002

        # BPM scaling: faster knot at higher tempo (guard against BPM=0 before detection)
        bpm_scale = max(0.7, config.BPM / 138.0) if config.BPM > 60 else 1.0
        
        # Dampened beat response to prevent "wild" behavior
        clamped_beat = min(1.5, beat)
        self.t += (0.010 + clamped_beat * 0.006 + mid * 0.004) * bpm_scale
        self.hist.append((math.sin(ax * self.t + self.dx),
                          math.sin(ay * self.t + self.dy),
                          math.sin(az * self.t + self.dz)))

        self.svel  += clamped_beat * 0.06
        self.svel  += (1.0 - self.scale) * 0.26
        self.svel  *= 0.60
        self.scale += self.svel
        self.scale  = max(0.35, self.scale)

        self.hue += clamped_beat * 0.06

        self.rvx += clamped_beat * 0.0022 + mid * 0.0007 + 0.00005
        self.rvy += clamped_beat * 0.0032 + mid * 0.0010 + 0.00007
        self.rvx *= 0.94
        self.rvy *= 0.94
        self.rx  += self.rvx
        self.ry  += self.rvy

        s  = self.scale
        n  = len(self.hist)
        if n < 2:
            return

        pts_3d = np.array(self.hist) * s
        px_a, py_a, pz_a = pts_3d[:, 0], pts_3d[:, 1], pts_3d[:, 2]
        cx_r, sx_r = math.cos(self.rx), math.sin(self.rx)
        cy_r, sy_r = math.cos(self.ry), math.sin(self.ry)
        y2   = py_a * cx_r - pz_a * sx_r
        z2   = py_a * sx_r + pz_a * cx_r
        x3   = px_a * cy_r + z2 * sy_r
        z3   = -px_a * sy_r + z2 * cy_r
        fov_l  = min(W, H) * 0.52
        zcam   = np.maximum(z3 + 2.8, 0.05)
        raw_x  = x3 * fov_l / zcam
        raw_y  = y2 * fov_l / zcam

        cx, cy = W // 2, H // 2

        # Treble brightens the glow: hi-hat energy makes the knot shimmer whiter
        l1_bright = min(0.90 + clamped_beat * 0.08 + high * 0.14, 0.98)
        glow_lw_mul = 4 + int(high * 6)
        PASSES = [(glow_lw_mul, 0.08, 0.22), (1, 0.50, l1_bright)]

        t_arr = np.arange(n, dtype=float) / n
        lw_base = (t_arr * 4).astype(int)

        sym_ca_sa = [(math.cos(sym / self.N_SYM * math.tau),
                      math.sin(sym / self.N_SYM * math.tau))
                     for sym in range(self.N_SYM)]
        for sym in range(self.N_SYM):
            ca, sa = sym_ca_sa[sym]
            sx_arr = np.round(cx + raw_x * ca - raw_y * sa).astype(int)
            sy_arr = np.round(cy + raw_x * sa + raw_y * ca).astype(int)

            h_arr = (self.hue + sym / self.N_SYM * 0.33 + t_arr * 0.55) % 1.0

            for lw_mul, l0, l1 in PASSES:
                l_arr  = l0 + t_arr * (l1 - l0)
                colors = _hsl_batch(h_arr, l=l_arr)
                lw_arr = np.maximum(1, lw_base * lw_mul)
                for j in range(1, n):
                    pygame.draw.line(surf,
                                     tuple(colors[j].astype(int)),
                                     (int(sx_arr[j - 1]), int(sy_arr[j - 1])),
                                     (int(sx_arr[j]), int(sy_arr[j])),
                                     int(lw_arr[j]))

        hx, hy = raw_x[-1], raw_y[-1]
        for sym in range(self.N_SYM):
            ca, sa = sym_ca_sa[sym]
            sx     = int(cx + hx * ca - hy * sa)
            sy_    = int(cy + hx * sa + hy * ca)
            r      = max(3, int(7 + clamped_beat * 5.0 + high * 3.0))
            pygame.draw.circle(surf,
                               hsl((self.hue + sym * 0.33) % 1.0, l=0.88), (sx, sy_), r)
            pygame.draw.circle(surf, (255, 255, 255), (sx, sy_), max(1, r // 3))
            if clamped_beat > 0.3 or high > 0.4:
                pygame.draw.circle(surf,
                                   hsl((self.hue + sym * 0.33 + 0.5) % 1.0,
                                       l=0.45 + clamped_beat * 0.25 + high * 0.15),
                                   (sx, sy_), max(2, int(r * (1.5 + high * 0.8))), 2)
