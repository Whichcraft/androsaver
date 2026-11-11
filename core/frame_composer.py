"""Frame surface ownership and composition, independent of UI drawing."""
from dataclasses import dataclass
from core.context import FrameContext

import pygame


@dataclass(frozen=True)
class FrameInputs:
    effect: object
    background: object
    bg_on: bool
    bg_alpha: int
    waveform: object
    fft: object
    beat: float
    tick: int
    context: FrameContext | None = None


def composition_state(name):
    """Compatibility property for historical app surface attributes."""
    def get(app):
        return getattr(app._frames, name)
    def set_value(app, value):
        setattr(app._frames, name, value)
    return property(get, set_value)


class FrameComposer:
    def __init__(self):
        self.fade_alpha = 48
        self.fade = None
        self._fade_surf = self._ui_surface = self._present_surface = None
        self.bg_surf = self.prev_surf = self.prev_surf_scaled = None
        self.crossfade_frame = 0
        self._display_snapshot = None

    def update_target(self, display, use_gl):
        if not use_gl:
            display.target = display.screen
            return
        size = display.screen.get_size()
        if (display.target is display.screen or display.target is None
                or display.target.get_size() != size):
            display.target = pygame.Surface(size, pygame.SRCALPHA)
            self.fade = self.make_fade(display.target, self.fade_alpha)

    def begin_gl(self, display, use_gl):
        if use_gl and display.renderer:
            display.renderer.ctx.screen.use()
            display.renderer.ctx.clear(0., 0., 0., 1.)
            display.renderer.ctx.disable(display.renderer.ctx.BLEND)

    def present_gl(self, display, use_gl, effect, postfx, mode, gain, tick):
        if use_gl and display.renderer:
            present = self._present_surface if self._present_surface is not None else display.target
            display.renderer.blit(present)
            if mode and effect.IS_GL:
                display.renderer.postprocess_screen(postfx, mode, min(1., gain), tick)
            if effect.IS_GL:
                display.target.fill((0, 0, 0, 0))

    def make_fade(self, target, alpha: int):
        size = target.get_size()
        if self._fade_surf is None or self._fade_surf.get_size() != size:
            self._fade_surf = pygame.Surface(size, pygame.SRCALPHA)
        self._fade_surf.fill((0, 0, 0, alpha))
        return self._fade_surf

    def prepare_present_surface(self, target, screen, use_gl):
        """Upscale reduced GL effect targets before drawing display-resolution UI."""
        if use_gl and target.get_size() != screen.get_size():
            if self._ui_surface is None or self._ui_surface.get_size() != screen.get_size():
                self._ui_surface = pygame.Surface(screen.get_size(), pygame.SRCALPHA)
            self._ui_surface.fill((0, 0, 0, 0))
            pygame.transform.smoothscale(target, screen.get_size(), self._ui_surface)
            return self._ui_surface
        return target

    def compose(self, target, frame, *, use_gl, genre_alpha, postfx, postfx_mode, gain, cf_frames):

        if frame.context is not None:
            frame.effect.set_frame_context(frame.context)
        new_alpha = genre_alpha if genre_alpha is not None else getattr(frame.effect, "TRAIL_ALPHA", 48)
        new_alpha = int(round(new_alpha))
        if getattr(frame.effect, "TIME_BASED", False):
            new_alpha = int(round(255 * (1 - (1 - new_alpha / 255) ** frame.effect._frame_step())))
        if new_alpha != self.fade_alpha:
            self.fade_alpha = new_alpha
            self.make_fade(target, self.fade_alpha)

        is_gl_fg = use_gl and frame.effect.IS_GL

        if not is_gl_fg:
            frame.effect._opaque_canvas(target)
            if getattr(frame.effect,"TRAIL_ALPHA",48) == 0:
                target.fill((0,0,0,255))
            else:
                target.blit(self.fade, (0,0))

        if frame.bg_on and not is_gl_fg:
            self.bg_surf.fill((0, 0, 0))
            if frame.context is not None:
                frame.background.render(self.bg_surf, frame.context)
            else:
                frame.background.draw(self.bg_surf, frame.waveform, frame.fft, frame.beat, frame.tick)
            self.bg_surf.set_alpha(frame.bg_alpha)
            if target.get_size() != self.bg_surf.get_size():
                bg_scaled = pygame.transform.smoothscale(self.bg_surf, target.get_size())
                target.blit(bg_scaled, (0, 0))
            else:
                target.blit(self.bg_surf, (0, 0))

            field = getattr(frame.background, "get_motion_field", lambda: None)()
            setter = getattr(frame.effect, "set_motion_field", None)
            if callable(setter):
                setter(field)
        elif callable(getattr(frame.effect, "set_motion_field", None)):
            frame.effect.set_motion_field(None)

        if frame.context is not None:
            frame.effect.render(target, frame.context)
        else:
            frame.effect.draw(target, frame.waveform, frame.fft, frame.beat, frame.tick)

        if self.prev_surf and not is_gl_fg:
            tw, th = target.get_size()
            if self.prev_surf_scaled is None or self.prev_surf_scaled.get_size() != (tw, th):
                self.prev_surf_scaled = pygame.transform.scale(self.prev_surf, (tw, th))
            scaled_prev = self.prev_surf_scaled
            frames = max(1, int(cf_frames))
            t = self.crossfade_frame / frames
            ease = t * t * (3.0 - 2.0 * t)
            scaled_prev.set_alpha(int(255 * (1.0 - ease)))
            target.blit(scaled_prev, (0, 0))
            self.crossfade_frame += 1
            if self.crossfade_frame >= frames:
                scaled_prev.set_alpha(0)
                self.prev_surf = None
                self.prev_surf_scaled = None

        if not is_gl_fg and postfx_mode:
            postfx.apply(target, postfx_mode, min(1.0, gain), frame.tick)

        return target
