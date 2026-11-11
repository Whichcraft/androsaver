# TODO

Preserve the current effect registry order and public `Effect.draw(...)` contract.
Every implementation task should include focused tests, documentation updates
where behavior is user-visible, the full unit suite, the headless smoke test,
and `git diff --check`.

## Maintenance pass (PLAN.md)

- [x] M01 — Validate saved post-process settings. Focused: 9 tests; full suite: 128 tests; all 32 smoke effects and whitespace checks passed.
- [x] M02 — Recipes select/restore full-registry backgrounds; Shift+B retains first-nine cycling. Focused: 13 tests; full suite: 131 tests; all 32 smoke effects and whitespace checks passed.
- [x] M03 — Extract CLI parsing into core/cli.py; preserve app wrapper, flags and help. Focused CLI/visual-response: 37 tests; full suite: 137 tests; all 32 smoke effects and whitespace checks passed.

- [x] M04 — Coherent audio snapshots with independent arrays; focused: 24 tests; full suite: 140 tests; all 32 smoke effects and whitespace checks passed.

- [x] M05 — Extract silence hysteresis with injected thresholds/block duration. Focused: 15 tests; full suite: 145 tests; all 32 smoke effects and whitespace checks passed.

- [x] M06 — Named preset/parameter transition state and pure sampling; tick timing, replacement and midpoint gain ordering preserved. Focused: 14 tests; full suite: 149 tests; all 32 smoke effects and whitespace checks passed.

- [x] M07 — Owned audio response state, explicit snapshot/frame/tap inputs and named outputs. Focused: 14 tests; full suite: 153 tests; all 32 smoke effects and whitespace checks passed.

- [x] M08 — Event-batch dispatch extracted; picker/pane/FlowField, modifiers, taps, presets and span restrictions tested. Focused: 9 tests; full suite: 158 tests; all 32 smoke effects and whitespace checks passed.

- [x] M09 — Base effect release delegates detachment; repeat release and CPU-state-preserving rebind verified. Focused graphics/visual tests: 49; full suite: 160 tests; all 32 smoke effects and whitespace checks passed.

- [x] M10 — FrameComposer owns surfaces/composition and GL presentation; UI stays in app. Focused: 53 tests; full suite: 161 tests; all 32 smoke effects and whitespace checks passed. Real CPU/AMD EGL frames matched baseline for Cube, Butterflies, LiquidLight, SlimeMold and Plasma (30 frames each/path); output visually inspected. Desktop presentation equivalence remains unverified.

- [x] M11 — Constructor failures clean acquired resources without saving partial settings; shutdown survives independent release failures. Focused: 23 tests; full suite: 165 tests; all 32 smoke effects and whitespace checks passed.

- [x] M12 — Expanded Butterflies statements and named pairing/update stages. Focused: 34 tests; full suite: 166 tests; all 32 smoke effects and whitespace checks passed. Compared 1200 frames and agent states to HEAD baseline: exact match, including two resizes.

- [x] M13 — Bounded module/effect/test reviews complete. Outcomes below account
  for every scope, including explicit no-change decisions and focused fixes.
- [x] M14 — README/architecture/plan reconciled with implemented ownership,
  contracts, settings and actual verification limits. Final gate passed.

Verification used SDL dummy video/audio and PYTHONDONTWRITEBYTECODE=1 with
.venv/bin/python: unittest focused modules, unittest discover -s tests -q,
smoke_test.py and git diff --check (new files also checked explicitly).
M10 additionally verified actual AMD EGL rendering. Desktop-window presentation,
audio-device switching and multi-monitor checks remain pending.
M01–M14 are complete on dev. M13 accounts for all 32 effects and all 24 test
modules. Final checks: 180 tests, 32 smoke effects/registry checks, compilation
and whitespace passed; reverse module/method order also passed with no config/
palette leaks. Dependencies, versions, registry order and public effect
signatures are unchanged. No commits or publishing were performed.

## Completed in 3.20.0

- [x] Correct GL alpha/composition and clean effect-switch framebuffers.
- [x] Add Laptop/TV profiles, calm motion and preserve supported resize state.
- [x] Tune sound-reactive Persistence, animate Hyperbolic segments, fix Butterfly
  edge trapping and retain fine SlimeMold detail on TV outputs.
- [x] Remove Mandelbox and Morphogenesis; update registry, recipes and docs.
- [x] Verify 127 unit tests, all 32 smoke-test effects, actual AMD GL rendering,
  effect-switch GPU clearing and a 4K SlimeMold framebuffer.

## Major-release work (outside the current improvement queue)

This work is intentionally separate from the current improvement queue. Do not
bump the major version until the prerequisite v4 implementation and review work
is complete.

- [ ] **V4-003 — Prepare and write the v4 major-release announcement**
  - **Scope:** `CHANGELOG.md`, a release section in `README.md`, version badges/help text, and a release checklist; do not publish or tag automatically.
  - **Change:** Draft an energetic but factual announcement explaining why v4 is awesome: the new courtship-and-dance Butterflies system, audio-phase/envelope responsiveness, palette continuity, adaptive quality tiers, deterministic visual regression coverage, curated recipes, optional psychedelic post-processing, and the other verified improvements actually present in the release.
  - **Versioning:** Identify every version string and update location, document migration notes from v3, list compatibility/setup changes, and specify the exact verification commands. Bump to `4.0.0` only after the prerequisite v4 work is complete and the release checklist is approved.
  - **Acceptance:** The announcement contains no unimplemented claims, all listed highlights map to code/tests, version strings are consistent when the bump is performed, and the full unit suite, smoke test, compile check, and `git diff --check` are recorded.

## M13 bounded review outcomes

- M13-A — core/display_manager.py: reviewed; no change justified. Monitor parsing
  and child commands are short, bounded methods; CPU fallback, negative monitor
  coordinates, failed requery and child exit races already have focused tests.
  Keep X11/platform behaviour unchanged; no new display abstraction needed.

- M13-B — beat_tracking.py: reviewed; no structural change justified. Bounded
  history, one-worker submission, cached fallback and explicit shutdown already
  form a small ownership boundary. Failure/reset and repeat-release tests use
  fake analysis/executor paths; do not move device capture into this module.

- M13-C bounded fix (complete): gl_renderer.py + tests/test_graphics_lifecycle.py.
  Make offscreen texture/FBO creation and replacement surface uploads transactional.
  Acceptance: allocation/upload failures release new resources, retain prior
  cache/texture/buffer state, and permit retry; successful eviction releases
  framebuffer before texture. No shader or public API change.

  M13-C complete: focused graphics/beat tests 24; full suite 168; all 32 smoke
  effects and whitespace checks passed.
- M13-D bounded fix (complete): core/postprocess.py + tests/test_postprocess.py.
  Rotation retains renderer-owned FBOs without checking cache eviction or
  replacement renderer identity. Reacquire on stale ownership; test eviction,
  same-size rebind, release and odd surfaces. Preserve shader/CPU math and never
  release a borrowed FBO from the chain.

  M13-D complete: focused postprocess tests 5; full suite 170; all 32 smoke
  effects and whitespace checks passed.
- M13-E bounded extraction (complete): core/ui_manager.py, psysualizer.py and
  tests/test_ui_manager.py. HUD text/layout still belongs to the app while
  UIManager owns fonts/caches. Supply a frozen HUDState; keep device queries in
  the app and move strings/rendering to UIManager. Acceptance: unchanged text,
  detail levels and order, resize/picker/flash caches independently testable.

  M13-E complete: focused UI tests 3; full suite 173; all 32 smoke effects and
  whitespace checks passed. HUDState keeps text/layout in UIManager and device
  enumeration in the app; no hardware claims were added.

- M13-F1 bounded fix (complete): benchmarks.py + tests/test_benchmarks.py.
  GL draw errors skip effect release; renderer release errors skip SDL quit.
  Add matching finally boundaries and a local seeded audio fixture. Acceptance:
  injected failures release owned resources, config is restored, repeat fixture
  inputs match without advancing global RNG, and nonfinite durations are rejected.

  M13-F1 complete: focused tests 8; full suite 176; all 32 smoke effects and
  whitespace checks passed.
- M13-F2 bounded fix (complete): core/regression_tester.py +
  tests/test_regression_tester.py. Standalone runner leaves dimensions/init flag,
  SDL driver and pygame resources behind. Acceptance: success/failure restore
  temporary state and quit SDL; registry assertion failures retain exit code 1.

  M13-F2 complete: focused runner test covers success/assertion/error and present/
  absent driver state; full suite 177; all 32 smoke effects/whitespace passed.
- M13-F3 bounded fix (complete): smoke_test.py + tests/test_smoke_runner.py.
  Top-level smoke runner also leaves config, driver selection and SDL initialized.
  Wrap its existing checks in restoration/finally; preserve failure exit codes.

  M13-F3 complete: focused runner test verifies config/driver restoration and
  failure status; full suite 178; all 32 smoke effects/whitespace passed.
- M13-G1 — core/quality.py: reviewed; no change justified. Small rolling
  percentile/hysteresis controller with explicit bounds/cooldown and focused
  invalid/disabled-input checks; keep governor policy and config publication.

- M13-G2 — effects/palette.py: reviewed; no change justified. Genre targets,
  wrapped hue interpolation and bounded colour mapping already form a cohesive
  unit; keep frame-based palette transitions and shared instance compatibility.
- M13-G3 — effects/utils.py: reviewed; no change justified. Stateless scalar/
  batch colour conversion with explicit clipping; abstraction would add overhead
  without separating ownership. Preserve existing colour/raster baselines.
- M13-H1 — effects/phason.py: reviewed; no extraction justified. State advance,
  shader uniforms and CPU presentation are already separate, with reused bounded
  field buffers. Preserve draw_frame config restoration and waveform contract.
- M13-H2 — effects/ferrofluid.py: reviewed; no extraction justified. Pole state
  survives field resize; smoothing/advance, CPU lighting and shader uniforms are
  explicit helpers. A shared Phason/Ferrofluid simulation abstraction would
  conflate different math; retain tested state and colour behaviour.
- M13-H3 — effects/gl_field.py: reviewed; no change justified. Context programs
  are helper-owned; cached offscreens are borrowed and checked via renderer
  ownership before reuse. Uniform resolution follows physical framebuffer size.

  G/H verification: full suite 178 and all 32 smoke effects passed at this state;
  focused quality/palette/effect-render checks also run. This is a scoped
  maintenance review, not a claim of exhaustive shader or parameter validation.

- M13-I1 bounded extraction: effects/mycelium.py +
  tests/test_effect_render_regressions.py. Colony drawing is mixed into the
  growth/update method despite not mutating simulation state. Extract that
  rendering stage; acceptance: rendering neither advances RNG nor mutates
  colony state, and baseline pixels/state remain identical across frame runs.

  M13-I1 complete: rendering is independently testable and does not advance
  colony state/RNG. 100 baseline frames matched exact pixels/colony state.
  Focused tests 32; full suite 179; all 32 smoke effects/whitespace passed.
- M13-I2 — effects/synapse.py: reviewed; no further change justified. Existing
  node initialization/resize, topology mutation, edge remapping and firing
  helpers expose the meaningful graph boundaries. Keep signal propagation and
  draw order together rather than introducing generic graph machinery.
- M13-I3 — effects/fireworks.py: reviewed; no further extraction justified.
  Launch/explosion, motion-field validation, trail advection and bounded particle
  lists already have helpers/tests. Preserve CPU feedback and resize scaling.
- M13-J Yantra (effects/yantra.py): reviewed; no change justified. Fixed ring/
  spoke populations, viewport-derived geometry and a local vertex helper;
  no owned GPU resources or persistent size-dependent buffers to abstract.

- M13-J Cube (effects/cube.py): reviewed; no change justified. Projection and
  satellite canvas helpers already isolate geometry/resize; elapsed-time
  smoothing and trail scaling have focused visual-response tests.
- M13-J TriFlux (effects/triflux.py): reviewed; no change justified. Grid creation/
  resize, activation, integration and antialiased edges have separate helpers;
  bounded active tiles and retained active state are already tested.
- M13-J Lissajous (effects/lissajous.py): reviewed; no change justified. Bounded
  deque and 60 Hz sample credits preserve trail duration across frame rates;
  geometry uses output dimensions with no GPU ownership to separate.
- M13-J Tunnel (effects/tunnel.py): reviewed; no change justified. Seeded bounded
  triangle arrays, in-place compaction and path/projection helpers are cohesive.
- M13-J Corridor (effects/corridor.py): reviewed; no change justified. Layer/
  fade allocation is isolated in _init_surfs; bounded sparks compact in place.
  Do not change its frame-based motion or recreate trails under a new contract.
- M13-J Nova (effects/nova.py): reviewed; no change justified. Fixed layers,
  bounded waveform subsampling and surface-derived projection need no new owner.
- M13-J Spiral (effects/spiral.py): reviewed; no change justified. Fixed point
  populations and path/projection helpers; retain the documented FFT/config
  contract rather than changing audio semantics in a maintenance pass.
- M13-J Bubbles bounded fix: effects/bubbles.py +
  tests/test_effect_render_regressions.py. The per-size surface cache retains
  every encountered size for the life of the effect. Bound retained surfaces
  with LRU eviction and explicit release, preserving rendering and RNG order.
  Acceptance: reuse recently used sizes, evict oldest at a fixed limit, clear
  owned caches/state on repeat release; existing visual fixtures still pass.

  M13-J Bubbles complete: cache/release test and existing visual fixtures pass.
  Focused tests 33; full suite 180; all 32 smoke effects/whitespace passed.
- M13-J Plasma (effects/plasma_gl.py): reviewed; no structural change justified.
  Advance/uniform/fallback/shader helpers already separate CPU/GPU work;
  borrowed cached FBOs are checked against renderer ownership before reuse.
- M13-J Branches (effects/branches.py): reviewed; no change justified. Recursion
  is isolated with depth/length termination and low-spec budgets; no persistent
  display-sized buffers or device ownership.
- M13-J Butterflies: reviewed through M12; readable pairing/update helpers and
  repeat release now verified with deterministic state/pixel comparisons.
- M13-J FlowField (effects/flowfield.py): reviewed; no change justified.
  Allocation/particle adjustment and field-angle helpers preserve bounded arrays;
  resize rescales existing coordinates/trails instead of reseeding.
- M13-J Aurora (effects/aurora.py): reviewed; no change justified. Fixed curtain
  definitions, lazy resize of waveform canvas and first-frame/empty-FFT tests;
  no further ownership split demonstrated.
- M13-J Lattice (effects/lattice.py): reviewed; no change justified. _resize/_bin
  expose profile-dependent allocation and safe FFT indexing; retain trail,
  shockwave and smooth motion policies protected by response tests.
- M13-J Magnetar (effects/magnetar.py): reviewed; no change justified. Particle
  allocation and contour drawing are explicit helpers; bounded population,
  clipped deposition and finally-released pixel views keep ownership local.
- M13-J SlimeMold (effects/slimemold.py): reviewed; no change justified.
  Dedicated resize/sensor helpers retain agent/trail state; physical-scale quality
  floor and clamped deposition already have focused regression coverage.
- M13-J Mobius (effects/mobius.py): reviewed; no change justified. Fixed strip
  grids and projection/geometry helpers; whole-strip fitting uses actual surface
  dimensions and tested elapsed-time smoothing.
- M13-J Chromatic (effects/chromatic.py): reviewed; no change justified.
  Bounded ring spawning, waveform geometry helper and scaled retained trails
  already isolate the effect's responsibilities.

- M13-J Persistence (effects/persistence.py): reviewed; no change justified.
  Seeded fixed models, reusable internal surface and explicit rotation helpers;
  existing morph schema and smooth direction/colour response stay unchanged.
- M13-J Heartbeat (effects/heartbeat.py): reviewed; no change justified.
  Named ring records, capped spawning and separate plate allocation/drawing
  helpers already expose the meaningful state/render boundaries.
- M13-J Hyperbolic (effects/hyperbolic.py): reviewed; no change justified.
  Geometry creation, clipping and projection helpers isolate the mesh work;
  output-sized body and bounded tile heights are protected by response tests.
- M13-J LiquidLight (effects/liquidlight.py): reviewed; no change justified.
  Explicit allocation, vortex injection, advection and presentation helpers;
  borrowed motion fields, preserved dye resize and owned-buffer release already
  have focused checks. Do not merge its solver into another field effect.
- M13-J Cymatica (effects/cymatica.py): reviewed; no change justified.
  Mode-field generation, bounded normalized particles, rendering and release
  are already separate; preserve smoothing instead of adding a mode framework.
- M13-J Tesseract (effects/tesseract.py): reviewed; no change justified.
  Preset geometry, capped vertices/edges, rotation and projection buffers are
  explicit; morph clamping and finite projection tests cover its extension seam.
- M13-J Spectrum (effects/spectrum.py): reviewed; no change justified. Small
  bin-reduction/peak/waveform renderer with output-derived geometry; retain
  the documented fixed FFT input contract and registry position.
- M13-J Waterfall (effects/waterfall.py): reviewed; no change justified. Bounded
  row deque, named grid-dimension helper and local RNG; no device/GPU lifecycle
  or shared simulation abstraction needed.

M13-I/J acceptance: all 32 registered effects now have an individual outcome
(M12 covers Butterflies, H covers Phason/Ferrofluid, I covers Mycelium/Synapse/
Fireworks, J covers the rest). At this state all 180 tests and all 32 smoke
effects passed, including compact all-effect visual fixtures. No claim of
exhaustive parameter or hardware testing. K/M14 completion is recorded below.

## M13-K test isolation review

Each of the 24 test modules was run separately with before/after snapshots of
config values and shared palette state. All 180 tests passed; only app-logic and
graphics-lifecycle modules leaked those globals.
- K/app_logic bounded fix (complete): tests/test_app_logic.py. Restore config and
  replace the shared palette per test; preserve assertions and fake-device paths.

  K/app_logic complete: focused 10, full suite 180 and all smoke checks passed.
- K/graphics_lifecycle bounded fixture fix: tests/test_graphics_lifecycle.py.
  Display fallback tests publish dimensions/init state without restoration.
  Restore config per test; preserve real-Pygame/fake-GL conditional coverage.

  K/graphics_lifecycle complete: focused 21, full suite 180 and smoke passed.
Remaining test modules reviewed individually; no change justified:

| Module under tests/ | Isolation/coverage boundary retained |
| --- | --- |
| test_app_lifecycle.py | Failure injection, mock device ownership, temporary config |
| test_audio_engine.py | Per-test fake tracker and stream teardown |
| test_audio_response.py | Per-instance gate/snapshots; no device lifecycle |
| test_beat_tracking.py | Executor shutdown in finally; fake optional analysis |
| test_benchmarks.py | Mock clock/resources, seeded fixtures, RNG/config checks |
| test_cli.py | Explicit argv, patched process argv, clean import subprocess |
| test_config.py | Seed/environment checks isolated in subprocesses |
| test_effect_render_regressions.py | Pygame class lifecycle and saved config fields |
| test_input_controller.py | Fresh action/state fakes, patched clock and persistence |
| test_palette.py | Local palette instances; singleton untouched |
| test_postprocess.py | Fake renderer cache plus real CPU surfaces; SDL teardown |
| test_quality.py | Restored quality/config fields and bounded sample windows |
| test_recipes.py | Temporary saved files and registered mock cleanup patches |
| test_regression_tester.py | Restored config/environment via scoped mocks |
| test_rng_determinism.py | Seeded effect instances, saved dimensions/energies |
| test_settings.py | Temporary directory and restored path overrides |
| test_silence.py | Pure instance-owned state and injected thresholds |
| test_smoke_runner.py | Failure status and temporary state restoration |
| test_transitions.py | Local morph/app fakes and scoped config patches |
| test_ui_manager.py | Config patches, local font/cache state and SDL cleanup |
| test_visual_regression.py | Existing compact baselines/tolerances unchanged |
| test_visual_response.py | Saved time/profile/quality fields; fake GL boundary |
| __init__.py | Package marker; no runtime setup to change |

M13-K source/fixture review and independent execution cover every test module;
only the two demonstrated global-state leaks were changed. Full suite remains
180 tests. Reverse module/method order also passed, and all 24 modules restored config/palette state.

## Final verification and limits

Final commands (2026-11-11):
- SDL_VIDEODRIVER=dummy SDL_AUDIODRIVER=dummy PYTHONDONTWRITEBYTECODE=1
  .venv/bin/python -m unittest discover -s tests -q — 180 passed.
- Same environment with .venv/bin/python smoke_test.py — 32 effects/registry pass.
- PYTHONPYCACHEPREFIX=/tmp/psysuals-final-pycache .venv/bin/python -m compileall
  -q psysualizer.py config.py settings.py benchmarks.py beat_tracking.py
  gl_renderer.py smoke_test.py core effects tests — passed; cache outside checkout.
- git diff --check plus explicit new-file whitespace checks — passed.
- AST comparison against HEAD b8c6de0: MODES order, Effect.draw/draw_frame
  signatures and __version__ retained. Dependency manifests have no diff.

Hardware evidence remains scoped: real AMD EGL and CPU framebuffer comparisons
passed for five effects; no desktop fullscreen/mode-switch equivalence claim.
CPU/GL resize and mode-switch logic, stream/device failures and span cleanup
are covered with surfaces/fakes. Live audio-device switching, physical
multi-monitor operation and external Android integration were not exercised.
These pending integration checks are recorded as limits, not successful tests.

### Changed-file manifest

Includes the earlier authorized M01–M03 changes and PLAN.md; excludes the user's
pre-existing FINDINGS.md, which was preserved.

- [ARCHITECTURE.md](ARCHITECTURE.md)
- [PLAN.md](PLAN.md)
- [README.md](README.md)
- [TODO.md](TODO.md)
- [benchmarks.py](benchmarks.py)
- [core/audio_engine.py](core/audio_engine.py)
- [core/audio_response.py](core/audio_response.py)
- [core/cli.py](core/cli.py)
- [core/frame_composer.py](core/frame_composer.py)
- [core/input_controller.py](core/input_controller.py)
- [core/postprocess.py](core/postprocess.py)
- [core/regression_tester.py](core/regression_tester.py)
- [core/silence.py](core/silence.py)
- [core/transitions.py](core/transitions.py)
- [core/ui_manager.py](core/ui_manager.py)
- [effects/base.py](effects/base.py)
- [effects/bubbles.py](effects/bubbles.py)
- [effects/butterflies.py](effects/butterflies.py)
- [effects/mycelium.py](effects/mycelium.py)
- [gl_renderer.py](gl_renderer.py)
- [psysualizer.py](psysualizer.py)
- [settings.py](settings.py)
- [smoke_test.py](smoke_test.py)
- [tests/test_app_lifecycle.py](tests/test_app_lifecycle.py)
- [tests/test_app_logic.py](tests/test_app_logic.py)
- [tests/test_audio_engine.py](tests/test_audio_engine.py)
- [tests/test_audio_response.py](tests/test_audio_response.py)
- [tests/test_benchmarks.py](tests/test_benchmarks.py)
- [tests/test_cli.py](tests/test_cli.py)
- [tests/test_effect_render_regressions.py](tests/test_effect_render_regressions.py)
- [tests/test_graphics_lifecycle.py](tests/test_graphics_lifecycle.py)
- [tests/test_input_controller.py](tests/test_input_controller.py)
- [tests/test_postprocess.py](tests/test_postprocess.py)
- [tests/test_recipes.py](tests/test_recipes.py)
- [tests/test_regression_tester.py](tests/test_regression_tester.py)
- [tests/test_rng_determinism.py](tests/test_rng_determinism.py)
- [tests/test_settings.py](tests/test_settings.py)
- [tests/test_silence.py](tests/test_silence.py)
- [tests/test_smoke_runner.py](tests/test_smoke_runner.py)
- [tests/test_transitions.py](tests/test_transitions.py)
- [tests/test_ui_manager.py](tests/test_ui_manager.py)
- [tests/test_visual_response.py](tests/test_visual_response.py)

## Explicit context migration (authorized after M01–M14)

Target: remove config.py and runtime config imports. Preserve old draw/draw_frame
signatures through instance-owned adapters; hosts must supply construction size.
- [x] C01 — Frozen AudioConfig/EffectOptions/FrameContext; explicit seed parsing;
  migrate audio configuration and verify independent audio instances. Focused 18,
  full suite 184, all smoke checks and whitespace passed.
- [x] C02 — Move display/UI/quality/app state to explicit owners.
- [x] C03 — Migrate effects individually, beginning with Spectrum/SlimeMold/Plasma;
  preserve RNG order and rendering math; add instance-owned legacy adapters.
- [x] C04 — Update tools/tests, delete config.py and document host migration.
- [x] C05 — Verify interleaved instances, failed render isolation, seeded
  reproducibility, existing fixtures and CPU/GPU rendering.


Migration verification (2026-11-11; supersedes the earlier 180-test report):
- Full suite: 190 tests passed in normal and reversed order with dummy SDL
  video/audio and the existing venv.
- Smoke runner: all 32 effects passed. Compileall and git diff --check passed.
- Independent contexts cover dimensions, profiles, timing, quality and seeds;
  exception paths restore frame bindings, including renderer detach failure.
- All 32 CPU effects matched saved pre-migration rendering exactly (12 frames
  each; Butterflies 32). Seven effects passed real AMD EGL comparisons within
  one 8-bit channel step; shader/uniform equality was checked for Phason and
  Ferrofluid. Existing committed visual baselines/tolerances were unchanged.
- A real app startup/update/quit cycle passed under dummy SDL with unavailable
  audio and temporary settings outside the checkout. This is not live-device
  or desktop presentation validation.
- Plasma, Phason and Ferrofluid headless adapters passed real EGL renderer
  switching across two contexts and two output sizes; RGBA shape/type verified.
- Registry order, version and legacy adapter arguments match HEAD; dependency
  manifests have no diff.
- No runtime config module remains. Tests/tools use explicit construction and
  instance-owned contexts; ARCHITECTURE.md and README.md describe host migration.
- Physical display/fullscreen, live audio switching, multi-monitor operation
  and the external Android integration remain unverified. Android constructors
  must provide EffectOptions; draw/draw_frame argument signatures are retained.

The manifest above records M01–M14. The complete current worktree manifest below
also includes the context migration; FINDINGS.md remains excluded and untouched.

- [ARCHITECTURE.md](ARCHITECTURE.md)
- [EFFECTS.md](EFFECTS.md)
- [README.md](README.md)
- [TODO.md](TODO.md)
- [benchmarks.py](benchmarks.py)
- Deleted: config.py
- [core/audio_engine.py](core/audio_engine.py)
- [core/display_manager.py](core/display_manager.py)
- [core/postprocess.py](core/postprocess.py)
- [core/quality.py](core/quality.py)
- [core/regression_tester.py](core/regression_tester.py)
- [core/ui_manager.py](core/ui_manager.py)
- [effects/aurora.py](effects/aurora.py)
- [effects/base.py](effects/base.py)
- [effects/branches.py](effects/branches.py)
- [effects/bubbles.py](effects/bubbles.py)
- [effects/butterflies.py](effects/butterflies.py)
- [effects/chromatic.py](effects/chromatic.py)
- [effects/corridor.py](effects/corridor.py)
- [effects/cube.py](effects/cube.py)
- [effects/cymatica.py](effects/cymatica.py)
- [effects/ferrofluid.py](effects/ferrofluid.py)
- [effects/fireworks.py](effects/fireworks.py)
- [effects/flowfield.py](effects/flowfield.py)
- [effects/heartbeat.py](effects/heartbeat.py)
- [effects/hyperbolic.py](effects/hyperbolic.py)
- [effects/lattice.py](effects/lattice.py)
- [effects/liquidlight.py](effects/liquidlight.py)
- [effects/lissajous.py](effects/lissajous.py)
- [effects/magnetar.py](effects/magnetar.py)
- [effects/mobius.py](effects/mobius.py)
- [effects/mycelium.py](effects/mycelium.py)
- [effects/nova.py](effects/nova.py)
- [effects/persistence.py](effects/persistence.py)
- [effects/phason.py](effects/phason.py)
- [effects/plasma_gl.py](effects/plasma_gl.py)
- [effects/slimemold.py](effects/slimemold.py)
- [effects/spectrum.py](effects/spectrum.py)
- [effects/spiral.py](effects/spiral.py)
- [effects/synapse.py](effects/synapse.py)
- [effects/tesseract.py](effects/tesseract.py)
- [effects/triflux.py](effects/triflux.py)
- [effects/tunnel.py](effects/tunnel.py)
- [effects/waterfall.py](effects/waterfall.py)
- [effects/yantra.py](effects/yantra.py)
- [gl_renderer.py](gl_renderer.py)
- [psysualizer.py](psysualizer.py)
- [settings.py](settings.py)
- [smoke_test.py](smoke_test.py)
- [tests/test_app_logic.py](tests/test_app_logic.py)
- [tests/test_audio_engine.py](tests/test_audio_engine.py)
- [tests/test_benchmarks.py](tests/test_benchmarks.py)
- [tests/test_config.py](tests/test_config.py)
- [tests/test_effect_render_regressions.py](tests/test_effect_render_regressions.py)
- [tests/test_graphics_lifecycle.py](tests/test_graphics_lifecycle.py)
- [tests/test_postprocess.py](tests/test_postprocess.py)
- [tests/test_quality.py](tests/test_quality.py)
- [tests/test_recipes.py](tests/test_recipes.py)
- [tests/test_rng_determinism.py](tests/test_rng_determinism.py)
- [tests/test_settings.py](tests/test_settings.py)
- [tests/test_visual_regression.py](tests/test_visual_regression.py)
- [tests/test_visual_response.py](tests/test_visual_response.py)
- [PLAN.md](PLAN.md)
- [core/audio_response.py](core/audio_response.py)
- [core/cli.py](core/cli.py)
- [core/context.py](core/context.py)
- [core/frame_composer.py](core/frame_composer.py)
- [core/input_controller.py](core/input_controller.py)
- [core/silence.py](core/silence.py)
- [core/transitions.py](core/transitions.py)
- [tests/render_host.py](tests/render_host.py)
- [tests/test_app_lifecycle.py](tests/test_app_lifecycle.py)
- [tests/test_audio_response.py](tests/test_audio_response.py)
- [tests/test_cli.py](tests/test_cli.py)
- [tests/test_context.py](tests/test_context.py)
- [tests/test_input_controller.py](tests/test_input_controller.py)
- [tests/test_regression_tester.py](tests/test_regression_tester.py)
- [tests/test_silence.py](tests/test_silence.py)
- [tests/test_smoke_runner.py](tests/test_smoke_runner.py)
- [tests/test_transitions.py](tests/test_transitions.py)
- [tests/test_ui_manager.py](tests/test_ui_manager.py)


## Plan review — 2026-11-11

PLAN.md was revisited after C01–C05. It now distinguishes completed M/C work
from proposed R01–R05: reproducible evidence, public input validation, rendering
failure semantics, measured copying cost and separate hardware/Android gates.
The item-by-item disposition replaces stale extraction/config instructions.
No R item has been started or approved for implementation by this review.

Historical continuation at plan-review time: documentation review complete;
subsequently superseded by the authorized R01–R05 execution below.
Validation: direct source inspection and constructor/array-ownership probes;
Markdown local-link checks and git diff --check. No production/test code changed;
the unit suite, smoke runner and hardware comparisons were not rerun.

## R01–R05 execution — 2026-11-11

User authorized all five items. Existing M/C changes and FINDINGS.md are preserved.
- [x] R01 — Permanent real-EGL adapter coverage and reproducibility notes.
  Focused 12 tests and full 191 tests passed (EGL test executed, no skip);
  32 smoke effects and git diff --check passed. Existing CPU baselines/tolerances
  unchanged. Historical migration reference remains unavailable as a versioned
  artifact, explicitly documented in README.md.
- [x] R02 — Explicit integer/range/finite/array validation and cooperative
  read-only ownership documented. Focused 59 and full 194 tests passed; 32 smoke
  checks and whitespace passed. Existing empty-FFT support is preserved for
  Heartbeat/Lattice/Cymatica; Spectrum/Waterfall additionally rendered 256/2048
  blocks. Invalid-dt coverage now tests boundary rejection plus the defensive
  helper directly (tests/test_visual_response.py; required fixture adaptation).
- [x] R03 — Public-entrypoint success/failure/retry and renderer outcomes tested
  directly without RenderHost. Contract documents binding-only restoration,
  partial state/RNG/pixel advancement, detach errors and discard/rebuild guidance.
  Focused 37 and full 196 tests passed; 32 smoke effects and whitespace passed.
- [x] R04 — Added context-only measurement CLI with no SDL/render initialization;
  focused 26 and full 198 tests, all 32 smoke checks and whitespace passed.
  Retained defensive copies: no material regression demonstrated in this scoped
  measurement. No ownership/performance implementation change was justified.
- [x] R05 — Completed scoped integration investigation; per-target outcomes
  below include a failed GL window-size request and unavailable physical span.
  Native Android architecture/unchanged-source CI confirmed; no Python bridge
  migration applies. This closes the verification task, not hardware acceptance.

R04 reproducible command (100 warmups per operation, 5,000 timed calls):
`PYTHONDONTWRITEBYTECODE=1 .venv/bin/python benchmarks.py --context-only --samples 5000`
Environment: Linux 7.0.0-43-generic x86_64, Python 3.14.4, NumPy 2.4.6.
Input: float32 mono waveform plus half-length FFT, output metadata 1920x1080.

| Block | Array bytes copied/call | Construct median/p95 (us) | Metadata replace median/p95 (us) |
| --- | ---: | ---: | ---: |
| 256 | 1536 | 13.014 / 21.782 | 16.581 / 20.342 |
| 1024 | 6144 | 14.338 / 24.668 | 17.112 / 30.659 |
| 8192 | 49152 | 16.321 / 26.403 | 19.818 / 23.724 |

At the default block size, three p95 metadata replacements estimate 91.977 us,
0.552% of a 16.667 ms (60 Hz) budget, copying 18,432 array bytes. This measures
validated host-boundary construction/replacement, not whole-app latency, allocator
traffic, actual frame p95 or achievable FPS. The large-block row is a context
stress input, not a claim all effects support that audio configuration.

### R05 integration results

Verification card completed with the individual outcomes below. This does not
claim full hardware acceptance: the GL sizing issue and physical-monitor limit
remain explicit. No external repository was changed, no Android build was run
locally, no subtree sync/backport/commit/push/release was performed.

| Target | Observed result | Evidence / limit |
| --- | --- | --- |
| Desktop CPU | PASS in the exercised scenarios | Real X11/SDL 2.32.10, Pygame-ce 2.5.7, 1920x1200 fullscreen and 960x540 window; fullscreen controls retained selected mode, recipe/layers and mode changes rendered, cleanup completed with temporary settings. |
| Desktop GL | PASS startup, fullscreen transitions, mode/layer changes, clear overlays and cleanup; FAIL requested smaller window size | Real AMD Radeon Rembrandt/radeonsi GL; requested 960x540 but actual display remained 1920x1200, including explicit SDL window resize. Rendered output matched actual dimensions. Smaller interactive GL sizing remains unresolved; EGL resize is separate evidence. |
| Audio | PASS for no-input startup and two live input routes | Actual PortAudio routes 5 then 12 published at least three new callback generations each while rendering continued (six/five frames during waits). Old stream closed on successful switch; invalid device retained the live stream; shutdown closed it. Audio data stayed in memory; no PCM saved. Routes are not proof of two distinct physical microphones. |
| Physical multi-monitor | UNAVAILABLE | Escalated xrandr --listmonitors reported one eDP-1 panel at 1920x1200. Physical span startup/hotplug/child display positioning could not be exercised; existing fake tests are not hardware evidence. |
| Android integration | Native architecture verified; current native CI evidence confirmed; Python constructor adoption NOT APPLICABLE | User identified ../androsaver. Runtime is Kotlin/GLES; psysuals is a reference subtree. app/build.gradle has no Python/Chaquopy bridge, VisualizerRenderer creates Kotlin modes, port notes explicitly exclude desktop adapters from runtime. |

Android source/CI audit:
- ../androsaver is clean on dev at 4bd56657fae80f4a1ff26a5a72bc4901bda9f374.
- Its app tree is 37eb0dfb9b8ce0962e4fd91f2628e4a3b14e3985, identical to
  18661a1d4153d4c9ceec0591e98f31afa7fe8e8c. Git diff for app, root Gradle
  files, wrapper and .github/workflows/build.yml against that commit is empty.
- GitHub API confirmed [run 40414215709](https://github.com/Whichcraft/androsaver/actions/runs/40414215709)
  completed successfully for exact SHA 18661a1d4153d4c9ceec0591e98f31afa7fe8e8c.
  verify, Device integration API 29, Device integration API 35 and build jobs
  succeeded; bundle-playstore was skipped and is not claimed as passed.
- VisualizerBackportIntegrationTest exercises all 32 native modes, shader/render
  errors, resize, switching through black frames, scene/bloom/display clearing.
  This is existing verified native code, not a new CI run of the Python worktree.
- The native reference remains upstream 0579390; the current Python worktree has
  not been imported. A future subtree update/native backport is separate work
  under AndroSaver's documented review/CI-only build policy. No Python constructor
  change is needed in its existing native runtime. Physical TV checks remain open.

Bounded follow-up proposal R05-D1 (not implemented by this verification card):
Investigate real X11 GL window sizing in core/display_manager.py with
tests/test_graphics_lifecycle.py. Reproduce fullscreen-to-960x540 and compare
SDL window size, surface size and framebuffer viewport before deciding whether
this is window-manager policy, stale SDL surface metadata or display setup.
Acceptance: requested windowed size is reflected in actual presentation, or a
specific unsupported host condition is documented with evidence. Preserve
fullscreen, context ownership and CPU fallback; no monitor configuration changes.

Additional user question: a direct paired CPU experiment used seed 7, 320x240,
Laptop profile and 32 equal-timing frames per effect. Silent inputs versus a
synthetic sine/FFT with mids .8, treble .9 and pulsed beat changed pixels for all
32 registered effects (no unchanged modes). Hyperbolic responds via bass tile
lift/brightness, mid rotation/hue and treble edges; the native port has equivalent
controls. This is evidence of response, not equal perceptual strength or an
Android runtime audio test. No effect tuning or code was changed for this check.

### R01–R05 final audit

All five cards meet their scoped acceptance criteria, including explicit
FAIL/UNAVAILABLE integration outcomes rather than blanket hardware acceptance.
The GL sizing follow-up R05-D1 and physical span/TV limits remain as documented.
Final full suite: 198 passed in normal order (R04 gate) and 198 passed in reversed
order after all changes; real EGL adapter test executed. All 32 smoke checks,
compileall (cache outside checkout), Markdown local-link/path checks and
git diff --check passed. Visual baseline dictionary values match HEAD;
requirements files have no diff. No commit/version/dependency change.

Files changed during R01–R05 (earlier M/C work remains intact):
- [core/context.py](core/context.py)
- [effects/base.py](effects/base.py)
- [benchmarks.py](benchmarks.py)
- [tests/test_context.py](tests/test_context.py)
- [tests/test_benchmarks.py](tests/test_benchmarks.py)
- [tests/test_visual_response.py](tests/test_visual_response.py)
- [ARCHITECTURE.md](ARCHITECTURE.md)
- [README.md](README.md)
- [PLAN.md](PLAN.md)
- [TODO.md](TODO.md)

Continuation: requested cards complete. Further work requires a new selected
scope, such as R05-D1, a physical multi-monitor test, or a separately authorized
AndroSaver subtree import/backport. AndroSaver's worktree remains untouched.


## Hyperbolic music response — 2026-11-11

User requested a stronger response. Completed: faster bass-driven facet lift,
stronger face/treble-edge light, a bounded hinge accent and whole-mesh projection
fitting to retain the viewport margin under sustained loud input. Idle rotation
and phase timing remain unchanged. New tests compare a short kick with silence,
verify decay and exercise sustained extreme input in Laptop/TV profiles.
The first larger hinge setting cropped the intended margin on loud input;
whole-mesh fitting now uses one scale for caps and walls. No baseline values or
tolerances are changed. AndroSaver's native code remains untouched; a native
backport is separate from this upstream effect change.
Verification: three focused pulse/recovery/extreme-input tests passed; full suite
200 tests passed; all 32 smoke checks, compileall and git diff --check passed.
Existing visual baseline values/tolerances were not changed. New sustained-input
coverage runs 120 frames per Laptop/TV profile and checks every projected cap
against the four-pixel viewport margin, finite state and existing height bounds.

One-off before/after comparison: 640x480, Laptop profile, seed 7, six frames of
beat 1.2 with mids/treble zero. Mean tile lift relative to silence rose from
0.001017 to 0.035200; mean absolute pixel response rose from 4.417 to 27.215.
Silent output matched exactly for that input sequence; this is sampled evidence,
not a guarantee of pixel identity at every idle phase. The comparison image
/tmp/psysuals-hyperbolic-response.png was visually inspected. Its temporary
pre-change source/reference is not a permanent regression gate.

Files changed for this request: effects/hyperbolic.py,
tests/test_visual_response.py, EFFECTS.md and TODO.md. No commit/version change;
no edits to AndroSaver. Native backport remains separate.


## Heartbeat background removal — 2026-11-11

User requested removal of the cymatics background. Removed the plate renderer,
its cached surfaces/fields, beat flash state and unused NumPy/surfarray imports.
Ring spawning, colours, shape, speed and idle-ring cadence are retained.
Replaced the plate test with background-preservation, visible beat-ring,
bounded-ring and small-target/empty-FFT RGBA adapter checks. Updated EFFECTS.md.
AndroSaver's native code is not changed by this upstream Python edit.
Completed: 34 focused rendering tests and the full 201-test suite passed; all
32 smoke checks, compileall and git diff --check passed. Verified removed plate
state/rendering and obsolete Heartbeat descriptions. Existing visual baseline
values/tolerances were not changed.
Files changed: effects/heartbeat.py, tests/test_effect_render_regressions.py,
EFFECTS.md and TODO.md. No commit or version change.


## Android Magnetar fix and combined delivery — 2026-11-11

User requested the missing magnetic-field layer in ../androsaver. Confirmed an
Android-only omission against its vendored Python renderer. Native MagnetarMode
now renders the dipole contour field using the existing GLES2 helper; particle
count, motion and particle drawing remain unchanged. Device regression coverage,
backport notes and AndroSaver TODO were updated in that checkout. Local diff/
source checks passed; Android tests/build have not run under its CI-only policy.

User instruction: batch all changes at the end. Do not commit, push, trigger
GitHub verification or publish these changes yet. Keep both worktrees intact;
Android verification remains pending. Changed in AndroSaver: MagnetarMode.kt,
VisualizerBackportIntegrationTest.kt, docs/psysuals-port-notes.md and TODO.md.


## Slowest-four performance pass — desktop verified; Android verification pending

User requested desktop and Android performance work, with no commit/push/CI or
publication until the combined batch. New steering: SlimeMold should resemble
desktop GL on Android, with fine detail rather than enlarged grid cells.

1080p baseline survey (48 deterministic frames per effect/backend, first 32
warmup, median of 16 timed frames; seed 7, Laptop profile, fixed 60Hz dt,
sine/FFT, mid .4, treble .6, pulsed beat; real AMD EGL GL with finish):
- Desktop GL slowest four: SlimeMold 86.74 ms (11.53 FPS), Lattice 46.39 ms
  (21.56 FPS), Hyperbolic 43.85 ms (22.80 FPS), LiquidLight 36.32 ms (27.54 FPS).
- These four are this pass's targets, following the GL reference. CPU-only
  slowest instead: Ferrofluid 528.80 ms, Phason 210.17 ms, SlimeMold 85.76 ms,
  Plasma 47.62 ms. GPU Phason/Ferrofluid were ~1.8/1.9 ms; do not confuse paths.
- Raw render timings exclude app HUD/audio/window flip; GL includes uploads and
  GPU completion. This is a bounded survey, not physical Android FPS evidence.
- Android measured ranking/verification is deferred with the user's CI batch.
  Prepare native measurements and review hot paths; do not claim unmeasured gains.

Implementation and desktop checks are complete below. Native compilation/FPS/
visual acceptance remain pending the user's combined verification batch.
Pre-change source and one-off comparison data are under
/tmp/psysuals-before-fps and /tmp/psysuals-fps-*.


### Slowest-four results and continuation

Desktop changes:
- SlimeMold reuses wrapped-diffusion and palette buffers. Sparse fields evaluate
  colour only for potentially visible cells; below 1/128 chemical strength even
  the maximum channel is <1 before integer conversion, so skipping it is exact.
  Dense fields use a full-array path. Simulation size, agents, RNG and chemistry
  are unchanged. Sparse/dense/threshold/padded-pitch reference tests pass.
- Lattice caches repeated frame colours and the scaled output surface, using
  RGB_MAX plus explicit opaque alpha instead of the slow RGBA_MAX path.
- Hyperbolic caches fixed hinge data and frame rotation matrices, avoids rolled
  arrays/redundant clipping and composites the fixed sphere mask once.
- LiquidLight gathers advection into existing buffers and avoids the strided
  three-channel sum reduction; its published motion fields remain independent.

Paired real AMD EGL render comparison: fixed 1920x1080, seed 7, Laptop profile,
60Hz simulation dt, 1024-sample sine/512-bin FFT, mid .4, treble .6, repeating
three-frame .9 beat then .08. Alternate old/new order, 32 warmup and 48 measured
frames per variant. Timed rendering/upload/GL finish, excluding clears, readbacks,
HUD/audio/window flip/VSync. Source reference includes all previously authorized
changes, including stronger Hyperbolic and ring-only Heartbeat; not bare HEAD.
SlimeMold was remeasured after its final palette optimization in an isolated run.

| Effect | Before median ms / FPS | After median ms / FPS | Before / after p95 ms |
| --- | ---: | ---: | ---: |
| SlimeMold | 82.448 / 12.13 | 44.795 / 22.32 | 86.279 / 48.683 |
| Lattice | 45.886 / 21.79 | 23.319 / 42.88 | 49.964 / 26.760 |
| Hyperbolic | 43.431 / 23.03 | 24.936 / 40.10 | 47.217 / 27.191 |
| LiquidLight | 34.472 / 29.01 | 25.051 / 39.92 | 36.807 / 27.209 |

These are scoped uncapped render rates on AMD Radeon Rembrandt/radeonsi,
Python 3.14.4, NumPy 2.4.6, Pygame-ce 2.5.7/Linux x86_64; not app/Android FPS.
One-off exact CPU comparisons passed at 320x240 (64 frames) and 1920x1080
(48 frames); selected real EGL RGBA frames also matched exactly. Original visual
baseline values/tolerances remain unchanged. Temporary pre-change sources are
not a portable versioned artifact. The current-state survey is durable:

`PYTHONDONTWRITEBYTECODE=1 .venv/bin/python tests/diagnostics/fps_survey.py --backend egl --json-output /tmp/psysuals-fps.json`

The tool surveys all modes, prints its slowest four, captures source hashes and
fixture/environment metadata, and never silently substitutes CPU for EGL.
Use cpu/both backends separately. A short 320x240 both-backend diagnostic run
passed for all four targets, with its JSON checked. Run real performance tests
without other heavy work running concurrently.

Final desktop checks: 207 tests, all 32 smoke checks, compileall (cache outside
checkout), baseline dictionary identity and whitespace checks passed. Six focused
performance-contract tests cover wrapped diffusion, resize, sparse/dense palette,
opaque cached Lattice output, Hyperbolic clipping and LiquidLight advection.

Native changes are applied in ../androsaver, preserving pending Magnetar work:
- SlimeMold uses fine chemical-field textures with linear filtering and desktop
  sine colours, 5,000–26,000 initial agents, simultaneous sensing/deposit phases,
  Gaussian initialization and wrapped diffusion. Field cap is 1920x1080 (two
  physical pixels/sample at 4K), with 8-bit intensity quantization. Sensor/motion
  scale follows physical output. This replaces enlarged rectangle cells.
- Lattice submits coincident additive beams once; Hyperbolic caches trigonometry,
  hinges and sort depths; LiquidLight caches row/column wave terms.
- Added JVM/detail/work-budget tests, SlimeMold GLES filter/palette/resize/context
  tests, and an opt-in all-mode 1080p FPS survey. Backport notes state all native
  differences and deferred verification. No native tests/build were run locally.
- Increasing SlimeMold detail/agents also increases CPU simulation work: reduced
  draw calls are not proof of improved net Android FPS. Native ranking, numerical
  tests and actual TV visual/performance acceptance still require the queued run.
- At the final GitHub/device batch, enable fpsSurvey=true as documented in the
  backport notes; compare target-device results before claiming native FPS gains.
  Default tests explicitly skip this longer survey. No workflow was triggered.
- Commits, pushes, source/subtree import, CI and publication remain deferred at
  the user's instruction. Do not change the vendored subtree before that batch.

Changed in this performance pass (previous changes preserved):
- effects/slimemold.py, effects/lattice.py, effects/hyperbolic.py, effects/liquidlight.py
- tests/test_effect_performance.py, tests/diagnostics/fps_survey.py, README.md, TODO.md
- AndroSaver modes/SlimeMoldMode.kt, LatticeMode.kt, HyperbolicMode.kt, LiquidLightMode.kt
- AndroSaver VisualizerPerformanceContractsTest.kt, VisualizerPerformanceIntegrationTest.kt
- AndroSaver docs/psysuals-port-notes.md and TODO.md

Next: continue requested edits locally. At the combined batch, synchronize the
reviewed upstream source, compile/run the native checks and measure physical TV
FPS/detail. Do not represent those deferred native checks as already passed.


## Active goal 1, 3, 4 — all remaining effects (2026-11-11)

User selected: native Hyperbolic music-response parity; shared desktop upload
optimization; all remaining effects rather than just the next four. Preserve
all existing dirty worktrees and FINDINGS.md. Commits, pushes, subtree sync,
GitHub builds/tests and publication remain deferred for the combined batch.

- [x] Fresh all-32 CPU/EGL baseline captured with the durable diagnostic.
- [x] Native Hyperbolic strong pulse/lighting and common cap/wall viewport fit
  prepared with pulse-release and bounds tests; native execution is pending.
- [x] Shared desktop direct surface upload implemented with GPU channel swizzling,
  safe pitch/format fallback and exception-safe view/texture ownership. Real EGL
  comparisons cover RGB/RGBA, resize, disabled alpha, alternate channel masks,
  padded subsurfaces and 24-/16-bit fallback; 46 focused tests passed.
- [x] Profile all 32 desktop effects and review remaining Python/native render
  hot paths. Individual changes: Cymatica static basis/particle trig, Magnetar
  fixed-grid denominators, Waterfall vector colours, full-detail CPU row tiling
  in Ferrofluid/Phason/Plasma, and Branches per-arm/depth styles and depth sway.
- [x] Prepare native bounded shared circle cache, Cymatica basis/energy reuse,
  allocation-free field/particle colours, and Branches depth styles/sway. Tests
  cover exact circle vertices, analytic basis/reset and full recursive detail.
- [ ] Complete Android JVM/device/build checks and target-TV FPS/detail acceptance
  in the user's final combined batch. No native performance gain is proven yet.
- Native Android compilation/device/FPS checks cannot be claimed while the
  CI batch is deferred; retain them as required final-batch verification.
- Current pre-goal sources saved outside checkouts under
  /tmp/psysuals-goal134-before; benchmark logs /tmp/psysuals-goal134-baseline.*.


### Goal 1/3/4 desktop evidence and per-effect decisions

Paired 1920x1080 AMD EGL measurements below alternate old/new render order.
The reference is the saved pre-goal source, including the earlier slowest-four
pass and all prior authorized changes, not bare HEAD. Seed 7, Laptop profile,
1/60 simulation dt, sine1024/FFT512, mid .4, treble .6, beat .9 for three of each
12 ticks, otherwise .08. Each variant runs 48 total frames: 32 warmup, 16 timed.
Median render/upload/GL-finish time excludes clears, readback, audio, HUD, flip
and VSync. These are uncapped scoped render measurements, not app or Android FPS.
Python 3.14.4, NumPy 2.4.6, Pygame-ce 2.5.7, AMD Rembrandt/radeonsi/Linux x86_64.
Branches was remeasured in isolation after its final per-depth style change;
other rows share the same paired all-mode run. Host/thermal variation means the
absolute times should not be compared with unrelated earlier survey runs.

| Effect | Before ms | After ms | Additional effect-specific decision |
| --- | ---: | ---: | --- |
| Yantra | 31.28 | 4.32 | Keep seven rings/24 spokes; shared upload removes dominant copy. |
| Cube | 35.58 | 8.62 | Keep two cubes and satellite/history surfaces. |
| TriFlux | 54.22 | 28.41 | Keep per-edge antialiasing, tile population and spring integration. |
| Lissajous | 23.29 | 6.00 | Keep bounded trail and three symmetry passes. |
| Tunnel | 29.06 | 10.14 | Keep 30 rings/20 sides and bounded triangle storage. |
| Corridor | 34.21 | 16.24 | Keep layered alpha trails and depth sorting. |
| Nova | 25.00 | 6.21 | Keep sevenfold symmetry and waveform detail. |
| Spiral | 38.63 | 14.95 | Keep full particle/connector geometry. |
| Bubbles | 34.42 | 20.07 | Keep alpha glow passes and bounded 32-surface cache. |
| Plasma | 1.44 | 1.44 | Shader unchanged; tile CPU fallback without changing pixels. |
| Branches | 58.10 | 19.53 | Cache depth styles/sway; retain every recursive segment. |
| Butterflies | 28.29 | 8.55 | Keep courtship/lifetime integration and pair geometry. |
| FlowField | 27.96 | 9.35 | Keep vectorized particle update and cached scaled surface. |
| Fireworks | 34.71 | 20.00 | Keep feedback/advection, particle budget and cached surfaces. |
| Aurora | 25.11 | 11.77 | Keep five ribbon fields and reused presentation surface. |
| Lattice | 35.22 | 20.02 | Earlier merged colour/blend cache retained; shared upload now helps. |
| Mycelium | 30.10 | 16.57 | Keep bounded colony, cached halo and quantized colour cache. |
| Magnetar | 45.80 | 32.21 | Cache fixed-grid radial denominators; preserve field/particles. |
| SlimeMold | 57.41 | 43.39 | Earlier fine-grid/diffusion/palette work retained. |
| Mobius | 24.29 | 10.64 | Keep 60x120 surface-line detail and viewport fit. |
| Chromatic | 18.69 | 4.91 | Keep bounded rings and vectorized distorted contours. |
| Persistence | 21.49 | 8.37 | Keep bounded eight-shape feedback/projection layers. |
| Synapse | 19.10 | 3.57 | Keep bounded graph/signals; topology sorting is not per-frame. |
| Heartbeat | 31.40 | 4.65 | Keep ring-only background and bounded ring geometry. |
| Hyperbolic | 36.01 | 23.02 | Earlier transform/hinge cache retained; no detail change. |
| LiquidLight | 32.61 | 18.54 | Earlier gather/buffer reuse retained; no field change. |
| Cymatica | 45.21 | 25.93 | Cache six static mode fields and repeated particle trig. |
| Phason | 2.01 | 2.06 | Shader unchanged; tile full-resolution CPU waves/palette. |
| Tesseract | 17.97 | 3.97 | Keep bounded 4D geometry and existing projection order. |
| Ferrofluid | 2.19 | 2.26 | Shader unchanged; tile full-resolution CPU field/shading. |
| Spectrum | 37.41 | 10.91 | Keep live bar/waveform detail; native already reuses scratch. |
| Waterfall | 60.12 | 25.49 | Vectorize HLS colours, cache bin widths/frequency hues. |

All 29 surface-uploaded effects improved in this paired fixture. The three
native desktop shader paths (Plasma, Phason, Ferrofluid) were unchanged and
remain approximately 1.4–2.3 ms; small timing differences are measurement noise.
The decisions above preserve detail, simulation/RNG order and existing visual
baselines. They are bounded performance decisions, not claims that no further
optimization could exist. Native source review uses the 32-mode renderer list;
unused CliffordMode is outside that registry.

CPU-only paired changes, same fixture and 32+16 frame schedule:

| Effect | Before ms | After ms |
| --- | ---: | ---: |
| Plasma | 65.13 | 51.21 |
| Magnetar | 47.03 | 40.54 |
| Cymatica | 29.68 | 23.68 |
| Phason | 259.65 | 195.66 |
| Ferrofluid | 628.70 | 427.45 |
| Waterfall | 21.61 | 15.74 |
| Branches | 26.31 | 15.75 |

Ferrofluid and Phason CPU fallback remain slow at full HD despite improvements;
no reduction of resolution, chemistry, waveform count or colour precision was
used to conceal that cost. Their desktop/native shader paths stay fast.

Verification actually run on the final Python source:
- 213 unit tests passed, including real-EGL upload/fallback comparisons. Mocked
  missing-ModernGL messages in the suite are intentional failure-path tests.
- All 32 smoke/regression checks passed; compileall and diff whitespace checks
  passed. New Branches test verifies both recursive neon passes and colour-work
  bounds in normal/low-spec modes; Waterfall checks edge/random colours against
  colorsys. Existing visual baseline values/tolerances were not changed.
- Saved-reference pixel comparisons: all 32 CPU effects matched exactly at
  320x240 for 64 frames; all 32 EGL effects matched at 320x240 for 48 frames.
  Six changed CPU effects matched at 1080p for eight frames. After final edits,
  Branches/Phason matched CPU at 1080p for 12 frames and odd 317x241 for 48;
  Branches also matched EGL at 1080p for 12 frames.
- The eight-frame 1080p EGL comparison matched 31 effects exactly. Phason had
  a first-frame difference of 1/255 in 365 channels; subsequent frames matched.
  An isolated repeat reproduced it, and an old-versus-identical-old control
  reproduced exactly the same first-frame variance over 64 frames. Its shader,
  state advance and GL entrypoints are AST/source identical. This is not
  evidence of a changed Phason shader; do not report every 1080p pixel as exact.
- Paired benchmarks and pixel-check harness/logs are outside the repositories
  under /tmp/goal134_* and /tmp/goal134-*; pre-goal reference sources are local
  temporary evidence, not a portable/versioned artifact. The durable current
  survey remains tests/diagnostics/fps_survey.py with environment/source hashes.

Native decisions: no Android runtime timing was collected. GLDraw circle-cache
and HSL scratch changes address observed repeated trig/allocation costs without
changing detail. Cymatica/Branches cache invariant terms; other modes retain
current detail and existing scratch/work budgets pending the native all-mode
survey. In particular, desktop CPU fallback costs do not justify altering
native shader detail. Android build/JVM/device/FPS/TV validation remains required
and unrun. Backport notes and native TODO record the pending tests and batching
instruction. No commit, push, subtree import, workflow or publication occurred.


Final durable current-state survey completed successfully for all 32 modes on
both CPU and AMD EGL (64 unique effect/backend rows with environment/source
hashes): /tmp/psysuals-goal134-after.json and .log. Command:

`PYTHONDONTWRITEBYTECODE=1 .venv/bin/python tests/diagnostics/fps_survey.py --backend both --json-output /tmp/psysuals-goal134-after.json`

Current scoped render ranking: CPU Ferrofluid 2.3, Phason 5.2, Plasma 19.4,
SlimeMold 23.8 FPS; EGL SlimeMold 21.8, Magnetar 30.9, Fireworks 31.6,
Waterfall 36.0 FPS. Use paired tables above for speedup claims, since this is
an independent survey run. All test/benchmark processes exited normally.

Continuation: local desktop implementation/verification and native source/test
preparation are finished for goal 1/3/4. Required remaining work is the combined
native source-import/build/JVM/device/FPS batch and physical TV acceptance.
Do not mark the full cross-platform goal achieved before those checks. The user
explicitly deferred commits/pushes/subtree import/CI/publication until all edits
are ready together. Do not trigger them autonomously or run a local Android
build. R05-D1 window sizing and physical multi-monitor are separate follow-ups,
not additional work authorized by this goal. Preserve FINDINGS.md and dirty
worktrees. No unrequested long-running processes remain from this pass.

Final Android survey-readiness audit: corrected p95 reporting (the previous
instrumentation test logged the maximum), log actual bloom state and audio/dt/
profile metadata, require all 32 unique modes and finite positive samples, and
allow fpsProfile=tv for target-TV acceptance. Updated native test/backport notes/
TODO only; no workflow change. The linear percentile formula matched NumPy on
three one-off fixtures, and native git diff --check passed. This is source/math
validation, not Android compilation or execution. No Python runtime source
changed; the 213-test/32-smoke results above remain the latest full run. Backups
and reviewed patch: /tmp/androsaver-goal134-survey-audit*. Required native
build/JVM/device/FPS/TV gates remain unrun under the same final-batch hold.

Completion-gate audit 1 (no further implementation progress): all 32 current
effect source hashes still match the completed 64-row CPU/EGL survey; the latest
full Python run remains 213 passing tests and all 32 smoke checks. Both working
branches remain dev with changes uncommitted. Item 3 is verified locally; native
execution for items 1 and 4 is still missing. AndroSaver AGENTS.md forbids local
Android builds, and the user's combined-batch hold still prevents commit/push/
subtree import/CI/publication. No authorized local action can close those gates.
Keep the full goal active, not complete; this is the first consecutive audit at
that impasse after the final survey-readiness correction.

Completion-gate audit 2: same native verification hold revalidated against the
current instructions and branches; no workflow changes or authorization to
start the final batch. Previous turn was no progress, not a verified process
wait. No further code change or repeated test run is justified. Native checks
remain incomplete; keep the goal active pending the blocked-audit threshold.

Completion-gate audit 3: the same final-batch hold remains in force for the
third consecutive goal turn, with no authorized action that can close native
build/JVM/device/FPS/TV gates. The previous two turns were no progress, not
live-process waits. Blocked threshold met: set the full goal to blocked, not
complete. Resume verification when the user authorizes the combined batch;
preserve all prepared changes and the existing no-local-Android-build rule.

## ChromaWeave — new psychedelic mode (2026-11-11)

- [x] Implement a distinctive eightfold iridescent weave: three warped ribbon
  layers, nested breathing petals, saturated travelling colours, a dark central
  rosette and fine cyan sparkles. Write the concrete brief in NEWEFFECTS.md.
- [x] Give bass a fast opening/light pulse over slower swell, mids interference
  travel, and treble fine glints; retain gentle idle motion and bounded release.
- [x] Full-resolution desktop GL shader plus row-tiled NumPy fallback with an
  aspect-preserving 640x360-equivalent CPU sample budget, smooth output scaling
  and further shared quality reduction. No per-cell rectangle rendering.
- [x] Register before Spectrum/Waterfall (33 modes). Preserve earlier visual
  indices 0–29, and document diagnostic indices moving 30/31 to 31/32. Add only
  the new visual baseline (19200, 428073); existing entries/tolerances unchanged.
- [x] Eight focused tests and expanded shared GPU lifecycle coverage: silence/
  colour/centre contrast, visible bass response/release, distinct mids/treble,
  resize/portrait/quality/CPU budget, loud input/TV and real EGL field comparisons
  across odd dimensions and context replacement. All 221 unit tests passed;
  all 33 smoke effects, compileall and whitespace checks passed.
- [x] Prepare matching native GLES2 effect, register mode and settings names,
  update native registry/survey expectations, and add JVM/envelope plus device
  colour/motion/resize/context-recreation checks. Verify source equality of the
  adapted shader and all 33 native registry/label/value names against Python.
- [ ] Native compilation/JVM/device/TV/FPS execution remains in the user's
  deferred combined batch. No local Android build, subtree sync, commit, push,
  workflow or publication. The prior goal remains blocked on those same gates.

Actual local performance: isolated durable survey at 1920x1080, Laptop profile,
seed 7, dt 1/60, mid .4, treble .6, repeating .9/.08 beat fixture, 32 warmup and
16 measured frames. AMD Rembrandt/radeonsi, Python 3.14.4/NumPy 2.4.6/Pygame-ce
2.5.7. Median scoped render/GL-finish time: EGL 3.760 ms (265.9 uncapped FPS),
CPU 43.438 ms (23.0 FPS) at a 640x360 internal grid. A separate matching CPU
quality .6 run used 384x216 and measured 17.293 ms (57.8 FPS). These exclude
audio/HUD/window flip/VSync and are not Android or full-app FPS. The initial
uncapped 1080p CPU prototype was 384.946 ms; the documented CPU sample budget
was added for usability. GL and Android keep full output detail.

Logs/artifacts outside the checkouts: /tmp/chromaweave-focused.log,
/tmp/chromaweave-unit-final.log, /tmp/chromaweave-smoke-final.log,
/tmp/chromaweave-fps-final.json, /tmp/chromaweave-quality.json. Visually inspected
a 1200x800 GPU still plus a 60-frame/20 FPS, 480x320 animation/contact sheet:
/tmp/chromaweave-preview.png, /tmp/chromaweave-preview.gif and
/tmp/chromaweave-contact.png. Preview frames use the actual effect renderer;
they are not generated artwork or committed assets. Try:

`.venv/bin/python psysualizer.py --gl --mode 30`

Files changed for this addition (all earlier work preserved):
- effects/chromaweave.py, effects/__init__.py
- tests/test_chromaweave.py, tests/test_context.py, tests/test_visual_regression.py
- README.md, EFFECTS.md, NEWEFFECTS.md, TODO.md
- AndroSaver app/src/main/java/com/androsaver/visualizer/modes/ChromaWeaveMode.kt
- AndroSaver app/src/main/java/com/androsaver/visualizer/VisualizerRenderer.kt
- AndroSaver app/src/main/res/values/arrays.xml
- AndroSaver app/src/test/java/com/androsaver/ChromaWeaveModeTest.kt
- AndroSaver app/src/androidTest/java/com/androsaver/VisualizerBackportIntegrationTest.kt
- AndroSaver app/src/androidTest/java/com/androsaver/VisualizerPerformanceIntegrationTest.kt
- AndroSaver README.md, docs/psysuals-port-notes.md, TODO.md

Native pre-images and narrow applied patches are under /tmp/androsaver-chromaweave*
and /tmp/androsaver-chromaweave-final-notes*. Existing FINDINGS.md was untouched.

## Combined commit/push/dev batch — authorized 2026-11-11

The user has now explicitly authorized commit, push and merge to dev for the
prepared changes. This supersedes the earlier batching hold recorded above.
Use effects/chromaweave-performance in both repositories, push reviewed commits
and fast-forward dev. Synchronize AndroSaver's psysuals reference subtree from
the new upstream commit before committing the native backports. Leave the
pre-existing untracked FINDINGS.md out of the batch. No main/master update.

Local verification for this exact prepared source: 221 Python tests, all 33
smoke effects, real AMD EGL comparisons, compileall and whitespace checks passed.
GitHub verification and AndroSaver's resulting dev APK are the next gates; do
not substitute a local Android build. Physical TV acceptance remains separate.
