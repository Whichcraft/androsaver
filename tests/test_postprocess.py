import unittest
from unittest import mock

import numpy as np
import pygame

from core.postprocess import PostProcessChain


class _FakeFbo:
    def __init__(self, width=96, height=64):
        self.width, self.height = width, height
        self.release = mock.Mock()


class _FakeRenderer:
    def __init__(self):
        self.calls = 0
        self.cache = {}
        self.allocations = 0

    def offscreen(self, width, height):
        if (width, height) not in self.cache:
            self.cache[width, height] = _FakeFbo(width, height)
            self.allocations += 1
        return self.cache[width, height]

    def is_offscreen_current(self, fbo, width, height):
        return self.cache.get((width, height)) is fbo

    def feedback_transform(self, surface, fbo, zoom, rotation):
        self.calls += 1

    def read_pixels(self, fbo):
        return np.full((fbo.height, fbo.width, 4), 80, dtype=np.uint8)


class PostProcessTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        pygame.init()
        pygame.display.set_mode((96, 64))

    @classmethod
    def tearDownClass(cls):
        pygame.quit()

    def _surface(self):
        surface = pygame.Surface((96, 64))
        surface.fill((20, 40, 80))
        pygame.draw.circle(surface, (240, 80, 30), (48, 32), 16)
        return surface

    def test_rotation_reacquires_after_eviction_rebind_resize_and_release(self):
        renderer = _FakeRenderer()
        chain = PostProcessChain(renderer)
        surface = self._surface()
        chain.apply(surface, 3, .5, 1)
        original = chain._fbo
        chain.apply(surface, 3, .5, 2)
        self.assertEqual(renderer.allocations, 1)
        renderer.cache.clear()  # Another effect evicts the borrowed target.
        chain.apply(surface, 3, .5, 3)
        self.assertIsNot(chain._fbo, original)
        previous = chain._fbo
        chain.renderer = replacement = _FakeRenderer()
        chain.apply(surface, 3, .5, 4)
        self.assertIs(chain._fbo_renderer, replacement)
        self.assertIsNot(chain._fbo, previous)
        chain.apply(pygame.Surface((97, 65)), 3, .5, 5)
        self.assertEqual((chain._fbo.width, chain._fbo.height), (97, 65))
        borrowed = chain._fbo
        chain.release()
        chain.release()
        borrowed.release.assert_not_called()
        self.assertIsNone(chain._fbo_renderer)
        chain.apply(surface, 3, .5, 6)
        self.assertIsNotNone(chain._fbo)

    def test_cpu_modes_support_odd_and_minimal_surfaces(self):
        for size in ((97, 65), (1, 1)):
            for mode in range(1, len(PostProcessChain.MODES)):
                with self.subTest(size=size, mode=mode):
                    surface = pygame.Surface(size)
                    surface.fill((80, 120, 160))
                    PostProcessChain().apply(surface, mode, .7, 12)
                    self.assertEqual(surface.get_size(), size)
                    self.assertGreater(int(pygame.surfarray.array3d(surface).sum()), 0)

    def test_off_mode_is_pixel_neutral(self):
        chain = PostProcessChain()
        surface = self._surface()
        before = pygame.surfarray.array3d(surface).copy()
        chain.apply(surface, 0, 1.0, 4)
        np.testing.assert_array_equal(before, pygame.surfarray.array3d(surface))

    def test_each_cpu_mode_keeps_finite_visible_output(self):
        for mode in range(1, len(PostProcessChain.MODES)):
            surface = self._surface()
            PostProcessChain().apply(surface, mode, 0.7, 12)
            pixels = pygame.surfarray.array3d(surface)
            self.assertTrue(np.isfinite(pixels).all(), PostProcessChain.MODES[mode])
            self.assertGreater(int(pixels.max()), 0, PostProcessChain.MODES[mode])

    def test_rotation_reuses_gl_offscreen_cache_and_rebuilds_on_resize(self):
        renderer = _FakeRenderer()
        chain = PostProcessChain(renderer)
        surface = self._surface()
        chain.apply(surface, 3, 0.5, 1)
        chain.apply(surface, 3, 0.5, 2)
        self.assertEqual(renderer.calls, 2)
        chain.release()
        self.assertIsNone(chain._fbo)


if __name__ == "__main__":
    unittest.main()
