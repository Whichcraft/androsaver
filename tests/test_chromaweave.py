"""Visual, musical and lifecycle checks for the new analytic weave field."""
from dataclasses import replace
import unittest

import numpy as np
import pygame

from core.context import EffectOptions, FrameContext
from effects import MODES
from effects.chromaweave import ChromaWeave, _weave_rgb


class ChromaWeaveTests(unittest.TestCase):
    def make_effect(self, width=160, height=120, **fields):
        frame = FrameContext(width, height, profile='laptop', **fields)
        effect = ChromaWeave(options=EffectOptions(width, height, seed=7), frame=frame)
        self.addCleanup(effect.release)
        return effect, frame

    def render_cpu(self, effect, frame):
        target = pygame.Surface((frame.width, frame.height), pygame.SRCALPHA)
        effect.render(target, frame)
        self.assertFalse(target.get_locked())
        self.assertTrue((pygame.surfarray.array_alpha(target) == 255).all())
        return pygame.surfarray.array3d(target).transpose(1, 0, 2)

    def test_registry_keeps_existing_visual_indices_and_diagnostics_last(self):
        names = [name for name, _ in MODES]
        self.assertEqual(names[29:], ['Ferrofluid', 'ChromaWeave', 'Horizon', 'Caustics', 'Lanterns', 'Spectrum', 'Waterfall'])
        self.assertEqual(len(names), len(set(names)))

    def test_silent_weave_has_colour_shadow_and_continuing_motion(self):
        effect, frame = self.make_effect()
        first = self.render_cpu(effect, frame)
        for tick in range(1, 91):
            effect.set_frame_context(replace(frame, tick=tick))
            effect._advance(0.)
        later = self.render_cpu(effect, replace(frame, tick=91))
        self.assertGreater(np.abs(first.astype(float)-later).mean(), 3.)
        self.assertGreater(first.max(), 100)
        self.assertGreater((np.ptp(first, axis=2) > 35).mean(), .2)
        self.assertLess(first[frame.height//2, frame.width//2].mean(), first.mean()*.3)
        self.assertLess((first.min(axis=2) > 220).mean(), .01)

    def test_bass_pulse_is_visible_and_releases_without_changing_the_camera(self):
        quiet, frame = self.make_effect()
        pulse, _ = self.make_effect()
        for tick in range(5):
            a = self.render_cpu(quiet, replace(frame, tick=tick))
            b = self.render_cpu(pulse, replace(frame, tick=tick, beat=1.2))
        self.assertGreater(np.abs(a.astype(float)-b).mean(), 4.)
        self.assertEqual(quiet._turn, pulse._turn)
        for tick in range(5, 200):
            quiet.set_frame_context(frame); pulse.set_frame_context(frame)
            u = quiet._advance(0.); v = pulse._advance(0.)
        self.assertLess(v['u_pulse'], .00001)
        self.assertLess(v['u_bass'], .002)
        self.assertEqual(u['u_time'], v['u_time'])

    def test_mid_and_treble_have_separate_observable_roles(self):
        quiet, frame = self.make_effect()
        mid, _ = self.make_effect(mid=1.)
        high, _ = self.make_effect(treble=1.)
        for _ in range(90):
            u = quiet._advance(0.); m = mid._advance(0.); h = high._advance(0.)
        self.assertGreater(m['u_time'], u['u_time'])
        self.assertEqual(h['u_time'], u['u_time'])
        self.assertGreater(h['u_high'], .9)
        x, y = np.meshgrid(np.linspace(-1.3, 1.3, 192), np.linspace(-1, 1, 144))
        a, b = _weave_rgb(x, y, u), _weave_rgb(x, y, h)
        self.assertGreater(np.count_nonzero(a != b), 30)
        self.assertGreater(b[..., 2].sum(), a[..., 2].sum())

    def test_tiled_fallback_resize_quality_and_state_are_coherent(self):
        effect, frame = self.make_effect(97, 65, mid=.4, treble=.6)
        self.render_cpu(effect, replace(frame, beat=.8))
        size = effect._surface.get_size()
        uniforms = dict(u_time=effect._time, u_weave=effect._weave, u_hue=effect._hue, u_turn=effect._turn,
                        u_bass=float(effect._motion_audio[0]), u_mid=float(effect._motion_audio[1]),
                        u_high=float(effect._light_audio[2]), u_pulse=float(effect._light_audio[0]), u_accent=1.)
        expected = _weave_rgb(effect._x[None, :], effect._y[:, None], uniforms)
        np.testing.assert_array_equal(pygame.surfarray.array3d(effect._surface).transpose(1, 0, 2), expected)
        previous_time = effect._time
        for width, height, scale in ((65, 97, 1.), (161, 91, .6), (97, 65, 1.)):
            self.render_cpu(effect, replace(frame, width=width, height=height, quality_scale=scale))
            self.assertEqual(effect._surface.get_size(), (round(width*scale), round(height*scale)))
            self.assertGreater(effect._time, previous_time)
            previous_time = effect._time
        self.assertEqual(effect._surface.get_size(), size)
        effect.release(); effect.release()
        self.assertIsNone(effect._surface)

    def test_loud_inputs_and_zero_dt_are_bounded_for_both_profiles(self):
        for profile in ('laptop', 'tv'):
            effect, frame = self.make_effect(96, 72, beat=6., mid=6., treble=6.)
            frame = replace(frame, profile=profile)
            for tick in range(120):
                effect.set_frame_context(frame)
                uniforms = effect._advance(6.)
            self.assertLessEqual(uniforms['u_pulse'], 1.5)
            self.assertLessEqual(uniforms['u_bass'], 1.5)
            self.assertEqual(uniforms['u_accent'], 1.3 if profile == 'tv' else 1.)
            effect.set_frame_context(replace(frame, dt=0.))
            self.assertEqual(effect._advance(0.), uniforms)
            image = self.render_cpu(effect, replace(frame, dt=0.))
            self.assertGreater(image.max(), 100)
            self.assertLess((image.min(axis=2) > 220).mean(), .03)

    def test_cpu_sample_budget_stays_bounded_and_quality_still_reduces_work(self):
        effect, frame = self.make_effect(1920, 1080)
        for width, height in ((1920, 1080), (1080, 1920), (3840, 2160)):
            frame = replace(frame, width=width, height=height)
            self.render_cpu(effect, frame)
            full = effect._surface.get_size()
            self.assertLessEqual(full[0]*full[1], effect.MAX_CPU_PIXELS)
            self.assertLess(abs(full[0]/full[1]-width/height), .01)
            self.render_cpu(effect, replace(frame, quality_scale=.6))
            reduced = effect._surface.get_size()
            self.assertLess(reduced[0]*reduced[1], full[0]*full[1]*.4)

    def test_real_egl_matches_cpu_field_and_rebinds_after_resize(self):
        try:
            import moderngl
        except ImportError as exc:
            self.skipTest(f'EGL unavailable: {exc}')
        from gl_renderer import GLRenderer
        renderers = []
        for _ in range(2):
            try:
                context = moderngl.create_standalone_context(backend='egl', require=330)
            except Exception as exc:
                self.skipTest(f'EGL context unavailable: {exc}')
            self.addCleanup(context.release)
            renderer = GLRenderer(97, 65, ctx=context)
            self.addCleanup(renderer.release)
            renderers.append(renderer)
        cpu, frame = self.make_effect(97, 65, beat=.8, mid=.4, treble=.6)
        gpu, _ = self.make_effect(97, 65, beat=.8, mid=.4, treble=.6)
        for tick, (width, height, renderer) in enumerate(((97, 65, renderers[0]),
                                                       (65, 97, renderers[0]),
                                                       (97, 65, renderers[1]))):
            current = replace(frame, width=width, height=height, tick=tick)
            expected = self.render_cpu(cpu, current)
            gpu.set_frame_context(current)
            actual = gpu.draw_frame(width, height, current.waveform, current.fft,
                                    current.beat, tick, renderer=renderer)
            self.assertEqual(actual.shape, (height, width, 4))
            self.assertTrue((actual[..., 3] == 255).all())
            error = np.abs(expected.astype(float)-actual[..., :3])
            self.assertLess(error.mean(), .7)
            self.assertLess(np.quantile(error, .999), 4.)
            self.assertIs(gpu.frame, current)
        self.assertIs(gpu.renderer, renderers[1])


if __name__ == '__main__':
    unittest.main()
