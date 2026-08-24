# psysuals — The Ultimate Psychedelic Experience

**Welcome to the super-duper greatest music visualizer ever made.** psysuals delivers uncompromising visual intensity and rock-solid performance. Whether you're blasting psytrance in a dark room or driving a multi-monitor stage setup, it is built to melt your mind with precision and style.

![Version](https://img.shields.io/badge/version-3.17.0-orange)
 ![Python](https://img.shields.io/badge/python-3.10%2B-blue) ![License](https://img.shields.io/badge/license-MIT-green)

---

## 🚀 Highlights

- **✨ Unified ModernGL Engine** — Harness the power of your GPU with experimental hardware acceleration. Run with `--gl` for blistering frame rates and per-pixel shader fluid motion.
- **🏗️ Modular Object-Oriented Architecture** — Re-written from a monolithic script into a sleek, professional engine. Specialized `AudioEngine`, `DisplayManager`, and `UIManager` classes ensure a clean, maintainable, and high-performance foundation.
- **🖥️ Ultimate Multi-Monitor "Span Mode"** — Gone are the days of fixed dual-screen limits. The app scales dynamically, spawning child processes for every monitor you own, with synchronized mode switching across the entire span.
- **🔊 Resilient Audio Pipeline** — Tolerant audio capture with a no-input fallback, silence-aware idle motion, live device switching, and spectral-flux beat detection.
- **⚖️ Built-in Benchmarking & Regression Checks** — Measure speed with `benchmarks.py`; the smoke test validates registry order and instantiates all 34 registered effects, with focused tests for audio timing, display lifecycle, and effect safety bounds.

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

The app restores the last saved display index on startup. Use `--display N` to override that for a single launch.

### Controls

| Key / Action | Effect |
|---|---|
| `←` / `→` or `Space` or click | Cycle to previous / next mode (or adjust pane slider when pane is open) |
| `↑` / `↓` | Increase / decrease effect intensity (or adjust FlowField particles by 2,000, or navigate pane sliders when pane is open) |
| `1` – `9` | Jump to modes 1–9 |
| `Tab` | Toggle real-time settings pane (effect gain, bg alpha, crossfade length) |
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

Effects can also read `config.BEAT_PHASE`, a normalized position from `0.0` at
the predicted beat boundary to `1.0` just before the next one. It falls back to
a slow idle phase when BPM timing is unavailable.

## Testing and benchmarks

Run the headless smoke and registry checks with:

```bash
SDL_VIDEODRIVER=dummy SDL_AUDIODRIVER=dummy .venv/bin/python smoke_test.py
```

Run the automated unit tests with:

```bash
.venv/bin/python -m unittest discover -s tests -v
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

Run a quick headless CPU performance comparison with:

```bash
SDL_VIDEODRIVER=dummy SDL_AUDIODRIVER=dummy .venv/bin/python benchmarks.py --cpu-only --duration 2
```

To attempt the CPU/ModernGL comparison, use a real display/context (do not set the dummy video driver):

```bash
.venv/bin/python benchmarks.py --gl --duration 2
```

The benchmark reports when ModernGL is unavailable or the context cannot be created; CPU measurements still complete.

`Shift+R` cycles curated pairings such as Hyperbolic + LiquidLight,
Tesseract + Persistence, Cymatica + Ferrofluid, Morphogenesis + Plasma, and
Fireworks + Aurora. Choosing a recipe explicitly enables its background; the
individual foreground/background controls remain available afterward.

`Shift+X` cycles optional post-processing: off, chromatic separation,
kaleidoscope mirroring, feedback rotation, and diffraction-style bloom. The
default is off; each pass reuses its working surfaces and remains bounded on
the CPU path.

Compatible visual modes also interpolate their declared projection or warp
parameters during a transition, so related geometries can flow into one
another instead of only crossfading their surfaces.
GL results include the display-present step (and context synchronization when available), so compare them on the same display and VSync settings.

Use `--display N` for monitor selection. When `xrandr` geometry is unavailable, the app falls back to SDL's detected display count and asks SDL to target the selected display.

## Project structure

```
psysuals/
├── psysualizer.py            # Entry point — clean orchestrator
├── core/
│   ├── audio_engine.py       # Capture, FFT, beat & genre detection
│   ├── display_manager.py    # Monitors, X11, windowing, span mode
│   ├── ui_manager.py         # HUD, pane, picker rendering
│   ├── postprocess.py         # Optional bounded psychedelic post-processing
│   ├── quality.py             # Adaptive frame-time quality governor
│   └── regression_tester.py   # Headless effect contract checks
├── beat_tracking.py          # Optional librosa-based BPM/beat refinement
├── config.py                 # Shared mutable state
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
│   └── *.py                   # 34 registered visual implementations and helpers
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
