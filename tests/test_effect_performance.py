"""Rendering invariants for optimizations; performance is measured separately."""
from dataclasses import replace
import colorsys
import unittest
from unittest.mock import patch

import numpy as np
import pygame

from core.context import EffectOptions, FrameContext
from effects.hyperbolic import Hyperbolic
from effects.branches import Branches
from effects.utils import hsl
from effects.lattice import Lattice
from effects.liquidlight import LiquidLight
from effects.slimemold import SlimeMold
from effects.waterfall import _row_colours


class EffectPerformanceContracts(unittest.TestCase):
    def test_branches_caches_styles_without_removing_recursive_segments(self):
        for low_spec in (False, True):
            frame = FrameContext(320, 240, beat=.9, mid=.4, treble=.6)
            effect = Branches(options=EffectOptions(320, 240, low_spec=low_spec), frame=frame)
            self.addCleanup(effect.release)
            target = pygame.Surface((320, 240))
            arms = effect.base_arms + int(.9*2.2 + .6*.8)
            trunk = min(240*.22*(1.+.9*.70+.4*.25), 240*.27)
            ratio = .62+.6*.06
            def segments(depth, length):
                if depth == 0 or length < 1.5:
                    return 0
                return (1 + 2*segments(depth-1, length*ratio) +
                        (segments(depth-1, length*ratio*.72) if depth >= effect.max_depth-1 else 0))
            with patch('effects.branches.hsl', wraps=hsl) as colours, \
                    patch('effects.branches.pygame.draw.line', wraps=pygame.draw.line) as lines:
                effect.render(target, frame)
            self.assertEqual(colours.call_count, arms*effect.max_depth*2)
            self.assertEqual(lines.call_count, arms*segments(effect.max_depth, trunk)*2)
            self.assertTrue(pygame.surfarray.array3d(target).any())

    def test_waterfall_vector_palette_matches_colorsys_at_edges_and_random_values(self):
        rng = np.random.default_rng(123)
        hues = np.concatenate((np.array([0., 1/6, 1/3, .5, 2/3, 5/6, 1.]),
                               rng.random(73)))
        lightness = rng.uniform(.04, .95, (8, hues.size))
        lightness[:3] = np.array([.04, .5, .95])[:, None]
        expected = np.array([[tuple(int(c*255) for c in colorsys.hls_to_rgb(h, l, 1.))
                              for h, l in zip(hues, row)] for row in lightness], dtype=np.uint8)
        np.testing.assert_array_equal(_row_colours(hues, lightness), expected)

    def test_slime_diffusion_preserves_wrapped_stencil_and_reuses_buffers(self):
        effect = SlimeMold(options=EffectOptions(64, 48, seed=7))
        rng = np.random.default_rng(123)
        effect._trail[:] = rng.uniform(0, 8, effect._trail.shape).astype(np.float32)
        buffers = {id(effect._trail), id(effect._diffuse)}
        for _ in range(4):
            previous = effect._trail.copy()
            expected = ((np.roll(previous, 1, 0) + np.roll(previous, -1, 0)
                         + np.roll(previous, 1, 1) + np.roll(previous, -1, 1)
                         + previous * 4.) / 8.) * .931
            effect._diffuse_trail(.931)
            np.testing.assert_array_equal(effect._trail, expected)
            self.assertEqual({id(effect._trail), id(effect._diffuse)}, buffers)
        effect.release()

    def test_slime_sparse_dense_and_dark_palette_match_reference_with_padded_pitch(self):
        effect = SlimeMold(options=EffectOptions(64, 48, seed=7))
        rng = np.random.default_rng(123)
        width, height = effect._W, effect._H
        parent = pygame.Surface((width+4, height+4))
        parent.fill((7, 8, 9))
        effect._surf = parent.subsurface((2, 2, width, height))
        for density in (0., .1, 1.):
            for hue in (0., .31, .98):
                with self.subTest(density=density, hue=hue):
                    field = rng.uniform(0., 8., effect._trail.shape).astype(np.float32)
                    field[rng.random(field.shape) >= density] = 0.
                    field.ravel()[:6] = [-.1, 0., 1/256, 1/128, 5/510, 1.]
                    effect._trail[:] = field
                    effect._hue = hue
                    intensity = np.clip(field/5., 0., 1.)
                    phase = (hue + intensity*.85) % 1.
                    expected = np.stack([
                        np.clip((np.sin((phase+offset)*np.pi*2)*127+128)*intensity*2, 0, 255).astype(np.uint8)
                        for offset in (0., .333, .667)], axis=-1)
                    effect._paint_trail()
                    actual = pygame.surfarray.array3d(effect._surf).transpose(1, 0, 2)
                    np.testing.assert_array_equal(actual, expected)
                    self.assertEqual(parent.get_at((0, 0))[:3], (7, 8, 9))
        effect.release()

    def test_slime_resize_recreates_matching_workspaces_without_reseeding_agents(self):
        effect = SlimeMold(options=EffectOptions(64, 48, seed=7))
        population = effect._n
        effect.render(pygame.Surface((128, 96)), FrameContext(128, 96))
        self.assertEqual(effect._n, population)
        for name in ('_diffuse', '_phase', '_channel', '_intensity'):
            self.assertEqual(getattr(effect, name).shape, effect._trail.shape)
        effect.release()

    def test_lattice_rgb_blending_preserves_opaque_output_and_scaled_surface(self):
        frame = FrameContext(96, 72, mid=.4, treble=.6, fft=np.ones(512))
        effect = Lattice(options=EffectOptions(96, 72), frame=frame)
        target = pygame.Surface((96, 72), pygame.SRCALPHA)
        effect.render(target, frame)
        scaled = effect._scaled
        target.fill((0, 0, 0, 0))
        effect.render(target, replace(frame, tick=1, beat=.8))
        self.assertIs(effect._scaled, scaled)
        self.assertTrue((pygame.surfarray.array_alpha(target) == 255).all())
        self.assertTrue(pygame.surfarray.array3d(target).any())
        effect.release()

    def test_hyperbolic_clipping_preserves_inputs_and_front_geometry(self):
        for points in ([(0, 0, .5), (1, 0, .6), (0, 1, .7)],
                       [(0, 0, -.5), (1, 0, -.6), (0, 1, -.7)],
                       [(0, 0, .5), (1, 0, 0), (0, 1, 0)]):
            vertices = np.array(points, dtype=float)
            before = vertices.copy()
            result = Hyperbolic._clip_front(vertices)
            np.testing.assert_array_equal(vertices, before)
            if max(vertices[:, 2]) < 1/3.2:
                self.assertEqual(len(result), 0)
            else:
                self.assertGreaterEqual(len(result), 3)
                self.assertTrue((result[:, 2] >= 1/3.2).all())
                if min(vertices[:, 2]) >= 1/3.2:
                    np.testing.assert_array_equal(result, vertices)

    def test_liquid_gathers_match_advection_and_keep_published_fields_independent(self):
        effect = LiquidLight(options=EffectOptions(96, 72))
        rng = np.random.default_rng(123)
        effect._vx[:] = rng.uniform(-.5, .5, effect._vx.shape).astype(np.float32)
        effect._vy[:] = rng.uniform(-.5, .5, effect._vy.shape).astype(np.float32)
        effect._dye[:] = rng.uniform(0, 1, effect._dye.shape).astype(np.float32)
        x = np.clip((effect._x-effect._vx*.85).astype(np.int32), 0, effect._W-1)
        y = np.clip((effect._y-effect._vy*.85).astype(np.int32), 0, effect._H-1)
        expected = (effect._vx[y, x]*.985, effect._vy[y, x]*.985, effect._dye[y, x]*.994)
        effect._advect()
        for actual, reference in zip((effect._vx, effect._vy, effect._dye), expected):
            np.testing.assert_array_equal(actual, reference)
        vx, vy = effect.get_motion_field()
        np.testing.assert_array_equal(vx, effect._vx)
        np.testing.assert_array_equal(vy, effect._vy)
        self.assertFalse(np.shares_memory(vx, effect._vx))
        self.assertFalse(np.shares_memory(vy, effect._vy))
        effect.release()
