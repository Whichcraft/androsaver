# psysuals → AndroSaver Port Notes

## Reference and active registry

`psysuals/` exactly vendors upstream dev `2080070db572a615185f069afbb16c54478702d6`
(the context/performance batch and ChromaWeave after psysuals 3.20.0). The Android
Kotlin/GLES backport follows its 33-effect registry and latest effect changes. Morphogenesis and Mandelbox have been
removed from implementations, rotation and settings. Spectrum and Waterfall
remain last. Stored enabled-mode names are intersected with the new registry;
removed selections fall back to the current registry. Effect preferences remain name-based.

The earlier source-only import is superseded by this Android backport. Desktop
pygame/ModernGL integration code remains vendored reference code, not an Android
runtime dependency. Native changes never edit the upstream subtree.

## Elapsed time, response and profiles

The upstream `TIME_BASED` effects use elapsed render seconds in 60 Hz
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
- Bounded native simulation/draw budgets remain in place. SlimeMold's finer
  texture-backed field and desktop-reference population are documented in the
  performance follow-up below. FlowField/Fireworks retain their population
  limits; other scalar fields interpolate to a capped GLES grid.
- LiquidLight retains its bounded normalized analytic-field adaptation instead
  of desktop's full CPU fluid solver. Its field/phase do not reset on viewport
  changes, so the upstream resize-preservation intent applies without allocating
  a new full-resolution fluid grid.
- Kotlin random generators are deterministic where already seeded, but their
  sequences need not match numpy. GLES bloom and line rendering differ from
  pygame rasterization; TriFlux uses triangle geometry with alpha fringes for
  portable antialiasing.
- Desktop BPM metadata is not available in the native audio snapshot; Lissajous
  uses the existing unit-tempo fallback instead of desktop tempo scaling.
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

Verified native app commit `18661a1d4153d4c9ceec0591e98f31afa7fe8e8c` in
[GitHub run 40414215709](https://github.com/Whichcraft/androsaver/actions/runs/40414215709):
JVM tests across all eight variants and all 16 device tests on each API 29/35
emulator passed, including all-mode EGL rendering, resize and framebuffer clears.
Signed dev APK publication and downloaded-asset integrity passed. The manifest
confirms version `2.12.5`, code `384`, and its immutable APK URL and `dev-channel`
tag both target that app commit. No Android build was run locally.


## Magnetar contour-layer correction

The Android particle port omitted the upstream 3.20 magnetic-potential contour
layer (`psysuals/effects/magnetar.py`, `_draw_contours`). This was an Android-only
backport gap: upstream already renders coloured dipole bands behind its particles.
The native mode now draws those bands with the existing full-screen GLES2 helper
before drawing particles. The shader uses the same softened dipole magnitude,
tanh compression, band phase and sine-channel colour mapping as upstream.

Android keeps its 4,000 particles and existing particle motion/beat shockwave.
The contour shader uses the actual output dimensions and a stable GLES2 expression
for tanh; it allocates no CPU field grid or framebuffer readback during playback.
As an intentional native composition adaptation, the normal-alpha field replaces
the former black trail fade at the same 48/255 floor. Existing particles decay
into the coloured field instead of repeatedly adding the field until it turns
white. Reusable uniform arrays retain the per-frame parameter values. Shader
handles are invalidated on EGL context recreation through FullscreenEffectMode.

Device regression coverage checks visible field coverage with bloom disabled,
resize, sustained audio and reuse across two EGL contexts. The first-frame
coverage threshold exceeds what the 4,000 particles can paint on a 320x240
viewport. Verification is pending the GitHub builder and will be batched with
other changes at the user's request; this fix has not been committed or pushed.
Local source/whitespace review passed. Android JVM/device/build checks have not
been run; no local Android build or physical-device visual acceptance is claimed.


## Slowest-four desktop/Android performance follow-up (verification pending)

The desktop 1080p GL survey identified SlimeMold, Lattice, Hyperbolic and
LiquidLight as its slowest four. Android device rankings have not yet been
measured; these native counterparts are optimization targets, not a claimed
Android bottom-four ranking. Commits, subtree import, CI and publication remain
deferred for the user's combined batch. This section supersedes the old
SlimeMold 2,500-agent/coarse-rectangle adaptation.

- **SlimeMold:** replace per-cell rectangles/HSL conversion with one luminance
  texture upload and a full-screen GLES2 colour pass using linear filtering.
  Use the desktop softened trail palette, Gaussian central population,
  simultaneous sensing-before-deposit and wrapped diffusion. Initial density
  follows desktop's 5,000–26,000 agents and survives resize. The simulation is
  full detail through 1920x1080; 4K uses a capped 1920x1080 field (two physical
  pixels per sample), not the former six/eight-pixel rectangles. Sampling and
  motion account for that physical scale. Normal playback reads no framebuffer.
  CPU steering shares heading trigonometry; neighbour indices and upload buffers
  are reused. A JVM check covers the detail/cap/resize policy; device coverage
  checks linear filters, desktop sine-palette colours, visibility and recreation.
- **SlimeMold intentional differences:** chemical values are uploaded as 8-bit
  normalized intensity with at most 1/255 truncation, then filtered in GLES2.
  Kotlin/Java and NumPy RNG streams are different; exact agent positions are not
  promised. Native's 1080p simulation cap preserves a TV memory/CPU budget and
  does not claim full 4K simulation parity or verified TV FPS.
- **Lattice:** sum coincident additive beam colours and submit each identical
  GL_LINES segment once instead of three times. Nodes and grid density remain.
  An active laptop grid submits 580 beam segments instead of up to 1,740.
  Intermediate 8-bit framebuffer rounding can differ slightly; the summed
  light contribution is retained. Cache the frame's common peak-decay factor.
- **Hyperbolic:** cache rotation trigonometry per frame, static hinge geometry
  and longitudes, and precompute sort depths once instead of allocating lists
  and averaging them during every sort comparison. Geometry/detail is retained.
  The pending native source now also follows upstream's stronger bass-pulse
  lift, faster tile response, brighter faces and treble edges. A common cap/wall
  projection scale keeps raised tiles within a four-pixel viewport margin.
  Pulse/release and loud-input bounds tests are prepared, not yet run. The
  upstream source/subtree import remains deferred to the combined batch.
- **LiquidLight:** cache row sine and column cosine terms once per frame,
  reducing those repeated inner-wave evaluations from 4,608 to 100 while
  retaining native's normalized analytic-field adaptation and render grid.

Native JVM/device tests and the opt-in all-33-effect 1080p survey are prepared,
but have not run. In the GitHub builder/device environment, enable the latter
with `./gradlew connectedDevStandardDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.fpsSurvey=true`.
It records 32 warmup and 16 timed frames per effect, GL completion, median,
linearly interpolated p95, maximum and uncapped throughput, with actual
backend/viewport/bloom state and profile/audio-fixture metadata. The p95 uses
NumPy's linear interpolation convention, rather than labelling the maximum as
p95. The test requires 33 unique modes and finite positive timing samples, with
no wall-clock speed thresholds. The default profile is laptop; for the physical
TV comparison add `-Pandroid.testInstrumentationRunnerArguments.fpsProfile=tv`.
Only laptop and tv are accepted. A software-emulator ranking is not a physical TV
GPU ranking; collect the same survey on the target device before making that
claim. Default device tests explicitly skip this optional longer survey.

Desktop optimizations and exact sampled pixel comparisons are tracked in the
upstream worktree TODO.md. Its new source commits/subtree sync are deferred;
this native patch does not change the existing vendored reference revision.


Local review status: native source and whitespace checks passed; compilation,
JVM/device checks and native FPS measurements are still pending. Desktop's
corresponding changes passed 207 tests, all 32 smoke checks and exact sampled
CPU/EGL rendering comparisons. Desktop-only paired GL measurements improved
SlimeMold 12.1→22.3 FPS, Lattice 21.8→42.9, Hyperbolic 23.0→40.1 and LiquidLight
29.0→39.9; do not use these as Android performance evidence. SlimeMold's finer
native simulation has more CPU work, so validate net FPS as well as visible
detail on the target TV. No commit/push/build/publication has been performed.


## Remaining-effects performance pass — pending combined verification

All 32 desktop effects were measured on CPU and real EGL. Desktop changes use
raw packed surface uploads with GPU channel swizzling and a padded/non-32-bit
fallback; static Cymatica modes and Magnetar field denominators are cached;
Waterfall colours are vectorized; the full-resolution CPU Ferrofluid, Phason
and Plasma fallbacks process bounded row tiles. No resolution/detail reductions.
Measured desktop results live in upstream TODO.md and are not Android FPS.

Additional native changes, preserving existing effect geometry and simulation:
- GLDraw caches both endpoint directions of common circles (1–128 segments).
  The cache is renderer-owned and bounded; other segment counts use the original
  trigonometry. Both endpoint angles retain the original Float operation order.
  A JVM test compares every submitted coordinate/colour for filled/wire circles,
  repeated draws and fallback/empty segment counts. It has not been run yet.
- Cymatica caches its six fixed 64x36 basis fields and reuses energy storage;
  particle derivatives reuse their trigonometric terms. Analytic basis/reset
  coverage is prepared for the GitHub run.
- Magnetar and FlowField reuse owned particle colour arrays; the shared field
  painter reuses its colour array for Cymatica/LiquidLight, avoiding one array
  per cell. The existing nonallocating HSL overload performs the same math.
- Branches computes colours once per arm/depth and the common depth sway once
  per frame; recursive geometry and both neon passes remain. A JVM work-budget
  test checks all 6,226 line segments for a full seven-level fixture.
- Other native effects retain their current detail and code. The shared circle
  cache benefits existing circle calls, including Bubbles, Mycelium, Fireworks,
  Spiral, Corridor and Lissajous. Existing bounded particle/history/scratch
  storage stays in place. Native Phason/Ferrofluid/Plasma stay shader-based;
  desktop CPU fallback costs do not justify reducing native shader detail.

Android compilation, JVM/device tests, target-TV comparison and the opt-in
all-mode timing survey remain required at the final batch. No local Android
build, subtree sync, commit, push, workflow or publication was performed.


## ChromaWeave — new native mode, pending combined verification

Added after Ferrofluid and before the final Spectrum/Waterfall pair. The native
registry and settings arrays now contain 33 modes. The prior 32-mode survey and
performance results above are historical; the opt-in survey now requires all
33 unique modes. ChromaWeave uses the same three-layer, eightfold warped-ribbon
field and palette equations as the new upstream effect, rendered at full GLES
output resolution. Its owned phase/hue/turn state and reused scalar uniforms
follow the shared bounded motion/light envelopes: bass swells/openings, mid
interference travel, treble cyan sparkles and stronger TV accents.

Desktop GL and NumPy fallback are implemented/tested upstream first. The CPU
fallback has a 640x360-equivalent sample budget, smoothly upscaled with shared
quality control; desktop GL and native GLES keep full output resolution. Native
shader output/profile, resize and context-recreation tests plus pulse-release
contracts are prepared here. Native compilation/JVM/device/TV/FPS checks remain
unrun under the combined-batch instruction; no local Android build or workflow
was triggered. Subtree import and publication remain deferred. Spectrum and
Waterfall move to zero-based indices 31/32; earlier visual indices are unchanged.


## Authorized combined dev batch — 2026-11-11

The user's commit/push/merge authorization supersedes earlier deferral notes.
Upstream 2080070 is committed, pushed and fast-forwarded to dev. Its GitHub
Python 3.10/3.12 unit/smoke workflow passed:
https://github.com/Whichcraft/psysuals/actions/runs/43042966999

The imported subtree tree ID was checked against the complete upstream tree;
all 21 prepared native files were restored byte-for-byte after import, before
this metadata update. The native batch will now be committed/pushed on
effects/chromaweave-performance and fast-forwarded to dev, where the existing
workflow verifies JVM/device tests and builds/publishes the dev APK. These new
Android CI results are pending at this checkpoint. Physical TV acceptance and
the optional fpsSurvey remain distinct from regular CI; do not claim them from
an emulator build. No local Android build and no main/master update.
