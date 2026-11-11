import contextlib
import io
import unittest
from types import SimpleNamespace
from unittest import mock

import pygame

from tests.render_host import RenderHost
import psysualizer
import settings
from psysualizer import VisualizerApp


class AppLifecycleTests(unittest.TestCase):
    def setUp(self):
        self.host = RenderHost()

    def test_parser_exit_does_not_initialize_or_cleanup_sdl(self):
        with mock.patch.object(VisualizerApp, '_parse_args', side_effect=SystemExit(0)), \
             mock.patch.object(pygame.display, 'set_mode') as set_mode, \
             mock.patch.object(pygame, 'quit') as quit_sdl:
            with self.assertRaises(SystemExit):
                VisualizerApp()
        set_mode.assert_not_called()
        quit_sdl.assert_not_called()

    def test_constructor_failure_cleans_each_acquired_resource_without_saving(self):
        for stage in ('after-display', 'after-audio', 'after-effect'):
            with self.subTest(stage=stage), contextlib.ExitStack() as stack:
                renderer, audio = mock.Mock(), mock.Mock()
                audio.input_devices.return_value = []
                effect = SimpleNamespace(IS_GL=False, release=mock.Mock(), set_frame_context=mock.Mock())
                display = SimpleNamespace(renderer=renderer, screen=pygame.Surface((64, 48)),
                                          kill_children=mock.Mock())
                display.target = display.screen
                args = SimpleNamespace(low_spec=False, gl=False, visual_profile=None,
                                       mode=None, span_child=False)
                stack.enter_context(mock.patch.object(VisualizerApp, '_parse_args', return_value=args))
                stack.enter_context(mock.patch.object(VisualizerApp, '_init_display'))
                stack.enter_context(mock.patch.object(VisualizerApp, '_setup_signals'))
                stack.enter_context(mock.patch.object(psysualizer, 'DisplayManager', return_value=display))
                stack.enter_context(mock.patch.object(psysualizer, 'AudioEngine', return_value=audio))
                stack.enter_context(mock.patch.object(psysualizer.atexit, 'register'))
                stack.enter_context(mock.patch.object(settings, 'load', return_value=dict(settings._DEFAULTS)))
                save = stack.enter_context(mock.patch.object(settings, 'save'))
                quit_sdl = stack.enter_context(mock.patch.object(pygame, 'quit'))
                stack.enter_context(mock.patch.object(pygame.display, 'set_mode'))
                ui = stack.enter_context(mock.patch.object(psysualizer, 'UIManager'))
                factory = mock.Mock(return_value=effect)
                stack.enter_context(mock.patch.object(psysualizer, 'MODES', [('Fake', factory)]))
                if stage == 'after-display':
                    ui.side_effect = RuntimeError(stage)
                elif stage == 'after-audio':
                    factory.side_effect = RuntimeError(stage)
                else:
                    factory.side_effect = [effect, RuntimeError(stage)]
                with self.assertRaisesRegex(RuntimeError, stage):
                    VisualizerApp()
                display.kill_children.assert_called_once_with()
                renderer.release.assert_called_once_with()
                audio.release.assert_called_once_with()
                quit_sdl.assert_called_once_with()
                save.assert_not_called()
                if stage == 'after-effect':
                    effect.release.assert_called_once_with()
                else:
                    effect.release.assert_not_called()
                if stage != 'after-display':
                    audio.open_input_stream.assert_called_once()

    def test_cleanup_continues_after_each_release_and_save_failure(self):
        app = self.host.app()
        app._initialized = True
        app._save_settings = mock.Mock(side_effect=OSError('save failed'))
        resources = [mock.Mock() for _ in range(5)]
        app.postfx, app.vis, app.bg_vis, renderer, app.audio = resources
        for resource in resources:
            resource.release.side_effect = RuntimeError('release failed')
        app.display = SimpleNamespace(renderer=renderer, kill_children=mock.Mock())
        with mock.patch.object(pygame.display, 'set_mode'), \
             mock.patch.object(pygame, 'quit') as quit_sdl, \
             contextlib.redirect_stderr(io.StringIO()):
            app._cleanup()
            app._cleanup()
        for resource in resources:
            resource.release.assert_called_once_with()
        app.display.kill_children.assert_called_once_with()
        app._save_settings.assert_called_once_with()
        quit_sdl.assert_called_once_with()

    def test_child_cleanup_never_writes_parent_settings(self):
        app = self.host.app()
        app._initialized = True
        app.args = SimpleNamespace(span_child=True)
        with mock.patch.object(settings, 'save') as save, \
             mock.patch.object(pygame.display, 'set_mode'), mock.patch.object(pygame, 'quit'):
            app._cleanup()
        save.assert_not_called()
