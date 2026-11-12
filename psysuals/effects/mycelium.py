"""Mycelium — a violet hyphal web that carries travelling light packets."""
import math

import numpy as np
import pygame

from .base import Effect
from .utils import _hsl_batch


class Mycelium(Effect):
    """Grow branching filaments, germinate spores and route pulses."""

    TRAIL_ALPHA = 0
    MAX_EDGES = 560
    MAX_TIPS = 220
    MAX_PULSES = 260
    PULSE_TARGET = 48
    MAX_SPORES = 160
    MAX_NODES = 2400
    BODY_COUNT = 3

    MAX_DEPTH = 14
    EDGE_LIFETIME = 1500.0
    MAX_RINGS = 8
    BLOOM_PIXELS = 128
    GROUND = (9, 7, 24)

    # The colony stays inside one violet-magenta family.
    HUE_SEED_LO = 0.70
    HUE_SEED_HI = 0.80
    HUE_LO = 0.66
    HUE_HI = 0.84
    BODY_HUE_STEP = 0.05
    BRANCH_HUE_STEP = 0.06
    SPORE_HUE_STEP = 0.04
    PULSE_HUE = 0.52
    AMBER_HUE = 0.09

    # The colony is grown, not sampled: bodies, root tips and a warm-up.
    ROOT_TIPS_PER_BODY = 4
    WARMUP_STEPS = 45
    PULSE_COOLDOWN = 12
    KEEPALIVE_FRAMES = 60
    PRUNE_BUDGET = 4
    GROWTH_TIPS = 64
    CROSS_LINK_P = 0.35

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self._hue = float(self.rng.uniform(self.HUE_SEED_LO, self.HUE_SEED_HI))
        self._phase = 0.0
        self._nodes: list = []
        self._node_edges: list = []
        self._edges: list = []
        self._free_nodes: list = []
        self._free_edges: list = []
        self._tips: list = []
        self._pulses: list = []
        self._spores: list = []
        self._bodies: list = []
        self._rings: list = []
        self._sparks: list = []
        self._motion_field = None
        self._bass_prev = 0.0
        self._beat_prev = 0.0
        self._last_emit = -self.PULSE_COOLDOWN
        self._frames = 0
        self._W, self._H = self.frame.width, self.frame.height
        self._bloom: list = []
        self._bloom_scaled: list = []
        self._warmup = False
        self._growing: list = []

        self.max_edges = self.MAX_EDGES
        self.max_tips = self.MAX_TIPS
        self.max_pulses = self.MAX_PULSES
        self.max_spores = self.MAX_SPORES
        if self.options.low_spec:
            self.max_edges //= 2
            self.max_tips //= 2
            self.max_pulses //= 2
            self.max_spores //= 2

        self._seed_colony()
        self._build_bloom()
        # The colony starts alive: the bodies light their first filaments.
        self._emit_pulses(0.0, 0.5)

    # ------------------------------------------------------------------
    # construction
    # ------------------------------------------------------------------
    def _composite_hue(self, hue_off, depth=0.0):
        """Fold an offset and a generation into the colony's violet band."""
        hue = self._hue + hue_off + depth * 0.02
        span = self.HUE_HI - self.HUE_LO
        if span <= 0.0:
            return self.HUE_LO
        return self.HUE_LO + (hue - self.HUE_LO) % span

    def _seed_colony(self):
        """Grow the seed colony: three bodies, root tips and a warm-up.

        The colony is grown with the same rules as the live growth, so the
        first frame already shows a curved, tapered hyphal web instead of a
        synthetic mesh.
        """
        width, height = self._W, self._H
        if width <= 0 or height <= 0:
            return
        self._place_bodies(width, height)
        if not self._bodies:
            return
        self._seed_root_tips()
        self._warmup = True
        try:
            for _ in range(self.WARMUP_STEPS):
                self._grow(1.0, 0.35, 0.45, 0.0, warmup=True)
        finally:
            self._warmup = False
        self._assign_cadence()

    def _place_bodies(self, width, height):
        """Anchor the fruiting bodies on seeded positions around the centre."""
        limit = min(width, height)
        base_angle = float(self.rng.uniform(0.0, math.tau))
        for index in range(self.BODY_COUNT):
            angle = (base_angle + index * (math.tau / self.BODY_COUNT)
                     + float(self.rng.uniform(-0.2, 0.2)))
            radius = limit * float(self.rng.uniform(0.14, 0.26))
            target_x = width / 2.0 + math.cos(angle) * radius
            target_y = height / 2.0 + math.sin(angle) * radius
            node = self._alloc_node(target_x, target_y)
            if node is None:
                continue
            self._bodies.append([node, index * self.BODY_HUE_STEP, 0.0])

    def _seed_root_tips(self):
        """Give every body four root tips that grow away from the centre."""
        width, height = self._W, self._H
        centre_x, centre_y = width / 2.0, height / 2.0
        for index, (node_index, hue_off, _) in enumerate(self._bodies):
            position = self._nodes[node_index]
            if position is None:
                continue
            outward = math.atan2(position[1] - centre_y, position[0] - centre_x)
            spread = self.ROOT_TIPS_PER_BODY
            for slot in range(spread):
                offset = (slot - (spread - 1) / 2.0) * 0.55
                angle = outward + offset + float(self.rng.uniform(-0.12, 0.12))
                self._tips.append([node_index, angle, 0, hue_off, 1.0,
                                   index, 0, 0])

    def _assign_cadence(self):
        """Stagger the live growth: every tip gets its own phase."""
        cadence = self._cadence(0.0)
        for tip in self._tips:
            tip[6] = cadence
            tip[7] = int(self.rng.integers(0, max(1, cadence)))

    def _build_bloom(self):
        """Precompute one radial falloff sprite per body."""
        size = self.BLOOM_PIXELS
        axis = np.arange(size, dtype=np.float32) - (size - 1) / 2.0
        grid_x, grid_y = np.meshgrid(axis, axis)
        radius = np.hypot(grid_x, grid_y) / (size / 2.0)
        falloff = np.clip(1.0 - radius, 0.0, 1.0) ** 2.6
        self._bloom = []
        for _, hue_off, _ in self._bodies:
            tint = _hsl_batch(np.array([self._composite_hue(hue_off)]),
                              np.array([0.72]), np.array([0.30]))[0]
            # The sprite is blitted with BLEND_RGB_ADD, so the falloff is
            # already carried by the colour magnitude.
            rgb = (tint.astype(np.float32)[None, None, :]
                   * falloff[:, :, None] * 0.55).astype(np.uint8)
            sprite = pygame.Surface((size, size))
            pygame.surfarray.blit_array(sprite, rgb.transpose(1, 0, 2))
            self._bloom.append(sprite)
        self._bloom_scaled = []

    # ------------------------------------------------------------------
    # pools
    # ------------------------------------------------------------------
    def _alloc_node(self, x, y):
        if self._free_nodes:
            index = self._free_nodes.pop()
            self._nodes[index] = [float(x), float(y)]
            self._node_edges[index] = []
            return index
        if len(self._nodes) >= self.MAX_NODES:
            return None
        index = len(self._nodes)
        self._nodes.append([float(x), float(y)])
        self._node_edges.append([])
        return index

    def _free_node(self, index):
        if index is None or self._nodes[index] is None:
            return
        self._nodes[index] = None
        self._node_edges[index] = []
        self._free_nodes.append(index)

    def _alloc_edge(self, a, b, hue_off, depth, body_index=0):
        if not self._free_edges and len(self._edges) >= self.max_edges:
            if self._warmup:
                # The seed colony stops at the pool limit; it is never
                # thinned while it grows, so it stays one connected web.
                return None
            position = self._nodes[b]
            if position is None or not self._free_near(position):
                return None  # the web has no spare filament; growth waits
        if self._free_edges:
            index = self._free_edges.pop()
        elif len(self._edges) < self.max_edges:
            index = len(self._edges)
            self._edges.append(None)
        else:
            return None
        lifetime = self.EDGE_LIFETIME * float(self.rng.uniform(0.75, 1.25))
        self._edges[index] = [a, b, 0.0, lifetime, float(hue_off) % 1.0,
                             int(depth), int(body_index)]
        self._node_edges[a].append(index)
        self._node_edges[b].append(index)
        return index

    def _free_edge(self, index):
        edge = self._edges[index]
        if edge is None:
            return
        a, b = edge[0], edge[1]
        for node in (a, b):
            adjacency = self._node_edges[node]
            if index in adjacency:
                adjacency.remove(index)
        self._edges[index] = None
        self._free_edges.append(index)
        if self._pulses:
            self._rehome_pulses(index, b)
        for node in (a, b):
            if not self._node_edges[node] and not self._node_in_use(node):
                self._free_node(node)

    def _rehome_pulses(self, index, far_node):
        """Carry the light of a pruned filament over to a neighbouring one."""
        survivors = []
        for pulse in self._pulses:
            if pulse[0] != index:
                survivors.append(pulse)
                continue
            candidates = [c for c in self._node_edges[far_node]
                          if c != index and self._edges[c] is not None]
            if not candidates:
                continue  # the packet dies with its filament
            chosen = candidates[int(self.rng.integers(0, len(candidates)))]
            self._orient_edge(chosen, far_node)
            pulse[0] = chosen
            pulse[1] = 0.0
            survivors.append(pulse)
        self._pulses = survivors

    def _free_near(self, position):
        """Trim the leaf filament closest to a growing tip.

        Trimming a leaf keeps the web connected, so the colony renews
        itself where it grows instead of tearing the net apart. A leaf that
        carries a tip gives the tip back to its parent, which then regrows
        the filament.
        """
        protected = self._tip_nodes()
        best = None
        best_leaf = None
        best_distance = None
        best_priority = 2
        for index, edge in enumerate(self._edges):
            if edge is None:
                continue
            a, b = edge[0], edge[1]
            if len(self._node_edges[b]) <= 1:
                far = b
            elif len(self._node_edges[a]) <= 1:
                far = a
            else:
                continue
            node = self._nodes[far]
            if node is None:
                continue
            priority = 1 if far in protected else 0
            distance = (node[0] - position[0]) ** 2 + (node[1] - position[1]) ** 2
            if (best is None or priority < best_priority
                    or (priority == best_priority and distance < best_distance)):
                best = index
                best_leaf = far
                best_distance = distance
                best_priority = priority
        if best is None:
            return False
        if best_priority:
            self._reroot_tip(self._edges[best], best_leaf)
        self._free_edge(best)
        return True

    def _tip_nodes(self):
        """The nodes that carry a tip, including the tips of this growth."""
        nodes = {tip[0] for tip in self._tips}
        nodes.update(tip[0] for tip in self._growing)
        return nodes

    def _reroot_tip(self, edge, leaf):
        """A trimmed filament hands its tip back to the parent node."""
        a, b = edge[0], edge[1]
        other = b if leaf == a else a
        for tip in self._tips:
            if tip[0] == leaf:
                tip[0] = other

    def _node_in_use(self, index):
        for body in self._bodies:
            if body[0] == index:
                return True
        for tip in self._tips:
            if tip[0] == index:
                return True
        for tip in self._growing:
            if tip[0] == index:
                return True
        return False

    def _orient_edge(self, index, start_node):
        """Make `start_node` the first endpoint so pulses travel from it."""
        edge = self._edges[index]
        if edge is None:
            return
        if edge[0] != start_node and edge[1] == start_node:
            edge[0], edge[1] = edge[1], edge[0]

    # ------------------------------------------------------------------
    # simulation
    # ------------------------------------------------------------------
    def _cadence(self, bass):
        """Frames between two growth steps of one tip.

        The base cadence is the planned one; it stretches with the size of
        the colony, so a big web renews gradually instead of churning.
        """
        base = max(45, 190 - int(bass * 70))
        load = max(1.0, len(self._tips) / float(self.GROWTH_TIPS))
        return int(base * load)

    def _grow(self, step, mid, treble, bass, warmup=False):
        width, height = self._W, self._H
        limit = min(width, height)
        cadence = self._cadence(bass)
        branch_p = 0.10 + mid * 0.18 + treble * 0.10
        spore_p = 0.02 + treble * 0.05
        field = self._motion_field
        body_positions = []
        for node_index, _, _ in self._bodies:
            position = self._nodes[node_index]
            body_positions.append((position[0], position[1]) if position
                                  else (width / 2.0, height / 2.0))
        next_tips = []
        sparks = []
        self._growing = next_tips
        for tip in list(self._tips):
            node_index, angle, depth, hue_off, energy, body_index, _, phase = tip
            position = self._nodes[node_index] if node_index is not None else None
            if position is None:
                continue
            # The tip survives this frame and enters the next generation
            # exactly once. Every path below either grows it or leaves it
            # alone; none of them appends it a second time.
            next_tips.append(tip)
            if depth >= self.MAX_DEPTH:
                continue  # a mature branch stays in the network, it stops growing
            if warmup and energy <= 0.0:
                continue  # a branch marked during the warm-up waits its turn
            if not warmup:
                tip[6] = cadence
                tip[7] = phase % max(1, cadence)
                phase = tip[7]
                if (self._frames + phase) % cadence != 0:
                    # The tip waits for its turn; it stays in the network
                    # exactly once and does not grow on this frame.
                    continue
            x, y = position
            field_x = (math.sin(y * 0.008 + self._phase * 1.1)
                       + 0.6 * math.cos((x + y) * 0.005 - self._phase * 0.7))
            field_y = (math.cos(x * 0.009 - self._phase * 0.9)
                       + 0.6 * math.sin((x - y) * 0.005 + self._phase * 0.6))
            magnitude = math.hypot(field_x, field_y)
            if magnitude > 1e-9:
                field_x /= magnitude
                field_y /= magnitude
            else:
                field_x, field_y = 1.0, 0.0
            if 0 <= body_index < len(body_positions):
                body_x, body_y = body_positions[body_index]
            else:
                body_x, body_y = width / 2.0, height / 2.0
            dx, dy = x - body_x, y - body_y
            distance = math.hypot(dx, dy)
            swirl_x = swirl_y = 0.0
            if distance > 1e-6:
                swirl = 0.10 + mid * 0.22
                swirl_x = -dy / distance * swirl
                swirl_y = dx / distance * swirl
            coh_x = coh_y = 0.0
            if distance > 1.00 * limit and distance > 1e-6:
                coh_x = -dx / distance * 0.25
                coh_y = -dy / distance * 0.25
            head_x, head_y = math.cos(angle), math.sin(angle)
            dir_x = 0.45 * head_x + 0.50 * field_x + swirl_x + coh_x
            dir_y = 0.45 * head_y + 0.50 * field_y + swirl_y + coh_y
            if field is not None:
                vx, vy = field
                rows, cols = vx.shape
                row = min(rows - 1, max(0, int(y / max(1, height) * rows)))
                col = min(cols - 1, max(0, int(x / max(1, width) * cols)))
                dir_x += 0.04 * float(np.clip(vx[row, col] - vy[row, col], -2.0, 2.0))
            norm = math.hypot(dir_x, dir_y)
            if norm > 1e-9:
                dir_x /= norm
                dir_y /= norm
            else:
                dir_x, dir_y = head_x, head_y
            new_angle = math.atan2(dir_y, dir_x)
            jitter = float(self.rng.normal(0.0, 0.05 + mid * 0.10 + treble * 0.05))
            new_angle += max(-0.6, min(0.6, jitter))
            length = max(6.0, (16.0 + bass * 10.0) * (1.0 - min(depth, 8) * 0.03))
            new_x = x + math.cos(new_angle) * length
            new_y = y + math.sin(new_angle) * length
            if new_x < 0.0:
                new_x = -new_x
                new_angle = math.pi - new_angle
            elif new_x > width:
                new_x = 2.0 * width - new_x
                new_angle = math.pi - new_angle
            if new_y < 0.0:
                new_y = -new_y
                new_angle = -new_angle
            elif new_y > height:
                new_y = 2.0 * height - new_y
                new_angle = -new_angle
            new_x = min(max(new_x, 0.0), width)
            new_y = min(max(new_y, 0.0), height)
            head = self._alloc_node(new_x, new_y)
            if head is not None:
                edge = self._alloc_edge(node_index, head, hue_off, depth,
                                        body_index)
                if edge is None:
                    self._free_node(head)
                    head = None
            if head is not None:
                tip[0] = head
                tip[1] = new_angle
                tip[4] = min(1.0, energy * 0.96 + 0.04)
                sparks.append((new_x, new_y))
                if self.rng.random() < self.CROSS_LINK_P:
                    # A loop in the net: the web survives the loss of a filament.
                    self._link_neighbour(head, node_index, new_x, new_y,
                                         length * 2.8, hue_off, depth,
                                         body_index)
                if (self.rng.random() < spore_p
                        and len(self._spores) < self.max_spores):
                    life = int(self.rng.integers(240, 601))
                    self._spores.append([
                        new_x, new_y, 0.0, 0.0, life, life,
                        (hue_off + self.SPORE_HUE_STEP) % 1.0,
                        float(self.rng.uniform(1.5, 3.5)),
                    ])
            if (head is not None and self.rng.random() < branch_p
                    and len(next_tips) < self.max_tips):
                sign = 1.0 if self.rng.random() < 0.5 else -1.0
                offset = float(self.rng.uniform(0.4, 0.9))
                child_phase = int(self.rng.integers(0, max(1, cadence)))
                # During the warm-up a branch is only marked, not grown: the
                # seed colony keeps its long filaments, and the branch opens
                # once the live growth starts.
                child_energy = 0.0 if warmup else tip[4] * 0.9
                next_tips.append([head, new_angle + sign * offset, depth + 1,
                                  (hue_off + self.BRANCH_HUE_STEP) % 1.0,
                                  child_energy, body_index, cadence, child_phase])
        # The tip population is capped: every surviving tip is counted exactly
        # once, so the network can never double its tips within a frame.
        self._tips = next_tips[:self.max_tips]
        # The growing list never holds more entries than the tip list.
        self._growing = self._tips
        self._sparks = sparks
        self._reseed_tips()

    def _link_neighbour(self, head, parent, x, y, radius, hue_off, depth,
                        body_index=0):
        """Join a fresh tip to the nearest other filament; the web gains a loop."""
        limit = radius * radius
        best = None
        best_distance = limit
        for index, node in enumerate(self._nodes):
            if node is None or index == head or index == parent:
                continue
            distance = (node[0] - x) ** 2 + (node[1] - y) ** 2
            if distance < best_distance:
                best_distance = distance
                best = index
        if best is None:
            return
        self._alloc_edge(head, best, hue_off, depth, body_index)

    def _reseed_tips(self):
        """Keep a minimum of root tips alive on the fruiting bodies."""
        if len(self._tips) >= self.BODY_COUNT or not self._bodies:
            return
        for index, (node_index, hue_off, _) in enumerate(self._bodies):
            if len(self._tips) >= self.BODY_COUNT:
                break
            if self._nodes[node_index] is None:
                continue
            angle = float(self.rng.uniform(0.0, math.tau))
            cadence = self._cadence(0.0)
            phase = int(self.rng.integers(0, max(1, cadence)))
            self._tips.append([node_index, angle, 0, hue_off, 1.0, index,
                               cadence, phase])

    def _prune(self):
        """Age every filament; a backbone filament is renewed, not torn out."""
        budget = self.PRUNE_BUDGET
        protected = {tip[0] for tip in self._tips}
        for index, edge in enumerate(self._edges):
            if edge is None:
                continue
            edge[2] += 1.0
            if edge[2] < edge[3] or budget <= 0:
                continue
            budget -= 1
            a, b = edge[0], edge[1]
            if len(self._node_edges[b]) <= 1 and b not in protected:
                self._free_edge(index)
            elif len(self._node_edges[a]) <= 1 and a not in protected:
                self._free_edge(index)
            else:
                edge[2] = 0.0
                edge[3] = self.EDGE_LIFETIME * float(self.rng.uniform(0.75, 1.25))

    def _nearest_body(self, x, y):
        best = 0
        best_distance = None
        for index, (node_index, _, _) in enumerate(self._bodies):
            position = self._nodes[node_index]
            if position is None:
                continue
            distance = math.hypot(position[0] - x, position[1] - y)
            if best_distance is None or distance < best_distance:
                best_distance = distance
                best = index
        return best, (best_distance if best_distance is not None else 0.0)

    def _emit_pulses(self, mid, bass):
        speed = (0.06 + mid * 0.10) * (0.8 + bass * 0.4)
        if len(self._pulses) >= self.PULSE_TARGET:
            return
        for body_index, (node_index, hue_off, _) in enumerate(self._bodies):
            if len(self._pulses) >= self.max_pulses:
                return
            count = 1 + int(min(2.0, bass * 2.0))
            for _ in range(count):
                if len(self._pulses) >= self.max_pulses:
                    return
                incident = list(self._node_edges[node_index]) if node_index is not None else []
                target = None
                if incident:
                    target = incident[int(self.rng.integers(0, len(incident)))]
                else:
                    position = self._nodes[node_index]
                    body_x, body_y = (position[0], position[1]) if position else (0.0, 0.0)
                    best = None
                    best_age = -1.0
                    for index, edge in enumerate(self._edges):
                        if edge is None:
                            continue
                        start = self._nodes[edge[0]]
                        end = self._nodes[edge[1]]
                        if start is None or end is None:
                            continue
                        near = (math.hypot(start[0] - body_x, start[1] - body_y) <= 120.0
                                or math.hypot(end[0] - body_x, end[1] - body_y) <= 120.0)
                        if near and edge[2] > best_age:
                            best_age = edge[2]
                            best = index
                    target = best
                if target is None:
                    continue
                edge = self._edges[target]
                if node_index is not None and node_index in (edge[0], edge[1]):
                    self._orient_edge(target, node_index)
                self._pulses.append([target, 0.0, speed, 1.0, 0, hue_off])

    def _update_pulses(self, step, mid):
        alive = []
        cap = min(self.max_pulses, self.PULSE_TARGET)
        for pulse in self._pulses:
            index, t, speed, energy, generation, hue_off = pulse
            edge = self._edges[index] if index < len(self._edges) else None
            if edge is None:
                continue
            if t >= 1.0:
                destination = edge[1]
                candidates = [c for c in self._node_edges[destination] if c != index]
                if not candidates:
                    continue  # the packet dies at a dead end
                chosen = candidates[int(self.rng.integers(0, len(candidates)))]
                self._orient_edge(chosen, destination)
                pulse[0] = chosen
                pulse[1] = 0.0
                pulse[3] = energy * 0.92
                alive.append(pulse)
                if (len(candidates) > 1
                        and self.rng.random() < 0.3 + mid * 0.4
                        and len(alive) < cap):
                    other = candidates[1 - candidates.index(chosen)]
                    self._orient_edge(other, destination)
                    alive.append([other, 0.0, speed, energy * 0.75, generation + 1, hue_off])
                continue
            pulse[1] = min(1.0, t + speed * step)
            alive.append(pulse)
        # The pulse population is capped: a handover or a branching pulse
        # never grows it beyond the target and the hard pool limit.
        self._pulses = alive[:cap]

    def _update_spores(self, step):
        width, height = self._W, self._H
        phase = self._phase
        survivors = []
        for spore in self._spores:
            x, y, vx, vy, life, max_life, hue_off, size = spore
            field_x = (math.sin(y * 0.008 + phase * 1.1)
                       + 0.6 * math.cos((x + y) * 0.005 - phase * 0.7))
            field_y = (math.cos(x * 0.009 - phase * 0.9)
                       + 0.6 * math.sin((x - y) * 0.005 + phase * 0.6))
            magnitude = math.hypot(field_x, field_y)
            if magnitude > 1e-9:
                field_x /= magnitude
                field_y /= magnitude
            else:
                field_x, field_y = 1.0, 0.0
            body_index, distance = self._nearest_body(x, y)
            position = self._nodes[self._bodies[body_index][0]] if self._bodies else None
            push_x = push_y = 0.0
            if position is not None and distance > 1e-6:
                push_x = (x - position[0]) / distance * 0.05
                push_y = (y - position[1]) / distance * 0.05
            vx = (vx + (field_x + push_x) * 0.12 * step) * 0.96
            vy = (vy + (field_y + push_y) * 0.12 * step) * 0.96
            x = min(max(x + vx * step, 0.0), width)
            y = min(max(y + vy * step, 0.0), height)
            life -= 1
            if life <= 0:
                self._germinate(x, y, hue_off, body_index)
                continue
            spore[0], spore[1], spore[2], spore[3], spore[4] = x, y, vx, vy, life
            survivors.append(spore)
        self._spores = survivors[:self.max_spores]

    def _germinate(self, x, y, hue_off, body_index):
        limit = 0.12 * min(self._W, self._H)
        nearest = None
        nearest_distance = limit
        for index, node in enumerate(self._nodes):
            if node is None:
                continue
            distance = math.hypot(node[0] - x, node[1] - y)
            if distance < nearest_distance:
                nearest_distance = distance
                nearest = index
        if nearest is None:
            return
        node = self._alloc_node(x, y)
        if node is None:
            return
        hue = (hue_off + self.SPORE_HUE_STEP) % 1.0
        if not self._free_edges and len(self._edges) >= self.max_edges:
            # The filament pool is full: retire the oldest filament so the
            # spore can take root without trimming the node it anchors to.
            self._retire_oldest_edge((nearest, node))
        edge = self._alloc_edge(nearest, node, hue, 0, body_index)
        if edge is None or self._nodes[nearest] is None:
            if edge is not None:
                self._free_edge(edge)
            self._free_node(node)
            return
        if len(self._tips) < self.max_tips:
            angle = float(self.rng.uniform(0.0, math.tau))
            cadence = self._cadence(0.0)
            phase = int(self.rng.integers(0, max(1, cadence)))
            self._tips.append([node, angle, 0, hue, 1.0, body_index,
                               cadence, phase])

    def _retire_oldest_edge(self, protected):
        """Retire the oldest filament that does not touch a protected node.

        A spore must always be able to take root, so the filament pool gives
        up its oldest thread when no leaf can be trimmed instead.
        """
        oldest = None
        oldest_age = -1.0
        for index, edge in enumerate(self._edges):
            if edge is None:
                continue
            if edge[0] in protected or edge[1] in protected:
                continue
            if edge[2] > oldest_age:
                oldest_age = edge[2]
                oldest = index
        if oldest is None:
            return None
        self._free_edge(oldest)
        return oldest

    def _update_rings(self, step, bass):
        alive = []
        for ring in self._rings:
            ring[2] += (1.6 + bass * 3.0) * step
            ring[3] -= 0.012 * step
            if ring[3] > 0.0:
                alive.append(ring)
        self._rings = alive

    def _spawn_ring(self, x, y):
        if len(self._rings) >= self.MAX_RINGS:
            self._rings.pop(0)
        self._rings.append([x, y, 4.0, 1.0])

    # ------------------------------------------------------------------
    # rendering
    # ------------------------------------------------------------------
    def _draw_bloom(self, surf):
        width, height = self._W, self._H
        radius = max(8, int(0.22 * min(width, height)))
        size = radius * 2
        if len(self._bloom_scaled) != len(self._bloom):
            self._bloom_scaled = [pygame.transform.smoothscale(sprite, (size, size))
                                  for sprite in self._bloom]
        for index, (_, _, _) in enumerate(self._bodies):
            position = self._nodes[self._bodies[index][0]]
            if position is None or index >= len(self._bloom_scaled):
                continue
            scaled = self._bloom_scaled[index]
            surf.blit(scaled,
                      (int(position[0] - scaled.get_width() / 2),
                       int(position[1] - scaled.get_height() / 2)),
                      special_flags=pygame.BLEND_RGB_ADD)

    def _pulse_light(self, live):
        """Accumulate the energy every travelling pulse puts on its edge."""
        count = len(live)
        lit = np.zeros(count, dtype=float)
        if not self._pulses:
            return lit
        slot_of = np.full(len(self._edges), -1, dtype=np.int64)
        for slot, (index, _) in enumerate(live):
            slot_of[index] = slot
        edge_index = np.fromiter((p[0] for p in self._pulses), dtype=np.int64,
                                 count=len(self._pulses))
        energy = np.fromiter((float(p[3]) for p in self._pulses), dtype=float,
                             count=len(self._pulses))
        valid = (edge_index >= 0) & (edge_index < len(slot_of))
        if not valid.any():
            return lit
        slots = slot_of[edge_index[valid]]
        usable = slots >= 0
        if usable.any():
            np.add.at(lit, slots[usable], energy[valid][usable])
        return np.clip(lit, 0.0, 1.0)

    def _draw_filaments(self, surf):
        live = [(index, edge) for index, edge in enumerate(self._edges) if edge is not None]
        if not live:
            return
        count = len(live)
        hues = np.empty(count, dtype=float)
        glow_light = np.empty(count, dtype=float)
        core_light = np.empty(count, dtype=float)
        depths = np.empty(count, dtype=np.int64)
        taper = np.empty(count, dtype=float)
        bodies = self._body_positions()
        span = max(1.0, 1.3 * min(self._W, self._H))
        for slot, (_, edge) in enumerate(live):
            age, lifetime, hue_off, depth = edge[2], edge[3], edge[4], edge[5]
            fade = max(0.25, 1.0 - age / lifetime)
            hues[slot] = self._composite_hue(hue_off, depth)
            glow_light[slot] = 0.18 + 0.12 * fade
            core_light[slot] = 0.55 + 0.30 * fade
            depths[slot] = depth
            taper[slot] = self._taper(edge, bodies, span)
        lit = self._pulse_light(live)
        core_light = np.clip(core_light + 0.45 * lit, 0.0, 1.0)
        core_hue = hues + (self.PULSE_HUE - hues) * 0.45 * lit
        glow_light = np.clip(glow_light + 0.30 * lit, 0.0, 1.0)
        glow_colors = _hsl_batch(hues, 0.72, glow_light)
        core_colors = _hsl_batch(core_hue, 0.85, core_light)
        glow_width = np.maximum(1, ((5 - depths // 2) * taper).astype(np.int64))
        core_width = np.maximum(1, ((2 - depths // 3) * taper).astype(np.int64))
        for slot, (_, edge) in enumerate(live):
            start = self._nodes[edge[0]]
            end = self._nodes[edge[1]]
            if start is None or end is None:
                continue
            point_a = (int(start[0]), int(start[1]))
            point_b = (int(end[0]), int(end[1]))
            glow = glow_colors[slot]
            core = core_colors[slot]
            pygame.draw.line(surf, (int(glow[0]), int(glow[1]), int(glow[2])),
                             point_a, point_b, int(glow_width[slot]))
            pygame.draw.line(surf, (int(core[0]), int(core[1]), int(core[2])),
                             point_a, point_b, int(core_width[slot]))

    def _body_positions(self):
        """The current position of every fruiting body."""
        positions = []
        for node_index, _, _ in self._bodies:
            position = self._nodes[node_index] if node_index is not None else None
            positions.append((position[0], position[1]) if position
                             else (self._W / 2.0, self._H / 2.0))
        return positions

    def _taper(self, edge, bodies, span):
        """Filaments are thick near their body and fine at the frontier."""
        start = self._nodes[edge[0]]
        end = self._nodes[edge[1]]
        if start is None or end is None:
            return 1.0
        body_index = edge[6] if len(edge) > 6 else 0
        if 0 <= body_index < len(bodies):
            body_x, body_y = bodies[body_index]
        else:
            body_x, body_y = self._W / 2.0, self._H / 2.0
        mid_x = (start[0] + end[0]) * 0.5
        mid_y = (start[1] + end[1]) * 0.5
        distance = math.hypot(mid_x - body_x, mid_y - body_y)
        return min(1.0, max(0.30, 1.0 - distance / span))

    def _draw_bodies(self, surf, bass):
        if not self._bodies:
            return
        for body in self._bodies:
            body[2] = min(1.0, 0.25 + bass * 0.35)
        hues = np.array([self._composite_hue(hue_off) for _, hue_off, _ in self._bodies])
        halo_colors = _hsl_batch(hues, np.array([0.75]), np.array([0.42]))
        disc_colors = _hsl_batch(hues, np.array([0.80]), np.array([0.75]))
        ring_colors = _hsl_batch(hues, np.array([0.60]), np.array([0.45]))
        for index, (node_index, _, _) in enumerate(self._bodies):
            position = self._nodes[node_index]
            if position is None:
                continue
            point = (int(position[0]), int(position[1]))
            halo = halo_colors[index]
            disc = disc_colors[index]
            ring = ring_colors[index]
            pygame.draw.circle(surf, (int(halo[0]), int(halo[1]), int(halo[2])),
                               point, max(1, int(14 + bass * 10)))
            pygame.draw.circle(surf, (int(disc[0]), int(disc[1]), int(disc[2])),
                               point, max(1, int(5 + bass * 5)))
            radius = max(1, int(16 + bass * 22))
            pygame.draw.circle(surf, (int(ring[0]), int(ring[1]), int(ring[2])),
                               point, radius, 1)

    def _draw_pulses(self, surf, bass_fast):
        if not self._pulses:
            return
        count = len(self._pulses)
        colors = _hsl_batch(np.full(count, self.PULSE_HUE), np.array([0.85]),
                            np.full(count, 0.72))
        for index, pulse in enumerate(self._pulses):
            edge_index, t = pulse[0], pulse[1]
            edge = self._edges[edge_index] if edge_index < len(self._edges) else None
            if edge is None:
                continue
            start = self._nodes[edge[0]]
            end = self._nodes[edge[1]]
            if start is None or end is None:
                continue
            x = start[0] + (end[0] - start[0]) * t
            y = start[1] + (end[1] - start[1]) * t
            tail_t = max(0.0, t - 0.18)
            tail_x = start[0] + (end[0] - start[0]) * tail_t
            tail_y = start[1] + (end[1] - start[1]) * tail_t
            energy = float(pulse[3])
            color = colors[index]
            rgb = (int(color[0]), int(color[1]), int(color[2]))
            pygame.draw.line(surf, rgb, (int(tail_x), int(tail_y)),
                             (int(x), int(y)), 2)
            pygame.draw.circle(surf, rgb, (int(x), int(y)),
                               max(1, int(2 + energy * 3)))

    def _draw_sparks(self, surf, treble):
        if not self._sparks:
            return
        color = _hsl_batch(np.array([self.AMBER_HUE]), np.array([0.85]),
                           np.array([0.55 + 0.25 * min(1.0, treble)]))[0]
        radius = max(1, int(1 + treble * 2))
        for x, y in self._sparks:
            pygame.draw.circle(surf, (int(color[0]), int(color[1]), int(color[2])),
                               (int(x), int(y)), radius)

    def _draw_spores(self, surf):
        if not self._spores:
            return
        # A spore is violet when it is released and amber when it settles.
        hues = []
        lights = []
        for spore in self._spores:
            fade = max(0.0, spore[4] / max(1, spore[5]))
            violet = self._composite_hue(spore[6])
            hues.append(violet + (1.0 - fade) * ((self.AMBER_HUE - violet) % 1.0))
            lights.append(0.45 * fade + 0.30)
        colors = _hsl_batch(np.array(hues), np.array([0.55]), np.array(lights))
        for index, spore in enumerate(self._spores):
            fade = max(0.0, spore[4] / max(1, spore[5]))
            radius = max(1, int(spore[7] * fade))
            color = colors[index]
            pygame.draw.circle(surf, (int(color[0]), int(color[1]), int(color[2])),
                               (int(spore[0]), int(spore[1])), radius)

    def _draw_rings(self, surf):
        if not self._rings:
            return
        hue = self._composite_hue(0.10)
        color = _hsl_batch(np.array([hue]), np.array([0.7]),
                           np.array([0.15 + 0.35 * 1.0]))[0]
        for x, y, radius, alpha in self._rings:
            fade = _hsl_batch(np.array([hue]), np.array([0.7]),
                              np.array([0.15 + 0.35 * alpha]))[0]
            pygame.draw.circle(surf, (int(fade[0]), int(fade[1]), int(fade[2])),
                               (int(x), int(y)), max(1, int(radius)), 1)

    # ------------------------------------------------------------------
    # frame hook
    # ------------------------------------------------------------------
    def _draw(self, surf, waveform, fft, beat, tick):
        if surf is None:
            return
        width, height = surf.get_size()
        if width <= 0 or height <= 0:
            return
        resized = (width, height) != (self._W, self._H)
        if resized:
            self._resize(width, height)
        step = self._frame_step()
        _, light = self._audio_response(beat)
        bass_fast = float(light[0])
        bass = float(np.clip(beat, 0.0, 1.5))
        mid = float(np.clip(self.frame.mid, 0.0, 1.5))
        treble = float(np.clip(self.frame.treble, 0.0, 1.5))
        onset = self._bass_prev < 0.5 <= bass_fast
        strong = bass >= 0.55
        kick = (bass - self._beat_prev) > 1.0
        self._bass_prev = bass_fast
        self._beat_prev = bass
        self._frames += 1
        silent_emit = bool(self.frame.is_silent) and self._frames % 180 == 0

        self._phase += (0.006 + mid * 0.008 + treble * 0.004) * step
        hue = self._hue + (0.0015 + mid * 0.0012) * step
        if hue > self.HUE_HI:
            hue = 2.0 * self.HUE_HI - hue
        elif hue < self.HUE_LO:
            hue = 2.0 * self.HUE_LO - hue
        self._hue = hue

        if resized:
            # A resize only re-lays out the network; it never rebuilds it.
            self._sparks = []
        else:
            self._grow(step, mid, treble, bass)
            self._prune()

            # The colony keeps a slow heartbeat of its own, so the network
            # always carries some travelling light even without a beat.
            beat_due = onset or kick or strong or silent_emit
            keepalive = (self._frames - self._last_emit >= self.KEEPALIVE_FRAMES
                         and not beat_due)
            if (beat_due or keepalive) and \
                    self._frames - self._last_emit >= self.PULSE_COOLDOWN:
                self._emit_pulses(mid, bass)
                self._last_emit = self._frames
            if onset:
                for node_index, hue_off, _ in self._bodies:
                    position = self._nodes[node_index]
                    if position is None:
                        continue
                    for _ in range(int(self.rng.integers(2, 6))):
                        if len(self._spores) >= self.max_spores:
                            break
                        angle = float(self.rng.uniform(0.0, math.tau))
                        speed = float(self.rng.uniform(0.5, 2.0))
                        life = int(self.rng.integers(240, 601))
                        self._spores.append([
                            position[0], position[1],
                            math.cos(angle) * speed, math.sin(angle) * speed,
                            life, life, (hue_off + self.SPORE_HUE_STEP) % 1.0,
                            float(self.rng.uniform(1.5, 3.5)),
                        ])
                    self._spawn_ring(position[0], position[1])

            self._update_pulses(step, mid)
            self._update_spores(step)
            self._update_rings(step, bass)

        surf.fill(self.GROUND)
        self._draw_bloom(surf)
        self._draw_filaments(surf)
        self._draw_bodies(surf, bass)
        self._draw_pulses(surf, bass_fast)
        self._draw_sparks(surf, treble)
        self._draw_spores(surf)
        self._draw_rings(surf)

    # ------------------------------------------------------------------
    # lifecycle
    # ------------------------------------------------------------------
    def _resize(self, width, height):
        old_width, old_height = self._W, self._H
        if old_width > 0 and old_height > 0:
            scale_x = width / old_width
            scale_y = height / old_height
            for node in self._nodes:
                if node is None:
                    continue
                node[0] = min(max(node[0] * scale_x, 0.0), width)
                node[1] = min(max(node[1] * scale_y, 0.0), height)
            for spore in self._spores:
                spore[0] = min(max(spore[0] * scale_x, 0.0), width)
                spore[1] = min(max(spore[1] * scale_y, 0.0), height)
                spore[2] *= scale_x
                spore[3] *= scale_y
            for ring in self._rings:
                ring[0] = min(max(ring[0] * scale_x, 0.0), width)
                ring[1] = min(max(ring[1] * scale_y, 0.0), height)
                ring[2] *= min(scale_x, scale_y)
        self._W, self._H = width, height
        self._bloom_scaled = []
        self._build_bloom()

    def set_motion_field(self, field) -> None:
        """Accept a producer field; anything else is ignored."""
        if field is None:
            self._motion_field = None
            return
        try:
            vx, vy = field
        except (TypeError, ValueError):
            self._motion_field = None
            return
        if (isinstance(vx, np.ndarray) and isinstance(vy, np.ndarray)
                and vx.ndim == 2 and vy.ndim == 2 and vx.shape == vy.shape
                and vx.size):
            self._motion_field = (vx, vy)
        else:
            self._motion_field = None

    def release(self) -> None:
        """Drop the cached surfaces and sprites."""
        self._bloom = []
        self._bloom_scaled = []
        super().release()
