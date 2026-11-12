# Mycelium — rebuild as a pulsing mycelial network

Branch `dev`. Started 2026-11-12. The previous maintainability plan (cards
R01–R05) now lives in [docs/plans/2026-11-11-maintainability.md](docs/plans/2026-11-11-maintainability.md);
its execution status stays in [TODO.md](TODO.md). This file is the single
planning authority for the Mycelium rebuild.

## Goal

Replace `effects/mycelium.py` with a from-scratch implementation whose identity
is a living hyphal network: persistent filaments carry travelling bioluminescent
pulses from fruit bodies out to the growing tips, and drifting spores that land
and germinate into new growth. The rebuild keeps the effect contract, keeps
every population bounded, and renders materially faster than the implementation
it replaces.

## Context

Facts a worker needs that are not obvious from the code alone.

### Contract and host

- `effects/base.py`: an effect subclasses `Effect` and implements
  `_draw(surf, waveform, fft, beat, tick)`. All state is instance-owned.
  `self.rng` is a `numpy.random.Generator` seeded from `EffectOptions.seed`;
  `self._random` is a seeded `random.Random`. Never seed or use global RNGs.
- `self.frame` is the current `FrameContext` and carries `width`, `height`,
  `dt`, `tick`, `profile`, `quality_scale`, `waveform`, `fft`, `beat`, `mid`,
  `treble`, `envelopes`, `bpm`, `phase`, `gain`, `is_silent`.
- `Effect._audio_response(beat)` returns `(motion, light)`; each is a 3-vector
  `(beat, mid, treble)` smoothed per 60 Hz frame step at 0.035 (motion, slow)
  and 0.25 (light, fast). `Effect._frame_step()` returns elapsed frames in
  60 Hz units, clamped to `[0, 6]`. `Effect._profile_response()` returns
  `(motion_scale, accent)`: `(0.65, 1.0)` for the laptop profile, `(0.85, 1.3)`
  for TV.
- `core/frame_composer.py:85-101` fades the target with `TRAIL_ALPHA` before
  each draw, and clears it to opaque black when `TRAIL_ALPHA == 0`. The new
  effect paints its own ground, so it sets `TRAIL_ALPHA = 0` and the network
  persists as state, not as pixels.
- `core/frame_composer.py:115-120` may call `set_motion_field(field)` with the
  background effect's `(vx, vy)` pair, or `set_motion_field(None)`. The new
  effect must accept both without raising and may use the field to bias growth.
- `effects/utils.py` offers `hsl(h, s, l)` (scalar, `colorsys`-based) and
  `_hsl_batch(h, s, l)` (vectorized, returns a `uint8` array shaped `(..., 3)`).
  Use `_hsl_batch` for all per-frame colour work; do not call `colorsys` in the
  frame loop.
- `effects/__init__.py`: the registry keeps the name "Mycelium" at position 17.
  `MODES` order, names and count (32) must not change; Spectrum and Waterfall
  stay last. Do not add a new file to `effects/`.
- `tests/render_host.py`: `host.create(cls)` builds the effect with
  `EffectOptions(width, height, low_spec, seed, audio)` plus the host frame;
  `effect.draw(surface, waveform, fft, beat, tick)` is the legacy adapter.
- The effect must construct with `options=` only (no frame) and must survive
  a 100×80 surface, a 1920×1080 surface and a resize between them.

### Measured cost centres of the implementation being replaced

Profiled 2026-11-12 on the old `effects/mycelium.py` (pygame-ce 2.5.7, Python
3.14.4, dummy SDL, 1920×1080, 60 frames, ~15.2 ms/frame):

| Cost | Per frame | Note |
| --- | ---: | --- |
| `_draw_halo` (diffusion + `smoothscale` + additive blit) | ~8.1 ms | dominant; the low-resolution reaction halo is dropped by this rebuild |
| `_draw_colony` (segment/capsule drawing) | ~4.3 ms | includes ~1.2 ms of `colorsys` colour lookups |
| `pygame.draw.line` calls (1200/frame) | ~0.65 ms | draw calls are cheap; population size is not the bottleneck |

Second spike at 1920×1080: 400 `pygame.draw.line` calls at width 1 cost
1.6–2.0 ms, the same 400 at width 5 cost 3.4–4.1 ms, 16 grouped
`pygame.draw.lines` calls over 600 segments cost 2.6–2.9 ms, and a
half-resolution draw plus `smoothscale` and additive blit cost 3.1–4.6 ms.

Consequences for this plan:

- Drop the low-resolution halo entirely; use a few additive sprite blits for
  the substrate bloom instead of a per-frame diffusion.
- Compute all filament colours with two vectorized `_hsl_batch` calls per
  frame, never with `colorsys` per segment.
- The old `lru_cache(maxsize=512)` colour cache is not reused: its key space
  (128 hue × 32 lightness × 16 saturation) is 65 536 wide, so it thrashes and
  misses on nearly every segment.

### Repository conventions

- No module-level mutable state, no globals for dimensions or audio.
- `config.py` no longer exists; dimensions come from `self.frame` / the target
  surface.
- Bounded populations, cleanup of owned surfaces, state preserved across resize.
- RNG only through `self.rng`, so an identical seed and frame sequence produce
  an identical result.
- Effects are rendered at full output resolution; there is no internal
  downscale surface in this design.

### Design brief (per NEWEFFECTS.md template)

1. **Visual identity:** a bioluminescent hyphal network whose filaments carry
   travelling light pulses from glowing fruit bodies to the growing tips, with
   spores that land and germinate into new filaments.
2. **Existing references:** Branches' recursive taper and Mycelium's old
   colony, SlimeMold's persistent material, Chromatic's separated bright
   accents, Synapse's travelling signals.
3. **Colour system:** dominant violet/magenta filaments over a deep indigo
   ground; cyan-white pulse light as the contrasting accent; amber spore and
   tip sparks as rare highlights. Hue drifts slowly and shifts with filament
   depth; age cools filaments toward the background. Shadows stay near-black
   indigo; only pulses and body cores reach near-white.
4. **Movement:** filaments grow continuously along a drifting field with a
   tangential swirl around their body; pulses travel outward and branch at
   junctions; spores drift and settle. The scene stays alive in silence:
   bodies emit a pulse every ~3 s and growth continues.
5. **Music mapping:** bass swells filament brightness and growth length and
   lights pulses; a beat onset releases a pulse wave from every body, releases
   spores and rings the bodies; mids raise the swirl, the branch probability
   and the pulse travel speed; treble adds tip sparks, spore release and
   filament shimmer.
6. **Composition:** three fruit bodies anchor the eye, the network spreads
   across the frame, and the pulse traffic keeps a clear direction of reading
   on a laptop and from across a room.
7. **Proof:** silence, a single kick, mid-only and treble-only inputs and
   sustained full-band audio, rendered headless and inspected as frames.

## Research findings

No external research phase was used. The contract, the conventions and the
cost centres above were read from this repository and measured with a
throwaway profiling spike.

## Spike findings

- `draw-cost`: how much does segment drawing cost at 1080p, and where does the
  old effect's time go? → Draw calls are cheap (0.65 ms for 1200 lines); the
  halo diffusion (8.1 ms) and per-segment `colorsys` lookups (1.2 ms) were the
  avoidable costs. Plan assumes: no halo diffusion, vectorized colours, and a
  frame-time target of ≤ 8 ms at 1080p.
- `draw-cost` (profile of the old effect): same numbers as above. The spike
  directory was deleted after the plan was written; the measurements above are
  its record.

## Decisions

| Question | Decision | Why |
|---|---|---|
| Registry entry | Keep the name "Mycelium" at position 17 | Registry order and count are a published contract |
| Trails | `TRAIL_ALPHA = 0`, the effect paints its own ground | The network persists as graph state; a decaying frame buffer is not needed and the old halo pass was the main cost |
| Graph storage | Fixed-size node and edge pools with free lists | Removing edges must not invalidate node or edge indices held by tips and pulses |
| Pulse transport | A pulse carries `(edge_index, t)` and hands over to an adjacent edge at a junction | Branching pulses are the effect's signature; a geometric payload would lose the junction |
| Colour | Two vectorized `_hsl_batch` calls per frame | Measured `colorsys` cost per segment |
| Substrate bloom | One precomputed radial sprite per body, additive blit | Replaces the 8 ms halo diffusion with a few blits |
| Old colour cache | Not reused | 65 536-wide key space against a 512-entry LRU cache thrashes |
| Visual baseline | `tests/test_visual_regression.py` baseline stays unchanged | The rebuild must fit the existing tolerance; a failing metric is reported, not the baseline rewritten |

## Scope

In scope: `effects/mycelium.py`, the Mycelium-specific tests, and the
documentation that describes this effect.

Out of scope: any other effect, `effects/base.py`, `effects/utils.py`,
`core/*`, the registry order, the visual baseline values and tolerances,
dependency or version changes, the changelog history migration, and the
external Android host.

## Files

| Path | Action | Notes |
|---|---|---|
| `effects/mycelium.py` | rewrite | New implementation, single `Mycelium(Effect)` class |
| `tests/test_effect_render_regressions.py` | edit | Replace the Mycelium-specific tests; keep the shared motion-field test working |
| `EFFECTS.md` | edit | Rewrite section 17 for the new behaviour |
| `NEWEFFECTS.md` | edit | Add the implemented brief for Mycelium |
| `README.md` | edit | Record the rebuilt effect |
| `PLAN.md` | edit (parent) | Task status |
| `CHANGELOG.md` | edit (parent) | Unreleased entry, owned by the parent |
| `TODO.md` | edit (parent) | Status and follow-ups, owned by the parent |
| `docs/plans/2026-11-11-maintainability.md` | moved (parent) | Previous plan, archived before this plan was written |

## Tasks

### Task 1 — Rewrite `effects/mycelium.py`  `status: done`
**Depends on:** none

**Goal**
Replace the implementation with the pulse-network design below, in one file,
without touching any other module.

**Steps**

1. Rewrite `effects/mycelium.py` from scratch. Do not copy the previous
   implementation; the file is replaced wholesale. Keep the module docstring
   short and accurate.
2. Class shape:

   ```python
   class Mycelium(Effect):
       TRAIL_ALPHA = 0
       MAX_EDGES = 420
       MAX_TIPS = 220
       MAX_PULSES = 260
       MAX_SPORES = 160
       MAX_NODES = 1200
       BODY_COUNT = 3
   ```

   With `options.low_spec` set, halve `MAX_EDGES`, `MAX_TIPS`, `MAX_PULSES`
   and `MAX_SPORES`. Expose `self.max_tips` as the effective tip cap; the
   shared test `tests/test_effect_render_regressions.py::test_motion_field_consumers_stay_finite_and_bounded`
   reads `mycelium._tips` and `mycelium.max_tips`, so both names must exist
   with those meanings (`_tips` is the list of growing tips, `max_tips` is the
   integer cap).

3. State, all instance-owned:

   | Attribute | Type | Meaning |
   | --- | --- | --- |
   | `_nodes` | list of `[x, y]` float | node positions in pixels |
   | `_node_edges` | list of lists | per node, the indices of incident edges |
   | `_edges` | list of `[a, b, age, lifetime, hue_off, depth]` | filaments between node `a` and node `b` |
   | `_free_nodes`, `_free_edges` | lists of int | reusable pool slots |
   | `_tips` | list of `[node_index, angle, depth, hue_off, energy, body_index]` | growing tips |
   | `_pulses` | list of `[edge_index, t, speed, energy, generation, hue_off]` | travelling light packets |
   | `_spores` | list of `[x, y, vx, vy, life, max_life, hue_off, size]` | drifting spores |
   | `_bodies` | list of `[node_index, hue_off, glow]` | fruit bodies, anchored on a node |
   | `_rings` | list of `[x, y, radius, alpha]` | expanding beat rings, max 8 |
   | `_hue` | float | slow base-hue drift |
   | `_phase` | float | field phase, advanced per frame |

   Nodes and edges are pools: allocate from the free list, and grow the pool
   only while it is below the cap. Never renumber a live node or edge.
4. Construction: seed `self._hue` from `self.rng`, place `BODY_COUNT` bodies
   on nodes around the centre at seeded angles (120° apart plus jitter) and a
   radius of 0.16–0.28 × `min(W, H)`; give each body a root tip and a first
   filament. Store the body positions on the nodes they are anchored to.
5. Growth, per tip, per frame (all motion scaled by `self._frame_step()`):

   - Field: `fx = sin(y*0.008 + phase*1.1) + 0.6*cos((x+y)*0.005 - phase*0.7)`,
     `fy = cos(x*0.009 - phase*0.9) + 0.6*sin((x-y)*0.005 + phase*0.6)`,
     normalised.
   - Swirl: the tangential unit vector around the tip's own body, scaled by
     `0.25 + mid*0.45`.
   - Cohesion: when the tip is farther than `0.42*min(W, H)` from its body,
     add `0.35` of the unit vector back towards the body.
   - Motion field: when `self._motion_field` is a pair of 2-D arrays, sample
     the nearest cell and add `0.04 * clip(vx - vy, -2, 2)` to the target
     direction, as the previous implementation did.
   - New direction: `0.55 * heading + 0.35 * field + swirl + cohesion`,
     normalised; the tip's angle becomes `atan2` of that direction.
   - Noise: add `self.rng.normal(0, 0.05 + mid*0.10 + treble*0.05)` radians to
     the angle, clamped to ±0.6 rad per step.
   - Step length: `(5.0 + bass*6.0) * (1 - min(depth, 12)*0.035)`, at least 2.0
     pixels.
   - New head: `head + (cos(angle), sin(angle)) * length`. Reflect at the
     borders (mirror the position and the angle); never wrap, because a wrap
     draws a line across the whole screen.
   - Create the new node and the edge, register the edge in both nodes'
     adjacency, and move the tip to the new node.
6. Branching: with probability `0.02 + mid*0.05 + treble*0.03` per tip per
   frame, create a child tip on the same node with an angle offset of
   ±(0.4–0.9) rad, `depth + 1`, hue offset +0.10. A tip stops growing when
   its depth reaches 14; it stays in the network as a mature end. Respect the
   tip cap.
7. Filament lifetime and pruning: each edge ages by one frame per frame and
   dies at its `lifetime` (1800 frames, ±25 % seeded jitter). Width and
   brightness scale with `max(0.25, 1 - age/lifetime)`. When the edge pool is
   full, free the oldest edge first. Freeing an edge removes it from both
   nodes' adjacency, frees the edge slot, frees the head node when its degree
   drops to zero and no tip uses it, and drops the pulses that referenced it.
8. Pulses:

   - Emission: on a beat onset (the fast bass response crosses 0.5 upward) and
     every 180 frames in silence, each body emits 1–3 pulses on random
     incident edges of its node; if the node has no incident edge, the body
     emits on the newest edge within 120 px instead. Skip emission when
     `MAX_PULSES` is reached.
   - Travel: `t += speed * step` with `speed = (0.55 + mid*1.2) * (0.8 + bass*0.4)`;
     `t` is clamped to 1.
   - Arrival: when `t` reaches 1, take the far node, look at its incident
     edges, drop the edge the pulse came from. With no candidate the pulse
     dies. Otherwise continue on a random candidate with `t = 0` and
     `energy *= 0.92`. With probability `0.3 + mid*0.4`, additionally spawn a
     child pulse on a second candidate with `energy *= 0.75` and
     `generation + 1`.
   - Rendering: the pulse position is the linear interpolation of the edge's
     two nodes; draw a bright dot of radius `2 + energy*2` in cyan-white plus a
     two-pixel tail towards the previous position. Brightness scales with the
     fast bass response.
9. Spores and germination:

   - A growing tip releases a spore with probability `0.02 + treble*0.05` per
     frame; a beat onset releases 2–5 spores per body.
   - A spore drifts with the field plus a slow outward push from the nearest
     body, lives 240–600 frames, and shrinks with its remaining life.
   - When a spore's life ends, it germinates if a node exists within
     `0.12*min(W, H)`: create a node at the spore position, connect it to the
     nearest node with an edge whose hue offset is shifted by +0.12, and start
     a new tip there with depth 0. Without a near node the spore fades without
     effect.
10. Body rings: a beat onset pushes an expanding ring per body onto `_rings`
    (maximum 8 total, oldest dropped); rings grow by `1.6 + bass*3` px per
    frame and fade out.
11. Rendering, in this order, into the target surface:

    - Fill the whole surface with the deep indigo ground `(9, 7, 24)`.
    - Substrate bloom: one precomputed 128×128 radial-falloff RGBA sprite per
      body (built once at construction, rebuilt when the size changes),
      tinted by the body's hue and scaled to about `0.35*min(W, H)`, blitted
      with `pygame.BLEND_RGB_ADD`.
    - Filaments: compute the colour of every edge in two vectorized
      `_hsl_batch` calls — one for the wide glow pass (lightness 0.18–0.30,
      width `5 - depth//4`, at least 1) and one for the bright core pass
      (lightness 0.55–0.85, width `2 - depth//8`, at least 1) — then draw each
      edge with `pygame.draw.line`.
    - Bodies: a bright disc plus a thin ring whose radius follows the bass.
    - Pulses, then tip sparks (warm amber dots on tips that grew this frame,
      driven by the treble), then spores, then the expanding rings.
    - Hue per edge: `(self._hue + hue_off + depth*0.015) % 1.0`; lightness
      fades with the edge age.
12. Resize: when the target size changes, scale every node position by the
    new/old width and height ratio and clamp it into the new bounds. Keep the
    topology, the tips, the pulses, the spores and the bodies; do not rebuild
    the network. Rebuild the bloom sprite.
13. `set_motion_field(field)`: accept `None` or a `(vx, vy)` pair of 2-D
    `numpy` arrays; ignore anything else without raising.
14. `release()`: drop the cached surfaces and sprites, then call
    `super().release()`.

**Acceptance criteria**

- [ ] `effects/mycelium.py` defines exactly one `Mycelium(Effect)` class with
      a `_draw` hook; no other file in `effects/` changed; the registry entry
      for "Mycelium" is unchanged.
- [ ] No module-level mutable state; the only randomness comes from
      `self.rng`; no `colorsys` call in the frame path.
- [ ] After 600 frames driven by a scripted beat sequence, `len(_edges) <=
      MAX_EDGES`, `len(_tips) <= max_tips`, `len(_pulses) <= MAX_PULSES`,
      `len(_spores) <= MAX_SPORES`, `len(_nodes) <= MAX_NODES`, and every
      coordinate, angle and radius is finite.
- [ ] A pulse emitted on a beat advances its `t` on later frames and reaches
      1.0 within 300 frames; it then continues on an adjacent edge or is
      removed.
- [ ] A spore placed next to a node produces a new tip after its life ends.
- [ ] Drawing at 100×80 and at 1920×1080 raises nothing; a resize between the
      two keeps the node count and the topology and rescales the positions.
- [ ] `set_motion_field(None)` and `set_motion_field((vx, vy))` with 2-D
      arrays both leave the effect drawable and finite.
- [ ] Two instances created with the same seed and driven by the same frame
      sequence hold identical `_nodes`, `_edges`, `_tips` and `_pulses`.
- [ ] Mean frame time over 60 frames at 1920×1080 is at most 8 ms, and at
      most 16 ms at 3840×2160 (measurement snippet below).
- [ ] `tests/test_visual_regression.py` passes with its existing Mycelium
      baseline `(17941, 423082)` unchanged.

**Verification**

```bash
SDL_VIDEODRIVER=dummy SDL_AUDIODRIVER=dummy PYTHONDONTWRITEBYTECODE=1 \
  .venv/bin/python -m unittest tests.test_effect_render_regressions -v
SDL_VIDEODRIVER=dummy SDL_AUDIODRIVER=dummy PYTHONDONTWRITEBYTECODE=1 \
  .venv/bin/python -m unittest discover -s tests -q
SDL_VIDEODRIVER=dummy SDL_AUDIODRIVER=dummy PYTHONDONTWRITEBYTECODE=1 \
  .venv/bin/python smoke_test.py
git diff --check
```

Frame-time measurement (run from the repository root, report the two means):

```bash
SDL_VIDEODRIVER=dummy SDL_AUDIODRIVER=dummy .venv/bin/python - <<'PY'
import time
import numpy as np, pygame
from core.context import EffectOptions, FrameContext
from effects.mycelium import Mycelium
pygame.init(); pygame.display.set_mode((320, 240))
for W, H in ((1920, 1080), (3840, 2160)):
    opts = EffectOptions(W, H, seed=7)
    wave = np.zeros(1024, dtype=np.float32); fft = np.zeros(512, dtype=np.float32)
    surf = pygame.Surface((W, H))
    eff = Mycelium(options=opts, frame=FrameContext(W, H, waveform=wave, fft=fft))
    for t in range(20):
        eff.draw(surf, wave, fft, 0.9 if t % 4 == 0 else 0.05, t)
    start = time.perf_counter()
    for t in range(60):
        eff.draw(surf, wave, fft, 0.9 if t % 4 == 0 else 0.05, t)
    print(f"{W}x{H}: {(time.perf_counter() - start) / 60 * 1000:.2f} ms/frame")
pygame.quit()
PY
```

**Notes / pitfalls**

- The old file used `lru_cache` colour lookups and a low-resolution halo.
  Both are gone by design; do not reintroduce them.
- The old file's `_cores`, `_segs`, `_spores` layout and `_draw_colony` helper
  are gone. The tests in Task 2 are rewritten accordingly.
- `self.frame.width/height` can differ from the target surface size; use the
  target surface size for drawing and `self.frame` for audio and profile.
- `pygame.draw.line` needs integer coordinates; clamp and cast them.
- The bloom sprite must be rebuilt after a resize, otherwise it is stretched.
- `Effect.render()` restores the previous frame binding when `_draw` raises;
  do not rely on `self.frame` staying bound after an exception.

### Task 1b — Correction round 1: make the colony spread and the palette coherent  `status: done`
**Depends on:** Task 1 (implemented, but visually wrong)

**Problem found during parent verification.** The first implementation satisfies
the letter of Task 1 but not its intent. Measured at 960×540 after 1400
frames: 6 tips left, all at depth 14, node extent x 366..617 and y 164..403
(about a quarter of the frame), mean filament segment 4.0 px, no pulse alive
at the sampled moment. Rendered frames show three small pinwheel tufts inside
large saturated bloom discs, not a web across the frame.

Root causes, all in Task 1's step 5-6 wording:

1. `depth` counted growth steps, so a tip matured after 14 steps (~0.2 s) and
   stopped growing. The colony could never extend.
2. The step length was 5-11 px scaled down by depth, so the filaments were
   stubs even while they grew.
3. Every tip grew every frame, so the 420-edge cap was reached in about two
   seconds and the network churned instead of spreading.
4. The bloom sprites were large (0.35 of the short side) and saturated, and
   the three bodies were spread over 0.45 of the hue circle, so the image read
   as coloured fog with a rainbow speckle in the middle instead of a violet
   network with cyan pulses.
5. The pulse speed (0.55-1.75 edges per frame) crossed an edge in about one
   frame, so a pulse was a flicker rather than travelling light.

**Goal**
A violet mycelial web that spans the frame, with travelling cyan pulses
visible along the filaments and a substrate bloom that supports rather than
dominates.

**Steps**

All changes are in `effects/mycelium.py`.

1. Depth becomes a branch generation, not a step count. A tip's depth no
   longer changes while it grows; branching assigns the child
   `depth = parent_depth + 1`. A tip stops growing when its branch generation
   reaches 14, as before. Filament width and hue still scale with the
   generation: glow width `max(1, 5 - depth // 2)`, core width
   `max(1, 2 - depth // 3)`, hue `base + hue_off + depth * 0.02`.
2. Pace the growth. Extend the tip tuple to
   `[node_index, angle, depth, hue_off, energy, body_index, cadence, phase]`.
   A tip grows on the frames where `(self._frames + phase) % cadence == 0`,
   with `cadence = max(45, 190 - int(bass * 70))` frames and a per-tip phase
   offset in `[0, cadence)` so the tips do not all grow in the same frame.
   With roughly 60 tips this yields about one new filament per frame, and the
   alive-edge count settles near the cap instead of churning through it.
3. Segment length: `(16.0 + bass * 10.0) * (1 - min(depth, 8) * 0.03)`
   pixels, at least 6 px.
4. Direction weights: `0.45 * heading + 0.50 * field + swirl + cohesion`.
   Swirl becomes `0.10 + mid * 0.22` (it was `0.25 + mid * 0.45`, which curled
   every filament into a pinwheel). Cohesion starts at `0.80 * min(W, H)`
   instead of `0.42 * min(W, H)` and keeps its 0.35 strength, so the colony
   can reach the frame edges.
5. Branching is now evaluated per growth event rather than per frame:
   `0.10 + mid * 0.18 + treble * 0.10`, with the child hue offset +0.06.
6. Budgets: `MAX_EDGES = 480` (240 on low spec) and
   `EDGE_LIFETIME = 1500` frames (25 s) with the existing ±25 % jitter. The
   steady state is a web of roughly 480 filaments that renews gradually.
7. Pulse speed: `(0.06 + mid * 0.10) * (0.8 + bass * 0.4)` edge per frame, so
   a pulse needs about 8-15 frames per filament and several seconds to reach
   the frontier. Pulse radius `2 + energy * 3`, tail 0.18 of the edge.
8. Lit filaments: while a pulse occupies an edge, add the pulse energy to
   that edge's core lightness, so the light is visibly carried along the
   filament instead of being a dot that jumps. Build the per-edge
   contribution with one vectorized accumulation over the pulse list and add
   it to the core-lightness array before the second `_hsl_batch` call.
9. Palette: seed the base hue in `[0.70, 0.86]` and let it drift inside
   `[0.66, 0.92]` (reflect at the bounds, do not wrap). The three bodies get
   hue offsets `index * 0.05`, so all three stay in the violet-magenta family.
   The branch hue offset is +0.06 and the spore hue offset +0.04.
10. Bloom: radius `max(8, int(0.20 * min(W, H)))`, tint lightness 0.20, falloff
    exponent 2.6, and RGB magnitude `tint * falloff * 0.35`. The bloom is a
    substrate glow under the web, not a coloured ball around each body.
11. Bodies: disc radius `4 + bass * 4`, ring radius `12 + bass * 18`.
12. Keep everything else: the pools, the pruning and the pulse handover, the
    spore germination, the resize behaviour, `TRAIL_ALPHA = 0`, the ground
    colour, `set_motion_field`, `release`, and the attribute names
    `_nodes`, `_node_edges`, `_edges`, `_tips`, `_pulses`, `_spores`,
    `_bodies`, `_rings`, `max_tips`.

**Acceptance criteria**

- [ ] After 1800 frames at 960×540, the node extent covers at least 70 % of
      the frame width and at least 70 % of the frame height.
- [ ] After 600 frames driven by the beat sequence `(0.0, 3.0, 6.0) * 8`, at
      least 6 pulses are alive at any sampled frame, and the alive-edge count
      stays between 60 % and 100 % of `MAX_EDGES`.
- [ ] The mean filament segment length is at least 12 px.
- [ ] Every population stays within its cap and every value stays finite.
- [ ] The rendered frame shows the web clearly: the bloom does not cover more
      than 12 % of the pixels above 200 luma, and the network is readable
      against it.
- [ ] The frame time stays at or below 8 ms at 1920×1080 and 16 ms at
      3840×2160.
- [ ] `tests/test_visual_regression.py` still passes with its unchanged
      baseline `(17941, 423082)`.
- [ ] Four reference frames are saved to `/tmp` (idle, one beat, busy, late)
      and reported, so the parent can inspect the result.

**Verification**

```bash
SDL_VIDEODRIVER=dummy SDL_AUDIODRIVER=dummy PYTHONDONTWRITEBYTECODE=1 \
  .venv/bin/python -m unittest discover -s tests -q
SDL_VIDEODRIVER=dummy SDL_AUDIODRIVER=dummy PYTHONDONTWRITEBYTECODE=1 \
  .venv/bin/python smoke_test.py
git diff --check
```

plus the frame-time snippet from Task 1 and the reference-frame script:

```bash
SDL_VIDEODRIVER=dummy SDL_AUDIODRIVER=dummy PYTHONPATH=. .venv/bin/python - <<'PY'
import numpy as np, pygame
from core.context import EffectOptions, FrameContext
from effects.mycelium import Mycelium
pygame.init(); pygame.display.set_mode((320, 240))
W, H = 960, 540
opts = EffectOptions(W, H, seed=7)
wave = np.zeros(1024, dtype=np.float32); fft = np.zeros(512, dtype=np.float32)
eff = Mycelium(options=opts, frame=FrameContext(W, H, waveform=wave, fft=fft))
surf = pygame.Surface((W, H))
for t in range(1801):
    beat = 0.9 if t % 45 == 0 else 0.05
    eff.render(surf, FrameContext(W, H, tick=t, waveform=wave, fft=fft,
                                  beat=beat, mid=0.4, treble=0.55))
    if t in (90, 300, 700, 1400, 1800):
        pygame.image.save(surf, f"/tmp/myc2_{t}.png")
pts = np.array([n for n in eff._nodes if n])
print("extent", pts[:, 0].min(), pts[:, 0].max(), pts[:, 1].min(), pts[:, 1].max())
print("edges", len([e for e in eff._edges if e]), "tips", len(eff._tips),
      "pulses", len(eff._pulses), "spores", len(eff._spores))
PY
```

**Notes / pitfalls**

- The correction changes dynamics, not the public surface: keep every
  attribute name listed in step 12 and keep `max_tips` and `_tips`.
- Do not reintroduce the halo diffusion or per-segment `colorsys` calls.
- The three bodies share a hue family now; a spread over the whole colour
  circle was the second half of the visual failure.

### Task 1c — Correction round 2: an organic colony instead of a sampled mesh  `status: done`
**Depends on:** Task 1b (implemented, but visually wrong)

**Problem found during parent verification.** Round 1 fixed the spread but
changed the material. The rendered frame is now a triangulated wireframe: the
construction samples a jittered grid of cells and links them with straight
segments, so the image reads as a polygonal mesh, not as hyphae. The fruiting
bodies are nearly invisible, the bloom is gone, every filament has the same
width, and the pulse dots are the only thing that reads as light. The mesh
also exists only because the eight-frame visual baseline needs ink on frame
one.

**Goal**
An organic colony: curved, tapering filaments that thicken near the fruiting
body and thin towards the frontier, connected into one living web, with
visible bodies, a restrained substrate bloom, amber spores and sparks, and
cyan light travelling along the filaments.

**Steps**

All changes are in `effects/mycelium.py`.

1. Remove the sampled mesh. `_seed_web`'s cell sampling, `_zone_map`,
   `_link_cells`, `_link` and the module-level `rows_of` / `cols_of`
   helpers are deleted. `_place_bodies` stays: it anchors the three fruiting
   bodies on seeded positions around the centre.
2. Replace the mesh with an organic seed colony that is grown by the same
   rules as the live growth:

   - Construction places the bodies and gives each body four root tips
     (twelve tips in total) pointing away from the centre.
   - `_grow` takes a `warmup` flag. When it is set, the cadence check is
     skipped (every tip grows on every call) and no pruning runs.
   - The construction then runs 45 warm-up steps with `step=1.0`,
     `mid=0.35`, `treble=0.45`, `bass=0.0`, which yields roughly 540 organic
     filament segments (bounded by `max_edges`) that are already curved,
     tapered and cross-linked.
   - The tips' cadence and phase are assigned after the warm-up, so the live
     growth starts staggered.
   - The construction must stay below 200 ms; report the measured time.
3. The live growth keeps the round-1 rules: the paced cadence, the field
   direction, the swirl, the cohesion, the cross-links and the branch
   probability.
4. Filament taper. Each edge stores its body index as a seventh field. At
   draw time the distance of the edge's midpoint from its body node gives
   `taper = clamp(1.0 - distance / (1.3 * min(W, H)), 0.30, 1.0)`. The glow
   width becomes `max(1, int((5 - depth // 2) * taper))` and the core width
   `max(1, int((2 - depth // 3) * taper))`, so the filaments are thick at the
   core and fine at the frontier.
5. Make the fruiting bodies and the bloom visible again:

   - Bloom: radius `max(8, int(0.22 * min(W, H)))`, tint lightness 0.30, RGB
     magnitude `tint * falloff * 0.55`.
   - A body is drawn as a low-lightness halo disc (radius `14 + bass * 10`),
     a bright core disc (radius `5 + bass * 5`, lightness 0.75) and a thin
     ring (radius `16 + bass * 22`).
6. Restore the warm accent. Sparks and spores use amber (hue 0.09), not the
   pulse cyan: sparks at lightness `0.55 + 0.25 * treble`, spores from violet
   to amber over their life. The pulses stay cyan (hue 0.52), and the
   filaments stay violet.
7. Keep everything else: the node and edge pools, `_free_near` trimming, the
   pruning and renewal, the pulse handover and `_rehome_pulses`, the
   keep-alive emission, the violet hue band, `TRAIL_ALPHA = 0`, the ground
   colour, `set_motion_field`, `release`, and every attribute name
   (`_nodes`, `_node_edges`, `_edges`, `_tips`, `_pulses`, `_spores`,
   `_bodies`, `_rings`, `max_tips`).

**Acceptance criteria**

- [ ] The filaments curve: the mean absolute turn angle at the nodes with two
      or more incident filaments is at least 10 degrees.
- [ ] The filaments taper: the ratio between the thickest and the thinnest
      drawn width in the web is at least 2.
- [ ] The web is one connected structure: after the warm-up, a flood fill
      from any node reaches at least 90 % of the alive nodes.
- [ ] After 1800 frames at 960×540 the node extent covers at least 70 % of
      the frame width and at least 70 % of the frame height.
- [ ] The bodies read: at least 300 pixels above 120 luma within 40 px of a
      body position, while the share of pixels above 200 luma stays at or
      below 12 %.
- [ ] Construction takes at most 200 ms.
- [ ] The frame time stays at or below 8 ms at 1920×1080 and 16 ms at
      3840×2160.
- [ ] `tests/test_visual_regression.py` passes with its unchanged baseline
      `(17941, 423082)`.
- [ ] Four reference frames are saved to `/tmp` and reported.

**Verification**

```bash
SDL_VIDEODRIVER=dummy SDL_AUDIODRIVER=dummy PYTHONDONTWRITEBYTECODE=1 \
  .venv/bin/python -m unittest discover -s tests -q
SDL_VIDEODRIVER=dummy SDL_AUDIODRIVER=dummy PYTHONDONTWRITEBYTECODE=1 \
  .venv/bin/python smoke_test.py
git diff --check
```

plus the frame-time snippet from Task 1 and the reference-frame script from
Task 1b, and a measurement script that reports the turn angle, the width
ratio, the connected share of the nodes, the body brightness, the construction
time and the node extent.

**Notes / pitfalls**

- The eight-frame visual baseline needs ink on the first frame. The warm-up
  colony provides it; do not reintroduce a mesh to satisfy the baseline.
- The warm-up must run the ordinary growth rules, otherwise the seeded
  colony and the live growth will look like two different effects.
- Round 1 changed the construction from "grow from the bodies" to "sample a
  grid". Round 2 goes back to growing from the bodies, with a warm-up so the
  colony is already alive on frame one.

**Outcome.** Implemented and verified. The colony is now organic, curved and
tapered, the fruiting bodies and the substrate bloom are visible, the packets
travel the filaments and the spores germinate. Measured: mean turn angle
22.5°, width ratio 5.0, connected share 96.9 % of the alive nodes, pixels
above 200 luma 0.075 %, construction 38 ms at 960×540.

One acceptance criterion was not met and is recorded as an open follow-up in
TODO.md: the node extent covers about 30 % of the frame width at 960×540,
not 70 %. Two tunings were compared. The shipped one keeps the curl (swirl
`0.10 + mid * 0.22`, cohesion threshold `1.00 * limit`, strength `0.25`) and
reads as an organic web; the other (swirl `0.06 + mid * 0.16`, cohesion
threshold `1.15 * limit`, strength `0.18`) draws straight rays across the
frame and reads as scattered lines. The curled variant was kept because it
reads as a living network. Note that the extent measurement for the wide
variant was not reproducible: one harness reported 70 % of the width, a
second harness measured about 31 % on the same constants, so the two states
were judged on the rendered frames rather than on that number. The spread
needs a frontier that travels along a large-scale flow instead of circling
its body.

### Task 2 — Rewrite the Mycelium tests  `status: done`
**Depends on:** Task 1, Task 1b, Task 1c

**Goal**
Make `tests/test_effect_render_regressions.py` describe the new effect, and
keep the shared motion-field test working.

**Steps**

1. Read `effects/mycelium.py` after Task 1 and replace the Mycelium-specific
   tests. The old tests `test_mycelium_colony_presentation_does_not_advance_simulation`,
   `test_mycelium_constructs_on_small_display`,
   `test_mycelium_reaction_halo_stays_bounded` and
   `test_mycelium_cores_wander_around_seeded_anchors` refer to the removed
   internals (`_cores`, `_segs`, `_halo`, `_draw_colony`) and must be replaced,
   not merely edited.
2. New tests, all inside `EffectRenderRegressionTests`:

   - `test_mycelium_network_state_stays_bounded`: drive 240 frames with the
     beat sequence `(0.0, 3.0, 6.0) * 8`; assert every population is within
     its cap, every coordinate is finite, and every node lies inside the
     surface.
   - `test_mycelium_pulses_travel_and_handover`: trigger a beat, assert at
     least one pulse exists, assert its `t` grows on the next frames, and
     assert it either hands over to another edge or disappears within 300
     frames.
   - `test_mycelium_spore_germinates_into_a_new_tip`: place a spore next to a
     known node, let its life expire, assert a tip appeared.
   - `test_mycelium_constructs_and_renders_on_small_display`: construct and
     draw at 100×80 and at 320×240.
   - `test_mycelium_resize_preserves_the_network`: draw at 320×240, then at
     640×480; assert the node count did not drop back to its initial value and
     that the positions stayed inside the new bounds.
   - `test_mycelium_is_seeded_and_reproducible`: two instances with the same
     seed and the same frame sequence hold identical `_nodes`, `_edges`,
     `_tips` and `_pulses`.
   - `test_mycelium_accepts_and_ignores_motion_fields`: `set_motion_field(None)`
     and a `(vx, vy)` pair of 2-D arrays both leave the effect drawable with
     finite state.
   - `test_mycelium_colony_spreads_across_the_frame`: drive 900 frames at
     320×240 with the beat sequence and assert that the node extent covers at
     least 60 % of the width and at least 60 % of the height. This is the
     property the first implementation failed.
3. Keep the shared test
   `test_motion_field_consumers_stay_finite_and_bounded` unchanged; it already
   reads `mycelium._tips` and `mycelium.max_tips`.
4. Do not change `tests/test_visual_regression.py`, and do not change any
   baseline value. If the Mycelium baseline no longer fits its tolerance,
   report it as a finding instead of rewriting the baseline.

**Acceptance criteria**

- [ ] No test in the file references `_cores`, `_segs`, `_halo` or
      `_draw_colony`.
- [ ] The six new tests pass and each asserts an observable property of the
      new effect.
- [ ] `tests/test_visual_regression.py` is unchanged and passes.
- [ ] The full unit suite passes.

**Verification**

```bash
SDL_VIDEODRIVER=dummy SDL_AUDIODRIVER=dummy PYTHONDONTWRITEBYTECODE=1 \
  .venv/bin/python -m unittest tests.test_effect_render_regressions -v
SDL_VIDEODRIVER=dummy SDL_AUDIODRIVER=dummy PYTHONDONTWRITEBYTECODE=1 \
  .venv/bin/python -m unittest tests.test_visual_regression -v
SDL_VIDEODRIVER=dummy SDL_AUDIODRIVER=dummy PYTHONDONTWRITEBYTECODE=1 \
  .venv/bin/python -m unittest discover -s tests -q
git diff --check
```

**Notes / pitfalls**

- The tests must not depend on wall-clock time or on real audio.
- The effect is deterministic for a fixed seed, so exact-value assertions are
  possible; prefer range assertions to avoid brittle tests.

### Task 3 — Update the documentation  `status: done`
**Depends on:** Task 1

**Goal**
Describe the new effect accurately in the three documents that carry effect
documentation.

**Steps**

1. `EFFECTS.md`: rewrite section 17 (`## 17. Mycelium`). Keep the section's
   shape — a paragraph on what the effect shows, an `- Audio:` bullet, a
   `- Visual notes:` bullet. Describe the pulse network, the fruiting bodies,
   the spore germination, and the actual audio mapping from Task 1. Remove
   every reference to the removed behaviour (reaction halo, satellite node
   rings, core colonies).
2. `NEWEFFECTS.md`: add an implemented-brief section for Mycelium in the same
   form as the existing "ChromaWeave — implemented brief" section, using the
   seven-item template at the end of that file (visual identity, existing
   references, colour system, movement, music mapping, composition, proof).
   Place it after the ChromaWeave brief.
3. `README.md`: the project keeps a highlighted section per notable effect
   change (see "New mode: ChromaWeave"). Add a short section for the rebuilt
   Mycelium in the same style, and check the surrounding text for statements
   that the rebuild invalidates (the effect list, the visual-baseline note,
   the verification summary). Do not restate the whole effect design; point
   to `EFFECTS.md` for the details.
4. Keep the existing tone and formatting of each file. No new headings beyond
   the ones the files already use for this purpose.

**Acceptance criteria**

- [ ] `EFFECTS.md` section 17 matches the implemented behaviour; no mention of
      the removed halo or colony cores.
- [ ] `NEWEFFECTS.md` contains a Mycelium implemented brief with all seven
      template items.
- [ ] `README.md` describes the rebuilt effect and contains no statement that
      the rebuild contradicts.
- [ ] No file mentions the old Mycelium behaviour as current.

**Verification**

```bash
SDL_VIDEODRIVER=dummy SDL_AUDIODRIVER=dummy PYTHONDONTWRITEBYTECODE=1 \
  .venv/bin/python -m unittest discover -s tests -q
git diff --check
```

**Notes / pitfalls**

- Documentation-only work: check the claims against `effects/mycelium.py`
  after Task 1, not against this plan.
- Do not edit `CHANGELOG.md`, `TODO.md` or `PLAN.md`; the parent owns them.

### Task 4 — Changelog, status and final verification  `status: done`
**Depends on:** Tasks 1, 2, 3

**Goal**
Record the rebuild in the project's own bookkeeping files and verify the whole
change.

**Steps**

1. `CHANGELOG.md`: add a `## [Unreleased]` section at the top, in the file's
   existing style, with a `### Changed` entry dated 2026-11-12 describing the
   rebuild and a `### Fixed` entry only if a real defect was fixed. The
   historical sections stay untouched; the shared `check-changelog` script
   already fails on the historical format and stays a separate follow-up.
2. `TODO.md`: record the rebuild under the current maintenance section with
   the actual verification results, and add the changelog-format migration and
   the `docs/plans/` archive note as follow-ups.
3. Run the shared verification gate and record the real numbers.

**Acceptance criteria**

- [ ] `CHANGELOG.md` has an `Unreleased` section describing the rebuild.
- [ ] `TODO.md` records the rebuild, the checks that were run and their
      results, and the two follow-ups.
- [ ] The full unit suite, the smoke test and `git diff --check` pass, and
      the measured frame times are recorded.

**Verification**

```bash
SDL_VIDEODRIVER=dummy SDL_AUDIODRIVER=dummy PYTHONDONTWRITEBYTECODE=1 \
  .venv/bin/python -m unittest discover -s tests -q
SDL_VIDEODRIVER=dummy SDL_AUDIODRIVER=dummy PYTHONDONTWRITEBYTECODE=1 \
  .venv/bin/python smoke_test.py
git diff --check
```

## Definition of done

- [ ] All four tasks marked `done`.
- [ ] The project test command passes (`unittest discover -s tests -q`).
- [ ] `smoke_test.py` passes.
- [ ] No file outside the file table changed, except `docs/plans/` and the
      untracked `FINDINGS.md`, which stays untouched.
- [ ] `CHANGELOG.md` and `TODO.md` describe the actual changes.
- [ ] The measured frame times are at or below the Task 1 targets.
