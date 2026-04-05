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

### VortexMode
Port directly for the fireworks mechanics (rockets + embers with gravity/drag).  The pygame pixel-feedback zoom-rotate wormhole (`pygame.transform.rotozoom`) requires FBO and is **not ported** — replaced with `draw.fadeBlack(15f/255f)` giving ~17-frame persistence on the framebuffer.  Embers use `setAdditiveBlend()`.

If FBO support is ever added to GLDraw, the wormhole can be re-added as: each frame zoom+rotate the previous FBO texture onto the current FBO, multiply down by 240/255.

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
