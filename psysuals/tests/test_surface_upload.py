"""Direct surface upload must preserve staged RGBA output and ownership."""
import unittest
from unittest import mock

import numpy as np
import pygame

from gl_renderer import GLRenderer


class SurfaceUploadTests(unittest.TestCase):
    def test_direct_upload_reuses_texture_without_staging_or_surface_locks(self):
        renderer = GLRenderer.__new__(GLRenderer)
        renderer.ctx = mock.Mock()
        texture = renderer.ctx.texture.return_value
        texture.size = (17, 13)
        renderer._blit_upload_buf = None
        surface = pygame.Surface((17, 13), pygame.SRCALPHA)
        first = renderer._upload_surface(surface, None, '_blit_upload_buf')
        second = renderer._upload_surface(surface, first, '_blit_upload_buf')
        self.assertIs(first, second)
        self.assertEqual(renderer.ctx.texture.call_count, 1)
        self.assertIsNone(renderer._blit_upload_buf)
        # Mocks retain call arguments; discard them to release their buffer views.
        texture.write.reset_mock()
        self.assertFalse(surface.get_locked())

    def test_direct_upload_failure_releases_replacement_and_unlocks_surface(self):
        renderer = GLRenderer.__new__(GLRenderer)
        renderer.ctx = mock.Mock()
        renderer._blit_upload_buf = previous_buffer = np.empty(16, dtype=np.uint8)
        previous_texture = mock.Mock(size=(2, 2))
        replacement = renderer.ctx.texture.return_value
        replacement.write.side_effect = RuntimeError('upload failed')
        surface = pygame.Surface((17, 13), pygame.SRCALPHA)
        with self.assertRaisesRegex(RuntimeError, 'upload failed'):
            renderer._upload_surface(surface, previous_texture, '_blit_upload_buf')
        replacement.release.assert_called_once_with()
        previous_texture.release.assert_not_called()
        self.assertIs(renderer._blit_upload_buf, previous_buffer)
        replacement.write.reset_mock()
        self.assertFalse(surface.get_locked())

    def test_padded_and_non32bit_surfaces_use_staged_conversion(self):
        parent = pygame.Surface((21, 17), pygame.SRCALPHA)
        for surface in (parent.subsurface((2, 2, 17, 13)), pygame.Surface((17, 13), depth=24),
                        pygame.Surface((17, 13), depth=16)):
            self.assertIsNone(GLRenderer._surface_swizzle(surface))


class EGLSurfaceUploadTests(unittest.TestCase):
    def test_direct_and_staged_rgba_match_across_formats_and_resize(self):
        try:
            import moderngl
        except ImportError as exc:
            self.skipTest(f'ModernGL unavailable: {exc}')
        try:
            context = moderngl.create_standalone_context(backend='egl', require=330)
        except Exception as exc:
            self.skipTest(f'EGL OpenGL 3.3 unavailable: {exc}')
        self.addCleanup(context.release)
        class Context:
            screen = None
            def __getattr__(self, name):
                return getattr(context, name)
        proxy = Context()
        direct, staged = [GLRenderer(17, 13, ctx=proxy) for _ in range(2)]
        self.addCleanup(direct.release)
        self.addCleanup(staged.release)
        staged._surface_swizzle = lambda surface: None
        parent = pygame.Surface((21, 17), pygame.SRCALPHA)
        disabled_alpha = pygame.Surface((17, 13), pygame.SRCALPHA)
        disabled_alpha.set_alpha(None)
        formats = [pygame.Surface((17, 13)), pygame.Surface((17, 13), pygame.SRCALPHA),
                   pygame.Surface((31, 23), pygame.SRCALPHA), disabled_alpha,
                   pygame.Surface((17, 13), pygame.SRCALPHA, 32,
                                  (0xff, 0xff00, 0xff0000, 0xff000000)),
                   parent.subsurface((2, 2, 17, 13)),
                   pygame.Surface((17, 13), depth=24), pygame.Surface((17, 13), depth=16),
                   pygame.Surface((17, 13), pygame.SRCALPHA)]
        rng = np.random.default_rng(7)
        for index, surface in enumerate(formats):
            with self.subTest(index=index, masks=surface.get_masks(), pitch=surface.get_pitch()):
                width, height = surface.get_size()
                pygame.surfarray.blit_array(surface, rng.integers(0, 256, (width, height, 3), dtype=np.uint8))
                if surface.get_flags() & pygame.SRCALPHA:
                    alpha = pygame.surfarray.pixels_alpha(surface)
                    alpha[:] = rng.integers(0, 256, alpha.shape, dtype=np.uint8)
                    del alpha
                fbo = context.simple_framebuffer((width, height), components=4)
                proxy.screen = fbo
                try:
                    outputs = []
                    for renderer in (staged, direct):
                        fbo.use(); context.clear(.12, .23, .34, 1.)
                        renderer.blit(surface)
                        outputs.append(renderer.read_pixels(fbo))
                    np.testing.assert_array_equal(*outputs)
                    self.assertFalse(surface.get_locked())
                finally:
                    fbo.release()
