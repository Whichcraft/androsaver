import unittest

import numpy as np
import pygame

from tests.render_host import RenderHost
from effects import MODES


# Quantized compact metrics, generated at 160x120 with the fixture below.
# Wide tolerances account for platform rasterization while still catching
# black frames and major geometry/palette regressions.
BASELINE = {
    "Yantra": (5381, 190386), "Cube": (2297, 70866),
    "TriFlux": (18771, 1143359), "Lissajous": (446, 37219),
    "Tunnel": (15859, 320990), "Corridor": (18938, 296785),
    "Nova": (3266, 116423), "Spiral": (13340, 189763),
    "Bubbles": (19200, 1274888), "Plasma": (19200, 558864),
    "Branches": (4578, 123481), "Butterflies": (17292, 625300),
    "FlowField": (19200, 892884), "Fireworks": (1017, 68958),
    "Aurora": (14009, 124197), "Lattice": (17787, 406107),
    "Mycelium": (17941, 423082), "Magnetar": (18720, 205784),
    "SlimeMold": (16606, 732669), "Mobius": (6025, 168840),
    "Chromatic": (3308, 108091), "Persistence": (9335, 126875),
    "Synapse": (2070, 50689), "Heartbeat": (11644, 153948),
    "Hyperbolic": (19200, 127052),
    "LiquidLight": (19200, 172183), "Cymatica": (10988, 229039),
    "Phason": (17096, 449507), "Tesseract": (921, 43391),
    "Ferrofluid": (19200, 110168),
    "ChromaWeave": (19200, 428073),
    "Horizon": (19200, 269235),
    "Caustics": (19200, 589797), "Lanterns": (19155, 184059),
    "Spectrum": (3299, 210051), "Waterfall": (2592, 57384),
}

_WAVEFORM = np.sin(np.linspace(0, np.pi * 8, 1024)).astype(np.float32)
_FFT = np.linspace(0.0, 1.0, 1024 // 2, dtype=np.float32)
_BEATS = (0.0, 1.2, 0.0, 2.0, 0.0, 1.2, 0.0, 0.0)
_WARMUP_FRAMES = {"Butterflies": 24}


class VisualRegressionTests(unittest.TestCase):
    def setUp(self):
        self.host = RenderHost()

    @classmethod
    def setUpClass(cls):
        pygame.init()
        pygame.display.set_mode((160, 120))

    @classmethod
    def tearDownClass(cls):
        pygame.quit()

    def test_all_registered_effects_match_compact_fixture_metrics(self):
        old = (self.host.width, self.host.height, self.host.initialized,
               self.host.mid, self.host.treble)
        self.host.width, self.host.height = 160, 120
        self.host.initialized = True
        self.host.mid, self.host.treble = 0.4, 0.6
        try:
            self.assertEqual({name for name, _ in MODES}, set(BASELINE))
            for name, effect_cls in MODES:
                surface = pygame.Surface((160, 120))
                effect = self.host.create(effect_cls)
                try:
                    warmup = _WARMUP_FRAMES.get(name, 0)
                    for tick in range(warmup):
                        surface.fill((0, 0, 0))
                        effect.draw(surface, _WAVEFORM, _FFT, 0.0, tick)
                    for tick, beat in enumerate(_BEATS, start=warmup):
                        surface.fill((0, 0, 0))
                        effect.draw(surface, _WAVEFORM, _FFT, beat, tick)
                    pixels = pygame.surfarray.array3d(surface).astype(np.float32)
                    self.assertTrue(np.isfinite(pixels).all(), name)
                    nonblack = int((pixels.max(axis=2) > 3).sum())
                    quantized_sum = int(np.round(pixels / 8).astype(np.uint8).sum())
                    expected_pixels, expected_sum = BASELINE[name]
                    self.assertGreaterEqual(nonblack, max(1, expected_pixels // 4), name)
                    self.assertGreater(quantized_sum, max(1, int(expected_sum * 0.35)), name)
                    self.assertLess(quantized_sum, int(expected_sum * 4.0) + 1, name)
                finally:
                    release = getattr(effect, "release", None)
                    if callable(release):
                        release()
        finally:
            (self.host.width, self.host.height, self.host.initialized,
             self.host.mid, self.host.treble) = old


if __name__ == "__main__":
    unittest.main()
