import math
import numpy as np
import pygame
import config
from .utils import hsl
from .base import Effect

class Cube(Effect):
    """Dual rotating wireframe cubes — each axis driven by a different band.

    Satellites are rendered on a dedicated persistent surface (faded more
    slowly than the main surface) so they leave long trailing lines that
    are always composited above the main cubes.
    """

    TRAIL_ALPHA  = 42
    _SAT_FADE    = 42

    VERTS = np.array([
        [-1,-1,-1],[1,-1,-1],[1,1,-1],[-1,1,-1],
        [-1,-1, 1],[1,-1, 1],[1,1, 1],[-1,1, 1],
    ], dtype=float)
    EDGES = [(0,1),(1,2),(2,3),(3,0),(4,5),(5,6),(6,7),(7,4),
             (0,4),(1,5),(2,6),(3,7)]

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self._rng = np.random.default_rng(config.RNG_SEED)
        self.rx = self.ry = self.rz = 0.0
        self.hue      = 0.0
        self.fade_hue = 0.0
        self.scale    = 1.0
        self.svel     = 0.0
        self.rvx = self.rvy = self.rvz = 0.0
        self.orb_angle  = 0.0
        self.sat_rx = 0.0
        self.sat_ry = 0.0
        self.sat_surf   = None
        self._sat_fade  = None

    def _target_size(self):
        return self._render_size()[:2]

    def _fov(self, W, H):
        return min(W, H) * 0.72

    def _project(self, v, W, H):
        cx, cy = W // 2, H // 2
        fov = self._fov(W, H)
        z = v[2] + 3.8
        return (int(v[0] * fov / z + cx),
                int(v[1] * fov / z + cy))

    def _project_sat(self, verts_3d, ox, oy, sat_scale, W, H):
        cx, cy = W // 2, H // 2
        fov = self._fov(W, H)
        z       = 3.8
        scale_s = fov / z
        cx_s    = ox * scale_s + cx
        cy_s    = oy * scale_s + cy
        extent = sat_scale * scale_s + 2
        cx_s   = max(extent, min(W - extent, cx_s))
        cy_s   = max(extent, min(H - extent, cy_s))
        return [(int(cx_s + v[0] * scale_s), int(cy_s + v[1] * scale_s))
                for v in verts_3d]

    def _init_sat_surf(self):
        W, H = self._target_size()
        self.sat_surf  = pygame.Surface((W, H), pygame.SRCALPHA)
        self.sat_surf.fill((0, 0, 0, 0))
        self._sat_fade_surf = pygame.Surface((W, H), pygame.SRCALPHA)
        self._sat_fade_surf.fill((0, 0, 0, self._SAT_FADE))

    @staticmethod
    def _Rx(a):
        c, s = math.cos(a), math.sin(a)
        return np.array([[1,0,0],[0,c,-s],[0,s,c]])

    @staticmethod
    def _Ry(a):
        c, s = math.cos(a), math.sin(a)
        return np.array([[c,0,s],[0,1,0],[-s,0,c]])

    @staticmethod
    def _Rz(a):
        c, s = math.cos(a), math.sin(a)
        return np.array([[c,-s,0],[s,c,0],[0,0,1]])

    def draw(self, surf, waveform, fft, beat, tick):
        W, H = self._target_size()
        if self.sat_surf is None or self.sat_surf.get_size() != (W, H):
            self._init_sat_surf()

        self.fade_hue += 0.0018
        bass = beat
        mid  = config.MID_ENERGY
        high = config.TREBLE_ENERGY
        motion = self._display_motion_scale()
        bass_m = bass * motion
        mid_m = mid * motion
        high_m = high * motion

        self.rvx += 0.00025 + mid_m * 0.025 + bass_m * 0.08
        self.rvy += 0.00035 + bass_m * 0.12
        self.rvz += 0.00018 + high_m * 0.035 + bass_m * 0.04
        self.rvx *= 0.94; self.rvy *= 0.94; self.rvz *= 0.94
        self.rx += self.rvx; self.ry += self.rvy; self.rz += self.rvz

        self.svel += bass_m * 0.32 + (1.0 - self.scale) * 0.18 * motion
        self.svel *= 0.68
        self.scale += self.svel
        self.scale = max(0.5, min(1.25, self.scale))

        R = self._Rx(self.rx) @ self._Ry(self.ry) @ self._Rz(self.rz)

        # High energy treble adds physical vertex jitter to the cubes
        jitter_amp = high_m * 0.08
        for base_scale, hue_off in ((self.scale, 0.0), (self.scale * 0.45, 0.5)):
            cube_verts = self.VERTS * base_scale
            if jitter_amp > 0.001:
                cube_verts = cube_verts + self._rng.normal(0, jitter_amp, cube_verts.shape)
            verts = (R @ cube_verts.T).T
            proj  = [self._project(v, W, H) for v in verts]
            for ei, (a, b) in enumerate(self.EDGES):
                h = (self.fade_hue + hue_off + ei / len(self.EDGES) * 0.4) % 1.0
                lw = max(1, int(2 + self.svel * 4 + high_m * 2.0)) if base_scale > 0.4 else 1
                color = hsl(h, l=0.40 + min(self.svel, 1.0) * 0.25 + mid_m * 0.10)
                pygame.draw.line(surf, color, proj[a], proj[b], lw)

        sat_scale = min(self.scale * 0.28, 0.55)
        ORB_R = 2.6 + mid_m * 0.4
        self.orb_angle += 0.012 + bass_m * 0.04 + mid_m * 0.03
        self.sat_rx += 0.018 + high_m * 0.04; self.sat_ry += 0.026 + high_m * 0.03
        Rs = self._Rx(self.sat_rx) @ self._Ry(self.sat_ry)

        # Fade satellite trail
        self.sat_surf.blit(self._sat_fade_surf, (0, 0))

        for si in range(2):
            theta = self.orb_angle + si * math.pi
            ox, oy = ORB_R * math.cos(theta), ORB_R * math.sin(theta)
            sat_verts = self.VERTS * sat_scale
            if jitter_amp > 0.001:
                sat_verts = sat_verts + self._rng.normal(0, jitter_amp, sat_verts.shape)
            verts = (Rs @ sat_verts.T).T
            proj  = self._project_sat(verts, ox, oy, sat_scale, W, H)
            h_off = si * 0.5
            for ei, (a, b) in enumerate(self.EDGES):
                h = (self.fade_hue + h_off + ei / len(self.EDGES) * 0.4) % 1.0
                # Treble increases satellite line width shimmer
                sat_lw = max(1, int(1 + high_m * 2))
                color = hsl(h, l=0.18 + min(self.svel, 1.0) * 0.28 + mid_m * 0.15)
                pygame.draw.line(self.sat_surf, color, proj[a], proj[b], sat_lw)

        # Use BLEND_RGBA_MAX to preserve alpha 255 from satellite lines
        surf.blit(self.sat_surf, (0, 0), special_flags=pygame.BLEND_RGB_MAX)
