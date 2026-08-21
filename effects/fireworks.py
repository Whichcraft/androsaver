"""Fireworks — pixel feedback falling/zooming fireworks display.

Each frame the previous frame is zoomed and multiplied dark, building
an infinite falling psychedelic tunnel.  Firework rockets launch from the
bottom, arc upward under gravity, and explode into glowing embers at the
apex.  Beat fires extra rockets and cranks the zoom speed.
The feedback loop swallows all trails.
"""
import math
import random

import numpy as np
import pygame
import pygame.surfarray as surfarray

import config
from .utils import hsl


_GRAV = 0.13    # pixels/frame² downward
_DRAG = 0.991   # velocity multiplier per frame


from .base import Effect

class Fireworks(Effect):
    TRAIL_ALPHA = 0   # we manage the surface
    RES_DIV     = 3   # Render at 1/3 resolution for FPS boost
    _FADE_ALPHA = 20

    _BASE_ZOOM       = 1.0038
    _BEAT_ZOOM       = 1.015
    _BEAT_FRAMES     = 50
    _HUE_BASE_STEP   = 0.0015
    _HUE_BASS_GAIN   = 0.002
    _HUE_HIGH_GAIN   = 0.001
    _ZOOM_BASS_GAIN  = 0.002
    _ZOOM_HIGH_GAIN  = 0.001
    # Auto-launch interval at gain=1.0.  Scales linearly with gain so that
    # higher intensity → longer interval (fewer rockets) and lower → shorter.
    # Formula: interval = _BASE_INTERVAL * gain  (clamped 20..200)
    _BASE_INTERVAL   = 40   # frames between auto-launches at gain 1.0
    _AUTO_INTERVAL_MIN = 30
    _AUTO_INTERVAL_MAX = 200
    _AUTO_START_STAGGER = (0, 40)
    _LAUNCH_X_RANGE   = (0.10, 0.90)
    _LAUNCH_VY_RANGE  = (-15.0, -10.0)
    _LAUNCH_VX_RANGE  = (-2.0, 2.0)
    _LAUNCH_HUE_JITTER = 0.25
    _EXPLODE_EMBERS   = (80, 120)
    _EXPLODE_TREBLE_COUNT_GAIN = 1.5
    _EXPLODE_SPEED_MEAN = 4.5
    _EXPLODE_SPEED_STD  = 1.8
    _EXPLODE_TREBLE_SPEED_GAIN = 1.2
    _EXPLODE_VERTICAL_DAMP = 0.85
    _EXPLODE_UPLIFT_RANGE = (0.0, 1.5)
    _EXPLODE_LIFE = (50, 100)
    _EXPLODE_HUE_JITTER = 0.09
    _EXPLODE_RADIUS_BASE = 2
    _EXPLODE_RADIUS_RAND = 3
    _ROCKET_TRAIL_LEN = 8
    _ROCKET_TRAIL_SKIP = 2
    _ROCKET_TRAIL_LIGHT = 0.8
    _ROCKET_TRAIL_TREBLE_RADIUS_GAIN = 2.0
    _ROCKET_CULL_MARGIN = 20
    _EMBER_CULL_MARGIN = 40
    _EMBER_LIGHT_BASE = 0.4
    _EMBER_LIGHT_LIFE_GAIN = 0.5
    _EMBER_LIGHT_TREBLE_GAIN = 0.15
    _MAX_ROCKETS = 120
    _MAX_EMBERS = 3000

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self._rng = np.random.default_rng(config.RNG_SEED)
        W, H, RD = self._render_size()
        self._trail   = pygame.Surface((W, H))
        self._trail.fill((0, 0, 0))
        self._scaled = pygame.Surface((config.WIDTH, config.HEIGHT))
        self._fade = pygame.Surface((W, H), pygame.SRCALPHA)
        self._fade.fill((0, 0, 0, self._FADE_ALPHA))
        self._flow_x = self._flow_y = None
        self._flow_src = self._flow_dst = None
        self._reset_flow(W, H)
        self._feedback_fbo = None
        self._hue     = self._rng.random()
        self._beat_t  = 0
        self._rockets = []   # [x, y, vx, vy, hue, trail_pts]
        self._embers  = []   # [x, y, vx, vy, hue, radius, life, max_life]
        self._auto_t  = int(self._rng.integers(*self._AUTO_START_STAGGER))
        self._beat_prev = 0.0

    # ── helpers ───────────────────────────────────────────────────────────────

    def _reset_flow(self, width, height):
        self._flow_x, self._flow_y = np.meshgrid(
            np.arange(width, dtype=np.float32),
            np.arange(height, dtype=np.float32),
            indexing="ij",
        )
        self._flow_src = np.empty((width, height, 3), dtype=np.uint8)
        self._flow_dst = np.empty_like(self._flow_src)
        self._flow_vx = np.empty((width, height), dtype=np.float32)
        self._flow_vy = np.empty_like(self._flow_vx)
        self._flow_src_x = np.empty((width, height), dtype=np.int32)
        self._flow_src_y = np.empty_like(self._flow_src_x)
        self._external_vx = self._external_vy = None
        self._external_sample_x = self._external_sample_y = None
        self._external_cache_vx = self._external_cache_vy = None
        self._external_key = None

    def _advect_trail(self, strength, tick):
        """Apply one bounded semi-Lagrangian step to the feedback trail."""
        width, height = self._trail.get_size()
        if self._flow_x is None or self._flow_x.shape != (width, height):
            self._reset_flow(width, height)
        np.sin(self._flow_y * 0.055 + tick * 0.018, out=self._flow_vx)
        np.cos(self._flow_x * 0.047 - tick * 0.014, out=self._flow_vy)
        self._flow_vx *= strength
        self._flow_vy *= strength
        np.clip(self._flow_x - self._flow_vx, 0, width - 1, out=self._flow_vx)
        np.clip(self._flow_y - self._flow_vy, 0, height - 1, out=self._flow_vy)
        if self._external_vx is not None:
            self._external_cache_vx[:] = self._external_vx[
                self._external_sample_y[:, None], self._external_sample_x[None, :]
            ].T * 0.18
            self._flow_vx += self._external_cache_vx
            self._flow_vy += self._external_cache_vy
            np.clip(self._flow_vx, 0, width - 1, out=self._flow_vx)
            np.clip(self._flow_vy, 0, height - 1, out=self._flow_vy)
        self._flow_src_x[:] = self._flow_vx
        self._flow_src_y[:] = self._flow_vy
        self._flow_src[:] = surfarray.array3d(self._trail)
        self._flow_dst[:] = self._flow_src[self._flow_src_x, self._flow_src_y]
        pixels = surfarray.pixels3d(self._trail)
        try:
            pixels[:] = self._flow_dst
        finally:
            del pixels

    def set_motion_field(self, field) -> None:
        if not field or len(field) != 2:
            self._external_vx = self._external_vy = None
            self._external_key = None
            return
        vx, vy = field
        if not isinstance(vx, np.ndarray) or not isinstance(vy, np.ndarray):
            self._external_vx = self._external_vy = None
            self._external_key = None
            return
        if vx.shape != vy.shape or vx.ndim != 2 or not np.isfinite(vx).all() or not np.isfinite(vy).all():
            self._external_vx = self._external_vy = None
            self._external_key = None
            return
        key = (id(vx), id(vy), vx.shape, self._trail_size())
        if key == self._external_key:
            return
        width, height = self._trail_size()
        source_h, source_w = vx.shape
        self._external_vx, self._external_vy = vx, vy
        self._external_sample_x = np.linspace(0, source_w - 1, width).astype(np.int32)
        self._external_sample_y = np.linspace(0, source_h - 1, height).astype(np.int32)
        self._external_cache_vx = np.empty((width, height), dtype=np.float32)
        self._external_cache_vy = np.empty_like(self._external_cache_vx)
        self._external_key = key

    def _trail_size(self):
        return self._trail.get_size()

    def _launch(self, res_div: int):
        if len(self._rockets) >= self._MAX_ROCKETS:
            return
        W, H = max(1, config.WIDTH // res_div), max(1, config.HEIGHT // res_div)
        x  = float(self._rng.uniform(W * self._LAUNCH_X_RANGE[0], W * self._LAUNCH_X_RANGE[1]))
        vy = float(self._rng.uniform(self._LAUNCH_VY_RANGE[0] / res_div, self._LAUNCH_VY_RANGE[1] / res_div))
        vx = float(self._rng.uniform(self._LAUNCH_VX_RANGE[0] / res_div, self._LAUNCH_VX_RANGE[1] / res_div))
        h  = (self._hue + float(self._rng.uniform(-self._LAUNCH_HUE_JITTER, self._LAUNCH_HUE_JITTER))) % 1.0
        self._rockets.append([float(x), float(H), vx, vy, h, []])

    def _explode(self, x, y, hue, treble=0.0, res_div: int = 1):
        n = int(self._rng.integers(*self._EXPLODE_EMBERS) * (1.0 + treble * self._EXPLODE_TREBLE_COUNT_GAIN))
        if getattr(config, "LOW_SPEC", False):
            n = max(5, n // 2)
        n = min(n, max(0, self._MAX_EMBERS - len(self._embers)))
        if n <= 0:
            return
        angs = self._rng.uniform(0.0, math.tau, n)
        spds = self._rng.normal(self._EXPLODE_SPEED_MEAN / res_div,
                                self._EXPLODE_SPEED_STD / res_div, n)
        spds *= (1.0 + treble * self._EXPLODE_TREBLE_SPEED_GAIN)
        uplift = self._rng.uniform(self._EXPLODE_UPLIFT_RANGE[0],
                                   self._EXPLODE_UPLIFT_RANGE[1] / res_div, n)
        lives = self._rng.integers(self._EXPLODE_LIFE[0], self._EXPLODE_LIFE[1] + 1, n)
        hue_j = self._rng.uniform(-self._EXPLODE_HUE_JITTER, self._EXPLODE_HUE_JITTER, n)
        radii = self._rng.integers(0, self._EXPLODE_RADIUS_RAND + 1, n)
        vx = np.cos(angs) * spds
        vy = np.sin(angs) * spds * self._EXPLODE_VERTICAL_DAMP - uplift
        hs = (hue + hue_j) % 1.0
        rs = np.maximum(1, (self._EXPLODE_RADIUS_BASE + radii) // res_div)
        for vx_i, vy_i, h_i, r_i, life in zip(vx, vy, hs, rs, lives):
            self._embers.append([x, y, float(vx_i), float(vy_i), float(h_i), int(r_i), int(life), int(life)])

    # ── main draw ─────────────────────────────────────────────────────────────

    def draw(self, surf, waveform, fft, beat, tick):
        W, H, RD = self._render_size()
        bass  = beat
        mid   = config.MID_ENERGY
        high  = config.TREBLE_ENERGY

        if self._trail.get_width() != W or self._trail.get_height() != H:
            self._trail = pygame.Surface((W, H))
            self._trail.fill((0, 0, 0))
            self._fade = pygame.Surface((W, H), pygame.SRCALPHA)
            self._fade.fill((0, 0, 0, self._FADE_ALPHA))
            self._reset_flow(W, H)
        if self._scaled.get_width() != surf.get_width() or self._scaled.get_height() != surf.get_height():
            self._scaled = pygame.Surface(surf.get_size())
        fbo_current = getattr(self.renderer, "is_offscreen_current", lambda fbo, w, h: True)
        if self.renderer is not None and (
            self._feedback_fbo is None
            or self._feedback_fbo.width != W
            or self._feedback_fbo.height != H
            or not fbo_current(self._feedback_fbo, W, H)
        ):
            self._feedback_fbo = self.renderer.offscreen(W, H)
        
        self._hue = (self._hue + self._HUE_BASE_STEP + bass * self._HUE_BASS_GAIN + high * self._HUE_HIGH_GAIN) % 1.0

        # Beat: launch rockets + boost
        if beat > 0.7 and self._beat_prev <= 0.7:
            self._beat_t = self._BEAT_FRAMES
            for _ in range(1 + int(beat)):
                self._launch(RD)
        self._beat_prev = float(beat)

        # Auto-launch between beats
        gain     = max(0.1, getattr(config, 'EFFECT_GAIN', 1.0))
        interval = int(max(self._AUTO_INTERVAL_MIN,
                           min(self._AUTO_INTERVAL_MAX, self._BASE_INTERVAL * gain)))
        self._auto_t += 1
        if self._auto_t >= interval:
            self._auto_t = 0
            self._launch(RD)

        # ── feedback: zoom trail ──────────────────────────────────────────────
        t = self._beat_t / self._BEAT_FRAMES if self._beat_t > 0 else 0.0
        self._beat_t = max(0, self._beat_t - 1)
        zoom    = self._BASE_ZOOM + t * (self._BEAT_ZOOM - self._BASE_ZOOM) + bass * self._ZOOM_BASS_GAIN + high * self._ZOOM_HIGH_GAIN

        # Feedback transform: use GL when available, CPU scale otherwise.
        if self.renderer is not None and self._feedback_fbo is not None:
            self.renderer.feedback_transform(self._trail, self._feedback_fbo, zoom, 0.0)
            fb = self.renderer.read_pixels(self._feedback_fbo)
            surfarray.blit_array(self._trail, fb[:, :, :3].transpose(1, 0, 2))
        else:
            zw, zh = round(W * zoom), round(H * zoom)
            zoomed = pygame.transform.scale(self._trail, (zw, zh))
            self._trail.fill((0, 0, 0))
            self._trail.blit(zoomed, (-((zw - W) // 2), -((zh - H) // 2)))

        # Fade with alpha overlay to preserve chroma better than RGB multiply.
        self._trail.blit(self._fade, (0, 0))
        self._advect_trail(0.35 + bass * 0.35 + mid * 0.18, tick)

        # ── rockets ───────────────────────────────────────────────────────────
        live = []
        for rk in self._rockets:
            x, y, vx, vy, hue, trail = rk
            vy += _GRAV / RD
            vx *= _DRAG
            x += vx; y += vy
            # Thinned trail for performance
            if tick % self._ROCKET_TRAIL_SKIP == 0:
                trail.append((int(x), int(y)))
                if len(trail) > self._ROCKET_TRAIL_LEN:
                    trail.pop(0)
            rk[0], rk[1], rk[2], rk[3] = x, y, vx, vy

            # Draw rocket trail core only (shimmers with treble)
            if trail:
                pygame.draw.circle(
                    self._trail,
                    hsl(hue, l=self._ROCKET_TRAIL_LIGHT),
                    trail[-1],
                    max(1, int(1.0 + high * self._ROCKET_TRAIL_TREBLE_RADIUS_GAIN)),
                )

            if vy >= 0 or y < -20:
                self._explode(x, y, hue, treble=high, res_div=RD)
            elif -self._ROCKET_CULL_MARGIN < x < W + self._ROCKET_CULL_MARGIN:
                live.append(rk)
        self._rockets = live

        # ── embers ────────────────────────────────────────────────────────────
        live = []
        for em in self._embers:
            x, y, vx, vy, hue, radius, life, max_life = em
            vy += _GRAV / RD
            vx *= _DRAG
            vy *= _DRAG
            x += vx; y += vy
            life -= 1
            em[0], em[1], em[2], em[3], em[4], em[5], em[6] = x, y, vx, vy, hue, radius, life
            if life > 0 and -self._EMBER_CULL_MARGIN < x < W + self._EMBER_CULL_MARGIN and y < H + self._EMBER_CULL_MARGIN:
                brightness = (
                    self._EMBER_LIGHT_BASE
                    + (life / max_life) * self._EMBER_LIGHT_LIFE_GAIN
                    + high * self._EMBER_LIGHT_TREBLE_GAIN
                )
                pygame.draw.circle(self._trail, hsl(hue, l=brightness),
                                   (int(x), int(y)), radius)
                live.append(em)
        self._embers = live

        if surf.get_size() != self._trail.get_size():
            pygame.transform.scale(self._trail, surf.get_size(), self._scaled)
            surf.blit(self._scaled, (0, 0), special_flags=pygame.BLEND_RGB_MAX)
        else:
            surf.blit(self._trail, (0, 0), special_flags=pygame.BLEND_RGB_MAX)

    def release(self):
        self._feedback_fbo = None
