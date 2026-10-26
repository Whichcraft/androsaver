"""Synapse — neural-network graph with cascading signal propagation.

    A living graph grows and sheds connection points while its nodes wander
    around loose anchors. Nodes are wired to their nearest neighbours. When a
    node fires it sends visible signal pulses down outgoing edges.

  Bass   → signal speed + node glow intensity
  Mid    → auto-fire rate
  Treble → signal colour saturation burst
  Beat   → cascade from 1-4 random nodes
"""
import math

import numpy as np
import pygame

import config
from .base import Effect
from .utils import hsl

_N_NODES       = 55
_EDGES_PER_NODE = 3
_MAX_SIGNALS    = 240
_MIN_NODES     = 28
_MAX_NODES     = 90


class Synapse(Effect):
    TIME_BASED = True
    TRAIL_ALPHA = 42

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self._rng = np.random.default_rng(config.RNG_SEED)
        self._hue = self._rng.random()

        self.n_nodes = _N_NODES
        self.max_signals = _MAX_SIGNALS
        if getattr(config, "LOW_SPEC", False):
            self.n_nodes = max(20, self.n_nodes // 2)
            self.max_signals = max(50, self.max_signals // 2)

        self._nodes: list = []
        self._edges: list = []
        self._outgoing: list = []
        self._init_w = 0
        self._init_h = 0
        self._signals: list = []
        self._glow = [0.0] * self.n_nodes
        self._anchors: list = []
        self._wander_phase = np.zeros(0, dtype=np.float32)
        self._wander_speed = np.zeros(0, dtype=np.float32)
        self._wander_amp = np.zeros(0, dtype=np.float32)
        self._beat_prev  = 0.0
        self._auto_fire_cd = 30
        self._topology_cd = 120
        self._visibility = []
        self._birth_origins = []
        self._retiring = False

    def _random_node(self, W, H):
        ang = float(self._rng.uniform(0, math.tau))
        r = float(self._rng.uniform(0.10, 0.44) * min(W, H))
        return [
            W / 2 + math.cos(ang) * r * float(0.80 + self._rng.uniform(0, 0.40)),
            H / 2 + math.sin(ang) * r * float(0.60 + self._rng.uniform(0, 0.30)),
        ]

    def _rebuild_edges(self):
        """Rewire nearest-neighbour connections after node changes."""
        active = [(tuple(self._edges[ei]), t, speed, hue)
                  for ei, t, speed, hue in self._signals]
        self._edges.clear()
        edge_set = set()
        for i, (xi, yi) in enumerate(self._nodes):
            dists = sorted(
                [(math.hypot(self._nodes[j][0] - xi,
                             self._nodes[j][1] - yi), j)
                 for j in range(self.n_nodes) if j != i]
            )
            for _, j in dists[:_EDGES_PER_NODE]:
                if (i, j) not in edge_set:
                    edge_set.add((i, j))
                    self._edges.append([i, j])
        self._outgoing = [[] for _ in range(self.n_nodes)]
        for ei, (fi, _) in enumerate(self._edges):
            self._outgoing[fi].append(ei)
        # Edge indices held by active signals are no longer valid.
        lookup = {tuple(edge): i for i, edge in enumerate(self._edges)}
        self._signals = [[lookup[edge], t, speed, hue]
                         for edge, t, speed, hue in active if edge in lookup]

    def _mutate_topology(self, W, H, add):
        """Add or remove one live connection point and rewire the graph."""
        low = max(20, _MIN_NODES // 2) if getattr(config, "LOW_SPEC", False) else _MIN_NODES
        high = max(low + 1, _MAX_NODES // 2) if getattr(config, "LOW_SPEC", False) else _MAX_NODES
        if add and self.n_nodes < high:
            mature = [i for i, visibility in enumerate(self._visibility) if visibility >= 0.9]
            candidates = mature or list(range(self.n_nodes))
            weights = np.array([0.1 + self._glow[i] for i in candidates])
            parent = int(self._rng.choice(candidates, p=weights / weights.sum()))
            origin = self._nodes[parent].copy()
            angle = math.atan2(origin[1] - H / 2, origin[0] - W / 2) + float(self._rng.uniform(-1.2, 1.2))
            distance = float(self._rng.uniform(0.05, 0.11)) * min(W, H)
            node = [max(W * 0.06, min(W * 0.94, origin[0] + math.cos(angle) * distance)),
                    max(H * 0.06, min(H * 0.94, origin[1] + math.sin(angle) * distance))]
            self._visibility.append(0.0)
            self._birth_origins.append(origin)
            self._nodes.append(node)
            self._anchors.append(node.copy())
            self._wander_phase = np.append(self._wander_phase, self._rng.uniform(0, math.tau))
            self._wander_speed = np.append(self._wander_speed, self._rng.uniform(0.006, 0.018))
            self._wander_amp = np.append(self._wander_amp, self._rng.uniform(0.012, 0.045) * min(W, H))
            self._glow.append(0.0)
            self.n_nodes += 1
        elif not add and self.n_nodes > low:
            idx = 0  # Oldest node retires first.
            # Preserve signals on surviving edges when node indices shift.
            active = [(self._edges[ei], t, speed, hue) for ei, t, speed, hue in self._signals]
            self._signals = []
            self._edges = [[a - (a > idx), b - (b > idx)]
                           for a, b in self._edges if a != idx and b != idx]
            lookup = {tuple(edge): i for i, edge in enumerate(self._edges)}
            for (a, b), t, speed, hue in active:
                if a != idx and b != idx:
                    edge = (a - (a > idx), b - (b > idx))
                    self._signals.append([lookup[edge], t, speed, hue])
            self._visibility.pop(idx)
            self._birth_origins.pop(idx)
            self._nodes.pop(idx)
            self._anchors.pop(idx)
            self._wander_phase = np.delete(self._wander_phase, idx)
            self._wander_speed = np.delete(self._wander_speed, idx)
            self._wander_amp = np.delete(self._wander_amp, idx)
            self._glow.pop(idx)
            self.n_nodes -= 1
        else:
            return
        self._rebuild_edges()

    def _ensure_nodes(self, W, H):
        if self._nodes and self._init_w == W and self._init_h == H:
            return
        if self._nodes:
            sx, sy = W / self._init_w, H / self._init_h
            for collection in (self._nodes, self._anchors, self._birth_origins):
                for point in collection:
                    point[0] *= sx
                    point[1] *= sy
            self._wander_amp *= min(W, H) / min(self._init_w, self._init_h)
            self._init_w, self._init_h = W, H
            return
        self._init_w, self._init_h = W, H
        self._nodes.clear()
        for i in range(self.n_nodes):
            self._nodes.append(self._random_node(W, H))
        self._anchors = [node.copy() for node in self._nodes]
        self._visibility = [1.0] * self.n_nodes
        self._birth_origins = [node.copy() for node in self._nodes]
        self._retiring = False
        self._signals.clear()
        self._wander_phase = self._rng.uniform(0, math.tau, self.n_nodes).astype(np.float32)
        self._wander_speed = self._rng.uniform(0.006, 0.018, self.n_nodes).astype(np.float32)
        self._wander_amp = (self._rng.uniform(0.012, 0.045, self.n_nodes) * min(W, H)).astype(np.float32)

        self._rebuild_edges()

    # ------------------------------------------------------------------

    def _fire(self, idx, fanout=2):
        if self._visibility[idx] < 0.9 or (idx == 0 and self._retiring):
            return
        self._glow[idx] = 1.0
        h = (self._hue + idx / self.n_nodes * 0.4) % 1.0
        outgoing = self._outgoing[idx]
        if not outgoing or len(self._signals) >= self.max_signals:
            return
        fanout = max(1, min(fanout, len(outgoing)))
        picks = self._rng.choice(outgoing, size=fanout, replace=False)
        for ei in np.atleast_1d(picks):
            if len(self._signals) >= self.max_signals:
                break
            spd = 0.020 + float(self._rng.uniform(0, 0.010))
            self._signals.append([ei, 0.0, spd, h])

    # ------------------------------------------------------------------

    def draw(self, surf, waveform, fft, beat, tick):
        W, H = surf.get_size()
        self._ensure_nodes(W, H)
        step = self._frame_step()
        motion, light = self._audio_response(beat)
        bass, mid, high = motion
        motion_scale, accent = self._profile_response()
        self._hue = (self._hue + (0.003 + mid * 0.002) * step) % 1.0

        # Fade the oldest node out before retiring it; grow two visible branches.
        for i in range(self.n_nodes):
            delta = -step / 60.0 if i == 0 and self._retiring else step / 75.0
            self._visibility[i] = max(0.0, min(1.0, self._visibility[i] + delta))
        if self._retiring and self._visibility[0] <= 1e-8:
            self._mutate_topology(W, H, add=False)
            self._retiring = False
            self._mutate_topology(W, H, add=True)
            self._mutate_topology(W, H, add=True)
        self._topology_cd -= step
        if self._topology_cd <= 0 and not self._retiring:
            self._retiring = True
            self._topology_cd += 180

        # Wander around stable anchors so links breathe without losing the
        # recognizable graph structure.
        self._wander_phase += self._wander_speed * (1.0 + high * 0.25 + bass * 0.1) * step * motion_scale
        for i, node in enumerate(self._nodes):
            phase = float(self._wander_phase[i])
            node[0] = self._anchors[i][0] + math.sin(phase) * float(self._wander_amp[i])
            node[1] = self._anchors[i][1] + math.cos(phase * 0.77) * float(self._wander_amp[i]) * 0.72
            if self._visibility[i] < 1.0 and not (i == 0 and self._retiring):
                progress = self._visibility[i]
                ease = progress * progress * (3 - 2 * progress)
                ox, oy = self._birth_origins[i]
                node[0] = ox + (node[0] - ox) * ease
                node[1] = oy + (node[1] - oy) * ease
            node[0] = max(4.0, min(W - 4.0, node[0]))
            node[1] = max(4.0, min(H - 4.0, node[1]))

        # Beat cascade
        if beat > 0.65 and self._beat_prev <= 0.65:
            for _ in range(int(1 + bass * 3)):
                self._fire(int(self._rng.integers(0, self.n_nodes)), fanout=2 + int(high * 2))
        self._beat_prev = beat

        # Auto-fire
        self._auto_fire_cd -= step
        if self._auto_fire_cd <= 0:
            self._fire(int(self._rng.integers(0, self.n_nodes)), fanout=1)
            self._auto_fire_cd = max(8, int(25 - mid * 15))

        # Advance signals
        spd_mul   = 1.0 + bass * 2.0
        live_sigs = []
        fired_nodes = []
        for sig in self._signals:
            ei, t, spd, h = sig
            t += spd * spd_mul * step
            sig[1] = t
            if t < 1.0:
                live_sigs.append(sig)
            else:
                fired_nodes.append(self._edges[ei][1])
        self._signals = live_sigs
        for idx in fired_nodes[:18]:
            self._fire(idx, fanout=1 + int(mid * 2))

        # Decay glow
        for i in range(self.n_nodes):
            self._glow[i] = max(0.0, self._glow[i] - 0.035 * step)

        # Draw edges (dim background wiring)
        for fi, ti in self._edges:
            xf, yf = self._nodes[fi]
            xt, yt = self._nodes[ti]
            h   = (self._hue + fi / self.n_nodes * 0.3) % 1.0
            visibility = min(self._visibility[fi], self._visibility[ti])
            lum = (0.06 + self._glow[fi] * 0.14) * visibility
            xt = xf + (xt - xf) * visibility
            yt = yf + (yt - yf) * visibility
            pygame.draw.line(surf, hsl(h, l=lum),
                             (int(xf), int(yf)), (int(xt), int(yt)), 1)

        # Draw travelling signals
        for ei, t, _, h in self._signals:
            fi, ti = self._edges[ei]
            xf, yf = self._nodes[fi]
            xt, yt = self._nodes[ti]
            sx = int(xf + (xt - xf) * t)
            sy = int(yf + (yt - yf) * t)
            bright = min(0.95, 0.55 + light[0] * 0.2 * accent + light[2] * 0.1) * min(self._visibility[fi], self._visibility[ti])
            r_px   = max(2, int(3 + bass * 2))
            pygame.draw.circle(surf, hsl(h, l=bright * 0.30),
                               (sx, sy), r_px + 3)
            pygame.draw.circle(surf, hsl(h, l=bright),
                               (sx, sy), r_px)

        # Draw nodes
        for i, (x, y) in enumerate(self._nodes):
            g = self._glow[i] * self._visibility[i]
            h = (self._hue + i / self.n_nodes * 0.4) % 1.0
            if g > 0.05:
                r_px = max(2, int(4 + g * 8 + bass * 4))
                pygame.draw.circle(surf, hsl(h, l=g * 0.22), (int(x), int(y)),
                                   r_px + 3)
                pygame.draw.circle(surf, hsl(h, l=g * 0.70), (int(x), int(y)),
                                   r_px)
            else:
                pygame.draw.circle(surf, hsl(h, l=0.10 * self._visibility[i]), (int(x), int(y)), 2)
