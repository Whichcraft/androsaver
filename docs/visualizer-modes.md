# Visualizer Modes Reference

All modes extend `BaseMode` (`com.androsaver.visualizer.BaseMode`):
```kotlin
abstract fun draw(gl: GLDraw, audio: AudioData, tick: Long)
```

`AudioData` provides: `bass`, `mid`, `high` (0–1 normalized FFT bands), `beat` (0–2, onset strength × intensity multiplier), `waveform[]`, `fft[]`.

Audio pipeline: Android `Visualizer` API → `AudioEngine` → 512-bin FFT → band extraction + beat detection → `AudioData` delivered at ~60 fps.

**Effect Intensity setting** multiplies only `beat` (not FFT values): Off=0×, Low=0.5×, Medium=1×, High=1.5×, Max=2×.

---

## Mode List (in rotation order)

| # | Class | Display Name | Key Visual |
|---|-------|-------------|------------|
| 1 | `YantraMode` | Yantra | 7 concentric polygons (triangle→nonagon) with web + spokes |
| 2 | `CubeMode` | Cube | Nested wireframe cubes + 2 orbiting satellite cubes |
| 3 | `TriFluxMode` | TriFlux | Triangle mosaic wall — tiles pop to foreground on beat |
| 4 | `LissajousMode` | Lissajous | 3D trefoil knot, neon glow |
| 5 | `TunnelMode` | Tunnel | First-person tunnel with triangle bursts |
| 6 | `CorridorMode` | Corridor | First-person neon rounded-rectangle corridor + sparks |
| 7 | `NovaMode` | Nova | 7-fold mirror kaleidoscope waveform |
| 8 | `SpiralMode` | Spiral | 6-arm neon helix |
| 9 | `BubblesMode` | Bubbles | Rising translucent bubbles |
| 10 | `PlasmaMode` | Plasma | Full-screen sine-wave interference |
| 11 | `BranchesMode` | Branches | Recursive fractal lightning tree |
| 12 | `ButterfliesMode` | Butterflies | Neon butterfly pairs entering, orbiting, departing |
| 13 | `FlowFieldMode` | FlowField | 4 000 particles riding a sine/cosine noise field |
| 14 | `VortexMode` | Vortex | Firework rockets exploding into glowing embers |
| 15 | `BarsMode` | Spectrum | Log-spaced spectrum + waveform overlay |
| 16 | `WaterfallMode` | Waterfall | Scrolling time-frequency spectrogram |

Remote: **←/→** cycles modes. **↑/↓** changes intensity.
Auto-cycle: configurable interval (Off / 1–15 min), rotates through all modes.

---

## YantraMode

Seven concentric polygons (3→9 sides). Rings rotate at graduated per-ring speeds (faster toward the outside) tied to frequency bands. Beat kicks radius outward (spring return). Web lines + spokes appear above beat threshold. Trail length tracks total energy. Silence: visible baseline spin.

## CubeMode

Two wireframe cubes (inner + outer), 3D rotation. Two orbiting satellite cubes (fixed 180° apart, independent rotation). Beat pulses cube scale; bass reinforces rotation velocity. Satellite trails drawn with additive blend for persistent glow. Silence: slow constant rotation.

## TriFluxMode

Triangle mosaic wall filling the screen. All triangles have animated rainbow edges. N_FILLED=5 tiles also receive a color fill. Two independent rainbow sweep bands glide diagonally across the grid. On bass beats, an interior tile pops to 4.5–8.5× size (up to 3 at once), bounces off screen edges, then springs back. Silence: gentle hue drift, sweeps still active.

## PlasmaMode

Shader-based full-screen plasma (sine interference). Always animating — never goes dark. Bass shifts color phase, high shifts wave frequency. No silence state (constant motion by design).

## TunnelMode

First-person tunnel perspective. Rings scroll toward viewer. Bass-triggered triangle bursts at tunnel mouth. Beat flashes ring brightness. Path follows a center curve (not screen corners). Silence: rings scroll slowly.

## LissajousMode

3D parametric trefoil knot rendered as a neon glowing line. Bass drives rotation X-axis, mid drives Y-axis, high drives Z-axis scale. Beat adds angular impulse. Glow achieved via multiple overlapping passes with alpha.

## CorridorMode

First-person ride through a neon rainbow corridor. 28 rounded-rectangle frames scroll toward the camera; hue sweeps from far (dark) to near (bright) across the full rainbow. Bass drives scroll speed. Beat flares nearest frames and continuously spawns glowing spark particles that streak toward the camera along a gently curving path. Silence: slow scroll, dim frames, few sparks.

## NovaMode

Waveform rendered with 7-fold rotational symmetry. Each spoke mirrors waveform amplitude. Beat expands radius. Layers spin at different speeds. High energy → more layers active. Silence: slow spin, minimal amplitude.

## SpiralMode

6 helical arms. Bass controls radius breathing. Beat triggers brightness flash and arm thickness pulse. Mid controls spiral tightness (pitch). Arms fade toward center. Silence: arms collapse to thin dim lines; restores pre-session radius on mode exit.

## BubblesMode

Translucent circles rise from bottom. Count and size driven by bass. Beat triggers synchronized pulse (all bubbles flash + expand). High frequency adds smaller bubbles. Bubbles wrap when they exit the top. Silence: a few large slow bubbles drift upward.

## BranchesMode

Psychedelic fractal lightning tree. Nine neon arms radiate from screen centre at a slowly rotating base angle, each splitting recursively to depth 7. Triple-fork at trunk and first split level for a dense inner canopy. Every segment drawn twice — a wide dim halo and a white-hot bright core — for neon flare. Mid frequencies twist all branch angles live via three overlapping sine fields; bass drives trunk length; beat fires extra arms with a brightness burst. Silence: arms contract, minimal jitter.

## ButterfliesMode

Up to 3 pairs of neon butterflies. Solo enters from a screen edge, partner joins after a delay and orbits lovingly. Wings flap with bass; pairs synchronise wing phase when close. Beat fires sparkles. After a random lifetime the pair wanders off-screen and a new pair enters. Scale 7.2 (solo) / 6.84 (partner).

## FlowFieldMode

4 000 particles surfing a continuously-evolving 3-layer sine/cosine noise field. Particles wrap at screen edges and paint rainbow trails on a very slowly fading background (fadeBlack 8/255 ≈ 40-frame persistence). Bass warps field intensity and particle speed; beat fires a phase jump that instantly reshapes all flow lines into a new pattern. Silence: particles drift gently across screen.

## VortexMode

Firework rockets launch from the bottom of the screen, arc upward under gravity with drag, and explode into 80–120 glowing embers at the apex. Embers drift under gravity while fading out. Beat fires extra rockets; auto-launch every ~85 frames between beats. Trails linger on a slow-fade dark background. Silence: rockets auto-launch at regular intervals.

## BarsMode (Spectrum)

Vertical bars on log-frequency scale. Bar height = FFT magnitude. Peak markers decay slowly. Waveform overlay across bar tops. Beat flashes bar color from accent to white. Silence: flat bars at minimum height.

## WaterfallMode

2D scrolling spectrogram (time on Y, frequency on X). Each row = one FFT frame. Brightness = magnitude. Beat flashes the leading edge row. Color maps low→high frequency through a gradient. Silence: dim uniform band slowly scrolling.

---

## Adding a New Mode

1. Create `com.androsaver.visualizer.modes.MyMode.kt` extending `BaseMode`
2. Implement `draw(gl: GLDraw, audio: AudioData, tick: Long)`
3. Register in `VisualizerRenderer` mode list
4. Add display name string to `res/values/strings.xml`
5. Add entry to `res/values/arrays.xml` (visualizer_modes array)
6. Add corresponding Prefs constant in `Prefs.kt`

## Audio Reactivity Details

See `visualizer-music-reactivity.md` for per-effect tables of exactly which visual elements react to bass/mid/high/beat, including numerical thresholds and silence behavior.
