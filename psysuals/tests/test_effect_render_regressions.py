import unittest

import numpy as np
import pygame

import config
from effects.aurora import Aurora
from effects.bubbles import Bubbles
from effects.plasma_gl import PlasmaGL
from effects.clifford import Clifford
from effects.mycelium import Mycelium
from effects.slimemold import SlimeMold
from effects.synapse import Synapse


class EffectRenderRegressionTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        pygame.init()
        pygame.display.set_mode((320, 240))

    @classmethod
    def tearDownClass(cls):
        pygame.quit()

    def setUp(self):
        self.old = (config.WIDTH, config.HEIGHT, config._INITIALIZED,
                    config.MID_ENERGY, config.TREBLE_ENERGY)
        config.WIDTH, config.HEIGHT = 320, 240
        config._INITIALIZED = True
        config.MID_ENERGY = 0.4
        config.TREBLE_ENERGY = 0.6
        self.surface = pygame.Surface((320, 240))
        self.waveform = np.zeros(config.BLOCK_SIZE, dtype=np.float32)
        self.fft = np.zeros(config.BLOCK_SIZE // 2, dtype=np.float32)

    def tearDown(self):
        config.WIDTH, config.HEIGHT, config._INITIALIZED, config.MID_ENERGY, config.TREBLE_ENERGY = self.old

    def test_slimemold_clamps_deposit_indices(self):
        effect = SlimeMold()
        effect._px[-1] = np.float32(effect._W)
        effect._py[-1] = np.float32(effect._H)
        effect.draw(self.surface, self.waveform, self.fft, 0.0, 0)

    def test_aurora_allocates_wave_buffer_on_first_frame(self):
        Aurora().draw(self.surface, self.waveform, self.fft, 0.0, 0)

    def test_clifford_first_frame_has_bloom_state(self):
        Clifford().draw(self.surface, self.waveform, self.fft, 0.0, 0)

    def test_mycelium_constructs_on_small_display(self):
        old_size = (config.WIDTH, config.HEIGHT)
        try:
            config.WIDTH, config.HEIGHT = 100, 80
            effect = Mycelium()
            effect.draw(pygame.Surface((100, 80)), self.waveform, self.fft, 0.0, 0)
        finally:
            config.WIDTH, config.HEIGHT = old_size

    def test_synapse_grows_sheds_and_wanders_nodes(self):
        effect = Synapse()
        effect.draw(self.surface, self.waveform, self.fft, 0.0, 0)
        initial = effect.n_nodes
        before = [node.copy() for node in effect._nodes]
        effect._mutate_topology(320, 240, add=True)
        self.assertEqual(effect.n_nodes, initial + 1)
        effect.draw(self.surface, self.waveform, self.fft, 0.0, 1)
        self.assertTrue(any(a != b for a, b in zip(before, effect._nodes[:initial])))
        effect._mutate_topology(320, 240, add=False)
        self.assertEqual(effect.n_nodes, initial)

    def test_bubbles_accepts_high_gain_beat_values(self):
        effect = Bubbles()
        for beat in (0.0, 3.0, 6.0):
            effect.draw(self.surface, self.waveform, self.fft, beat, 0)

    def test_plasma_cpu_fallback_reduces_large_internal_grid(self):
        old_size = (config.WIDTH, config.HEIGHT)
        try:
            config.WIDTH, config.HEIGHT = 1920, 1080
            effect = PlasmaGL()
            effect._ensure_fallback(1920, 1080)
            self.assertLess(effect._X.shape[1], 1080)
            self.assertLess(effect._X.shape[0], 1920)
        finally:
            config.WIDTH, config.HEIGHT = old_size


if __name__ == "__main__":
    unittest.main()
