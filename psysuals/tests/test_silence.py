import unittest

from core.silence import SilenceGate, SilenceThresholds


class SilenceGateTests(unittest.TestCase):
    def gate(self, silent=True):
        return SilenceGate(SilenceThresholds(.0035, .006, .0015, .003, .14, 2),
                           1024 / 44100, silent)

    def test_fresh_blocks_and_duplicate_generations(self):
        gate = self.gate()
        gate.update(.01, .01, 1., 1)
        gate.update(.01, .01, 1., 1)
        self.assertTrue(gate.is_silent)
        gate.update(.01, .01, 1.02, 2)
        self.assertFalse(gate.is_silent)
        gate.update(0., 0., 1.1, 3)
        self.assertFalse(gate.is_silent)
        gate.update(0., 0., 1.2, 4)
        self.assertTrue(gate.is_silent)

    def test_structured_quiet_music_exits_and_prevents_silence(self):
        gate = self.gate()
        for i in range(1, 10):
            gate.update(.001, .001, i * .1, i, music_activity=1.2)
            self.assertEqual(gate.is_silent, i < 2)

    def test_invalid_audio_breaks_loud_streak(self):
        gate = self.gate()
        for i, rms in enumerate((.01, float('nan'), .01)):
            gate.update(rms, .01, i * .02, i)
        self.assertTrue(gate.is_silent)
        self.assertEqual(gate.loud_blocks, 1)

    def test_missing_and_backward_timestamps_use_block_duration(self):
        gate = self.gate(False)
        for i, timestamp in enumerate((None, 1., .5, .5, float('inf'), 0.)):
            gate.update(0., 0., timestamp, i)
        self.assertFalse(gate.is_silent)
        gate.update(0., 0., 0., 6)
        self.assertTrue(gate.is_silent)

    def test_thresholds_and_block_duration_are_injected(self):
        gate = SilenceGate(SilenceThresholds(1., 2., 1., 2., .25, 1), .1)
        gate.update(2., 0., 0., 1)
        self.assertFalse(gate.is_silent)
        for i in range(2, 5):
            gate.update(0., 0., 0., i)
        self.assertTrue(gate.is_silent)
