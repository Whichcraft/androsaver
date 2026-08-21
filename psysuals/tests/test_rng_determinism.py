import unittest

import config
import numpy as np
import pygame

from effects.cube import Cube
from effects.flowfield import FlowField
from effects.magnetar import Magnetar
from effects.slimemold import SlimeMold
from effects.waterfall import GlowSquares


class RngDeterminismTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        pygame.init()
        pygame.display.set_mode((320, 240))

    @classmethod
    def tearDownClass(cls):
        pygame.quit()

    def setUp(self):
        self.old_size = (config.WIDTH, config.HEIGHT)
        self.old_initialized = config._INITIALIZED
        self.old_mid = config.MID_ENERGY
        self.old_treble = config.TREBLE_ENERGY
        config.WIDTH, config.HEIGHT = 320, 240
        config._INITIALIZED = True
        config.MID_ENERGY = 0.5
        config.TREBLE_ENERGY = 0.8

    def tearDown(self):
        config.WIDTH, config.HEIGHT = self.old_size
        config._INITIALIZED = self.old_initialized
        config.MID_ENERGY = self.old_mid
        config.TREBLE_ENERGY = self.old_treble

    def test_seeded_effects_start_with_matching_state(self):
        for cls, attrs in (
            (Cube, ("rx", "orb_angle")),
            (FlowField, ("_px", "_py")),
            (Magnetar, ("_px", "_py")),
            (SlimeMold, ("_px", "_py", "_ang")),
            (GlowSquares, ()),
        ):
            first = cls()
            second = cls()
            for attr in attrs:
                left, right = getattr(first, attr), getattr(second, attr)
                if isinstance(left, np.ndarray):
                    np.testing.assert_array_equal(left, right, err_msg=cls.__name__)
                else:
                    self.assertEqual(left, right)


if __name__ == "__main__":
    unittest.main()
