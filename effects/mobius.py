"""Möbius — a solid, shaded Möbius ribbon rendered with perspective projection.

The strip is drawn as depth-sorted filled bands, so the near side hides the
far side. A wireframe of the same surface showed both sides at once and its
lines crossed into moiré noise wherever the strip turned edge-on.

  Bass   → spin speed, tilt wobble speed, twist
  Mid    → spin speed, hue drift
  Treble → spin speed contribution
  Beat   → shiver: the ribbon and its rim flare
"""
import math

import numpy as np
import pygame

from .base import Effect
from .utils import _hsl_batch


class Mobius(Effect):
    TIME_BASED = True
    TRAIL_ALPHA = 150

    _N_V = 6     # colour bands across the ribbon width
    _N_U = 96    # segments around the strip

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self._ry = 0.0   # spin of the strip in its own plane
        self._rx = 0.0   # phase of the tilt wobble
        self._rw = 0.0   # phase of the rocking fourth-dimensional rotation
        self._hue    = 0.55
        self._shiver = 0.0
        u = np.linspace(0, math.tau, self._N_U + 1, dtype=np.float32)
        v = np.linspace(-0.36, 0.36, self._N_V + 1, dtype=np.float32)
        self._u, self._v = np.meshgrid(u, v)          # (N_V+1, N_U+1)

    # ------------------------------------------------------------------

    def _strip_pts(self, twist):
        """Möbius strip 4-D coordinates on the (v, u) grid."""
        u, v = self._u, self._v
        hu   = u / 2.0
        cohu = np.cos(hu) * twist
        x    = (1.0 + v * cohu) * np.cos(u)
        y    = (1.0 + v * cohu) * np.sin(u)
        z    = v * np.sin(hu) * twist
        w    = v * np.sin(hu) * 0.35
        return x, y, z, w

    def _project(self, pts, W, H, fov=3.0):
        """Rotate in XW, spin and tilt; return camera-space xyz and pixel xy."""
        x4, y, z, w4 = pts
        # Rock into the fourth axis; a full XW turn would flatten the strip.
        angle4 = 0.5 * math.sin(self._rw)
        cw, sw = math.cos(angle4), math.sin(angle4)
        # Perspective from the fourth axis scales the whole solid, not one axis.
        scale4 = (fov + 0.35) / np.maximum(0.35, fov + 0.35 - (x4 * sw + w4 * cw))
        x, y, z = (x4 * cw - w4 * sw) * scale4, y * scale4, z * scale4
        # The strip stands upright in the plane of the screen and spins in
        # that plane, with a small wobble for depth. A free tumble turned it
        # edge-on, where it shrank to a dark sliver.
        cs, ss = math.cos(self._ry), math.sin(self._ry)
        tilt = 0.35 * math.sin(self._rx * 2.5)
        yaw = 0.45 * math.sin(self._rx * 1.5 + 1.0)
        ct, st = math.cos(tilt), math.sin(tilt)
        cy, sy = math.cos(yaw), math.sin(yaw)
        xs, ys = x * cs - y * ss, x * ss + y * cs
        yr = ys * ct - z * st
        zt = ys * st + z * ct
        xr = xs * cy + zt * sy
        zr = -xs * sy + zt * cy

        zr = np.maximum(zr, -fov + 0.2)
        sc = fov / (fov + zr)
        half = min(W, H) * 0.50
        screen = np.stack([xr * sc * half + W / 2.0, yr * sc * half + H / 2.0], axis=-1)
        return np.stack([xr, yr, zr], axis=-1), screen

    def _draw(self, surf, waveform, fft, beat, tick):
        W, H = surf.get_size()
        step = self._frame_step()
        motion, light = self._audio_response(beat)
        bass, mid, high = motion
        motion_scale, accent = self._profile_response()
        self._hue = (self._hue + (0.0012 + mid * 0.002) * step) % 1.0
        self._ry += (0.008 + bass * 0.006 + mid * 0.003 + high * 0.003) * step * motion_scale
        self._rx += (0.003 + bass * 0.002) * step * motion_scale
        self._rw += (0.004 + mid * 0.002 + high * 0.003) * step * motion_scale
        self._shiver = float(light[0]) * accent * 0.4
        twist = 1.0 + bass * 0.06 + mid * 0.06

        cam, projected = self._project(self._strip_pts(twist), W, H)

        # Fit the entire strip together: preserve shape and leave a glow margin.
        center = np.array([W / 2.0, H / 2.0])
        offsets = projected - center
        extent = np.max(np.abs(offsets), axis=(0, 1))
        fit = min(1.0, (W * 0.5 - max(4, W * 0.04)) / max(1, extent[0]),
                  (H * 0.5 - max(4, H * 0.04)) / max(1, extent[1]))
        self._fit = min(getattr(self, "_fit", fit) + 0.001 * step, fit)
        self._projected = center + offsets * self._fit

        # One quad per (band, segment), painted far to near.
        p00, p01 = cam[:-1, :-1], cam[:-1, 1:]
        p10, p11 = cam[1:, :-1], cam[1:, 1:]
        depth = (p00[..., 2] + p01[..., 2] + p10[..., 2] + p11[..., 2]) * 0.25
        normal = np.cross(p01 - p00, p10 - p00)
        # The strip has one side, so only the tilt toward the viewer matters.
        facing = np.abs(normal[..., 2]) / np.maximum(np.linalg.norm(normal, axis=-1), 1e-9)
        near = np.clip(0.5 - depth / 3.0, 0.0, 1.0)

        # A colour field that is continuous on the strip: crossing the seam
        # flips the width coordinate, and v * cos(u / 2) flips with it.
        v = ((np.arange(self._N_V) + 0.5) / self._N_V - 0.5)[:, None]
        u = ((np.arange(self._N_U) + 0.5) / self._N_U * math.tau)[None, :]
        hue = self._hue + 0.34 * v * np.cos(u / 2.0) + 0.16 * np.sin(u)
        lum = (0.10 + 0.28 * facing ** 1.5) * (0.45 + 0.75 * near)
        lum = lum * (1.0 + bass * 0.35) + self._shiver * 0.12 * facing
        fill = _hsl_batch(hue, 0.9, np.minimum(lum, 0.62)).tolist()
        rim = _hsl_batch(hue, 0.55, np.clip(0.30 + 0.45 * near + self._shiver * 0.3, 0, 0.92)).tolist()

        s = np.rint(self._projected).astype(int)
        quads = np.stack([s[:-1, :-1], s[:-1, 1:], s[1:, 1:], s[1:, :-1]], axis=2).tolist()
        rim_w = max(1, round(min(W, H) / 360))
        last = self._N_V - 1
        polygon, line = pygame.draw.polygon, pygame.draw.line
        for k in np.argsort(-depth, axis=None).tolist():
            i, j = divmod(k, self._N_U)
            q = quads[i][j]
            polygon(surf, fill[i][j], q)
            # The single boundary curve of the strip, as a bright rim.
            if i == 0:
                line(surf, rim[i][j], q[0], q[1], rim_w)
            if i == last:
                line(surf, rim[i][j], q[3], q[2], rim_w)
