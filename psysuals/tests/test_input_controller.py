import unittest
from collections import deque
from types import SimpleNamespace
from unittest import mock

import pygame

from tests.render_host import RenderHost
from core.input_controller import handle_events


class InputControllerTests(unittest.TestCase):
    def setUp(self):
        self.host = RenderHost()

    def app(self):
        return SimpleNamespace(
            ui=SimpleNamespace(picking=False, pick_sel=0, pane_open=False, pane_sel=0),
            audio=SimpleNamespace(input_devices=lambda: [(3, 'first'), (7, 'second')],
                                  active_dev=3, start_input_stream=mock.Mock()),
            display=SimpleNamespace(kill_children=mock.Mock(), spawn_span_children=mock.Mock(),
                                    toggle_fullscreen=mock.Mock()),
            args=SimpleNamespace(span_child=False), visual_profile='auto', span_mode=False, span_vis2_idx=0,
            mode_idx=2, bg_mode_i=8, name='Cube', vis=SimpleNamespace(adjust_particles=mock.Mock()),
            _quit=mock.Mock(), _switch_mode=mock.Mock(), _pane_adjust=mock.Mock(),
            _set_background_mode=mock.Mock(), _apply_recipe=mock.Mock(),
            _release_for_display_change=mock.Mock(), _rebuild_effects=mock.Mock(),
            effect_gain=.7, auto_gain=False, bg_on=False, recipe_idx=-1, postfx_mode=0,
            show_hud=True, hud_level=2, tap_times=deque(maxlen=4), tick=10,
            presets=[], active_preset=-1, _start_preset_morph=mock.Mock(), bg_alpha=102, cf_frames=45,
        )

    def key(self, app, key, shift=False):
        handle_events(app, [pygame.event.Event(pygame.KEYDOWN, key=key,
                       mod=pygame.KMOD_SHIFT if shift else 0)], '/app/psysualizer.py')

    def test_picker_consumes_keys_before_quit_or_mode_actions(self):
        app = self.app()
        app.ui.picking = True
        self.key(app, pygame.K_DOWN)
        self.assertEqual(app.ui.pick_sel, 1)
        self.key(app, pygame.K_RETURN)
        app.audio.start_input_stream.assert_called_once_with(7)
        app.ui.picking = True
        self.key(app, pygame.K_SPACE)
        app._switch_mode.assert_not_called()
        self.key(app, pygame.K_ESCAPE)
        self.assertFalse(app.ui.picking)
        app._quit.assert_not_called()
        self.key(app, pygame.K_ESCAPE)
        app._quit.assert_called_once()

    def test_pane_precedes_particle_and_mode_controls(self):
        app = self.app()
        app.name = 'FlowField'
        app.ui.pane_open = True
        self.key(app, pygame.K_UP)
        self.assertEqual(app.ui.pane_sel, 2)
        self.key(app, pygame.K_LEFT)
        app._pane_adjust.assert_called_once_with(-1)
        app.vis.adjust_particles.assert_not_called()
        app.ui.pane_open = False
        self.key(app, pygame.K_UP)
        self.key(app, pygame.K_DOWN)
        self.assertEqual(app.vis.adjust_particles.call_args_list,
                         [mock.call(2000), mock.call(-2000)])
        self.key(app, pygame.K_9)
        app._switch_mode.assert_called_with(8)
        handle_events(app, [pygame.event.Event(pygame.MOUSEBUTTONDOWN)], '/app/psysualizer.py')
        app._switch_mode.assert_called_with(3)

    def test_span_child_cannot_spawn_and_span_keys_take_precedence(self):
        app = self.app()
        app.args.span_child = True
        self.key(app, pygame.K_m, True)
        app.display.spawn_span_children.assert_not_called()
        app.args.span_child = False
        self.key(app, pygame.K_m, True)
        app.display.spawn_span_children.assert_called_once_with(0, '/app/psysualizer.py')
        self.assertTrue(app.span_mode)
        self.key(app, pygame.K_d)
        self.assertFalse(app.ui.picking)
        self.key(app, pygame.K_a)
        self.assertFalse(app.auto_gain)
        self.key(app, pygame.K_m, True)
        app.display.kill_children.assert_called_once()

    def test_shift_variants_and_live_profile(self):
        app = self.app()
        self.key(app, pygame.K_b, True)
        app._set_background_mode.assert_called_once_with(0)
        self.key(app, pygame.K_b)
        self.assertTrue(app.bg_on)
        self.key(app, pygame.K_r, True)
        app._apply_recipe.assert_called_once_with(0)
        self.key(app, pygame.K_x, True)
        self.assertEqual(app.postfx_mode, 1)
        self.key(app, pygame.K_h, True)
        self.assertEqual(app.hud_level, 0)
        self.assertFalse(app.show_hud)
        with mock.patch.object(app, 'visual_profile', 'auto'):
            self.key(app, pygame.K_v)
            self.assertEqual(app.visual_profile, 'laptop')

    def test_tap_tempo_and_preset_modifiers(self):
        app = self.app()
        with mock.patch('core.input_controller._time.monotonic', side_effect=[1., 1.5]):
            self.key(app, pygame.K_m)
            self.key(app, pygame.K_m)
        self.assertEqual(app.tap_bpm, 120.)
        self.assertEqual(app._phase_anchor_tick, 10)
        with mock.patch('core.input_controller.sett.save_preset') as save:
            self.key(app, pygame.K_p)
            save.assert_called_once()
        self.assertEqual(len(app.presets), 1)
        self.key(app, pygame.K_p, True)
        app._start_preset_morph.assert_called_once_with(app.presets[0])
