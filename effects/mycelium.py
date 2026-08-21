"""Mycelium — psychedelic hyphae colonies with swirling spores."""
import math

import numpy as np
import pygame

import config
from .base import Effect
from .utils import hsl, _hsl_batch

_MAX_SEGS = 900
_MAX_TIPS = 240
_CORE_COUNT = 5


class Mycelium(Effect):
    TRAIL_ALPHA = 8

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
        self._W, self._H = config.WIDTH, config.HEIGHT
        self.max_segs = _MAX_SEGS
        self.max_tips = _MAX_TIPS
        if getattr(config, "LOW_SPEC", False):
            self.max_segs //= 2
            self.max_tips //= 2
            
        self._build_cores()
        self._seed_tips(14 if getattr(config, "LOW_SPEC", False) else 28)

    def _build_cores(self):
        W, H = self._W, self._H
        cx, cy = W / 2.0, H / 2.0
        rad = min(W, H) * 0.22
        self._cores = []
        base = np.linspace(0.0, math.tau, _CORE_COUNT, endpoint=False, dtype=np.float32)
        angs = base + self._rng.uniform(-0.22, 0.22, _CORE_COUNT)
        dists = rad * self._rng.uniform(0.55, 1.15, _CORE_COUNT)
        for i, (ang, dist) in enumerate(zip(angs, dists)):
            self._cores.append((
                cx + math.cos(ang) * dist,
                cy + math.sin(ang) * dist,
                i / _CORE_COUNT * 0.45,
            ))

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
        branch_p = 0.07 + mid * 0.10 + high * 0.08
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
                )
                ta2 = ta + (swirl + field - ta) * 0.22 + float(gauss[idx])
                length = max(2.5, speed * (1.0 - depth * 0.030) * float(len_mul[idx]))
                ex = (tx + math.cos(ta2) * length) % W
                ey = (ty + math.sin(ta2) * length) % H
                
                # Tip grows, occasionally releases a spore
                if self._rng.random() < 0.12 and len(self._spores) < (150 if getattr(config, "LOW_SPEC", False) else 300):
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
        max_spores_limit = 150 if getattr(config, "LOW_SPEC", False) else 300
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
        self._spores = new_spores[:max_spores_limit]

        # Draw Cores
        for i, (cx, cy, h_off) in enumerate(self._cores):
            phase = self._phase * (0.7 + i * 0.08)
            wobble = 10.0 + math.sin(phase) * 8.0
            hue = (self._hue + h_off + math.sin(phase * 0.6) * 0.05) % 1.0
            glow = 0.06 + self._pulse * 0.12 + bass * 0.05
            pygame.draw.circle(surf, hsl(hue, l=glow * 0.35), (int(cx), int(cy)), int(22 + wobble))
            pygame.draw.circle(surf, hsl(hue, l=glow), (int(cx), int(cy)), int(7 + bass * 8))

            # Rotating ring of satellite nodes around each core
            num_dots = 6
            r_ring = 15.0 + wobble * 0.5 + bass * 12.0
            for d in range(num_dots):
                ang = self._phase * 0.8 + d * (math.tau / num_dots)
                dx = cx + math.cos(ang) * r_ring
                dy = cy + math.sin(ang) * r_ring
                pygame.draw.circle(surf, hsl(hue, l=min(0.9, glow * 1.8)), (int(dx), int(dy)), int(3 + bass * 2))

        # Draw Hyphae Segments
        for x1, y1, x2, y2, age, max_age, depth, h_off in self._segs:
            fade = max(0.0, 1.0 - age / max_age)
            hue = (self._hue + h_off + depth * 0.015) % 1.0
            bright = (0.22 + bass * 0.10 + high * 0.08 + self._pulse * 0.14) * fade
            glow_w = max(1, 5 - depth // 4)
            core_w = max(1, 3 - depth // 6)
            p1 = (int(x1), int(y1))
            p2 = (int(x2), int(y2))
            
            # Bioluminescent glow style drawing:
            # Broad soft color glow + bright thin core line (batched)
            h_arr = np.full(2, hue)
            s_arr = np.array([0.9, 0.4])
            l_arr = np.array([bright * 0.3, min(0.95, bright * 1.5)])
            cols  = _hsl_batch(h_arr, s_arr, l_arr)
            pygame.draw.line(surf, tuple(cols[0].astype(int)), p1, p2, glow_w + 3)
            pygame.draw.line(surf, tuple(cols[1].astype(int)), p1, p2, core_w)
            
            if depth > 5 and age < max_age * 0.25 and (depth + age) % 5 == 0:
                pygame.draw.circle(surf, hsl(hue, l=bright * 0.85), p2, max(1, 3 - depth // 8))

        # Draw Spores
        for sx, sy, svx, svy, shue, slife, smax_life, ssize in self._spores:
            fade = slife / smax_life
            hue = (shue + self._hue) % 1.0
            val = min(0.95, 0.3 + high * 0.5) * fade
            r = max(1.0, ssize * fade * (1.0 + high * 0.8))
            pygame.draw.circle(surf, hsl(hue, l=val), (int(sx), int(sy)), int(r))
