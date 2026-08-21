import unittest
from collections import deque

import numpy as np

import config
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
    def _silence_app(self, is_silent):
        app = VisualizerApp.__new__(VisualizerApp)
        app.is_silent = is_silent
        app._silence_last_generation = None
        app._silence_last_audio_time = None
        app._silence_quiet_seconds = 0.0
        app._silence_loud_blocks = 0
        return app

    def test_silence_entry_uses_audio_blocks_not_render_frames(self):
        app = self._silence_app(False)
        block_seconds = 1024 / 44100
        for generation in range(1, 7):
            app._update_silence_state(0.0, 0.0, generation * block_seconds, generation)
            self.assertFalse(app.is_silent)
            # A duplicate render update must not advance the gate.
            app._update_silence_state(0.0, 0.0, generation * block_seconds, generation)
        app._update_silence_state(0.0, 0.0, 7 * block_seconds, 7)
        self.assertTrue(app.is_silent)

    def test_silence_exit_requires_two_fresh_loud_blocks(self):
        app = self._silence_app(True)
        app._update_silence_state(0.01, 0.01, 1.0, 1)
        self.assertTrue(app.is_silent)
        app._update_silence_state(0.01, 0.01, 1.02, 2)
        self.assertFalse(app.is_silent)

    def test_invalid_audio_does_not_exit_silence(self):
        app = self._silence_app(True)
        app._update_silence_state(float("nan"), 0.0, 1.0, 1)
        app._update_silence_state(0.01, 0.01, 1.02, 2)
        self.assertTrue(app.is_silent)
        self.assertTrue(np.isfinite(app._silence_quiet_seconds))

    def test_render_rate_does_not_change_silence_timing(self):
        outcomes = []
        for repeats in (1, 2, 5):
            app = self._silence_app(False)
            block_seconds = 1024 / 44100
            for generation in range(1, 8):
                for _ in range(repeats):
                    app._update_silence_state(
                        0.0, 0.0, generation * block_seconds, generation
                    )
            outcomes.append(app.is_silent)
        self.assertEqual(outcomes, [True, True, True])

    def _phase_app(self, silent=False):
        app = VisualizerApp.__new__(VisualizerApp)
        app.tick = 0
        app.is_silent = silent
        app.using_tap = False
        app._phase_anchor_tick = 0
        app._phase_last_onset = 0.0
        app._phase_anchor_time = 0.0
        return app

    def test_beat_phase_is_zero_at_onset_and_bounded(self):
        app = self._phase_app()
        app._update_beat_phase(10.0, 120.0, 10.0)
        self.assertAlmostEqual(config.BEAT_PHASE, 0.0)
        app.tick = 15
        app._update_beat_phase(10.25, 120.0, 10.0)
        self.assertAlmostEqual(config.BEAT_PHASE, 0.5)
        self.assertGreaterEqual(config.BEAT_PHASE, 0.0)
        self.assertLess(config.BEAT_PHASE, 1.0)

    def test_beat_phase_uses_idle_fallback_without_timing(self):
        app = self._phase_app()
        app.tick = 12
        app._update_beat_phase(float("nan"), 0.0, 0.0)
        self.assertGreaterEqual(config.BEAT_PHASE, 0.0)
        self.assertLess(config.BEAT_PHASE, 1.0)

    def test_preset_morph_interpolates_and_switches_at_midpoint(self):
        app = VisualizerApp.__new__(VisualizerApp)
        app.tick = 0
        app.bpm = 120.0
        app.mode_idx = 0
        app.bg_mode_i = 0
        app.bg_on = False
        app.effect_gain = 0.5
        app.bg_alpha = 80
        app.cf_frames = 40
        app._preset_morph = None
        switches = []
        app._switch_mode = lambda index: switches.append(("fg", index))
        app._set_background_mode = lambda index: switches.append(("bg", index))
        app._start_preset_morph({"mode_idx": 2, "bg_mode_i": 3, "bg_on": True,
                                 "intensity": 1.5, "bg_alpha": 180, "cf_frames": 80})
        duration = app._preset_morph["duration"]
        app.tick = duration // 2
        app._advance_preset_morph()
        self.assertTrue(switches)
        self.assertGreater(app.effect_gain, 0.5)
        self.assertLess(app.effect_gain, 1.5)
        app.tick = duration
        app._advance_preset_morph()
        self.assertIsNone(app._preset_morph)
        self.assertAlmostEqual(app.effect_gain, 1.5)
        self.assertEqual(app.bg_alpha, 180)
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
