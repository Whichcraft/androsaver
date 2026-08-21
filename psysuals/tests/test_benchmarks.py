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


if __name__ == "__main__":
    unittest.main()
