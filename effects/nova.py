import math

import numpy as np
import pygame

import config
from .utils import hsl


from .base import Effect

class Nova(Effect):
    """Extremely psychedelic waveform kaleidoscope — audio waveform mapped to
    polar sectors with 7-fold mirror symmetry across 4 concentric spinning
    layers.  Each layer reacts to a different frequency band; beat explodes
    all layers outward with spring physics then snaps them back."""

    N_SYM    = 7
    N_LAYERS = 4
    N_WAVE   = 120

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.hue  = 0.0
        self.time = 0.0
        signs = [1, -1, 1, -1]
        self.rot   = [i / self.N_LAYERS * math.pi for i in range(self.N_LAYERS)]
        self.rvel  = [signs[i] * (0.005 + i * 0.0022) for i in range(self.N_LAYERS)]
        self.poff  = [0.0] * self.N_LAYERS
        self.pvel  = [0.0] * self.N_LAYERS

    def draw(self, surf, waveform, fft, beat, tick):
        self.hue  += 0.007
        W, H = surf.get_size()
        bass = beat
        mid  = config.MID_ENERGY
        high = config.TREBLE_ENERGY
        
        self.time += 0.018 + bass * 0.025 + mid * 0.015
        
        bands = [bass, mid, high, (bass + mid + high) / 3.0]

        cx, cy = W // 2, H // 2
        max_r  = min(W, H) * 0.44

        for i in range(self.N_LAYERS):
            e = min(bands[i], 1.0)
            self.pvel[i] += bass * (0.32 + e * 0.14)
            self.pvel[i] += -self.poff[i] * 0.24
            self.pvel[i] *= 0.63
            self.poff[i] += self.pvel[i]
            self.rot[i]  += self.rvel[i] * (1.0 + e * 2.8 + bass * 1.2 + mid * 1.0)

        step   = max(1, len(waveform) // self.N_WAVE)
        wave   = waveform[::step][:self.N_WAVE]
        sector = math.tau / self.N_SYM

        for i in range(self.N_LAYERS - 1, -1, -1):
            e      = min(bands[i], 1.0)
            base_r = max_r * (0.22 + i / max(self.N_LAYERS - 1, 1) * 0.72)
            r_off  = self.poff[i] * base_r * 0.42
            h      = (self.hue + i / self.N_LAYERS * 0.45) % 1.0
            bright = 0.44 + e * 0.30 + mid * 0.15 + bass * 0.10
            amp    = base_r * (0.14 + e * 0.20 + bass * 0.10 + high * 0.15)
            lw     = max(1, int(1 + e * 1.5 + high * 2.0))

            for sym in range(self.N_SYM):
                w_slice   = wave if sym % 2 == 0 else wave[::-1]
                angle_off = sym * sector + self.rot[i]
                n_w   = len(w_slice)
                if n_w == 0:
                    continue
                j_arr = np.arange(n_w, dtype=float)
                theta = j_arr / n_w * sector + angle_off
                r_pts = np.maximum(2.0, base_r + r_off + w_slice.astype(float) * amp)
                xs    = np.round(cx + np.cos(theta) * r_pts).astype(int)
                ys    = np.round(cy + np.sin(theta) * r_pts).astype(int)
                pts   = np.column_stack([xs, ys])
                if len(pts) > 1:
                    pygame.draw.lines(surf, hsl(h, l=bright), False, pts, lw)

            pygame.draw.circle(surf, hsl(h, l=bright * 0.28),
                               (cx, cy), max(1, int(base_r + r_off)), 1)

        for tri_layer, (t_rvel, t_r_frac, t_h_off) in enumerate(
                [(0.012, 0.58, 0.25), (-0.008, 0.82, 0.55)]):
            t_rot = self.rot[0] * t_rvel / 0.005 + mid * 0.20
            # Treble adds a radial jitter to the outer triangle positions
            t_r   = max_r * t_r_frac * (1 + self.poff[0] * 0.25) + math.sin(self.time * 8.0 + tri_layer) * (high * 20.0)
            t_h   = (self.hue + t_h_off) % 1.0
            t_l   = 0.50 + bass * 0.20 + mid * 0.15 + high * 0.15
            t_lw  = max(1, int(1 + bass * 2.0 + high * 1.5))
            for sym in range(self.N_SYM):
                a_mid  = sym / self.N_SYM * math.tau + t_rot
                a_l    = a_mid - math.pi / self.N_SYM * 0.55
                a_r    = a_mid + math.pi / self.N_SYM * 0.55
                tip    = (int(cx + math.cos(a_mid) * t_r * 1.18),
                          int(cy + math.sin(a_mid) * t_r * 1.18))
                base_l = (int(cx + math.cos(a_l)  * t_r * 0.82),
                          int(cy + math.sin(a_l)  * t_r * 0.82))
                base_r = (int(cx + math.cos(a_r)  * t_r * 0.82),
                          int(cy + math.sin(a_r)  * t_r * 0.82))
                pygame.draw.polygon(surf, hsl(t_h, l=t_l),
                                    [tip, base_l, base_r], t_lw)

        for t_ring, (t_speed, t_r_frac, t_h_off) in enumerate(
                [(0.025, 0.12, 0.0), (-0.017, 0.18, 0.45)]):
            t_rot = self.time * t_speed + high * 0.30
            t_r   = max_r * t_r_frac * (1.0 + self.poff[0] * 0.35 + bass * 0.25 + mid * 0.15)
            t_h   = (self.hue + t_h_off) % 1.0
            t_l   = 0.55 + bass * 0.25 + high * 0.20
            t_lw  = max(1, int(1 + bass * 2))
            for sym in range(3):
                a_mid   = sym / 3 * math.tau + t_rot
                a_l     = a_mid - math.pi / 3 * 0.55
                a_r     = a_mid + math.pi / 3 * 0.55
                tip     = (int(cx + math.cos(a_mid) * t_r * 1.2),
                           int(cy + math.sin(a_mid) * t_r * 1.2))
                base_l  = (int(cx + math.cos(a_l)  * t_r * 0.8),
                           int(cy + math.sin(a_l)  * t_r * 0.8))
                base_r_ = (int(cx + math.cos(a_r)  * t_r * 0.8),
                           int(cy + math.sin(a_r)  * t_r * 0.8))
                pygame.draw.polygon(surf, hsl(t_h, l=t_l),
                                    [tip, base_l, base_r_], t_lw)
