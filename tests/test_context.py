from dataclasses import FrozenInstanceError
from dataclasses import replace
import contextlib
import io
import unittest
import random
import ast
from pathlib import Path

import numpy as np

from core.context import AudioConfig, EffectOptions, FrameContext, parse_seed
from core.audio_engine import AudioEngine
from effects.base import Effect
from effects.spectrum import Bars
from effects.slimemold import SlimeMold
from effects.plasma_gl import PlasmaGL
from effects.bubbles import Bubbles
import pygame


class ContextTests(unittest.TestCase):
    def test_headless_adapter_rebinds_renderer_and_restores_frame_on_detach_failure(self):
        class Probe(Effect):
            def detach_renderer(self):
                self.detached.append(self.renderer)
                if self.fail_detach:
                    raise RuntimeError('detach failed')
                super().detach_renderer()

            def _draw_frame(self, *args):
                return self.renderer

        first, second = object(), object()
        effect = Probe(options=EffectOptions(64, 48))
        effect.detached, effect.fail_detach = [], False
        before = effect.frame
        args = (96, 72, before.waveform, before.fft, .5, 1)
        self.assertIs(effect.draw_frame(*args, renderer=first), first)
        self.assertIs(effect.draw_frame(*args, renderer=second), second)
        self.assertEqual(effect.detached, [None, first])
        self.assertIs(effect.renderer, second)
        effect.fail_detach = True
        with self.assertRaises(RuntimeError):
            effect.draw_frame(*args, renderer=first)
        self.assertIs(effect.frame, before)
        self.assertIs(effect.renderer, second)

    def test_runtime_has_no_ambient_config_module(self):
        root = Path(__file__).resolve().parent.parent
        self.assertFalse((root / 'config.py').exists())
        sources = list((root / 'core').glob('*.py')) + list((root / 'effects').glob('*.py'))
        sources += [root / name for name in ('psysualizer.py', 'benchmarks.py', 'smoke_test.py',
                                             'beat_tracking.py', 'gl_renderer.py', 'settings.py')]
        for path in sources:
            for node in ast.walk(ast.parse(path.read_text())):
                if isinstance(node, ast.Import):
                    self.assertNotIn('config', [alias.name for alias in node.names], path.name)
                elif isinstance(node, ast.ImportFrom):
                    self.assertNotEqual(node.module, 'config', path.name)

    def test_interleaved_hosts_match_independent_rendering(self):
        pygame.init()
        self.addCleanup(pygame.quit)
        contexts = [
            FrameContext(96, 72, dt=1/30, profile='laptop', quality_scale=.6,
                         waveform=np.ones(1024), fft=np.linspace(0., 1., 512), mid=.4, treble=.6),
            FrameContext(160, 96, dt=1/60, profile='tv', quality_scale=1.,
                         waveform=np.ones(1024), fft=np.linspace(1., 0., 512), mid=.2, treble=.8),
        ]
        for cls in (Bars, SlimeMold, PlasmaGL):
            effects = []
            try:
                refs, mixed = [], []
                for collection in (refs, mixed):
                    for seed, frame in enumerate(contexts):
                        effect = cls(options=EffectOptions(frame.width, frame.height, seed=seed), frame=frame)
                        effects.append(effect)
                        collection.append((effect, pygame.Surface((frame.width, frame.height))))
                for index, (effect, surface) in enumerate(refs):
                    for tick in range(12):
                        effect.render(surface, replace(contexts[index], tick=tick, beat=.8 if tick % 3 else .1))
                for tick in range(12):
                    for index, (effect, surface) in enumerate(mixed):
                        effect.render(surface, replace(contexts[index], tick=tick, beat=.8 if tick % 3 else .1))
                for index in range(2):
                    np.testing.assert_array_equal(pygame.surfarray.array3d(refs[index][1]),
                                                  pygame.surfarray.array3d(mixed[index][1]), err_msg=cls.__name__)
                    self.assertEqual(mixed[index][0].frame.width, contexts[index].width)
                    self.assertEqual(mixed[index][0].frame.profile, contexts[index].profile)
            finally:
                for effect in effects:
                    effect.release()

    def test_failed_render_and_headless_adapter_restore_only_their_own_context(self):
        class Failing(Effect):
            def _draw(self, *args):
                raise RuntimeError('failed frame')
        effect = Failing(options=EffectOptions(64, 48))
        other = Bars(options=EffectOptions(96, 72))
        before, other_before = effect.frame, other.frame
        with self.assertRaises(RuntimeError):
            effect.render(pygame.Surface((128, 96)), FrameContext(128, 96))
        self.assertIs(effect.frame, before)
        with self.assertRaises(RuntimeError):
            effect.draw_frame(128, 96, np.zeros(1024), np.zeros(512), .7, 1)
        self.assertIs(effect.frame, before)
        self.assertIs(other.frame, other_before)

    def test_legacy_draw_and_rgba_adapter_use_explicit_instance_context(self):
        frame = FrameContext(96, 72, profile='tv', mid=.4, treble=.6)
        effect = Bars(options=EffectOptions(96, 72), frame=frame)
        surface = pygame.Surface((96, 72))
        effect.draw(surface, frame.waveform, frame.fft, .5, 1)
        rgba = effect.draw_frame(128, 96, frame.waveform, frame.fft, .5, 2)
        self.assertEqual(rgba.shape, (96, 128, 4))
        self.assertEqual(rgba.dtype, np.uint8)
        self.assertTrue((rgba[:, :, 3] == 255).all())
        self.assertEqual(effect.frame.width, 96)
        self.assertEqual(effect.frame.profile, 'tv')
        rgba[:] = 0
        self.assertFalse((effect.draw_frame(128, 96, frame.waveform, frame.fft, .5, 3) == 0).all())

    def test_seeded_bubbles_are_independent_of_global_rng(self):
        python_state, numpy_state = random.getstate(), np.random.get_state()
        first = Bubbles(options=EffectOptions(96, 72, seed=7))
        second = Bubbles(options=EffectOptions(96, 72, seed=7))
        try:
            self.assertEqual(first.pool, second.pool)
            self.assertEqual(python_state, random.getstate())
            np.testing.assert_array_equal(numpy_state[1], np.random.get_state()[1])
        finally:
            first.release()
            second.release()

    def test_frame_owns_readonly_arrays_and_frozen_values(self):
        waveform = np.ones(8)
        frame = FrameContext(320, 240, waveform=waveform)
        waveform[:] = 9
        self.assertTrue((frame.waveform == 1).all())
        with self.assertRaises(ValueError):
            frame.waveform[0] = 3
        with self.assertRaises(FrozenInstanceError):
            frame.width = 999

    def test_construction_requires_valid_dimensions(self):
        for cls in (EffectOptions, FrameContext):
            with self.assertRaises(ValueError):
                cls(0, 240)
        with self.assertRaises(ValueError):
            AudioConfig(block_size=0)

    def test_audio_instances_have_independent_explicit_configuration(self):
        first = AudioEngine(AudioConfig(sample_rate=48000, block_size=256))
        second = AudioEngine(AudioConfig(sample_rate=44100, block_size=1024))
        try:
            self.assertEqual(first.get_snapshot().waveform.shape, (256,))
            self.assertEqual(second.get_snapshot().waveform.shape, (1024,))
            self.assertEqual(first.beat_tracker.sample_rate, 48000)
            self.assertEqual(second.beat_tracker.sample_rate, 44100)
        finally:
            first.release()
            second.release()

    def test_seed_parsing_never_changes_global_rng(self):
        state = np.random.get_state()
        with contextlib.redirect_stderr(io.StringIO()):
            self.assertEqual([parse_seed(s) for s in (None, '', '0', '-7', 'invalid')],
                             [0, 0, 0, 2 ** 32 - 7, 0])
        after = np.random.get_state()
        np.testing.assert_array_equal(state[1], after[1])
        self.assertEqual(state[2:], after[2:])


class EGLAdapterTests(unittest.TestCase):
    """Real GPU checks: context creation may skip; rendering errors must fail."""

    def test_gpu_adapters_resize_rebind_and_return_independent_rgba(self):
        try:
            import moderngl
        except ImportError as exc:
            self.skipTest(f'ModernGL unavailable: {exc}')
        from gl_renderer import GLRenderer
        from effects.phason import Phason
        from effects.ferrofluid import Ferrofluid
        from effects.chromaweave import ChromaWeave

        renderers = []
        for _ in range(2):
            try:
                context = moderngl.create_standalone_context(backend='egl', require=330)
            except Exception as exc:
                self.skipTest(f'EGL OpenGL 3.3 context unavailable: {exc}')
            self.addCleanup(context.release)
            renderer = GLRenderer(96, 72, ctx=context)
            self.addCleanup(renderer.release)
            renderers.append(renderer)

        frame = FrameContext(96, 72, profile='laptop',
                             waveform=np.sin(np.linspace(0, np.pi * 8, 1024)).astype(np.float32),
                             fft=np.linspace(0., 1., 512, dtype=np.float32))
        for cls in (PlasmaGL, Phason, Ferrofluid, ChromaWeave):
            effect = cls(options=EffectOptions(96, 72, seed=7), frame=frame)
            try:
                for renderer in renderers:
                    for width, height in ((96, 72), (128, 96)):
                        with self.subTest(effect=cls.__name__, size=(width, height),
                                          device=renderer.ctx.info['GL_RENDERER']):
                            first = effect.draw_frame(width, height, frame.waveform,
                                                      frame.fft, .8, 1, renderer=renderer)
                            saved = first.copy()
                            second = effect.draw_frame(width, height, frame.waveform,
                                                       frame.fft, .8, 2, renderer=renderer)
                            self.assertEqual(first.shape, (height, width, 4))
                            self.assertEqual(first.dtype, np.uint8)
                            self.assertGreater(int(first[:, :, :3].max()), 0)
                            self.assertFalse(np.shares_memory(first, second))
                            np.testing.assert_array_equal(first, saved)
                            self.assertIs(effect.frame, frame)
                            self.assertIs(effect.renderer, renderer)
            finally:
                effect.release()


class ContextInputTests(unittest.TestCase):
    def test_invalid_public_fields_fail_at_construction(self):
        cases = [
            (FrameContext, 'width', (True, 1.5, 0, -1, '64')),
            (FrameContext, 'height', (False, .5, 0)),
            (FrameContext, 'fps', (True, 0, 30.5)),
            (FrameContext, 'tick', (-1, True, 1.5)),
            (FrameContext, 'quality_tier', (-1, 3, True, .5)),
            (FrameContext, 'quality_scale', (-1, 0, 1.1, float('nan'), float('inf'))),
            (FrameContext, 'dt', (-1, True, float('nan'), float('inf'), '0.1')),
            (FrameContext, 'phase', (-.1, 1.1, float('nan'))),
            (FrameContext, 'profile', ('other', None)),
            (FrameContext, 'envelopes', (None, (), (1, 2), (1, 2, -1), (1, 2, float('nan')))),
            (FrameContext, 'is_silent', (0, 'yes')),
            (EffectOptions, 'width', (True, 1.5, 0)),
            (EffectOptions, 'height', (False, .5, 0)),
            (EffectOptions, 'seed', (-1, 2 ** 32, True, 1.5)),
            (EffectOptions, 'low_spec', (1, None)),
            (EffectOptions, 'audio', ({}, None)),
            (AudioConfig, 'sample_rate', (True, 0, 44100.5)),
            (AudioConfig, 'block_size', (True, 0, 1, 256.5)),
            (AudioConfig, 'channels', (True, 0, 1.5)),
            (AudioConfig, 'envelope_attack_seconds', (0, -1, float('nan'))),
            (AudioConfig, 'envelope_release_seconds', (0, float('inf'))),
        ]
        for field in ('beat', 'mid', 'treble', 'bpm', 'gain'):
            cases.append((FrameContext, field, (-1, True, float('nan'), float('inf'))))
        for cls, field, values in cases:
            for value in values:
                with self.subTest(cls=cls.__name__, field=field, value=value):
                    kwargs = {field: value}
                    if cls is not AudioConfig:
                        kwargs = {'width': 64, 'height': 48, **kwargs}
                    with self.assertRaisesRegex(ValueError, field):
                        cls(**kwargs)

    def test_audio_array_contract_preserves_real_dtype_and_owns_data(self):
        for name in ('waveform', 'fft'):
            for value in ([[]] if name == 'waveform' else []) + [[[1, 2]], ['1'], [True], [1j], [float('nan')], [float('inf')], None]:
                with self.subTest(name=name, value=value):
                    with self.assertRaisesRegex(ValueError, name):
                        FrameContext(64, 48, **{name: value})
            for dtype in (np.float32, np.float64, np.int16):
                source = np.ones(32, dtype=dtype)
                frame = FrameContext(64, 48, **{name: source}, envelopes=[0., 1., 2.])
                self.assertEqual(getattr(frame, name).dtype, dtype)
                source[:] = 0
                self.assertTrue(getattr(frame, name).all())
                self.assertFalse(getattr(frame, name).flags.writeable)
                self.assertEqual(frame.envelopes, (0., 1., 2.))
                # A cooperative read-only contract, not tamper-proof memory.
                getattr(frame, name).setflags(write=True)
                getattr(frame, name)[:] = 2
                self.assertFalse(source.any())

    def test_supported_alternative_audio_sizes_with_selected_consumers(self):
        from effects.waterfall import GlowSquares
        self.assertEqual(FrameContext(64, 48, fft=[]).fft.shape, (0,))
        for block_size in (256, 1024, 2048):
            audio = AudioConfig(sample_rate=48000, block_size=block_size)
            frame = FrameContext(320, 240, dt=0., quality_scale=.6,
                                 waveform=np.zeros(block_size, dtype=np.float32),
                                 fft=np.ones(block_size // 2, dtype=np.float32))
            for cls in (Bars, GlowSquares):
                effect = cls(options=EffectOptions(320, 240, audio=audio, seed=np.int64(7)))
                try:
                    effect.render(pygame.Surface((320, 240)), frame)
                finally:
                    effect.release()


class RenderOutcomeTests(unittest.TestCase):
    def test_binding_pixels_rng_and_retry_for_all_public_entrypoints(self):
        class Probe(Effect):
            def _draw(self, surface, *args):
                self.steps += 1
                self.rng.random()
                surface.fill((self.steps, 0, 0))
                if self.fail:
                    raise RuntimeError('hook failed after advancing')

        for entrypoint in ('render', 'draw', 'draw_frame'):
            for fail_first in (False, True):
                with self.subTest(entrypoint=entrypoint, fail_first=fail_first):
                    old = FrameContext(64, 48, profile='tv')
                    new = FrameContext(96, 72, profile='laptop', tick=2, beat=.8)
                    effect = Probe(options=EffectOptions(64, 48, seed=7), frame=old)
                    other = Probe(options=EffectOptions(64, 48, seed=7), frame=old)
                    surface = pygame.Surface((96, 72))
                    effect.steps, effect.fail = 0, fail_first
                    reference_rng = np.random.default_rng(7)
                    reference_rng.random()

                    def invoke():
                        if entrypoint == 'render':
                            return effect.render(surface, new)
                        if entrypoint == 'draw':
                            return effect.draw(surface, new.waveform, new.fft, new.beat, new.tick)
                        return effect.draw_frame(96, 72, new.waveform, new.fft, new.beat, new.tick)

                    if fail_first:
                        with self.assertRaisesRegex(RuntimeError, 'hook failed'):
                            invoke()
                        self.assertIs(effect.frame, old)
                        if entrypoint != 'draw_frame':
                            self.assertEqual(surface.get_at((0, 0)).r, 1)
                    else:
                        invoke()
                        if entrypoint == 'draw_frame':
                            self.assertIs(effect.frame, old)
                        else:
                            self.assertEqual(effect.frame.width, 96)
                            self.assertEqual(effect.frame.tick, 2)
                            self.assertEqual(effect.frame.profile, 'laptop' if entrypoint == 'render' else 'tv')
                    self.assertEqual(effect.steps, 1)
                    self.assertEqual(effect.rng.random(), reference_rng.random())
                    self.assertIs(other.frame, old)
                    self.assertEqual(other.rng.random(), np.random.default_rng(7).random())
                    effect.fail = False
                    invoke()
                    self.assertEqual(effect.steps, 2)
                    self.assertEqual(effect.frame.width, 64 if entrypoint == 'draw_frame' else 96)
                    effect.release()
                    other.release()

    def test_gpu_hook_error_keeps_replacement_renderer_and_restores_frame(self):
        class Probe(Effect):
            def _draw_frame(self, *args):
                self.steps += 1
                if self.fail:
                    raise RuntimeError('GPU hook failed')
                return self.renderer
        first, second = object(), object()
        effect = Probe(options=EffectOptions(64, 48), renderer=first)
        effect.steps, effect.fail = 0, True
        before = effect.frame
        args = (96, 72, before.waveform, before.fft, .5, 2)
        with self.assertRaisesRegex(RuntimeError, 'GPU hook failed'):
            effect.draw_frame(*args, renderer=second)
        self.assertIs(effect.frame, before)
        self.assertIs(effect.renderer, second)
        self.assertEqual(effect.steps, 1)
        effect.fail = False
        self.assertIs(effect.draw_frame(*args), second)
        self.assertIs(effect.frame, before)
        effect.release()
