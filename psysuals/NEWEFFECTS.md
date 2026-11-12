# Designing new psychedelic effects

Design new psysuals effects as living worlds made from colour, geometry and
music. Give each world a recognisable structure, an evolving visual rhythm and
a response to sound that can be seen immediately. A new effect should add a
new visual experience to the collection, with its own silhouette, colour logic
and movement.

This guide draws on the established effect collection, including the stronger
Hyperbolic bass response. Proposed directions below remain proposals; the
ChromaWeave and Mycelium briefs record the implemented modes.
See [EFFECTS.md](EFFECTS.md) for current behaviour and
[ARCHITECTURE.md](ARCHITECTURE.md) for implementation contracts.

## Learn from the existing effects

Use the existing effects as a vocabulary. Borrow a technique with a clear purpose,
then develop a new relationship between form, colour and music.

| Existing effects | Design lesson | Direction to explore |
| --- | --- | --- |
| Yantra, Nova, Spiral | Nested symmetry, mirrored signals and repeated arms make complex images readable. | Let a stable centre organise structures whose phases gradually diverge. |
| Cube, Lissajous, Mobius, Persistence, Tesseract | Rotation, projection and accumulated outlines reveal depth and unusual topology. | Give an unfamiliar object a clear silhouette, then reveal its interior through motion and colour. |
| Tunnel, Corridor | Repeated frames and perspective establish a convincing journey through space. | Let colours and local events travel through depth while the camera follows a smooth path. |
| Plasma, Phason, Cymatica | Overlapping fields generate intricate patterns from a small set of coherent rules. | Use interference, nodal boundaries and slowly changing phase relationships to create apparent complexity. |
| LiquidLight, Ferrofluid, SlimeMold | Flow, surface lighting and persistent trails give colour a material quality. | Make colour behave like dye, iridescent liquid or a growing chemical trace. |
| Branches, Mycelium, Synapse, Butterflies | Growth, propagation and interacting agents create events that can be followed over time. | Give luminous organisms or networks a visible life cycle and local musical responses. |
| FlowField, Magnetar, Fireworks | Many small elements become coherent when they share fields, forces or origins. | Make particles reveal an invisible structure, with controlled bursts that briefly disturb it. |
| Hyperbolic, TriFlux, Lattice | Repeated cells create a stable reference against which local movement becomes obvious. | Send pressure, light and colour through a surface whose cells respond individually. |
| Aurora, Bubbles, Chromatic, Heartbeat | Layered curtains, translucent bodies and expanding ripples give light a spatial rhythm. | Build broad atmosphere around readable pulses, overlaps and prismatic boundaries. |
| Spectrum, Waterfall | Frequency and time can directly organise a visual composition. | Distribute musical detail across space so different instruments occupy different regions. |

Choose one organising principle and one main transformation. For example, combine
Phason's interference logic with Hyperbolic's articulated surface to make waves
that travel across lifting cells. Define how those ideas interact before adding
particles, trails or another layer.

## Make colour part of the structure

Colour is a primary design system. Decide where colours come from, how they
relate to one another and what changes them.

Start with a dominant family: violet and magenta, cyan and electric blue, or
acid green and yellow. Give it a contrasting accent such as amber, red-orange
or hot pink. Allow the dominant family to evolve gradually. A full spectrum can
work beautifully when its progression follows geometry or wave phase.

Map colour to something meaningful:

- **Position:** hue travels around a ring, along a ribbon or through a spiral.
- **Depth:** distant layers become darker or cooler; nearby edges become clearer.
- **Field phase:** colour bands reveal constructive and destructive interference.
- **Age:** a fresh pulse has a bright core that cools into a coloured afterimage.
- **Material:** a dark body, saturated surface and contrasting reflection suggest
  liquid, crystal, glass or metal.
- **Musical region:** different frequency bands excite distinct areas or colour
  accents, following the spatial clarity of Spectrum and Waterfall.

Keep neighbouring colours related. Adjacent facets can share a base hue with
small offsets; opposing phases can use complementary hues. Interpolate hue
around its circular range so transitions remain intentional at the wrap point.
Use seeded randomness for variation within the palette and structure.

The existing [HSL helpers](effects/utils.py) provide a useful starting point:
hue wraps around the colour wheel, while saturation and lightness can be controlled
separately. HSL lightness is not perceptually uniform, so inspect actual renders;
the same numeric lightness does not make blue and yellow equally bright.

## Build luminous depth

Use several levels of light: a deep coloured background, a readable body, luminous
surfaces or trails, and a small number of brilliant accents. Near-black indigo,
blue-green or violet can carry atmosphere while preserving space between forms.

Learn from the contrast between Hyperbolic's shaded walls and bright caps,
Ferrofluid's reflections, Aurora's overlapping ribbons and Chromatic's separated
RGB outlines. These effects give colour an edge, thickness or apparent material.

Preserve saturated midtones during loud passages. Increasing every channel,
brightening every surface and adding long trails at once quickly removes colour
separation. Keep a brightness budget: choose which layer gets brighter and which
stays dark enough to frame it. Reserve white or near-white for small highlights.

Give trails a purpose. They can reveal a knot's path, a fluid's circulation or a
signal's history. Fade them far enough that new structure stays legible. Inspect
sustained output for accumulated grey, muddy colours and lingering old scenes.

## Create psychedelic movement

Make the image continually reveal new relationships. Slow global rotation can
carry a faster travelling wave; local cells can breathe at slightly different
phases; two symmetric layers can gradually slip past one another. Interference,
moiré, recursive growth and changing apparent depth can all emerge from a few
consistent rules.

Keep a stable reference that the viewer can follow: a centre, surface, flow,
network or travelling direction. Let large structures evolve smoothly while
small details carry quicker accents. Use deliberate changes in density and
complexity so the effect has spacious passages as well as rich ones.

The effect should remain alive in silence through gentle circulation, slow phase
drift or quiet growth. Idle movement establishes atmosphere; musical events must
produce a distinct change on top of it.

## Make music visibly matter

Assign different visual responsibilities to different signals. The mappings below
are starting points; each effect should choose roles that suit its identity.

| Musical input | Visual role | Existing reference |
| --- | --- | --- |
| Bass energy | Weight and pressure: lift, swell, expansion or a stronger flow. | Hyperbolic facets; LiquidLight vortices; Heartbeat rings. |
| Beat onset | A local event with a clear start and release. | TriFlux activation; Chromatic droplets; Synapse cascades. |
| Mids | Continuing development: twist, travel, branching or colour-boundary motion. | Mycelium's swirl; Hyperbolic's travelling waves and rotation. |
| Treble | Fine light and texture: edge shimmer, reflections, sparks or small ripples. | Ferrofluid reflections; Corridor sparks; Hyperbolic edges. |
| Waveform / FFT detail | Shape or spatial distribution that follows the sound. | Nova's waveform symmetry; Spectrum and Waterfall. |

Combine a slower response for large-scale motion with a faster response for light
and selected local deformation. The existing Effect._audio_response helper
separates those timescales. Scale motion with elapsed time, bound extreme inputs
and let transients release naturally. Give onset-driven spawning a cooldown and
an explicit capacity.

Hyperbolic provides an important design lesson: an effect can be technically
reactive while its musical response is hard to notice. Its stronger version
adds fast bass-driven lift and brightness over the slower swell. Judge the visible
result, not merely whether an audio value appears in a formula.

A short kick should produce an unmistakable local response without sending the
camera jumping or destroying the form. Sustained bass should reach a bounded,
rich state; treble should add detail without washing the scene white. Confirm
that a quiet passage lets the effect settle again.

## Possible new directions

These are proposals, not existing or scheduled effects.

- **Prismatic Bloom:** combine Yantra's radial organisation, Phason's interference
  and Ferrofluid's surface lighting. Layer translucent petals around a dark core;
  bass opens successive rings, mids move interference bands across them and
  treble adds cyan or gold edge reflections.
- **Opal Reef:** build on Mycelium's growth, SlimeMold's connected traces and
  LiquidLight's circulation. Branching coral-like forms carry violet-to-turquoise
  currents; beats release bounded luminous spores that briefly colour nearby flow.
- **Spectral Cathedral:** develop Tunnel's depth and Lattice's structural rhythm
  into a journey through stained-glass arches. Frequency regions illuminate
  different panels, bass sends pressure down the nave and treble traces fine
  prismatic seams. Keep the travel path smooth.
- **Chromatic Origami:** combine TriFlux's local activation, Hyperbolic's hinged
  surfaces and Chromatic's separated colour edges. A continuous sheet folds in
  travelling bands; bass lifts selected folds and releases coloured light beneath
  them while the underlying structure remains recognisable.
- **Neural Aurora:** connect Synapse's travelling signals with Aurora's broad
  ribbons. Visible pulses move between branching curtains; each arrival briefly
  changes local thickness and colour before the curtain returns to its slow flow.

Each proposal should earn its place through its own behaviour. Describe what
changes over thirty seconds, what a beat does and what distinguishes its colour
relationships from the reference effects.

## Write a concrete brief before implementation

### ChromaWeave — implemented brief

- **Identity:** eightfold iridescent ribbons weave through nested breathing
  petals around a dark central rosette. Three independently drifting fields
  reveal new crossings and coloured pockets over a thirty-second passage.
- **References:** Yantra's radial organisation, Phason's analytic interference
  and Aurora's layered light, combined into a continuous woven surface.
- **Colour:** violet/magenta bodies, cyan and acid-green seams; hue follows
  radius, folded position and interference phase. Dark gaps keep overlapping
  layers readable; tiny treble glints carry the brightest accents.
- **Movement:** slow rotation and phase drift continue in silence. The
  eightfold fold stays recognisable as ribbons slide through one another.
- **Music:** fast bass light opens and brightens the weave; slower bass adds
  weight to the swell. Mids change travelling interference; treble adds small
  cyan sparkles. Shared bounded envelopes provide attack and natural release.
- **Composition:** a quiet centre anchors a screen-filling pattern. The
  aspect-correct field preserves its proportions in portrait and landscape;
  the TV profile strengthens accents while retaining gentle global motion.
- **Proof:** render silence, a short kick, isolated mids/treble and sustained
  audio; inspect both CPU/GPU output, colour separation and centre contrast.
  Desktop uses a full-size fragment shader or row-tiled NumPy fallback with
  a 640x360-equivalent sample budget and smooth scaling. Android's separate
  full-resolution GLES2 counterpart follows the same field equations.

### Mycelium — implemented brief

- **Identity:** a bioluminescent hyphal colony. Three fruiting bodies on a
  deep indigo ground grow an organic violet-magenta web that curves, branches
  and tapers, while cyan light packets travel the filaments and hand over to
  another filament at the junctions; amber spores drift and take root.
- **References:** Branches' recursive taper, Synapse's travelling signals and
  Chromatic's separated bright accents, grown as one persistent network
  instead of a sampled mesh.
- **Colour:** dominant violet-magenta filaments inside a narrow hue band that
  drifts slowly; cyan-white packets are the only cool accent and amber
  sparks and spores are rare warm highlights. Filaments fade with age, the
  ground stays near-black indigo and only packets and body cores reach
  near-white. A restrained substrate bloom supports the web instead of
  covering it.
- **Movement:** filaments grow outward from the bodies along a drifting field
  with a tangential swirl, paced per tip so the colony renews itself instead
  of churning; packets hand over at junctions and sometimes branch; spores
  drift with the field. The colony keeps a slow heartbeat of its own, so
  light travels even in silence.
- **Music:** bass (beat) lengthens the growth step, lights the bodies, sends
  a packet wave out of every body, sheds spores and rings the bodies; mids
  raise the swirl, the branching probability and the packet speed; treble
  adds tip sparks, spore release and shimmer. Onsets release and ring, with
  bounded populations and a cooldown.
- **Composition:** the three bodies anchor the eye, the web grows away from
  them with filaments tapering from thick at the bodies to fine at the
  frontier, and the packet traffic keeps a clear direction of reading on a
  laptop and from across a room.
- **Proof:** render silence, a short kick, mid-only and treble-only input and
  sustained full-band audio; inspect the frame time at 1920x1080 and
  3840x2160, the bounded filament, tip, packet and spore populations, the
  connected share of the alive nodes and the tapering of the drawn widths.

Describe a proposed effect using this short template:

1. **Visual identity:** one sentence naming the central form and its distinctive
   transformation.
2. **Existing references:** two or three effects and the specific technique
   borrowed from each.
3. **Colour system:** dominant family, contrasting accents, spatial hue mapping,
   shadow treatment and highlight budget.
4. **Movement:** global evolution, local behaviour, depth cues and idle state.
5. **Music mapping:** an observable role for bass, mids, treble and onsets, with
   response/release behaviour and limits.
6. **Composition:** where the eye rests, how density changes, and how the form
   remains legible on a laptop and from across a room.
7. **Proof:** the silent, isolated-pulse and sustained-input renders that will
   demonstrate the intended result.

Implement within the existing [effect contract](ARCHITECTURE.md): instance-owned
state and RNG, explicit EffectOptions/FrameContext inputs, and _draw/_draw_frame
hooks beneath the public adapters. Keep populations and feedback bounded,
preserve supported simulation state on resize, and handle resource cleanup.
These are implementation boundaries; the visual brief should remain readable
without knowing those APIs. AndroSaver's native Kotlin/GLES backport is separate
from implementing a Python effect here.

## Judge the result in motion

Compare identical seeds and timing with silent input, isolated bass kicks,
mid-only and treble-only signals, and sustained full-band energy. Verify that
the mappings can actually be distinguished. Check both an individual pulse and
the accumulated result after a longer passage.

Inspect colour separation, shadow depth, bright-area coverage, trail clarity
and the recognisability of the main form. Test resize, aspect ratios and both
Laptop/TV profiles; large displays should retain deliberate detail and readable
accents. Check CPU rendering and any implemented GPU path separately.

A successful new effect has an identity visible in a still frame, a transformation
worth watching over time, and a musical response that becomes clear when the
sound changes.
