import unittest
from collections import deque

from psysualizer import VisualizerApp


class _GenreAudio:
    def __init__(self):
        self.detect_calls = 0
        self.applied = []

    def detect_genre(self):
        self.detect_calls += 1
        return "rock"

    def apply_genre_weights(self, genre):
        self.applied.append(genre)


class AppLogicTests(unittest.TestCase):
    def test_genre_polling_continues_after_silence(self):
        app = VisualizerApp.__new__(VisualizerApp)
        app.audio = _GenreAudio()
        app._genre_check_cd = 1
        app.is_silent = False
        app.current_genre = "detecting..."

        app._update_genre()
        app._update_genre()

        self.assertEqual(app.audio.detect_calls, 2)
        self.assertEqual(app.audio.applied, ["rock", "rock"])
        self.assertEqual(app.current_genre, "rock")

    def test_auto_gain_keeps_effect_intensity(self):
        app = VisualizerApp.__new__(VisualizerApp)
        app.beat = 1.0
        app.effect_gain = 1.5
        app.target_rms = 0.05
        app.rms_buf = deque([0.10])
        app.auto_gain = True

        self.assertAlmostEqual(app._compute_draw_beat(), 0.75)

        app.auto_gain = False
        self.assertAlmostEqual(app._compute_draw_beat(), 1.5)


if __name__ == "__main__":
    unittest.main()
