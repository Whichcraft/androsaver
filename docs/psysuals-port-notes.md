# psysuals → AndroSaver Port Notes

## Reference and active registry

`psysuals/` exactly vendors upstream main `0579390b296530fe05fbccf1eebaa37e7aa22952`
(psysuals 3.20.0). The Android Kotlin/GLES backport follows its 32-effect
registry and latest effect changes. Morphogenesis and Mandelbox have been
removed from implementations, rotation and settings. Spectrum and Waterfall
remain last. Stored enabled-mode names are intersected with the new registry;
removed selections fall back safely without shifting numeric preference IDs.

The earlier source-only import is superseded by this Android backport. Desktop
pygame/ModernGL integration code remains vendored reference code, not an Android
runtime dependency. Native changes never edit the upstream subtree.

## Elapsed time, response and profiles

The fourteen upstream `TIME_BASED` effects use elapsed render seconds in 60 Hz
units, clamped to 0–0.1 seconds to bound stalls. `EffectDynamics` applies the
upstream exponential smoothing rates: 0.035 for geometry/motion and 0.25 for
light accents. NaN/infinite audio is discarded and responses are bounded to 1.5.

Settings offers Auto, Laptop and TV profiles independently of viewport size.
Auto uses TV at widths of at least 2560 pixels, otherwise Laptop. Laptop uses
motion 0.65/light 1.0; TV uses motion 0.85/light 1.3. Intensity remains the
separate beat-gain control. Capture data is copied under its lock; timing and
profile are render-owned and never mutate capture buffers.

Time-based effects are Cube, TriFlux, Lissajous, Tunnel, Plasma, Lattice,
Mobius, Chromatic, Persistence, Synapse, Hyperbolic, Cymatica, Phason and
Ferrofluid. Other effects retain their upstream frame-based dynamics.
Lissajous and Cube trail histories sample in 60 Hz units rather than tying
trail duration to display FPS. Spring integration in TriFlux uses bounded
substeps. The Android minimum trail decay (48/255 at 60 Hz) is retained and
scaled exponentially for elapsed time.

## Composition and effect changes

Every pending effect change discards queued geometry and clears the scene FBO,
both bloom FBOs and the display framebuffer to opaque black on the GL thread.
Two blank presentation frames cover retained buffers before the next effect
renders. There is no outgoing-effect crossfade. Surface resizing also clears
new attachments while preserving CPU simulation state where applicable.

GLES scene composition is already opaque and does not use pygame RGB-only
surface alpha. Thus upstream's CPU alpha correction maps to the existing
opaque final GLES composite. Fireworks never reads the framebuffer back to the
CPU. Its Android fade/geometry trails remain the intentional alternative to
pygame's CPU pixel-feedback zoom. No raw GPU/CPU framebuffer readback is used
in normal playback; readbacks are confined to device regression tests.

## Effect-specific backports

| Effect | 3.20 behaviour |
|---|---|
| Cube | Smoothed angular speeds/scale, no random vertex jitter, viewport-scaled projection, fitted cubes/satellites and gentler independent orbits |
| TriFlux | One activation per beat edge with cooldown, elapsed-time lifetimes, substepped springs, whole active-triangle fitting, retained active state on resize, and alpha-fringed antialiased outlines |
| Lissajous | Stable 3:2:5 knot, 60 Hz trail sampling, smoothed rotation/scale, wider bounded projection and no lines across discontinuities |
| Tunnel | Slow audio-independent camera curvature, bounded accumulated triangle spawning, stable ring IDs/star shapes, gentle travel and rotation |
| Plasma | Slow phase/hue, smoothed bounded domain warp, reduced audio frequency/hue modulation and separate light accents |
| Butterflies | Interior spawns, center-directed edge avoidance, bounded pair orbit centers, viewport-scaled sprites and retained positions on resize |
| FlowField | Scale existing particle coordinates on resize without reseeding or changing population |
| Fireworks | Height-relative launch/gravity, rescaled live rockets/embers/trails on resize, no GPU readback |
| Aurora | Rebuild viewport-dependent coordinates without resetting phases; empty/short FFT inputs remain safe |
| Lattice | Profile-based 22×14 Laptop / 14×9 TV grids, smooth scale and motion, elapsed-time peak decay |
| SlimeMold | Preserve agents and bilinearly resample the chemical field on resize |
| Mobius | Slow smoothed geometry, light shiver, whole-strip fitting with a glow margin |
| Chromatic | Slow radius/phase, elapsed-time decay, display-scaled RGB separation/warp and resize preservation; keep the user-requested nine-ring cap |
| Persistence | Five rigid solids, smooth axis reversals with cooldown, bounded speed accents, colour drift and no fourth-coordinate deformation |
| Synapse | Gradual oldest-node retirement and branch births near active parents, retained signals on surviving edges, elapsed-time motion and resize preservation |
| Hyperbolic | Connected dual-icosphere mesh (162 shared pentagon/hexagon tiles), independently lifting/hinging caps, shaded walls, travelling colours and front-horizon clipping |
| Cymatica | Smoothed six-band spatial weights, no argmax jumps, actual nodal-gradient particle descent and bounded sand |
| Phason | Full-resolution GLES seven-wave interference, slow phase and bounded pulse; no audio-driven wave-count jumps |
| Ferrofluid | Full-resolution GLES metallic droplets, connected liquid envelope, triangular magnetic peaks, analytic gradients and specular/rim lighting |

## Intentional Android differences

- Python's ModernGL shaders are adapted to GLSL ES 1.00 with guarded fragment
  precision. Phason/Ferrofluid use the actual scene framebuffer dimensions and
  rebuild GL handles after context recreation without reading pixels back.
- Bounded native simulation/draw budgets remain in place: SlimeMold uses 2,500
  agents and a reduced chemical grid; FlowField/Fireworks retain their population
  limits; scalar fields interpolate to a capped GLES grid. Desktop's near-full-
  resolution CPU SlimeMold field would be too expensive on TV sticks.
- LiquidLight retains its bounded normalized analytic-field adaptation instead
  of desktop's full CPU fluid solver. Its field/phase do not reset on viewport
  changes, so the upstream resize-preservation intent applies without allocating
  a new full-resolution fluid grid.
- Kotlin random generators are deterministic where already seeded, but their
  sequences need not match numpy. GLES bloom and line rendering differ from
  pygame rasterization; TriFlux uses triangle geometry with alpha fringes for
  portable antialiasing.
- Android omits desktop window/monitor controls, pygame overlays, recipes and
  external audio adapters. The existing Android audio-permission and lifecycle
  diagnostics remain authoritative.

## Retained standing host adaptations

- Corridor draws frames normally and sparks additively on top. Its dedicated
  25-snapshot spark trail remains separate from the frame fade; the Android
  bass spark multiplier remains 5 rather than the desktop 1.2.
- FlowField keeps bass attraction (`delta-to-centre * bass * 0.0018`) and
  per-axis treble scatter (`random(-1,1) * treble * 3.2`) in addition to the
  field-angle displacement. Particle adjustments remain in 2,000-agent steps.
- Fireworks keeps the gain-aware background launch interval:
  `(40 * gain).toInt().coerceIn(20, 200)`. Ember drawing remains additive.
- Lattice retains Android's centre-out frequency assignment (bass near the
  centre, treble near the edges), reusable scratch arrays and double-stroke
  beams. Its grid density now follows the explicit profile rather than the old
  width-only three-tier rule.
- Audio snapshots are copied into reusable render-owned storage under a short
  capture lock. Pausing clears accumulated audio/genre samples while preserving
  whether automatic genre detection is configured. GL work never holds that lock.
- Geometry and direct-upload buffers share a lazy 12 MiB staging pair, with one
  live batch at a time and the existing vertex ceiling. The dev performance
  overlay is throttled and session-guarded; persistent diagnostics stay on top.

## Verification and future imports

The vendored tree passed 127 Python unit tests, headless smoke rendering of all
32 upstream effects and parsing of all 67 Python files. Android checks run only
on GitHub. Added JVM checks cover time invariance, stall/NaN bounds, sphere mesh
connectivity/horizon clipping, field resampling and sustained-beat activation.
Added EGL device checks exercise all 32 native effects, resize, shader visibility,
black switch frames, and scene/bloom/display clearing with bloom on and off.
Physical TV/audio/GPU combinations remain manual validation.

For future updates, compare each changed upstream effect with its Kotlin port,
apply the standing Android limits above, update both registry/resource arrays
and current documentation, and use GitHub for Android builds. If a bug exists
upstream, fix/test it in psysuals first and import that commit before backporting.
