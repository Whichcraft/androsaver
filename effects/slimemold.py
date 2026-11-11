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

from .base import Effect

_BASE_SA = math.radians(30)   # sensor angle offset from heading
_BASE_SD = 7                   # sensor distance in sim pixels


class SlimeMold(Effect):
    TRAIL_ALPHA = 0
    RES_DIV     = 1

    def _render_div(self) -> int:
        # Keep trails crisp on larger displays while retaining the compact
        # simulation grid on small screens.  The quality governor can lower it.
        preferred = 1 if min(self.frame.width, self.frame.height) >= 900 else 3
        auto = self._auto_res_div()
        base = max(1, min(preferred, auto))
        quality_scale = max(0.5, min(1.0, float(self.frame.quality_scale)))
        return max(base, min(8, int(math.ceil(base / quality_scale))))

    def _render_size(self):
        if min(self.frame.width, self.frame.height) >= 900 or self._visual_profile() == "tv":
            scale = max(.85, min(1., float(self.frame.quality_scale)))
            w,h = max(1,round(self.frame.width*scale)), max(1,round(self.frame.height*scale))
            return w,h,self.frame.width/w
        return super()._render_size()

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self._rng = self.rng
        self._W, self._H = 1, 1
        self._n = 0
        self._trail = np.zeros((1, 1), dtype=np.float32)
        self._surf = pygame.Surface((1, 1))
        self._scaled = pygame.Surface((1, 1))
        self._hue = 0.30
        self._reset_or_resize_sim()

    def _reset_or_resize_sim(self):
        W, H, RD = self._render_size()
        if self._W > 1 and self._H > 1:
            old_w,old_h = self._W,self._H
            self._px *= W/old_w; self._py *= H/old_h
            self._trail = self._resize_field(self._trail,W,H)
            self._W,self._H = W,H
            self._surf = pygame.Surface((W,H))
            self._scaled = pygame.Surface((self.frame.width,self.frame.height))
            self._reset_workspaces()
            return
        self._W, self._H = W, H
        area = W * H
        self._n = max(5000, min(26000, int(13000 * area / (640 * 360))))
        if self.options.low_spec:
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
        self._scaled = pygame.Surface((self.frame.width, self.frame.height))
        self._reset_workspaces()

    def _reset_workspaces(self):
        self._diffuse = np.empty_like(self._trail)
        self._intensity = np.empty_like(self._trail)
        self._phase = np.empty_like(self._trail)
        self._channel = np.empty_like(self._trail)
        self._visible = np.empty(self._trail.shape, dtype=bool)
        self._packed = np.empty(self._trail.size, dtype=np.uint32)
        self._bits = np.empty(self._trail.size, dtype=np.uint32)

    def _sense(self, offset_ang, dist):
        W, H = self._W, self._H
        sx = np.clip(
            (self._px + np.cos(self._ang + offset_ang) * dist).astype(np.int32),
            0, W - 1)
        sy = np.clip(
            (self._py + np.sin(self._ang + offset_ang) * dist).astype(np.int32),
            0, H - 1)
        return self._trail[sy, sx]

    def _diffuse_trail(self, decay):
        # Diffuse (approximate 3×3 box blur)
        t = self._trail
        # Preserve the original wrapped stencil and addition order without
        # allocating four rolled copies of a full-size chemical field.
        diffused = self._diffuse
        diffused[1:] = t[:-1]
        diffused[0] = t[-1]
        diffused[:-1] += t[1:]
        diffused[-1] += t[0]
        diffused[:, 1:] += t[:, :-1]
        diffused[:, 0] += t[:, -1]
        diffused[:, :-1] += t[:, 1:]
        diffused[:, -1] += t[:, 0]
        np.multiply(t, 4., out=self._channel)
        diffused += self._channel
        diffused /= 8.
        diffused *= decay
        self._trail, self._diffuse = diffused, t

    def _palette_channels(self, intensity, phase, work):
        """Yield the original sine palette without allocating channel fields."""
        np.multiply(intensity, .5, out=phase)
        phase += self._hue
        np.remainder(phase, 1., out=phase)
        for offset in (0., .333, .667):
            np.add(phase, offset, out=work)
            work *= math.tau
            np.sin(work, out=work)
            work *= 127
            work += 128
            work *= intensity
            work *= 2
            np.clip(work, 0, 255, out=work)
            yield work

    def _paint_trail(self):
        # Below 1/128 chemical strength, even the brightest channel is < 1
        # after scaling (255 * 2 / 5 / 128 < 1), so uint8 output is exactly black.
        # Skip only that invisible colour work; keep the full chemical simulation.
        np.greater_equal(self._trail, 1/128, out=self._visible)
        count = np.count_nonzero(self._visible)
        if count < self._trail.size * .65:
            indices = np.flatnonzero(self._visible)
            intensity = self._intensity.ravel()[:count]
            phase = self._phase.ravel()[:count]
            work = self._channel.ravel()[:count]
            np.take(self._trail, indices, out=intensity, mode='clip')
            intensity /= 5.
            np.clip(intensity, 0., 1., out=intensity)
            packed, bits = self._packed[:count], self._bits[:count]
            packed.fill(0)
            for channel, values in enumerate(self._palette_channels(intensity, phase, work)):
                np.copyto(bits, values, casting='unsafe')
                np.left_shift(bits, (2-channel)*8, out=bits)
                np.bitwise_or(packed, bits, out=packed)
            pix = surfarray.pixels2d(self._surf)
            try:
                pix.fill(0)
                # flat writes through even when a surface has padded row pitch.
                pix.T.flat[indices] = packed
            finally:
                del pix
        else:
            # Dense fields avoid the index-gather/scatter overhead entirely.
            np.divide(self._trail, 5., out=self._intensity)
            np.clip(self._intensity, 0., 1., out=self._intensity)
            pix = surfarray.pixels3d(self._surf)
            try:
                channels = self._palette_channels(self._intensity, self._phase, self._channel)
                for channel, values in enumerate(channels):
                    pix[:, :, channel] = values.T
            finally:
                del pix

    def _draw(self, surf, waveform, fft, beat, tick):
        W, H, RD = self._render_size()
        if self._W != W or self._H != H:
            self._reset_or_resize_sim()
            W, H, RD = self._render_size()
        W, H = self._W, self._H
        if self._scaled.get_width() != surf.get_width() or self._scaled.get_height() != surf.get_height():
            self._scaled = pygame.Surface(surf.get_size())
        bass = beat
        mid  = self.frame.mid
        high = self.frame.treble

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

        self._diffuse_trail(.94 - bass * .01)

        self._paint_trail()

        if surf.get_size() != self._surf.get_size():
            pygame.transform.smoothscale(self._surf, surf.get_size(), self._scaled)
            surf.blit(self._scaled, (0, 0), special_flags=pygame.BLEND_RGB_MAX)
        else:
            surf.blit(self._surf, (0, 0), special_flags=pygame.BLEND_RGB_MAX)
