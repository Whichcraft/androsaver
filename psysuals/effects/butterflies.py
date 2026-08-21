"""Butterflies — up to three pairs dancing to the music.

Each pair: a solo butterfly flutters in first; its partner joins after
10–30 s.  Once together they *chase each other* in a tightening mutual
orbit — both turning toward the other's position, creating a playful
love-pursuit spiral.  After a random lifetime the pair wanders off-screen
and vanishes.  New pairs can re-enter from the edges.
Wing flapping syncs when partners are close; sparkles on the beat.

Trail is managed on an internal surface (TRAIL_ALPHA = 0) so it is
preserved correctly on Android / OpenGL backends where the display
backbuffer is not guaranteed between frames.
"""
import math

import numpy as np
import pygame

import config
from .utils import hsl


from .base import Effect

# ── Wing geometry ─────────────────────────────────────────────────────────────

def _wing_poly(x, y, heading, side, upper, flap, scale):
    """Four-point polygon for one wing.
    side: +1 / -1  (which perpendicular direction)
    upper: +1 front/larger wing, -1 rear/smaller wing
    flap: 0..1 (0 = closed, 1 = fully spread)
    """
    ca, sa = math.cos(heading), math.sin(heading)
    off  = 5.0 * scale * upper
    ax   = x + ca * off
    ay   = y + sa * off
    ws   = scale * (22 if upper == 1 else 13)
    spread  = (0.22 + flap * 0.68) * math.pi / 2
    w_ang   = heading + side * spread + upper * 0.10
    tip_x   = ax + math.cos(w_ang) * ws
    tip_y   = ay + math.sin(w_ang) * ws
    fe_ang  = w_ang - side * math.pi * 0.28
    fe_x    = ax + math.cos(fe_ang) * ws * 0.58
    fe_y    = ay + math.sin(fe_ang) * ws * 0.58
    re_ang  = w_ang + side * math.pi * 0.32
    re_x    = ax + math.cos(re_ang) * ws * 0.52
    re_y    = ay + math.sin(re_ang) * ws * 0.52
    return [(int(ax), int(ay)), (int(fe_x), int(fe_y)),
            (int(tip_x), int(tip_y)), (int(re_x), int(re_y))]


# ── Single butterfly ──────────────────────────────────────────────────────────

class _Butterfly:
    def __init__(self, x, y, hue, rng, scale=4.0):
        self.x           = float(x)
        self.y           = float(y)
        self.heading     = float(rng.uniform(0, math.tau))
        self.wing_phase  = float(rng.uniform(0, math.tau))
        self._rng        = rng
        self.hue         = hue
        self.scale       = scale
        self._wander_des = self.heading
        self._wander_cd  = 0
        self._depart_ang = None

    def off_screen(self, W, H):
        m = 80 * self.scale
        return (self.x < -m or self.x > W + m or
                self.y < -m or self.y > H + m)

    def start_depart(self):
        if self._depart_ang is None:
            self._depart_ang = float(self._rng.uniform(0, math.tau))

    def update(self, bass, beat, t, W, H, chase_pos=None):
        """Update position and wing phase.

        chase_pos: if set, the butterfly steers toward this (x, y) target
                   instead of wandering.  Used for the mutual-chase love pairs.
        """
        self.wing_phase += 0.09 + bass * 0.16 + beat * 0.06

        if self._depart_ang is not None:
            self.heading += (self._depart_ang - self.heading + math.pi) % math.tau - math.pi
            spd = (2.5 + bass * 0.5) * self.scale
            self.x += math.cos(self.heading) * spd
            self.y += math.sin(self.heading) * spd
            return

        if chase_pos is not None:
            desired = math.atan2(chase_pos[1] - self.y, chase_pos[0] - self.x)
        else:
            self._wander_cd -= 1
            if self._wander_cd <= 0:
                self._wander_des = (self.heading
                                    + float(self._rng.uniform(-math.pi * 0.6, math.pi * 0.6)))
                self._wander_cd = int(self._rng.integers(60, 180))
            desired = self._wander_des

        # ── Boundary repulsion ────────────────────────────────────────────────
        m = min(int(50 * self.scale), W // 5, H // 5)
        rx, ry = 0.0, 0.0
        if m > 0:
            if self.x < m:
                rx = (m - self.x) / m
            elif self.x > W - m:
                rx = (W - m - self.x) / m
            if self.y < m:
                ry = (m - self.y) / m
            elif self.y > H - m:
                ry = (H - m - self.y) / m

        if rx or ry:
            push = self.scale * max(abs(rx), abs(ry)) * 2.5
            self.x += rx * push
            self.y += ry * push
            desired = math.atan2(ry, rx)
            diff = (desired - self.heading + math.pi) % math.tau - math.pi
            self.heading += max(-0.35, min(0.35, diff * 0.50))
            self._wander_des = desired
            self._wander_cd  = int(self._rng.integers(60, 180))
        else:
            diff = (desired - self.heading + math.pi) % math.tau - math.pi
            self.heading += max(-0.10, min(0.10, diff * 0.14))

        spd = (1.5 + bass * 0.8 + beat * 0.4) * self.scale
        self.x += math.cos(self.heading) * spd
        self.y += math.sin(self.heading) * spd

        cl = min(int(28 * self.scale), max(1, W // 2), max(1, H // 2))
        self.x = max(float(cl), min(float(W - cl), self.x))
        self.y = max(float(cl), min(float(H - cl), self.y))

    def draw(self, surf, outline_col=None):
        flap     = math.sin(self.wing_phase) * 0.5 + 0.5
        body_col = hsl(self.hue, l=0.28)
        wc1      = hsl(self.hue, l=0.58)
        wc2      = hsl((self.hue + 0.10) % 1.0, l=0.46)
        
        # Consolidate wing drawing - fewer polygon calls
        for upper in (-1, 1):
            col = wc1 if upper == 1 else wc2
            for side in (-1, 1):
                pts = _wing_poly(self.x, self.y, self.heading,
                                 side, upper, flap, self.scale)
                pygame.draw.polygon(surf, col, pts)
                # Skip outline when scale is small or just draw one
                if self.scale > 3.0:
                    pygame.draw.polygon(surf, (20, 20, 20), pts, 1)

        ca, sa = math.cos(self.heading), math.sin(self.heading)
        bl = int(8 * self.scale) # Slightly shorter body
        hx, hy = int(self.x + ca * bl), int(self.y + sa * bl)
        tx, ty = int(self.x - ca * bl), int(self.y - sa * bl)
        lw = max(1, int(2 * self.scale))
        pygame.draw.line(surf, body_col, (hx, hy), (tx, ty), lw)



# ── Pair ──────────────────────────────────────────────────────────────────────

def _edge_spawn(W, H, rng):
    """Return a position just inside one screen edge."""
    m = min(60, max(1, min(W, H) // 3))
    edge = int(rng.integers(0, 4))
    y0, y1 = m, max(m + 1, H - m)
    x0, x1 = m, max(m + 1, W - m)
    if edge == 0:  return float(m), float(rng.uniform(y0, y1))
    if edge == 1:  return float(W - m), float(rng.uniform(y0, y1))
    if edge == 2:  return float(rng.uniform(x0, x1)), float(m)
    return float(rng.uniform(x0, x1)), float(H - m)


# Scale is 70 % of the original 7.2 / 6.84
_SOLO_SCALE = round(7.2  * 0.70, 2)   # 5.04
_LOVE_SCALE = round(6.84 * 0.70, 2)   # 4.79


class _Pair:
    """One loving pair of butterflies with its own lifecycle.

    Once both butterflies are alive they chase each other — solo steers
    toward love and love steers toward solo, each targeting a point offset
    by a rotating angle at a shrinking radius.  This creates a playful
    spiral pursuit that eventually settles into a tight mutual orbit.
    """

    def __init__(self, hue, rng, spawn_delay=0):
        self.hue          = hue
        self._rng         = rng
        self._spawn_delay = spawn_delay
        self._join_delay  = int(rng.integers(600, 1800))
        self._lifetime    = int(rng.integers(2400, 5400))
        self._age         = -spawn_delay
        self._orbit_ang   = float(rng.uniform(0, math.tau))
        self._orbit_r     = 240.0    # starts wide, shrinks to ~40
        self.solo         = None
        self.love         = None
        self._departing   = False
        # Wander-break: occasionally one butterfly leaves the orbit briefly
        self._break_cd    = int(rng.integers(800, 1600))  # frames until next break
        self._break_timer = 0                          # counts down during break

    def dead(self, W, H):
        if not self._departing:
            return False
        b1_gone = self.solo is None or self.solo.off_screen(W, H)
        b2_gone = self.love is None or self.love.off_screen(W, H)
        return b1_gone and b2_gone

    def update(self, bass, beat, global_hue, t, W, H):
        self._age += 1

        if self.solo is None and self._age >= 0:
            x, y = _edge_spawn(W, H, self._rng)
            self.solo = _Butterfly(x, y, hue=global_hue, rng=self._rng, scale=_SOLO_SCALE)

        if self.solo is None:
            return

        self.solo.hue = global_hue
        if self.love:
            self.love.hue = (global_hue + 0.50) % 1.0

        if self.love is None and self._age >= self._join_delay:
            x, y = _edge_spawn(W, H, self._rng)
            self.love = _Butterfly(x, y,
                                   hue=(global_hue + 0.50) % 1.0,
                                   rng=self._rng,
                                   scale=_LOVE_SCALE)

        if self._age >= self._lifetime and not self._departing:
            self._departing = True
            self.solo.start_depart()
            if self.love:
                self.love.start_depart()

        if self.love is not None and not self._departing:
            # Wander-break countdown
            if self._break_timer > 0:
                self._break_timer -= 1
            else:
                self._break_cd -= 1
                if self._break_cd <= 0:
                    self._break_timer = int(self._rng.integers(200, 500))
                    self._break_cd    = int(self._rng.integers(900, 1800))
                    # Expand orbit radius back out so reunion feels fresh
                    self._orbit_r = min(self._orbit_r + 80.0, 200.0)

            if self._break_timer > 0:
                # One butterfly wanders freely; the orbit pauses
                self.solo.update(bass, beat, t, W, H)
                self.love.update(bass, beat, t, W, H)
            else:
                # Mutual chase: orbit angle rotates faster as radius shrinks
                # (conservation-of-angular-momentum feel)
                ang_speed = 0.012 + beat * 0.020 + 0.003 * max(0.0, 1.0 - self._orbit_r / 240)
                self._orbit_ang += ang_speed
                if self._orbit_r > 40.0:
                    self._orbit_r -= 0.06

                r = self._orbit_r
                # Solo chases: point offset from love's position at opposite angle
                solo_target = (
                    self.love.x + math.cos(self._orbit_ang + math.pi) * r,
                    self.love.y + math.sin(self._orbit_ang + math.pi) * r,
                )
                # Love chases: point offset from solo's position at the orbit angle
                love_target = (
                    self.solo.x + math.cos(self._orbit_ang) * r,
                    self.solo.y + math.sin(self._orbit_ang) * r,
                )
                self.solo.update(bass, beat, t, W, H, chase_pos=solo_target)
                self.love.update(bass, beat, t, W, H, chase_pos=love_target)
        else:
            self.solo.update(bass, beat, t, W, H)
            if self.love:
                self.love.update(bass, beat, t, W, H)

        # Wing sync when close
        if self.love is not None:
            dist = math.hypot(self.love.x - self.solo.x,
                              self.love.y - self.solo.y)
            sync_range = 130 * _SOLO_SCALE
            if dist < sync_range:
                sync = 1.0 - dist / sync_range
                diff = self.love.wing_phase - self.solo.wing_phase
                self.love.wing_phase -= diff * sync * 0.12

    def draw(self, surf, beat, global_hue):
        if self.solo is None:
            return

        if self.love and beat > 0.8 and not self._departing:
            dist = math.hypot(self.love.x - self.solo.x,
                              self.love.y - self.solo.y)
            if dist < 300:
                mx = (self.solo.x + self.love.x) / 2
                my = (self.solo.y + self.love.y) / 2
                for _ in range(4):
                    sx = int(mx + float(self._rng.normal(0, 25)))
                    sy = int(my + float(self._rng.normal(0, 25)))
                    pygame.draw.circle(surf,
                                       hsl((global_hue + 0.12) % 1.0, l=0.80),
                                       (sx, sy), int(self._rng.integers(2, 5)))

        oc1 = hsl((global_hue + 0.05) % 1.0, l=0.20)
        oc2 = hsl((global_hue + 0.55) % 1.0, l=0.20)
        self.solo.draw(surf, outline_col=oc1)
        if self.love:
            self.love.draw(surf, outline_col=oc2)


# ── Main effect ───────────────────────────────────────────────────────────────

class Butterflies(Effect):
    """Three pairs of butterflies.  Each pair has its own lifecycle:
    solo → partner joins → mutual love chase → wander off-screen → new pair.

    TRAIL_ALPHA = 0: trails are managed on an internal surface so they survive
    on Android / OpenGL backends where the display backbuffer is not preserved
    between frames.
    """

    TRAIL_ALPHA = 0    # owns its trail surface
    RES_DIV     = 2    # Render at 1/2 resolution
    MAX_PAIRS   = 3
    _PAIR_OFFSET_1 = 0
    _PAIR_OFFSET_2_LOW = 300
    _PAIR_OFFSET_2_HIGH = 700
    _PAIR_OFFSET_3_LOW = 800
    _PAIR_OFFSET_3_HIGH = 1400
    # Fade equivalent to the old TRAIL_ALPHA=22 main-loop overlay:
    #   semi-transparent black at alpha 22 → multiply by (255-22)/255 ≈ 233/255
    _FADE_FILL  = (233, 233, 233)

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self._rng = np.random.default_rng(config.RNG_SEED)
        W, H = self._render_size()[:2]
        self._tick       = 0
        self._global_hue = float(self._rng.random())
        self._trail      = pygame.Surface((W, H))
        self._trail.fill((0, 0, 0))
        self._scaled     = pygame.Surface((1, 1))
        self._pairs: list[_Pair] = []
        offsets = [self._PAIR_OFFSET_1,
                   int(self._rng.integers(self._PAIR_OFFSET_2_LOW, self._PAIR_OFFSET_2_HIGH)),
                   int(self._rng.integers(self._PAIR_OFFSET_3_LOW, self._PAIR_OFFSET_3_HIGH))]
        for i, off in enumerate(offsets):
            hue = (self._global_hue + i / self.MAX_PAIRS) % 1.0
            self._pairs.append(_Pair(hue, self._rng, spawn_delay=off))

    def draw(self, surf, waveform, fft, beat, tick):
        dest_w, dest_h = surf.get_size()
        W, H = self._render_size()[:2]
        if self._trail.get_size() != (W, H):
            self._trail = pygame.Surface((W, H))
            self._trail.fill((0, 0, 0))
            self._scaled = pygame.Surface((dest_w, dest_h))
        elif self._scaled.get_size() != (dest_w, dest_h):
            self._scaled = pygame.Surface((dest_w, dest_h))
        self._tick       += 1
        self._global_hue  = (self._global_hue + 0.0014) % 1.0
        bass = float(np.mean(fft[:min(6, len(fft))]))

        self._pairs = [p for p in self._pairs if not p.dead(W, H)]
        while len(self._pairs) < self.MAX_PAIRS:
            hue = (self._global_hue + float(self._rng.random()) * 0.5) % 1.0
            self._pairs.append(_Pair(hue, self._rng, spawn_delay=int(self._rng.integers(60, 200))))

        # Fade the internal trail surface each frame
        self._trail.fill(self._FADE_FILL, special_flags=pygame.BLEND_RGB_MULT)

        for i, pair in enumerate(self._pairs):
            gh = (self._global_hue + i / self.MAX_PAIRS) % 1.0
            pair.update(bass, beat, gh, self._tick, W, H)
            pair.draw(self._trail, beat, gh)

        if surf.get_size() != self._trail.get_size():
            pygame.transform.scale(self._trail, surf.get_size(), self._scaled)
            surf.blit(self._scaled, (0, 0), special_flags=pygame.BLEND_RGB_MAX)
        else:
            surf.blit(self._trail, (0, 0), special_flags=pygame.BLEND_RGB_MAX)
