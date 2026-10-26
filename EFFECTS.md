# psysuals — Effects Reference (3.20.0)

There are 32 registered modes. Mandelbox and Morphogenesis are removed in 3.20.0.

All effects consume the same shared audio signals. They differ only in how they interpret those signals visually.

---

## Global audio signals

In addition to the raw normalized bands below, effects may read
`config.BASS_ENVELOPE`, `config.MID_ENVELOPE`, and `config.TREBLE_ENVELOPE`.
These values stay within `0.0 .. 6.0` and use separate attack/release time
constants for responsive transients and smooth sustained decay.

| Signal | Meaning | Source |
|--------|---------|--------|
| `waveform` | Latest raw mono PCM block (`1024` samples) | `sounddevice` callback |
| `fft` | Smoothed, log-scaled spectrum (`512` bins) | Blackman-windowed FFT + EMA |
| `beat` | Normalised raw beat impulse (`0.0 .. 3.0`); active effects receive this multiplied by the selected gain (up to `6.0`) | Spectral flux, optional tracker refinement, main-loop decay |
| `config.MID_ENERGY` | Normalised mid-band energy | Smoothed relative mid-band slice |
| `config.TREBLE_ENERGY` | Normalised treble energy | Smoothed relative treble-band slice |
| `config.BPM` | Live BPM estimate or tap-tempo override | Callback onset timing + optional `librosa` refinement |
| `config.IS_SILENT` | True while silence gate is active | RMS + FFT hysteresis in main loop |

`effect_gain` scales the beat response passed to the active foreground effect
and, when enabled, the background effect. Its current default is `0.7`, and
changing modes resets it back to that default.

## Hardware Acceleration (ModernGL)

The app supports hardware acceleration via the `--gl` flag. When enabled:
- The app uses ModernGL to offload heavy pixel calculations to the GPU.
- Effects like `Plasma` switch to a GLSL shader-based implementation.
- Other effects continue to draw to a transparent surface that is composited over the GL viewport each frame.
- A fallback system ensures that all effects remain functional even if ModernGL is not installed or the GPU path is disabled.

If no audio input stream is available, the app stays up and the effects simply run against silence until a device is selected.

When `config.IS_SILENT` is true, the runtime suppresses beat spikes and feeds effects low idle motion floors instead of hard zero. That keeps visuals alive during gaps before and after tracks without amplifying the noise floor into fake hits.

Standardized audio energy parameters used across all effects:

| Signal | Source | Typical role |
|--------|--------|--------------|
| `beat` / `bass` | `beat` parameter | kick, sub, pulse, large-scale motion |
| `mid` | `config.MID_ENERGY` | synth body, geometry drift, colour motion, flight speed |
| `high` / `treble` | `config.TREBLE_ENERGY` | shimmer, edges, sparkles, fine detail, high-frequency jitter |

---

## Display profiles and timing

`--visual-profile auto|laptop|tv` selects the viewing profile; `V` cycles it live
and the selection persists. Profiles control motion, light accents and Lattice
density separately from internal render resolution. Auto guesses from output
width; explicit Laptop/TV overrides work at any resolution.

The fourteen retuned effects use `config.FRAME_DT` (seconds, default 1/60) for motion,
exponential audio smoothing and trail decay. Brightness follows faster audio
accents while cameras and geometry follow a slower envelope. Frames longer than
0.1 seconds are capped to avoid jumps after a pause. Other effects retain their
existing timing. CPU/GL compositing uses a display-sized canvas; each effect owns
its internal simulation resolution. Fullscreen field shaders use the actual GL
framebuffer size, including HiDPI outputs.

## Mode list

The `#` column is the human-readable ordinal. The `--mode` command-line
option uses the zero-based registry index instead (`0` for Yantra through `31`
for Waterfall); the number keys directly select only modes 1–9.

| # | Key | Mode | Summary |
|---|-----|------|---------|
| 1 | `1` | Yantra | Sacred-geometry mandala with springy concentric rings |
| 2 | `2` | Cube | Dual wireframe cubes with orbiting satellites |
| 3 | `3` | TriFlux | Triangle mosaic wall with popping foreground tiles |
| 4 | `4` | Lissajous | 3-D knot with neon trail and BPM-sensitive motion |
| 5 | `5` | Tunnel | Curving polygon tube rushing toward the camera |
| 6 | `6` | Corridor | Neon rounded-rectangle corridor with sparks |
| 7 | `7` | Nova | Waveform kaleidoscope with layered symmetry |
| 8 | `8` | Spiral | Multi-arm helix vortex with cross-ring links |
| 9 | `9` | Bubbles | Rising translucent bubbles with global pulse swells |
| 10 | `←` / `→` | Plasma | Full-screen sine-interference plasma |
| 11 | `←` / `→` | Branches | Fractal lightning tree with audio-jittered angles |
| 12 | `←` / `→` | Butterflies | Emergent butterflies that form audio-reactive dancing pairs |
| 13 | `←` / `→` | FlowField | 8 000+ particles (4 000+ in low-spec) surfing a shifting vector field |
| 14 | `←` / `→` | Fireworks | Feedback zooming tunnel with fireworks |
| 15 | `←` / `→` | Aurora | Parallel Northern Lights ribbons with additive glow |
| 16 | `←` / `→` | Lattice | Crystal grid with zoom feedback and peak-normalization |
| 17 | `←` / `→` | Mycelium | Multi-colony psychedelic hyphae with swirling spores |
| 18 | `←` / `→` | Magnetar | Magnetic-dipole particle field with equatorial shockwaves |
| 19 | `←` / `→` | SlimeMold | Physarum trail simulation with self-organising veins |
| 20 | `←` / `→` | Mobius | Rotating wireframe strip without interior cross-lines |
| 21 | `←` / `→` | Chromatic | RGB raindrop ripples with wavy prismatic outlines |
| 22 | `←` / `→` | Persistence | Long-trail polygon moiré mandala |
| 23 | `←` / `→` | Synapse | Neural graph with bounded travelling light pulses |
| 24 | `←` / `→` | Heartbeat | Centre-out shockwave rings morphing circle to polygon |
| 25 | `←` / `→` | Hyperbolic | Stereographic tiling on a rotating 3-D sphere |
| 26 | `←` / `→` | LiquidLight | Audio-injected fluorescent fluid |
| 27 | `←` / `→` | Cymatica | Chladni-like nodal plates with glowing sand |
| 28 | `←` / `→` | Phason | Quasiperiodic interference bloom with 5–11 waves |
| 29 | `←` / `→` | Tesseract | Rotating 4-D crystal projections |
| 30 | `←` / `→` | Ferrofluid | Metallic magnetic droplets |
| 31 | `←` / `→` | Spectrum | Log-spaced analyser bars with waveform overlay |
| 32 | `←` / `→` | Waterfall | Scrolling time-frequency spectrogram |

`Spectrum` and `Waterfall` are intentionally the final two registry entries.

---

## 1. Yantra

Concentric polygon rings rotate in alternating directions around a bright central hub, with spokes and ring webs building a mandala-like lattice.

- Audio: beat (bass) drives the center dot expansion and ring spring-physics pulses; MID_ENERGY (mids) alternates ring rotations and drives spoke angular wobble; TREBLE_ENERGY (treble) adds high-frequency spoke/vertex jitters and modulates spoke/ring line thicknesses.
- Visual notes: long-lived geometry, strong symmetry, low camera motion, good for steady psytrance passages.

## 2. Cube

Two nested wireframe cubes rotate in 3-D while a pair of small satellite cubes orbit around them on separate trails.

- Audio: smoothed bass and band energies gently change bounded rotation and scale; faster accents change colour/brightness. Satellites orbit slowly and all projected geometry fits inside the viewport.
- Visual notes: the main app uses the CPU implementation; satellites keep their own persistent trail fade.

## 3. TriFlux

An equilateral triangle wall fills the screen with rainbow wireframe tiles. Selected tiles rise into the foreground, spin inside a screen margin, and spring back into place.

- Audio: rising beats activate one foreground tile with a minimum interval; MID_ENERGY scales background sweep speeds and adds to active-tile scale/brightness; TREBLE_ENERGY accelerates active-tile spin rates and shimmers outline widths.
- Visual notes: triangle outlines are antialiased at all widths; motion/lifetimes use elapsed time, active tiles survive resizing, and two independent sweep bands cross the wall.

## 4. Lissajous

A neon 3-D knot traces a dense trail with threefold rotational symmetry and a glowing head point.

- Audio: fixed 3:2:5 frequency ratios retain a continuous knot; beat gently drives spring expansion and rotation, treble expands glow halos, and live BPM scales knot travel speed.
- Visual notes: motion also reacts to live BPM, so faster tracks tighten and accelerate the knot. Rotation damping is tighter so the knot settles smoothly after beats.

## 5. Tunnel

A first-person ride through a curving polygon tube, with incoming triangles flying toward the viewer from the far end.

- Audio: slow audio envelopes make small changes to forward speed, with quicker light accents. Camera curvature is independent of transients, and ring decoration follows stable ring identities instead of changing on depth sorting.
- Visual notes: the tube bends continuously, smoothed audio gently changes travel speed and ring brightness, and the triangle count is kept lean (max 30 live) so the mid-tunnel stays uncluttered.

## 6. Corridor

Rounded neon frames form a corridor that breathes toward the camera while sparks streak on a separate persistent layer above the geometry.

- Audio: beat drives frame speed and brightness; MID_ENERGY scales camera path sways and frame border inflates; TREBLE_ENERGY drives spark spawn rates, spark sizes, and brightness levels.
- Visual notes: the dedicated spark layer keeps the foreground trails readable instead of smearing them into the walls.

## 7. Nova

Waveform data is mirrored into a sevenfold kaleidoscope across four independently rotating layers.

- Audio: layers are driven by beat, MID_ENERGY, and TREBLE_ENERGY; MID_ENERGY scales layer rotation velocities and outer/inner shapes; TREBLE_ENERGY drives outer-ring radial triangle jitters and waveform line thicknesses.
- Visual notes: this mode is more waveform-shaped than FFT-shaped, so it shows snare and melodic contours clearly.

## 8. Spiral

Six luminous arms sweep toward the viewer as a helix, with cross-ring links tying the structure together into a rotating neon vortex.

- Audio: low FFT energy and beat impulses drive helix travel speed; FFT bins sampled along the depth of each arm shape its radius; beat impulses drive spring expansion and the nearest-point flare. Spiral does not apply separate MID_ENERGY or TREBLE_ENERGY geometry layers.
- Visual notes: colour drift is more aggressive than most modes, which keeps long sessions from feeling static.

## 9. Bubbles

Hundreds of glowing translucent bubbles drift upward with layered halos, reflective highlights, and beat-synchronised global swelling.

- Audio: beat (bass) drives basic bubble scale pulsing, sizes, and mega-bubbles; MID_ENERGY speeds up ascent rates; TREBLE_ENERGY triggers additional bubble spawns and adds horizontal coordinate jitter (wobbles).
- Visual notes: strong beats can trigger exaggerated bloom and larger bubble events, making kicks feel like pressure waves.

## 10. Plasma

Seven sine-wave fields interfere into a full-screen psychedelic texture. When running with `--gl`, this effect uses a GLSL fragment shader for per-pixel hardware acceleration.

- Audio: smoothed audio changes slow phase travel, restrained density/warp and colour; brighter beat accents do not abruptly deform the field. CPU and GL use the same restrained time progression.
- Visual notes: the field combines a bounded domain warp with seven-wave quasiperiodic interference. This is the primary effect that takes advantage of the ModernGL path; a reduced-resolution CPU fallback is automatically used if GL is not available.

## 11. Branches

A recursive lightning tree radiates from the screen centre with multiple branching levels and short trunk stubs, so the structure explodes outward immediately.

- Audio: beat (bass) drives trunk scale and extra arm counts; MID_ENERGY subtly drives branch sway angles and spreads; TREBLE_ENERGY adds modest high-frequency lightning jitter and small branch length decay ratios.
- Visual notes: the tree rotates slowly as a whole. Mid and treble reactivity is deliberately moderate so the structure stays readable even at high energy levels.

## 12. Butterflies

Butterflies emerge from cocoon motes within the visible screen area. The bounded
flock forms nearby, compatible pairs; partners orbit and mirror one another's
wing beats, then dissolve the pairing so new relationships can form. The
simulation supports up to 12 butterflies and six pairs, with seeded randomness
for repeatable runs.

- Audio: emergence is age-based; bass accelerates free flight, mids affect flight speed and paired orbit motion, treble controls wing shimmer and breakup probability, and beat pulses affect flight and wing cadence.
- Visual notes: new cocoons start inside a screen margin and emerge after 19 updates. Body size follows the internal canvas, and paired orbits remain within its bounds. Trails are managed on a persistent reduced-resolution internal surface and scaled into the destination each frame. Pair matching uses a bounded nearest-neighbour search; resize-safe surfaces and idempotent cleanup preserve the normal CPU/Pygame effect contract.

## 13. FlowField

Eight thousand+ particles surf a continuously changing multi-layer vector field (scaling dynamically up to 100,000 on high-resolution displays) and paint the path they take directly into a persistent trail surface.

- Audio: beat (bass) drives particle speeds and field angle scales; MID_ENERGY accelerates field evolution; TREBLE_ENERGY triggers a vectorized center-outward push on particle positions during transients.
- Visual notes: particles wrap cleanly across the full viewport, so the flow reaches the screen edges without a dark border.

## 14. Fireworks

The previous frame is zoomed and darkened into a tunnel while fireworks launch upward and explode into glowing embers. A small semi-Lagrangian velocity field advects the feedback trail into smoke-like curls without changing the rocket or ember population caps.

- Audio: beat (bass) launches rockets and drives zoom scaling; bass injects stronger trail vortices; MID_ENERGY steers the curl; TREBLE_ENERGY increases rocket explosion ember counts, launch-trail widths, velocities, and dye separation; beat bursts briefly increase vorticity.
- Visual notes: auto-launch timing is also tied to `config.EFFECT_GAIN`, so quieter gain settings create more background rockets between beats. Beat bursts trigger on rising edges, while rocket and ember populations remain explicitly capped.
- Performance: trail advection is one fixed, clamped step on the existing reduced-resolution surface, keeping the CPU path within the normal frame budget without per-particle field allocations. Feedback remains on the CPU, avoiding GPU readback stalls during GL composition; rocket/ember coordinates and the feedback trail survive resizing.

## 15. Aurora

Five translucent ribbon curtains sweep across the screen as thick parallel bands with additive overlap, bright edge lines, and a cyan-to-violet bias.

- Audio: beat (bass) controls ribbon amplitude and flash triggers; MID_ENERGY drives ribbon thickness and hue shifts; TREBLE_ENERGY drives curtain shimmer phase velocities.
- Visual notes: the effect uses harmonic ribbon stacks rather than particles, so it feels broad and atmospheric.

## 16. Lattice

A crystal grid of nodes and beams maps columns across FFT bins and normalizes
column peaks for balanced activity. Laptop uses 22×14 nodes and TV uses 14×9,
independently of GL render scale. Auto chooses the profile by output width;
explicit profiles override it at any resolution. Slow zoom feedback and a
bounded shockwave reveal the grid without changing its Euclidean spacing.

- Audio: column activity is peak-normalized; beat drives shockwave expansion and subtle whole-grid scaling; MID_ENERGY drives feedback zoom, hue speed, column brightness, and shockwave speed; TREBLE_ENERGY shimmers node size, core brightness, beam width, and grid scale.
- Visual notes: nodes retain Euclidean grid spacing at all energy levels. Node hue has a radial offset, keeping the centre cooler while corners drift more violet. Lattice no longer exposes the Hyperbolic effect's warp parameter, so switches between those modes clear the previous image.

## 17. Mycelium

A bioluminescent fungal colony simulation featuring wandering colony cores with rotating satellite node rings, glowing organic root structures (hyphae), and dynamic swirling spore particles that drift according to a gravity/orbital vector field.

- Audio: bass drives growth speed, core pulsing, and core spore releases; mid controls swirl field motion and branching spread; treble/high frequencies accelerate spore flutter, spore count, and glowing trail brightness; beat seeds new bursts across multiple colony cores.
- Visual notes: growing hyphae tips dynamically shed glowing spores, while a bounded low-resolution reaction halo blooms around active cores and tips. The cores wander smoothly around seeded anchors and carry rotating rings of satellite nodes; hyphae draw with a soft neon bioluminescent glow.

## 18. Magnetar

Thousands of particles ride the field lines of an analytically computed rotating magnetic dipole. Particles accumulate near the poles and trace luminous flux lines. Beat fires a shockwave that scatters particles outward from the magnetic equator.

- Audio: bass drives field rotation speed and particle velocity; mid shifts the dipole tilt angle; treble shifts particle colour saturation; beat triggers the equatorial shockwave.
- Visual notes: rendered at reduced resolution for performance, with a bounded glossy magnetic-potential contour layer behind the particles. Particle density still scales higher on large displays so the field does not thin out on TVs.

## 19. SlimeMold

A Physarum-inspired multi-agent simulation: thousands of agents deposit chemical trail, sense three directions, and steer toward the strongest signal. The self-organising vein network pulses and reforms in real time.

- Audio: bass controls agent speed and trail deposit strength; mid sharpens gradient sensitivity; treble widens the sensor angle; beat teleports a fraction of agents back toward centre.
- Visual notes: runs at a finer internal resolution with smooth scaling, reducing enlarged pixels on large displays.

- TV detail: at least 85% of native width and height is retained under lower quality tiers. Resizing resamples the floating-point chemical trail and rescales agents without resetting the network.

## 20. Mobius

A 3-D Möbius strip rendered as a wireframe with perspective projection. The visible structure is carried by the latitude loops alone, leaving the band clean instead of cutting it up with interior cross-lines.

- Audio: bass controls rotation speed; mid controls roll/tilt and fourth-coordinate rotation; treble adds further rotation speed; beat triggers the shiver.
- Visual notes: hue varies continuously along the strip width, highlighting the non-orientable topology without the straight interior wires.

## 21. Chromatic

Prismatic raindrop ripples expand from beat origins as wavy RGB-separated outlines rather than plain circles, producing chromatic interference that remains readable at large output sizes.

- Audio: bass controls expansion speed and ring intensity; mid adds speed/intensity to automatic idle droplets and slightly advances their wave phase; treble increases the RGB split and wave deformation; beat rising edges spawn new droplets.
- Visual notes: the rings are intentionally irregular and fluid, so the effect reads as chromatic rainfall instead of concentric circles. At most five ripple objects coexist, each drawn as three separated RGB outlines.

## 22. Persistence

Multiple nested polygons rotate in 3D space on non-coplanar X, Y, and Z axes at different speeds. The geometric figures target roughly 80% of the render height. Projected through a perspective camera with depth-based thickness and brightness shading, their counter-rotating ghost images accumulate under long persistence to form 3D holographic moiré mandalas.

- Audio: slow audio envelopes gently adjust rigid X/Y/Z rotation; five shapes stay present at fixed relative sizes; quick bass and treble accents brighten their edges. Beats can reverse one rotation axis at most every two seconds, easing through the direction change; audio also drives bounded speed and colour offsets.
- Visual notes: lines closer to the camera are drawn thicker and brighter, creating realistic 3D depth cueing as the polygons spin in perspective. Rigid 3-D rotation preserves each solid without fourth-coordinate deformation.

## 23. Synapse

An evolving neural graph fades its oldest point over one second and grows two new points near active parents roughly every three seconds, growing to a bounded capacity while its nodes wander around loose anchors. Each point is rewired to nearby neighbours as the topology changes. Signals travel visibly along edges as glowing pulses; arriving signals fire destination nodes which cascade further. Beat triggers multi-node cascades.

- Audio: bass drives signal propagation speed and node glow intensity; mid controls the baseline auto-fire rate; treble increases signal color saturation; beat fires bounded multi-node cascades.
- Visual notes: the live signal pool is capped, node fan-out is sampled, and topology changes preserve signals on surviving edges and discard stale edge references, keeping the effect stable while the graph remains alive.

## 24. Heartbeat

Rhythmic pressure waves expand from the screen centre. Each beat spawns concentric rings whose outline morphs between a circle (gentle bass) and a polygon (heavy bass). Multiple overlapping rings create moiré interference patterns.

- Audio: bass drives wave speed and polygon morphing (low bass = circle, high bass = triangle) and excites the plate overlay; mid modulates propagation speed and nodal-mode morphing; treble adds shimmer; beat spawns new ring(s).
- Visual notes: strong beats at high bass produce stark polygonal shockwaves over a fading Chladni-like cymatics pattern; gentle beats produce soft circular ripples. The analytic plate is cached at reduced resolution and uses no runtime eigensolver.

## 25. Hyperbolic

Connected pentagon and hexagon tiles form the dual of a subdivided icosahedron. The sphere rotates in true 3-D before perspective projection, over a faint shaded body. Each segment independently rises, tilts around an edge and sinks into the surface; shaded side walls and moving neon colour waves reveal the depth.

- Audio: smoothed mids accelerate rotation and travelling displacement waves; bass lifts selected segments and faster treble accents brighten edges. Segment heights are bounded and eased over elapsed time.
- Visual notes: topology is cached and adjacent cells share their edges. Back faces are hidden and polygons crossing the perspective horizon are clipped before drawing.

## 26. LiquidLight

Three fluorescent dye channels are advected through a bounded low-resolution velocity field, producing coherent liquid-light curls and fine tendrils. The field is smoothly scaled into the destination surface.

- Audio: bass injects paired counter-rotating vortices; MID_ENERGY steers the main injection orbit and adds warm dye; TREBLE_ENERGY adds restrained high-frequency wisps; silence retains gentle circulation.
- Visual notes: semi-Lagrangian advection uses fixed substeps and reusable scratch arrays, with explicit velocity and density caps.

## 27. Cymatica

Analytic Chladni-like mode pairs create glowing nodal lines on a morphing virtual plate. A bounded sand layer drifts toward quiet regions, making the geometry appear to crystallize directly from the audio.

- Audio: six FFT regions contribute slowly smoothed mode weights, while smoothed treble adjusts line contrast and colour. Sand descends the actual plate amplitude gradient independently of display resolution.
- Visual notes: the mode set is curated and evaluated on a cached low-resolution grid, with no runtime eigensolver and safe handling for empty FFT input.

## 28. Phason

Seven slowly animated plane waves interfere into a quasiperiodic star field. Slow phase shifts rearrange bright constellations without simply translating the image, producing a crystalline bloom.

- Audio: slow envelopes control bounded phase speed, a small pulse and gentle colour shifts. Wave count remains fixed to prevent abrupt geometry switches.
- Visual notes: coordinate and phase arrays are cached, wave count stays at seven, and the field is rendered at reduced resolution before scaling.

## 29. Tesseract

A curated tesseract, 24-cell, and deliberately thinned 600-cell-like vertex cloud rotate in two four-dimensional planes before stereographic projection into the display. Bounded edge echoes make the structure turn inside out.

- Audio: bass pulses fourth-coordinate perspective and advances presets on rising edges; bass, MID_ENERGY, and TREBLE_ENERGY independently accelerate rotation in the XW and YZ planes; treble also colors vertices by fourth-coordinate depth.
- Visual notes: vertex and edge sets are explicit and capped, near-projection singularities are clipped, and no general polytope generator is used.

## 30. Ferrofluid

A connected magnetic liquid surface supports a triangular lattice of sharp peaks. Analytic surface normals, soft lighting and specular reflections reveal metallic crowns around slowly moving poles.

- Audio: smoothed bass broadens the droplets; mids accelerate their slow rotation; treble brightens reflections.
- Visual notes: CPU and optional fullscreen GL paths use the same Gaussian liquid envelope, magnetic peak lattice and lighting model, with no beat inversion.


## 31. Spectrum

Classic analyser bars are spaced logarithmically, with peak markers and a waveform line layered over the centre of the screen.

- Audio: log-spaced FFT bands control bar height; beat drives bar smoothing decay; MID_ENERGY shifts base hue; TREBLE_ENERGY modulates waveform overlay line thickness and vertical amplitude.
- Visual notes: this is the plainest diagnostic mode and is useful for checking input levels quickly.

## 32. Waterfall

A scrolling spectrogram stores recent history as rows, with hue encoding frequency and brightness encoding energy.

- Audio: FFT energy fills scrolling rows; beat/mids add row brightness boosts; TREBLE_ENERGY adds a heat-shimmer jitter/offset to newly scrolled rows.
- Visual notes: because it accumulates history, this is the best mode for spotting rhythmic patterns and sustained tonal bands.

---

## Navigation

| Key | Action |
|-----|--------|
| `1` – `9` | Jump directly to modes 1–9 |
| `←` / `→` | Previous / next mode across all 32 modes |
| `Space` or mouse click | Next mode |
| `↑` / `↓` | Adjust intensity for the current mode (`0.0 .. 2.0`), or adjust FlowField particles by 2,000 |
| `Tab` | Open the settings pane |
| `P` / `Shift+P` | Save / load presets |
| `D` | Open the device picker, or cycle the shared secondary-display mode while span mode is active |
| `F` | Toggle fullscreen |
| `H` / `Shift+H` | Toggle HUD / change HUD detail |
| `Q` / `Esc` | Quit |

Mode changes reset intensity to the default `0.7`. Loading a preset can restore a saved custom intensity on purpose.
