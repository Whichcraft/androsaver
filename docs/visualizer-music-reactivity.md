# Visualizer Music Reactivity

How each effect responds to audio. All effects receive three frequency bands and a beat signal derived from the system audio:

| Signal | Source | Typical range |
|--------|--------|---------------|
| **Bass** | FFT bins 0–6 (~20–250 Hz) | 0.0 – 1.0 |
| **Mid** | FFT bins 6–30 (~250 Hz – 2 kHz) | 0.0 – 1.0 |
| **High** | FFT bins 30–512 (~2 kHz – 20 kHz) | 0.0 – 1.0 |
| **Beat** | Onset strength (scaled by the Effect Intensity setting) | 0.0 – 2.0 |

The **Effect Intensity** setting (Off / Low / Medium / High / Max) is a multiplier applied only to the beat signal before it reaches the effect. FFT values are always unscaled. At **Low** (default) the beat multiplier is 0.5×; at **Max** it is 2.0×.

---

## Yantra

**What it looks like:** Six concentric rotating polygons (triangle → octagon), connected by web lines and radial spokes.

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

**What it looks like:** Two nested wireframe cubes (inner cube is 45% the size of the outer) rotating in 3D, with two small satellite cubes orbiting around them.

**Music reactivity:**

| Visual element | Reacts to |
|----------------|-----------|
| Rotation velocity (all three axes) | Mid drives X-axis, bass drives Y-axis, high drives Z-axis; beat adds a larger impulse to all three |
| **Spin direction flip** | Every time bass crosses 0.4 on a rising edge the spin direction reverses — creates a sudden direction change on kick drums |
| Overall scale (size) | Beat pumps a spring that inflates both cubes; scale decays back to 1× between beats |
| Edge brightness | Proportional to current scale velocity (brighter when the cube is expanding) |
| Satellite orbit speed | Beat adds angular velocity to the orbit |
| Satellite orbit radius | Each satellite oscillates in and out with its own phase (bouncy radial motion) |
| Echo trail length | Fixed 14-frame ring buffer; older frames drawn with lower alpha |

**Silence behaviour:** Cubes coast at their current spin velocity which decays slowly (damping 0.86× per frame). No new impulses means gradual slowdown. Trails fade as the motion decreases.

---

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

**What it looks like:** First-person ride through a curved neon tube. The tube itself is geometry-only. Bass punches spawn rotating triangles that fly toward the viewer along the tunnel center line.

**Music reactivity:**

| Visual element | Reacts to |
|----------------|-----------|
| Triangle spawn | Bass rising-edge trigger at threshold 0.20; one burst per crossing |
| Burst size | Scales with bass intensity: 2 triangles at threshold, up to 15 at 3.5× threshold |
| Triangle spin speed | Scales with bass intensity: faster and wilder at higher bass |
| Triangle size | Scales with bass intensity: larger at higher bass |
| Triangle brightness | Beat makes triangles brighter as they approach the camera |
| Triangle trail | All triangles fade with the global fadeBlack (α 0.11) |

**Tunnel walls:** Constant speed, no music reactivity. The smoothness of the ride is always the same regardless of what is playing.

**Triangle tracking:** Each triangle records the tunnel path position at the moment of spawn. As it approaches the camera it stays locked to the tunnel's curving center line, so it always appears inside the tube.

**Silence behaviour:** No new triangles spawn. Existing ones continue flying toward the camera and eventually disappear. The tunnel ride itself never stops.

---

## Lissajous

**What it looks like:** A 3D trefoil Lissajous knot drawn with three-fold rotational symmetry and a two-pass neon glow (thin outer halo + bright inner line).

**Music reactivity:**

| Visual element | Reacts to |
|----------------|-----------|
| Knot shape (frequency ratios) | Bass slightly shifts the X-frequency (ax), mid shifts Y (ay), high shifts Z (az); all changes are small (×0.05) so the knot shape evolves slowly |
| Phase drift | Bass and high continuously advance the phase offsets dx and dz |
| Animation speed | Beat accelerates how fast the trace advances along the knot |
| Scale (overall size) | Beat inflates a spring; the knot shrinks back between beats |
| 3D rotation speed | Beat adds angular impulse to both X and Y rotation axes |
| Hue advance speed | Beat nudges the hue slightly, so colours change faster on louder passages |
| Trail / glow persistence | Controlled by fadeBlack (α 0.18); older parts of the knot fade to black fairly quickly |

**Silence behaviour:** The knot continues tracing at its base speed (t += 0.010/frame) and rotates gently with its baseline angular velocity. The shape changes very slowly. Only dot trails visible — no circles or additional overlays.

---

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
| Spawn rate | Beat and bass spawn additional bubbles each frame (1–6+ per frame depending on intensity) |
| Rising speed | Beat makes newly spawned bubbles rise faster |
| Bubble size at spawn | Bass makes new bubbles larger |
| Colour spread | Higher beat = wider hue spread across newly spawned bubbles (more colour variety) |
| All bubbles' size per frame | A global pulse spring driven by beat and bass makes every bubble on screen swell in unison |
| Per-bubble beat flash | When beat > 0.5, each bubble gains a bright outer ring scaled by beat strength |
| Extra neon rings | Only visible when beat > 1.0 (requires Effect Intensity above Low); up to two extra expanding rings per bubble |
| Mid-frequency size modulation | Mid energy adds a small per-frame size boost to all visible bubbles |

**Silence behaviour:** No new bubbles spawn. Existing bubbles continue rising with their individual velocities and drift with wobble. The global pulse spring decays to zero. Bubbles gradually float off the top of the screen until the pool empties.

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
