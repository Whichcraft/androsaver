"""Clifford — high-contrast, 3D-shaded strange-attractor storm."""
import math

import numpy as np
import pygame
import pygame.surfarray as surfarray

import config
from .base import Effect


class Clifford(Effect):
    TRAIL_ALPHA = 0
    RES_DIV     = 2
    _FADE_ALPHA = 18

    _N = 80_000
    _PASSES = 6
    _PRESETS = [
        (-1.40, 1.60, 1.00, 0.70),
        (-1.70, 1.80, -1.90, -0.40),
        (1.30, -1.70, 1.80, 1.30),
        (-1.20, 1.90, -0.90, 1.70),
        (1.60, 1.10, -1.50, 0.90),
    ]

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self._rng = np.random.default_rng(config.RNG_SEED)
        self._trail = pygame.Surface((1, 1))
        self._frame_surf = pygame.Surface((1, 1))
        self._scaled = pygame.Surface((1, 1))
        self._fade = pygame.Surface((1, 1), pygame.SRCALPHA)
        self._hue = float(self._rng.random())
        self._a, self._b = np.float32(-1.4), np.float32(1.6)
        self._c, self._d = np.float32(1.0),  np.float32(0.7)
        self._ta = self._a; self._tb = self._b
        self._tc = self._c; self._td = self._d
        self._boost = 0.0
        self._xmin = -2.0
        self._xmax = 2.0
        self._ymin = -2.0
        self._ymax = 2.0
        self._beat_prev = 0.0
        self._reset_state()

    def _reset_state(self):
        W, H, RD = self._render_size()
        self._W, self._H = W, H
        self._trail = pygame.Surface((W, H))
        self._trail.fill((0, 0, 0))
        self._frame_surf = pygame.Surface((W, H))
        self._frame_surf.fill((0, 0, 0))
        self._fade = pygame.Surface((W, H), pygame.SRCALPHA)
        self._fade.fill((0, 0, 0, self._FADE_ALPHA))
        self._scaled = pygame.Surface((config.WIDTH, config.HEIGHT))
        
        self._n = self._N
        if getattr(config, "LOW_SPEC", False):
            self._n //= 2
            
        self._xs = self._rng.uniform(-1.6, 1.6, self._n).astype(np.float32)
        self._ys = self._rng.uniform(-1.6, 1.6, self._n).astype(np.float32)
        self._xmin, self._xmax = -2.0, 2.0
        self._ymin, self._ymax = -2.0, 2.0

        # Pre-allocated grid for cosine palette (O1)
        self._x_g = np.linspace(-1.0, 1.0, W, dtype=np.float32)
        self._y_g = np.linspace(-1.0, 1.0, H, dtype=np.float32)
        self._xx, self._yy = np.meshgrid(self._x_g, self._y_g, indexing='ij')
        # Pre-allocated multi-pass point buffer (O2)
        passes = 4 if getattr(config, "LOW_SPEC", False) else self._PASSES
        self._all_x = np.empty((passes, self._n), dtype=np.float32)
        self._all_y = np.empty((passes, self._n), dtype=np.float32)

        self._new_params(force=True)

    def _new_params(self, force=False):
        base = self._PRESETS[int(self._rng.integers(0, len(self._PRESETS)))]
        jitter = 0.0 if force else 0.18
        offsets = self._rng.uniform(-jitter, jitter, 4)
        self._ta = np.float32(base[0] + offsets[0])
        self._tb = np.float32(base[1] + offsets[1])
        self._tc = np.float32(base[2] + offsets[2])
        self._td = np.float32(base[3] + offsets[3])

    def draw(self, surf, waveform, fft, beat, tick):
        W, H, RD = self._render_size()
        bass = beat
        mid  = config.MID_ENERGY
        high = config.TREBLE_ENERGY

        if self._trail.get_width() != W or self._trail.get_height() != H:
            self._reset_state()
            W, H = self._W, self._H
        sw, sh = surf.get_size()
        if self._scaled.get_width() != sw or self._scaled.get_height() != sh:
            self._scaled = pygame.Surface((sw, sh))

        self._hue = (self._hue + 0.004 + mid * 0.002 + high * 0.006) % 1.0

        if bass > 0.8 and self._beat_prev <= 0.8:
            self._new_params()
        self._beat_prev = bass

        # Smooth parameter interpolation
        spd  = 0.022 + mid * 0.025 + bass * 0.020 + self._boost * 0.008
        self._a = np.float32(self._a + (self._ta - self._a) * spd)
        self._b = np.float32(self._b + (self._tb - self._b) * spd)
        self._c = np.float32(self._c + (self._tc - self._c) * spd)
        self._d = np.float32(self._d + (self._td - self._d) * spd)

        xs, ys = self._xs, self._ys
        all_x = self._all_x
        all_y = self._all_y
        # Multi-pass iteration to generate high point density
        for i in range(self._all_x.shape[0]):
            nx = np.sin(self._a * ys) - np.cos(self._b * xs)
            ny = np.sin(self._c * xs) - np.cos(self._d * ys)
            xs = nx.astype(np.float32)
            ys = ny.astype(np.float32)
            all_x[i] = xs
            all_y[i] = ys
        self._xs, self._ys = xs, ys

        if not np.isfinite(xs).all() or not np.isfinite(ys).all():
            self._reset_state()
            return

        draw_x = all_x.ravel()
        draw_y = all_y.ravel()

        # Dynamic bounding frame interpolation
        x_lo, x_hi = np.percentile(draw_x, (1.0, 99.0))
        y_lo, y_hi = np.percentile(draw_y, (1.0, 99.0))
        if x_hi - x_lo < 0.10 or y_hi - y_lo < 0.10:
            self._reset_state()
            return

        self._xmin += (x_lo - self._xmin) * 0.12
        self._xmax += (x_hi - self._xmax) * 0.12
        self._ymin += (y_lo - self._ymin) * 0.12
        self._ymax += (y_hi - self._ymax) * 0.12

        x_span = max(0.01, self._xmax - self._xmin)
        y_span = max(0.01, self._ymax - self._ymin)

        # Map to pixel indexes
        ix = np.clip(((draw_x - self._xmin) / x_span * (W - 1)).astype(np.int32), 0, W - 1)
        iy = np.clip(((draw_y - self._ymin) / y_span * (H - 1)).astype(np.int32), 0, H - 1)

        # Calculate density counts using fast np.bincount
        indices = ix * H + iy
        counts = np.bincount(indices, minlength=W * H)
        density = counts.reshape((W, H)).astype(np.float32)

        # Compress the density range so the attractor reads as a solid,
        # energetic structure instead of a few dim isolated pixels.
        if density.max() > 0:
            log_density = np.log1p(density)
            norm_density = np.power(log_density / log_density.max(), 0.62)
        else:
            norm_density = np.zeros_like(density)

        # 3D relief shading computation
        if norm_density.max() > 0:
            dy, dx = np.gradient(norm_density)
            # Light source orbits over time
            l_ang = tick * 0.035
            lx = math.cos(l_ang)
            ly = math.sin(l_ang)
            lz = 0.58
            l_len = math.hypot(lx, ly, lz)
            lx /= l_len; ly /= l_len; lz /= l_len

            # Normal vector components in bump-map relief space
            nz = 0.09
            n_len = np.sqrt(dx*dx + dy*dy + nz*nz)
            
            # Diffuse component (N . L)
            diffuse = ((-dx) * lx + (-dy) * ly + nz * lz) / n_len
            diffuse = np.clip(diffuse, 0.0, 1.0)
            
            # Strong base emission plus audio-reactive bloom. The hot core is
            # lifted separately so beats produce a visible power surge.
            hot = np.clip((norm_density - 0.42) / 0.58, 0.0, 1.0)
            emission = 0.58 + bass * 0.55 + high * 0.35 + self._boost * 0.16
            bright = norm_density * emission * (0.52 + 0.48 * diffuse)
            bright += hot * (0.18 + bass * 0.35)
        else:
            bright = np.zeros_like(norm_density)

        # Map color values across grid coordinates using cosine palette
        xx, yy = self._xx, self._yy
        r = np.hypot(xx, yy)
        ang = np.arctan2(yy, xx) / math.tau + 0.5
        h_arr = (self._hue + ang * 0.45 + r * 0.20) % 1.0

        r_arr = np.clip((np.sin(h_arr * math.tau) * 127 + 128) * bright * 2.4, 0, 255).astype(np.uint32)
        g_arr = np.clip((np.sin((h_arr + 0.333) * math.tau) * 127 + 128) * bright * 2.4, 0, 255).astype(np.uint32)
        b_arr = np.clip((np.sin((h_arr + 0.667) * math.tau) * 127 + 128) * bright * 2.4, 0, 255).astype(np.uint32)
        hot = np.clip(bright - 0.72, 0.0, 1.0) * 255
        r_arr = np.clip(r_arr + hot, 0, 255).astype(np.uint32)
        g_arr = np.clip(g_arr + hot, 0, 255).astype(np.uint32)
        b_arr = np.clip(b_arr + hot, 0, 255).astype(np.uint32)
        colors = (r_arr << 16) | (g_arr << 8) | b_arr

        # Write frame buffer
        pix = surfarray.pixels2d(self._frame_surf)
        try:
            pix[:, :] = colors
        finally:
            del pix

        # Fade and accumulate trails
        self._trail.blit(self._fade, (0, 0))
        self._trail.blit(self._frame_surf, (0, 0), special_flags=pygame.BLEND_RGB_MAX)

        if surf.get_size() != self._trail.get_size():
            if self._scaled.get_size() != surf.get_size():
                self._scaled = pygame.Surface(surf.get_size())
            pygame.transform.scale(self._trail, surf.get_size(), self._scaled)
            surf.blit(self._scaled, (0, 0), special_flags=pygame.BLEND_RGB_MAX)
        else:
            surf.blit(self._trail, (0, 0), special_flags=pygame.BLEND_RGB_MAX)
