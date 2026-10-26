import unittest

import config
from core.quality import QualityGovernor
from effects.phason import Phason
from effects.ferrofluid import Ferrofluid
from effects.slimemold import SlimeMold


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

    def test_new_effects_render_at_output_resolution_in_high_quality(self):
        old = (config.WIDTH, config.HEIGHT, config.QUALITY_SCALE)
        try:
            config.QUALITY_SCALE = 1.0
            for width, height in ((1920, 1080), (3840, 2160)):
                config.WIDTH, config.HEIGHT = width, height
                for effect_cls in (Phason, Ferrofluid, SlimeMold):
                    effect = object.__new__(effect_cls)
                    self.assertEqual(effect._render_size(), (width, height, 1), effect_cls.__name__)
            config.QUALITY_SCALE = 0.6
            config.WIDTH, config.HEIGHT = 1920, 1080
            self.assertEqual(object.__new__(Phason)._render_size(), (1152, 648, 1920 / 1152))
            self.assertEqual(object.__new__(SlimeMold)._render_size(), (1632, 918, 1920/1632))
            config.QUALITY_SCALE = 1.0
            config.WIDTH, config.HEIGHT = 320, 240
            self.assertEqual(object.__new__(SlimeMold)._render_size(), (106, 80, 3))
        finally:
            config.WIDTH, config.HEIGHT, config.QUALITY_SCALE = old


if __name__ == "__main__":
    unittest.main()
