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
No persistent `sat_surf`; use a **30-frame ring-buffer** for the satellite
trail, drawn with `setAdditiveBlend()`.  Everything else ports directly.

### CorridorMode
No persistent `spark_surf`; draw frames first (normal blend), then draw sparks
with `setAdditiveBlend()` so sparks are always on top.  Spark spawn rate from
psysuals uses `bass * 1.2`; raise to `bass * 5f` in Android because beat
signal tends to be lower and sparks were invisible at low intensity.

### TriFluxMode
No `TRAIL_ALPHA` surface management needed — `draw.fadeBlack(28f/255f)` covers
it.  Two-pass draw (non-active tiles first, then active on top) replaces the
psysuals z-order that comes for free from direct surface drawing.

### BranchesMode
Port directly.  `draw.fadeBlack(16f/255f)` for trail persistence.

---

## Checklist after every psysuals import

1. Read `psysuals/effects/__init__.py` — check MODES list order; update
   `VisualizerRenderer.kt` and `arrays.xml` to match.
2. For each changed / new effect file, diff against the current Kotlin port and
   apply parameter changes using the table above.
3. Apply per-effect standing adaptations from this document.
4. Build (`./gradlew compileDevReleaseKotlin`) and fix any compile errors.
5. Update `CHANGELOG.md` and `docs/visualizer-modes.md`.
6. Commit on `dev`.
