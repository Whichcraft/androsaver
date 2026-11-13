"""Horizon — a landscape built from the music.

Rows of solid mountain ridges scroll toward the camera. Every row is a
snapshot of the spectrum taken when the row appeared on the horizon, so the
music's recent past rolls by as terrain. The rows are painted far to near and
each ridge hides the ones behind it. A flat valley with lane lines runs down
the middle toward a sun on the horizon, under a sky of stars.

  Spectrum → shape of each new ridge: bass beside the valley, treble outside
  Bass     → ride speed, near ridges swell, sun and sky glow
  Mid      → ride speed, hue drift
  Treble   → stars twinkle, far ridge lines glitter
  Beat     → the sun flares and every ridge line flashes
"""
import math

import numpy as np
import pygame

from .base import Effect
from .utils import _hsl_batch, hsl, track_levels


class Horizon(Effect):
    TIME_BASED  = True
    TRAIL_ALPHA = 255   # every frame is fully repainted; no trails

    N_ROWS   = 26
    N_SIDE   = 22       # mountain columns on each side of the valley
    Z_NEAR   = 0.9
    Z_FAR    = 22.0
    CAMERA_H = 1.0
    VALLEY   = 1.3      # half width of the flat valley, in world units
    HORIZON  = 0.50     # horizon height as a fraction of the frame
    N_STARS  = 110
    _SKY_BANDS = 20

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.hue  = 0.62
        self.time = 0.0          # rows travelled
        self._spectrum = np.zeros(self.N_SIDE)
        self._peak = 1e-3
        # Mountain coordinate t: 0 at the valley edge, 1 at the outer edge.
        t = np.linspace(0.0, 1.0, self.N_SIDE + 1)
        self._t = np.concatenate([t[::-1], t])
        self._side = np.concatenate([-np.ones(self.N_SIDE + 1), np.ones(self.N_SIDE + 1)])
        rise = np.clip(self._t / 0.30, 0.0, 1.0)
        self._rise = rise * rise * (3.0 - 2.0 * rise)
        # Ridge heights by row identity; a row keeps its shape while it travels.
        self._rows = np.zeros((self.N_ROWS + 2, len(self._t)))
        self._newest = -1
        self._fill_rows(self.N_ROWS + 1)
        self._stars = self.rng.random((self.N_STARS, 3))
        self._clock = 0.0       # elapsed time in 60 Hz frames, for twinkle

    # ------------------------------------------------------------------

    def _ridge(self):
        """Heights of a new ridge from the current spectrum plus resting hills."""
        level = np.concatenate([self._spectrum[::-1], [0.0, 0.0], self._spectrum])
        hills = self.rng.random(len(self._t))
        hills = np.convolve(hills, (0.25, 0.5, 0.25), mode="same")
        shape = 0.22 + 0.30 * hills + (0.45 * float(self._spectrum.mean()) + 0.75 * level) * (0.6 + 0.8 * hills)
        return self._rise * np.convolve(shape, (0.25, 0.5, 0.25), mode="same")

    def _fill_rows(self, newest):
        """Create every row up to identity ``newest``; bounded by the buffer."""
        first = max(self._newest + 1, newest - len(self._rows) + 1)
        for row_id in range(first, newest + 1):
            self._rows[row_id % len(self._rows)] = self._ridge()
        self._newest = max(self._newest, newest)

    def _update_spectrum(self, fft):
        self._spectrum, self._peak = track_levels(self._spectrum, self._peak, fft, self._smooth)

    def _sky(self, surf, W, H, horizon_y, bass, flash, high):
        """Sky gradient, stars and the sun; returns the sky colour at the horizon."""
        bands = self._SKY_BANDS
        depth = (np.arange(bands) + 0.5) / bands          # 0 top … 1 horizon
        colours = _hsl_batch(self.hue + 0.10 + 0.22 * depth, 0.85,
                             0.015 + (0.16 + 0.10 * bass + 0.06 * flash) * depth ** 2.2).tolist()
        edges = np.rint(np.linspace(0, horizon_y, bands + 1)).astype(int).tolist()
        for i in range(bands):
            surf.fill(colours[i], (0, edges[i], W, edges[i + 1] - edges[i]))
        surf.fill(colours[-1], (0, horizon_y, W, H - horizon_y))

        size = max(1, round(min(W, H) / 540))
        twinkle = 0.5 + 0.5 * np.sin(self._clock * 0.07 + self._stars[:, 2] * math.tau * 3.0)
        light = np.clip((0.25 + 0.75 * twinkle * (0.4 + high)) * (1.0 - self._stars[:, 1]) ** 0.6, 0, 1)
        xs = (self._stars[:, 0] * W).astype(int).tolist()
        ys = (self._stars[:, 1] * horizon_y * 0.92).astype(int).tolist()
        grey = (light * 235).astype(int).tolist()
        for x, y, g in zip(xs, ys, grey):
            surf.fill((g, g, min(255, g + 20)), (x, y, size, size))

        radius = min(W, H) * 0.15 * (1.0 + 0.10 * flash + 0.05 * bass)
        cx, cy = W // 2, int(horizon_y - radius * 0.25)
        # A soft halo: many faint discs, each a little smaller and brighter.
        halo = 14
        for i in range(halo):
            frac = (i + 1) / halo
            lum = 0.03 + 0.17 * frac ** 2 * (1.0 + 0.6 * flash)
            pygame.draw.circle(surf, hsl(self.hue + 0.34 + 0.06 * frac, 0.9, lum), (cx, cy),
                               int(radius * (2.3 - 1.25 * frac)))
        # The disc is shaded in horizontal slices, bright on top.
        slices = 14
        clip = surf.get_clip()
        top = cy - radius
        for i in range(slices):
            y0, y1 = int(top + 2 * radius * i / slices), int(top + 2 * radius * (i + 1) / slices)
            surf.set_clip(clip.clip(pygame.Rect(0, y0, W, max(1, y1 - y0))))
            frac = i / (slices - 1)
            pygame.draw.circle(surf, hsl(self.hue + 0.52 - 0.14 * frac, 1.0, min(0.9, 0.72 - 0.22 * frac + 0.12 * flash)),
                               (cx, cy), int(radius))
        surf.set_clip(clip)
        return colours[-1]

    # ------------------------------------------------------------------

    def _draw(self, surf, waveform, fft, beat, tick):
        W, H = surf.get_size()
        step = self._frame_step()
        motion, light = self._audio_response(beat)
        bass, mid, high = motion
        motion_scale, accent = self._profile_response()
        flash = float(light[0]) * accent

        self._clock += step
        self.hue = (self.hue + (0.0006 + mid * 0.0010) * step) % 1.0
        self.time += (0.028 + bass * 0.035 + mid * 0.02) * step * motion_scale
        self._update_spectrum(fft)

        horizon_y = int(H * self.HORIZON)
        sky = np.array(self._sky(surf, W, H, horizon_y, bass, flash, float(light[2])), dtype=float)

        # Row k keeps identity first + k and slides from the horizon to the
        # camera. Depth is exponential in the row coordinate, which spaces the
        # rows evenly on screen and lets them speed up as they approach.
        n = self.N_ROWS
        first = int(math.floor(self.time))
        self._fill_rows(first + n)
        s = (np.arange(n + 1) - (self.time - first)) / n
        z = self.Z_NEAR * (self.Z_FAR / self.Z_NEAR) ** np.clip(s, 0.0, 1.0)
        near = 1.0 - np.clip(s, 0.0, 1.0)
        ids = first + np.arange(n + 1)
        heights = self._rows[ids % len(self._rows)]

        focal = min(W, H) * 0.9
        # The outer edge always reaches past the sides of the frame, so wide
        # screens show terrain to the edges instead of bare sky.
        edge = np.maximum(self.VALLEY + 1.5 + 1.15 * z, W * 0.55 * z / focal)
        x = self._side[None, :] * (self.VALLEY + self._t[None, :] * (edge[:, None] - self.VALLEY))
        swell = (0.9 + 0.22 * z) * (1.0 + 0.35 * bass * near)
        y = heights * swell[:, None]
        sx = W / 2.0 + x * focal / z[:, None]
        sy = horizon_y + (self.CAMERA_H - y) * focal / z[:, None]
        ground = horizon_y + self.CAMERA_H * focal / z
        lanes = W / 2.0 + np.array([-1.0, -0.5, 0.0, 0.5, 1.0])[None, :] * self.VALLEY * focal / z[:, None]

        # A new row fades in out of the sky on the horizon instead of popping
        # up: both its fill and its ridge line start in the sky colour.
        arrive = np.clip((1.0 - s) * n / 1.5, 0.0, 1.0)
        fill = _hsl_batch(self.hue + 0.08 + 0.10 * near, 0.75, 0.025 + 0.09 * near ** 1.5)
        fill = np.rint(sky + (fill - sky) * arrive[:, None]).astype(int).tolist()
        glitter = 0.5 + 0.5 * np.sin(self._clock * 0.31 + ids * 1.7)
        line_l = (0.14 + 0.46 * near ** 0.8 + 0.20 * flash + 0.25 * high * glitter * (1.0 - near)) * arrive
        line = _hsl_batch(self.hue + 0.30 - 0.32 * near, 1.0, np.minimum(line_l, 0.85)).tolist()
        lane = _hsl_batch(self.hue + 0.50, 0.9, (0.10 + 0.30 * near) * (1.0 + 0.4 * flash)).tolist()
        widths = np.maximum(1, np.rint(min(W, H) / 540.0 * (1.0 + 2.2 * near ** 2))).astype(int).tolist()

        sx, sy = np.rint(sx).astype(int), np.rint(sy).astype(int)
        lanes, ground = np.rint(lanes).astype(int).tolist(), np.rint(ground).astype(int).tolist()
        bottom = H + 4
        for k in range(n, -1, -1):
            if s[k] < 0.0:
                continue
            ridge = list(zip(sx[k].tolist(), sy[k].tolist()))
            pygame.draw.polygon(surf, fill[k], ridge + [(ridge[-1][0], bottom), (ridge[0][0], bottom)])
            pygame.draw.lines(surf, line[k], False, ridge, widths[k])
            # Lane lines run on the valley floor from this row to the next.
            if k > 0:
                near_y = min(ground[k - 1], bottom)
                for a, b in zip(lanes[k], lanes[k - 1]):
                    pygame.draw.line(surf, lane[k], (a, ground[k]), (b, near_y), widths[k])
