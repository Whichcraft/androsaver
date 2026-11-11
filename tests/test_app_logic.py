import unittest
from collections import deque
from types import SimpleNamespace
from unittest import mock
from core.audio_engine import AudioSnapshot
from core.audio_response import AudioResponse
from core.silence import SilenceGate, SilenceThresholds
from effects.palette import ColorPalette

import numpy as np

from tests.render_host import RenderHost
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
    def setUp(self):
        self.host = RenderHost()

    def test_update_consumes_only_one_audio_snapshot(self):
        app = self.host.app()
        snapshot = AudioSnapshot(np.zeros(1024), np.zeros(512), 1., .4, .6,
                                 120., 10., 7, (.1, .2, .3), 9.5)
        app.audio = SimpleNamespace(
            get_snapshot=mock.Mock(return_value=snapshot),
            beat_tracker=SimpleNamespace(enabled=False),
            stream=object(),
        )
        app._advance_preset_morph = mock.Mock()
        app._advance_parameter_morph = mock.Mock()
        app._update_genre = mock.Mock()
        gate = SilenceGate(SilenceThresholds(.0035, .006, .0015, .003, .14, 2),
                           1024 / 44100, False)
        app.response = AudioResponse(gate)
        app.response.update = mock.Mock(wraps=app.response.update)
        app.tap_bpm_expiry = 0.
        app._phase_anchor_tick = 0
        app.display = SimpleNamespace(reposition_window_fix=mock.Mock(), screen=__import__("pygame").Surface((320,240)))
        app.is_silent = False
        app.tap_bpm = 0.
        app.energy_hist = deque(maxlen=40)
        app.energy_sum = app.beat_decay = 0.
        app.effect_gain = 1.
        app.auto_gain = False
        app.rms_buf = deque(maxlen=30)
        app.tick = 1
        app.span_mode = False
        # Isolate all self.host publication and palette state from other tests.
        with mock.patch.object(app.palette, "update"):
            app._update()
        app.audio.get_snapshot.assert_called_once_with()
        app.response.update.assert_called_once()
        self.assertIs(app.response.update.call_args.args[0], snapshot)
        self.assertEqual(app.response.gate.last_generation, 7)
        self.assertEqual(app.response._phase_last_onset, 9.5)
        self.assertIs(app.waveform, snapshot.waveform)
        self.assertIs(app.fft, snapshot.fft)

    def _silence_app(self, is_silent):
        app = self.host.app()
        app.is_silent = is_silent
        app.silence_gate = SilenceGate(
            SilenceThresholds(.0035, .006, .0015, .003, .14, 2),
            1024 / 44100, is_silent)
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
        self.assertTrue(np.isfinite(app.silence_gate.quiet_seconds))

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
        app = self.host.app()
        app.tick = 0
        app.is_silent = silent
        app.using_tap = False
        app._phase_anchor_tick = 0
        app.response = AudioResponse(SilenceGate(
            SilenceThresholds(.0035, .006, .0015, .003, .14, 2), 1024 / 44100))
        return app

    def test_beat_phase_is_zero_at_onset_and_bounded(self):
        app = self._phase_app()
        app._update_beat_phase(10.0, 120.0, 10.0)
        self.assertAlmostEqual(app.frame.phase, 0.0)
        app.tick = 15
        app._update_beat_phase(10.25, 120.0, 10.0)
        self.assertAlmostEqual(app.frame.phase, 0.5)
        self.assertGreaterEqual(app.frame.phase, 0.0)
        self.assertLess(app.frame.phase, 1.0)

    def test_beat_phase_uses_idle_fallback_without_timing(self):
        app = self._phase_app()
        app.tick = 12
        app._update_beat_phase(float("nan"), 0.0, 0.0)
        self.assertGreaterEqual(app.frame.phase, 0.0)
        self.assertLess(app.frame.phase, 1.0)

    def test_preset_morph_interpolates_and_switches_at_midpoint(self):
        app = self.host.app()
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
        duration = app._preset_morph.duration
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
        app = self.host.app()
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
        app = self.host.app()
        app.beat = 1.0
        app.effect_gain = 1.5
        app.response = AudioResponse(SilenceGate(
            SilenceThresholds(.0035, .006, .0015, .003, .14, 2), 1024 / 44100))
        app.response.rms_buf = deque([0.10])
        app.auto_gain = True

        self.assertAlmostEqual(app._compute_draw_beat(), 0.75)

        app.auto_gain = False
        self.assertAlmostEqual(app._compute_draw_beat(), 1.5)


if __name__ == "__main__":
    unittest.main()
