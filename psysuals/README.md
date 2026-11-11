# psysuals — The Ultimate Psychedelic Experience

**Welcome to the super-duper greatest music visualizer ever made.** psysuals delivers uncompromising visual intensity and rock-solid performance. Whether you're blasting psytrance in a dark room or driving a multi-monitor stage setup, it is built to melt your mind with precision and style.

![Version](https://img.shields.io/badge/version-3.20.0-orange)
 ![Python](https://img.shields.io/badge/python-3.10%2B-blue) ![License](https://img.shields.io/badge/license-MIT-green)

---

## Version 3.20.0

This release fixes CPU-effect visibility in GL mode, makes mode changes start
with empty framebuffers, preserves simulation state through supported resizes,
and adds independent Laptop/TV response profiles. Cube, Tunnel, Plasma and
Phason are calmer; Persistence responds through smooth direction, speed and
colour changes. Hyperbolic has individually moving 3-D facets.

Mandelbox and Morphogenesis are removed. There are now 33 modes and four layered
recipes. Because saved presets store numeric mode indices, reselect/re-save
presets for effects after Heartbeat when upgrading. No dependency or file-format
migration is required.

## New mode: ChromaWeave

A colourful eightfold kaleidoscope of woven neon ribbons, iridescent petals and
fine cyan sparkles. Bass opens and brightens the weave, mids move interference
bands, and treble lights the crossings. It keeps gently evolving in silence.
Select it with `--mode 30` or the arrow keys. Desktop GL renders the field at
full output size; a NumPy CPU fallback uses a bounded sampling grid and smooth
scaling so machines without GL can still use the mode.

ChromaWeave sits before the final Spectrum/Waterfall pair. Their zero-based
indices move from 30/31 to 31/32; reselect/re-save numeric presets for those two
modes. All earlier indices stay in place. The native Android counterpart is
prepared separately and awaits the combined GitHub/device verification batch.

## Maintenance pass

The desktop app delegates CLI parsing, input, audio response, transitions and
frame composition to small modules. Recipes select and restore their named
background effects; Shift+B still cycles the first nine backgrounds. Malformed
saved post-processing selections fall back to off. Settings/preset formats,
mode indices, effect APIs and dependency versions are unchanged.

Bubbles keeps a 32-entry surface cache. GL allocation failures retain previous
resources; post-processing reacquires evicted framebuffer targets. Startup
failures release acquired resources without saving incomplete settings.

See [ARCHITECTURE.md](ARCHITECTURE.md) for ownership/extension contracts and
[TODO.md](TODO.md) for completed tasks and validation.

## Explicit context migration

config.py has been removed. Hosts construct effects with EffectOptions and pass
immutable FrameContext values to render(surface, frame). AudioEngine takes an
AudioConfig; dimensions and quality decisions belong to their explicit owners.
Importing modules no longer seeds global Python/NumPy randomness.

The draw(...) and draw_frame(...) signatures remain as instance-owned adapters.
External Python hosts must update effect construction: global dimensions and
an initialization flag are no longer supported. AndroSaver uses a native
Kotlin/GLES backport and does not call these Python constructors. That migration
preserved saved presets and registry order; the later ChromaWeave insertion is
described above. The migration passed 190 unit tests and all 32 then-registered
smoke effects. All 32 CPU effects match saved pre-migration pixels exactly;
seven AMD EGL effects differ by at most one 8-bit channel step. See the host example below and
[ARCHITECTURE.md](ARCHITECTURE.md) for the new extension contract.

## Laptop and TV profiles

Use `--visual-profile laptop` for a close laptop display or `--visual-profile tv`
for viewing from a distance. `V` cycles Auto → Laptop → TV during playback; the
selection is saved on exit. Auto uses output width as a fallback (TV from 2560
pixels), so choose a profile explicitly for a 4K laptop or 1080p TV.

Laptop keeps camera motion gentle and uses a 22×14 Lattice grid. TV uses a 14×9
grid and stronger light accents. Both retain slow geometry transitions. The fourteen
retuned effects (Cube, TriFlux, Lissajous, Tunnel, Plasma, Lattice, Mobius,
Chromatic, Persistence, Synapse, Hyperbolic, Cymatica, Phason and Ferrofluid)
use elapsed time for motion rather than render-frame counts. External hosts supply FrameContext.dt in seconds per frame; its default is
1/60 and consumed motion steps remain capped at 0.1 seconds.

GL and CPU effects receive a full display-sized canvas. Reduced-resolution
simulations scale their completed image once; lowering quality preserves the
viewport, centre and aspect ratio. TriFlux triangle outlines are antialiased,
including enlarged foreground triangles. Mode changes clear the CPU/GPU frame
and presentation buffers immediately; previous pictures are not crossfaded.
Effects with their own feedback replace the composed frame instead of retaining
old maximum colours. Fullscreen/quality resizing preserves existing particle,
network, dye and trail state where supported.

SlimeMold keeps at least 85% of native width/height on TV-sized outputs even at
lower quality tiers. Phason, Ferrofluid and LiquidLight reduce internal detail
gradually rather than immediately dropping to half size.

## 🚀 Highlights

- **✨ Unified ModernGL Engine** — Harness the power of your GPU with optional hardware acceleration. Run with `--gl` for per-pixel shader fluid motion.
- **🏗️ Modular Object-Oriented Architecture** — Re-written from a monolithic script into a sleek, professional engine. Specialized `AudioEngine`, `DisplayManager`, and `UIManager` classes ensure a clean, maintainable, and high-performance foundation.
- **🖥️ Ultimate Multi-Monitor "Span Mode"** — Gone are the days of fixed dual-screen limits. The app scales dynamically, spawning child processes for every monitor you own, with synchronized mode switching across the entire span.
- **🔊 Resilient Audio Pipeline** — Tolerant audio capture with a no-input fallback, silence-aware idle motion, live device switching, and spectral-flux beat detection.
- **⚖️ Built-in Benchmarking & Regression Checks** — Measure speed with `benchmarks.py`; the smoke test validates registry order and instantiates all 33 registered effects, with focused tests for audio timing, display lifecycle, and effect safety bounds.

---

## ⚙️ Installation

### 1. Requirements
- Python 3.10+
- Optional audio input device (microphone or loopback monitor); the app can run in silent mode without one
- (Optional) OpenGL 3.3+ capable GPU for hardware acceleration

### 2. Setup
Clone the repository and create a virtual environment:
```bash
git clone https://github.com/Whichcraft/psysuals.git
cd psysuals
python3 -m venv .venv
source .venv/bin/activate
.venv/bin/python -m pip install -r requirements.txt
```

### Ubuntu/Debian: `externally-managed-environment`

Recent Ubuntu and Debian releases protect the system Python (PEP 668), so a
direct `pip install -r requirements-gl.txt` may fail with
`error: externally-managed-environment`. Install the venv support package and
install project dependencies into the repository's virtual environment instead:

```bash
sudo apt update
sudo apt install python3-full
python3 -m venv .venv
.venv/bin/python -m pip install --upgrade pip
.venv/bin/python -m pip install -r requirements.txt
.venv/bin/python -m pip install -r requirements-gl.txt
```

Use the same `.venv/bin/python` for the run and test commands below. The
`--break-system-packages` pip override is intentionally not recommended: it
modifies the OS-managed Python and can break distribution packages.

### 3. Advanced Beat Tracking (Optional)
To enable high-accuracy Librosa-based beat tracking and BPM estimation:
```bash
.venv/bin/python -m pip install librosa
```

### 4. Hardware Acceleration (Optional)
To enable the high-performance ModernGL path:
```bash
.venv/bin/python -m pip install -r requirements-gl.txt
```

### Python headless API and AndroSaver

AndroSaver vendors psysuals as a reference subtree and backports applicable
changes into native Kotlin/GLES. Its GitHub builder compiles that native app;
it does not run Python, Chaquopy or this rendering API. Native changes and
subtree imports are separate, explicitly reviewed work in AndroSaver.

For external hosts that do embed Python, this repository supplies a headless
rendering API. CPU
effects inherit `Effect.draw_frame(width, height, waveform, fft, beat, tick,
renderer=None)`, which returns an RGBA array sized to the requested physical
framebuffer. The result is a fresh `numpy.uint8` array with shape
`(height, width, 4)`. `PlasmaGL` supplies its own ModernGL offscreen
implementation and requires the host's `GLRenderer` as `renderer`.

Initialize Pygame and pass construction options explicitly:

    import pygame
    from core.context import EffectOptions, FrameContext
    from effects.spectrum import Bars

    pygame.init()
    effect = Bars(options=EffectOptions(640, 360, seed=7))
    frame = FrameContext(640, 360, profile="laptop", mid=.4, treble=.6)
    effect.set_frame_context(frame)
    rgba = effect.draw_frame(640, 360, frame.waveform, frame.fft, .7, 0)
    effect.release()
    pygame.quit()

New hosts can call effect.render(surface, frame) directly. Reuse the same effect
across frames. Bind supplemental timing/bands/profile with set_frame_context(frame)
for legacy adapters. GPU frame rendering needs the host's GLRenderer: Plasma
requires it, while Phason/Ferrofluid retain CPU fallback. Framebuffer dimensions
are local to the effect and restored after draw_frame(), including failures.

---

## Usage

```bash
.venv/bin/python psysualizer.py
```

Run with ModernGL hardware acceleration:
```bash
.venv/bin/python psysualizer.py --gl
```

Run on low-spec/low-power systems (caps framerate to 30 FPS and scales down simulation complexity):
```bash
.venv/bin/python psysualizer.py --low-spec
```

The app restores the last saved display index on startup. Use `--display N` to
override that for a single launch. Use `--mode N` to select the starting effect
by its zero-based index from the 33-effect registry (`0`–`32`).

### Controls

| Key / Action | Effect |
|---|---|
| `←` / `→` or `Space` or click | Cycle to previous / next mode (or adjust pane slider when pane is open) |
| `↑` / `↓` | Increase / decrease effect intensity (or adjust FlowField particles by 2,000, or navigate pane sliders when pane is open) |
| `1` – `9` | Jump to modes 1–9 |
| `Tab` | Toggle real-time settings pane (effect gain, bg alpha, parameter-transition length) |
| `P` | Save current state as a preset |
| `Shift+P` | Morph to the next saved preset over eight beats (or a short time fallback) |
| `A` | Toggle auto-gain (auto-scales beat to current volume) · in span mode: cycle the shared secondary-display effect backward |
| `B` | Toggle background layer (renders a second effect at configurable opacity behind the active one) |
| `Shift+B` | Cycle background effect (modes 1–9) |
| `Shift+R` | Cycle curated foreground/background recipes |
| `M` | Tap tempo — tap 2+ times to lock BPM for 8 s |
| `Shift+M` | Toggle span mode — single monitor: one effect NOFRAME full-screen; multi-monitor: one child process per other display |
| `D` | Open device picker (↑↓ navigate, Enter confirm, Esc cancel) · in span mode: cycle the shared secondary-display effect forward |
| `F` | Toggle fullscreen (effects are rebuilt for the new display dimensions) |
| `H` | Toggle HUD on / off |
| `Shift+H` | Cycle HUD detail: full → minimal → off |
| `Q` / `Esc` | Quit |

Changing modes with `←` / `→`, `Space`, number keys, or mouse click resets intensity to the default `0.7`. Saved presets can still restore a custom intensity deliberately.

## Selecting an audio input device

Press `D` while running to open the interactive device picker. Use `↑`/`↓` to navigate the list of available input devices, `Enter` to switch, `Esc` to cancel. The active device is shown in the HUD at the top of the screen.

If no input device is available at startup, psysuals stays open in silent mode instead of crashing. The HUD shows `no input` until a device is selected successfully.

Before and after tracks, psysuals uses RMS/FFT silence detection with hysteresis. It enters silence after about 140 ms of quiet audio and exits after two fresh loud audio blocks (about 46 ms at the default 44.1 kHz / 1024-sample callback). Repeated video frames do not accelerate these timings, and invalid audio is ignored. During true silence, beat spikes are suppressed and effects fall back to a faint low-motion idle state instead of freezing completely or reacting to noise-floor normalization.

Effects receive FrameContext.phase, a normalized position from `0.0` at
the predicted beat boundary to `1.0` just before the next one. It falls back to
a slow idle phase when BPM timing is unavailable.

## Testing and benchmarks

Run the headless smoke and registry checks with:

```bash
SDL_VIDEODRIVER=dummy SDL_AUDIODRIVER=dummy .venv/bin/python smoke_test.py
```

Run the automated unit tests headlessly with:

```bash
SDL_VIDEODRIVER=dummy SDL_AUDIODRIVER=dummy .venv/bin/python -m unittest discover -s tests -v
```

The suite includes compact deterministic visual fixtures for every registered
effect. They compare quantized coverage and color-energy metrics rather than
large screenshots, so black frames and major visual regressions are detected
without committing binary artifacts.

At runtime, a rolling frame-time governor can move effects among high,
balanced, and low internal-resolution tiers. It uses a 90th-percentile window,
hysteresis, and a cooldown; benchmark commands are unaffected because they do
not run the visualizer loop.

Audio and graphics boundary tests use fakes when hardware or optional native dependencies are unavailable; the real Pygame resize test runs when Pygame is installed.

For actual GPU validation, run `.venv/bin/python psysualizer.py --gl` with the
real display and audio session; `SDL_VIDEODRIVER=dummy` cannot exercise OpenGL.
Version 3.20.0 was also checked by reading visible AMD/Mesa GL framebuffers for
the affected effects at 960×540, checking clean GPU buffers on mode switches,
and rendering SlimeMold into a 3840×2160 framebuffer. These checks complement
the unit suite; they do not measure every TV or music input.

The earlier maintenance pass verified 180 unit tests and all 32 smoke effects, plus
reversed test order with config/palette restoration. Five effects matched the
previous rendering pixel-for-pixel on CPU and real AMD EGL framebuffers
(30 frames each). Butterflies matched 1,200 baseline frames with two resizes;
Mycelium matched 100 baseline frames. These are scoped comparisons, not a claim
that every shader, resolution or device has been verified. Desktop fullscreen/
mode-switch presentation, live audio-device switching, multi-monitor operation
and the external Android host remain pending for this maintenance pass.

Run a quick headless CPU performance comparison with:

```bash
SDL_VIDEODRIVER=dummy SDL_AUDIODRIVER=dummy .venv/bin/python benchmarks.py --cpu-only --duration 2
```

To attempt the CPU/ModernGL comparison, use a real display/context (do not set the dummy video driver):

```bash
.venv/bin/python benchmarks.py --gl --duration 2
```

The benchmark reports when ModernGL is unavailable or its context cannot be
created; CPU measurements still complete. Audio fixtures use a local seeded RNG
without advancing global random state; durations must be finite and positive.
Effects/renderers are released on benchmark failure. Standalone smoke/regression
runners use explicit render dimensions and restore SDL driver selection on exit.

`Shift+R` cycles curated pairings such as Hyperbolic + LiquidLight,
Tesseract + Persistence, Cymatica + Ferrofluid, and
Fireworks + Aurora. Choosing a recipe explicitly enables its background; the
individual foreground/background controls remain available afterward.

`Shift+X` cycles optional post-processing: off, chromatic separation,
kaleidoscope mirroring, feedback rotation, and diffraction-style bloom. The
default is off; each pass reuses its working surfaces and remains bounded on
the CPU path.

Modes with a shared declared morph parameter interpolate it during a
transition. Tesseract and Persistence currently share their projection
parameter. Image buffers are cleared on every mode switch; numeric parameter
interpolation does not retain the previous effect image.
GL results include the display-present step (and context synchronization when available), so compare them on the same display and VSync settings.

Use `--display N` for monitor selection. When `xrandr` geometry is unavailable, the app falls back to SDL's detected display count and asks SDL to target the selected display.

## Project structure

```
psysuals/
├── psysualizer.py            # Entry point — clean orchestrator
├── core/
│   ├── context.py            # Frozen audio/options/frame inputs
│   ├── cli.py                # Parser with explicit argv
│   ├── input_controller.py   # Modal event dispatch
│   ├── audio_engine.py       # Capture, FFT and coherent audio snapshots
│   ├── silence.py            # Audio-block hysteresis
│   ├── audio_response.py     # Beat decay, gain, phase and idle response
│   ├── transitions.py        # Tick-based preset/parameter interpolation
│   ├── frame_composer.py     # Surface ownership, layers, GL presentation
│   ├── display_manager.py    # Monitors, X11, windowing, span mode
│   ├── ui_manager.py         # HUD, pane, picker rendering
│   ├── postprocess.py         # Optional bounded psychedelic post-processing
│   ├── quality.py             # Adaptive frame-time quality governor
│   └── regression_tester.py   # Headless effect contract checks
├── beat_tracking.py          # Optional librosa-based BPM/beat refinement
├── settings.py               # User settings persistence
├── gl_renderer.py            # Shared moderngl renderer utilities
├── requirements-gl.txt       # Optional moderngl dependency set
├── smoke_test.py             # Headless effect instantiation and registry checks
├── benchmarks.py             # Performance measurement tool
├── effects/
│   ├── __init__.py           # MODES list and package re-exports
│   ├── base.py               # Effect base class and contract
│   ├── utils.py              # Shared colour helpers
│   ├── palette.py            # Shared colour palette
│   └── *.py                   # 33 registered visual implementations and helpers
├── ARCHITECTURE.md           # Code structure and extension guide
├── EFFECTS.md                # Full parameter reference for all effects
├── tests/                    # Automated settings, audio, graphics, and cleanup tests
├── CHANGELOG.md              # Release and unreleased change history
├── TODO.md                   # Audit status and outstanding maintenance items
├── requirements.txt
└── README.md
```

## License

MIT

### Reproducing context and rendering checks

Run these permanent checks from the repository root with the existing venv:

```bash
SDL_VIDEODRIVER=dummy SDL_AUDIODRIVER=dummy PYTHONDONTWRITEBYTECODE=1 .venv/bin/python -m unittest tests.test_context tests.test_visual_regression -v
```

The context tests exercise independent instances and the drawing adapters without
external fixtures. `EGLAdapterTests` creates two real EGL OpenGL 3.3 contexts and
checks Plasma, Phason and Ferrofluid across two output sizes, including independent
RGBA results and renderer replacement. Missing ModernGL/EGL reports an explicit
skip; rendering failures after context creation fail the test. This is offscreen
GPU validation, not an interactive desktop or Android integration test.

The permanent CPU visual test uses its existing 160×120 seeded input sequence and
compact metrics in `tests/test_visual_regression.py`; these are broad regression
bounds, not exact pixel equality. Baselines and tolerances were not regenerated.
The earlier exact pre-context CPU/seven-effect EGL comparison was a one-off
experiment using temporary reference sources. That reference is not a versioned
artifact, so a fresh checkout cannot reproduce that historical comparison.
Comparing against HEAD would also include earlier maintenance changes and would
not isolate the context migration. Do not use that experiment as a permanent
acceptance gate or treat its one-channel GPU difference as a portable tolerance.


### Follow-up verification (R01–R05)

Public context inputs now reject invalid sizes, timing, quality and array data
at construction. Arrays are defensive read-only copies for cooperative callers,
not tamper-proof memory. Rendering failure restores frame bindings, not pixels,
RNG or simulation state. See ARCHITECTURE.md for exact outcomes and recovery.

Measure context-boundary overhead without rendering or SDL initialization:

```bash
PYTHONDONTWRITEBYTECODE=1 .venv/bin/python benchmarks.py --context-only --samples 5000
```

On the recorded Python 3.14/NumPy 2.4 Linux environment, default-size metadata
replacement had a 17.1 us median and 30.7 us p95. These are scoped construction/
validation/copy measurements, not a whole-app FPS claim. Defensive copies remain.

Real X11 CPU/AMD GL checks exercised startup, fullscreen transitions, mode and
layer changes, direct-GL overlay clearing and cleanup with temporary settings.
CPU resized between 1920×1200 and 960×540. On this host GL stayed at 1920×1200
after a requested window resize; smaller interactive GL window sizing remains
unresolved. Offscreen EGL resize checks do not close that desktop limitation.
Live audio switched between two input routes with advancing callback generations,
continued rendering, failed-switch retention and closed streams on shutdown;
no PCM was saved. Only one physical monitor was available, so physical span
operation remains unverified.

AndroSaver's current native app/build inputs match its verified app commit
18661a1; [GitHub run 40414215709](https://github.com/Whichcraft/androsaver/actions/runs/40414215709)
passed native verification, API 29/35 device integration and APK build. This
is existing CI evidence for unchanged native code, not a new Android run of
these Python changes or proof on every physical TV. No constructor adoption is
needed for that native app; its reference subtree has not been synchronized by
this work. TODO.md records the exact evidence and remaining integration limits.


### Slowest-four rendering follow-up

The 1080p GL survey selected SlimeMold, Lattice, Hyperbolic and LiquidLight.
Paired pre-change/current measurements on AMD Radeon Rembrandt EGL, with fixed
seed/audio, 32 warmup and 48 timed frames, gave these uncapped render rates:

| Effect | Before FPS | After FPS |
| --- | ---: | ---: |
| SlimeMold | 12.1 | 22.3 |
| Lattice | 21.8 | 42.9 |
| Hyperbolic | 23.0 | 40.1 |
| LiquidLight | 29.0 | 39.9 |

These include CPU rendering, upload and GPU completion; they exclude the app HUD,
audio processing, window flip and VSync. Exact sampled CPU pixels and real EGL
RGBA frames matched the pre-change source. Resolution, populations and visual
baseline tolerances were retained. The temporary pre-change reference is not a
versioned benchmark artifact; this table records a one-off paired experiment.

Run the reproducible current-state survey, including all 33 registered effects:

```bash
PYTHONDONTWRITEBYTECODE=1 .venv/bin/python tests/diagnostics/fps_survey.py --backend egl --json-output /tmp/psysuals-fps.json
```

Use `--backend cpu` or `both` to inspect CPU fallback separately; the CPU-only
slowest effects differ from GL. Run performance measurements without concurrent
tests or other heavy work. Missing ModernGL/EGL fails explicitly rather than
substituting a CPU run. JSON includes fixture/environment and effect source hashes.

Native Android changes and a finer, filtered SlimeMold field are prepared in
AndroSaver. Android compilation, tests, measured rankings and physical-TV visual
acceptance remain pending the user's combined GitHub/device verification batch.
See TODO.md and AndroSaver's backport notes for scope and intentional differences.
