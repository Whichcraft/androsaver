import json
import os
import tempfile
import unittest
from unittest import mock

import settings


class SettingsTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.files = (
            settings._CONFIG_DIR,
            settings._SETTINGS_FILE,
            settings._PRESETS_FILE,
        )
        settings._CONFIG_DIR = self.tmp.name
        settings._SETTINGS_FILE = os.path.join(self.tmp.name, "settings.json")
        settings._PRESETS_FILE = os.path.join(self.tmp.name, "presets.json")

    def tearDown(self):
        settings._CONFIG_DIR, settings._SETTINGS_FILE, settings._PRESETS_FILE = self.files
        self.tmp.cleanup()

    def test_load_normalizes_corrupt_values(self):
        with open(settings._SETTINGS_FILE, "w") as fh:
            json.dump({
                "mode_idx": "not-an-int",
                "bg_alpha": 999,
                "cf_frames": -20,
                "effect_gain": 9,
                "show_hud": "yes",
            }, fh)

        loaded = settings.load()

        self.assertEqual(loaded["mode_idx"], settings._DEFAULTS["mode_idx"])
        self.assertEqual(loaded["bg_alpha"], 255)
        self.assertEqual(loaded["cf_frames"], 0)
        self.assertEqual(loaded["effect_gain"], 2.0)
        self.assertTrue(loaded["show_hud"])

    def test_load_handles_non_object_json(self):
        with open(settings._SETTINGS_FILE, "w") as fh:
            json.dump(["wrong", "root", "type"], fh)

        self.assertEqual(settings.load(), settings._DEFAULTS)

    def test_load_handles_invalid_utf8(self):
        with open(settings._SETTINGS_FILE, "wb") as fh:
            fh.write(b'{"mode_idx": 1}\xff')

        self.assertEqual(settings.load(), settings._DEFAULTS)

    def test_load_handles_unavailable_directory(self):
        with mock.patch.object(settings, "_ensure_config_dir", return_value=False):
            self.assertEqual(settings.load(), settings._DEFAULTS)

    def test_save_write_failure_does_not_escape(self):
        with mock.patch.object(settings, "_ensure_config_dir", return_value=True), \
             mock.patch("builtins.open", side_effect=PermissionError("read-only")):
            settings.save({"mode_idx": 1})

    def test_presets_filter_and_normalize_entries(self):
        with open(settings._PRESETS_FILE, "w") as fh:
            json.dump([
                {"name": "valid", "mode_idx": 4, "intensity": 4, "bg_mode_i": -3},
                {"name": "bad-mode", "mode_idx": "4"},
                {"mode_idx": 2},
            ], fh)

        presets = settings.load_presets()

        self.assertEqual(len(presets), 1)
        self.assertEqual(presets[0]["name"], "valid")
        self.assertEqual(presets[0]["intensity"], 2.0)
        self.assertEqual(presets[0]["bg_mode_i"], 0)

    def test_presets_ignore_invalid_utf8_and_non_object_entries(self):
        with open(settings._PRESETS_FILE, "wb") as fh:
            fh.write(b'{"valid":{"mode_idx":2},"bad":42}\xff')

        self.assertEqual(settings.load_presets(), [])

    def test_atomic_preset_write_round_trip(self):
        settings.save_preset("Test", {"mode_idx": 2, "intensity": 0.8})
        self.assertEqual(settings.load_presets()[0]["name"], "Test")
        self.assertTrue(os.path.exists(settings._PRESETS_FILE))


if __name__ == "__main__":
    unittest.main()
