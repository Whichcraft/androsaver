"""Corridor — a first-person ride down a solid neon corridor.

The corridor is built from wall, floor and ceiling panels in true
perspective, painted far to near so the walls close the view. Neon ribs are
set into the walls; between them run light bars on both walls, a broken
light strip along the ceiling and two lane lines along the floor. All of it
slides past the camera. The passage bends gently, fades into fog and ends in
a glowing doorway.

  Bass     → ride speed, wall and doorway glow
  Mid      → ride speed, hue drift
  Spectrum → wall light bars by depth: bass near the camera, treble far away
  Beat     → a pulse of light shoots down the corridor, flaring each rib
"""
import math

import numpy as np
import pygame

from .base import Effect
from .utils import _hsl_batch, hsl, track_levels


class Corridor(Effect):
    TIME_BASED  = True
    TRAIL_ALPHA = 255   # every frame is fully repainted; no trails

    N_SEGMENTS = 32
    Z_FAR      = 13.0
    Z_NEAR     = 0.42
    HALF_W     = 1.7
    HALF_H     = 1.0
    RIB_DEPTH  = 0.10   # depth of a neon rib, in world units
    RIB_EVERY  = 2      # segments per rib
    GATE_EVERY = 8      # segments per wider, brighter gate
    _MAX_PULSES = 3

    # Cross-section points as fractions of the half extents (x, y; y down).
    _SECTION = np.array([
        (-1, -1), (1, -1), (1, 1), (-1, 1),                 # corners TL TR BR BL
        (-0.20, -1), (0.20, -1),                            # ceiling light strip
        (-0.58, 1), (-0.52, 1), (0.52, 1), (0.58, 1),       # floor lane lines
        (-1, -0.16), (-1, 0.16), (1, -0.16), (1, 0.16),     # wall light bars
    ], dtype=np.float64) * (HALF_W, HALF_H)

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.hue  = 0.0
        self.time = 0.0          # distance travelled, in world units
        self._pulses = []        # [z, strength] of beat pulses running away
        self._beat_prev = 0.0
        self._spectrum = np.zeros(self.N_SEGMENTS + 1)
        self._peak = 1e-3

    # ------------------------------------------------------------------

    @staticmethod
    def _path(s):
        """Centre line of the corridor at travelled distance s."""
        return (2.0 * np.sin(s * 0.10) + 0.35 * np.sin(s * 0.23 + 1.0),
                0.7 * np.sin(s * 0.13 + 2.0))

    def _project(self, z, W, H):
        """Screen positions of the cross-section at each depth z → (n, points, 2)."""
        # Offset relative to the camera, with the local heading removed so
        # the view always looks along the corridor and bends appear ahead.
        x0, y0 = self._path(self.time)
        x1, y1 = self._path(self.time + 0.01)
        px, py = self._path(self.time + z)
        ox = px - x0 - (x1 - x0) / 0.01 * z
        oy = py - y0 - (y1 - y0) / 0.01 * z
        x = self._SECTION[None, :, 0] + ox[:, None]
        y = self._SECTION[None, :, 1] + oy[:, None]
        roll = 0.09 * math.sin(self.time * 0.07)
        c, s = math.cos(roll), math.sin(roll)
        scale = min(W, H) * 0.74 / z[:, None]
        return np.stack([(x * c - y * s) * scale + W / 2.0,
                         (x * s + y * c) * scale + H / 2.0], axis=-1)

    def _update_spectrum(self, fft):
        self._spectrum, self._peak = track_levels(self._spectrum, self._peak, fft, self._smooth)

    # ------------------------------------------------------------------

    def _draw(self, surf, waveform, fft, beat, tick):
        W, H = surf.get_size()
        step = self._frame_step()
        motion, light = self._audio_response(beat)
        bass, mid, high = motion
        motion_scale, accent = self._profile_response()
        flash = float(light[0]) * accent

        self.hue = (self.hue + (0.0012 + mid * 0.0015) * step) % 1.0
        self.time += (0.045 + bass * 0.05 + mid * 0.03 + high * 0.01) * step * motion_scale
        self._update_spectrum(fft)

        if beat > 0.60 and self._beat_prev <= 0.60 and len(self._pulses) < self._MAX_PULSES:
            self._pulses.append([0.0, min(1.5, beat)])
        self._beat_prev = beat
        for pulse in self._pulses:
            pulse[0] += 0.45 * step
        self._pulses = [p for p in self._pulses if p[0] < self.Z_FAR + 2.0]

        # Slice k sits at the camera-side edge of segment k.
        n = self.N_SEGMENTS
        spacing = self.Z_FAR / n
        first = int(math.floor(self.time / spacing))
        z_raw = np.arange(n + 1) * spacing - (self.time - first * spacing)
        z = np.maximum(z_raw, self.Z_NEAR)
        pts = np.rint(self._project(z, W, H)).astype(int)
        ids = first + np.arange(n + 1)
        ribbed = ids % self.RIB_EVERY == 0
        gate = ids % self.GATE_EVERY == 0
        rib_z = z_raw + np.where(gate, 2.2, 1.0) * self.RIB_DEPTH
        rib = np.rint(self._project(np.maximum(rib_z, self.Z_NEAR), W, H)).astype(int)

        fog = np.clip(1.0 - z / self.Z_FAR, 0.0, 1.0) ** 1.6
        stripe = np.where(ids // self.RIB_EVERY % 2 == 0, 1.0, 0.6)
        lit = ids % self.RIB_EVERY == 1          # segments carrying lights
        hues = self.hue + ids * 0.035
        level = self._spectrum
        glow = 1.0 + flash * 0.5 + bass * 0.4
        pulse = np.zeros(n + 1)
        for pz, strength in self._pulses:
            pulse += strength * np.exp(-((z - pz) / 0.8) ** 2)
        pulse = np.minimum(pulse, 1.5) * (0.35 + 0.65 * fog)

        wall_c  = _hsl_batch(hues, 0.85, fog * stripe * 0.075 * glow).tolist()
        floor_c = _hsl_batch(hues + 0.04, 0.8, fog * stripe * (0.04 + 0.05 * level) * glow).tolist()
        ceil_c  = _hsl_batch(hues - 0.04, 0.8, fog * stripe * 0.035 * glow).tolist()
        bar_c   = _hsl_batch(hues + 0.12, 1.0, np.minimum(
            fog * (0.10 + 0.55 * level) * glow + pulse * 0.25, 0.85)).tolist()
        rib_c   = _hsl_batch(hues, 1.0, np.minimum(
            fog * np.where(gate, 0.52, 0.30) * (1.0 + flash * 0.3) + pulse * 0.4, 0.9)).tolist()
        lamp_c  = _hsl_batch(hues, 0.35, np.minimum(fog * 0.70 + pulse * 0.2, 0.95)).tolist()
        lane_c  = _hsl_batch(hues + 0.5, 0.9, fog * 0.34 * glow).tolist()
        pts, rib = pts.tolist(), rib.tolist()

        # The doorway at the far end, glowing with the bass.
        far = pts[n]
        polygon = pygame.draw.polygon
        door = np.array(far[:4], dtype=float)
        centre = door.mean(axis=0)
        door_l = min(0.6, 0.22 + bass * 0.25 + flash * 0.15)
        for shrink, lift in ((1.0, 0.0), (0.72, 0.14), (0.44, 0.30)):
            polygon(surf, hsl(hues[n] + 0.5, 0.75, door_l + lift),
                    (centre + (door - centre) * shrink).tolist())

        for k in range(n - 1, -1, -1):
            a, b = pts[k], pts[k + 1]       # near and far edge of the segment
            if z[k + 1] <= self.Z_NEAR:
                continue
            polygon(surf, ceil_c[k],  (a[0], a[1], b[1], b[0]))
            polygon(surf, floor_c[k], (a[3], a[2], b[2], b[3]))
            polygon(surf, wall_c[k],  (a[0], a[3], b[3], b[0]))
            polygon(surf, wall_c[k],  (a[1], a[2], b[2], b[1]))
            if lit[k]:
                polygon(surf, lamp_c[k], (a[4], a[5], b[5], b[4]))
                polygon(surf, bar_c[k], (a[10], a[11], b[11], b[10]))
                polygon(surf, bar_c[k], (a[12], a[13], b[13], b[12]))
            polygon(surf, lane_c[k], (a[6], a[7], b[7], b[6]))
            polygon(surf, lane_c[k], (a[8], a[9], b[9], b[8]))
            # The neon rib set into this segment's camera-side edge.
            if ribbed[k] and rib_z[k] > self.Z_NEAR:
                r = rib[k]
                colour = rib_c[k]
                for i, j in ((0, 1), (1, 2), (2, 3), (3, 0)):
                    polygon(surf, colour, (a[i], a[j], r[j], r[i]))
                # A hairline keeps distant gates visible once they get thin.
                if gate[k]:
                    pygame.draw.lines(surf, colour, True, r[:4], 1)
