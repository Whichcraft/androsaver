# Visualizer Music Reactivity

How each effect responds to audio. All effects receive three frequency bands and a beat signal derived from the system audio:

| Signal | Source | Typical range |
|--------|--------|---------------|
| **Bass** | FFT bins 0–6 (~20–250 Hz) | 0.0 – 1.0 |
| **Mid** | FFT bins 6–30 (~250 Hz – 2 kHz) | 0.0 – 1.0 |
| **High** | FFT bins 30–512 (~2 kHz – 20 kHz) | 0.0 – 1.0 |
| **Beat** | Onset strength (scaled by the Effect Intensity setting) | 0.0 – 2.0 |
| **Gain** | Current beatGain multiplier (Effect Intensity setting value) | 0.0 – 2.0 |

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

**What it looks like:** First-person ride through a curved neon tube with rotating star polygons at each ring centre. Triangles spawn deep in the tube and fly toward the viewer.

**Music reactivity:**

| Visual element | Reacts to |
|----------------|-----------|
| Triangle spawn rate | `(bass × 2.0 + beat × 3.0).toInt()` per frame; beat threshold 0.5 |
| Triangle spawn depth | Far third only (z 0.80–0.98); never spawns near the camera |
| Triangle size | `(0.45 + random × 0.65) × (1.0 + bass × 1.5)` — larger at higher bass |
| Triangle brightness | Scales with nearness and `beat × nearT × 0.50` |
| Live triangle cap | 50 — older triangles are dropped when cap is exceeded |
| Ring brightness | `0.06 + nearT × 0.70 + fft[fi] × 0.20 + beat × nearT × 0.50` |
| Scroll speed | `dt = 0.03 + bass × 0.09 + beat × 0.18` |
| Star polygon rotation | Constant per-ring angular velocity; direction alternates per ring |

**Tunnel walls:** Constant star-polygon geometry; brightness scales with distance and local FFT bin.

**Silence behaviour:** No new triangles spawn. Existing ones continue toward the camera. Rings scroll slowly.

---

## TriFlux

**What it looks like:** A mosaic of triangles filling the screen. All triangles have animated rainbow edges. On strong bass beats, individual tiles pop to large size, bounce off screen edges, and spring back.

**Music reactivity:**

| Visual element | Reacts to |
|----------------|-----------|
| Edge hue cycling | Constant drift + beat accelerates hue |
| Diagonal sweep bands | Two independent rainbow sweeps always glide across the grid regardless of audio |
| Tile pop size | Bass: 4.5–8.5× normal size, up to 3 tiles simultaneously |
| Tile pop trigger | Bass crossing threshold; rising-edge detection |
| Tile bounce | Popped tiles bounce off screen edges with velocity reversal |
| Tile spring return | Spring force pulls popped tile back to home position |

**Silence behaviour:** Edge rainbow drift and diagonal sweeps continue. No tile pops.

---

## Corridor

**What it looks like:** First-person ride through a neon rainbow corridor of rounded rectangles. Glowing sparks streak toward the camera along a curving path.

**Music reactivity:**

| Visual element | Reacts to |
|----------------|-----------|
| Scroll speed | Bass drives corridor frame scroll speed |
| Frame brightness (near) | Beat flares the nearest frames brighter |
| Spark spawn rate | `bass × 5.0` sparks per frame (raised from psysuals `bass × 1.2` to compensate for lower Android beat signal) |
| Spark size | Bass makes sparks larger at spawn |
| Spark trail | 25-frame ring buffer replayed with additive blend + linearly decaying alpha |
| Hue cycling | Continuous slow drift |

**Silence behaviour:** Corridor scrolls slowly. Very few sparks. Frames are dim.

---

## Branches

**What it looks like:** Nine neon arms radiate from the centre and split recursively to depth 7. Each segment is drawn as a wide dim halo + white-hot bright core for neon flare.

**Music reactivity:**

| Visual element | Reacts to |
|----------------|-----------|
| Trunk length | Bass drives trunk base length |
| Branch angle jitter | Mid frequencies modulate all branch angles via 3 overlapping sine fields |
| Beat burst | Beat fires extra arm forks with a brightness spike |
| Base rotation | Slow constant rotation of all arms together |
| Branch length at each depth | Scales by a fixed ratio; bass influences only the trunk |

**Silence behaviour:** Arms contract to minimal length. Very slow residual jitter from the sine fields.

---

## Butterflies

**What it looks like:** Up to 3 pairs of neon butterflies. Each pair performs a mutual pursuit spiral that tightens over its lifetime, then the pair wanders off-screen.

**Music reactivity:**

| Visual element | Reacts to |
|----------------|-----------|
| Wing flap speed | Bass — both butterflies in a pair flap faster on bass hits |
| Wing sync | When the two butterflies are close, their wing phase converges |
| Beat sparkles | Beat fires sparkle particles around both butterflies |
| Orbit angular speed | `0.012 + beat × 0.020 + 0.003 × (1 − orbitR/240)` — tightens as orbit shrinks |
| Orbit radius | Starts at 240 px, decrements 0.06 px/frame to 40 px minimum |
| Pair lifetime | Random; new pair replaces it after it wanders off-screen |

**Silence behaviour:** Butterflies drift slowly with minimal wing motion. Orbit tightens at base speed.

---

## FlowField

**What it looks like:** 4 000 particles riding a continuously-evolving 3-layer sine/cosine noise field, painting rainbow trails on a very slow fade background.

**Music reactivity:**

| Visual element | Reacts to |
|----------------|-----------|
| Field intensity + particle speed | Bass warps field angle amplitude and particle step size |
| Bass gravity | `(centre − position) × bass × 0.0018` per axis — particles converge toward centre on kick drums |
| Treble scatter | `Random(−1,1) × treble × 3.2` per axis — hi-hats push particles in random directions |
| Phase jump | Beat fires an instant large phase shift that reshapes all flow lines |
| Trail persistence | Fixed `fadeBlack(8/255)` ≈ 40-frame persistence |
| Hue | Particle hue derived from its angle in the field; shifts continuously |

**Silence behaviour:** Particles drift gently along the base field. No gravity, no scatter.

---

## Vortex

**What it looks like:** Firework rockets arc upward under gravity, then explode into 80–120 glowing embers that drift and fade.

**Music reactivity:**

| Visual element | Reacts to |
|----------------|-----------|
| Beat rockets | Beat > 0.6: `1 + (beat × 2).toInt()` extra rockets fired immediately |
| Auto-launch interval | `BASE_INTERVAL(40) × gain`, clamped 20–200 frames; lower intensity → more frequent rockets |
| Rocket velocity | Fixed random range (vy −15 to −10, vx ±2); not audio-reactive |
| Ember count per explosion | 80–120 randomly |
| Ember speed | Gaussian-approximated random (range ~3.6–9.0) |
| Ember lifetime | 50–100 frames randomly |
| Hue cycling | Slow drift + `bass × 0.002` per frame |
| Trail persistence | `fadeBlack(15/255)` ≈ 17-frame persistence |

**Silence behaviour:** Auto-launch continues at `BASE_INTERVAL × gain` frames. Rockets and embers behave identically regardless of audio except for beat-triggered extras.

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
| **Glow brightness** | High (treble) energy brightens the inner glow pass: `l1Bright = min(0.90 + beat×0.08 + high×0.14, 0.98)` — hi-hats make the knot shimmer whiter |
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
| **Bass flash** | When bass > 0.65: `bassFlash` spikes to `bass × 2.8`, decays 0.18/frame over ~10 frames; inflates all rendered radii by `bassFlash × 0.45` |
| **Mega-bubbles** | When beat > 0.7: 1–3 mega-bubbles spawn at `r × (2.2 + bass × 2.0)` radius with `vy × 1.4` rise speed |

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

## Aurora

**What it looks like:** Five translucent sinusoidal ribbon curtains undulate horizontally across the screen in a slow-shifting Northern Lights palette. Ribbons are drawn with additive blend; overlapping curtains bloom together.

**Music reactivity:**

| Visual element | Reacts to |
|----------------|-----------|
| Ribbon amplitude | `H × (0.04 + bass × 0.12 + bloom × 0.07)` — bass billows the curtains |
| Shimmer speed | `1 + treble × 4 + beat × 1.5` — multiplies all harmonic phase velocities |
| Ribbon height / thickness | `H × (0.09 + mid × 0.05)` — mid thickens the ribbons |
| Beat bloom | Beat > 0.8 (rising edge): `bloom = 1.0`, hue nudged +0.10; bloom decays −0.03/frame |
| Glow intensity | `0.10 + bloom × 0.08` |
| Core intensity | `0.28 + bloom × 0.22 + bass × 0.08` |
| Edge line brightness | `min(0.80 + bloom × 0.18, 0.98)` |
| Hue drift | `+0.003 + mid × 0.002` per frame |
| Trail persistence | `fadeBlack(14/255)` ≈ 18-frame persistence |

**Silence behaviour:** Curtains continue to undulate at base shimmer speed (1.0). Amplitude collapses to minimum (0.04 × H). No bloom. Hue drifts at 0.003/frame.

---

## Lattice

**What it looks like:** A 14×9 crystal grid of glowing nodes connected by double-stroke beam lines. Left columns respond to bass, right columns to treble. On every strong beat, a shockwave ring expands from the centre and causes nearby nodes to flare white.

**Music reactivity:**

| Visual element | Reacts to |
|----------------|-----------|
| Node brightness | `fft[bin(col)] + 0.08 (idle floor)` — per-node FFT bin mapped left=bass, right=treble |
| Beam brightness | Average of two adjacent nodes' brightness; outer stroke at 0.22–0.38 L, inner at 0.48–0.80 L |
| Shockwave | Beat > 0.6 (rising edge): `shockR = 0`; ring expands at `6 × (1 + bass × 2 + beat × 0.8)` px/frame |
| Shock flare | Nodes within 22 px of wavefront: `shock = 1 − |dist − shockR| / 22`; flare radius `rCore + shock × 9`, lightness `0.88 + shock × 0.12` |
| Grid scale breath | `svel += (1 + bass × 0.04 − scale) × 0.18`; `svel *= 0.70`; scale clamped 0.90–1.12 |
| Hue drift | `+0.0025 + mid × 0.001` per frame; radial offset +0 (centre) to +0.55 (corner) |
| Node glow radius | `max(3, 5 + bright × 10)` px |
| Node core radius | `max(1, 2 + bright × 4)` px |
| Trail persistence | `fadeBlack(20/255)` ≈ 13-frame persistence |

**Silence behaviour:** Grid glows dimly at idle floor (0.08 per node). Scale stays near 1.0. Hue rotates slowly. No shockwave until next beat.

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
