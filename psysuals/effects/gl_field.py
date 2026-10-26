"""Small helper for optional fullscreen field shaders."""
from __future__ import annotations

import numpy as np

try:
    import moderngl
except ImportError:  # pragma: no cover - depends on optional GL extra
    moderngl = None


VERTEX_SHADER = """
#version 330 core
in vec2 in_vert;
void main() { gl_Position = vec4(in_vert, 0.0, 1.0); }
"""


class FullscreenField:
    def __init__(self, fragment_shader):
        self.fragment_shader = fragment_shader
        self._renderer = None
        self._program = self._vao = None
        self._fbos = {}

    @staticmethod
    def available(renderer):
        return renderer is not None and moderngl is not None

    def render(self, renderer, uniforms, fbo=None):
        if self._program is None or self._renderer is not renderer:
            self.release()
            self._renderer = renderer
            self._program, self._vao = renderer.program(VERTEX_SHADER, self.fragment_shader)
        # gl_FragCoord is measured in framebuffer pixels, not SDL logical pixels.
        uniforms = dict(uniforms)
        if "u_resolution" in uniforms:
            if fbo is not None:
                size = (fbo.width, fbo.height)
            else:
                size = renderer.ctx.screen.size
            uniforms["u_resolution"] = tuple(map(float, size))
        for name, value in uniforms.items():
            uniform = self._program[name]
            if isinstance(value, np.ndarray):
                uniform.write(value.astype("f4", copy=False).tobytes())
            else:
                uniform.value = value
        renderer.render(self._vao, fbo)

    def draw_frame(self, renderer, width, height, uniforms):
        key = (width, height)
        fbo = self._fbos.get(key)
        current = getattr(renderer, "is_offscreen_current", lambda *_: True)
        if fbo is None or not current(fbo, width, height):
            self._fbos[key] = renderer.offscreen(width, height)
            fbo = self._fbos[key]
        self.render(renderer, uniforms, fbo)
        return renderer.read_pixels(fbo)

    def release(self):
        self._fbos.clear()
        if self._vao is not None:
            self._vao.release()
            self._vao = None
        if self._program is not None:
            self._program.release()
            self._program = None
        self._renderer = None
