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
| `np.mean(fft[:6])` | `fft.meanSlice(0, 6)` |
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
**Mutual pursuit spiral** (added v2.7.0): Solo butterfly steers toward Love's offset point (at `orbitAng + PI` on orbit radius), Love steers toward Solo's offset point (at `orbitAng` on orbit radius).  Orbit radius starts at 240 px and decrements 0.06 px/frame toward 40 px.  Both butterflies are 70% of the upstream scale (solo 7.2→5.04, love 6.84→4.79).  Wing-sync threshold scales with the new size.

**Wander breaks** (added v2.10.0+): `ButterflyPair` has two fields — `breakCd` (initial 800–1600) and `breakTimer` (initial 0).  Each orbit frame: if `breakTimer > 0`, decrement it (free-wander phase); else decrement `breakCd`, and when it reaches 0 set `breakTimer = 200–500`, `breakCd = 900–1800`, `orbitR = min(orbitR + 80, 200)`.  While `breakTimer > 0`, both butterflies call `update(bass, beat)` with no `chasePos` instead of the orbit code.

Do **not** port the psysuals `orbit_pos` fixed-point orbit logic — the Android version uses a different mutual-chase implementation that is equivalent in result but avoids the psysuals helper function.

### AuroraMode
Port of `effects/aurora.py`.  Key differences:

- **DEFS encoding** — Python uses a nested list of tuples; Android encodes each ribbon as a flat `FloatArray(11)`: `[y_frac, hue_off, k0, spd0, aw0, k1, spd1, aw1, k2, spd2, aw2]`.  Offsets 2/5/8 = k_mult, 3/6/9 = speed, 4/7/10 = amp_weight.
- **Geometry caching** — Python recomputes `xs` and `ks` in `__init__` once.  Android caches them in `initGeom(W: Int)` keyed on `draw.W`; recomputes only when width changes (e.g. orientation change or mode resume on a different screen).
- **No pygame surface** — Python draws all ribbons to a temporary `pygame.Surface` and blits additively to the main surface.  Android calls `draw.setAdditiveBlend()` once before the ribbon loop and `draw.setNormalBlend()` after; all ribbons are drawn directly in additive mode.
- **Edge lines** — Python calls `pygame.draw.lines(..., width=2)` for each ribbon's top edge.  Android uses `draw.lineStrip(topPts, r, g, b, 1f)` — no width parameter, effectively 1 px.  The top-edge FloatArrays are accumulated during the ribbon loop (before the blend-mode switch) and drawn after.
- **Type safety** — `initGeom` receives `W: Int` but internally uses `W.toFloat()` for all float arithmetic.  `draw.W.toFloat()` is used for `W` in `draw()`.  Never mix typed Int variables with Float arithmetic without explicit `.toFloat()`.

### LatticeMode
Port of `effects/lattice.py`.  Key differences:

- **Grid data** — Python uses a list of dicts.  Android uses parallel `FloatArray`/`IntArray` fields (`nodeOx`, `nodeOy`, `nodeCol`, `nodeHOff`) allocated once at `N_NODES = 126` capacity.  Rebuilt in `initGrid(W, H)` keyed on `cachedW/cachedH`.
- **Per-frame scratch arrays** — `sxArr`, `syArr`, `bright` are pre-allocated `FloatArray(N_NODES)` fields reused each frame to avoid GC.
- **fftBin mapping** — Python: `int(col / (COLS-1) * min(fft_len-1, int(fft_len * FFT_USE)))`.  Android: `(col.toFloat() / (COLS-1).toFloat() * maxBin.toFloat()).toInt()` with `maxBin = min(fftLen-1, (fftLen.toFloat() * FFT_USE).toInt())`.  Explicit `.toFloat()` at every Int×Float boundary.
- **Scale breath** — Python: `self._scale = max(0.90, min(self._scale + self._svel, 1.12))`.  Android: `scale = (scale + svel).coerceIn(0.90f, 1.12f)`.
- **Double-stroke beams** — Python draws width-3 (dark) then width-1 (bright) `pygame.draw.line`.  Android calls `draw.line(...)` twice at the same coordinates with different lightness values and alpha 0.65 / 1.0.  The visual result is equivalent on a dark background.

### TriFluxMode
No `TRAIL_ALPHA` surface management needed — `draw.fadeBlack(28f/255f)` covers
it.  Two-pass draw (non-active tiles first, then active on top) replaces the
psysuals z-order that comes for free from direct surface drawing.

### BranchesMode
Port directly.  `draw.fadeBlack(10f/255f)` for trail persistence (matches `TRAIL_ALPHA=10`).

---

## Checklist after every psysuals import

1. Read `psysuals/effects/__init__.py` — check MODES list order; update
   `VisualizerRenderer.kt` and `arrays.xml` to match.
2. For each changed / new effect file, diff against the current Kotlin port and
   apply parameter changes using the table above.
3. Apply per-effect standing adaptations from this document.
4. Build (`./gradlew compileDevReleaseKotlin`) and fix any compile errors.
5. Update `CHANGELOG.md`, `docs/visualizer-modes.md`, and `docs/architecture.md`.
6. Run `qmd update && qmd embed`.
7. Commit on `dev`.
