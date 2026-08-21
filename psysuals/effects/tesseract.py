"""Tesseract Bloom — bounded rotating 4-D polytope projections."""
import itertools
import math

import numpy as np
import pygame

import config
from .base import Effect
from .utils import hsl


class Tesseract(Effect):
    MORPH_SCHEMA = {"_morph_projection": (0.0, 1.0)}
    TRAIL_ALPHA = 20
    MAX_EDGES = 220
    MAX_VERTICES = 140
    PROJECTION_DISTANCE = 4.2

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self._rng = np.random.default_rng(config.RNG_SEED)
        self._preset = 0
        self._vertices = np.empty((1, 4), dtype=np.float32)
        self._edges = np.empty((0, 2), dtype=np.int32)
        self._rotated = np.empty_like(self._vertices)
        self._projected = np.empty((1, 3), dtype=np.float32)
        self._build_preset(0)
        self._beat_prev = 0.0
        self._morph_projection = 0.5
        self._hue = float(self._rng.random())

    @staticmethod
    def _tesseract():
        vertices = np.array(list(itertools.product((-1.0, 1.0), repeat=4)), dtype=np.float32)
        edges = [(i, j) for i in range(16) for j in range(i + 1, 16)
                 if (np.asarray(vertices[i]) != np.asarray(vertices[j])).sum() == 1]
        return vertices, np.asarray(edges, dtype=np.int32)

    @staticmethod
    def _cell24():
        vertices = []
        for axis in range(4):
            for sign in (-1.0, 1.0):
                point = np.zeros(4, dtype=np.float32)
                point[axis] = sign
                vertices.append(point)
        vertices.extend(np.asarray(p, dtype=np.float32) * 0.5
                        for p in itertools.product((-1.0, 1.0), repeat=4))
        vertices = np.asarray(vertices, dtype=np.float32)
        distances = np.linalg.norm(vertices[:, None, :] - vertices[None, :, :], axis=2)
        edges = np.argwhere((distances > 0.45) & (distances < 1.05))
        edges = edges[edges[:, 0] < edges[:, 1]][:Tesseract.MAX_EDGES]
        return vertices, edges.astype(np.int32)

    def _cell600_thinned(self):
        vertices = self._rng.normal(size=(120, 4)).astype(np.float32)
        vertices /= np.maximum(np.linalg.norm(vertices, axis=1, keepdims=True), 1e-6)
        distances = np.linalg.norm(vertices[:, None, :] - vertices[None, :, :], axis=2)
        edges = []
        for i in range(len(vertices)):
            nearest = np.argsort(distances[i])[1:4]
            edges.extend((i, int(j)) for j in nearest if i < j)
        return vertices, np.asarray(edges[:self.MAX_EDGES], dtype=np.int32)

    def _build_preset(self, preset):
        if preset == 0:
            vertices, edges = self._tesseract()
        elif preset == 1:
            vertices, edges = self._cell24()
        else:
            vertices, edges = self._cell600_thinned()
        self._vertices = vertices[:self.MAX_VERTICES].astype(np.float32, copy=False)
        self._edges = edges[:self.MAX_EDGES]
        self._rotated = np.empty_like(self._vertices)
        self._projected = np.empty((len(self._vertices), 3), dtype=np.float32)

    def _rotate(self, angle_xw, angle_yz):
        self._rotated[:] = self._vertices
        cx, sx = math.cos(angle_xw), math.sin(angle_xw)
        cy, sy = math.cos(angle_yz), math.sin(angle_yz)
        x = self._rotated[:, 0].copy()
        w = self._rotated[:, 3].copy()
        self._rotated[:, 0] = x * cx - w * sx
        self._rotated[:, 3] = x * sx + w * cx
        y = self._rotated[:, 1].copy()
        z = self._rotated[:, 2].copy()
        self._rotated[:, 1] = y * cy - z * sy
        self._rotated[:, 2] = y * sy + z * cy

    def draw(self, surf, waveform, fft, beat, tick):
        bass = min(max(float(beat), 0.0), 1.5)
        mid = min(max(float(config.MID_ENERGY), 0.0), 4.0)
        high = min(max(float(config.TREBLE_ENERGY), 0.0), 4.0)
        if bass > 0.7 and self._beat_prev <= 0.7:
            self._preset = (self._preset + 1) % 3
            self._build_preset(self._preset)
        self._beat_prev = bass
        self._hue = (self._hue + 0.001 + high * 0.001) % 1.0
        self._rotate(tick * (0.006 + mid * 0.001), tick * (0.009 + high * 0.001))
        w = self._rotated[:, 3]
        denom4 = np.maximum(0.45, self.PROJECTION_DISTANCE - w * (0.8 + self._morph_projection * 0.4))
        self._projected[:, 0] = self._rotated[:, 0] / denom4
        self._projected[:, 1] = self._rotated[:, 1] / denom4
        self._projected[:, 2] = self._rotated[:, 2] / denom4
        denom3 = np.maximum(0.25, 3.2 - self._projected[:, 2])
        scale = min(surf.get_width(), surf.get_height()) * 1.6
        points = np.column_stack((
            surf.get_width() * 0.5 + self._projected[:, 0] / denom3 * scale,
            surf.get_height() * 0.5 + self._projected[:, 1] / denom3 * scale,
        )).astype(np.int32)
        order = sorted(self._edges.tolist(), key=lambda edge: float(self._projected[edge, 2].mean()))
        for start, end in order:
            p0, p1 = points[start], points[end]
            if ((p0[0] < -surf.get_width() and p1[0] < -surf.get_width()) or
                    (p0[0] > surf.get_width() * 2 and p1[0] > surf.get_width() * 2)):
                continue
            depth = float((self._rotated[start, 3] + self._rotated[end, 3]) * 0.25)
            color = hsl((self._hue + depth * 0.08) % 1.0, l=max(0.3, min(0.95, 0.58 + high * 0.08 + bass * 0.12)))
            pygame.draw.line(surf, color, p0, p1, 1 if self._preset else 2)

    def release(self):
        self._vertices = np.empty((0, 4), dtype=np.float32)
        self._edges = np.empty((0, 2), dtype=np.int32)
        self._rotated = np.empty((0, 4), dtype=np.float32)
        self._projected = np.empty((0, 3), dtype=np.float32)
