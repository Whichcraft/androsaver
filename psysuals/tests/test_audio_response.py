import unittest
from dataclasses import replace

import numpy as np

from core.audio_engine import AudioSnapshot
from core.audio_response import AudioResponse
from core.silence import SilenceGate, SilenceThresholds


class AudioResponseTests(unittest.TestCase):
    def response(self, silent=False):
        return AudioResponse(SilenceGate(
            SilenceThresholds(.0035, .006, .0015, .003, .14, 2), 1024/44100, silent))

    def snapshot(self):
        return AudioSnapshot(np.full(1024, .01), np.full(512, .01),
                             0., .4, .6, 120., 10., 1, (.1, .2, .3), 9.5)

    def update(self, response, snapshot=None, **overrides):
        args = dict(dt=1/60, tick=1, fps=60, tap_bpm=0., tap_expiry=0.,
                    tap_anchor_tick=0, now=10., previous_bpm=120., gain=1., auto_gain=False)
        args.update(overrides)
        return response.update(snapshot or self.snapshot(), **args)

    def test_equal_elapsed_decay_and_gain(self):
        outcomes = []
        for fps in (30, 60, 120):
            response = self.response()
            response.beat_decay = 2.
            for tick in range(fps):
                values = self.update(response, dt=1/fps, fps=fps, tick=tick, gain=1.5)
            outcomes.append(values.beat)
            self.assertAlmostEqual(values.draw_beat, values.beat * 1.5)
        for value in outcomes:
            self.assertAlmostEqual(value, 2. * .9 ** 60)

    def test_silent_floors_clear_energy_and_envelopes(self):
        response = self.response(True)
        snapshot = replace(self.snapshot(), waveform=np.zeros(1024), fft=np.zeros(512),
                           raw_beat=100., mid_energy=0., treble_energy=0., envelopes=(0., 0., 0.))
        values = self.update(response, snapshot)
        self.assertTrue(values.is_silent)
        self.assertGreater(values.beat, 0.)
        self.assertGreater(values.mid, 0.)
        self.assertGreater(values.treble, 0.)
        self.assertEqual(values.envelopes, (0., 0., 0.))
        self.assertEqual(response.energy_sum, 0.)
        self.assertEqual(list(response.energy_hist), [0.])

    def test_tap_expiry_phase_and_envelope_bounds(self):
        response = self.response()
        values = self.update(response, replace(self.snapshot(), envelopes=(-1., 2., 9.)),
                             tap_bpm=150., tap_expiry=11., tick=12)
        self.assertEqual(values.bpm, 150.)
        self.assertTrue(values.using_tap)
        self.assertAlmostEqual(values.phase, .5)
        self.assertEqual(values.envelopes, (0., 2., 6.))
        expired = self.update(response, tap_bpm=150., tap_expiry=10.)
        self.assertEqual(expired.bpm, 120.)
        self.assertFalse(expired.using_tap)
        self.assertGreaterEqual(expired.phase, 0.)
        self.assertLess(expired.phase, 1.)

    def test_rolling_energy_sum_and_auto_gain_limits(self):
        response = self.response()
        for i in range(100):
            self.update(response, replace(self.snapshot(), raw_beat=float(i % 7)))
        self.assertAlmostEqual(response.energy_sum, sum(response.energy_hist))
        response.rms_buf.clear()
        response.rms_buf.append(.1)
        self.assertAlmostEqual(response.compute_draw_beat(1., True, 1.5), .75)
        response.rms_buf.clear()
        response.rms_buf.append(0.)
        self.assertEqual(response.compute_draw_beat(1., True, 1.5), 3.)
