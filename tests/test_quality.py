import unittest

import config
from core.quality import QualityGovernor


class QualityGovernorTests(unittest.TestCase):
    def setUp(self):
        self.old = (config.QUALITY_TIER, config.QUALITY_SCALE)

    def tearDown(self):
        config.QUALITY_TIER, config.QUALITY_SCALE = self.old

    def test_slow_frames_step_down_and_publish_scale(self):
        governor = QualityGovernor(fps=60)
        for _ in range(governor._WINDOW):
            governor.observe(30.0)
        self.assertEqual(governor.tier, 1)
        self.assertAlmostEqual(config.QUALITY_SCALE, 0.8)

    def test_cooldown_prevents_quality_flapping(self):
        governor = QualityGovernor(fps=60)
        for _ in range(governor._WINDOW):
            governor.observe(30.0)
        tier = governor.tier
        for _ in range(10):
            governor.observe(1.0)
        self.assertEqual(governor.tier, tier)

    def test_invalid_and_disabled_samples_do_not_change_tier(self):
        governor = QualityGovernor(fps=60)
        for value in (float("nan"), float("inf"), -1.0):
            governor.observe(value)
        governor.observe(100.0, enabled=False)
        self.assertEqual(governor.tier, 2)
        self.assertAlmostEqual(config.QUALITY_SCALE, 1.0)


if __name__ == "__main__":
    unittest.main()
