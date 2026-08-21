import unittest

from effects.palette import ColorPalette, _TRANSITION_FRAMES


class PaletteTransitionTests(unittest.TestCase):
    def test_genre_transition_reaches_target_without_jumps(self):
        palette = ColorPalette()
        palette.base_hue = 0.99
        palette.set_genre("electronic")
        initial_alpha = palette.trail_alpha
        palette.update(0.0, 0.0, 0.0, 0)
        self.assertNotEqual(palette.trail_alpha, 16)
        self.assertLess(abs(palette.trail_alpha - initial_alpha), 2.0)
        for tick in range(_TRANSITION_FRAMES):
            palette.update(0.0, 0.0, 0.0, tick + 1)
        self.assertAlmostEqual(palette.trail_alpha, 16.0, places=3)
        self.assertAlmostEqual(palette._sat_boost, 0.20, places=3)

    def test_hue_transition_uses_shortest_wrapped_arc(self):
        palette = ColorPalette()
        palette.base_hue = 0.99
        palette.set_genre("electronic")
        before = palette.base_hue
        palette.update(0.0, 0.0, 0.0, 0)
        delta = (palette.base_hue - before + 0.5) % 1.0 - 0.5
        self.assertLess(delta, 0.0)
        self.assertLess(abs(delta), 0.02)


if __name__ == "__main__":
    unittest.main()
