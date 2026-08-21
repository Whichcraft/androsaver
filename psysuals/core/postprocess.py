from __future__ import annotations

import math

import numpy as np
import pygame
import pygame.surfarray as surfarray


class PostProcessChain:
    """Reusable optional post-processing passes for the composed frame."""

    MODES = ("off", "chromatic", "kaleidoscope", "rotation", "bloom")

    def __init__(self, renderer=None):
        self.renderer = renderer
        self._scratch = None
        self._source = None
        self._result = None
        self._fbo = None

    def _ensure(self, size):
        if self._scratch is not None and self._scratch.get_size() == size:
            return
        self._scratch = pygame.Surface(size)
        self._source = np.empty((size[0], size[1], 3), dtype=np.uint8)
        self._result = np.empty_like(self._source)
        self._fbo = None

    def _cpu_chromatic(self, surface, amount):
        self._source[:] = surfarray.array3d(surface)
        shift = max(1, min(12, int(round(amount * 8))))
        self._result[:] = self._source
        self._result[:, :, 0] = np.roll(self._source[:, :, 0], shift, axis=0)
        self._result[:, :, 2] = np.roll(self._source[:, :, 2], -shift, axis=0)
        surfarray.blit_array(surface, self._result)

    def _cpu_kaleidoscope(self, surface):
        self._scratch.blit(surface)
        width, height = surface.get_size()
        left = self._scratch.subsurface((0, 0, max(1, width // 2), height)).copy()
        mirror = pygame.transform.flip(left, True, False)
        surface.blit(left, (0, 0))
        surface.blit(mirror, (width - mirror.get_width(), 0))

    def _cpu_rotation(self, surface, amount, tick):
        self._scratch.blit(surface)
        angle = math.sin(tick * 0.02) * amount * 8.0
        rotated = pygame.transform.rotate(self._scratch, angle)
        surface.fill((0, 0, 0))
        surface.blit(rotated, rotated.get_rect(center=surface.get_rect().center))

    def _cpu_bloom(self, surface, amount):
        self._scratch.blit(surface)
        width, height = surface.get_size()
        small = pygame.transform.smoothscale(self._scratch, (max(1, width // 3), max(1, height // 3)))
        glow = pygame.transform.smoothscale(small, (width, height))
        glow.set_alpha(max(0, min(150, int(amount * 100))))
        surface.blit(glow, (0, 0), special_flags=pygame.BLEND_RGB_ADD)

    def apply(self, surface, mode=0, intensity=0.0, tick=0):
        mode = max(0, min(len(self.MODES) - 1, int(mode)))
        if mode == 0 or surface is None:
            return
        self._ensure(surface.get_size())
        intensity = max(0.0, min(1.0, float(intensity)))
        if mode == 1:
            self._cpu_chromatic(surface, intensity)
        elif mode == 2:
            self._cpu_kaleidoscope(surface)
        elif mode == 3:
            if self.renderer is not None:
                width, height = surface.get_size()
                if self._fbo is None or self._fbo.width != width or self._fbo.height != height:
                    self._fbo = self.renderer.offscreen(width, height)
                self.renderer.feedback_transform(surface, self._fbo, 1.0 + intensity * 0.04, math.sin(tick * 0.02) * intensity * 0.08)
                pixels = self.renderer.read_pixels(self._fbo)
                surfarray.blit_array(surface, pixels[:, :, :3].transpose(1, 0, 2))
            else:
                self._cpu_rotation(surface, intensity, tick)
        else:
            self._cpu_bloom(surface, intensity)

    def release(self):
        self._scratch = None
        self._source = None
        self._result = None
        self._fbo = None
