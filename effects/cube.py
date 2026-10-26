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

    TIME_BASED = True
    TRAIL_ALPHA  = 18
    _SAT_FADE    = 16

    VERTS = np.array([
        [-1,-1,-1],[1,-1,-1],[1,1,-1],[-1,1,-1],
        [-1,-1, 1],[1,-1, 1],[1,1, 1],[-1,1, 1],
    ], dtype=float)
    EDGES = [(0,1),(1,2),(2,3),(3,0),(4,5),(5,6),(6,7),(7,4),
             (0,4),(1,5),(2,6),(3,7)]

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
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
        return getattr(self, "_canvas_size", (config.WIDTH, config.HEIGHT))

    def _project(self, v, fov=None):
        W, H = self._target_size()
        fov = min(W, H) * 0.9 if fov is None else fov
        cx, cy = W // 2, H // 2
        z = v[2] + 3.8
        return (int(v[0] * fov / z + cx),
                int(v[1] * fov / z + cy))

    def _project_sat(self, verts_3d, ox, oy, sat_scale, fov=None):
        W, H = self._target_size()
        fov = min(W, H) * 0.9 if fov is None else fov
        cx, cy = W // 2, H // 2
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
        if self.sat_surf is None:
            self.sat_surf = pygame.Surface((W,H),pygame.SRCALPHA)
            self.sat_surf.fill((0,0,0,0))
        else:
            self.sat_surf = pygame.transform.smoothscale(self.sat_surf,(W,H))
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
        self._canvas_size = surf.get_size()
        if self.sat_surf is None or self.sat_surf.get_size() != self._target_size():
            self._init_sat_surf()

        step = self._frame_step()
        motion,light = self._audio_response(beat)
        bass,mid,high = motion
        motion_scale,accent = self._profile_response()
        self.fade_hue += (.0012+mid*.0005)*step
        self.rvx = self._smooth(self.rvx,.004+mid*.002+bass*.003,.04)
        self.rvy = self._smooth(self.rvy,.005+bass*.004,.04)
        self.rvz = self._smooth(self.rvz,.002+high*.0015,.04)
        self.rx += self.rvx*step*motion_scale
        self.ry += self.rvy*step*motion_scale
        self.rz += self.rvz*step*motion_scale
        previous_scale = self.scale
        self.scale = self._smooth(self.scale,1.+bass*.06,.035)
        self.svel = self.scale-previous_scale

        R = self._Rx(self.rx) @ self._Ry(self.ry) @ self._Rz(self.rz)

        for base_scale, hue_off in ((self.scale, 0.0), (self.scale * 0.45, 0.5)):
            verts = (R @ (self.VERTS * base_scale).T).T
            proj = self._fit_viewport([self._project(v) for v in verts], *surf.get_size()).astype(int).tolist()
            for ei, (a, b) in enumerate(self.EDGES):
                h = (self.fade_hue + hue_off + ei / len(self.EDGES) * 0.4) % 1.0
                lw = max(1, int(2 + light[2])) if base_scale > 0.4 else 1
                color = hsl(h,l=min(.72,.40+light[0]*.12*accent+light[2]*.04))
                pygame.draw.line(surf, color, proj[a], proj[b], lw)

        sat_scale = min(self.scale * 0.28, 0.55)
        ORB_R = 2.6
        self.orb_angle += (.006+bass*.002+mid*.001)*step*motion_scale
        self.sat_rx += .008*step*motion_scale; self.sat_ry += .010*step*motion_scale
        Rs = self._Rx(self.sat_rx) @ self._Ry(self.sat_ry)

        # Fade satellite trail
        alpha = round(255*(1-(1-self._SAT_FADE/255)**step))
        self._sat_fade_surf.fill((0,0,0,alpha))
        self.sat_surf.blit(self._sat_fade_surf, (0, 0))

        for si in range(2):
            theta = self.orb_angle + si * math.pi
            ox, oy = ORB_R * math.cos(theta), ORB_R * math.sin(theta)
            verts = (Rs @ (self.VERTS * sat_scale).T).T
            proj = self._fit_viewport(self._project_sat(verts,ox,oy,sat_scale),*surf.get_size()).astype(int).tolist()
            h_off = si * 0.5
            for ei, (a, b) in enumerate(self.EDGES):
                h = (self.fade_hue + h_off + ei / len(self.EDGES) * 0.4) % 1.0
                color = hsl(h,l=min(.65,.22+light[0]*.10*accent))
                pygame.draw.line(self.sat_surf, color, proj[a], proj[b], 1)

        # Use BLEND_RGBA_MAX to preserve alpha 255 from satellite lines
        surf.blit(self.sat_surf, (0, 0), special_flags=pygame.BLEND_RGBA_MAX)
