import unittest
from dataclasses import replace
from unittest import mock

import pygame

from tests.render_host import RenderHost
from core.ui_manager import HUDState, UIManager


class UIManagerTests(unittest.TestCase):
    def setUp(self):
        self.host = RenderHost()
        pygame.init()
        self.addCleanup(pygame.quit)
        self.ui = UIManager(width=640)
        self.surface = pygame.Surface((640, 480))

    def state(self):
        return HUDState('test', 59.8, True, 'tv', 1, 'Cube', .7, 'Aurora',
                        2, 120., 'psytrance', True, True, 'test input', 'Preset 1')

    def test_full_hud_text_and_minimal_detail(self):
        rendered = []
        font = mock.Mock()
        font.render.side_effect = lambda text, *args: (
            rendered.append(text) or pygame.Surface((80, 20)))
        self.ui.font = self.ui.font_s = font
        self.ui.draw_hud(self.surface, self.state())
        self.assertEqual(rendered, [
            'psysuals vtest  [60 fps | GL | tv]', 'Mode 2: Cube (0.7) + BG: Aurora',
            'BPM: 120.0 (psytrance) [TAP] [AUTO]', 'Input: test input', 'Preset: Preset 1'])
        rendered.clear()
        self.ui.draw_hud(self.surface, replace(self.state(), level=1, use_gl=False,
                                              bg_name=None, preset_name=None))
        self.assertEqual(rendered, ['psysuals vtest  [60 fps | tv]', 'Mode 2: Cube (0.7)'])

    def test_picker_and_flash_caches_follow_target_dimensions(self):
        for size in ((640, 480), (320, 240)):
            surface = pygame.Surface(size)
            self.ui.draw_device_picker(surface, [], None)
            self.assertEqual(self.ui._picker_overlay.get_size(), size)
            self.ui.pick_sel = 1
            self.ui.draw_device_picker(surface, [(0, 'one'), (1, 'two')], 1)
            self.ui.draw_tap_flash(surface, 120., 100)
            self.assertEqual(self.ui._flash_surf.get_size(), size)
            self.assertGreater(int(pygame.surfarray.array3d(surface).sum()), 0)

    def test_font_resize_and_hud_background_cache(self):
        first_font = self.ui.font
        self.ui.draw_hud(pygame.Surface((1280, 480)), self.state())
        self.assertEqual(self.ui._last_width, 1280)
        self.assertIsNot(first_font, self.ui.font)
        lines = [pygame.Surface((100, 20))]
        self.ui.draw_hud_background(self.surface, lines)
        cached = self.ui._hud_bg
        self.ui.draw_hud_background(self.surface, lines)
        self.assertIs(cached, self.ui._hud_bg)
        self.ui.draw_hud_background(self.surface, [pygame.Surface((200, 20))])
        self.assertIsNot(cached, self.ui._hud_bg)
