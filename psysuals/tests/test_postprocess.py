import unittest

import numpy as np
import pygame

from core.postprocess import PostProcessChain


class _FakeFbo:
    width = 96
    height = 64


class _FakeRenderer:
    def __init__(self):
        self.calls = 0

    def offscreen(self, width, height):
        return _FakeFbo()

    def feedback_transform(self, surface, fbo, zoom, rotation):
        self.calls += 1

    def read_pixels(self, fbo):
        return np.full((64, 96, 4), 80, dtype=np.uint8)


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
