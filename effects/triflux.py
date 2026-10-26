import math

import numpy as np
import pygame

import config
from .utils import hsl


from .base import Effect

class Attractor(Effect):
    """Triangle mosaic wall.

    All triangles are wireframe with rainbow edges.  4-6 tiles are filled
    at any time.  On beats, a tile pops to the FRONT at 4-5× size, pulses
    with bass, then after a fixed lifetime springs back into the grid.
    Active tiles are drawn last (always on top) with a solid fill so
    nothing shows through from behind.
    """

    TIME_BASED = True
    TRAIL_ALPHA  = 48
    N_COLS       = 14
    GAP          = 0.88
    N_FILLED     = 5
    N_ACTIVE_MAX = 3
    ACTIVE_LIFE_MIN = 150  # ~2.5 s — guaranteed minimum time big and in foreground
    ACTIVE_LIFE_MAX = 360  # 6 s — maximum random lifetime
    MIN_LIFE        = 150  # minimum remaining life when extended by a beat

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.hue        = 0.0
        self._rng = np.random.default_rng(config.RNG_SEED)
        self.tiles      = []
        self.filled_ids = []
        self.active_ids = []
        self._built     = False
        self._W = self._H = 0
        self._swap_cd = 0
        self._beat_prev = 0.0
        self._beat_cd = 0.0
        self._auto_cd   = int(self._rng.integers(180, 301))  # frames until next auto-activation
        # Two independent rainbow sweeps so one is nearly always on screen
        # Second starts mid-travel so they're never both off-screen simultaneously
        self._sweeps = [
            {"pos": 0.0,   "angle": self._rng.uniform(0, math.tau), "vel": 3.2},
            {"pos": 600.0, "angle": self._rng.uniform(0, math.tau), "vel": 4.1},
        ]
        self._sweep_width = 90.0         # half-width of each sweep band
        self._sweep_diag  = 0.0          # set on build (screen diagonal)

    # ------------------------------------------------------------------
    def _build_grid(self):
        self.tiles.clear()
        self.filled_ids.clear()
        self.active_ids.clear()
        tw = self._W / self.N_COLS
        th = tw * math.sqrt(3) / 2
        # Start one tile outside each edge so no clipped triangles appear at borders
        n_cols_ext = self.N_COLS + 2   # one extra column each side
        n_rows = int(self._H / th) + 3  # one extra row each side
        x0 = -tw
        y0 = -th

        for r in range(n_rows):
            y_top = y0 + r * th
            y_bot = y_top + th
            for k in range(n_cols_ext + 1):
                x = x0 + k * tw
                up = [
                    (x + tw / 2, y_top),
                    (x,          y_bot),
                    (x + tw,     y_bot),
                ]
                self.tiles.append(self._make_tile(up))
                dn = [
                    (x + tw / 2,       y_top),
                    (x + tw + tw / 2,  y_top),
                    (x + tw,           y_bot),
                ]
                self.tiles.append(self._make_tile(dn))

        # Seed filled set — allow any tile whose centroid is near the screen
        visible = self._visible_indices()
        k = min(self.N_FILLED, len(visible))
        self.filled_ids = list(self._rng.choice(visible, size=k, replace=False))
        self._built = True
        # Screen diagonal for sweep wrap-around
        self._sweep_diag = math.hypot(self._W, self._H)
        # Initialise sweep positions at the true screen edge for each angle
        for idx, sw in enumerate(self._sweeps):
            mn, mx = self._sweep_edge_range(sw["angle"])
            sw["pos"] = mn - self._sweep_width + idx * (mx - mn + self._sweep_width) * 0.5

    def _sweep_edge_range(self, angle):
        """Return (min_proj, max_proj) of screen corners onto sweep direction."""
        W, H = self._W, self._H
        cos_a, sin_a = math.cos(angle), math.sin(angle)
        projs = [x * cos_a + y * sin_a for x, y in ((0,0),(W,0),(0,H),(W,H))]
        return min(projs), max(projs)

    def _make_tile(self, verts):
        cx = sum(v[0] for v in verts) / 3
        cy = sum(v[1] for v in verts) / 3
        return {
            "verts":   verts,
            "cx": cx,  "cy": cy,
            "hue":     self._rng.random(),
            "hvel":    self._rng.uniform(-0.0006, 0.0006),
            "bright":  self._rng.uniform(0.20, 0.50),
            "rot":     0.0,
            "rot_vel": 0.0,
            "scale":   1.0,
            "svel":    0.0,
            "life":    0,      # frames remaining as active; 0 = not active / falling back
        }

    def _visible_indices(self):
        """Tiles whose centroid is within a half-tile margin of the screen."""
        W, H = self._W, self._H
        tw = W / self.N_COLS
        m  = tw
        return [i for i, t in enumerate(self.tiles)
                if -m <= t["cx"] < W + m and -m <= t["cy"] < H + m]

    def _interior_indices(self):
        """Tiles whose centroid is comfortably inside the screen (not edge tiles)."""
        W, H  = self._W, self._H
        tw    = W / self.N_COLS
        margin = tw * 1.5   # stay at least 1.5 tile-widths from each edge
        return [i for i, t in enumerate(self.tiles)
                if margin <= t["cx"] < W - margin and margin <= t["cy"] < H - margin]

    def _screen_verts(self, tile):
        cx, cy  = tile["cx"], tile["cy"]
        s       = self.GAP * tile["scale"]
        cos_r   = math.cos(tile["rot"])
        sin_r   = math.sin(tile["rot"])
        _C = 16383  # safe pygame/SDL coordinate limit
        return [(max(-_C, min(_C, int(cx + ((vx - cx) * s) * cos_r - ((vy - cy) * s) * sin_r))),
                 max(-_C, min(_C, int(cy + ((vx - cx) * s) * sin_r + ((vy - cy) * s) * cos_r))))
                for vx, vy in tile["verts"]]

    def _rainbow_edges(self, surf, pts, lw=1):
        for i in range(3):
            a = pts[i];  b = pts[(i + 1) % 3]
            angle = math.atan2(b[1] - a[1], b[0] - a[0])
            h = (self.hue * 2 + angle / math.tau + 0.5) % 1.0
            # Parallel subpixel strokes retain smooth outer edges at every width.
            length = math.hypot(b[0] - a[0], b[1] - a[1])
            if length == 0:
                continue
            nx, ny = -(b[1] - a[1]) / length, (b[0] - a[0]) / length
            color = hsl(h, l=0.75)
            for stroke in range(max(1, lw)):
                offset = stroke - (lw - 1) / 2.0
                pygame.draw.aaline(surf, color,
                                   (a[0] + nx * offset, a[1] + ny * offset),
                                   (b[0] + nx * offset, b[1] + ny * offset))

    # ------------------------------------------------------------------
    def _activate(self, bass):
        candidates = [i for i in self._interior_indices() if i not in self.active_ids]
        if candidates and len(self.active_ids) < self.N_ACTIVE_MAX:
            idx = int(self._rng.choice(candidates))
            tile = self.tiles[idx]
            self.active_ids.append(idx)
            tile["life"] = float(self._rng.integers(self.ACTIVE_LIFE_MIN, self.ACTIVE_LIFE_MAX))
            tile["rot_vel"] = float(self._rng.choice([-1,1]) * self._rng.uniform(.008,.018))
            tile["home_cx"], tile["home_cy"] = tile["cx"], tile["cy"]

    def _resize_grid(self, W, H):
        active = [(self.tiles[i].copy(), self.tiles[i]["cx"] / self._W,
                   self.tiles[i]["cy"] / self._H) for i in self.active_ids] if self._built else []
        self._W, self._H = W, H
        self._build_grid()
        for previous, x, y in active:
            candidates = [i for i in self._interior_indices() if i not in self.active_ids]
            if not candidates:
                break
            idx = min(candidates, key=lambda i: (self.tiles[i]["cx"]-x*W)**2 + (self.tiles[i]["cy"]-y*H)**2)
            tile = self.tiles[idx]
            for key in ("hue","hvel","bright","rot","rot_vel","scale","svel","life"):
                tile[key] = previous[key]
            tile["home_cx"],tile["home_cy"] = tile["cx"],tile["cy"]
            self.active_ids.append(idx)

    def _advance(self, bass, mid, high, step):
        self.hue += .003 * step
        self._swap_cd -= step
        self._auto_cd -= step
        self._beat_cd = max(0., self._beat_cd-step)
        if self._swap_cd <= 0:
            self._swap_cd += float(self._rng.integers(70,121))
            choices = [i for i in self._visible_indices() if i not in self.filled_ids]
            if choices and self.filled_ids:
                self.filled_ids[int(self._rng.integers(len(self.filled_ids)))] = int(self._rng.choice(choices))
        if self._auto_cd <= 0:
            self._auto_cd += float(self._rng.integers(180,321))
            self._activate(bass)
        for sweep in self._sweeps:
            sweep["pos"] += sweep["vel"] * (1+mid*.35) * step
            _, maximum = self._sweep_edge_range(sweep["angle"])
            if sweep["pos"] > maximum+self._sweep_width:
                sweep["angle"] = float(self._rng.uniform(0, math.tau))
                sweep["pos"] = self._sweep_edge_range(sweep["angle"])[0]-self._sweep_width
        # Small integration steps keep the damped springs stable across frame rates.
        substeps = max(1, math.ceil(step*2))
        dt = step/substeps
        finished = []
        for i,tile in enumerate(self.tiles):
            tile["hue"] = (tile["hue"] + (tile["hvel"]+.00015)*step) % 1
            active = i in self.active_ids
            if active:
                tile["life"] = max(0.,tile["life"]-step)
            alive = active and tile["life"] > 0
            target = 4.5 + bass*1.2 + mid*.4 if alive else 1.
            for _ in range(substeps):
                tile["svel"] += (target-tile["scale"])*.14*dt
                tile["svel"] *= .74**dt
                tile["scale"] += tile["svel"]*dt
                if alive:
                    target_speed = math.copysign(.010+bass*.006+high*.003,tile["rot_vel"] or 1)
                    tile["rot_vel"] += (target_speed-tile["rot_vel"])*(1-.96**dt)
                else:
                    tile["rot_vel"] += -tile["rot"]*.05*dt
                    tile["rot_vel"] *= .85**dt
                tile["rot"] += tile["rot_vel"]*dt
            if active and not alive and abs(tile["scale"]-1)<.03 and abs(tile["rot"])<.02 and abs(tile["rot_vel"])<.003:
                tile["scale"],tile["rot"],tile["rot_vel"],tile["svel"] = 1.,0.,0.,0.
                finished.append(i)
        for i in finished:
            self.active_ids.remove(i)

    def draw(self, surf, waveform, fft, beat, tick):
        W,H = surf.get_size()
        if not self._built or (W,H) != (self._W,self._H):
            self._resize_grid(W,H)
        motion,light = self._audio_response(beat)
        bass,mid,high = motion
        step = self._frame_step()
        if beat > .25 and self._beat_prev <= .25 and self._beat_cd <= 0:
            self._activate(bass)
            self._beat_cd = 20
        self._beat_prev = beat
        self._advance(bass,mid,high,step)
        for i,tile in enumerate(self.tiles):
            if i in self.active_ids:
                continue
            points = self._screen_verts(tile)
            for sweep in self._sweeps:
                distance = tile["cx"]*math.cos(sweep["angle"])+tile["cy"]*math.sin(sweep["angle"])
                t = max(0.,1-abs(distance-sweep["pos"])/self._sweep_width)
                if t > 0:
                    hue = (self.hue+distance/self._sweep_diag)%1
                    pygame.draw.polygon(surf,hsl(hue,l=min(.85,.2+t*.55+mid*.1)),points)
            if i in self.filled_ids:
                pygame.draw.polygon(surf,hsl((tile["hue"]+self.hue)%1,l=min(.75,tile["bright"]+light[0]*.2)),points)
            self._rainbow_edges(surf,points)
        for i in self.active_ids:
            tile = self.tiles[i]
            points = self._fit_viewport(self._screen_verts(tile),W,H).astype(int).tolist()
            hue = (tile["hue"]+self.hue)%1
            pygame.draw.polygon(surf,(0,0,0),points)
            pygame.draw.polygon(surf,hsl(hue,l=min(.92,tile["bright"]+.25+light[0]*.25)),points)
            self._rainbow_edges(surf,points,max(2,int(3+light[2]*1.5)))
