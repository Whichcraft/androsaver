"""
gl_renderer.py — thin moderngl wrapper for psysuals.

Desktop path:
    Call GLRenderer(w, h) *after* pygame.display.set_mode(..., OPENGL | DOUBLEBUF).
    moderngl binds to pygame's active GL context automatically.

Android / headless path:
    Pass an externally-created moderngl.Context as ctx=.
    Use offscreen() + read_pixels() to get RGBA numpy arrays without a display.
"""

from __future__ import annotations

import numpy as np
import sys
import pygame

try:
    import moderngl
    HAS_MODERNGL = True
except ImportError:
    HAS_MODERNGL = False


class GLRenderer:
    """
    Minimal moderngl context wrapper: compiles shader programs,
    owns a shared fullscreen-quad VBO, and manages offscreen FBOs.
    """

    # Two triangles as a strip covering clip space [-1,1]²
    _QUAD = np.array([-1, -1,  1, -1,  -1, 1,  1, 1], dtype="f4")
    def __init__(self, width: int, height: int, ctx: "moderngl.Context | None" = None):
        if not HAS_MODERNGL:
            raise RuntimeError("moderngl not installed — pip install moderngl")
        self.width  = width
        self.height = height
        self.ctx    = ctx if ctx is not None else moderngl.create_context()
        self._vbo   = self.ctx.buffer(self._QUAD.tobytes())
        self._blit_tex: moderngl.Texture | None = None
        self._blit_prog: moderngl.Program | None = None
        self._blit_vao: moderngl.VertexArray | None = None
        self._feedback_tex: moderngl.Texture | None = None
        self._feedback_prog: moderngl.Program | None = None
        self._feedback_vao: moderngl.VertexArray | None = None
        self._offscreen_cache: dict[tuple[int, int], tuple[moderngl.Texture, moderngl.Framebuffer]] = {}
        self._blit_upload_buf: np.ndarray | None = None
        self._feedback_upload_buf: np.ndarray | None = None

    # ── Shader helpers ────────────────────────────────────────────────────────

    def program(self, vert: str, frag: str) -> tuple:
        """
        Compile *vert* + *frag* GLSL sources into a fullscreen-quad program.
        Returns (Program, VAO) ready to pass to render().
        The vertex shader must declare:  in vec2 in_vert;
        """
        prog = self.ctx.program(vertex_shader=vert, fragment_shader=frag)
        try:
            vao = self.ctx.vertex_array(prog, [(self._vbo, "2f", "in_vert")])
        except Exception:
            prog.release()
            raise
        return prog, vao

    def blit_program(self) -> tuple:
        """Return a simple texture-blitting shader program."""
        vert = """
        #version 330 core
        in vec2 in_vert;
        out vec2 v_uv;
        void main() {
            v_uv = in_vert * 0.5 + 0.5;
            v_uv.y = 1.0 - v_uv.y;
            gl_Position = vec4(in_vert, 0.0, 1.0);
        }
        """
        frag = """
        #version 330 core
        uniform sampler2D u_tex;
        in vec2 v_uv;
        out vec4 fragColor;
        void main() {
            fragColor = texture(u_tex, v_uv);
        }
        """
        return self.program(vert, frag)

    def feedback_program(self) -> tuple:
        """Return a texture feedback shader with zoom/rotation sampling."""
        vert = """
        #version 330 core
        in vec2 in_vert;
        out vec2 v_uv;
        void main() {
            v_uv = in_vert * 0.5 + 0.5;
            // Uploaded surfaces store their top row first; sample like the
            // blit shader so the read-back frame is not upside down.
            v_uv.y = 1.0 - v_uv.y;
            gl_Position = vec4(in_vert, 0.0, 1.0);
        }
        """
        frag = """
        #version 330 core
        uniform sampler2D u_tex;
        uniform float u_zoom;
        uniform float u_rot;
        uniform vec2 u_center;
        in vec2 v_uv;
        out vec4 fragColor;
        void main() {
            vec2 p = v_uv - u_center;
            float c = cos(u_rot);
            float s = sin(u_rot);
            p = mat2(c, -s, s, c) * (p / max(u_zoom, 0.0001));
            vec2 uv = p + u_center;
            if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
                fragColor = vec4(0.0);
            } else {
                fragColor = texture(u_tex, uv);
            }
        }
        """
        return self.program(vert, frag)

    # ── Render helpers ────────────────────────────────────────────────────────

    def render(self, vao, fbo=None) -> None:
        """
        Draw a fullscreen quad.
        *fbo*=None → renders to the pygame/GLFW window (screen framebuffer).
        *fbo*=<Framebuffer> → renders offscreen.
        """
        target = fbo if fbo is not None else self.ctx.screen
        target.use()
        if fbo is not None:
            self.ctx.viewport = (0, 0, fbo.width, fbo.height)
        else:
            # Use the live framebuffer dimensions. Android/EGL surfaces can
            # differ from the logical dimensions supplied at construction.
            screen_w, screen_h = self.ctx.screen.size
            self.ctx.viewport = (0, 0, screen_w, screen_h)
        # self.ctx.clear(0.0, 0.0, 0.0, 1.0) # Removed clear to allow layering
        vao.render(moderngl.TRIANGLE_STRIP)

    @staticmethod
    def _surface_swizzle(surface):
        """Describe tightly packed 32-bit source bytes; None requires staging."""
        width, _ = surface.get_size()
        if surface.get_bytesize() != 4 or surface.get_pitch() != width * 4:
            return None
        masks, shifts, losses = surface.get_masks(), surface.get_shifts(), surface.get_losses()
        has_alpha = bool(surface.get_flags() & pygame.SRCALPHA)
        channels = range(4 if has_alpha else 3)
        if any(not masks[c] or losses[c] or shifts[c] % 8 for c in channels):
            return None
        order = [shifts[c] // 8 if sys.byteorder == 'little' else 3-shifts[c] // 8
                 for c in channels]
        if len(set(order)) != len(order):
            return None
        swizzle = ''.join('RGBA'[index] for index in order)
        return swizzle if has_alpha else swizzle + '1'

    def _upload_surface(
        self,
        surface: "pygame.Surface",
        tex: "moderngl.Texture | None",
        buffer_attr: str,
    ) -> "moderngl.Texture":
        size = surface.get_size()
        swizzle = self._surface_swizzle(surface)
        upload_buf = getattr(self, buffer_attr, None)
        required = size[0] * size[1] * 4
        replace_texture = (tex is None or tex.size != size or
                           (swizzle is None and (upload_buf is None or upload_buf.size != required)))
        old_tex = tex
        if replace_texture:
            tex = self.ctx.texture(size, 4)
        try:
            if swizzle is not None:
                # ModernGL consumes the buffer synchronously. GPU swizzling
                # replaces a full-frame channel shuffle; no staging copy.
                view = surface.get_view('0')
                try:
                    tex.write(view)
                finally:
                    del view  # release the surface lock even if upload fails
                tex.swizzle = swizzle
                candidate_buffer = None
            else:
                if upload_buf is None or upload_buf.size != required:
                    upload_buf = np.empty(required, dtype=np.uint8)
                w, h = size
                buf = upload_buf.reshape(h, w, 4)
                rgb = (pygame.surfarray.pixels3d(surface) if surface.get_bytesize() in (3, 4)
                       else pygame.surfarray.array3d(surface))
                try:
                    buf[:, :, :3] = np.transpose(rgb, (1, 0, 2))
                    if surface.get_flags() & pygame.SRCALPHA:
                        alpha = pygame.surfarray.pixels_alpha(surface)
                        try:
                            buf[:, :, 3] = np.transpose(alpha)
                        finally:
                            del alpha
                    else:
                        buf[:, :, 3] = 255
                finally:
                    del rgb
                tex.write(upload_buf)
                tex.swizzle = 'RGBA'
                candidate_buffer = upload_buf
            tex.use(0)
        except Exception:
            if replace_texture:
                tex.release()
            raise
        setattr(self, buffer_attr, candidate_buffer)
        if replace_texture and old_tex is not None:
            old_tex.release()
        return tex

    def blit(self, surface: "pygame.Surface") -> None:
        """Upload *surface* to a texture and render it as a fullscreen quad."""
        if self._blit_prog is None:
            self._blit_prog, self._blit_vao = self.blit_program()

        self._blit_tex = self._upload_surface(
            surface, self._blit_tex, "_blit_upload_buf")
        self._blit_prog["u_tex"] = 0

        self.ctx.enable(moderngl.BLEND)
        self.ctx.blend_func = (moderngl.SRC_ALPHA, moderngl.ONE_MINUS_SRC_ALPHA)
        self.render(self._blit_vao)

    def postprocess_screen(self, chain, mode, intensity, tick):
        """Read the presented screen, apply a chain, and present it again."""
        width, height = self.ctx.screen.size
        raw = self.ctx.screen.read(components=3, alignment=1)
        pixels = np.frombuffer(raw, dtype=np.uint8).reshape(height, width, 3)[::-1]
        surface = pygame.Surface((width, height))
        pygame.surfarray.blit_array(surface, pixels.transpose(1, 0, 2))
        chain.apply(surface, mode, intensity, tick)
        self.blit(surface)

    def surface_texture(self, surface: "pygame.Surface") -> "moderngl.Texture":
        """Upload a pygame surface to a reusable texture."""
        self._feedback_tex = self._upload_surface(
            surface, self._feedback_tex, "_feedback_upload_buf")
        self._feedback_tex.filter = (moderngl.LINEAR, moderngl.LINEAR)
        self._feedback_tex.repeat_x = False
        self._feedback_tex.repeat_y = False
        return self._feedback_tex

    def feedback_transform(self, surface: "pygame.Surface", fbo, zoom: float, rot: float) -> None:
        """Render *surface* through the feedback shader into *fbo*."""
        if self._feedback_prog is None:
            self._feedback_prog, self._feedback_vao = self.feedback_program()

        tex = self.surface_texture(surface)
        fbo.use()
        fbo.clear(0.0, 0.0, 0.0, 0.0)
        self.ctx.disable(moderngl.BLEND)
        tex.use(0)
        self._feedback_prog["u_tex"] = 0
        self._feedback_prog["u_zoom"] = float(zoom)
        self._feedback_prog["u_rot"] = float(rot)
        self._feedback_prog["u_center"] = (0.5, 0.5)
        self.render(self._feedback_vao, fbo)

    def release(self):
        """Release all GL resources."""
        if self._blit_tex:
            self._blit_tex.release()
            self._blit_tex = None
        if self._feedback_tex:
            self._feedback_tex.release()
            self._feedback_tex = None
        self._blit_upload_buf = None
        self._feedback_upload_buf = None
        if self._vbo is not None:
            self._vbo.release()
            self._vbo = None
        if self._blit_vao:
            self._blit_vao.release()
            self._blit_vao = None
        if self._blit_prog:
            self._blit_prog.release()
            self._blit_prog = None
        if self._feedback_vao:
            self._feedback_vao.release()
            self._feedback_vao = None
        if self._feedback_prog:
            self._feedback_prog.release()
            self._feedback_prog = None
        for tex, fbo in self._offscreen_cache.values():
            fbo.release()
            tex.release()
        self._offscreen_cache.clear()

    # ── Offscreen / Android ───────────────────────────────────────────────────

    def offscreen(self, width: int = 0, height: int = 0) -> "moderngl.Framebuffer":
        """Return a renderer-owned cached RGBA framebuffer for the requested size.

        Callers must not release the returned framebuffer directly. The cache
        may evict it, and :meth:`release` releases all remaining entries.
        Call ``is_offscreen_current`` before reusing a retained reference.
        """
        w = width  or self.width
        h = height or self.height
        key = (w, h)
        if key not in self._offscreen_cache:
            tex = self.ctx.texture((w, h), 4)
            try:
                fbo = self.ctx.framebuffer(color_attachments=[tex])
            except Exception:
                tex.release()
                raise
            if len(self._offscreen_cache) >= 8:
                old_key = next(iter(self._offscreen_cache))
                old_tex, old_fbo = self._offscreen_cache.pop(old_key)
                old_fbo.release()
                old_tex.release()
            self._offscreen_cache[key] = (tex, fbo)
        return self._offscreen_cache[key][1]

    def is_offscreen_current(self, fbo, width: int = 0, height: int = 0) -> bool:
        """Return whether *fbo* is the live cached framebuffer for its size."""
        w = width or self.width
        h = height or self.height
        entry = self._offscreen_cache.get((w, h))
        return entry is not None and entry[1] is fbo

    def read_pixels(self, fbo: "moderngl.Framebuffer") -> np.ndarray:
        """
        Read *fbo* → (H, W, 4) RGBA uint8 numpy array.
        Flips Y because OpenGL's origin is bottom-left.
        """
        raw = fbo.read(components=4)
        arr = np.frombuffer(raw, dtype=np.uint8).reshape(fbo.height, fbo.width, 4)
        return arr[::-1].copy()
