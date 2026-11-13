# psysuals → AndroSaver Port Reference

## Current reference and registry

The `psysuals/` subtree matches upstream main
`40711a58911075a23c4d84be94b0fc0ea295a452` (v3.23.0 plus Caustics and
Lanterns). It is reference source, not an
Android runtime dependency. Importing Python does not update Kotlin effects.

The Android registry contains 36 modes. Preserve Mycelium's name and existing
slot: zero-based index **16** (17th entry), between Lattice and Magnetar, in
`VisualizerRenderer.modes` and both `viz_effect_entries`/`viz_effect_values`
arrays. Horizon, Caustics and Lanterns follow ChromaWeave as in upstream; Spectrum
and Waterfall remain last. Android effect preferences are name-based, so the
upstream nameless-settings migration is not needed, and `VisualizerSelection`
adds newly known modes to a saved selection.

## Mycelium port

`MyceliumMode.kt` now renders the psysuals 3.21 hyphal web. The colony model
lives in `MyceliumColony.kt` without any GL state, so its behaviour is checked
in the JVM suite: three fruiting bodies, violet tapered filaments on an opaque
indigo ground, cyan light packets that hand over at the junctions, amber spores
that germinate, and a heartbeat that keeps light travelling in silence. Nodes,
edges, tips, packets and spores live in bounded pools, and a resize re-lays out
the live network instead of rebuilding it.

Desktop timings in the upstream changelog are not Android measurements.

## Corridor, Mobius and Chromatic ports

`CorridorMode.kt`, `MobiusMode.kt` and `ChromaticMode.kt` follow the psysuals
3.22 reworks. Corridor is a solid corridor of perspective panels painted far to
near, with ribs, gates, spectrum-driven wall bars, a ceiling strip, lane lines
and beat pulses; it repaints every frame and keeps no spark history. Mobius is
a depth-sorted, shaded ribbon that stands upright in the plane of the screen.
Chromatic draws each ripple as six contiguous spectral bands around one shared
outline. All three use filled quads through `GLDraw.polygon`; none allocates
while drawing.

## psysuals 3.23 and Caustics/Lanterns ports

- `HorizonMode`, `CausticsMode` and `LanternsMode` are native ports. Caustics
  evaluates the upstream height field's Hessian analytically in a GLES2 shader
  instead of a blurred 256-wide CPU grid; Lanterns draws its gradients as
  colour bands and its glow sprites as additive circles. All three use
  `EffectDynamics`, elapsed time and fixed pools.
- `SpectrumLevels` is the shared spectrum helper for Corridor and Horizon;
  non-finite bins read as silence.
- `AudioBandLevel` ports the 3.23 `BandLevel` mid/treble averages: seeded from
  the first second, held through pauses judged against the noise floor, quick
  catch-up after silence, ratio clipped to 0..6. Android still publishes the
  deviation (ratio − 1). The NaN-sample, DC-offset, device-switch and
  stalled-stream fixes concern desktop capture; the Visualizer API delivers
  8-bit mix data and owns its own lifecycle.
- Effect fixes: Magnetar and FlowField re-seed a small share of particles per
  frame (Magnetar also those at the core); ChromaWeave accumulates its weave
  phase; Butterflies keep their size over resize round trips; Bubbles caps
  swell and radius; Spectrum, Waterfall, Spiral, Bubbles and Aurora tolerate
  short, empty or non-finite input and very narrow surfaces.
- Not applicable: the GL rotation flip, CPU trail-fade resize, preset morph
  frames and uniform-location handling (GLES ignores location -1). FlowField's
  GL fade already reaches black.

## Standing Android adaptations

- GLES2 shaders, bloom and geometry replace pygame/ModernGL integration. Normal
  playback performs no framebuffer readback. Switching effects clears scene,
  bloom and presentation buffers; there is no outgoing-effect crossfade.
- `EffectDynamics` supplies bounded elapsed-time response for effects that use it;
  Auto/Laptop/TV profiles remain explicit. Do not assume every effect uses it.
- Keep the user-requested nine-ring Chromatic cap; upstream keeps five. Drops
  stay local and land where the frame is emptiest. Android uses 60 outline
  steps per ripple instead of 96 to bound the vertex count.
- LiquidLight is a native bounded dye simulation: a coarse velocity grid
  advects dye and heat with a semi-Lagrangian backtrace, beats inject
  vortices, and the grid is uploaded as a texture and shaded. It replaces the
  earlier analytic field, which read like Plasma and Phason. It is not the
  desktop fluid solver.
- Cymatica evaluates the Chladni plate per pixel in a GLES2 shader, so the
  nodal lines stay smooth instead of showing the shared field grid. The six
  mode weights and the descending sand grains match upstream.
- SlimeMold seeds its agents across the whole frame instead of a central blob,
  and its colour cycle follows the music faster. The simulation stays capped
  at 1080p; its 4K output does not imply full-4K simulation.
- Ferrofluid's peaks bounce on a spring driven by the beats and its skin
  carries a full spectrum instead of a single hue.
- Tunnel's ride speed, tube width and winding follow the music, with a beat
  kick that surges and settles.
- Phason's wave directions and swell drift with the music; Tesseract keeps a
  bounded geometry trail like Cube.
- Persistence pulses its solids with the music and cycles its colours faster.
- Magnetar includes its native contour field plus bounded particles. The field
  and the particles share one level: the scene fades toward black and both are
  added into that same buffer, so the contours read as light rather than as a
  translucent plate over the scene. The band drift matches the calmer upstream
  value; the field is scaled down natively because the retained buffer
  accumulates it.
- Retain documented host tuning: FlowField's
  attraction/scatter, Fireworks' gain-aware launches and Lattice's center-out
  frequency assignment. Match behavior without discarding deliberate native limits.
  Lattice's center-out assignment was Android-only while the vendored upstream
  snapshot mapped bins from the left edge; upstream psysuals adopted the same
  mapping on its `dev` branch after 3.21.0.
- Kotlin and NumPy random sequences and rasterization need not match pixel for
  pixel. Desktop window controls, overlays, recipes and BPM adapters are separate
  from Android settings, audio permissions and lifecycle handling.

## Verification boundary

Android dev commit `41c5e659ef45d3899b117d858cb0c8198fd0a1fd` passed JVM tests and
API 29/35 emulator integration checks, then published dev 2.12.10 (391) in
[run 43310096483](https://github.com/Whichcraft/androsaver/actions/runs/43310096483).
This verifies the existing native implementation, not a native 3.21 Mycelium
rewrite. The optional FPS survey is skipped by default. No physical-stick FPS
result is recorded here. Android builds run in GitHub Actions, not locally.

## Measuring the actual TV stick

### Quick check with the installed dev build

The dev visualizer already displays mode, actual viewport, smoothed frame time,
FPS, vertex count and bloom state. This is observed frame cadence, including
frame pacing; it is not an isolated GPU timer. Stable builds omit this overlay.

Select one effect instead of automatic rotation. Record the viewport and select
the same visual profile and bloom setting for before/after comparisons. Use the
same audio and intensity, warm up the stick, then observe several minutes of
silence and sustained music. Record typical FPS/frame time and visible stutters,
including whether performance drops as the stick warms. Repeat the comparison.
A 60 FPS frame budget is about 16.7 ms; 30 FPS is about 33.3 ms. The overlay's
smoothed average cannot establish p95 or expose every individual dropped frame.

### Repeatable render survey over ADB

The existing `VisualizerPerformanceIntegrationTest.reportAllModeFrameTimesAt1080p`
runs all 36 effects at 1920×1080 with synthetic audio, 32 warmup frames and 16
measured frames per effect. It synchronizes GL completion and reports renderer,
profile, bloom, median/p95/max milliseconds and uncapped FPS as `FPS_SURVEY`
lines. This is an offscreen render-throughput survey, not TV presentation FPS
or a sustained thermal test. Use `fpsSurvey=true` and `fpsProfile=tv`.

The stick can remain plugged into the TV. Connect a computer on the same LAN
using the stick's supported developer/ADB debugging option. Some devices offer
wireless pairing; others expose a vendor-specific network-debugging connection.
Use the address/port shown by the device and approve its authorization prompt.
See [Android's ADB connection guide](https://developer.android.com/tools/adb).

This survey requires a matching app APK and instrumentation-test APK, not just
the published release APK. Current CI uploads device reports, not these APKs.
Before running on the stick, prepare the paired APKs in GitHub Actions, preferably
with a separate benchmark application ID to preserve the installed app and its
settings. Confirm the installed runner with `adb shell pm list instrumentation`.
Run the selected test through `adb shell am instrument` with the arguments above
and collect `FPS_SURVEY` lines from logcat. See
[Android's instrumentation command guide](https://developer.android.com/studio/test/command-line).

For reliable before/after decisions, extend the survey beyond its current short
sample window and include a warmed-up on-screen run at the stick's actual
viewport. Software-emulator rankings and desktop results cannot substitute for
measurements on this hardware.
