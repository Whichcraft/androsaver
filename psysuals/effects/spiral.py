import math

import numpy as np
import pygame

import config
from .utils import hsl


from .base import Effect

class Spiral(Effect):
    """Neon helix vortex — 6 arms fly toward the viewer with audio-reactive radius
    breathing, two-pass neon glow on arm lines, and cross-ring connections between
    arms at regular depth intervals.  Beat spring explodes the whole structure
    outward and snaps the hue palette."""

    N_ARMS    = 6
    N_PTS     = 80
    Z_FAR     = 10.0
    Z_NEAR    = 0.12
    RADIUS    = 1.0
    SPIN      = 1.6
    N_SYM     = 3
    RING_STEP = 8   # draw a cross-ring every N depth points

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.hue   = 0.0
        self.time  = 0.0
        self.scale = 1.0
        self.svel  = 0.0
        spacing    = (self.Z_FAR - self.Z_NEAR) / self.N_PTS
        self.pts   = [
            {"arm": arm, "z": self.Z_NEAR + j * spacing,
             "pt":  self.Z_NEAR + j * spacing}
            for arm in range(self.N_ARMS)
            for j   in range(self.N_PTS)
        ]

    def _path(self, t, mid=0.0):
        scale = 1.0 + mid * 0.50
        return (math.sin(t * 0.18) * 0.25 * scale, math.cos(t * 0.13) * 0.20 * scale)

    def _proj(self, wx, wy, wz, W, H):
        fov = min(W, H) * 0.72
        z   = max(wz, 0.01)
        return (int(wx * fov / z + W // 2),
                int(wy * fov / z + H // 2),
                fov / z)

    def draw(self, surf, waveform, fft, beat, tick):
        self.hue  += 0.007 + beat * 0.04
        W, H = surf.get_size()
        bass       = beat
        mid        = config.MID_ENERGY
        high       = config.TREBLE_ENERGY

        dt         = 0.038 + bass * 0.10 + mid * 0.08 + high * 0.04
        self.time += dt

        self.svel += bass * 0.48
        self.svel += (1.0 - self.scale) * 0.25
        self.svel *= 0.81
        self.scale = max(0.4, self.scale + self.svel)

        for p in self.pts:
            p["z"] -= dt
            if p["z"] < self.Z_NEAR:
                p["z"] += self.Z_FAR
                p["pt"] = self.time + p["z"]

        by_arm = [[] for _ in range(self.N_ARMS)]
        for p in self.pts:
            by_arm[p["arm"]].append(p)

        cx0, cy0 = W // 2, H // 2

        # Treble dynamically twists the spiral arms
        spin_val = self.SPIN * (1.0 + high * 0.40)

        arm_segs = []
        for arm_idx, arm_pts in enumerate(by_arm):
            arm_pts.sort(key=lambda p: -p["z"])
            segs = []
            for p in arm_pts:
                near_t = max(0.0, 1.0 - p["z"] / self.Z_FAR)
                # Helix radius reacts to mids and treble
                r_mod  = mid * 0.70 + high * 0.40
                angle  = p["pt"] * spin_val + arm_idx / self.N_ARMS * math.tau
                pcx, pcy = self._path(p["pt"], mid=mid)
                r      = (self.RADIUS + r_mod) * self.scale
                wx     = pcx + r * math.cos(angle)
                wy     = pcy + r * math.sin(angle)
                sx, sy, sc = self._proj(wx, wy, p["z"], W, H)
                h      = (self.hue + arm_idx / self.N_ARMS * 0.5 + near_t * 1.3) % 1.0
                bright = near_t ** 1.15
                segs.append((sx, sy, h, bright, sc, near_t))
            arm_segs.append(segs)

        for sym in range(self.N_SYM):
            ang    = sym / self.N_SYM * math.tau
            ca, sa = math.cos(ang), math.sin(ang)

            def rot(sx, sy, _ca=ca, _sa=sa):
                dx, dy = sx - cx0, sy - cy0
                return (int(cx0 + dx * _ca - dy * _sa),
                        int(cy0 + dx * _sa + dy * _ca))

            for segs in arm_segs:
                for sx, sy, h, bright, sc, near_t in segs:
                    rx, ry  = rot(sx, sy)
                    r_dot   = max(1, min(int(sc * (0.028 + high * 0.015)), 12))
                    c_halo  = hsl(h, l=bright * 0.20 + mid * 0.08)
                    c_core  = hsl(h, l=min(bright * 0.90 + 0.08 + high * 0.05, 0.98))
                    pygame.draw.circle(surf, c_halo, (rx, ry), r_dot * 3 + 1)
                    pygame.draw.circle(surf, c_core, (rx, ry), r_dot)
                    if near_t > 0.80 and (bass > 0.35 or high > 0.45):
                        fr = max(3, int(r_dot * (2.2 + bass * 0.9 + high * 1.2)))
                        pygame.draw.circle(surf, hsl(h, l=0.96), (rx, ry), fr)

            n = len(arm_segs[0]) if arm_segs else 0
            for j in range(0, n, self.RING_STEP):
                for a_segs in arm_segs:
                    if j < len(a_segs):
                        sx, sy, h, bright, sc, near_t = a_segs[j]
                        rp      = rot(sx, sy)
                        r_dot   = max(1, min(int(sc * (0.022 + high * 0.012)), 10))
                        c_halo  = hsl(h, l=bright * 0.35 + mid * 0.10)
                        c_core  = hsl(h, l=min(bright * 0.85 + 0.10 + high * 0.05, 0.95))
                        pygame.draw.circle(surf, c_halo, rp, r_dot * 3)
                        pygame.draw.circle(surf, c_core, rp, r_dot + 1)
