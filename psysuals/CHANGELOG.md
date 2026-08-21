# Changelog

All notable changes to psysuals are documented here.
Versions follow [Semantic Versioning](https://semver.org/).

---

## [3.14.0] — 2026-08-21

### Fixed
- **Bounded effect resources** — Capped Bubbles' visual geometry/cache sizes and Fireworks' sustained-beat rocket/ember populations; Fireworks beat bursts now trigger on threshold crossings.
- **Sample-accurate audio callbacks** — Preserved captured callback lengths for beat tracking, retained the newest fixed-size analysis window for long callbacks, and used ADC-domain end times for fallback onset timing.
- **Safe display-context recreation** — Released foreground/background effects and the shared GL renderer before fullscreen or monitor-geometry context changes, then rebuilt them against the replacement context.
- **Reduced-resolution Butterflies trail** — Kept the internal trail at `RES_DIV` resolution, scaled it into the destination, and guarded tiny viewport edge spawning.
- **Space-key mode cycling** — Implemented the documented Space shortcut while preserving device-picker keyboard precedence.
- **Completed GL benchmark frames** — Present and synchronize measured frames, and avoid redundant Pygame-surface blits for direct-GL effects.
- **Non-XRandR display selection** — Use SDL's detected display count and display target when monitor geometry is unavailable.

### Documentation and verification
- Updated README, architecture, effect behavior, and release notes for the completed audit remediation.
- Verified 52 unit tests, the 27-effect headless smoke test, byte compilation, and whitespace checks.


## [3.13.0] — 2026-07-22

### Fixed
- **Headless smoke and benchmark initialization** — Set and restore `config._INITIALIZED` in the test and benchmark harnesses, and release instantiated effects and graphics resources after each run.
- **ModernGL fallback** — `--gl` now falls back to the CPU display path when ModernGL is unavailable or its context cannot be created, avoiding a blank display.
- **GL fade initialization** — Lazily create and resize fade surfaces before using them, including when starting directly in a reduced-resolution effect.
- **Background effect lifecycle** — Release the previous background effect when cycling or loading a preset to avoid accumulating surfaces and GPU resources.
- **Settings resilience** — Handle unavailable config directories and malformed settings/presets without aborting startup; log failed writes and remove temporary files.
- **Idempotent GL cleanup** — Make shared VBO cleanup safe during repeated shutdown paths.
- **Optional librosa fallback** — Handle all import-time failures from the optional beat-tracking dependency and retain the fallback tracker.
- **Effect RNG isolation** — Use per-effect seeded generators in Persistence, FlowField, Chromatic, and Clifford so their random sequences do not interfere with one another.
- **FlowField particle tuning** — Up/down arrows now add or remove 2,000 particles at a time while FlowField is active, preserving existing particle positions and clamping the range to the supported budget.
- **Display fallback** — Retry failed display initialization with plain CPU/windowed flags instead of repeating an OpenGL failure.
- **Audio teardown** — Make stream stop/close failures best-effort so device fallback can continue and cleanup always clears stream state.
- **Monitor geometry refresh** — Detect monitor size and position changes even when the monitor count is unchanged, then rebuild span children.
- **GL diagnostics** — Report ModernGL import failures while retaining the CPU fallback.
- **Offscreen framebuffer ownership** — Document renderer-owned FBO caching and let effects detect cache eviction before reusing retained references.
- **Beat-tracker lifecycle** — Make release idempotent and safe against analysis submission races.
- **Persistence geometry** — Increased the geometric figure target to roughly 80% of render height and removed the outgoing treble flash circles.
- **Persistence projection scale** — Enlarged the camera focal scale so the outer geometric figures visibly fill most of the viewport instead of appearing undersized.
- **Persistence viewport calibration** — Calibrated the projection against rendered bounds so the outer figures target approximately 80% of viewport height.
- **Clifford visual rework** — Increased attractor density and iteration passes, strengthened emission and beat bloom, and added high-contrast hot cores for a more powerful presentation.
- **SlimeMold edge indexing** — Clamp particle deposit coordinates to the actual trail dimensions to prevent resize/rounding-induced `IndexError` crashes.
- **Aurora first-frame initialization** — Allocate the reusable wave buffer during construction so the first frame cannot assign through `None`.
- **Clifford first-frame initialization** — Initialize the rework's bloom state before its first speed calculation.
- **Mycelium small-display initialization** — Initialize display dimensions before seeding tips and clamp tiny-display tip radii to a valid random range.
- **Signal-safe pygame shutdown** — Defer pygame/audio teardown until the main loop exits so Ctrl-C cannot uninitialize a surface during an active effect draw, and make repeated shutdown requests harmless.
- **TV display tuning** — Use a finer Slimemold render grid on TV-sized displays while retaining its chunkier laptop profile.
- **Laptop motion tuning** — Reduce high-energy motion, jitter, and perspective speed in Cube, Tunnel, and Corridor on smaller displays without changing the large-TV response.
- **Synapse living topology** — Add and remove bounded connection points during runtime, rewire nearest-neighbour edges safely, and give nodes smooth anchor-based wandering.
- **Renderer recreation lifecycle** — Force foreground and background effects to rebuild after fullscreen or monitor-context changes, even when the resolution is unchanged.
- **Shutdown resource cleanup** — Release active effect-owned resources before releasing the shared renderer and pygame display.
- **Corrupt settings recovery** — Treat invalid UTF-8 settings files like other malformed settings and fall back to defaults.
- **Signed monitor geometry** — Parse monitors positioned left of or above the primary display in `xrandr` layouts.
- **Benchmark GL state** — Keep a failed benchmark context attempt local so later benchmark runs can retry ModernGL.
- **Benchmark command entrypoint** — Make the documented `python benchmarks.py` command execute the benchmark instead of exiting silently.
- **Preset corruption recovery** — Ignore invalid UTF-8 and non-object entries in saved presets without aborting startup.
- **Monitor query failure handling** — Preserve the last known monitor layout when `xrandr` is temporarily unavailable.
- **Shader allocation cleanup** — Release compiled shader programs when VAO creation fails.
- **Span child shutdown races** — Make child termination and kill operations safe when processes exit concurrently.

### Documentation
- Added headless smoke-test, benchmark, and `unittest` commands to the README.
- Corrected the README version/Python badges, runtime requirements, control descriptions, project tree, and silent-audio wording.
- Corrected architecture details for spectral flux, silence decay, direct GL rendering, and removed volatile line-count claims.
- Updated the effect catalog's FlowField particle range.
- Updated the release version to 3.13.0 in the application and README.
- Historical implementation notes use review identifiers only; they do not refer to a required review-artifact file.

### Tests
- Added automated tests for audio callback padding/truncation, genre warm-up, device fallback, UTF-8 settings/preset corruption, signed monitor geometry, transient `xrandr` failure, shader allocation cleanup, resize behavior, child-process races, benchmark CLI parsing, and idempotent GL release. The test count is intentionally maintained by the test suite rather than hardcoded here.
- Added coverage for FlowField's 2,000-particle interactive adjustment steps.
- Added coverage for failed stream teardown, monitor geometry changes, display fallback flags, FBO cache ownership, seeded effect initialization, and beat-tracker shutdown races.
- Added regression coverage for Aurora, Clifford, Mycelium, Slimemold, and Synapse first-frame/render paths.
- Added coverage for display-aware Slimemold resolution and laptop motion scaling inputs.
- Added a GitHub Actions matrix for Python 3.10 and 3.12 with native Pygame dependencies, unit tests, and the headless smoke test.
- Hardened partially initialized display cleanup so `__del__` cannot fail when child-process state was never created.

> Audit note: historical review entries below use review-era wording. In the current implementation, `pygame.surfarray.pixels2d()` is indexed as `(x, y)`; FlowField and Magnetar therefore intentionally use `pix[ix, iy]`. SlimeMold's NumPy trail remains row-major `(y, x)` and transposes only when copying to the Pygame surface.

## [3.11.0] — 2026-06-24

### Fixed
- **Screen cleared on effect change** — `_switch_mode()` now calls `self.display.target.fill((0, 0, 0))` unconditionally after capturing the previous frame, so the new effect always starts on a clean black surface regardless of GL/non-GL mode.
- **Slime mold broadcast error** — Fixed `pix[:] = colors` → `pix[:] = colors.T` in `slimemold.py:150` where `pixels2d` returns `(W, H)` but the trail/color array is `(H, W)`.
- **HUD trail spacing missing variable** — Added `spacing = 3` in `_render_hud()` to fix `NameError` when drawing multiband bars.
- **FlowField index swap** — Corrected `pix[ix, iy]` → `pix[iy, ix]` in `flowfield.py` to align with `pixels2d` returning `(W, H)` layout.
- **Clifford transpose removal** — Changed `pix[:] = colors.T` → `pix[:] = colors` in `clifford.py` since density/colors already match `pixels2d` shape.
- **Magnetar index swap** — Corrected `pix[ix, iy]` → `pix[iy, ix]` in `magnetar.py` to align with `pixels2d` `(W, H)` layout.
- **Genre weight flux glitch** — Stored weighted spectrum in `_prev_spectrum` instead of raw spectrum, so `apply_genre_weights()` between frames no longer causes a one-frame spectral flux spike. (M27)
- **Waveform double-copy** — Replaced `self._waveform = mono.copy()` with in-place `self._waveform[:] = mono` to avoid an unnecessary allocation on every audio callback. (M28)
- **False first-frame beat** — Added `_first_frame` flag to skip beat detection on the very first audio frame, preventing a phantom beat from zero-initialized `_prev_spectrum`. (M29)
- **Smooth-FFT half-value first frame** — Changed `_smooth_fft` init from `np.zeros(...)` to `None`; first frame copies spectrum directly instead of averaging with zeros. (M30)
- **Band-energy warmup** — Raised `_flux_avg`/`_mid_avg`/`_treble_avg` initial values from `1e-6` to `0.01`/`0.1`/`0.1` so band-energy ratios are reasonable on the first few frames. (M29 follow-up)
- **UI rect negative width** — Clamped `config.WIDTH - 60` with `max(1, ...)` in device picker to prevent `pygame.Rect` from receiving a negative width on tiny windows. (M33)
- **Preset duplicate "name" key** — Removed `"name"` from the preset entry dict before passing to `settings.save_preset()`, which already inserts `{"name": name, **data}`. (I34)
- **Private method called externally** — Renamed `DisplayManager._spawn_child()` → `spawn_child()` and updated the call in `psysualizer.py` to use the public name. (I35)
- **GL surface upload duplication** — Extracted `_upload_surface()` helper shared by `blit()` and `surface_texture()`, reducing code duplication in texture upload logic. (I36, I37)
- **Redundant X11 round-trips** — Reduced `reposition_window_fix()` calls from 60 frames down to a single call at `tick == 0`. (I41)
- **AudioEngine.release()** — Added a public `release()` method that cleans up the stream and beat tracker; `_quit()` now calls `self.audio.release()` instead of manually accessing `beat_tracker`. (I46)
- **Aurora NameError** — Fixed `k_unit = math.tau / W` → `k_unit = math.tau / config.WIDTH` in `__init__()` where `W` was never defined. (C1)
- **Init ordering / orphan children** — Moved `_setup_signals()` and `atexit.register()` before `_init_audio()` and `spawn_span_children()` to close the gap where an exception could leak child processes and zombie audio threads. (C2, M24)
- **GL FBO viewport** — `render()` now sets `ctx.viewport` to the FBO dimensions when rendering offscreen, fixing clipped rendering on non-default-sized framebuffers. (C3)
- **GL double-release** — `release()` nullifies all released GL objects (`_blit_tex`, `_feedback_tex`, `_vbo`, VAOs, programs) to prevent undefined behavior on accidental double-release. (C4)
- **Triflux resize handling** — Replaced stale `config.WIDTH/HEIGHT` references with `self._W/self._H` from `surf.get_size()`, triggering grid rebuild on resize. (M21)
- **Mycelium resize handling** — Same migration from `config.WIDTH/HEIGHT` to `self._W/self._H` with auto-rebuild of core positions. (M22)
- **Bubbles resize handling** — Same migration; `_make()` converted from `@staticmethod` to instance method using `self._W/self._H`. (M23)
- **Child wait timeout** — `child.wait()` in span-mode respawn loop now uses `timeout=1` to prevent indefinite blocking. (M25)
- **Beat tracking bare except** — Bare `except Exception: return` now prints a diagnostic before returning. (M26)
- **AudioEngine `__import__("sys")`** — Replaced runtime `__import__("sys")` with top-level `import sys` across all modules. (I38, I39, I40)
- **Regression tester init flag** — `run_all_tests()` now sets `config._INITIALIZED = True` after pygame setup. (M31)
- **UI device picker negative visible** — Clamped `visible` with `max(0, ...)` to prevent negative range on tiny screens. (M32)
- **Settings PermissionError** — `save()` and `_write_presets()` catch `(PermissionError, OSError)` to handle write-protected config dirs gracefully. (M34)
- **Silence beat floor scope** — `silence_beat_floor` is now defined in both `if self.is_silent:` and `else:` branches, preventing a potential `NameError` during silence transitions. (I32)
- **settings.py: PermissionError handling** — Added try/except to `save()` and `_write_presets()` to handle write-protected directories gracefully.
- **config.py: RNG_SEED=0 seeding** — `RNG_SEED` now seeds the RNGs when `PSYSUALS_SEED=0` is explicitly set; previously `0` was treated as falsy and ignored. (I42)
- **Benchmarks config restore** — Wrapped benchmark body in `try/finally` to always restore `config.WIDTH/HEIGHT`. (I43)
- **Smoke test env var placement** — Moved `os.environ['SDL_VIDEODRIVER'] = 'dummy'` from module level into `run_smoke_tests()`. (I44)
- **Corridor unused import** — Removed `import numpy` from corridor.py. (I45)
- **Presets in-memory append** — New presets are appended to `self.presets` directly instead of reloading from disk after every save. (review I8)
- **Waterfall grid ROWS/COLS** — Removed leftover `classmethod` keyword and unused `ROWS`/`COLS` class attributes after resolution-aware grid migration. (review C1, I32)
- **Cube projection args** — `_project()` / `_project_sat()` now accept `W, H` explicitly via `_target_size()` instead of relying on stale config globals. (review C2, I42)
- **Effect init order / seed plumbing** — Reordered `super().__init__()` before `_rng` in `fireworks`/`butterflies`/`mycelium`; migrated `triflux`, `synapse`, `fireworks`, and `mycelium` from `random.*` to `self._rng.*` for full seed reproducibility. (review M21, I10)
- **Config initialized guard** — Added `config._INITIALIZED` flag and `assert_initialized()`; called at start of every `Effect.__init__()` via the base class. (review M23, M25, I40)
- **Spectrum bar width guard** — Clamped `bar_w - 2` with `max(1, ...)` to prevent negative-width `pygame.Rect`. (review I41)
- **Butterflies scaled surface pre-allocation** — Pre-allocate `_scaled` surface and reuse `pygame.transform.scale(dest=...)` to avoid per-frame surface allocation on resize. (review I39)
- **GL context attributes guard** — `gl_set_attribute` calls are now only made when `_gl_attrs_set` is `False`, preventing silent ignores on display re-open. (review I5)
- **Smoke test window size** — Changed headless test window from `1×1` to `320×240` for compatibility with stricter SDL compositors. (review I6)
- **Benchmarks config restore** — Benchmark restore `config.WIDTH`/`HEIGHT` after run. (review I7)
- **Presets append-only** — `save_preset()` is now append-only with a separate `_write_presets()` for disk writes, never mutates existing entries. (review I8)
- **Span mode xrandr re-query** — `requery_xmonitors()` is called before respawning dead child processes to detect hotplug changes. (review I9)
- **Dead import removal** — Removed unused `math`/`random` imports from corridor, bubbles, triflux, cube, waterfall, spectrum, and synaes. (review I33–I38)
- **Span child zombie leak** — Call `.wait()` before respawning dead span children to prevent zombie process accumulation. (review I28)
- **Negative mode index from settings** — Clamp `mode_idx` with `max(0, ...)` to prevent out-of-bounds access on corrupt settings.
- **`_hsl_batch` parameter order** — Reordered signature to `(h, s, l)` matching `hsl()` convention and updated all callers to use keyword arguments. (review I29)
- **Slime mold axis swap** — Corrected NumPy row-major indexing (`[y, x]`) in trail sensing and deposit to fix diffusion direction on non-square screens. (review M18)
- **Beat tracker flag race** — Removed premature `_analysis_running = False` from early-return and exception paths; `finally` block now handles all exits exclusively. (review M20)
- **Font fallback chain** — Replaced single `SysFont("monospace")` with a fallback chain (`monospace`, `Courier New`, `DejaVu Sans Mono`, etc.) for HUD, settings pane, device picker, and tap-tempo display. (review I27)
- **Missing settings default** — Added `effect_gain: 0.7` to `settings._DEFAULTS` for consistent first-run behavior.
- **Narrow genre weights** — Expanded genre frequency weights from 20 bins to the full 512-bin FFT spectrum with proportional band slicing per genre. (review M19)
- **Thread-per-analysis overhead** — Replaced `threading.Thread` with `ThreadPoolExecutor(max_workers=1)` in beat tracking to reduce thread creation churn and ensure orderly shutdown.
- **Dead code removal** — Removed unused `pygame.image.to_string` conditional branches in `GLRenderer` and unused `silence_beat_floor` assignment in `psysualizer.py`.
- **Genre detection accumulator bound** — Reset unconditionally every 300 frames regardless of genre match, preventing unbounded growth during prolonged silence or near-silence. (review I30)
- **Genre detection reset race** — Moved accum/frame reset inside the lock (`core/audio_engine.py:74-76`) so the callback cannot interleave and lose a frame's contribution. (review I31)
- **Magnetar scaled surface** — Lazy-init `_scaled` from `surf.get_size()` in `draw()` to handle render target size changes (`effects/magnetar.py:60-61`).
- **Audio callback exception logging** — Changed bare `pass` to `traceback.print_exc()` in the audio callback exception handler so FFT/buffer errors are visible. (review M2)
- **Fade surface pre-allocation** — Pre-allocate a single fade surface and re-fill with `fill()` on alpha change instead of allocating a new `pygame.Surface` every ~5 seconds. (review M7)
- **Möbius treble dead code removed** — Removed unused `_u_off` attribute and dead treble-driven longitude code; treble now contributes to rotation speed instead. (review M9)
- **Waterfall resolution-aware grid** — Derived grid dimensions from `config.HEIGHT`/`WIDTH` via `_grid_dims()` instead of hardcoded 100×80. (review M10)
- **Spectrum peak marker clamp** — Peak markers in `Bars` only draw when `bar_h > 0`, preventing orphan peaks above zero-height bars. (review M11)
- **Config initialization guard** — Added `config.assert_initialized()` and `_INITIALIZED` flag to catch effects accessing WIDTH/HEIGHT before display init. (review M12)
- **Spectrum uses surf.get_size()** — `Bars` derives all drawing dimensions from `surf.get_size()` instead of `config.WIDTH`/`HEIGHT` globals. (review M13)
- **Flatten open_input_stream candidates** — Pre-flatten candidate list before iteration to eliminate redundant `preferred_input_candidates(None)` double expansion. (review M14)
- **PlasmaGL lazy fallback init** — CPU fallback surface and meshgrid arrays are now rebuilt on resolution change via `_ensure_fallback(w, h)`. (review M15)
- **Remove list() in audio callback** — Removed unnecessary `list()` conversion in `np.diff(list(self._beat_times))` in the real-time audio callback to reduce allocation pressure. (review M16)
- **10+ effects surf.get_size() migration** — Migrated branches, yantra, nova, spiral, tunnel, heartbeat, synapse, chromatic, lissajous, mobius, aurora, and corridor from `config.WIDTH`/`HEIGHT` to `surf.get_size()` for all drawing coordinates. (review M17)
- **Display index validation warning** — Added warning message when `display_idx` falls back to 0 due to out-of-range value. (review I1)
- **Pygame display info cache** — Cached `pygame.display.Info()` result in `_auto_res_div()` with invalidation on dimension change to avoid per-frame video driver queries. (review I4)
- **Rebuild effects dimension guard** — `_rebuild_effects` skips the full release-and-recreate cycle when `(WIDTH, HEIGHT)` hasn't changed. (review I14)
- **Remove list() in tap tempo** — Removed unnecessary `list()` conversion in `np.diff(list(self.tap_times))`. (review I15)
- **Butterflies BLEND_RGB_MAX** — Changed `BLEND_RGBA_MAX` to `BLEND_RGB_MAX` on 24-bit RGB trail surface for correctness. (review I16)
- **Corridor/Persistence RES_DIV** — Set `RES_DIV = 2` on both effects to reduce fill-rate cost at high resolutions. (review I17)
- **Waterfall np.random in hot loop** — Replaced `random.random()` calls in the hot draw loop with a single `np.random.uniform()` per row. (review I18)
- **Settings save on quit** — Moved `_save_settings()` into `_quit()` so settings persist on SIGINT/SIGTERM/KeyboardInterrupt, not just Q/window-close. (review I20)
- **Benchmark GL resource leak** — Call `vis_gl.release()` between benchmark iterations to free GPU resources. (review I21)
- **Aurora quad soft-fade** — Reduced glow/core color opacity on aurora quads near clip boundaries to prevent hard horizontal clip lines. (review I22)
- **PlasmaGL fallback _render_div** — CPU fallback now uses `_render_div()` instead of hardcoded `// 4` for resolution-adaptive sizing. (review I23)
- **Beat onset threshold 0.22s** — Reduced minimum inter-onset interval from 0.28s (~214 BPM) to 0.22s (~273 BPM) for breakcore/psytrance compatibility. (review I24)
- **qmd.yml glob scope** — Scoped `source` glob pattern to `[!.]*.py` with `__pycache__`/`.venv` exclusions. (review I25)
- **Beat tracker shutdown** — Call `self.audio.beat_tracker.release()` in `_quit()` to shut down `ThreadPoolExecutor` gracefully. (review I26)
- **Slime mold axis swap** — Corrected trail array shape `(W, H)` → `(H, W)` and all indexing to numpy row-major `[y, x]`. (B1)
- **Corridor infinite surface leak on resize** — `_init_surfs()` now receives `(W, H)` from `draw()`; split semicolon-chained statements. (B2, I13)
- **Aurora division by zero** — Added `max(tot_w, 1e-6)` guard in harmonic weight normalization. (B3)
- **Chromatic stale radius fade** — Re-read `r = ring[2]` after mutation; safe filter-then-iterate ring removal. (B4)
- **Fireworks ember destructure** — All 7 mutable positional fields (x, y, vx, vy, hue, radius, life) explicitly written per frame. (B5)
- **Fireworks sub-pixel zoom drift** — `int(W * zoom)` → `round(W * zoom)` to prevent centering error accumulation. (B6)
- **Lattice aspect-agnostic scaling** — `min(W, H) / 640.0` for glow thickness and shockwave width. (B8)
- **Butterflies trail surface on resize** — Fresh `pygame.Surface(surf.get_size())` on size mismatch instead of stale scaled surface. (B9)
- **Flowfield/clifford stale config refs** — Compare against `surf.get_size()` instead of `config.WIDTH/HEIGHT`. (B10)
- **IS_GL compositing inversion** — Guard in `_render()` skips fade/background/crossfade blits for IS_GL effects; backbuffer shows correctly behind SRCALPHA overlays. (B16)
- **Aurora phases on resize** — `_phases` now rebuilt alongside `_ks` on resize to stay in sync with harmonic structure. (B7)
- **Duplicate SIGINT handling** — Removed outer `try/except KeyboardInterrupt` in `__main__`; signal handler now owns all cleanup. (B11)
- **Preset bg_vis RES_DIV** — `_update_target_res()` re-called after creating new `bg_vis` from preset to account for background render divisor. (B12)
- **Persistence RES_DIV** — Changed `W, H = surf.get_size()` to `self._render_size()[:2]`; draw to private `_scaled` surface at reduced resolution then scale up. (B13)
- **BeatTracking `__import__("sys")`** — Replaced runtime import with top-level `import sys`. (B14)
- **DisplayManager `__import__("sys")`** — Replaced runtime import with top-level `import sys`. (B15)
- **Magnetar np.full → scalar** — Replaced per-frame `np.full(n, val)` allocation with scalar passed directly to `_hsl_batch`. (O5)
- **Aurora wave pre-allocation** — Pre-allocate `self._wave` on init/resize instead of per-ribbon `np.zeros` in the hot draw loop. (O8)
- **Lattice scaled surface cache** — Cache `self._scaled` and reuse via `pygame.transform.scale(dest=...)` to avoid per-frame surface allocation. (O9)
- **AudioEngine _band_bounds guard** — Added `if size else 0` to prevent negative start index on zero-size arrays. (I4)
- **Tunnel unused import** — Removed unused `import numpy` from tunnel.py. (I10)
- **Butterflies dead code** — Removed commented-out antenna drawing code. (I11)
- **Corridor semicolons** — Split `pygame.Surface(…) ; fill(…)` onto separate lines. (I13)
- **Fireworks docstring** — Corrected stale "tunnel with fireworks" description to "fireworks display". (I14)
- **Chromatic shadowed ring** — Renamed loop variable `ring` → `entry` to avoid shadowing the outer loop variable. (I15)
- **Magnetar r3 pre-compute** — Pre-computed `r3 = r2 * r + 0.1` once instead of repeating it in both dipole field components. (I19)
- **Lattice FFT_START_BIN constant** — Extracted magic number `3` into module-level `_FFT_START_BIN`. (M4)
- **Tunnel MAX_TRIS constant** — Extracted magic number `30` into class constant `MAX_TRIS`. (M5)
- **Yantra backward loop comment** — Added comment explaining the reverse ring iteration. (M7)
- **Lissajous symmetry rotation reuse** — Pre-computed `sym_ca_sa` list to avoid redundant `cos`/`sin` calls in the second loop. (M8)
- **Butterflies short FFT guard** — Guarded `fft[:6]` with `min(6, len(fft))` to prevent silent partial-array mean on small FFTs. (M9)
- **_hsl_batch scalar default** — Changed `s=1.0` to `s=np.float32(1.0)` so the default matches the dtype used by array inputs. (I1)
- **_cached_info ClassVar** — Added `from typing import ClassVar` and annotated `_cached_info` as `ClassVar[...]` to accurately reflect its class-level scope. (I2)
- **GLRenderer FBO cache cap** — `offscreen()` now evicts the oldest entry when the cache exceeds 8 FBOs, preventing unbounded growth during resize. (I3)
- **Butterflies pair offset constants** — Extracted magic numbers `0`, `(300,700)`, `(800,1400)` into named class constants `_PAIR_OFFSET_1` etc. (I12)
- **Palette tuple docs** — Added index comment documenting `(target_hue, drift_speed, sat_boost, trail_alpha)` layout. (I20)
- **Genre detection throttle** — `detect_genre()` now called every 60 frames during silence instead of every frame, reducing lock contention during prolonged idle. (I21)
- **PlasmaGL FBO cache** — Handled by the same `gl_renderer.py` cap; `_fbo_cache` delegates to the already-capped `r.offscreen()`. (M10)
- **Seeded RNG in 4 effects** — Added `self._rng` to tunnel, corridor, bubbles, and butterflies; replaced `random.*` calls with `self._rng.*` for full seed reproducibility. (I8)
- **Heartbeat namedtuple** — Replaced positional list-of-7 ring state with a typed `_Ring` NamedTuple. (I16)
- **Mycelium _hsl_batch** — Batched two per-segment `hsl()` calls into one `_hsl_batch` call, reducing Python-level `colorsys.hls_to_rgb` invocations. (M2)
- **SlimeMold _reset_sim → _reset_or_resize_sim** — Renamed to reflect dual purpose (constructor + resize handler). (M3)
- **Lissajous list(zip) → direct array indexing** — Replaced `list(zip(sx_arr.tolist(), sy_arr.tolist()))` with direct array access; hoisted `h_arr` outside the passes loop to avoid redundant hue recomputation. (O3, O4)
- **HUD background to UIManager** — Moved `_hud_bg` surface from `VisualizerApp` to `UIManager.draw_hud_background()` for better encapsulation. (I7)
- **Aurora fade pre-computation** — Pre-computed fade factors per vertex with numpy outside the per-quad inner loop, replacing per-polygon int divisions with vectorized operations. (M6)
- **Chromatic _wave_points numpy vectorization** — Replaced Python per-point for loop with numpy vectorized operations for all 72 points × 3 channels × 14 rings. (O10)
- **Butterflies _Pair.draw() seeded RNG** — Pass `self._rng` through to `_Pair` and `_Butterfly` classes, replacing `random.*` with `self._rng.*` in the hot draw loop and all helper methods. (O11)
- **Nova list(zip) → np.column_stack** — Replaced `list(zip(xs.tolist(), ys.tolist()))` with `np.column_stack([xs, ys])` in per-layer per-sym loop. (O13)
- **Effects __init__ import guard** — Noted that all effects already defer to `__init__`, mitigating the fragility concern. (I5)
- **Resolved stale config refs** — Flowfield, clifford, lattice already migrated to `surf.get_size()` in earlier passes. (I9)
- **Clifford meshgrid hoist** — Hoisted `np.meshgrid` and grid coordinate arrays into `_reset_state()` so they're only rebuilt on resize, saving ~16 MB per frame at 1080p. (O1)
- **Clifford pre-allocated point buffer** — Replaced per-frame `all_x = []`/`all_y = []` + `np.concatenate` with a pre-allocated `(4, N)` array filled in-place. (O2)
- **FlowField pre-allocated arrays** — Pre-allocated `_angles`, `_ir`/`_ig`/`_ib`, and `_colors_arr` to avoid per-frame `np.zeros` and uint32 array allocation in the hot loop. (O6)
- **Lattice beam drawing dedup** — Factored duplicated horizontal/vertical beam drawing into `_draw_beam()` helper, reducing ~40 lines to 8. (I17)
- **Lattice _resize split** — Split `_resize` into `_init_surfaces()` (surface/geometry) and `_build_grid()` (grid nodes/column peaks) for cleaner separation. (I18)
- **Corridor spark struct-of-arrays** — Replaced per-spark dicts with fixed-size numpy arrays (`_sz`, `_shue`, `_sox`, `_soy`) and in-place compaction, eliminating dict allocation overhead for up to 100 sparks. (O12)
- **Tunnel triangle struct-of-arrays** — Replaced per-triangle dicts with fixed-size numpy arrays (`_tz`, `_tpt`, `_trot`, `_trvel`, `_tsize`, `_thue`) and in-place compaction, eliminating dict allocation overhead for up to 30 triangles. (O14)
- **UIManager use target.get_size()** — Replaced `config.WIDTH/HEIGHT` with `target.get_size()` / `target.get_width()` / `target.get_height()` in all cached surface checks and positioning, removing stale-config risk. (I22)
- **GL renderer pre-allocated upload buffer** — Replaced `pygame.image.tostring` (8 MB per-frame bytes allocation) with a pre-allocated numpy buffer filled via `pixels3d`/`pixels_alpha` views, eliminating per-frame 8 MB allocation in the GL blit path. (O15)

<!-- Duplicate 3.11.0 entries merged into the release section above. -->

### Fixed
- **Screen cleared on effect change** — `_switch_mode()` now calls `self.display.target.fill((0, 0, 0))` unconditionally after capturing the previous frame, so the new effect always starts on a clean black surface regardless of GL/non-GL mode.
- **Slime mold broadcast error** — Fixed `pix[:] = colors` → `pix[:] = colors.T` in `slimemold.py:150` where `pixels2d` returns `(W, H)` but the trail/color array is `(H, W)`.
- **HUD trail spacing missing variable** — Added `spacing = 3` in `_render_hud()` to fix `NameError` when drawing multiband bars.
- **FlowField index swap** — Corrected `pix[ix, iy]` → `pix[iy, ix]` in `flowfield.py` to align with `pixels2d` returning `(W, H)` layout.
- **Clifford transpose removal** — Changed `pix[:] = colors.T` → `pix[:] = colors` in `clifford.py` since density/colors already match `pixels2d` shape.
- **Magnetar index swap** — Corrected `pix[ix, iy]` → `pix[iy, ix]` in `magnetar.py` to align with `pixels2d` `(W, H)` layout.
- **Genre weight flux glitch** — Stored weighted spectrum in `_prev_spectrum` instead of raw spectrum, so `apply_genre_weights()` between frames no longer causes a one-frame spectral flux spike. (M27)
- **Waveform double-copy** — Replaced `self._waveform = mono.copy()` with in-place `self._waveform[:] = mono` to avoid an unnecessary allocation on every audio callback. (M28)
- **False first-frame beat** — Added `_first_frame` flag to skip beat detection on the very first audio frame, preventing a phantom beat from zero-initialized `_prev_spectrum`. (M29)
- **Smooth-FFT half-value first frame** — Changed `_smooth_fft` init from `np.zeros(...)` to `None`; first frame copies spectrum directly instead of averaging with zeros. (M30)
- **Band-energy warmup** — Raised `_flux_avg`/`_mid_avg`/`_treble_avg` initial values from `1e-6` to `0.01`/`0.1`/`0.1` so band-energy ratios are reasonable on the first few frames. (M29 follow-up)
- **UI rect negative width** — Clamped `config.WIDTH - 60` with `max(1, ...)` in device picker to prevent `pygame.Rect` from receiving a negative width on tiny windows. (M33)
- **Preset duplicate "name" key** — Removed `"name"` from the preset entry dict before passing to `settings.save_preset()`, which already inserts `{"name": name, **data}`. (I34)
- **Private method called externally** — Renamed `DisplayManager._spawn_child()` → `spawn_child()` and updated the call in `psysualizer.py` to use the public name. (I35)
- **GL surface upload duplication** — Extracted `_upload_surface()` helper shared by `blit()` and `surface_texture()`, reducing code duplication in texture upload logic. (I36, I37)
- **Redundant X11 round-trips** — Reduced `reposition_window_fix()` calls from 60 frames down to a single call at `tick == 0`. (I41)
- **AudioEngine.release()** — Added a public `release()` method that cleans up the stream and beat tracker; `_quit()` now calls `self.audio.release()` instead of manually accessing `beat_tracker`. (I46)
- **Aurora NameError** — Fixed `k_unit = math.tau / W` → `k_unit = math.tau / config.WIDTH` in `__init__()` where `W` was never defined. (C1)
- **Init ordering / orphan children** — Moved `_setup_signals()` and `atexit.register()` before `_init_audio()` and `spawn_span_children()` to close the gap where an exception could leak child processes and zombie audio threads. (C2, M24)
- **GL FBO viewport** — `render()` now sets `ctx.viewport` to the FBO dimensions when rendering offscreen, fixing clipped rendering on non-default-sized framebuffers. (C3)
- **GL double-release** — `release()` nullifies all released GL objects (`_blit_tex`, `_feedback_tex`, `_vbo`, VAOs, programs) to prevent undefined behavior on accidental double-release. (C4)
- **Triflux resize handling** — Replaced stale `config.WIDTH/HEIGHT` references with `self._W/self._H` from `surf.get_size()`, triggering grid rebuild on resize. (M21)
- **Mycelium resize handling** — Same migration from `config.WIDTH/HEIGHT` to `self._W/self._H` with auto-rebuild of core positions. (M22)
- **Bubbles resize handling** — Same migration; `_make()` converted from `@staticmethod` to instance method using `self._W/self._H`. (M23)
- **Child wait timeout** — `child.wait()` in span-mode respawn loop now uses `timeout=1` to prevent indefinite blocking. (M25)
- **Beat tracking bare except** — Bare `except Exception: return` now prints a diagnostic before returning. (M26)
- **AudioEngine `__import__("sys")`** — Replaced runtime `__import__("sys")` with top-level `import sys` across all modules. (I38, I39, I40)
- **Regression tester init flag** — `run_all_tests()` now sets `config._INITIALIZED = True` after pygame setup. (M31)
- **UI device picker negative visible** — Clamped `visible` with `max(0, ...)` to prevent negative range on tiny screens. (M32)
- **Settings PermissionError** — `save()` and `_write_presets()` catch `(PermissionError, OSError)` to handle write-protected config dirs gracefully. (M34)
- **Silence beat floor scope** — `silence_beat_floor` is now defined in both `if self.is_silent:` and `else:` branches, preventing a potential `NameError` during silence transitions. (I32)
- **settings.py: PermissionError handling** — Added try/except to `save()` and `_write_presets()` to handle write-protected directories gracefully.
- **config.py: RNG_SEED=0 seeding** — `RNG_SEED` now seeds the RNGs when `PSYSUALS_SEED=0` is explicitly set; previously `0` was treated as falsy and ignored. (I42)
- **Benchmarks config restore** — Wrapped benchmark body in `try/finally` to always restore `config.WIDTH/HEIGHT`. (I43)
- **Smoke test env var placement** — Moved `os.environ['SDL_VIDEODRIVER'] = 'dummy'` from module level into `run_smoke_tests()`. (I44)
- **Corridor unused import** — Removed `import numpy` from corridor.py. (I45)
- **Presets in-memory append** — New presets are appended to `self.presets` directly instead of reloading from disk after every save. (FINDINGS.md: I8)
- **Waterfall grid ROWS/COLS** — Removed leftover `classmethod` keyword and unused `ROWS`/`COLS` class attributes after resolution-aware grid migration. (FINDINGS.md: C1, I32)
- **Cube projection args** — `_project()` / `_project_sat()` now accept `W, H` explicitly via `_target_size()` instead of relying on stale config globals. (FINDINGS.md: C2, I42)
- **Effect init order / seed plumbing** — Reordered `super().__init__()` before `_rng` in `fireworks`/`butterflies`/`mycelium`; migrated `triflux`, `synapse`, `fireworks`, and `mycelium` from `random.*` to `self._rng.*` for full seed reproducibility. (FINDINGS.md: M21, I10)
- **Config initialized guard** — Added `config._INITIALIZED` flag and `assert_initialized()`; called at start of every `Effect.__init__()` via the base class. (FINDINGS.md: M23, M25, I40)
- **Spectrum bar width guard** — Clamped `bar_w - 2` with `max(1, ...)` to prevent negative-width `pygame.Rect`. (FINDINGS.md: I41)
- **Butterflies scaled surface pre-allocation** — Pre-allocate `_scaled` surface and reuse `pygame.transform.scale(dest=...)` to avoid per-frame surface allocation on resize. (FINDINGS.md: I39)
- **GL context attributes guard** — `gl_set_attribute` calls are now only made when `_gl_attrs_set` is `False`, preventing silent ignores on display re-open. (FINDINGS.md: I5)
- **Smoke test window size** — Changed headless test window from `1×1` to `320×240` for compatibility with stricter SDL compositors. (FINDINGS.md: I6)
- **Benchmarks config restore** — Benchmark restore `config.WIDTH`/`HEIGHT` after run. (FINDINGS.md: I7)
- **Presets append-only** — `save_preset()` is now append-only with a separate `_write_presets()` for disk writes, never mutates existing entries. (FINDINGS.md: I8)
- **Span mode xrandr re-query** — `requery_xmonitors()` is called before respawning dead child processes to detect hotplug changes. (FINDINGS.md: I9)
- **Dead import removal** — Removed unused `math`/`random` imports from corridor, bubbles, triflux, cube, waterfall, spectrum, and synaes. (FINDINGS.md: I33–I38)
- **Span child zombie leak** — Call `.wait()` before respawning dead span children to prevent zombie process accumulation. (FINDINGS.md: I28)
- **Negative mode index from settings** — Clamp `mode_idx` with `max(0, ...)` to prevent out-of-bounds access on corrupt settings.
- **`_hsl_batch` parameter order** — Reordered signature to `(h, s, l)` matching `hsl()` convention and updated all callers to use keyword arguments. (FINDINGS.md: I29)
- **Slime mold axis swap** — Corrected NumPy row-major indexing (`[y, x]`) in trail sensing and deposit to fix diffusion direction on non-square screens. (FINDINGS.md: M18)
- **Beat tracker flag race** — Removed premature `_analysis_running = False` from early-return and exception paths; `finally` block now handles all exits exclusively. (FINDINGS.md: M20)
- **Font fallback chain** — Replaced single `SysFont("monospace")` with a fallback chain (`monospace`, `Courier New`, `DejaVu Sans Mono`, etc.) for HUD, settings pane, device picker, and tap-tempo display. (FINDINGS.md: I27)
- **Missing settings default** — Added `effect_gain: 0.7` to `settings._DEFAULTS` for consistent first-run behavior.
- **Narrow genre weights** — Expanded genre frequency weights from 20 bins to the full 512-bin FFT spectrum with proportional band slicing per genre. (FINDINGS.md: M19)
- **Thread-per-analysis overhead** — Replaced `threading.Thread` with `ThreadPoolExecutor(max_workers=1)` in beat tracking to reduce thread creation churn and ensure orderly shutdown.
- **Dead code removal** — Removed unused `pygame.image.to_string` conditional branches in `GLRenderer` and unused `silence_beat_floor` assignment in `psysualizer.py`.
- **Genre detection accumulator bound** — Reset unconditionally every 300 frames regardless of genre match, preventing unbounded growth during prolonged silence or near-silence. (FINDINGS.md: I30)
- **Genre detection reset race** — Moved accum/frame reset inside the lock (`core/audio_engine.py:74-76`) so the callback cannot interleave and lose a frame's contribution. (FINDINGS.md: I31)
- **Magnetar scaled surface** — Lazy-init `_scaled` from `surf.get_size()` in `draw()` to handle render target size changes (`effects/magnetar.py:60-61`).
- **Audio callback exception logging** — Changed bare `pass` to `traceback.print_exc()` in the audio callback exception handler so FFT/buffer errors are visible. (FINDINGS.md: M2)
- **Fade surface pre-allocation** — Pre-allocate a single fade surface and re-fill with `fill()` on alpha change instead of allocating a new `pygame.Surface` every ~5 seconds. (FINDINGS.md: M7)
- **Möbius treble dead code removed** — Removed unused `_u_off` attribute and dead treble-driven longitude code; treble now contributes to rotation speed instead. (FINDINGS.md: M9)
- **Waterfall resolution-aware grid** — Derived grid dimensions from `config.HEIGHT`/`WIDTH` via `_grid_dims()` instead of hardcoded 100×80. (FINDINGS.md: M10)
- **Spectrum peak marker clamp** — Peak markers in `Bars` only draw when `bar_h > 0`, preventing orphan peaks above zero-height bars. (FINDINGS.md: M11)
- **Config initialization guard** — Added `config.assert_initialized()` and `_INITIALIZED` flag to catch effects accessing WIDTH/HEIGHT before display init. (FINDINGS.md: M12)
- **Spectrum uses surf.get_size()** — `Bars` derives all drawing dimensions from `surf.get_size()` instead of `config.WIDTH`/`HEIGHT` globals. (FINDINGS.md: M13)
- **Flatten open_input_stream candidates** — Pre-flatten candidate list before iteration to eliminate redundant `preferred_input_candidates(None)` double expansion. (FINDINGS.md: M14)
- **PlasmaGL lazy fallback init** — CPU fallback surface and meshgrid arrays are now rebuilt on resolution change via `_ensure_fallback(w, h)`. (FINDINGS.md: M15)
- **Remove list() in audio callback** — Removed unnecessary `list()` conversion in `np.diff(list(self._beat_times))` in the real-time audio callback to reduce allocation pressure. (FINDINGS.md: M16)
- **10+ effects surf.get_size() migration** — Migrated branches, yantra, nova, spiral, tunnel, heartbeat, synapse, chromatic, lissajous, mobius, aurora, and corridor from `config.WIDTH`/`HEIGHT` to `surf.get_size()` for all drawing coordinates. (FINDINGS.md: M17)
- **Display index validation warning** — Added warning message when `display_idx` falls back to 0 due to out-of-range value. (FINDINGS.md: I1)
- **Pygame display info cache** — Cached `pygame.display.Info()` result in `_auto_res_div()` with invalidation on dimension change to avoid per-frame video driver queries. (FINDINGS.md: I4)
- **Rebuild effects dimension guard** — `_rebuild_effects` skips the full release-and-recreate cycle when `(WIDTH, HEIGHT)` hasn't changed. (FINDINGS.md: I14)
- **Remove list() in tap tempo** — Removed unnecessary `list()` conversion in `np.diff(list(self.tap_times))`. (FINDINGS.md: I15)
- **Butterflies BLEND_RGB_MAX** — Changed `BLEND_RGBA_MAX` to `BLEND_RGB_MAX` on 24-bit RGB trail surface for correctness. (FINDINGS.md: I16)
- **Corridor/Persistence RES_DIV** — Set `RES_DIV = 2` on both effects to reduce fill-rate cost at high resolutions. (FINDINGS.md: I17)
- **Waterfall np.random in hot loop** — Replaced `random.random()` calls in the hot draw loop with a single `np.random.uniform()` per row. (FINDINGS.md: I18)
- **Settings save on quit** — Moved `_save_settings()` into `_quit()` so settings persist on SIGINT/SIGTERM/KeyboardInterrupt, not just Q/window-close. (FINDINGS.md: I20)
- **Benchmark GL resource leak** — Call `vis_gl.release()` between benchmark iterations to free GPU resources. (FINDINGS.md: I21)
- **Aurora quad soft-fade** — Reduced glow/core color opacity on aurora quads near clip boundaries to prevent hard horizontal clip lines. (FINDINGS.md: I22)
- **PlasmaGL fallback _render_div** — CPU fallback now uses `_render_div()` instead of hardcoded `// 4` for resolution-adaptive sizing. (FINDINGS.md: I23)
- **Beat onset threshold 0.22s** — Reduced minimum inter-onset interval from 0.28s (~214 BPM) to 0.22s (~273 BPM) for breakcore/psytrance compatibility. (FINDINGS.md: I24)
- **qmd.yml glob scope** — Scoped `source` glob pattern to `[!.]*.py` with `__pycache__`/`.venv` exclusions. (FINDINGS.md: I25)
- **Beat tracker shutdown** — Call `self.audio.beat_tracker.release()` in `_quit()` to shut down `ThreadPoolExecutor` gracefully. (FINDINGS.md: I26)
- **Slime mold axis swap** — Corrected trail array shape `(W, H)` → `(H, W)` and all indexing to numpy row-major `[y, x]`. (B1)
- **Corridor infinite surface leak on resize** — `_init_surfs()` now receives `(W, H)` from `draw()`; split semicolon-chained statements. (B2, I13)
- **Aurora division by zero** — Added `max(tot_w, 1e-6)` guard in harmonic weight normalization. (B3)
- **Chromatic stale radius fade** — Re-read `r = ring[2]` after mutation; safe filter-then-iterate ring removal. (B4)
- **Fireworks ember destructure** — All 7 mutable positional fields (x, y, vx, vy, hue, radius, life) explicitly written per frame. (B5)
- **Fireworks sub-pixel zoom drift** — `int(W * zoom)` → `round(W * zoom)` to prevent centering error accumulation. (B6)
- **Lattice aspect-agnostic scaling** — `min(W, H) / 640.0` for glow thickness and shockwave width. (B8)
- **Butterflies trail surface on resize** — Fresh `pygame.Surface(surf.get_size())` on size mismatch instead of stale scaled surface. (B9)
- **Flowfield/clifford stale config refs** — Compare against `surf.get_size()` instead of `config.WIDTH/HEIGHT`. (B10)
- **IS_GL compositing inversion** — Guard in `_render()` skips fade/background/crossfade blits for IS_GL effects; backbuffer shows correctly behind SRCALPHA overlays. (B16)
- **Aurora phases on resize** — `_phases` now rebuilt alongside `_ks` on resize to stay in sync with harmonic structure. (B7)
- **Duplicate SIGINT handling** — Removed outer `try/except KeyboardInterrupt` in `__main__`; signal handler now owns all cleanup. (B11)
- **Preset bg_vis RES_DIV** — `_update_target_res()` re-called after creating new `bg_vis` from preset to account for background render divisor. (B12)
- **Persistence RES_DIV** — Changed `W, H = surf.get_size()` to `self._render_size()[:2]`; draw to private `_scaled` surface at reduced resolution then scale up. (B13)
- **BeatTracking `__import__("sys")`** — Replaced runtime import with top-level `import sys`. (B14)
- **DisplayManager `__import__("sys")`** — Replaced runtime import with top-level `import sys`. (B15)
- **Magnetar np.full → scalar** — Replaced per-frame `np.full(n, val)` allocation with scalar passed directly to `_hsl_batch`. (O5)
- **Aurora wave pre-allocation** — Pre-allocate `self._wave` on init/resize instead of per-ribbon `np.zeros` in the hot draw loop. (O8)
- **Lattice scaled surface cache** — Cache `self._scaled` and reuse via `pygame.transform.scale(dest=...)` to avoid per-frame surface allocation. (O9)
- **AudioEngine _band_bounds guard** — Added `if size else 0` to prevent negative start index on zero-size arrays. (I4)
- **Tunnel unused import** — Removed unused `import numpy` from tunnel.py. (I10)
- **Butterflies dead code** — Removed commented-out antenna drawing code. (I11)
- **Corridor semicolons** — Split `pygame.Surface(…) ; fill(…)` onto separate lines. (I13)
- **Fireworks docstring** — Corrected stale "tunnel with fireworks" description to "fireworks display". (I14)
- **Chromatic shadowed ring** — Renamed loop variable `ring` → `entry` to avoid shadowing the outer loop variable. (I15)
- **Magnetar r3 pre-compute** — Pre-computed `r3 = r2 * r + 0.1` once instead of repeating it in both dipole field components. (I19)
- **Lattice FFT_START_BIN constant** — Extracted magic number `3` into module-level `_FFT_START_BIN`. (M4)
- **Tunnel MAX_TRIS constant** — Extracted magic number `30` into class constant `MAX_TRIS`. (M5)
- **Yantra backward loop comment** — Added comment explaining the reverse ring iteration. (M7)
- **Lissajous symmetry rotation reuse** — Pre-computed `sym_ca_sa` list to avoid redundant `cos`/`sin` calls in the second loop. (M8)
- **Butterflies short FFT guard** — Guarded `fft[:6]` with `min(6, len(fft))` to prevent silent partial-array mean on small FFTs. (M9)
- **_hsl_batch scalar default** — Changed `s=1.0` to `s=np.float32(1.0)` so the default matches the dtype used by array inputs. (I1)
- **_cached_info ClassVar** — Added `from typing import ClassVar` and annotated `_cached_info` as `ClassVar[...]` to accurately reflect its class-level scope. (I2)
- **GLRenderer FBO cache cap** — `offscreen()` now evicts the oldest entry when the cache exceeds 8 FBOs, preventing unbounded growth during resize. (I3)
- **Butterflies pair offset constants** — Extracted magic numbers `0`, `(300,700)`, `(800,1400)` into named class constants `_PAIR_OFFSET_1` etc. (I12)
- **Palette tuple docs** — Added index comment documenting `(target_hue, drift_speed, sat_boost, trail_alpha)` layout. (I20)
- **Genre detection throttle** — `detect_genre()` now called every 60 frames during silence instead of every frame, reducing lock contention during prolonged idle. (I21)
- **PlasmaGL FBO cache** — Handled by the same `gl_renderer.py` cap; `_fbo_cache` delegates to the already-capped `r.offscreen()`. (M10)
- **Seeded RNG in 4 effects** — Added `self._rng` to tunnel, corridor, bubbles, and butterflies; replaced `random.*` calls with `self._rng.*` for full seed reproducibility. (I8)
- **Heartbeat namedtuple** — Replaced positional list-of-7 ring state with a typed `_Ring` NamedTuple. (I16)
- **Mycelium _hsl_batch** — Batched two per-segment `hsl()` calls into one `_hsl_batch` call, reducing Python-level `colorsys.hls_to_rgb` invocations. (M2)
- **SlimeMold _reset_sim → _reset_or_resize_sim** — Renamed to reflect dual purpose (constructor + resize handler). (M3)
- **Lissajous list(zip) → direct array indexing** — Replaced `list(zip(sx_arr.tolist(), sy_arr.tolist()))` with direct array access; hoisted `h_arr` outside the passes loop to avoid redundant hue recomputation. (O3, O4)
- **HUD background to UIManager** — Moved `_hud_bg` surface from `VisualizerApp` to `UIManager.draw_hud_background()` for better encapsulation. (I7)
- **Aurora fade pre-computation** — Pre-computed fade factors per vertex with numpy outside the per-quad inner loop, replacing per-polygon int divisions with vectorized operations. (M6)
- **Chromatic _wave_points numpy vectorization** — Replaced Python per-point for loop with numpy vectorized operations for all 72 points × 3 channels × 14 rings. (O10)
- **Butterflies _Pair.draw() seeded RNG** — Pass `self._rng` through to `_Pair` and `_Butterfly` classes, replacing `random.*` with `self._rng.*` in the hot draw loop and all helper methods. (O11)
- **Nova list(zip) → np.column_stack** — Replaced `list(zip(xs.tolist(), ys.tolist()))` with `np.column_stack([xs, ys])` in per-layer per-sym loop. (O13)
- **Effects __init__ import guard** — Noted that all effects already defer to `__init__`, mitigating the fragility concern. (I5)
- **Resolved stale config refs** — Flowfield, clifford, lattice already migrated to `surf.get_size()` in earlier passes. (I9)
- **Clifford meshgrid hoist** — Hoisted `np.meshgrid` and grid coordinate arrays into `_reset_state()` so they're only rebuilt on resize, saving ~16 MB per frame at 1080p. (O1)
- **Clifford pre-allocated point buffer** — Replaced per-frame `all_x = []`/`all_y = []` + `np.concatenate` with a pre-allocated `(4, N)` array filled in-place. (O2)
- **FlowField pre-allocated arrays** — Pre-allocated `_angles`, `_ir`/`_ig`/`_ib`, and `_colors_arr` to avoid per-frame `np.zeros` and uint32 array allocation in the hot loop. (O6)
- **Lattice beam drawing dedup** — Factored duplicated horizontal/vertical beam drawing into `_draw_beam()` helper, reducing ~40 lines to 8. (I17)
- **Lattice _resize split** — Split `_resize` into `_init_surfaces()` (surface/geometry) and `_build_grid()` (grid nodes/column peaks) for cleaner separation. (I18)
- **Corridor spark struct-of-arrays** — Replaced per-spark dicts with fixed-size numpy arrays (`_sz`, `_shue`, `_sox`, `_soy`) and in-place compaction, eliminating dict allocation overhead for up to 100 sparks. (O12)
- **Tunnel triangle struct-of-arrays** — Replaced per-triangle dicts with fixed-size numpy arrays (`_tz`, `_tpt`, `_trot`, `_trvel`, `_tsize`, `_thue`) and in-place compaction, eliminating dict allocation overhead for up to 30 triangles. (O14)
- **UIManager use target.get_size()** — Replaced `config.WIDTH/HEIGHT` with `target.get_size()` / `target.get_width()` / `target.get_height()` in all cached surface checks and positioning, removing stale-config risk. (I22)
- **GL renderer pre-allocated upload buffer** — Replaced `pygame.image.tostring` (8 MB per-frame bytes allocation) with a pre-allocated numpy buffer filled via `pixels3d`/`pixels_alpha` views, eliminating per-frame 8 MB allocation in the GL blit path. (O15)

## [3.10.0] — 2026-06-22

### Changed
- **Mycelium** — Completely reworked from a single central burst into a multi-colony bioluminescent hyphae network with rotating satellite rings and swirling orbital spore particles.
- **Persistence** — Reworked nested rotating polygons into 3D perspective projection space with depth cueing (depth-based thickness and brightness shading) and non-coplanar axis rotation.
- **Clifford Attractor** — Rewritten to use high-speed 2D density mapping via `np.bincount`, logarithmic density scaling, dynamic percentile framing, and 3D diffuse relief shading from a rotating light source.
- **Butterflies** — Reverted to the stable April 2026 version to remove size variations and swarm forces, restoring classic chase/orbit motion behavior.
- **FlowField** — Shifted initial resolution divisor to start 2 levels faster (`RES_DIV = 3`).
- **Lattice** — Shifted initial resolution divisor up by 1 level to start 1 level faster.
- **TriFlux** — Moderated active rotation speeds, spin acceleration, and damping to reduce jitter and make the effect less nervous.

### Fixed
- **deque keyword crash** — Resolved Python < 3.10 startup crash by using positional arguments for `deque` in `psysualizer.py`.
- **Signal handler AttributeError** — Robustly delayed signal setup during initialization and added safety checks in `_quit()` to avoid referencing undefined display/audio managers.
- **ModernGL resource leak** — Released GPU shader programs and VAOs explicitly on `release()` in `PlasmaGL`.
- **Unbounded cache growth** — Quantized bubble surface cache keys to multiples of 8 and enforced a cache capacity limit of 128 in `Bubbles` to prevent memory leaks.
- **Branches recursion performance** — Optimized recursive tree rendering to scale depth based on `config.LOW_SPEC` settings.
- **Pygame image deprecation** — Updated `pygame.image.tostring` calls to `to_string` in `GLRenderer` to align with modern Pygame-ce.
- **Regression tester path** — Resolved relative path glob check to use absolute paths based on `__file__`.
- **UI Font scale on resize** — Added dynamic recalculation of font sizes in `UIManager` when window size/resolution updates.
- **Audio Callback Blocksize Jitter** — Padded/sliced incoming audio chunks in `AudioEngine` to prevent failures when the host ALSA/PortAudio block size varies.
- **Division-by-zero check** — Added safety checks in `Butterflies` boundary repulsion to prevent crashes on tiny viewport sizing.

## [3.9.0] — 2026-06-19

### Changed
- **Lightweight Dependencies** — Moved `librosa` from core requirements to an optional setup step. The visualizer and beat engine remain fully functional without it, drastically reducing installation size and time.
- **HUD Readability** — Added a semi-transparent black background panel behind both the top-left HUD text lines and the bottom-left multiband energy bars to guarantee visibility during dark, bright, or chaotic visuals.
- **Silence & No-input Modulations** — Added dynamic LFO (low-frequency oscillator) and soft heartbeat BPM-based modulation to visual idle parameters (beat decay, mid/treble floors) when silent or when no audio device is active, keeping visuals organic and avoiding static freezing.
- **Performance `--low-spec` Mode** — Added a `--low-spec` command-line argument that caps frame rate at 30 FPS and dynamically halves the active particle, slime agent, neural node/signal, and Mycelium growth budgets in performance-heavy effects (FlowField, SlimeMold, Vortex, Bubbles, Mycelium, Synapse).
- **Cleanup** — Purged `BUGS.md`, empty `results/` folder, and cleared tasks in `TODO.md`.
- **Bootstrap and hardening** — restored a runnable entrypoint, split the CPU/ModernGL architecture, added smoke tests and benchmarks, modularized the app, tightened dependencies, improved beat/BPM detection, hardened startup validation, and added regression checks for the registry contract.
- **Audio startup fallback** — startup now prefers concrete input devices (such as PipeWire/Pulse or explicit hardware devices) before falling back to Linux's generic `default` wrapper, reducing ALSA startup failures that aborted the app before the UI appeared.
- **Silence handling** — added RMS/FFT-based silence detection with hysteresis so effects drop to a deliberate low-motion idle level before and after tracks instead of reacting to noise-floor normalization spikes.
- **Crossfade scaling** — cached the scaled previous-frame surface used during mode crossfades, reducing repeated full-frame scaling work on every transition frame.
- **Aurora** — ribbon fills now use successive strip quads instead of one large polygon per band, avoiding the straight-line/spoke artifacts seen on Android.
- **FlowField** — removed the edge recycling behavior so particles wrap across the full viewport and no longer leave a dark border.
- **Butterflies** — trail scaling now uses a cached resize-aware surface instead of allocating a fresh full-screen scale result every frame.
- **Butterflies** — wing polygons are now pre-rendered into cached flap frames and rotated from those masks instead of being recomputed every draw.
- **Butterflies** — boundary handling now bounces butterflies back into view instead of pinning them to the edge.
- **Butterflies** — added light cohesion/separation steering so the swarm does not collapse into a single clump.
- **Butterflies** — centralized the main motion and lifecycle tuning values into named constants for easier adjustment.
- **HiDPI scaling** — internal render divisors now adapt to the actual display size so low-res effects stay sharper on large screens without forcing every frame to full resolution.
- **Magnetar** — particle density now scales higher on large displays, and the internal particle state is rebuilt on resize so the field stays full after geometry changes.
- **SlimeMold** — increased internal simulation fidelity, added resize-safe buffer rebuilds, and smooth-scaled the output to reduce chunky Android pixels.
- **Clifford** — replaced unconstrained random parameter jumps with curated presets plus dynamic framing and collapse recovery, keeping the attractor broad instead of collapsing into a tiny bright point.
- **Möbius** — removed the interior longitude wires so the band reads cleanly without straight cross-lines.
- **Chromatic** — replaced concentric RGB circles with wavy chromatic raindrop ripples that read properly on Android.
- **Mycelium** — reworked from a single center-out burst into a multi-colony swirling growth pattern for fuller, more psychedelic screen coverage.
- **Synapse** — bounded live pulse count and cascade fan-out to prevent runaway signal growth and improve stability on lower-power platforms.
- **Vortex** — feedback upscaling now reuses a cached surface and recreates internal buffers cleanly on resize.
- **Vortex** — the feedback zoom/rotation step now runs through a GL shader when ModernGL is available instead of relying solely on CPU rotozoom.
- **Vortex** — centralized the main launch, trail, feedback, and culling tuning values into named constants.
- **Random updates** — batched several scalar random paths into `numpy` vector draws and added optional `PSYSUALS_SEED` session seeding for reproducible runs.
- **Audio reactivity** — fixed FFT band handling so genre and energy calculations clamp safely to the available spectrum instead of assuming fixed bin ranges.
- **Import hygiene** — removed unused imports, lazy-loaded optional `librosa`, and deferred GL helper imports until the `--gl` path is actually used.
- **ModernGL lifecycle** — offscreen FBOs are now cached and released by `GLRenderer`, and the GL effects clear their local framebuffer caches on shutdown.
- **NVIDIA OpenGL compatibility** — requested a 3.3 core context explicitly and standardized shader versions so `--gl` works on stricter desktop drivers.
- **FlowField** — scaled particle count by screen area to keep large displays visually dense without overloading smaller ones.
- **Tunnel / Corridor** — lowered the near recycling threshold so geometry clears the screen before being recycled, removing boundary pops and gaps.

### Removed
- **Droste** — removed from the active mode registry.
- **OilSlick** — removed from the active mode registry.
- **Coral** — removed from the active mode registry.

### Documented
- **Mode count** — updated current docs to match the active 27-mode registry.

## [3.8.0] — 2026-06-15

### Added
- **Mycelium** — spreading fungal hyphal network; tips grow outward leaving decaying filament segments; beat fires a central bloom burst.
- **Magnetar** — ~6 000 particles riding an analytical rotating magnetic dipole field; beat fires an equatorial shockwave.
- **SlimeMold** — Physarum-style multi-agent trail simulation; self-organising vein network that pulses and reforms with the music.
- **Droste** — infinite self-similar recursive zoom portal with spiralling geometry fed into the feedback loop.
- **Clifford** — 40 000-walker strange attractor with audio-morphing parameters (a, b, c, d); beat jumps to a new attractor shape.
- **Möbius** — perspective-projected 3-D Möbius strip wireframe with twist shiver on beat.
- **Chromatic** — expanding ring waves that split red, green, and blue channels outward, creating prismatic aberration halos.
- **Persistence** — nested counter-rotating polygons with very long trail persistence for wagon-wheel moiré accumulation.
- **OilSlick** — full-screen thin-film interference pattern; two wave families mapped continuously to hue across the pixel grid.
- **Synapse** — neural graph with ~55 nodes and visible signal pulses cascading along edges on beat.
- **Coral** — bioluminescent fractal coral growing upward from the bottom edge with gravitropic branching and bloom pulse.
- **Heartbeat** — concentric pressure waves morphing between circle and polygon depending on bass intensity.

---

## [3.7.0] — 2026-06-15

### Changed
- **Lissajous — calmer motion** — reduced mid influence on rotation velocity (`mid * 0.0015` → `0.0007`, `mid * 0.0020` → `0.0010`), tightened damping (`0.97` → `0.94`), and halved shape-distortion coefficients from mid/treble (`mid * 0.35` → `0.18`, `high * 0.40` → `0.22`, `high * 0.0004` → `0.0002`).
- **Tunnel — fewer triangles, lower speed** — reduced base travel speed (`0.03` → `0.022`), cut mid speed contribution (`0.06` → `0.04`), lowered triangle spawn rate (bass factor `2.0` → `1.2`, mid factor `3.0` → `1.5`, threshold `0.4` → `0.5`), and capped live triangles at 30 (was 50).
- **FlowField — edge recycling into cloud** — replaced random recycling with edge-proximity detection: particles within 8% of any screen edge are relocated to a random position within the central 60% of the screen, preventing edge clustering.
- **Branches — less reactive to mid/treble** — reduced mid jitter coefficients (`0.80` → `0.45`, `0.40` → `0.22`), treble jitter (`0.35` → `0.18`), spread (`mid * 0.55` → `0.28`, `high * 0.20` → `0.10`), ratio (`high * 0.12` → `0.06`), and extra arms from treble (`high * 1.8` → `0.8`).
- **Lattice — center-out frequency mapping** — changed column-to-frequency assignment from monotonic left→right to center-out: center columns respond to bass, edge columns respond to treble, creating a symmetric bloom on beats.
- **Lattice — dynamic grid density** — grid size now scales with display resolution: 14×9 on ≤1440p, 18×12 on 1440p–4K, 22×14 on 4K+. Column peak-normalization resets automatically on resize.

---

## [3.6.0] — 2026-06-15

### Added
- **Butterflies — two butterfly sizes** — each session now contains a mix of large (scale 5.04/4.79) and small (scale 2.52/2.39, half size) butterfly pairs in a big/small/big rotation. Total pair count unchanged (3 pairs, 6 butterflies).

### Fixed
- **BUG-023 (butterflies.py)** — mutual butterfly orbit/chase was completely non-functional: `solo_target`/`love_target` were being passed as the unused `t` parameter of `_Butterfly.update` instead of `chase_pos`, so butterflies always wandered. Orbit/chase now activates correctly.
- **Butterflies — smaller initial orbit radius** — reduced `_orbit_r` from 240 → 120 on pair creation, preventing overly wide initial sweeps when orbit first activates.
- **Butterflies — per-pair sync range** — wing-phase sync distance now scales with each pair's actual butterfly size instead of the hardcoded big-butterfly constant.

---

## [3.5.2] — 2026-06-11

### Fixed
- **BUG-017 (auto-gain)** — seed `rms_buf` with `target_rms` to prevent 2.0× spike on silence
- **BUG-018 (crossfade)** — set alpha to 0 before clearing previous frame, preventing remnant
- **BUG-019 (settings)** — atomic writes via `write-to-temp + os.replace()` in all 3 write paths
- **BUG-020 (audio callback)** — `try/except` around entire callback body prevents silent stream death
- **BUG-021 (GL blend modes)** — `BLEND_RGBA_MAX` → `BLEND_RGB_MAX` in 5 effects, `BLEND_ADD` → `BLEND_RGB_ADD` in aurora
- **BUG-022 (benchmarks)** — reset `tick = 0` per test to eliminate CPU/GL phase noise

## [3.5.1] — 2026-06-11

### Documented
- **BUG-018 (crossfade)** — off-by-one prevents full transparency on last frame
- **BUG-019 (settings)** — non-atomic writes silently reset user config on crash
- **BUG-020 (audio callback)** — unhandled exceptions silently kill audio stream
- **BUG-021 (GL blend modes)** — `BLEND_RGBA_MAX`/`BLEND_ADD` corrupt alpha on SRCALPHA targets
- **BUG-022 (benchmarks)** — shared tick counter introduces phase noise between CPU/GL tests

## [3.5.0] — 2026-06-11

### Fixed
- **BUG-016 (vortex.py)** — ember life off-by-one: decrement now applied to local variable before boundary check, preventing one extra frame of life and incorrect brightness on the last frame
- **BUG-017 (psysualizer.py)** — auto-gain spike on silence: documented (no code fix)

## [3.4.0] — 2026-06-03

### Changed
- **Creative Multi-band Audio Reactivity** — upgraded all 18 visualizer effects to use the new normalized audio engine parameters (`beat`, `config.MID_ENERGY`, and `config.TREBLE_ENERGY`) creatively and distinctively instead of relying on raw frequency splits. 

## [3.3.0] — 2026-06-03

### Added
- **Lattice Grid Sizing Adaption** — implemented dynamic canvas resolution divisor (`RES_DIV`) scaling (rendering at full resolution on 1440p/4K TV screens, half resolution on FHD, and 1/3 on laptops) and corrected the shockwave speed expansion formula to scale consistently with screen dimensions.
- **Lattice Column Normalization & Noise Gate** — introduced a dynamic peak-normalization tracking algorithm and noise gate filter to ensure balanced brightness and activity across all columns.
- **HUD Proportional Scaling** — scaled HUD font sizes dynamically based on screen width (from 20px on default windows up to 48px on 4K TV screens) and computed y-offsets dynamically to avoid overlap.

### Changed
- **Lattice Trail Rework** — removed the vortex/twisting rotation from the rotozoom feedback loop, making grid trails zoom straight back.
- **Lattice Column Cut-out** — completely cut out and hid the leftmost column (column 0) by positioning it off-screen and skipping its rendering.
- **Aurora Ribbons** — restored the original constant-thickness ribbon bands, reverting the edge-tapering fade logic.
- **FlowField Particles** — boosted particle count (baseline 12,000, max 50,000) and optimized rendering with `pygame.surfarray` vectorised assignment for an 18x speedup.
- **Butterflies Spawning** — reduced spawning offset, partner-joining, and death respawn delays for a much faster pace.

### Fixed
- **Pygame Dependency** — replaced `pygame` with `pygame-ce` in `requirements.txt` to provide precompiled wheels on Python 3.14, resolving installation and compilation errors.
- **FlowField Boundaries** — clipped particle coordinate arrays in `FlowField` to prevent out-of-bounds `IndexError` crashes.
- **VisualizerApp GL Initialization** — resolved an `AttributeError` by initializing `fade_alpha` before calling `_update_target_res` in OpenGL mode.

## [3.2.0] — 2026-06-03

### Changed
- **Aurora Effect** — tapered ribbon boundaries and outer glows to zero at screen edges, eliminating straight vertical line cut-offs for a more organic look.
- **Lattice Effect** — added resolution-aware, multi-layered soft glows to nodes and beams, dynamically scaling their sizes and properties for large/4K displays.
- **Lattice Dynamic Resizing** — implemented real-time render surface and node layout adjustments during screen/window size changes.
- **Lattice Robustness** — added safe FFT slicing to prevent index/range errors.

## [3.1.0] — 2026-04-21

### Added
- **Reworked Lattice** — complete visual overhaul with a dynamic rotozoom feedback tunnel
- **Installation Guide** — added step-by-step setup and hardware acceleration instructions to README

### Changed
- **Massive FPS Boost** — optimized Butterflies, Vortex, and FlowField (now handling 8,000 particles smoothly)
- **Smoother Lissajous** — dampened rotation and beat response for a more stable visual flow
- **Improved Exit Responsiveness** — app now terminates instantly and reliably with a single `Esc` or `Q` keypress

### Fixed
- **Plasma GL** — fixed blank screen issue by ensuring the GL backbuffer is cleared correctly each frame
- **Dependency Import** — resolved `NameError: name 'pygame' is not defined` in `GLRenderer`
- **AudioEngine Bug** — restored the missing `get_audio` method to resolve application startup crashes

### Added
- **Super-Duper Massive Release** — Psysuals v3 is here!
- **Modular OO Architecture** — complete refactor of `psysualizer.py` into specialized classes (`AudioEngine`, `DisplayManager`, `UIManager`, `VisualizerApp`)
- **Performance Benchmarks** — added `benchmarks.py` to measure and compare effect frame rates (CPU vs GPU)
- **Regression Testing** — integrated `core/regression_tester.py` into `smoke_test.py` to guard effect contract and registry order

### Changed
- **Enhanced Beat Detection** — improved spectral flux normalization and constrained BPM range for higher stability
- **Hardened Validation** — robust display geometry and audio device validation with safe fallback paths
- **Relaxed Dependencies** — updated `requirements.txt` to use minimum version ranges instead of fixed pins

### Fixed
- **Improved Process Termination** — app and all span-mode children now exit immediately and reliably on `Esc` or `Q`
- **Saved Display Selection** — `display_idx` is now restored from settings on startup unless `--display N` overrides it

---

## [2.16.0] — 2026-04-21

### Added
- **Unified ModernGL architecture** — `psysualizer.py` now supports both CPU (Pygame) and GPU (ModernGL) rendering via the `--gl` flag
- **GL/UI Compositing** — UI elements (HUD, pane, picker) are now rendered as textures and blitted over OpenGL content in GL mode
- **`smoke_test.py`** — automated instantiation and import check for all 18 effects
- **`requirements-gl.txt`** — optional GL dependency set for the ModernGL path

### Changed
- **Effect Contract** — all effects now inherit from a base `Effect` class and accept an optional `renderer`
- **Span mode** — the primary instance now spawns one child process for every non-primary monitor
- **GL shader assets** — `gl_renderer.py` now owns loading the tracked GLSL files under `effects/shaders/`
- **Plasma** — now uses `PlasmaGL` by default, providing hardware acceleration with a transparent CPU fallback

### Fixed
- **No-input startup and device switching** — failure to open the saved or default input device now degrades to a live silent mode instead of aborting the app
- **Background effect rebuild on display changes** — fullscreen toggles now recreate the background visualiser as well as the foreground one
- **Span-child recursion** — child windows launched by span mode no longer recursively enter span mode themselves

### Removed
- **`psysualizer_gl.py`** — redundant experimental entry point removed in favour of the unified `--gl` flag
- **Dead Particles module** — removed the unregistered `effects/rhythmic_particles.py` file

---

## [2.15.0] — 2026-04-21
...
