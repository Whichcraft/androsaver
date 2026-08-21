import math

import numpy as np
import pygame

import config
from .utils import hsl


from .base import Effect

class Tunnel(Effect):
    """First-person ride through a curving tube.

    Each ring has a fixed position on the tube path and a z-depth that
    decreases every frame so rings physically fly toward the camera.
    When a ring passes through (z < Z_NEAR) it is recycled to the far end.
    """

    N_RINGS = 30
    N_SIDES = 20
    TUBE_R  = 2.8
    Z_FAR   = 10.0
    Z_NEAR  = 0.06
    MAX_TRIS = 30

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self._rng = np.random.default_rng(config.RNG_SEED)
        self.hue  = 0.0
        self.time = 0.0
        spacing = (self.Z_FAR - self.Z_NEAR) / self.N_RINGS
        self.rings = [
            {"z": self.Z_NEAR + i * spacing,
             "pt": self.Z_NEAR + i * spacing}
            for i in range(self.N_RINGS)
        ]
        self._tri_count = 0
        self._tz    = np.empty(self.MAX_TRIS, dtype=np.float32)
        self._tpt   = np.empty(self.MAX_TRIS, dtype=np.float32)
        self._trot  = np.empty(self.MAX_TRIS, dtype=np.float32)
        self._trvel = np.empty(self.MAX_TRIS, dtype=np.float32)
        self._tsize = np.empty(self.MAX_TRIS, dtype=np.float32)
        self._thue  = np.empty(self.MAX_TRIS, dtype=np.float32)

    def _path(self, t, treble=0.0):
        # Treble increases path wobble amplitude
        scale = 1.0 + treble * 0.45
        return (math.sin(t * 0.21) * 0.8 * scale,
                math.cos(t * 0.16) * 0.6 * scale)

    def _proj(self, wx, wy, wz, W, H):
        fov = min(W, H) * 0.75
        z   = max(wz, 0.01)
        return (int(wx * fov / z + W / 2),
                int(wy * fov / z + H / 2),
                fov / z)

    def draw(self, surf, waveform, fft, beat, tick):
        self.hue  += 0.006
        W, H = surf.get_size()
        bass       = beat
        mid        = config.MID_ENERGY
        high       = config.TREBLE_ENERGY
        motion     = self._display_motion_scale()
        bass_m     = bass * motion
        mid_m      = mid * motion
        high_m     = high * motion

        dt         = 0.018 + bass_m * 0.09 + mid_m * 0.04 + high_m * 0.03
        self.time += dt

        # Spawn only in the far third of the tube and cap the live count so the
        # mid-range doesn't fill up with spinning triangles.
        spawn_n = int(bass_m * 1.2 + (mid_m * 1.5 if mid_m > 0.5 else 0))
        for _ in range(min(spawn_n, self.MAX_TRIS - self._tri_count)):
            i = self._tri_count
            self._tz[i]    = float(self._rng.uniform(self.Z_FAR * 0.80, self.Z_FAR * 0.98))
            self._tpt[i]   = self.time + self._tz[i]
            self._trot[i]  = float(self._rng.uniform(0, math.tau))
            self._trvel[i] = float(self._rng.choice([-1, 1]) * self._rng.uniform(0.04, 0.12) * (1.0 + mid_m * 1.5))
            self._tsize[i] = float(self._rng.uniform(0.45, 1.1) * (1.0 + bass_m * 1.5 + high_m * 0.5))
            self._thue[i]  = float((self.hue + self._rng.uniform(0, 0.5)) % 1.0)
            self._tri_count += 1

        for r in self.rings:
            r["z"] -= dt
            if r["z"] < self.Z_NEAR:
                r["z"]  += self.Z_FAR
                r["pt"]  = self.time + r["z"]

        ordered = sorted(self.rings, key=lambda r: -r["z"])

        for i in range(len(ordered) - 1):
            r1 = ordered[i]
            r2 = ordered[i + 1]

            cx1, cy1 = self._path(r1["pt"], treble=high_m)
            cx2, cy2 = self._path(r2["pt"], treble=high_m)

            sx1, sy1, sc1 = self._proj(cx1, cy1, r1["z"], W, H)
            sx2, sy2, sc2 = self._proj(cx2, cy2, r2["z"], W, H)

            sr1 = max(1, int(self.TUBE_R * sc1))
            sr2 = max(1, int(self.TUBE_R * sc2))

            near_t = max(0.0, 1.0 - r1["z"] / self.Z_FAR)
            h      = (self.hue + near_t) % 1.0
            bright = 0.06 + near_t * 0.60 + mid_m * 0.15 * near_t + bass_m * near_t * 0.40
            lw     = max(1, int(1 + bass_m * 2.5 * near_t + high_m * 1.5 * near_t))

            pygame.draw.circle(surf, hsl(h, l=bright * 0.35), (sx1, sy1), sr1 + 4, lw + 3)
            pygame.draw.circle(surf, hsl(h, l=bright),        (sx1, sy1), sr1,     lw)

            for side in range(self.N_SIDES):
                angle = side / self.N_SIDES * math.tau
                p1 = (sx1 + int(math.cos(angle) * sr1),
                      sy1 + int(math.sin(angle) * sr1))
                p2 = (sx2 + int(math.cos(angle) * sr2),
                      sy2 + int(math.sin(angle) * sr2))
                hs = (h + side / self.N_SIDES * 0.25 + high * 0.10) % 1.0
                pygame.draw.line(surf, hsl(hs, l=bright * 0.55), p1, p2, 1)

            n_star  = 3 + (i % 4)
            s_dir   = 1 if i % 2 == 0 else -1
            s_rot   = self.time * 0.45 * s_dir + i * 0.52 + mid_m * 0.15
            s_r     = max(2, int(sr1 * (0.24 + high_m * 0.12)))
            s_h     = (h + 0.5) % 1.0
            s_l     = min(bright * 1.1 + mid_m * 0.25 + high_m * 0.15, 0.95)
            s_pts   = [
                (sx1 + int(math.cos(v / n_star * math.tau + s_rot) * s_r),
                 sy1 + int(math.sin(v / n_star * math.tau + s_rot) * s_r))
                for v in range(n_star)
            ]
            pygame.draw.polygon(surf, hsl(s_h, l=s_l), s_pts, max(1, lw))

        # Update + compact triangles in-place (struct-of-arrays)
        new_n = 0
        for i in range(self._tri_count):
            self._tz[i]   -= dt
            self._trot[i] += self._trvel[i] * (1.0 + mid_m * 1.5)
            if self._tz[i] < self.Z_NEAR:
                continue
            tcx, tcy   = self._path(self._tpt[i], treble=high_m)
            sx, sy, sc = self._proj(tcx, tcy, self._tz[i], W, H)
            near_t     = max(0.0, 1.0 - self._tz[i] / self.Z_FAR)
            tr         = max(3, int(self._tsize[i] * sc))
            h          = (self._thue[i] + near_t * 0.4) % 1.0
            bright     = 0.35 + near_t * 0.60
            lw         = max(1, int(1 + near_t * 3 + high_m * 1.5))
            pts = [
                (sx + int(math.cos(self._trot[i] + v * math.tau / 3) * tr),
                 sy + int(math.sin(self._trot[i] + v * math.tau / 3) * tr))
                for v in range(3)
            ]
            pygame.draw.polygon(surf, hsl(h, l=bright * 0.30), pts, lw + 4)
            pygame.draw.polygon(surf, hsl(h, l=bright),         pts, lw)
            if new_n != i:
                self._tz[new_n]    = self._tz[i]
                self._tpt[new_n]   = self._tpt[i]
                self._trot[new_n]  = self._trot[i]
                self._trvel[new_n] = self._trvel[i]
                self._tsize[new_n] = self._tsize[i]
                self._thue[new_n]  = self._thue[i]
            new_n += 1
        self._tri_count = new_n
