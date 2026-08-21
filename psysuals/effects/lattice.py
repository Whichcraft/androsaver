"""Lattice — crystal grid of glowing nodes and beam lines with feedback.

A dynamic-resolution grid of nodes glows according to their assigned FFT frequency bin,
using a center-out frequency mapping (center columns = bass, edge columns = treble).
Grid density scales up on larger displays. Features a rotozoom feedback loop
creating a trailing tunnel effect.
"""
import math
import numpy as np
import pygame
import config
from .utils import hsl
from .base import Effect

_FFT_USE = 0.55
_SHOCK_W = 22
_IDLE = 0.08
_FFT_START_BIN = 3  # skip DC component and sub-bass rumble

class Lattice(Effect):
    TRAIL_ALPHA = 0 # Managed by internal surface

    @property
    def RES_DIV(self):
        if config.WIDTH >= 2560:
            return 2
        elif config.WIDTH >= 1600:
            return 3
        return 4

    @property
    def _grid_cols(self):
        if config.WIDTH >= 2560:
            return 22
        elif config.WIDTH >= 1600:
            return 18
        return 14

    @property
    def _grid_rows(self):
        if config.WIDTH >= 2560:
            return 14
        elif config.WIDTH >= 1600:
            return 12
        return 9

    def __init__(self, **kwargs):
        super().__init__(**kwargs)

        self._surf = None
        self._hue = 0.52
        self._scaled = None
        self._shock_r = 9999.0
        self._shock_spd = 0.0
        self._beat_prev = 0.0
        self._scale = 1.0
        self._svel = 0.0
        self._cx = 0.0
        self._cy = 0.0
        self._max_r = 1.0
        self._nodes = []
        self._col_peaks = np.ones(self._grid_cols, dtype=np.float32) * 0.2

        W, H = self._render_size()[:2]
        self._init_surfaces(W, H)

    def _init_surfaces(self, W, H):
        self._surf = pygame.Surface((W, H))
        self._surf.fill((0, 0, 0))
        self._shock_spd = 6.0 * (W / 640.0)
        cx, cy = W / 2.0, H / 2.0
        self._cx, self._cy = cx, cy
        self._max_r = math.hypot(cx, cy)
        self._build_grid()

    def _build_grid(self):
        n_cols = self._grid_cols
        n_rows = self._grid_rows
        W, H = self._render_size()[:2]
        cx, cy = self._cx, self._cy
        x0, y0 = W * 0.08, H * 0.10
        cw = W * 0.84 / max(n_cols - 1, 1)
        ch = H * 0.80 / max(n_rows - 1, 1)

        self._nodes = []
        for row in range(n_rows):
            for col in range(n_cols):
                ox = x0 + col * cw
                oy = y0 + row * ch
                dist = math.hypot(ox - cx, oy - cy)
                h_off = dist / self._max_r * 0.55
                self._nodes.append({
                    'ox': ox, 'oy': oy,
                    'col': col, 'row': row,
                    'dist': dist,
                    'h_off': h_off,
                })

        self._col_peaks = np.ones(n_cols, dtype=np.float32) * 0.2

    def _draw_beam(self, ni_a, ni_b, hue, sx_arr, sy_arr, bright,
                   scale_factor, high):
        w_base = max(1, int(1.0 * scale_factor))
        pygame.draw.line(self._surf, hsl(hue, s=0.25, l=0.06),
                         (int(sx_arr[ni_a]), int(sy_arr[ni_a])),
                         (int(sx_arr[ni_b]), int(sy_arr[ni_b])), w_base)
        avg_b = (bright[ni_a] + bright[ni_b]) * 0.5
        if avg_b > 0.1:
            w_outer = max(1, int((3.5 + high * 1.5) * scale_factor))
            pygame.draw.line(self._surf, hsl(hue, l=min(avg_b * 0.15, 0.25)),
                             (int(sx_arr[ni_a]), int(sy_arr[ni_a])),
                             (int(sx_arr[ni_b]), int(sy_arr[ni_b])), w_outer)
            w_core = max(1, int((1.2 + high * 0.5) * scale_factor))
            pygame.draw.line(self._surf, hsl(hue, l=min(avg_b * 0.4, 0.7)),
                             (int(sx_arr[ni_a]), int(sy_arr[ni_a])),
                             (int(sx_arr[ni_b]), int(sy_arr[ni_b])), w_core)

    def _bin(self, col: int, n_cols: int, fft_len: int) -> int:
        start = _FFT_START_BIN
        end = min(fft_len - 1, int(fft_len * _FFT_USE))
        if end <= start:
            return 0
        # Center-out mapping: center columns → low frequencies, edge columns → high
        center = (n_cols - 1) / 2.0
        normalized = abs(col - center) / max(center, 1.0)
        return start + int(normalized * (end - start))

    def draw(self, surf, waveform, fft, beat, tick):
        n_cols = self._grid_cols
        n_rows = self._grid_rows
        W, H = self._render_size()[:2]
        if self._surf is None or self._surf.get_width() != W or self._surf.get_height() != H:
            self._init_surfaces(W, H)

        fft_len = len(fft)
        bass = beat
        mid = config.MID_ENERGY
        high = config.TREBLE_ENERGY
        cx, cy = self._cx, self._cy
        scale_factor = min(W, H) / 640.0

        self._hue = (self._hue + 0.0025 + mid * 0.005 + high * 0.002) % 1.0

        if beat > 0.6 and self._beat_prev <= 0.6:
            self._shock_r = 0.0
        self._beat_prev = beat
        self._shock_r += self._shock_spd * (1.0 + bass * 2.0 + mid * 0.8)

        self._svel += (1.0 + bass * 0.04 + high * 0.02 - self._scale) * 0.18
        self._svel *= 0.70
        self._scale = max(0.90, min(self._scale + self._svel, 1.12))

        # Feedback loop at lower res (zoom scaled by bass and mids)
        sc = 1.0 + bass * 0.01 + mid * 0.008
        rw, rh = max(1, int(W * sc)), max(1, int(H * sc))
        rotated = pygame.transform.scale(self._surf, (rw, rh))
        self._surf.fill((0, 0, 0))
        self._surf.blit(rotated, (-((rw - W) // 2), -((rh - H) // 2)))
        self._surf.fill((230, 230, 230), special_flags=pygame.BLEND_RGB_MULT)

        sc = self._scale
        sx_arr = np.empty(len(self._nodes), dtype=np.float32)
        sy_arr = np.empty(len(self._nodes), dtype=np.float32)
        bright = np.empty(len(self._nodes), dtype=np.float32)

        # Dynamic frequency peak normalization with noise gate to keep column activity balanced
        raw_energies = np.zeros(n_cols, dtype=np.float32)
        for col in range(n_cols):
            raw_energies[col] = float(fft[self._bin(col, n_cols, fft_len)])

        # Apply noise gate (subtract background noise floor)
        raw_energies = np.maximum(raw_energies - 0.015, 0.0)

        # Guard against size mismatch after a grid resize
        if len(self._col_peaks) != n_cols:
            self._col_peaks = np.ones(n_cols, dtype=np.float32) * 0.2

        # Peak tracking: decay slowly, snap instantly to new peaks
        self._col_peaks = np.maximum(self._col_peaks * 0.996, raw_energies)
        self._col_peaks = np.maximum(self._col_peaks, 0.08)

        norm_energies = raw_energies / self._col_peaks
        # Node column brightness scaled by mids and raw energy
        scaled_energies = norm_energies * (0.50 + mid * 0.30)

        for ni, nd in enumerate(self._nodes):
            sx = cx + (nd['ox'] - cx) * sc
            sy = cy + (nd['oy'] - cy) * sc
            sx_arr[ni], sy_arr[ni] = sx, sy
            energy = float(scaled_energies[nd['col']]) + _IDLE
            dist = math.hypot(sx - cx, sy - cy)
            shock_w = (_SHOCK_W / self.RES_DIV) * scale_factor
            shock = max(0.0, 1.0 - abs(dist - self._shock_r) / shock_w)
            bright[ni] = min(energy + shock * (0.6 + bass * 0.4 + high * 0.3), 1.8)

        # Draw Beams
        for row in range(n_rows):
            for col in range(n_cols):
                ni = row * n_cols + col
                hue = (self._hue + self._nodes[ni]['h_off']) % 1.0
                if col < n_cols - 1:
                    self._draw_beam(ni, ni + 1, hue, sx_arr, sy_arr, bright,
                                    scale_factor, high)
                if row < n_rows - 1:
                    self._draw_beam(ni, ni + n_cols, hue, sx_arr, sy_arr, bright,
                                    scale_factor, high)

        # Draw Nodes
        for ni, nd in enumerate(self._nodes):
            hue = (self._hue + nd['h_off']) % 1.0

            # Draw base faint node (shimmers with treble)
            r_base = max(1, int((2.0 + high * 1.8) * scale_factor))
            pygame.draw.circle(self._surf, hsl(hue, s=0.25, l=0.08),
                               (int(sx_arr[ni]), int(sy_arr[ni])), r_base)

            b = float(bright[ni])
            if b > 0.15:
                base_r = (2.0 + b * 5.0 + high * 2.5) * scale_factor
                r_core = max(1, int(base_r))
                r_mid = max(2, int(base_r * 1.8))
                r_outer = max(3, int(base_r * 3.0))

                # Outer soft glow
                pygame.draw.circle(self._surf, hsl(hue, l=min(b * 0.15 + 0.02, 0.25)),
                                   (int(sx_arr[ni]), int(sy_arr[ni])), r_outer)
                # Middle soft glow
                pygame.draw.circle(self._surf, hsl(hue, l=min(b * 0.40 + 0.08, 0.60)),
                                   (int(sx_arr[ni]), int(sy_arr[ni])), r_mid)
                # Core bright center
                pygame.draw.circle(self._surf, hsl(hue, l=min(b * 0.75 + 0.15 + high * 0.05, 0.95)),
                                   (int(sx_arr[ni]), int(sy_arr[ni])), r_core)

        if surf.get_size() != self._surf.get_size():
            if self._scaled is None or self._scaled.get_size() != surf.get_size():
                self._scaled = pygame.transform.scale(self._surf, surf.get_size())
            else:
                pygame.transform.scale(self._surf, surf.get_size(), self._scaled)
            surf.blit(self._scaled, (0, 0), special_flags=pygame.BLEND_RGB_MAX)
        else:
            surf.blit(self._surf, (0, 0), special_flags=pygame.BLEND_RGB_MAX)
