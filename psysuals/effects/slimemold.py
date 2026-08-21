"""SlimeMold — Physarum-style multi-agent slime simulation.

Thousands of agents deposit chemical trails, sense ahead-left / ahead /
ahead-right, and steer toward the strongest signal.  The result is a
self-organising vein network that pulses and reforms with the music.

  Bass   → agent speed + trail deposit strength
  Mid    → sensor sensitivity (sharper gradient response)
  Treble → sensor angle (wider = more meandering)
  Beat   → teleport burst + trail strength spike
"""
import math

import numpy as np
import pygame
import pygame.surfarray as surfarray

import config
from .base import Effect

_BASE_SA = math.radians(30)   # sensor angle offset from heading
_BASE_SD = 7                   # sensor distance in sim pixels


class SlimeMold(Effect):
    TRAIL_ALPHA = 0
    RES_DIV     = 3

    def _render_div(self) -> int:
        # Keep the deliberately chunky laptop look, but use a finer grid on
        # TV-sized displays where enlarged pixels become distracting.
        auto = self._auto_res_div()
        preferred = 2 if min(config.WIDTH, config.HEIGHT) >= 900 else 3
        return max(1, min(preferred, auto))

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self._rng = np.random.default_rng(config.RNG_SEED)
        self._W, self._H = 1, 1
        self._n = 0
        self._trail = np.zeros((1, 1), dtype=np.float32)
        self._surf = pygame.Surface((1, 1))
        self._scaled = pygame.Surface((1, 1))
        self._hue = 0.30
        self._reset_or_resize_sim()

    def _reset_or_resize_sim(self):
        W, H, RD = self._render_size()
        self._W, self._H = W, H
        area = W * H
        self._n = max(5000, min(26000, int(13000 * area / (640 * 360))))
        if getattr(config, "LOW_SPEC", False):
            self._n = max(2500, self._n // 2)
        cx, cy   = W / 2.0, H / 2.0
        r_spread = min(W, H) * 0.25
        self._px  = np.clip(
            self._rng.normal(cx, r_spread * 0.3, self._n),
            0, W - 1).astype(np.float32)
        self._py  = np.clip(
            self._rng.normal(cy, r_spread * 0.3, self._n),
            0, H - 1).astype(np.float32)
        self._ang = self._rng.uniform(0, math.tau, self._n).astype(np.float32)
        self._trail = np.zeros((H, W), dtype=np.float32)
        self._surf  = pygame.Surface((W, H))
        self._scaled = pygame.Surface((config.WIDTH, config.HEIGHT))

    def _sense(self, offset_ang, dist):
        W, H = self._W, self._H
        sx = np.clip(
            (self._px + np.cos(self._ang + offset_ang) * dist).astype(np.int32),
            0, W - 1)
        sy = np.clip(
            (self._py + np.sin(self._ang + offset_ang) * dist).astype(np.int32),
            0, H - 1)
        return self._trail[sy, sx]

    def draw(self, surf, waveform, fft, beat, tick):
        W, H, RD = self._render_size()
        if self._W != W or self._H != H:
            self._reset_or_resize_sim()
            W, H, RD = self._render_size()
        W, H = self._W, self._H
        if self._scaled.get_width() != surf.get_width() or self._scaled.get_height() != surf.get_height():
            self._scaled = pygame.Surface(surf.get_size())
        bass = beat
        mid  = config.MID_ENERGY
        high = config.TREBLE_ENERGY

        self._hue = (self._hue + 0.002 + mid * 0.003) % 1.0

        sa  = _BASE_SA + high * 0.40
        sd  = _BASE_SD + bass * 5.0
        spd = 0.7 + bass * 1.5

        # Beat: teleport a fraction of agents back toward center
        if bass > 0.7:
            n_tp = int(self._n * 0.05)
            idx  = self._rng.integers(0, self._n, n_tp)
            cx, cy = W / 2.0, H / 2.0
            r_spread = min(W, H) * 0.20
            self._px[idx]  = np.clip(
                self._rng.normal(cx, r_spread, n_tp), 0, W - 1).astype(np.float32)
            self._py[idx]  = np.clip(
                self._rng.normal(cy, r_spread, n_tp), 0, H - 1).astype(np.float32)
            self._ang[idx] = self._rng.uniform(0, math.tau, n_tp).astype(np.float32)

        fwd   = self._sense(0.0,  sd)
        left  = self._sense(-sa,  sd)
        right = self._sense(+sa,  sd)

        rot = 0.18 + high * 0.15

        go_left  = (left  > fwd) & (left  >= right)
        go_right = (right > fwd) & (right >  left)
        go_rand  = (~go_left) & (~go_right) & (
            self._rng.random(self._n) < 0.05)

        d_ang = np.zeros(self._n, dtype=np.float32)
        d_ang[go_left]  = -rot
        d_ang[go_right] = +rot
        rnd_n = int(go_rand.sum())
        if rnd_n:
            d_ang[go_rand] = (
                (self._rng.random(rnd_n) - 0.5) * rot * 2).astype(np.float32)
        self._ang += d_ang

        self._px = (self._px + np.cos(self._ang) * spd) % W
        self._py = (self._py + np.sin(self._ang) * spd) % H

        # Deposit trail
        trail_h, trail_w = self._trail.shape
        # Float32 modulo can round a value at the upper edge back to W/H;
        # clamp against the actual NumPy trail shape before indexed writes.
        ix = np.clip(self._px.astype(np.int32), 0, trail_w - 1)
        iy = np.clip(self._py.astype(np.int32), 0, trail_h - 1)
        np.add.at(self._trail, (iy, ix), 0.8 + bass * 0.6)

        # Diffuse (approximate 3×3 box blur)
        t = self._trail
        diffused = (np.roll(t, 1, 0) + np.roll(t, -1, 0)
                    + np.roll(t, 1, 1) + np.roll(t, -1, 1)
                    + t * 4.0) / 8.0
        self._trail = diffused * (0.94 - bass * 0.01)

        # Render: map trail intensity to hue
        t_norm = np.clip(self._trail / 5.0, 0.0, 1.0)
        h_val  = (self._hue + t_norm * 0.5) % 1.0
        r_arr  = np.clip(
            (np.sin(h_val * math.tau) * 127 + 128) * t_norm * 2, 0, 255
        ).astype(np.uint32)
        g_arr  = np.clip(
            (np.sin((h_val + 0.333) * math.tau) * 127 + 128) * t_norm * 2, 0, 255
        ).astype(np.uint32)
        b_arr  = np.clip(
            (np.sin((h_val + 0.667) * math.tau) * 127 + 128) * t_norm * 2, 0, 255
        ).astype(np.uint32)
        colors = (r_arr << 16) | (g_arr << 8) | b_arr

        pix = surfarray.pixels2d(self._surf)
        try:
            pix[:] = colors.T
        finally:
            del pix

        if surf.get_size() != self._surf.get_size():
            pygame.transform.smoothscale(self._surf, surf.get_size(), self._scaled)
            surf.blit(self._scaled, (0, 0), special_flags=pygame.BLEND_RGB_MAX)
        else:
            surf.blit(self._surf, (0, 0), special_flags=pygame.BLEND_RGB_MAX)
