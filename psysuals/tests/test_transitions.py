import unittest
from types import SimpleNamespace
from unittest import mock

from tests.render_host import RenderHost
from core.transitions import ParameterTransition, PresetTransition
from psysualizer import VisualizerApp


class TransitionTests(unittest.TestCase):
    def setUp(self):
        self.host = RenderHost()

    def test_preset_start_midpoint_end_and_bounds(self):
        morph = PresetTransition(10, 20, 2, 3, True, .5, 1.5, 80, 180, 40, 80)
        self.assertEqual(morph.sample(0).gain, .5)
        self.assertFalse(morph.sample(19).switch_now)
        midpoint = morph.sample(20)
        self.assertEqual((midpoint.gain, midpoint.alpha, midpoint.cf_frames), (1., 130, 60))
        self.assertTrue(midpoint.switch_now)
        morph.switched = True
        self.assertFalse(morph.sample(21).switch_now)
        self.assertTrue(morph.sample(40).complete)
        self.assertEqual(morph.sample(40).gain, 1.5)

    def test_parameter_keys_intersect_and_complete(self):
        morph = ParameterTransition(10, 20, {'shared': 2., 'old': 4.},
                                    {'shared': 6., 'new': 8.})
        self.assertEqual(morph.sample(0), ({'shared': 2.}, False))
        self.assertEqual(morph.sample(20), ({'shared': 4.}, False))
        self.assertEqual(morph.sample(30), ({'shared': 6.}, True))

    def test_replacement_and_midpoint_switch_override_gain_reset(self):
        app = self.host.app()
        app.tick, app.bpm = 0, 120.
        app.mode_idx, app.bg_mode_i, app.bg_on = 0, 0, False
        app.effect_gain, app.bg_alpha, app.cf_frames = .5, 80, 40
        def switch(index):
            app.mode_idx = index
            app.effect_gain = self.host.default_effect_gain
        app._switch_mode = mock.Mock(side_effect=switch)
        app._set_background_mode = mock.Mock()
        app._start_preset_morph({'mode_idx': 1, 'intensity': 1.5})
        with mock.patch.object(app, 'fps', 60):
            app._start_preset_morph({'mode_idx': 2, 'intensity': 1.5})
        self.assertEqual(app._preset_morph.duration, 240)
        app.tick = 120
        app._advance_preset_morph()
        self.assertEqual(app.effect_gain, 1.)
        app._advance_preset_morph()
        app._switch_mode.assert_called_once_with(2)
        app.tick = 240
        app._advance_preset_morph()
        self.assertIsNone(app._preset_morph)
        self.assertEqual(app.effect_gain, 1.5)

    def test_fallback_duration_and_parameter_application(self):
        app = self.host.app()
        app.tick, app.bpm = 0, 0.
        app.mode_idx, app.bg_mode_i, app.bg_on = 0, 0, False
        app.effect_gain, app.bg_alpha, app.cf_frames = .7, 102, 45
        with mock.patch.multiple(app, bpm=0., fps=30):
            app._start_preset_morph({})
        self.assertEqual(app._preset_morph.duration, 120)
        app._parameter_morph = ParameterTransition(0, 10, {'a': 0.}, {'a': 1.})
        app.vis = SimpleNamespace(set_morph_values=mock.Mock())
        app.tick = 10
        app._advance_parameter_morph()
        app.vis.set_morph_values.assert_called_once_with({'a': 1.})
        self.assertIsNone(app._parameter_morph)
