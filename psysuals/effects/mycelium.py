"""Mycelium — psychedelic hyphae colonies with swirling spores."""
import math
from functools import lru_cache

import numpy as np
import pygame
import pygame.surfarray as surfarray

import config
from .base import Effect
from .utils import hsl

_MAX_SEGS = 600
_MAX_TIPS = 180
_MAX_SPORES = 200
_CORE_COUNT = 5


@lru_cache(maxsize=512)
def _segment_color(hue_step: int, light_step: int, saturation_step: int):
    """Reuse quantized hypha colors instead of allocating tiny NumPy arrays per line."""
    return hsl(hue_step / 128.0, saturation_step / 16.0, light_step / 32.0)


class Mycelium(Effect):
    TRAIL_ALPHA = 38

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self._rng = np.random.default_rng(config.RNG_SEED)
        self._hue = self._rng.random()
        self._phase = 0.0
        self._pulse = 0.0
        self._segs: list = []
        self._tips: list = []
        self._spores: list = []
        self._cores: list[tuple[float, float, float]] = []
        self._core_anchors: list[tuple[float, float]] = []
        self._W, self._H = config.WIDTH, config.HEIGHT
        self._halo_w = self._halo_h = 0
        self._halo = None
        self._halo_surface = None
        self._halo_scaled = None
        self._motion_field = None
        self.max_segs = _MAX_SEGS
        self.max_tips = _MAX_TIPS
        self.max_spores = _MAX_SPORES
        if getattr(config, "LOW_SPEC", False):
            self.max_segs //= 2
            self.max_tips //= 2
            self.max_spores //= 2
            
        self._build_cores()
        self._seed_tips(14 if getattr(config, "LOW_SPEC", False) else 28)

    def _reset_halo(self, W, H):
        self._halo_w = max(32, W // 4)
        self._halo_h = max(24, H // 4)
        self._halo = np.zeros((self._halo_h, self._halo_w), dtype=np.float32)
        self._halo_surface = pygame.Surface((self._halo_w, self._halo_h))
        self._halo_scaled = pygame.Surface((W, H))

    def _draw_halo(self, surf, bass, mid, high):
        W, H = surf.get_size()
        expected = (max(32, W // 4), max(24, H // 4))
        if self._halo is None or (self._halo_w, self._halo_h) != expected:
            self._reset_halo(W, H)
        self._halo *= 0.965
        for cx, cy, _ in self._cores:
            ix = int(np.clip(cx / max(1, W) * self._halo_w, 0, self._halo_w - 1))
            iy = int(np.clip(cy / max(1, H) * self._halo_h, 0, self._halo_h - 1))
            self._halo[iy, ix] = min(1.0, self._halo[iy, ix] + 0.16 + bass * 0.16 + self._pulse * 0.22)
        for tx, ty, *_ in self._tips[:80]:
            ix = int(np.clip(tx / max(1, W) * self._halo_w, 0, self._halo_w - 1))
            iy = int(np.clip(ty / max(1, H) * self._halo_h, 0, self._halo_h - 1))
            self._halo[iy, ix] = min(1.0, self._halo[iy, ix] + 0.025 + high * 0.01)
        t = self._halo
        self._halo[:] = np.clip(
            (np.roll(t, 1, 0) + np.roll(t, -1, 0) + np.roll(t, 1, 1) + np.roll(t, -1, 1) + t * 4.0) / 8.0,
            0.0, 1.0)
        hue = (self._hue + self._halo * 0.45 + mid * 0.02) % 1.0
        glow = np.clip(self._halo * 1.8, 0.0, 1.0)
        red = np.clip((np.sin(hue * math.tau) * 127 + 128) * glow, 0, 255)
        green = np.clip((np.sin((hue + 0.333) * math.tau) * 127 + 128) * glow, 0, 255)
        blue = np.clip((np.sin((hue + 0.667) * math.tau) * 127 + 128) * glow, 0, 255)
        pixels = surfarray.pixels3d(self._halo_surface)
        try:
            pixels[:] = np.stack((red, green, blue), axis=-1).astype(np.uint8).transpose(1, 0, 2)
        finally:
            del pixels
        pygame.transform.smoothscale(self._halo_surface, surf.get_size(), self._halo_scaled)
        surf.blit(self._halo_scaled, (0, 0), special_flags=pygame.BLEND_RGB_ADD)

    def _build_cores(self):
        W, H = self._W, self._H
        cx, cy = W / 2.0, H / 2.0
        rad = min(W, H) * 0.22
        self._cores = []
        self._core_anchors = []
        base = np.linspace(0.0, math.tau, _CORE_COUNT, endpoint=False, dtype=np.float32)
        angs = base + self._rng.uniform(-0.22, 0.22, _CORE_COUNT)
        dists = rad * self._rng.uniform(0.55, 1.15, _CORE_COUNT)
        for i, (ang, dist) in enumerate(zip(angs, dists)):
            core_x = cx + math.cos(ang) * dist
            core_y = cy + math.sin(ang) * dist
            self._cores.append((
                core_x,
                core_y,
                i / _CORE_COUNT * 0.45,
            ))
            self._core_anchors.append((core_x, core_y))

    def _seed_tips(self, count, core_idx=None):
        if not self._cores:
            self._build_cores()
        n = min(count, self.max_tips - len(self._tips))
        if n <= 0:
            return
        if core_idx is None:
            idxs = self._rng.integers(0, len(self._cores), size=n)
        else:
            idxs = np.full(n, core_idx, dtype=np.int32)
        angs = self._rng.uniform(0.0, math.tau, n)
        radius_low = 2.0
        radius_high = max(radius_low + 1.0, min(self._W, self._H) * 0.035)
        radii = self._rng.uniform(radius_low, radius_high, n)
        hoffs = self._rng.uniform(0.0, 0.15, n)
        for idx, ang, radius, hoff in zip(idxs, angs, radii, hoffs):
            cx, cy, h_off = self._cores[int(idx)]
            self._tips.append([
                cx + math.cos(ang) * radius,
                cy + math.sin(ang) * radius,
                ang,
                0,
                (h_off + float(hoff)) % 1.0,
                int(idx),
            ])

    def draw(self, surf, waveform, fft, beat, tick):
        W, H = surf.get_size()
        if W != self._W or H != self._H:
            self._W, self._H = W, H
            self._cores = []
        bass = beat
        mid = config.MID_ENERGY
        high = config.TREBLE_ENERGY

        if not self._cores:
            self._build_cores()

        self._hue = (self._hue + 0.003 + mid * 0.002) % 1.0
        self._phase += 0.010 + mid * 0.012 + high * 0.006
        self._pulse = max(0.0, self._pulse - 0.03)
        wander = min(W, H) * (0.035 + mid * 0.004)
        orbit = self._phase * 0.025
        breathe = 1.0 + math.sin(self._phase * 0.31) * (0.08 + bass * 0.025)
        self._cores = [
            (
                W * 0.5 + ((anchor_x - W * 0.5) * math.cos(orbit)
                           - (anchor_y - H * 0.5) * math.sin(orbit)) * breathe
                + math.sin(self._phase * (0.42 + i * 0.035) + i * 1.7) * wander,
                H * 0.5 + ((anchor_x - W * 0.5) * math.sin(orbit)
                           + (anchor_y - H * 0.5) * math.cos(orbit)) * breathe
                + math.cos(self._phase * (0.37 + i * 0.030) + i * 2.1) * wander,
                self._cores[i][2],
            )
            for i, (anchor_x, anchor_y) in enumerate(self._core_anchors)
        ]
        self._draw_halo(surf, bass, mid, high)

        # Beat spawns new tips
        if bass > 0.65 and len(self._tips) < self.max_tips:
            self._pulse = 1.0
            for _ in range(1 + int(bass * 2.5)):
                self._seed_tips(int(5 + bass * 6), int(self._rng.integers(0, len(self._cores))))

        # Beat spawns spores from cores
        if bass > 0.65:
            for cx, cy, h_off in self._cores:
                for _ in range(int(self._rng.integers(2, 5))):
                    ang = self._rng.uniform(0.0, math.tau)
                    spd = self._rng.uniform(1.0, 3.5)
                    self._spores.append([
                        cx, cy,
                        math.cos(ang) * spd, math.sin(ang) * spd,
                        h_off,
                        int(self._rng.integers(120, 240)), # life
                        240, # max_life
                        self._rng.uniform(2.0, 4.5) # size
                    ])

        speed = 6.5 + bass * 18.0 + mid * 5.0
        spread = 0.20 + mid * 0.30 + high * 0.10
        branch_p = 0.12 + mid * 0.12 + high * 0.10
        seg_life = 90 + int(bass * 55 + high * 20)

        next_tips: list = []
        tips = self._tips
        tip_count = len(tips)
        if tip_count:
            gauss = self._rng.normal(0.0, spread * 0.16, tip_count)
            len_mul = self._rng.uniform(0.72, 1.22, tip_count)
            branch_roll = self._rng.random(tip_count)
            branch_sign = self._rng.integers(0, 2, tip_count) * 2 - 1
            branch_angle = self._rng.uniform(0.35, 0.95 + spread, tip_count)
            branch_core = self._rng.integers(0, len(self._cores), tip_count)
            seg_jitter = self._rng.integers(-16, 56, tip_count)
            for idx, (tx, ty, ta, depth, h_off, core_idx) in enumerate(tips):
                if depth >= 18:
                    continue
                cx, cy, _ = self._cores[core_idx]
                swirl = math.atan2(ty - cy, tx - cx) + math.pi * 0.5
                field = (
                    math.sin(tx * 0.013 + self._phase + core_idx * 0.7) * 0.55
                    + math.cos(ty * 0.011 - self._phase * 1.2 + depth * 0.25) * 0.35
                    + math.sin((tx + ty) * 0.006 - self._phase * 0.7) * 0.42
                    + math.cos(math.atan2(ty - H * 0.5, tx - W * 0.5) * 3.0
                               + self._phase * 0.45) * (0.18 + high * 0.06)
                )
                if self._motion_field is not None:
                    vx, vy = self._motion_field
                    if vx.ndim == 2 and vy.shape == vx.shape and vx.size:
                        fy = min(vx.shape[0] - 1, max(0, int(ty / max(1, H) * vx.shape[0])))
                        fx = min(vx.shape[1] - 1, max(0, int(tx / max(1, W) * vx.shape[1])))
                        field += float(np.clip(vx[fy, fx] - vy[fy, fx], -2.0, 2.0)) * 0.04
                ta2 = ta + (swirl + field - ta) * 0.22 + float(gauss[idx])
                length = max(2.5, speed * (1.0 - depth * 0.030) * float(len_mul[idx]))
                ex = (tx + math.cos(ta2) * length) % W
                ey = (ty + math.sin(ta2) * length) % H
                
                # Tip grows, occasionally releases a spore
                if self._rng.random() < 0.12 and len(self._spores) < self.max_spores:
                    self._spores.append([
                        ex, ey,
                        math.cos(ta2 + math.pi) * 0.5 + self._rng.uniform(-0.5, 0.5),
                        math.sin(ta2 + math.pi) * 0.5 + self._rng.uniform(-0.5, 0.5),
                        h_off,
                        int(self._rng.integers(80, 160)),
                        160,
                        self._rng.uniform(1.5, 3.5)
                    ])

                if len(self._segs) < self.max_segs:
                    self._segs.append([tx, ty, ex, ey, 0, seg_life + int(seg_jitter[idx]), depth, h_off])
                if len(next_tips) < self.max_tips:
                    next_tips.append([ex, ey, ta2, depth + 1, h_off, core_idx])
                if branch_roll[idx] < branch_p and depth < 13 and len(next_tips) < self.max_tips:
                    b_ang = ta2 + float(branch_sign[idx]) * float(branch_angle[idx])
                    b_core = core_idx if self._rng.random() > 0.18 else int(branch_core[idx])
                    next_tips.append([ex, ey, b_ang, depth + 1, (h_off + 0.10) % 1.0, b_core])

        self._tips = next_tips[:self.max_tips]

        if not self._tips:
            self._build_cores()
            self._seed_tips(14 if getattr(config, "LOW_SPEC", False) else 28)

        self._segs = [s for s in self._segs if s[4] < s[5]]
        for s in self._segs:
            s[4] += 1

        # Update Spores
        new_spores = []
        for sx, sy, svx, svy, shue, slife, smax_life, ssize in self._spores:
            # find nearest core
            nearest_core = None
            min_d = 99999.0
            for cx, cy, _ in self._cores:
                d = math.hypot(sx - cx, sy - cy)
                if d < min_d:
                    min_d = d
                    nearest_core = (cx, cy)
            
            if nearest_core:
                cx, cy = nearest_core
                dx, dy = sx - cx, sy - cy
                d = max(1.0, min_d)
                # Orbit force
                ox = -dy / d
                oy = dx / d
                # Pull/push force
                px = -dx / d
                py = -dy / d
                
                f_orbit = 0.4 + mid * 0.8
                f_pull = 0.05 + bass * 0.1
                if d > 200:
                    f_orbit *= 0.5
                    f_pull *= 2.0
                elif d < 50:
                    f_orbit *= 1.5
                    f_pull = -0.1
                    
                svx += ox * f_orbit + px * f_pull + self._rng.uniform(-0.15, 0.15)
                svy += oy * f_orbit + py * f_pull + self._rng.uniform(-0.15, 0.15)
                
            svx *= 0.94
            svy *= 0.94
            
            sx = (sx + svx) % W
            sy = (sy + svy) % H
            
            slife -= 1
            if slife > 0:
                new_spores.append([sx, sy, svx, svy, shue, slife, smax_life, ssize])
        self._spores = new_spores[:self.max_spores]

        # Draw Cores
        for i, (cx, cy, h_off) in enumerate(self._cores):
            phase = self._phase * (0.7 + i * 0.08)
            wobble = 10.0 + math.sin(phase) * 8.0
            hue = (self._hue + h_off + math.sin(phase * 0.6) * 0.05) % 1.0
            glow = min(0.92, 0.20 + self._pulse * 0.28 + bass * 0.16)
            pygame.draw.circle(surf, hsl(hue, l=glow * 0.30), (int(cx), int(cy)), int(30 + wobble))
            pygame.draw.circle(surf, hsl(hue, l=glow), (int(cx), int(cy)), int(8 + bass * 9))

            # Rotating ring of satellite nodes around each core
            num_dots = 9
            r_ring = 20.0 + wobble * 0.65 + bass * 16.0
            for d in range(num_dots):
                ang = self._phase * (0.8 + i * 0.035) + d * (math.tau / num_dots)
                dx = cx + math.cos(ang) * r_ring
                dy = cy + math.sin(ang) * r_ring
                dot_hue = (hue + d / num_dots * 0.24 + math.sin(self._phase + d) * 0.035) % 1.0
                pygame.draw.circle(surf, hsl(dot_hue, l=min(0.96, glow * 1.55)), (int(dx), int(dy)), int(3 + bass * 2))
            ring_r = int(r_ring + 9 + math.sin(self._phase * 0.6 + i) * 5)
            pygame.draw.circle(surf, hsl(hue + 0.5, l=0.13 + self._pulse * 0.20),
                               (int(cx), int(cy)), ring_r, 1)

        # Draw Hyphae Segments
        for x1, y1, x2, y2, age, max_age, depth, h_off in self._segs:
            fade = max(0.0, 1.0 - age / max_age)
            hue = (self._hue + h_off + depth * 0.015) % 1.0
            bright = (0.35 + bass * 0.14 + high * 0.12 + self._pulse * 0.20) * fade
            glow_w = max(1, 5 - depth // 4)
            core_w = max(1, 3 - depth // 6)
            p1 = (int(x1), int(y1))
            p2 = (int(x2), int(y2))
            
            # Bioluminescent glow style drawing:
            # Broad colored halo and a narrow bright hypha core.
            glow_color = _segment_color(int(hue * 128) % 128,
                                        int(min(1.0, bright * 0.40) * 32), 14)
            core_color = _segment_color(int(hue * 128) % 128,
                                        int(min(0.98, bright * 1.65) * 32), 7)
            pygame.draw.line(surf, glow_color, p1, p2, glow_w + 3)
            pygame.draw.line(surf, core_color, p1, p2, core_w)
            
            if depth > 5 and age < max_age * 0.25 and (depth + age) % 5 == 0:
                pygame.draw.circle(surf, hsl(hue, l=bright * 0.85), p2, max(1, 3 - depth // 8))

        # Draw Spores
        for sx, sy, svx, svy, shue, slife, smax_life, ssize in self._spores:
            fade = slife / smax_life
            hue = (shue + self._hue) % 1.0
            val = min(0.95, 0.3 + high * 0.5) * fade
            r = max(1.0, ssize * fade * (1.0 + high * 0.8))
            pygame.draw.circle(surf, hsl(hue, l=val), (int(sx), int(sy)), int(r))

    def set_motion_field(self, field) -> None:
        if not field or len(field) != 2:
            self._motion_field = None
            return
        vx, vy = field
        if isinstance(vx, np.ndarray) and isinstance(vy, np.ndarray) and vx.shape == vy.shape and vx.ndim == 2:
            self._motion_field = (vx, vy)
        else:
            self._motion_field = None
