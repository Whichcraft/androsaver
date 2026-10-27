# Visualizer Music Reactivity

Current native registry: psysuals 3.20, 32 effects. Fourteen effects use elapsed
render time and separate motion/light smoothing. Saved Auto/Laptop/TV profiles
control motion and light independently of resolution. Effect changes clear all
retained framebuffers with no outgoing crossfade.

How each effect responds to audio. All effects receive low-frequency onset,
mid, treble, and beat-derived signals from the system audio:

| Signal | Source | Typical range |
|--------|--------|---------------|
| **Beat** | Weighted FFT bins 0–19 (~0–860 Hz at 44.1 kHz) compared with a 15-frame rolling average | 0.0 – 1.0 before intensity |
| **Mid** | FFT bins 20–99 (~860 Hz–4.3 kHz), expressed as positive deviation above a rolling average | 0.0+ |
| **Treble** | FFT bins 100–255 (~4.3–11 kHz), expressed as positive deviation above a rolling average | 0.0+ |
| **FFT** | Smoothed 512-bin spectrum supplied directly to spectrum-oriented effects | normalized magnitudes |
| **Waveform** | Time-domain capture, 512 samples | -1.0 – 1.0 |

The **Effect Intensity** setting (Off / Low / Medium / High / Max) is a multiplier applied only to the beat signal before it reaches the effect. FFT values are always unscaled. At **Low** (default) the beat multiplier is 0.5×; at **Max** it is 2.0×.

The first beat after startup or a genre change is suppressed while the rolling
baseline is initialized. Mid and treble are deviation signals, not raw
volume meters: steady energy trends toward zero, while transients rise above
zero. If `RECORD_AUDIO` is denied or the Android `Visualizer` API is
unavailable, rendering continues with zeroed audio data.

In the effect sections below, **bass** is historical psysuals terminology for
the low-frequency beat/onset signal. In the Android implementation that value
is `audio.beat` after the selected intensity multiplier; it is not a separate
raw `AudioData.bass` field. **High** in older descriptions means Android's
`audio.treble` signal.

---

## Yantra

**What it looks like:** Seven concentric rotating polygons (triangle → nonagon), connected by web lines and radial spokes.

**Music reactivity:**

| Visual element | Reacts to |
|----------------|-----------|
| Ring rotation speed | Each ring driven by its own frequency band (bass → outermost, mixed bands toward center); beat adds an additional angular impulse |
| Ring radius pulse | Beat kicks each ring outward via a spring; it bounces back when the sound drops |
| Spoke brightness and length | Beat and high-frequency energy; spokes only appear when beat > 0.05 |
| Overall trail length | Total energy (bass + mid + high): loud audio = long glowing trails (fadeBlack α 0.04), silence = short trails (α 0.17) |
| Hue cycling speed | Constant slow drift; time also advances faster at higher beat |

**Silence behaviour:** Rings drift almost imperceptibly (base angular velocity is 0.0004 rad/frame ≈ 1.4°/s). The screen fades quickly, leaving a clean dark image. All activity ramps back up immediately when audio returns.

---

## Cube

Two fitted wireframe cubes and two independent satellites. Smoothed audio drives gentle angular speeds and a small scale change; fast light accents change brightness. Projection scales with the viewport and trail samples use 60 Hz time units.

## Plasma

**What it looks like:** Full-screen interference pattern of overlapping sine waves rendered entirely on the GPU.

**Music reactivity:**

| Visual element | Reacts to |
|----------------|-----------|
| Animation speed | Bass and beat accelerate time (`time += 0.018 + bass×0.05 + beat×0.08`) |
| Wave frequency / complexity | Mid widens the spatial frequency of all four wave fields (`fm = 1 + mid×0.7`) |
| Colour hue offset | Bass shifts the hue of the entire screen by up to ±0.35 |
| Brightness | Beat brightens the whole image; high-frequency energy adds a smaller lift |

**Silence behaviour:** The plasma continues to animate at its base speed (0.018/frame). It never stops moving — it is the only effect with no silence state.

---

## Tunnel

A gently curved first-person tunnel. Camera curvature is independent of audio; smoothed audio adjusts slow travel. Rings retain stable star identities through depth sorting and triangles spawn using bounded elapsed-time credit.

## Lissajous

A stable 3:2:5 3-D knot with smoothed rotation/scale, 60 Hz trail sampling and bounded projection. Lines never bridge large discontinuities; light responds more quickly than geometry.

## Nova

**What it looks like:** A 7-fold kaleidoscope of the live audio waveform drawn across 4 concentric layers, with two counter-rotating triangle rings and a small central triangle cluster.

**Music reactivity:**

| Visual element | Reacts to |
|----------------|-----------|
| Layer rotation speed | Each layer driven by a different band (bass, mid, high, average); beat adds additional spin |
| Layer radius pulse | Beat kicks each layer outward via a spring; bounces back |
| Waveform amplitude | Each layer's waveform is magnified by its energy band plus beat |
| Layer brightness | Energy band and beat raise brightness |
| Triangle ring brightness | Bass and beat brighten both outer triangle rings |
| Central triangle size | Bass and beat expand the central cluster |
| Animation speed | Beat accelerates time (time += 0.018 + beat×0.025) |

**Waveform source:** Nova is the only effect (besides Spectrum) that uses the raw time-domain waveform, not just FFT bins. Every layer shows the actual audio waveform shape.

**Silence behaviour:** All four layers drift at their base rotation velocities (0.005–0.0088 rad/frame). Waveform amplitude collapses to near-flat. The mandala remains visible but very calm.

---

## Spiral

**What it looks like:** Six spiral arms of particles flying out of a vanishing point, rendered with three-fold rotational symmetry (18 arms total visible), plus ring connectors at regular depth intervals.

**Music reactivity:**

| Visual element | Reacts to |
|----------------|-----------|
| Particle travel speed | Bass and beat increase how fast particles fly toward the viewer |
| Arm radius (spiral opening) | Each particle's radial distance is pushed outward by the FFT bin corresponding to its depth — mid-frequency peaks widen the arms |
| Overall scale | Beat inflates a spring (scale factor); the whole spiral breathes in and out |
| Hue advance speed | Beat slightly accelerates hue cycling |
| Beat flash | Particles very close to the camera flash bright white on strong beats (beat > 0.35) |

**Particle depth mapping:** Each particle's distance from center maps to a specific FFT bin. Deep particles react to bass frequencies; close particles react to highs. This makes the spiral's width breathe at different frequencies along its length.

**Silence behaviour:** Particles coast forward at base speed (0.038/frame) and the spiral continues its slow inward flow. Scale spring decays to 1× between beats. The three-fold symmetry (N_SYM=3) is always on.

---

## Bubbles

**What it looks like:** Translucent neon bubbles rising from the bottom of the screen, each with a multi-layer halo and a specular highlight.

**Music reactivity:**

| Visual element | Reacts to |
|----------------|-----------|
| Spawn rate | A baseline of two bubbles per frame continues; beat, bass, and treble add extra spawns depending on intensity |
| Rising speed | Beat makes newly spawned bubbles rise faster |
| Bubble size at spawn | Bass makes new bubbles larger |
| Colour spread | Higher beat = wider hue spread across newly spawned bubbles (more colour variety) |
| All bubbles' size per frame | A global pulse spring driven by beat and bass makes every bubble on screen swell in unison |
| Per-bubble beat flash | When beat > 0.5, each bubble gains a bright outer ring scaled by beat strength |
| Extra neon rings | Only visible when beat > 1.0 (requires Effect Intensity above Low); up to two extra expanding rings per bubble |
| Mid-frequency size modulation | Mid energy adds a small per-frame size boost to all visible bubbles |

**Silence behaviour:** The baseline spawn rate continues, but no audio-driven extra bubbles or mega-bubbles appear. Existing bubbles continue rising with their individual velocities and drift with wobble, while the global pulse spring decays to zero.

---

## Spectrum

**What it looks like:** Classic equalizer bar graph with ~80 log-spaced bars covering 20 Hz – 16 kHz, floating peak markers, and a waveform line overlay.

**Music reactivity:**

| Visual element | Reacts to |
|----------------|-----------|
| Bar heights | Direct FFT magnitude — each bar shows the average energy in its frequency range |
| Bar smoothing speed | Beat accelerates the lerp toward the target height (faster attack on strong beats) |
| Peak markers | Float at the highest point each bar has reached; decay at 6% per frame when not exceeded |
| Bar brightness | Higher bars are brighter (lightness 0.38 + h×0.42) |
| Waveform overlay | Live time-domain audio waveform drawn across the full width |

**This effect does not use `beat` for size explosions.** It is a faithful frequency analyser — what you see is what is in the audio. The beat signal only affects display smoothing speed.

---

## Waterfall

**What it looks like:** Scrolling time-frequency spectrogram — new frequency data appears at the top and scrolls downward, leaving a visual history of the last ~100 frames.

**Music reactivity:**

| Visual element | Reacts to |
|----------------|-----------|
| Pixel brightness | Direct FFT magnitude per frequency column per frame |
| Hue cycling speed | Beat slightly accelerates global hue drift |
| Leading-edge flash | Beat briefly brightens the newest rows at the top of the screen |
| Colour across frequency | Higher-frequency columns have a different hue offset (up to 0.75 hue rotation across the full width) |

**This effect shows audio history, not just the current moment.** Silence appears as dark rows. Loud transients leave bright horizontal bands. The scrolling speed is constant — one row per frame regardless of tempo.

**Silence behaviour:** Dark rows scroll downward. Any previously bright rows fade as they age (brightness decays by 70% as rows reach the bottom). The screen gradually darkens over ~100 frames of silence.

## Remaining registered effects: signal matrix

The compact matrix below covers the registered effects whose detailed visual
descriptions live in [`visualizer-modes.md`](visualizer-modes.md). “Low” means
the historical psysuals bass/Android beat signal; “beat” means the same signal
after the intensity multiplier when the effect uses an onset trigger. A dash
means the signal is not used for the primary behavior. Every mode still
receives the complete `AudioData` snapshot, so this table describes meaningful
inputs rather than an API restriction.

| Effect | Low / beat response | Mid response | Treble response | Other input | Silence behavior |
|---|---|---|---|---|---|
| TriFlux | Rising beat edge activates one tile with cooldown; low changes the pop target | Sweep speed and pop target gain | Rainbow edge/detail modulation | — | Sweeps, hue drift and occasional automatic pops continue |
| Corridor | Low drives frame travel speed; beat flares near frames | Adds frame/spark brightness and spawn energy | Spark radius and near-frame detail | — | Slow corridor motion and sparse/dim sparks |
| Branches | Low changes trunk length; beat adds arms and a brightness burst | Three sine fields twist branch angles | Segment/core line weight | — | Arms contract with minimal jitter |
| Butterflies | Low controls wing flap and pair motion; beat emits sparkles | — | — | Pair state controls pursuit/wander phases | Pairs continue bounded motion; no new beat sparkles |
| FlowField | Low attracts particles toward the center | — | Per-particle scatter force | Beat jumps the field phase | Particles drift in the base field without gravity/scatter |
| Fireworks | Beat launches extra rockets; `audio.gain` changes background launch interval | — | Ember count/brightness and explosion variation | Gravity/drag are constant | Rockets continue auto-launching at the gain-scaled interval |
| Aurora | Low billows curtain amplitude; beat flashes bloom and nudges hue | Ribbon height/thickness | Shimmer speed | — | Curtains drift at base shimmer speed |
| Lattice | Low drives whole-grid scale breath; beat fires center shockwave | Peak-normalized grid activity | Edge/column activity and beam brightness | Smoothed FFT maps frequency to nodes | Dim base grid and hue rotation; no shockwave |
| Mycelium | Beat blooms a colony, seeds tips, and releases spores | — | Spore/filament color and motion detail | Colony growth state | Tips and spores continue slow bounded growth/decay |
| Magnetar | Beat creates an equatorial shockwave and vertical scatter | — | Particle color/glow detail | Dipole rotation state | Particles orbit slowly along field lines |
| SlimeMold | Low controls agent speed; beat returns a fraction toward center | — | — | Diffusing trail buffer | Agents continue slow motion while trails decay |
| Mobius | Smoothed low gently changes twist; fast response adds light shiver | — | Gentle rotation contribution and light accents | 3-D projection and hue drift | Strip rotates at its base speed |
| Chromatic | Low/beat drives ripple warp and ring spawning | — | RGB split radius and halo separation | Ring age controls expansion/fade | Existing rings expand and expire; no new rings |
| Persistence | Beat raises the nested-solid boost and edge brightness | — | — | Independent 3-D orbital speeds | Solids continue slow rotation with long persistence |
| Synapse | Strong beat adds nodes and launches signal cascades | Signal/arrival variation | — | Periodic mutation adds or sheds nodes | Graph wanders and existing signals decay |
| Heartbeat | Beat spawns rings; low level morphs circle ↔ polygon | — | — | Ring age controls expansion and fade | Rings continue; periodic low-energy auto-spawn keeps motion alive |

The eight field/geometry effects added with the current upstream integration
are documented individually below because their bounded scalar-field or
projection parameters are more informative than a single matrix cell.

## Hyperbolic

A connected dual-icosphere of 162 shared pentagon/hexagon tiles. Individual caps lift, hinge and sink with travelling colour waves and shaded walls. Horizon clipping hides rear faces; geometry and light use separate smooth responses.

## LiquidLight

**What it looks like:** A fluorescent interference field made from crossed
swirl and wave functions. Mid increases phase speed and treble accelerates
hue drift. The bounded shared field renderer keeps memory and work constant
across display sizes; audio changes the pattern's motion rather than its
resolution.

## Cymatica

A Chladni-like nodal plate with slowly smoothed weights across six FFT bands. Sand follows the actual analytic nodal gradient. There are no argmax mode jumps or sawtooth mixture resets.

## Phason

A full-resolution GLES seven-wave interference field. Phase/hue advance slowly with elapsed time and its pulse remains bounded. The number of waves is fixed rather than switching with audio energy.

## Tesseract

**What it looks like:** A projected 4-D hypercube wireframe with up to three
overlaid depth layers. Beat rising through 0.7 advances the preset; beat also
drives scale and vertical bounce. Mid and treble contribute small phase and
hue changes. Scale is bounded to 0.62–1.28× so strong audio cannot push the
geometry off-screen.

## Ferrofluid

A GLES metallic liquid envelope with five moving magnetic poles and a triangular lattice of sharp peaks. Analytic surface gradients drive diffuse, specular and rim lighting; geometry is smoothed and rendered at framebuffer resolution.
