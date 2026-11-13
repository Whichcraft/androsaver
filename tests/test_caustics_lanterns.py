import unittest

import numpy as np
import pygame

from effects.caustics import Caustics, _MAX_RIPPLES
from effects.lanterns import Lanterns, _MAX_LANTERNS, _MAX_SPARKS
from tests.render_host import RenderHost


class CausticsLanternsTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        pygame.init()
        pygame.display.set_mode((64, 64))

    @classmethod
    def tearDownClass(cls):
        pygame.quit()

    def setUp(self):
        self.host = RenderHost(320, 180)
        self.surface = pygame.Surface((320, 180))
        self.waveform = np.zeros(1024, dtype=np.float32)
        self.fft = np.zeros(512, dtype=np.float32)

    def frames(self, effect, count, beat=0.0, surface=None):
        surface = surface or self.surface
        shots = []
        for tick in range(count):
            effect.draw(surface, self.waveform, self.fft, beat(tick) if callable(beat) else beat, tick)
            shots.append(pygame.surfarray.array3d(surface).astype(np.int16))
        return shots

    def test_caustic_net_moves_in_silence(self):
        shots = self.frames(self.host.create(Caustics), 40)
        self.assertGreater(float(np.abs(shots[-1] - shots[0]).mean()), 2.0)
        self.assertGreater(float(shots[-1].max()), 150)       # bright caustic lines

    def test_caustics_beats_drop_bounded_ripples_that_fade(self):
        effect = self.host.create(Caustics)
        self.frames(effect, 120, beat=lambda t: 1.2 if t % 6 < 2 else 0.0)
        self.assertGreater(len(effect._ripples), 0)
        self.assertLessEqual(len(effect._ripples), _MAX_RIPPLES)
        self.frames(effect, 400)
        self.assertEqual(effect._ripples, [])

    def test_caustics_bass_sharpens_the_net(self):
        quiet, loud = self.host.create(Caustics), self.host.create(Caustics)
        self.frames(quiet, 60)
        self.frames(loud, 60, beat=1.4)
        h_quiet, h_loud = quiet._height(0.0), loud._height(1.0)
        self.assertGreater(float(np.ptp(h_loud)), float(np.ptp(h_quiet)))

    def test_lanterns_rise_and_beats_release_more(self):
        effect = self.host.create(Lanterns)
        start = {id(l): l["y"] for l in effect._lanterns}
        self.frames(effect, 30)
        risen = [l["y"] > start[id(l)] for l in effect._lanterns if id(l) in start]
        self.assertTrue(risen and all(risen))
        before = len(effect._lanterns)
        self.frames(effect, 2, beat=1.4)
        self.assertGreater(len(effect._lanterns), before)

    def test_lantern_and_spark_pools_stay_bounded(self):
        effect = self.host.create(Lanterns)
        self.host.treble = 1.5
        self.frames(effect, 600, beat=lambda t: 1.5 if t % 4 < 2 else 0.0)
        self.assertLessEqual(len(effect._lanterns), _MAX_LANTERNS)
        self.assertLessEqual(len(effect._sparks), _MAX_SPARKS)
        self.assertLessEqual(len(effect._glow_cache), 161)

    def test_odd_sizes_and_short_spectra_render(self):
        for cls in (Caustics, Lanterns):
            effect = self.host.create(cls)
            for size in ((97, 53), (1200, 300), (16, 16), (300, 900)):
                target = pygame.Surface(size)
                effect.draw(target, self.waveform, np.zeros(0, dtype=np.float32), 2.0, 0)
                self.assertNotEqual(target.get_bounding_rect().width, 0, cls.__name__)


if __name__ == "__main__":
    unittest.main()
