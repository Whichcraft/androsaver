import unittest

import numpy as np
import pygame

import config
from effects import MODES


# Quantized compact metrics, generated at 160x120 with the fixture below.
# Wide tolerances account for platform rasterization while still catching
# black frames and major geometry/palette regressions.
BASELINE = {
    "Yantra": (5381, 190386), "Cube": (2297, 70866),
    "TriFlux": (18771, 1143359), "Lissajous": (446, 37219),
    "Tunnel": (15859, 320990), "Corridor": (18552, 378144),
    "Nova": (3266, 116423), "Spiral": (13340, 189763),
    "Bubbles": (19200, 1274888), "Plasma": (19200, 558864),
    "Branches": (4578, 123481), "Butterflies": (17292, 625300),
    "FlowField": (19200, 892884), "Fireworks": (1017, 68958),
    "Aurora": (14009, 124197), "Lattice": (17787, 406107),
    "Mycelium": (17941, 423082), "Magnetar": (18720, 205784),
    "SlimeMold": (16606, 732669), "Mobius": (4461, 201641),
    "Chromatic": (15297, 744271), "Persistence": (1104, 8672),
    "Synapse": (6213, 228956), "Heartbeat": (11644, 153948),
    "Morphogenesis": (19159, 459180), "Hyperbolic": (19200, 127052),
    "LiquidLight": (19200, 172183), "Cymatica": (10988, 229039),
    "Phason": (17096, 449507), "Tesseract": (921, 43391),
    "Ferrofluid": (19200, 711128), "Mandelbox": (16393, 363334),
    "Spectrum": (3299, 210051), "Waterfall": (2592, 57384),
}

_WAVEFORM = np.sin(np.linspace(0, np.pi * 8, config.BLOCK_SIZE)).astype(np.float32)
_FFT = np.linspace(0.0, 1.0, config.BLOCK_SIZE // 2, dtype=np.float32)
_BEATS = (0.0, 1.2, 0.0, 2.0, 0.0, 1.2, 0.0, 0.0)


class VisualRegressionTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        pygame.init()
        pygame.display.set_mode((160, 120))

    @classmethod
    def tearDownClass(cls):
        pygame.quit()

    def test_all_registered_effects_match_compact_fixture_metrics(self):
        old = (config.WIDTH, config.HEIGHT, config._INITIALIZED,
               config.MID_ENERGY, config.TREBLE_ENERGY)
        config.WIDTH, config.HEIGHT = 160, 120
        config._INITIALIZED = True
        config.MID_ENERGY, config.TREBLE_ENERGY = 0.4, 0.6
        try:
            self.assertEqual({name for name, _ in MODES}, set(BASELINE))
            for name, effect_cls in MODES:
                surface = pygame.Surface((160, 120))
                effect = effect_cls()
                try:
                    for tick, beat in enumerate(_BEATS):
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
            (config.WIDTH, config.HEIGHT, config._INITIALIZED,
             config.MID_ENERGY, config.TREBLE_ENERGY) = old


if __name__ == "__main__":
    unittest.main()
