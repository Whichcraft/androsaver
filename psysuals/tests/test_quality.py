import unittest

from tests.render_host import RenderHost
from core.quality import QualityGovernor
from effects.phason import Phason
from effects.ferrofluid import Ferrofluid
from effects.slimemold import SlimeMold


class QualityGovernorTests(unittest.TestCase):
    def setUp(self):
        self.host = RenderHost()
        self.old = (self.host.quality_tier, self.host.quality_scale)

    def tearDown(self):
        self.host.quality_tier, self.host.quality_scale = self.old

    def test_slow_frames_step_down_and_publish_scale(self):
        governor = QualityGovernor(fps=60)
        for _ in range(governor._WINDOW):
            governor.observe(30.0)
        self.assertEqual(governor.tier, 1)
        self.assertAlmostEqual(governor.decision.scale, 0.8)

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
        self.assertAlmostEqual(governor.decision.scale, 1.0)

    def test_new_effects_render_at_output_resolution_in_high_quality(self):
        old = (self.host.width, self.host.height, self.host.quality_scale)
        try:
            self.host.quality_scale = 1.0
            for width, height in ((1920, 1080), (3840, 2160)):
                self.host.width, self.host.height = width, height
                for effect_cls in (Phason, Ferrofluid, SlimeMold):
                    effect = self.host.bare(effect_cls)
                    self.assertEqual(effect._render_size(), (width, height, 1), effect_cls.__name__)
            self.host.quality_scale = 0.6
            self.host.width, self.host.height = 1920, 1080
            self.assertEqual(self.host.bare(Phason)._render_size(), (1152, 648, 1920 / 1152))
            self.assertEqual(self.host.bare(SlimeMold)._render_size(), (1632, 918, 1920/1632))
            self.host.quality_scale = 1.0
            self.host.width, self.host.height = 320, 240
            self.assertEqual(self.host.bare(SlimeMold)._render_size(), (106, 80, 3))
        finally:
            self.host.width, self.host.height, self.host.quality_scale = old


if __name__ == "__main__":
    unittest.main()
