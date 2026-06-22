# psysuals → AndroSaver Port Notes

When importing a new psysuals release, always apply these Android-specific
adaptations after porting each effect.  The differences exist because Android
uses OpenGL ES 2.0 (no persistent surfaces, no per-surface alpha fade), while
psysuals uses pygame surfaces.

---

## Universal rules

| psysuals pattern | Android equivalent |
|------------------|--------------------|
| Per-surface alpha fade (`surf.blit(fade, ...)`) | `draw.fadeBlack(alpha/255f)` once at top of `draw()` |
| Separate persistent surface (e.g. `sat_surf`, `spark_surf`) | Ring-buffer replay or `draw.setAdditiveBlend()` pass |
| `pygame.BLEND_ADD` blit | `draw.setAdditiveBlend()` / `draw.setNormalBlend()` |
| `pygame.draw.polygon(pts, color, width=0)` | `draw.polygon(pts, r, g, b, alpha, filled=true)` |
| `pygame.draw.polygon(pts, color, width=lw)` | `draw.polygon(pts, r, g, b, alpha, filled=false)` |
| `pygame.draw.circle(surf, color, center, r)` | `draw.circle(cx, cy, r, r, g, b, alpha, filled=true)` |
| `pygame.draw.line` | `draw.line(x1, y1, x2, y2, r, g, b, alpha)` |
| `config.WIDTH / HEIGHT` | `draw.W / draw.H` |
| `beat` (bass signal) | `audio.beat` |
| `config.MID_ENERGY` | `audio.mid` (deviation above rolling avg, bins 20–99; 0 at steady state, positive on peaks) |
| `config.TREBLE_ENERGY` | `audio.treble` (same for bins 100–255; 0 at steady state, positive on peaks) |
| `np.mean(fft[:6])` (old pre-v3.4.0 pattern) | `audio.beat` (use `audio.mid`/`audio.treble` for frequency bands) |
| `hsl(h, l=x)` | `GLDraw.hsl(h, 1f, x)` → FloatArray(3) |

---

## Per-effect standing adaptations

### TunnelMode
Port directly from psysuals `effects/tunnel.py`.  No special delta needed —
match parameters exactly (TUBE_R, dt formula, spawn rate, triangle size).
The v1.4.3 enhancements (bass-expanded tube, higher reactivity) were reverted
upstream in v2.0.0; always track the canonical psysuals version.

### CubeMode
No persistent `sat_surf`; use a **15-frame ring-buffer** for the satellite
trail (v2.0.2: `_SAT_FADE` doubled → half the persistence), drawn with
`setAdditiveBlend()`.

**Base rotation constants must stay at v1.4.x values** (`0.00025/0.00035/0.00018`)
even when psysuals raises them.  The psysuals values (`0.00165/0.00248/0.00083`)
are ~6× higher and cause the inner cube to spin erratically at default intensity
on a full-screen TV display.  Keep only the audio-reactive multipliers
(`mid*0.012`, `bass*0.015`, `high*0.008`, `beat*0.10/0.12/0.05`), damping
(`×0.94`), and velocity clamp (`rvx/rvy ±0.08`, `rvz ±0.05`) from upstream.

### CorridorMode
No persistent `spark_surf`; draw frames first (normal blend), then draw sparks
with `setAdditiveBlend()` so sparks are always on top.  Spark spawn rate from
psysuals uses `bass * 1.2`; raise to `bass * 5f` in Android because beat
signal tends to be lower and sparks were invisible at low intensity.

**Spark trail ring buffer** — psysuals uses a dedicated `spark_surf` faded at
`_SPARK_FADE=10` (≈0.039f), giving ~25 frames of persistence vs. the 9 frames
from the main `fadeBlack(0.11f)`.  Android replaces this with a 25-frame ring
buffer of per-frame spark screen snapshots (sx, sy, r, h, bright) replayed with
`setAdditiveBlend()` at linearly decreasing alpha.  Do not remove this ring
buffer or merge it into the main fade — sparks must trail significantly longer
than the corridor frames.

### FlowFieldMode
Port directly.  `draw.fadeBlack(8f/255f)` replaces `BLEND_RGB_MULT(247/255)` — equivalent on a dark background.  Particles drawn with `setAdditiveBlend()` as tiny circles (radius 1.5f, segments=4).  No numpy; particle positions held in plain `FloatArray(N)`.

Seed detection: particles are initialised on the first draw call when W/H are known (tick==0 or all-zero check).

**Bass gravity + treble scatter** (added v2.8.0): per-particle, apply two extra forces each frame:
- Bass attract: `(W*0.5 - px) * bass*0.0018` (toward centre)
- Treble scatter: `Random(-1,1) * treble*3.2` (random direction per axis)

Port these identically from psysuals `effects/flowfield.py`.  Both forces are additive to the normal field-angle displacement.

### VortexMode
Port directly for the fireworks mechanics (rockets + embers with gravity/drag).  The pygame pixel-feedback zoom-rotate wormhole (`pygame.transform.rotozoom`) requires FBO and is **not ported** — replaced with `draw.fadeBlack(15f/255f)` giving ~17-frame persistence on the framebuffer.  Embers use `setAdditiveBlend()`.

**Gain-aware interval** (added v2.7.0): `interval = (BASE_INTERVAL * audio.gain).toInt().coerceIn(20, 200)` where `BASE_INTERVAL = 40`.  `audio.gain` is the `beatGain` multiplier passed in via `AudioData`.  Do not port the psysuals version's fixed `LAUNCH_INTERVAL` — the Android version intentionally scales with gain.

Note: `GLDraw` now has FBO bloom support, but the vortex wormhole is still not ported — bloom is a post-processing effect applied to all modes, not a per-mode FBO blit.

### ButterfliesMode
**Mutual pursuit spiral** (reverted to stable version in v3.10.0): Solo butterfly steers toward Love's offset point (at `orbitAng + PI` on orbit radius), Love steers toward Solo's offset point (at `orbitAng` on orbit radius). Orbit radius starts at **240 px** and decrements 0.06 px/frame toward 40 px. **No size variations or swarm forces**: all pairs use the standard sizes (solo 5.04, love 4.79) for stable, clean movement without the clutter of swarm separation/cohesion dynamics. **Unidirectional wing sync**: partner `lv.wingPhase` adjusts toward solo `sl.wingPhase` (`diff * sync * 0.12f`).

**Wander breaks**: `ButterflyPair` has two fields — `breakCd` (initial 800–1600) and `breakTimer` (initial 0). Each orbit frame: if `breakTimer > 0`, decrement it (free-wander phase); else decrement `breakCd`, and when it reaches 0 set `breakTimer = 200–500`, `breakCd = 900–1800`, `orbitR = min(orbitR + 80, 200)`. While `breakTimer > 0`, both butterflies call `update(bass, beat)` with no `chasePos` instead of the orbit code.


### AuroraMode
Port of `effects/aurora.py`.  Key differences:

- **DEFS encoding** — Python uses a nested list of tuples; Android uses a list of `RibbonDef` / `Harmonic` data classes.
- **Geometry caching** — Caches `xs` and `ks` arrays, rebuilt only when `draw.W` changes.
- **No pygame surface** — Python draws all ribbons to a temporary `pygame.Surface` and blits additively to the main surface.  Android calls `draw.setAdditiveBlend()` once before the ribbon loop and `draw.setNormalBlend()` after.
- **Edge lines NOT ported** — psysuals removed sharp top-edge line drawing in v3.5.x.  The Android port never drew them.  `savedTopPts`/`savedHues` fields removed in v3.7.0 backport (they were dead code).

### LatticeMode
Port of `effects/lattice.py`.  Key differences:

- **Dynamic grid** (v3.7.0): grid density scales with display width — `14×9` (default), `18×12` (≥1600px), `22×14` (≥2560px).  Implemented as `gridCols(W)` / `gridRows(W)` helper functions.  Column count passed to `getBin()` so center-out mapping scales with grid size.
- **Center-out frequency mapping** (v3.7.0): `getBin(col, nCols, fftLen)` maps center columns to low frequencies (bass) and edge columns to high frequencies (treble).  `normalized = abs(col - center) / center`.
- **Grid data** — Python uses a list of dicts.  Android uses a `Node` data class list, rebuilt when `lastW/lastH` change.
- **Per-frame scratch arrays** — `sxArr`, `syArr`, `bright` are allocated per-frame (size matches `nodes.size`).
- **Double-stroke beams** — Python draws width-3 (dark) then width-1 (bright) `pygame.draw.line`.  Android calls `draw.line(...)` twice at the same coordinates with different lightness values.  The visual result is equivalent on a dark background.
- **colPeaks guard** — A size mismatch check (`if colPeaks.size != nCols`) ensures peaks are reset after a grid resolution change without requiring a full mode reset.

### TriFluxMode
No `TRAIL_ALPHA` surface management needed — `draw.fadeBlack(28f/255f)` covers
it.  Two-pass draw (non-active tiles first, then active on top) replaces the
psysuals z-order that comes for free from direct surface drawing.

### BranchesMode
Port directly.  `draw.fadeBlack(10f/255f)` for trail persistence (matches `TRAIL_ALPHA=10`).

### FlowFieldMode
Port directly.  `draw.fadeBlack(8f/255f)` replaces `BLEND_RGB_MULT(247/255)`.  Particles drawn with `setAdditiveBlend()` as tiny circles (radius 1.5f, segments=4).  No numpy; particle positions held in plain `FloatArray(N)`.  Upstream edge recycling removed in v3.9.0 (particles wrap natively).

### MyceliumMode
Port directly.  `draw.fadeBlack(8f/255f)` from `TRAIL_ALPHA=8`. Swirling growth pattern around 5 cores (colony centers). Segments use double `draw.line()` (dark + bright). Pre-allocated arrays and cores structure.

### MagnetarMode
N reduced 6 000 → 4 000 for Android performance.  Particles drawn as `draw.circle(radius=1.5f, segments=4)` with `setAdditiveBlend()`.  `_FADE_ALPHA=24` trail decay mapped to `draw.fadeBlack(24f/255f)`.

### SlimeMoldMode
N reduced 10 000 → 2 500; RES_DIV raised 4 → 8 (trail grid ~240×135 for 1080p).  NumPy vectorised sensing and movement replaced with scalar Kotlin loops.  Trail grid rendered as coloured rects.  3×3 diffusion approximated with 5-tap cross kernel.

### CliffordMode
N reduced 40 000 → 8 000 for Android performance.  NumPy vectorised map iterations replaced with scalar Kotlin loops over `FloatArray(N)`.  Attractor presets and dynamic framing based on running min/max. `_FADE_ALPHA=18` trail decay mapped to `draw.fadeBlack(18f/255f)`. Particles drawn as tiny circles (radius 1.5f, segments=4, additive blend). Iterates 3 steps per frame.

### MobiusMode
Port directly.  `TRAIL_ALPHA=15` → `draw.fadeBlack(15f/255f)`.  NumPy 3-D rotation and perspective projection replaced with Kotlin FloatArray loops; scratch `pts2d` array pre-allocated (longitude wires and scratch `pts2dV` removed in v3.9.0).

### ChromaticMode
Port directly. Wavy raindrop ripples outline. `_FADE_ALPHA=24` → `draw.fadeBlack(24f/255f)` replaces `BLEND_RGB_MULT(232,228,236)`. Closed polygons drawn with RGB-separated offsets and custom sine-wave ripple function.

### PersistenceMode
Port directly.  `TRAIL_ALPHA=5` → `draw.fadeBlack(5f/255f)`.  `draw.polygon()` for both glow (wireframe) and col (wireframe) passes.

### SynapseMode
Port directly.  `TRAIL_ALPHA=18` → `draw.fadeBlack(18f/255f)`.  Signal pulses and node glows drawn with `setAdditiveBlend()` circles. Outgoing edge lists pre-calculated; signals capped at `MAX_SIGNALS=240` and fan-outs limited to prevent runaway cascades.

### HeartbeatMode
Port directly.  `TRAIL_ALPHA=20` → `draw.fadeBlack(20f/255f)`.  Ring polygon drawn with 120-point `FloatArray` passed to `draw.polygon()` twice (glow + col).

---

## Checklist after every psysuals import

1. Read `psysuals/effects/__init__.py` — check MODES list order; update
   `VisualizerRenderer.kt` and `arrays.xml` to match.
2. For each changed / new effect file, diff against the current Kotlin port and
   apply parameter changes using the table above.
3. Apply per-effect standing adaptations from this document.
4. Build (validated via GitHub CI/CD pipeline on push, or locally with appropriate SDK configuration) and fix any compile errors.
5. Update `CHANGELOG.md`, `docs/visualizer-modes.md`, and `docs/architecture.md`.
6. Run `qmd update && qmd embed`.
7. Commit on `dev`.
