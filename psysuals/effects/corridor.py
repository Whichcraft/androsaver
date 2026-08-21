import math

import numpy as np
import pygame

import config
from .utils import hsl


from .base import Effect

class Corridor(Effect):
    """Neon rainbow corridor — concentric rounded-rectangle frames fly toward
    the camera creating a first-person ride through a glowing geometric passage.
    Full rainbow sweep across depth; beat flares nearest frames and spawns
    glowing sparks that streak toward the camera.

    Two persistent surfaces are managed internally:
      corridor_surf — frame geometry with its own fade
      spark_surf    — sparks with a slower fade
    psysualizer clears surf to black each frame (TRAIL_ALPHA=255), then we
    blit corridor_surf then spark_surf so sparks are ALWAYS above frames.
    """

    N_FRAMES = 28
    Z_FAR    = 12.0
    Z_NEAR   = 0.06
    WORLD_H  = 2.0
    ASPECT   = 1.65
    _MAX_SPARKS = 100

    TRAIL_ALPHA    = 255   # psysualizer clears surf to black; we manage trails
    RES_DIV        = 2
    _CORRIDOR_FADE = 28
    _SPARK_FADE    = 10    # slower fade → longer spark trails

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self._rng  = np.random.default_rng(config.RNG_SEED)
        self.hue    = 0.0
        self.time   = 0.0
        spacing     = (self.Z_FAR - self.Z_NEAR) / self.N_FRAMES
        self.frames = [{"z": self.Z_NEAR + i * spacing} for i in range(self.N_FRAMES)]
        self._spark_count = 0
        self._sz    = np.empty(self._MAX_SPARKS, dtype=np.float32)
        self._shue  = np.empty(self._MAX_SPARKS, dtype=np.float32)
        self._sox   = np.empty(self._MAX_SPARKS, dtype=np.float32)
        self._soy   = np.empty(self._MAX_SPARKS, dtype=np.float32)
        self.corridor_surf = None
        self.spark_surf    = None
        self._cfade        = None
        self._sfade        = None

    def _path(self, t, mid=0.0):
        scale = 1.0 + mid * 0.50
        return (math.sin(t * 0.19) * 0.5 * scale, math.cos(t * 0.14) * 0.35 * scale)

    def _init_surfs(self, W, H):
        self.corridor_surf = pygame.Surface((W, H), pygame.SRCALPHA)
        self.corridor_surf.fill((0, 0, 0, 255))
        self.spark_surf = pygame.Surface((W, H), pygame.SRCALPHA)
        self.spark_surf.fill((0, 0, 0, 255))
        self._cfade = pygame.Surface((W, H))
        self._cfade.set_alpha(self._CORRIDOR_FADE)
        self._cfade.fill((0, 0, 0))
        self._sfade = pygame.Surface((W, H))
        self._sfade.set_alpha(self._SPARK_FADE)
        self._sfade.fill((0, 0, 0))

    def draw(self, surf, waveform, fft, beat, tick):
        W, H = surf.get_size()
        if self.corridor_surf is None or self.corridor_surf.get_size() != (W, H):
            self._init_surfs(W, H)

        self.hue  += 0.005
        bass       = beat
        mid        = config.MID_ENERGY
        high       = config.TREBLE_ENERGY
        motion     = self._display_motion_scale()
        bass_m     = bass * motion
        mid_m      = mid * motion
        high_m     = high * motion

        dt         = 0.022 + bass_m * 0.08 + mid_m * 0.06 + high_m * 0.04
        self.time += dt

        fov = min(W, H) * 0.72

        # Spawn rate is driven heavily by treble transients (sparkles) and bass
        spawn_n = int(bass_m * 4.0 + high_m * 6.0)
        for _ in range(min(spawn_n, self._MAX_SPARKS - self._spark_count)):
            i = self._spark_count
            self._sz[i]   = self.Z_FAR * (0.55 + float(self._rng.random()) * 0.37)
            self._shue[i] = (self.hue + float(self._rng.uniform(0, 0.6))) % 1.0
            self._sox[i]  = float(self._rng.uniform(-(self.WORLD_H * self.ASPECT) * 0.85,
                                                     (self.WORLD_H * self.ASPECT) * 0.85))
            self._soy[i]  = float(self._rng.uniform(-self.WORLD_H * 0.85, self.WORLD_H * 0.85))
            self._spark_count += 1

        for f in self.frames:
            f["z"] -= dt
            if f["z"] < self.Z_NEAR:
                f["z"] += self.Z_FAR

        # Update + compact sparks in-place (struct-of-arrays)
        new_n = 0
        for i in range(self._spark_count):
            self._sz[i] -= dt
            if self._sz[i] >= self.Z_NEAR:
                if new_n != i:
                    self._sz[new_n]   = self._sz[i]
                    self._shue[new_n] = self._shue[i]
                    self._sox[new_n]  = self._sox[i]
                    self._soy[new_n]  = self._soy[i]
                new_n += 1
        self._spark_count = new_n

        # ── Fade both layers ─────────────────────────────────────────────────
        self.corridor_surf.blit(self._cfade, (0, 0))
        self.spark_surf.blit(self._sfade, (0, 0))

        # ── Draw frames onto corridor_surf ───────────────────────────────────
        for f in sorted(self.frames, key=lambda f: -f["z"]):
            z      = max(f["z"], 0.01)
            near_t = max(0.0, 1.0 - z / self.Z_FAR)
            pcx, pcy = self._path(self.time - z * 0.5, mid=mid_m)
            cx_s   = int(pcx * fov / z + W / 2)
            cy_s   = int(pcy * fov / z + H / 2)

            h      = (self.hue + near_t) % 1.0
            bright = 0.06 + near_t * 0.70 + mid_m * 0.15 * near_t + bass_m * near_t * 0.50
            lw     = max(1, int(1 + bass_m * 3 * near_t + mid_m * 1.5 * near_t))

            half_h = int(self.WORLD_H * fov / z)
            half_w = int(self.WORLD_H * self.ASPECT * fov / z)
            if half_w < 3 or half_h < 3:
                continue

            rect   = pygame.Rect(cx_s - half_w, cy_s - half_h, half_w * 2, half_h * 2)
            radius = max(2, min(half_w // 3, half_h // 3, half_w - 1, half_h - 1))

            infl = lw * 5 + 4 + int(mid * 8)
            gr   = rect.inflate(infl, infl)
            g_r  = max(2, min(gr.width // 2 - 1, gr.height // 2 - 1, radius + infl // 2))
            pygame.draw.rect(self.corridor_surf, hsl(h, l=bright * 0.22), gr,   lw + 4, border_radius=g_r)
            pygame.draw.rect(self.corridor_surf, hsl(h, l=bright),        rect, lw,     border_radius=radius)

        # ── Draw sparks onto spark_surf ──────────────────────────────────────
        for i in range(self._spark_count):
            z      = max(self._sz[i], 0.01)
            near_t = max(0.0, 1.0 - z / self.Z_FAR)
            pcx, pcy = self._path(self.time - z * 0.5, mid=mid_m)
            sx     = int((pcx + self._sox[i]) * fov / z + W / 2)
            sy     = int((pcy + self._soy[i]) * fov / z + H / 2)
            # Spark size scales with treble transients
            r      = max(2, int((fov / z * 0.05) * (1.0 + high_m * 1.5)))
            h      = (self._shue[i] + near_t * 0.35) % 1.0
            bright = 0.35 + near_t * 0.60 + high_m * 0.25
            pygame.draw.circle(self.spark_surf, hsl(h, l=bright * 0.25), (sx, sy), r + 4)
            pygame.draw.circle(self.spark_surf, hsl(h, l=bright),         (sx, sy), max(1, r))

        # ── Composite: corridor below, sparks always on top ──────────────────
        surf.blit(self.corridor_surf, (0, 0))
        surf.blit(self.spark_surf,    (0, 0), special_flags=pygame.BLEND_ADD)
