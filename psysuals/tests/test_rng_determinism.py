import unittest

from tests.render_host import RenderHost
import numpy as np
import pygame

from effects.cube import Cube
from effects.flowfield import FlowField
from effects.magnetar import Magnetar
from effects.slimemold import SlimeMold
from effects.waterfall import GlowSquares
from effects.butterflies import Butterflies


class RngDeterminismTests(unittest.TestCase):
    def test_butterfly_pairing_trajectory_is_seeded_and_bounded(self):
        first, second = self.host.create(Butterflies), self.host.create(Butterflies)
        surface_a, surface_b = pygame.Surface((320, 240)), pygame.Surface((320, 240))
        waveform, fft = np.zeros(1024), np.linspace(0., 1., 512)
        saw_pair = False
        try:
            for tick in range(400):
                beat = .8 if tick % 12 == 0 else .1
                first.draw(surface_a, waveform, fft, beat, tick)
                second.draw(surface_b, waveform, fft, beat, tick)
                self.assertLessEqual(len(first._butterflies), first.MAX_POPULATION)
                self.assertLessEqual(len(first._pairs), first.MAX_PAIRS)
                saw_pair |= bool(first._pairs)
                for pair in first._pairs:
                    self.assertIs(pair.a.partner, pair.b)
                    self.assertIs(pair.b.partner, pair.a)
                self.assertEqual([(b.x, b.y, b.age, b.state) for b in first._butterflies],
                                 [(b.x, b.y, b.age, b.state) for b in second._butterflies])
            self.assertTrue(saw_pair)
            np.testing.assert_array_equal(pygame.surfarray.array3d(surface_a),
                                          pygame.surfarray.array3d(surface_b))
        finally:
            first.release()
            second.release()

    @classmethod
    def setUpClass(cls):
        pygame.init()
        pygame.display.set_mode((320, 240))

    @classmethod
    def tearDownClass(cls):
        pygame.quit()

    def setUp(self):
        self.host = RenderHost()
        self.old_size = (self.host.width, self.host.height)
        self.old_initialized = self.host.initialized
        self.old_mid = self.host.mid
        self.old_treble = self.host.treble
        self.host.width, self.host.height = 320, 240
        self.host.initialized = True
        self.host.mid = 0.5
        self.host.treble = 0.8

    def tearDown(self):
        self.host.width, self.host.height = self.old_size
        self.host.initialized = self.old_initialized
        self.host.mid = self.old_mid
        self.host.treble = self.old_treble

    def test_seeded_effects_start_with_matching_state(self):
        for cls, attrs in (
            (Cube, ("rx", "orb_angle")),
            (FlowField, ("_px", "_py")),
            (Magnetar, ("_px", "_py")),
            (SlimeMold, ("_px", "_py", "_ang")),
            (GlowSquares, ()),
        ):
            first = self.host.create(cls)
            second = self.host.create(cls)
            for attr in attrs:
                left, right = getattr(first, attr), getattr(second, attr)
                if isinstance(left, np.ndarray):
                    np.testing.assert_array_equal(left, right, err_msg=cls.__name__)
                else:
                    self.assertEqual(left, right)


if __name__ == "__main__":
    unittest.main()
