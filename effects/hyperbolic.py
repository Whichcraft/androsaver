"""Hyperbolic Cathedral — bounded Poincare-disk-inspired tiling."""
import math

import numpy as np
import pygame

import config
from .base import Effect
from .utils import hsl


class Hyperbolic(Effect):
    MORPH_SCHEMA = {"_morph_warp": (0.0, 1.0)}
    TRAIL_ALPHA = 42
    MAX_GENERATIONS = 5
    MAX_TILES = 160
    ARC_SEGMENTS = 4

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self._rng = np.random.default_rng(config.RNG_SEED)
        self._size = (0, 0)
        self._tiles = []
        self._hue = float(self._rng.random())
        self._shock = 0.0
        self._morph_warp = 0.5
        self._beat_prev = 0.0
        self._reset_geometry(max(1, config.WIDTH), max(1, config.HEIGHT))

    def _reset_geometry(self, W, H):
        self._size = (W, H)
        self._tiles = []
        # Concentric generations approximate a regular hyperbolic tiling while
        # keeping topology small enough for the CPU renderer.
        for generation in range(self.MAX_GENERATIONS + 1):
            count = 6 * (generation + 1)
            radial = 0.10 + generation * 0.135
            radius = 0.105 / (1.0 + generation * 0.62)
            for index in range(count):
                angle = math.tau * index / count + generation * 0.13
                center = complex(math.cos(angle) * radial,
                                 math.sin(angle) * radial)
                self._tiles.append((center, radius, 6, generation))
                if len(self._tiles) >= self.MAX_TILES:
                    return

    @staticmethod
    def _mobius(z, offset):
        denominator = 1.0 - np.conj(offset) * z
        if abs(denominator) < 1e-4:
            return z
        return (z - offset) / denominator

    def _project(self, z, W, H):
        radius = min(W, H) * 0.47
        return int(W * 0.5 + z.real * radius), int(H * 0.5 + z.imag * radius)

    def draw(self, surf, waveform, fft, beat, tick):
        W, H = surf.get_size()
        if (W, H) != self._size:
            self._reset_geometry(W, H)

        bass = min(max(float(beat), 0.0), 1.5)
        mid = min(max(float(config.MID_ENERGY), 0.0), 4.0)
        high = min(max(float(config.TREBLE_ENERGY), 0.0), 4.0)
        bpm = min(max(float(getattr(config, "BPM", 0.0)), 0.0), 240.0)
        if bass > 0.7 and self._beat_prev <= 0.7:
            self._shock = min(1.0, self._shock + 0.65)
        self._beat_prev = bass
        self._shock *= 0.93
        self._hue = (self._hue + 0.001 + mid * 0.0008 + bpm * 0.000002) % 1.0

        offset_radius = min(0.28, 0.035 + mid * 0.025 + self._shock * 0.07 + self._morph_warp * 0.02)
        offset_angle = tick * (0.004 + bpm * 0.00001)
        offset = complex(math.cos(offset_angle), math.sin(offset_angle)) * offset_radius

        surf.fill((2, 1, 8))
        pygame.draw.circle(surf, (22, 8, 38), (W // 2, H // 2), int(min(W, H) * 0.47), 1)
        for center, tile_radius, sides, generation in self._tiles:
            phase = tick * (0.003 + generation * 0.0007) + generation * 0.4
            points = []
            for side in range(sides + 1):
                angle = phase + math.tau * side / sides
                vertex = center + tile_radius * complex(math.cos(angle), math.sin(angle))
                transformed = self._mobius(vertex, offset)
                points.append(transformed)
            screen_points = []
            for start, end in zip(points, points[1:]):
                for segment in range(self.ARC_SEGMENTS):
                    mix = segment / self.ARC_SEGMENTS
                    z = start * (1.0 - mix) + end * mix
                    if abs(z) < 1.05:
                        screen_points.append(self._project(z, W, H))
            if abs(points[-1]) < 1.05:
                screen_points.append(self._project(points[-1], W, H))
            if len(screen_points) < 2:
                continue
            brightness = max(0.22, min(0.95, 0.30 + high * 0.08 + self._shock * 0.35 - generation * 0.025))
            color = hsl((self._hue + generation * 0.075) % 1.0, l=brightness)
            pygame.draw.lines(surf, color, False, screen_points, max(1, 2 - generation // 3))

    def release(self):
        self._tiles = []
