import unittest
from types import SimpleNamespace
from unittest import mock

import pygame
from tests.render_host import RenderHost
import settings
import psysualizer

from effects import MODES
from psysualizer import RECIPES, VisualizerApp


class RecipeTests(unittest.TestCase):
    def setUp(self):
        self.host = RenderHost()

    def make_app(self, saved=None):
        # Exercise real app initialization with fake devices/effects and no window.
        self.real_load = settings.load
        self.addCleanup(pygame.quit)
        pygame.init()
        surface = pygame.Surface((160, 120))
        display = SimpleNamespace(renderer=None, screen=surface, target=surface,
                                  kill_children=mock.Mock(), display_idx=0)
        modes = [(name, mock.Mock(return_value=mock.Mock(
            IS_GL=False, get_morph_values=lambda: {}))) for name, _ in MODES]
        args = SimpleNamespace(low_spec=False, visual_profile=None, mode=None,
                               gl=False, span_child=False)
        patches = [
            mock.patch.object(psysualizer, "MODES", modes),
            mock.patch.object(VisualizerApp, "_parse_args", return_value=args),
            mock.patch.object(VisualizerApp, "_init_display"),
            mock.patch.object(VisualizerApp, "_setup_signals"),
            mock.patch.object(VisualizerApp, "_init_audio"),
            mock.patch.object(psysualizer, "AudioEngine",
                              return_value=SimpleNamespace(active_dev=None)),
            mock.patch.object(psysualizer, "DisplayManager", return_value=display),
            mock.patch.object(psysualizer.atexit, "register"),
            mock.patch.object(settings, "load", return_value={
                **settings._DEFAULTS, **(saved or {})}),
            mock.patch.object(settings, "load_presets", return_value=[]),
        ]
        for patch in patches:
            patch.start()
            self.addCleanup(patch.stop)
        return VisualizerApp(), modes

    def test_recipes_construct_declared_effects_and_restore_saved_background(self):
        import os
        import tempfile
        app, modes = self.make_app()
        for index, recipe in enumerate(RECIPES):
            with self.subTest(recipe=recipe["name"]):
                app._apply_recipe(index)
                self.assertEqual(app.name, recipe["foreground"])
                self.assertEqual(app.bg_name, recipe["background"])
                self.assertIs(app.vis, modes[app.mode_idx][1].return_value)
                self.assertIs(app.bg_vis, modes[app.bg_mode_i][1].return_value)
                self.assertEqual(app.bg_alpha, recipe["bg_alpha"])
                self.assertTrue(app.bg_on)
                with tempfile.TemporaryDirectory() as directory:
                    with mock.patch.object(settings, "_CONFIG_DIR", directory), \
                         mock.patch.object(settings, "_SETTINGS_FILE",
                                           os.path.join(directory, "settings.json")):
                        app._save_settings()
                        with mock.patch.object(settings, "load", side_effect=self.real_load):
                            restored = VisualizerApp()
                self.assertEqual(restored.bg_name, recipe["background"])
                self.assertEqual(restored.bg_alpha, recipe["bg_alpha"])
                self.assertTrue(restored.bg_on)

    def test_background_indices_wrap_across_full_registry(self):
        app, modes = self.make_app()
        for index in (-1, len(modes), len(modes) + 3):
            with self.subTest(index=index):
                previous = app.bg_vis
                app._set_background_mode(index)
                previous.release.assert_called()
                self.assertEqual(app.bg_mode_i, index % len(modes))
                self.assertEqual(app.bg_name, modes[index % len(modes)][0])

    def test_shift_b_cycles_only_first_nine_backgrounds(self):
        app, _ = self.make_app()
        app.ui.picking = False
        event = pygame.event.Event(pygame.KEYDOWN, key=pygame.K_b,
                                   mod=pygame.KMOD_SHIFT)
        for start in (0, 8, 25):
            with self.subTest(start=start):
                app.bg_mode_i = start
                with mock.patch.object(pygame.event, "get", return_value=[event]):
                    app._handle_events()
                self.assertEqual(app.bg_mode_i, (start + 1) % 9)

    def test_recipes_reference_registered_modes_and_valid_alpha(self):
        names = {name for name, _ in MODES}
        self.assertEqual(len(RECIPES), 4)
        for recipe in RECIPES:
            self.assertIn(recipe["foreground"], names)
            self.assertIn(recipe["background"], names)
            self.assertNotEqual(recipe["foreground"], recipe["background"])
            self.assertGreaterEqual(recipe["bg_alpha"], 0)
            self.assertLessEqual(recipe["bg_alpha"], 255)


if __name__ == "__main__":
    unittest.main()
