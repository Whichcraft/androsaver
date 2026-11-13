"""Lanterns — paper lanterns rising over dark water.

Warm lanterns drift up into a deep blue night from a still lake. Each one
glows, sways in the wind and is mirrored in the water as a rippling streak
of light. Nearer lanterns are larger, brighter and faster; far ones are
small points of gold near the horizon.

  Bass   → the flames flicker brighter, the lanterns' glow swells
  Mid    → wind: the lanterns sway and drift sideways
  Treble → sparks drift up from the lanterns
  Beat   → new lanterns are released from the water
"""
import math

import numpy as np
import pygame

from .base import Effect
from .utils import hsl

_MAX_LANTERNS = 42
_MAX_SPARKS = 160
_HORIZON = 0.64          # horizon height as a fraction of the frame


class Lanterns(Effect):
    TIME_BASED  = True
    TRAIL_ALPHA = 255       # every frame is fully repainted; no trails

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.time = 0.0
        self._lanterns = []     # dict(x, y, depth, hue, phase, flicker)
        self._sparks = []       # [x, y, vx, vy, life, hue]
        self._beat_prev = 0.0
        self._idle = 0.0
        self._sky_size = None
        self._sky = None
        self._glow_cache = {}
        rng = self.rng
        self._stars = rng.random((90, 3))
        for _ in range(14):     # start with a sky already in motion
            self._release(rng.uniform(0.0, 1.0))

    # ------------------------------------------------------------------

    def _release(self, height=0.0):
        if len(self._lanterns) >= _MAX_LANTERNS:
            return
        rng = self.rng
        depth = float(rng.uniform(0.25, 1.0))        # 1 = nearest
        self._lanterns.append(dict(
            x=float(rng.uniform(0.04, 0.96)),
            # Height above the water, 0 at the waterline, 1 at the top.
            y=float(height), depth=depth,
            hue=float(rng.uniform(0.02, 0.11)),
            phase=float(rng.uniform(0.0, math.tau)),
            flicker=float(rng.uniform(0.0, math.tau))))

    def _background(self, W, H):
        if self._sky_size == (W, H):
            return self._sky
        self._sky_size = (W, H)
        horizon = int(H * _HORIZON)
        y = np.arange(H)[:, None].astype(float)
        sky_t = np.clip(y / max(1, horizon), 0.0, 1.0)
        water_t = np.clip((y - horizon) / max(1, H - horizon), 0.0, 1.0)
        top, low = np.array((6, 8, 30.0)), np.array((40, 32, 70.0))
        water_top, water_low = np.array((22, 20, 46.0)), np.array((3, 4, 14.0))
        rows = np.where(y < horizon, top + (low - top) * sky_t ** 1.6,
                        water_top + (water_low - water_top) * water_t ** 0.7)
        pixels = np.repeat(rows[:, None, :], W, axis=1).reshape(H, W, 3)
        self._sky = pygame.Surface((W, H))
        pygame.surfarray.blit_array(self._sky, np.clip(pixels, 0, 255).astype(np.uint8).transpose(1, 0, 2))
        return self._sky

    def _glow(self, radius, colour):
        """Soft additive glow sprite, cached per size and colour bucket."""
        radius = max(2, int(radius))
        key = (radius, colour)
        sprite = self._glow_cache.get(key)
        if sprite is None:
            if len(self._glow_cache) > 160:
                self._glow_cache.clear()
            size = radius * 2 + 1
            yy, xx = np.mgrid[0:size, 0:size] - radius
            fall = np.clip(1.0 - np.sqrt(xx * xx + yy * yy) / radius, 0.0, 1.0) ** 2.2
            rgb = (fall[..., None] * np.array(colour, dtype=float)).astype(np.uint8)
            sprite = pygame.Surface((size, size))
            pygame.surfarray.blit_array(sprite, rgb.transpose(1, 0, 2))
            self._glow_cache[key] = sprite
        return sprite

    # ------------------------------------------------------------------

    def _draw(self, surf, waveform, fft, beat, tick):
        W, H = surf.get_size()
        step = self._frame_step()
        seconds = step / 60.0
        motion, light = self._audio_response(beat)
        bass, mid, high = (float(v) for v in motion)
        motion_scale, accent = self._profile_response()
        flash = float(light[0]) * accent
        self.time += seconds
        rng = self.rng

        if beat > 0.6 and self._beat_prev <= 0.6:
            for _ in range(1 + int(min(1.5, beat) * 2)):
                self._release()
        self._beat_prev = beat
        self._idle += seconds
        if self._idle > 1.6:            # a gentle stream in silence
            self._idle = 0.0
            self._release()

        wind = (0.010 + 0.10 * mid) * math.sin(self.time * 0.11)
        for lantern in self._lanterns:
            d = lantern["depth"]
            lantern["y"] += (0.020 + 0.040 * d) * seconds * motion_scale
            sway = (0.004 + 0.060 * mid) * math.sin(self.time * (0.7 + 0.8 * mid) + lantern["phase"])
            lantern["x"] += (wind * d + sway) * seconds * motion_scale
            lantern["flicker"] += (5.0 + 6.0 * bass) * seconds
        self._lanterns = [l for l in self._lanterns if l["y"] < 1.15 and -0.1 < l["x"] < 1.1]

        surf.blit(self._background(W, H), (0, 0))
        horizon = H * _HORIZON
        unit = min(W, H)

        # Stars: faint, twinkling, only in the sky.
        twinkle = 0.55 + 0.45 * np.sin(self.time * 1.3 + self._stars[:, 2] * 40.0)
        for (sx, sy, _), t in zip(self._stars.tolist(), twinkle.tolist()):
            g = int(40 + 90 * t * (1.0 - sy))
            surf.set_at((int(sx * W), int(sy * horizon * 0.95)), (g, g, min(255, g + 30)))

        add = pygame.BLEND_RGB_ADD
        for lantern in sorted(self._lanterns, key=lambda l: l["depth"]):
            d = lantern["depth"]
            x = lantern["x"] * W
            # Far lanterns stay near the horizon; near ones rise across the sky.
            y = horizon - lantern["y"] * horizon * (0.35 + 0.65 * d)
            size = unit * (0.008 + 0.026 * d)
            flame = 0.80 + (0.12 + 0.15 * bass) * math.sin(lantern["flicker"]) + 0.08 * math.sin(lantern["flicker"] * 2.7)
            bright = min(1.0, (0.35 + 0.45 * d) * flame * (1.0 + 0.45 * bass + 0.60 * flash))
            fade = min(1.0, (1.12 - lantern["y"]) * 4.0)          # fade out at the top
            arrive = min(1.0, lantern["y"] * 12.0 + 0.15)         # rise out of the water
            bright *= fade * arrive
            if bright < 0.02:
                continue
            body = hsl(lantern["hue"], 0.95, 0.30 + 0.32 * bright)
            glow = tuple(int(c * 0.45 * bright) for c in hsl(lantern["hue"] + 0.02, 1.0, 0.5))
            sprite = self._glow(size * (2.8 + 2.6 * bass + 1.5 * flash), glow)
            surf.blit(sprite, (int(x - sprite.get_width() / 2), int(y - sprite.get_height() / 2)), special_flags=add)
            w, h = max(2, int(size * 1.05)), max(3, int(size * 1.45))
            rect = pygame.Rect(int(x - w / 2), int(y - h / 2), w, h)
            pygame.draw.rect(surf, body, rect, border_radius=max(1, w // 3))
            core = hsl(lantern["hue"] + 0.04, 1.0, min(0.92, 0.55 + 0.40 * bright))
            pygame.draw.ellipse(surf, core, rect.inflate(-w // 2, -h // 3).move(0, h // 8))
            # The dark rim at the top of the paper shade.
            rim = tuple(int(c * 0.35) for c in body)
            pygame.draw.line(surf, rim, (rect.left + 1, rect.top), (rect.right - 2, rect.top), max(1, h // 8))

            # The reflection: a shimmering column of short strokes below the
            # mirror line, widening and fading with distance from it.
            mirror = horizon + (horizon - y) * 0.30
            if mirror < H:
                strokes = 12
                for i in range(strokes):
                    t = i / (strokes - 1)
                    ry = mirror + (t - 0.3) * size * 5.0
                    if not horizon + 1 < ry < H:
                        continue
                    jitter = math.sin(self.time * 2.1 + i * 1.7 + lantern["phase"]) * size * (0.15 + 0.35 * mid) * (0.4 + t)
                    half = size * (0.35 + 0.55 * t) * (1.0 + 0.3 * math.sin(self.time * 3.3 + i * 2.1))
                    shimmer = 1.0 + 0.9 * high * (0.5 + 0.5 * math.sin(self.time * 9.0 + i * 2.3 + lantern["phase"]))
                    c = tuple(min(255, int(v * 0.75 * bright * shimmer * (1.0 - 0.8 * t))) for v in body)
                    pygame.draw.line(surf, c, (int(x + jitter - half), int(ry)),
                                     (int(x + jitter + half), int(ry)), max(1, int(size * 0.22)))

            # Sparks ride up from the flame with the treble.
            if high > 0.05 and len(self._sparks) < _MAX_SPARKS and rng.random() < 2.0 * high * d * step:
                self._sparks.append([x, y - h / 2, rng.normal(0, 8.0), -rng.uniform(20, 45) * (0.5 + d),
                                     1.0, lantern["hue"] + 0.05])

        alive = []
        for spark in self._sparks:
            spark[0] += spark[2] * seconds
            spark[1] += spark[3] * seconds
            spark[4] -= 0.45 * seconds
            if spark[4] > 0:
                alive.append(spark)
                c = hsl(spark[5], 1.0, 0.25 + 0.5 * spark[4])
                size = 2 + int(unit / 400)
                surf.fill(c, (int(spark[0]), int(spark[1]), size, size))
        self._sparks = alive
