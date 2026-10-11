import unittest

import benchmarks


class BenchmarkCliTests(unittest.TestCase):
    def test_cpu_only_defaults_and_duration(self):
        args = benchmarks.parse_args(["--cpu-only", "--duration", "0.25"])
        self.assertTrue(args.cpu_only)
        self.assertFalse(args.gl)
        self.assertEqual(args.duration, 0.25)

    def test_gl_mode_is_mutually_exclusive_and_selectable(self):
        args = benchmarks.parse_args(["--gl"])
        self.assertTrue(args.gl)
        self.assertFalse(args.cpu_only)

    def test_non_positive_duration_is_rejected(self):
        with self.assertRaises(SystemExit):
            benchmarks.parse_args(["--duration", "0"])

    def test_resolution_and_effect_selection(self):
        args = benchmarks.parse_args(["--resolution", "4k", "--effects", "Phason", "Mandelbox"])
        self.assertEqual(args.resolution, "4k")
        self.assertEqual(args.effects, ["Phason", "Mandelbox"])

    def test_unknown_effect_is_rejected(self):
        with self.assertRaises(SystemExit):
            benchmarks.parse_args(["--effects", "not-an-effect"])


if __name__ == "__main__":
    unittest.main()
