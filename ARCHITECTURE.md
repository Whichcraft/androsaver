# psysuals — Architecture (3.20.0)

## Overview

```
psysualizer.py   ← main entry point: audio orchestration + pygame loop
                 (Supports --gl for hardware-accelerated ModernGL rendering)
core/audio_engine.py ← sounddevice callback, FFT, fallback beat/BPM analysis
beat_tracking.py ← optional librosa beat/BPM refinement, off the render thread
config.py        ← shared mutable runtime state
effects/         ← effect modules and shared helpers, registered in MODES
requirements-gl.txt ← optional dependency set for the GL path
```

`psysualizer.py` owns the desktop runtime: audio capture, beat/BPM extraction,
device selection, saved display restoration, span mode, HUD, and render
orchestration. It supports both a CPU/Pygame surface path and a GPU/ModernGL
path. The repository does not include an Android application or build system;
its Android/headless integration is provided through the external GitHub
builder, which consumes the Python `Effect.draw_frame()` contract described
below.

---

## Audio pipeline

```
sounddevice InputStream callback
    │  variable-size mono callback blocks at 44 100 Hz
    ▼
_audio_cb()
    │  push captured samples to LibrosaBeatTracker history (short blocks stay short; long blocks keep the newest BLOCK_SIZE samples)
    │  Blackman-windowed rfft → log1p scale → /10
    │  exponential smoothing (α = 0.50) → _smooth_fft
    │  weighted spectral flux across available FFT bins → raw beat energy
    │  onset timestamps → fallback BPM
    │  bass / mid / treble rolling normalisation and attack/release envelopes
    ▼
get_audio()
    returns (waveform, fft, raw_beat, mid_energy, treble_energy, bpm, audio_time)
```

The callback keeps its per-block work bounded. It computes the always-available fallback beat/BPM path and pushes raw audio into `LibrosaBeatTracker`; the optional refinement layer may be unavailable when its dependency is missing.

Input-stream startup is tolerant now: the app tries the saved device first, then preferred concrete inputs (favoring PipeWire/Pulse and other explicit devices ahead of Linux's brittle `default` wrappers), and if all candidates fail it keeps running with no live input stream so the UI can still come up.

`beat_tracking.py` is an optional refinement layer:

- `push_audio()` stores recent blocks in a bounded deque.
- `analyze()` returns the last cached BPM immediately and, when enough history exists, schedules heavier `librosa` onset / beat analysis on a background thread.
- `refine_beat()` combines the low-latency spectral-flux signal with recent onset strength and beat-grid proximity.

This keeps the visual loop responsive while still improving BPM stability and beat timing when `librosa` is present.

### Beat normalisation

The main loop converts callback-time raw beat energy into the value passed to effects:

```python
impulse = max(0.0, min(raw_beat / (avg + 1e-6) - 1.0, 3.0))
step = min(0.1, max(0.0, config.FRAME_DT)) * 60.0
beat_decay = max(impulse, beat_decay * (0.82 if is_silent else 0.90) ** step)
beat = max(beat_decay, silence_beat_floor if is_silent else 0.0)
```

`avg` is the rolling average of the last 40 raw-beat samples. The result is volume-adaptive and decays smoothly between kicks.

`AudioEngine.get_envelopes()` exposes bounded attack/release envelopes for
bass, mids, and treble. Their time constants use callback timestamps, so
transient response and decay do not depend on render FPS.

The app also publishes `config.BEAT_PHASE`, a normalized `0.0 .. 1.0` position
within the current beat cycle. When fallback BPM and onset timestamps are
available, phase `0.0` is anchored to the latest onset; tap tempo uses the tap
frame as its anchor. With no reliable timing, the phase uses a slow
deterministic idle fallback; spectral-flux beat impulses are still computed
independently when audio is not gated as silent.

Saved-preset changes blend numeric intensity, background alpha, and parameter-transition
length over eight beats when BPM is known, or a short time-based fallback when
it is not. The discrete foreground/background mode switch occurs at the
midpoint; a new preset safely replaces an active morph.

Effects may declare a bounded `MORPH_SCHEMA` for compatible mode transitions.
Tesseract and Persistence currently share `_morph_projection`, so that value
interpolates after switching modes. Hyperbolic exposes a bounded warp parameter,
but Lattice does not expose a matching schema. Mode switches clear CPU, GPU,
presentation and post-process buffers; outgoing images are never crossfaded.
Display/context changes detach GPU resources before rebuilding the context while
retaining CPU simulation objects. Individual effects resize their state instead
of reseeding particles, network nodes or dye when supported.

### Silence handling

Before and after tracks, the app applies a silence gate with hysteresis using waveform RMS plus average FFT energy:

- Enter silence after `SILENCE_ENTER_SECONDS` of quiet audio blocks under the low thresholds (currently about 140 ms).
- Exit silence after `SILENCE_EXIT_BLOCKS` fresh audio blocks (currently two, about 46 ms at 44.1 kHz/1024 samples) cross either higher threshold.
- Repeated render frames do not advance the gate; non-finite samples and stale callback data are ignored.
- While silent, `raw_beat` is forced to zero, the rolling beat history is cleared, and `MID_ENERGY` / `TREBLE_ENERGY` are clamped to faint idle floors.

This prevents normalization noise from turning silence into fake beat spikes while still keeping effects gently alive at a low-motion baseline.

---

## Render loop

The visualizer observes raw render time after each presented frame. A bounded
quality governor uses a rolling 90th-percentile window and cooldown hysteresis
to publish `config.QUALITY_TIER` and `config.QUALITY_SCALE`; effects consume
the scale through the base render-resolution contract. It never increases an
effect's declared population or iteration limits.

The render loop in `psysualizer.py` uses a dual-target strategy to support both CPU and GPU effects:

1. **Target Abstraction**: CPU drawing operations target a `target` surface.
   - In CPU mode: `target` is the display surface.
   - In GL mode: `target` is display-sized. CPU effects produce an opaque
     composed frame; direct-GL effects use a transparent UI/HUD overlay.
2. **Effect Execution**: Effects are instantiated with an optional `GLRenderer`.
   - If an effect supports GL (like `PlasmaGL`), it renders directly to the GL context.
   - Otherwise, it draws to the `target` surface.
3. **Compositing**: In GL mode, the full-size CPU composition or transparent
   overlay is uploaded once. RGB-only blend operations retain an opaque alpha
   channel for CPU effects. Effects with `TRAIL_ALPHA = 0` replace the composed
   image while managing their own internal history. CPU-effect target surfaces
   are not erased after presentation; direct-GL overlays are cleared.
4. **Final flip**: `pygame.display.flip()` presents the frame.

Mode switches recreate the effect instance and reset foreground intensity to `config.DEFAULT_EFFECT_GAIN`.

Fullscreen/display changes call `Effect.detach_renderer()` before releasing the
shared GL renderer and recreating the context. CPU simulation instances remain
alive and are rebound to the replacement renderer. Supported effects rescale
particle positions, trails, graph anchors or fields without restarting. GPU
programs and post-process targets are recreated. Same-size forced rebuilds remain
supported; effects without the detach contract fall back to reconstruction.

Fourteen retuned effects consume `config.FRAME_DT` in seconds (default 1/60,
maximum consumed step 0.1). `TIME_BASED` opts their global trail fade into the same
time units. `CONTINUOUS_QUALITY` lets expensive fields reduce internal width and
height gradually with the quality scale; SlimeMold protects a native-detail floor
of 85% per axis on TV-sized/profiled outputs. Rendered images always scale into
the same display-sized composition. Fullscreen shaders receive actual GL
framebuffer dimensions rather than logical SDL dimensions.

---

## Display and Span Mode

- Startup display selection comes from saved settings unless `--display N` overrides it.
- If `xrandr` geometry is unavailable, SDL's display count and the `display=` target are used for selection; coordinate-based X11 spanning is only enabled when monitor geometry is known.
- On multi-monitor setups, the primary process can enter span mode and spawn one child process for every *other* monitor.
- Child windows are launched with `--span-child`, so they never recursively create more span children.
- `A` / `D` in span mode change the shared secondary-display mode across all spawned child windows.

---

## Shared config

`config.py` is mutable at runtime. After opening the desktop display, the app
writes the real monitor dimensions back into `config.WIDTH` and `config.HEIGHT`,
so effects can treat `config` as the live source of truth. For headless or
Android rendering, `Effect.draw_frame()` temporarily replaces those dimensions
with the requested physical framebuffer size for the duration of one frame.

| Variable | Default | Notes |
|----------|---------|-------|
| `WIDTH` | `0` | Placeholder until the display opens |
| `HEIGHT` | `0` | Placeholder until the display opens |
| `FPS` | `60` | Target frame rate |
| `FRAME_DT` | `1/60` | Elapsed render seconds, published once per frame |
| `VISUAL_PROFILE` | `auto` | Auto/Laptop/TV response profile; `V` cycles, CLI overrides saved setting |
| `QUALITY_TIER` / `QUALITY_SCALE` | `2` / `1.0` | Governor-selected internal detail budget |
| `LOW_SPEC` | `False` | Performance mode: caps FPS at 30 and scales down simulation budgets |
| `SAMPLE_RATE` | `44100` | Audio sample rate |
| `BLOCK_SIZE` | `1024` | Audio callback / FFT block size |
| `CHANNELS` | `1` | Mono input |
| `MID_ENERGY` | `0.0` | Normalised mid-band energy, updated each frame |
| `TREBLE_ENERGY` | `0.0` | Normalised treble energy, updated each frame |
| `BPM` | `0.0` | Live BPM estimate or tap-tempo override |
| `IS_SILENT` | `True` | Exported silence-gate state for effects and HUD logic |
| `DEFAULT_EFFECT_GAIN` | `0.7` | Reset value used on startup and mode changes |
| `EFFECT_GAIN` | `0.7` | Current beat gain applied to active rendered effects |
| `SILENCE_*` | various | Silence gate thresholds and idle motion floors |

### Resource safety

Effects keep bounded runtime state even when normalized beat values are unusually high. Bubbles cap visual geometry and cached surface dimensions; Fireworks cap rockets and embers and only trigger beat bursts on rising edges. Butterflies cap the flock at 12 agents and six pairs, use seeded per-effect randomness, and match only nearby free agents. Reduced-resolution effects retain their internal render targets and scale into the display surface instead of silently reallocating to full resolution.

---

## Effect contract

Effects are simple classes that can draw to a `pygame.Surface` or use a `GLRenderer`. Direct GL effects render through the active context and may ignore the surface argument:

```python
class MyEffect(Effect):
    TRAIL_ALPHA = 48

    def __init__(self, renderer=None, **kwargs):
        super().__init__(renderer=renderer, **kwargs)
        ...

    def draw(self, surf, waveform, fft, beat, tick):
        if self.renderer:
            # GL path
            ...
        else:
            # Pygame path (surf is a pygame.Surface)
            ...
```

CPU effects also inherit
`Effect.draw_frame(width, height, waveform, fft, beat, tick, renderer=None)` for
Android or headless hosts. The external GitHub builder must initialize Pygame
and set `config.WIDTH`, `config.HEIGHT`, and `config._INITIALIZED` before
constructing an effect, then call this method once per output frame. The
adapter sets the requested physical framebuffer size for the duration of the
render, draws into a matching Pygame surface, and returns a fresh
`(height, width, 4)` `numpy.uint8` RGBA array. Direct-GL effects may override it
with a GPU/offscreen implementation; `PlasmaGL` requires the active
`GLRenderer` argument. The host should reuse one effect instance across output
frames so stateful trails and simulations persist, then call `release()` when
that instance is discarded.

Inputs passed to `draw()`:

| Parameter | Type | Meaning |
|-----------|------|---------|
| `surf` | `pygame.Surface` | Destination surface |
| `waveform` | `np.ndarray` `(1024,)` | Raw mono PCM block |
| `fft` | `np.ndarray` `(512,)` | Smoothed log-scaled spectrum |
| `beat` | `float` | Normalised beat impulse after gain / decay |
| `tick` | `int` | Frame counter |

Butterflies use the standard CPU effect contract. Their persistent trail is
rendered at the effect's reduced internal resolution and scaled into `surf`.
New agents begin inside a viewport margin as cocoon motes and remain in that
state for their first 19 updates. Body size follows the internal canvas and pair
orbits stay inside the visible area. They then
pair, orbit with wing synchronisation, and eventually break up; `release()`
clears all owned surfaces and simulation state and is safe to call more than
once.

Effects may also read:

- `config.WIDTH`, `config.HEIGHT`
- `config.FRAME_DT`, `config.VISUAL_PROFILE`, `config.QUALITY_SCALE`
- `config.MID_ENERGY`, `config.TREBLE_ENERGY`
- `config.BPM`
- `config.IS_SILENT`
- `config.EFFECT_GAIN`
- `effects.palette.palette`

Standardized audio energy parameters used across the codebase:

| Signal | Source | Approx. use / typical mapping |
|--------|--------|------------------------------|
| `beat` / `bass` | `beat` parameter | kick, sub, spring pulses, scale expansion |
| `mid` | `config.MID_ENERGY` | synths, melodic lines, rotation speed, sways |
| `high` / `treble` | `config.TREBLE_ENERGY` | hats, high-frequency transients, sparkles, vertex jitter |

---

## Adding an effect

The current registry contains 32 effects; Mandelbox and Morphogenesis were
removed in 3.20.0. Four recipes reference only active modes.

1. Create `effects/myeffect.py`.
2. Implement `draw(surf, waveform, fft, beat, tick)`.
3. Import it in `effects/__init__.py`.
4. Add it to `MODES`.

Rules that matter in this repo:

- Modes 1–9 are the number-key modes.
- `Spectrum` and `Waterfall` must remain the final two `MODES` entries, in that order.
- If an effect owns its own persistent trail surface, set `TRAIL_ALPHA = 0` and manage fading internally.

---

## Helper modules

| File | Role |
|------|------|
| `core/audio_engine.py` | `AudioEngine`: capture, FFT, beat/genre detection |
| `core/display_manager.py` | `DisplayManager`: monitors, X11, windowing, span mode; lazily loads the GL renderer only when `--gl` is active |
| `core/ui_manager.py` | `UIManager`: HUD, pane, picker rendering |
| `core/postprocess.py` | bounded optional psychedelic post-processing chain |
| `core/quality.py` | rolling frame-time quality governor and resolution-tier selection |
| `core/regression_tester.py` | headless effect contract and registry regression checks |
| `effects/utils.py` | `hsl()` and `_hsl_batch()` colour helpers |
| `effects/palette.py` | shared hue/saturation/lightness palette driven by audio |
| `settings.py` | persistent settings and preset storage under `~/.config/psysuals/` |
| `gl_renderer.py` | moderngl helper for the optional GL path, including fullscreen blits and feedback transforms, loaded lazily by the display manager |
| `requirements-gl.txt` | optional dependency set for the GL path |

---

Line counts are intentionally not part of this document because they change with ordinary maintenance. The helper-module table above describes the stable responsibilities and file boundaries.
