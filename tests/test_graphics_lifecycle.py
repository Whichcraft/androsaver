import types
import unittest
import sys
from unittest import mock

try:
    import pygame
    REAL_PYGAME = True
except Exception as exc:  # pragma: no cover - dependency-specific environment
    REAL_PYGAME = False
    pygame = types.ModuleType("pygame")
    pygame.OPENGL = 1
    pygame.DOUBLEBUF = 2
    pygame.NOFRAME = 4
    pygame.FULLSCREEN = 8
    pygame.SRCALPHA = 16

    class FakeSurface:
        def __init__(self, size, flags=0):
            self._size = size
            self._flags = flags

        def get_size(self):
            return self._size

        def get_flags(self):
            return self._flags

    def set_mode(size, flags=0):
        return FakeSurface(size, flags)

    pygame.display = types.SimpleNamespace(set_mode=set_mode)
    sys.modules["pygame"] = pygame

try:
    import config
    import gl_renderer
    from effects.flowfield import FlowField
    from core.display_manager import DisplayManager
    if REAL_PYGAME:
        from psysualizer import VisualizerApp
    else:
        VisualizerApp = None
    GRAPHICS_IMPORT_ERROR = None
except Exception as exc:  # pragma: no cover - dependency-specific environment
    config = None
    gl_renderer = None
    DisplayManager = None
    VisualizerApp = None
    GRAPHICS_IMPORT_ERROR = exc


class GraphicsLifecycleTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if REAL_PYGAME:
            pygame.init()
            pygame.display.set_mode((64, 48))

    @classmethod
    def tearDownClass(cls):
        if REAL_PYGAME:
            pygame.quit()

    def test_gl_unavailable_falls_back_to_cpu_display(self):
        args = types.SimpleNamespace(gl=True)
        manager = DisplayManager.__new__(DisplayManager)
        manager.args = args
        manager._gl_renderer_cls = None
        manager._has_moderngl = False
        manager.renderer = None
        manager.screen = None
        manager.target = None
        manager.fullscreen = True
        manager.display_idx = 0
        manager.num_displays = 1
        manager.xmonitors = []
        manager._xmove_target = None
        manager._libX11 = None

        with mock.patch("core.display_manager._load_gl_renderer", return_value=(None, False)):
            manager.open_display(0, False)

        self.assertFalse(args.gl)
        self.assertIsNone(manager.renderer)
        self.assertIs(manager.target, manager.screen)
        self.assertFalse(manager.screen.get_flags() & pygame.OPENGL)

    def test_display_error_retries_without_opengl_flags(self):
        if not REAL_PYGAME:
            self.skipTest("real Pygame is required for display fallback coverage")
        args = types.SimpleNamespace(gl=True)
        manager = DisplayManager.__new__(DisplayManager)
        manager.args = args
        manager._gl_renderer_cls = None
        manager._has_moderngl = False
        manager.renderer = None
        manager.screen = None
        manager.target = None
        manager.fullscreen = False
        manager.display_idx = 0
        manager.num_displays = 1
        manager.xmonitors = []
        manager._xmove_target = None
        manager._libX11 = None

        calls = []
        original = pygame.display.set_mode

        def fail_once(size, flags=0):
            calls.append(flags)
            if len(calls) == 1:
                raise pygame.error("context failed")
            return original(size, flags)

        with mock.patch("core.display_manager._load_gl_renderer", return_value=(object, True)), \
             mock.patch.object(pygame.display, "set_mode", side_effect=fail_once):
            manager.open_display(0, False)

        self.assertEqual(calls[0] & pygame.OPENGL, pygame.OPENGL)
        self.assertEqual(calls[1], 0)
        self.assertFalse(args.gl)

    def test_fade_surface_reallocates_on_target_resize(self):
        if not REAL_PYGAME:
            self.skipTest("real Pygame is required for Surface resize coverage")
        app = VisualizerApp.__new__(VisualizerApp)
        app.display = types.SimpleNamespace(target=pygame.Surface((32, 24), pygame.SRCALPHA))
        app._fade_surf = None

        first = app._make_fade(20)
        app.display.target = pygame.Surface((48, 36), pygame.SRCALPHA)
        second = app._make_fade(30)

        self.assertEqual(first.get_size(), (32, 24))
        self.assertEqual(second.get_size(), (48, 36))
        self.assertIsNot(first, second)

    def test_reduced_gl_target_gets_full_resolution_ui_surface(self):
        if not REAL_PYGAME:
            self.skipTest("real Pygame is required for UI compositing coverage")
        app = VisualizerApp.__new__(VisualizerApp)
        app.args = types.SimpleNamespace(gl=True)
        app.display = types.SimpleNamespace(
            target=pygame.Surface((640, 360), pygame.SRCALPHA),
            screen=pygame.Surface((1920, 1080)),
        )
        app._ui_surface = None

        app._present_surface = app._prepare_present_surface(app.display.target)

        self.assertEqual(app._present_surface.get_size(), (1920, 1080))
        self.assertEqual(app.display.target.get_size(), (640, 360))

    def test_forced_effect_rebuild_ignores_same_size_guard(self):
        if not REAL_PYGAME:
            self.skipTest("real Pygame is required for effect rebuild coverage")

        class FakeEffect:
            IS_GL = False

            def __init__(self, renderer=None):
                self.renderer = renderer
                self.released = False

            def release(self):
                self.released = True

        target = pygame.Surface((32, 24))
        app = VisualizerApp.__new__(VisualizerApp)
        app.vis = FakeEffect()
        app.bg_vis = FakeEffect()
        app.VisCls = FakeEffect
        app.bg_mode_i = 0
        app.display = types.SimpleNamespace(
            renderer=object(), screen=target, target=target,
        )
        app.args = types.SimpleNamespace(gl=False)
        app.prev_surf_scaled = None
        app._last_rebuild_size = (32, 24)

        with mock.patch("psysualizer.MODES", [("Fake", FakeEffect)]):
            app._rebuild_effects(force=True)

        self.assertIsInstance(app.vis, FakeEffect)
        self.assertIsInstance(app.bg_vis, FakeEffect)

    def test_gl_renderer_release_is_idempotent(self):
        class Resource:
            def __init__(self):
                self.releases = 0

            def release(self):
                self.releases += 1

        class Context:
            def buffer(self, data):
                return Resource()

        old_has = gl_renderer.HAS_MODERNGL
        try:
            gl_renderer.HAS_MODERNGL = True
            renderer = gl_renderer.GLRenderer(32, 24, ctx=Context())
            renderer.release()
            renderer.release()
        finally:
            gl_renderer.HAS_MODERNGL = old_has

    def test_shader_program_is_released_if_vao_creation_fails(self):
        class Resource:
            def __init__(self):
                self.released = False

            def release(self):
                self.released = True

        class Context:
            def __init__(self):
                self.program_resource = Resource()

            def buffer(self, data):
                return Resource()

            def program(self, **kwargs):
                return self.program_resource

            def vertex_array(self, *args):
                raise RuntimeError("vao failed")

        old_has = gl_renderer.HAS_MODERNGL
        try:
            gl_renderer.HAS_MODERNGL = True
            ctx = Context()
            renderer = gl_renderer.GLRenderer(32, 24, ctx=ctx)
            with self.assertRaises(RuntimeError):
                renderer.program("vertex", "fragment")
            self.assertTrue(ctx.program_resource.released)
            renderer.release()
        finally:
            gl_renderer.HAS_MODERNGL = old_has

    def test_surface_upload_buffers_support_alternating_sizes(self):
        class Texture:
            def __init__(self, size):
                self.size = size
                self.writes = []

            def release(self):
                pass

            def use(self, unit):
                pass

            def write(self, data):
                self.writes.append(data.copy())

        class Context:
            def texture(self, size, components):
                return Texture(size)

        renderer = gl_renderer.GLRenderer.__new__(gl_renderer.GLRenderer)
        renderer.ctx = Context()
        renderer._blit_upload_buf = None
        renderer._feedback_upload_buf = None
        renderer._upload_buf = None
        large = pygame.Surface((64, 48), pygame.SRCALPHA)
        small = pygame.Surface((16, 12), pygame.SRCALPHA)

        blit_tex = renderer._upload_surface(large, None, "_blit_upload_buf")
        feedback_tex = renderer._upload_surface(small, None, "_feedback_upload_buf")
        renderer._upload_surface(large, blit_tex, "_blit_upload_buf")
        renderer._upload_surface(small, feedback_tex, "_feedback_upload_buf")

        self.assertEqual(len(blit_tex.writes[-1]), 64 * 48 * 4)
        self.assertEqual(len(feedback_tex.writes[-1]), 16 * 12 * 4)

    def test_offscreen_cache_ownership_is_queryable(self):
        renderer = gl_renderer.GLRenderer.__new__(gl_renderer.GLRenderer)
        renderer.width = 32
        renderer.height = 24
        fbo = object()
        renderer._offscreen_cache = {(32, 24): (object(), fbo)}

        self.assertTrue(renderer.is_offscreen_current(fbo, 32, 24))
        self.assertFalse(renderer.is_offscreen_current(object(), 32, 24))

    def test_flowfield_adjusts_particles_in_2000_steps(self):
        if not REAL_PYGAME:
            self.skipTest("real Pygame is required for FlowField surface coverage")
        old_size = (config.WIDTH, config.HEIGHT)
        old_initialized = config._INITIALIZED
        try:
            config.WIDTH, config.HEIGHT = 320, 240
            config._INITIALIZED = True
            effect = FlowField()
            initial = effect._n
            effect.adjust_particles(2000)
            self.assertEqual(effect._n, initial + 2000)
            effect.adjust_particles(-2000)
            self.assertEqual(effect._n, initial)
        finally:
            config.WIDTH, config.HEIGHT = old_size
            config._INITIALIZED = old_initialized

    def test_display_child_cleanup_waits_and_clears_registry(self):
        manager = DisplayManager.__new__(DisplayManager)
        child = mock.Mock()
        child.poll.side_effect = [None, 0]
        manager.span_children = {1: child}

        manager.kill_children()

        child.terminate.assert_called_once_with()
        child.wait.assert_called()
        self.assertEqual(manager.span_children, {})

    def test_quit_is_reentrant_and_does_not_raise_system_exit(self):
        if VisualizerApp is None:
            self.skipTest("VisualizerApp dependencies unavailable")
        app = VisualizerApp.__new__(VisualizerApp)
        app._quit_requested = False
        app.args = types.SimpleNamespace(span_child=False)
        app.display = types.SimpleNamespace(
            span_children={},
            renderer=None,
            kill_children=mock.Mock(),
        )
        app.audio = types.SimpleNamespace(
            active_dev=None,
            release=mock.Mock(),
        )
        app.vis = types.SimpleNamespace(release=mock.Mock())
        app.bg_vis = types.SimpleNamespace(release=mock.Mock())
        app._save_settings = mock.Mock()

        with mock.patch("psysualizer.pygame.display.set_mode"), \
             mock.patch("psysualizer.pygame.quit"):
            app._quit()
            app._quit()
            app._cleanup()
            app._cleanup()

        self.assertTrue(app._quit_requested)
        app.vis.release.assert_called_once_with()
        app.bg_vis.release.assert_called_once_with()
        app.display.kill_children.assert_called_once_with()
        app.audio.release.assert_called_once_with()

    def test_monitor_parser_accepts_negative_coordinates(self):
        manager = DisplayManager.__new__(DisplayManager)
        output = "Monitors: 2\n 0: +*LEFT 1920/500x1080/300+-1920+0 LEFT\n 1: +RIGHT 1920/500x1080/300+0+0 RIGHT\n"
        with mock.patch("core.display_manager.subprocess.check_output", return_value=output):
            self.assertEqual(
                manager._xrandr_monitors(),
                [(-1920, 0, 1920, 1080), (0, 0, 1920, 1080)],
            )

    def test_monitor_requery_detects_geometry_change(self):
        manager = DisplayManager.__new__(DisplayManager)
        manager.xmonitors = [(0, 0, 1920, 1080)]
        manager.num_displays = 1
        manager._xrandr_monitors = mock.Mock(return_value=[(0, 0, 1600, 900)])

        self.assertTrue(manager.requery_xmonitors())
        self.assertEqual(manager.xmonitors, [(0, 0, 1600, 900)])
        self.assertFalse(manager.requery_xmonitors())

    def test_monitor_requery_preserves_layout_when_xrandr_fails(self):
        manager = DisplayManager.__new__(DisplayManager)
        manager.xmonitors = [(0, 0, 1920, 1080), (1920, 0, 1920, 1080)]
        manager.num_displays = 2
        manager.display_idx = 1
        with mock.patch("core.display_manager.subprocess.check_output", side_effect=OSError("xrandr unavailable")):
            self.assertFalse(manager.requery_xmonitors())
        self.assertEqual(manager.xmonitors, [(0, 0, 1920, 1080), (1920, 0, 1920, 1080)])
        self.assertEqual(manager.num_displays, 2)

    def test_display_child_cleanup_ignores_exit_races(self):
        manager = DisplayManager.__new__(DisplayManager)
        child = mock.Mock()
        child.poll.return_value = None
        child.terminate.side_effect = ProcessLookupError()
        child.kill.side_effect = ProcessLookupError()
        child.wait.return_value = 0
        manager.span_children = {1: child}

        manager.kill_children()

        self.assertEqual(manager.span_children, {})


if __name__ == "__main__":
    unittest.main()
