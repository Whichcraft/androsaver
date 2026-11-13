# psysuals — Architecture (3.23.0 worktree)

## Ownership

The desktop app coordinates the frame loop and resource lifecycle. Runtime
configuration belongs to an instance; config.py has been removed.

| Module | Responsibility |
| --- | --- |
| core/context.py | Frozen AudioConfig, EffectOptions, FrameContext; explicit parse_seed |
| core/cli.py | Parser construction and explicit argv parsing |
| core/input_controller.py | Ordered event dispatch against app state/actions |
| core/audio_engine.py | Capture, FFT, fallback beat/BPM and coherent AudioSnapshot |
| core/silence.py | SilenceGate with injected thresholds and block duration |
| core/audio_response.py | Beat history/decay, auto-gain, phase and idle response |
| core/transitions.py | Tick-based preset/parameter interpolation |
| core/frame_composer.py | Composition surfaces, layers and GL presentation |
| core/display_manager.py | SDL/X11 windows, explicit dimensions and span children |
| core/ui_manager.py | Fonts/caches and supplied HUDState; no device enumeration |
| core/quality.py | Instance governor returning QualityDecision |
| beat_tracking.py | Optional librosa analysis worker |
| settings.py | JSON settings/presets under ~/.config/psysuals/ |
| gl_renderer.py | Programs, uploads and renderer-owned offscreen targets |
| effects/ | Registered implementations and instance simulation state |

Each VisualizerApp owns its audio configuration, UI selections, target FPS,
palette, quality governor, audio response and latest immutable frame.
DisplayManager reports actual SDL dimensions through screen and width/height.
UIManager sizes fonts from its constructor width and draw target.
No module-global dimensions, audio bands or quality settings are published.

## Explicit inputs

AudioConfig holds sample rate, block size, channels and envelope constants.
AudioEngine receives it at construction. Defaults remain 44,100 Hz, 1,024 samples,
mono and 35 ms/180 ms attack/release.

EffectOptions supplies initial width/height, low-spec policy, seed and AudioConfig.
Effects require positive construction dimensions. Each owns its NumPy RNG;
Bubbles owns a seeded Python RNG. Startup explicitly parses PSYSUALS_SEED.
Imports never seed global Python/NumPy randomness.

FrameContext holds output dimensions, elapsed seconds, target FPS, tick,
Auto/Laptop/TV profile, quality tier/scale, waveform, FFT, gained beat, mid/treble,
three envelopes, BPM, phase, gain and silence state. The record is frozen.
Waveform/FFT are independent read-only copies; envelopes are a tuple.
Rendering resources remain separate on each effect/renderer.

## Frame flow

1. Read one AudioSnapshot under the engine lock: arrays, generation, audio time,
   envelopes and onset timing belong to the same callback publication.
2. Optionally refine BPM/beat with the librosa worker.
3. AudioResponse consumes the snapshot and explicit frame/tap inputs. SilenceGate
   advances only on fresh audio generations. The app builds an effect context
   with the gained beat and updates its own palette/HUD state.
4. FrameComposer sends that context to foreground and enabled background.
5. UIManager draws supplied status and controls; the app presents the frame.
6. QualityGovernor returns the next tier/scale for the app's next context.

Legacy audio getters remain available. Audio callbacks never read display state.
Beat normalization retains the 40-sample history and elapsed-time decay.
Preset/parameter transitions still use ticks and nominal target FPS: eight beats
with BPM, four nominal seconds otherwise. Duration can stretch below target FPS.
Simulation formulas and timing policies were not redesigned.

## Effect and host API

New hosts call render(surface, frame_context). Subclasses implement the _draw
hook so the base class handles context binding and both legacy adapters:

    import pygame
    from core.context import EffectOptions, FrameContext
    from effects.base import Effect

    class MyEffect(Effect):
        def _draw(self, surface, waveform, fft, beat, tick):
            radius = max(1, int(8 + self.frame.mid * 4))
            pygame.draw.circle(surface, (255, 128, 64),
                               (self.frame.width // 2, self.frame.height // 2), radius)

    pygame.init()
    surface = pygame.Surface((640, 360))
    effect = MyEffect(options=EffectOptions(640, 360, seed=7))
    effect.render(surface, FrameContext(640, 360, profile="laptop", mid=.4))
    effect.release()
    pygame.quit()

The draw(surf, waveform, fft, beat, tick) signature remains. It derives dimensions
from the target and uses the instance's supplemental frame data. Supply updates
with set_frame_context(frame) when using this adapter.

The Android/headless signature remains:
draw_frame(width, height, waveform, fft, beat, tick, renderer=None).
It returns a fresh uint8 RGBA array shaped (height, width, 4). It temporarily
binds only this instance's output context and restores it on success/failure.
A supplied renderer becomes the active renderer until detached; switching
renderers detaches old context-owned resources. GPU effects implement the
_draw_frame hook. Plasma requires GL; Phason/Ferrofluid retain CPU fallbacks.

Host initialization changes deliberately: pass EffectOptions instead of global
dimensions/an initialization flag. An optional initial frame can be supplied
alongside options for constructor-time profile/quality selection.
A Python-embedding external host must adopt this constructor input. AndroSaver
instead maintains a native Kotlin/GLES backport with a vendored reference subtree;
it does not instantiate Python effects. No Android build system is included here. Reuse effect instances to retain simulation state.

## Composition, resize and lifecycle

CPU frames are opaque. Direct-GL effects use a transparent UI overlay. CPU
composition uploads once in GL mode; direct-GL overlays clear after presentation.
Reduced-resolution images scale to the full output. SlimeMold retains its TV
detail floor. CPU post-processing precedes UI; direct-GL post-processing follows
UI upload, preserving the existing order.

Mode switches clear presentation/GPU buffers and reset gain to .7. MODES retains
the original 32-entry numeric registry with Spectrum and Waterfall last.
Recipes select full-registry backgrounds; Shift+B cycles the first nine.
Preset formats remain unchanged.

Display changes detach GPU resources before context replacement. Supported CPU
simulation state is retained/rescaled; legacy doubles can fall back to rebuild.
Failed rendering restores its previous frame binding without changing another
instance. Effect.release() handles inherited detachment; specialized overrides
own additional buffers and call the base method when using its GPU resources.
Bubbles retains at most 700 particles and 32 LRU-cached surface sizes.

GLRenderer owns offscreens. Borrowers check is_offscreen_current() before reuse
and never release borrowed FBOs. Replacement upload/framebuffer allocation is
transactional on allocation/upload failure. Post-processing checks cache and
renderer identity. Startup failure cleans acquired resources without saving
partial settings. Shutdown attempts independent releases despite another failure.
Span children do not overwrite parent settings.

## Verification and limits

Run README.md's headless checks. tests/test_context.py verifies immutable inputs,
interleaved independent hosts, failed-frame isolation, seeded reproducibility,
legacy RGBA adapters and absence of runtime config imports.
Existing visual baseline values/tolerances remain unchanged.

All 32 CPU effects match saved pre-migration pixels exactly. Seven effects also
passed real AMD EGL comparison; Phason had at most one 8-bit channel step of
difference with identical shader source and uniforms. This offscreen evidence
does not itself validate desktop presentation, physical multi-monitor or live
device switching. R05 separately exercised desktop/audio and inspected the native
AndroSaver integration; TODO.md records exact outcomes and remaining limits.

## Public input validation

Context constructors reject invalid fields with ValueError naming the field;
they do not silently clamp values. Sizes, sample rate, channels and target FPS
are positive integers (bool and fractional values are rejected); audio block
size is an integer of at least two samples. Tick is a nonnegative integer.
EffectOptions accepts an AudioConfig, bool low_spec and a seed in 0..2**32-1;
parse_seed is the separate environment-string normalization boundary.

FrameContext requires finite nonnegative dt, beat, bands, BPM, gain and three
envelope values. Phase is in [0, 1], quality tier is integer 0..2 and quality
scale is in (0, 1]. Profiles are auto/laptop/tv; is_silent is bool. Long finite
dt is accepted; effects retain their existing stall cap. Zero dt/BPM/gain and
zero audio are valid. Audio envelope time constants are finite and positive.

Waveform/FFT must be finite, one-dimensional real numeric arrays; waveform
must be nonempty. Empty FFT is a supported sentinel for existing effects such
as Heartbeat, Lattice and Cymatica, not a promise that every effect accepts it;
integer and floating dtypes are preserved. Constructors copy caller data and
mark it read-only. They accept independent lengths because FrameContext does
not contain AudioConfig. Hosts must supply waveform length block_size and FFT
length block_size//2 for effects whose construction depends on those sizes.
Default 1024/512 is the all-effect integration contract. AudioEngine owns sized
buffers; Spectrum and Waterfall additionally have render coverage for block
sizes 256 and 2048 at 48 kHz. This does not promise arbitrary audio lengths for
every effect; capture channel counts/sample rates still depend on the device.
AudioEngine analyzes the first captured channel even when more are requested.

Frozen records prevent field reassignment; the defensive read-only array
contract assumes cooperative callers. NumPy owners can explicitly re-enable
writing. Do not do so or share a frame with code that mutates it. This is not
a security boundary or a guarantee of deeply immutable memory.

## Rendering outcomes and recovery

| Entry point | Success | Hook error |
| --- | --- | --- |
| render(surface, frame) | Retains supplied frame | Restores previous frame binding |
| draw(surface, waveform, fft, beat, tick) | Retains derived frame using target size and previous supplemental data | Restores previous frame binding |
| draw_frame(width, height, ...) | Restores previous frame; retains supplied renderer | Restores previous frame; retains renderer if replacement completed |

Invalid adapter inputs fail before invoking hooks. Renderer replacement first
detaches the old resources, then assigns the supplied renderer. If detachment
fails, the replacement is not assigned; old resources may already be partially
released. A successful replacement is retained even when the subsequent hook
fails. Renderers own borrowed offscreen targets; effects do not take ownership
of the supplied renderer itself.

These are binding guarantees, not transactions: simulation state, random draws,
pixels and released resources are not rolled back. Retry only when the specific
hook's partial state is understood and safe. After an uncertain GPU/resource
failure, attempt release, discard the effect and reconstruct it with the host's
known current renderer/options/frame; report cleanup failures separately. Do not
reuse an old context after the host destroyed it. The host retains ownership of
renderer shutdown. One failed effect does not alter another instance's frame.

Use one serialized caller per effect instance. Independent instance state does
not imply concurrent rendering safety for SDL, audio backends or GL contexts.
Subclass authors implement _draw and optionally _draw_frame, keeping public
adapters inherited. Retained argument signatures do not guarantee compatibility
for external subclasses that previously overrode public draw methods.
