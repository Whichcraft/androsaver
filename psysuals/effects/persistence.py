"""Persistence of Vision — stroboscopic rotating geometric 3D wireframe models.

Multiple nested 3D models (Tetrahedron, Octahedron, Cube, Icosahedron, Dodecahedron)
rotate in 3D space at subtly different speeds on different axes. With long trail persistence,
their ghost images accumulate in perspective, creating a glowing 3D wagon-wheel moiré and
holographic mandala.

  Bass   → rotation speed burst
  Mid    → number of shapes / model complexity
  Treble → strobe flash
  Beat   → speed spike + hue jump
"""
import math
import numpy as np
import pygame

import config
from .base import Effect
from .utils import hsl

_MAX_SHAPES = 8


class Persistence(Effect):
    MORPH_SCHEMA = {"_morph_projection": (0.0, 1.0)}
    TRAIL_ALPHA = 38
    RES_DIV     = 2

    @staticmethod
    def _Rx(a):
        c, s = math.cos(a), math.sin(a)
        return np.array([[1.0, 0.0, 0.0], [0.0, c, -s], [0.0, s, c]], dtype=float)

    @staticmethod
    def _Ry(a):
        c, s = math.cos(a), math.sin(a)
        return np.array([[c, 0.0, s], [0.0, 1.0, 0.0], [-s, 0.0, c]], dtype=float)

    @staticmethod
    def _Rz(a):
        c, s = math.cos(a), math.sin(a)
        return np.array([[c, -s, 0.0], [s, c, 0.0], [0.0, 0.0, 1.0]], dtype=float)

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self._rng = np.random.default_rng(config.RNG_SEED)
        self._hue = 0.0
        # Initialize 3D rotation angles for each shape
        self._rot_x = self._rng.uniform(0, math.tau, _MAX_SHAPES).tolist()
        self._rot_y = self._rng.uniform(0, math.tau, _MAX_SHAPES).tolist()
        self._rot_z = self._rng.uniform(0, math.tau, _MAX_SHAPES).tolist()
        self._rot_w = self._rng.uniform(0, math.tau, _MAX_SHAPES).tolist()
        
        # Non-coplanar speed ratios for complex 3D orbital dynamics
        self._speeds_x = [0.008 * (1 + i * 0.12) for i in range(_MAX_SHAPES)]
        self._speeds_y = [0.012 * (1 + i * 0.08) for i in range(_MAX_SHAPES)]
        self._speeds_z = [0.015 * (1 + i * 0.15) for i in range(_MAX_SHAPES)]
        
        self._boost = 0.0
        self._morph_projection = 0.5
        self._beat_prev = 0.0
        self._scaled = None

        # Build Platonic solid wireframe geometry
        phi = (1.0 + math.sqrt(5.0)) / 2.0
        inv_phi = 1.0 / phi

        raw_models = [
            # 1. Tetrahedron
            [
                [1.0, 1.0, 1.0],
                [-1.0, -1.0, 1.0],
                [-1.0, 1.0, -1.0],
                [1.0, -1.0, -1.0]
            ],
            # 2. Octahedron
            [
                [1.0, 0.0, 0.0], [-1.0, 0.0, 0.0],
                [0.0, 1.0, 0.0], [0.0, -1.0, 0.0],
                [0.0, 0.0, 1.0], [0.0, 0.0, -1.0]
            ],
            # 3. Cube
            [
                [-1.0, -1.0, -1.0], [1.0, -1.0, -1.0], [1.0, 1.0, -1.0], [-1.0, 1.0, -1.0],
                [-1.0, -1.0, 1.0], [1.0, -1.0, 1.0], [1.0, 1.0, 1.0], [-1.0, 1.0, 1.0]
            ],
            # 4. Icosahedron
            [
                [0.0, -1.0, -phi], [0.0, -1.0, phi], [0.0, 1.0, -phi], [0.0, 1.0, phi],
                [-1.0, -phi, 0.0], [-1.0, phi, 0.0], [1.0, -phi, 0.0], [1.0, phi, 0.0],
                [-phi, 0.0, -1.0], [-phi, 0.0, 1.0], [phi, 0.0, -1.0], [phi, 0.0, 1.0]
            ],
            # 5. Dodecahedron
            [
                [-1.0, -1.0, -1.0], [1.0, -1.0, -1.0], [1.0, 1.0, -1.0], [-1.0, 1.0, -1.0],
                [-1.0, -1.0, 1.0], [1.0, -1.0, 1.0], [1.0, 1.0, 1.0], [-1.0, 1.0, 1.0],
                [0.0, -inv_phi, -phi], [0.0, -inv_phi, phi], [0.0, inv_phi, -phi], [0.0, inv_phi, phi],
                [-inv_phi, -phi, 0.0], [-inv_phi, phi, 0.0], [inv_phi, -phi, 0.0], [inv_phi, phi, 0.0],
                [-phi, 0.0, -inv_phi], [-phi, 0.0, inv_phi], [phi, 0.0, -inv_phi], [phi, 0.0, inv_phi]
            ]
        ]

        self._models = []
        for rm in raw_models:
            verts = np.array(rm, dtype=float)
            # Normalize vertices to the unit sphere
            norms = np.linalg.norm(verts, axis=1, keepdims=True)
            norms[norms == 0.0] = 1.0
            verts = verts / norms
            
            # Compute edges based on minimum distance
            n_verts = len(verts)
            dists = []
            for i in range(n_verts):
                for j in range(i + 1, n_verts):
                    d_val = np.linalg.norm(verts[i] - verts[j])
                    dists.append((d_val, i, j))
            min_dist = min(d[0] for d in dists)
            
            edges = []
            threshold = min_dist * 1.05
            for d_val, i, j in dists:
                if d_val <= threshold:
                    edges.append((i, j))
            self._models.append((verts, edges))

    def draw(self, surf, waveform, fft, beat, tick):
        W, H, RD = self._render_size()
        bass = beat
        mid  = config.MID_ENERGY
        high = config.TREBLE_ENERGY

        if self._scaled is None or self._scaled.get_size() != (W, H):
            self._scaled = pygame.Surface((W, H))
        self._scaled.fill((0, 0, 0))

        self._hue = (self._hue + 0.003 + mid * 0.003) % 1.0
        self._boost = max(0.0, self._boost - 0.04)

        if bass > 0.75 and self._beat_prev <= 0.75:
            self._boost = 1.5 + bass * 1.0
        self._beat_prev = bass

        cx, cy = W // 2, H // 2
        # This is the camera focal scale, not the model radius. A 0.40*H
        # scale leaves the projected solids visually undersized because of
        # perspective depth. Use a substantially wider focal scale so the
        # outer figures occupy most of the viewport height.
        base_r = H * (0.90 + self._morph_projection * 0.10)
        fov = base_r
        spd_mul = 1.0 + bass * 2.5 + self._boost

        n_shapes = max(3, min(_MAX_SHAPES, 3 + int(mid * 5)))

        for i in range(n_shapes):
            # Update 3D rotation angles
            self._rot_x[i] += self._speeds_x[i] * spd_mul
            self._rot_y[i] += self._speeds_y[i] * spd_mul
            self._rot_z[i] += self._speeds_z[i] * spd_mul
            self._rot_w[i] += (0.006 + high * 0.004) * spd_mul

            ax, ay, az = self._rot_x[i], self._rot_y[i], self._rot_z[i]
            r = 0.2 + 0.8 * (i + 1) / n_shapes
            h = (self._hue + i / n_shapes * 0.55) % 1.0
            bright = 0.28 + bass * 0.25 + (high * 0.12 if i == n_shapes - 1 else 0)

            # Get the model and apply rotations
            model_idx = i % len(self._models)
            u_verts, edges = self._models[model_idx]
            
            R = self._Rx(ax) @ self._Ry(ay) @ self._Rz(az)
            scaled_verts = u_verts * r
            rotated_verts = (R @ scaled_verts.T).T

            # Lightweight fourth-coordinate rotation adds an inside-out
            # projection accent while retaining the existing 3-D solids.
            rw = self._rot_w[i]
            w4 = rotated_verts[:, 0] * math.sin(rw) + rotated_verts[:, 2] * math.cos(rw)
            x4 = rotated_verts[:, 0] * math.cos(rw) - rotated_verts[:, 2] * math.sin(rw)
            depth4 = np.maximum(0.35, 2.8 - w4)
            rotated_verts[:, 0] = x4 / depth4
            rotated_verts[:, 1] /= depth4
            rotated_verts[:, 2] /= depth4

            projected = []
            depths = []

            for v in rotated_verts:
                # Camera distance and perspective projection
                cam_z = 2.2
                depth = cam_z + v[2]
                if depth <= 0.1:
                    depth = 0.1
                sx = cx + int(v[0] * fov / depth)
                sy = cy + int(v[1] * fov / depth)
                projected.append((sx, sy))
                depths.append(v[2])

            # Draw edge segments with depth fading
            for edge_idx, (a, b) in enumerate(edges):
                p1 = projected[a]
                p2 = projected[b]

                # Compute depth factor (Z ranges from -r to +r, mapping to [0.15, 1.0])
                avg_z = (depths[a] + depths[b]) / 2.0
                z_norm = avg_z / r
                d_factor = 0.15 + 0.85 * (1.0 - (z_norm + 1.0) / 2.0)
                d_factor = max(0.15, min(1.0, d_factor))

                col = hsl(h, l=bright * d_factor)
                glow = hsl(h, l=bright * 0.30 * d_factor)
                thickness = max(1, int(4 * d_factor))

                # Double-pass drawing for neon/glowing effect
                pygame.draw.line(self._scaled, glow, p1, p2, thickness + 2)
                pygame.draw.line(self._scaled, col, p1, p2, thickness)

        # Scale up to full resolution
        scaled = pygame.transform.smoothscale(self._scaled, surf.get_size())
        surf.blit(scaled, (0, 0))
