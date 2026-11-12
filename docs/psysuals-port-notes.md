# psysuals → AndroSaver Port Reference

## Current reference and registry

The `psysuals/` subtree matches upstream main v3.21.0,
`d7e9af05013fcc05e8add9f9924a5f45668c067f`. It is reference source, not an
Android runtime dependency. Importing Python does not update Kotlin effects.

The Android registry contains 33 modes. Preserve Mycelium's name and existing
slot: zero-based index **16** (17th entry), between Lattice and Magnetar, in
`VisualizerRenderer.modes` and both `viz_effect_entries`/`viz_effect_values`
arrays. The 3.21 rewrite needs no new mode, reordering or preset migration.
Spectrum and Waterfall remain last. Android effect preferences are name-based.

## Outstanding Mycelium port

`MyceliumMode.kt` still renders the earlier five-colony effect with rotating
satellite rings, rainbow filaments, orbiting spores and retained trails. The
3.21 Python rewrite has not been backported. Its native acceptance targets are:

- Three fruiting bodies, violet tapered filaments on opaque indigo ground,
  cyan light packets and amber spores.
- Connected filament junctions, packet handover, spore germination and ongoing
  autonomous light waves in silence.
- Bass-driven growth/body lighting/packet waves, mid-driven swirl/branching/packet
  speed, and treble sparks/spore release.
- Bounded nodes, edges, tips, packets and spores, including successful germination
  under pool pressure. Kotlin's existing caps do not establish new-model parity.
- Preserve and rescale live topology on viewport changes; the current native
  implementation clears and rebuilds it.

Use the Python network regression tests as behavioral references. Add focused
native checks for routing, germination, resource bounds, silence and resizing.
Desktop timings in the upstream changelog are not Android measurements.

## Standing Android adaptations

- GLES2 shaders, bloom and geometry replace pygame/ModernGL integration. Normal
  playback performs no framebuffer readback. Switching effects clears scene,
  bloom and presentation buffers; there is no outgoing-effect crossfade.
- `EffectDynamics` supplies bounded elapsed-time response for effects that use it;
  Auto/Laptop/TV profiles remain explicit. Do not assume every effect uses it.
- Keep the user-requested nine-ring Chromatic cap. LiquidLight retains its
  bounded analytic-field adaptation rather than the desktop fluid solver.
- SlimeMold uses a filtered luminance texture and a simulation capped at 1080p;
  its 4K output does not imply full-4K simulation. Native Phason, Ferrofluid,
  Plasma and ChromaWeave use GLES shader paths.
- Magnetar includes its native contour field plus bounded particles. Circle,
  field, palette and geometry caches are native implementation details; Python
  vectorization improvements are not instructions to reproduce NumPy in Kotlin.
- Retain documented host tuning: Corridor's separate spark history, FlowField's
  attraction/scatter, Fireworks' gain-aware launches and Lattice's center-out
  frequency assignment. Match behavior without discarding deliberate native limits.
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
runs all 33 effects at 1920×1080 with synthetic audio, 32 warmup frames and 16
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
