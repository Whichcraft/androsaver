import os
import subprocess
import sys
import unittest


class ConfigSeedTests(unittest.TestCase):
    def read_seed(self, value):
        env = os.environ.copy()
        if value is None:
            env.pop("PSYSUALS_SEED", None)
        else:
            env["PSYSUALS_SEED"] = value
        result = subprocess.run(
            [sys.executable, "-c", "import config; print(config.RNG_SEED)"],
            env=env,
            capture_output=True,
            text=True,
            check=True,
        )
        return result.stdout.strip(), result.stderr

    def test_seed_values_and_invalid_fallback(self):
        self.assertEqual(self.read_seed(None)[0], "0")
        self.assertEqual(self.read_seed("")[0], "0")
        self.assertEqual(self.read_seed("0")[0], "0")
        self.assertEqual(self.read_seed("-7")[0], str((2 ** 32) - 7))
        stdout, stderr = self.read_seed("not-an-integer")
        self.assertEqual(stdout, "0")
        self.assertIn("invalid PSYSUALS_SEED", stderr)


if __name__ == "__main__":
    unittest.main()
