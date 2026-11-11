import math

import numpy as np
import pygame
from dataclasses import replace
import random
from core.context import EffectOptions, FrameContext

class Effect:
    """Base class for all visualization effects."""
    
    # Optional — controls trail fade speed (lower = longer trails, default 48)
    TRAIL_ALPHA = 48
    
    # Resolution divisor for performance-heavy effects
    RES_DIV = 1
    
    # Set to True if the effect renders directly to the GL backbuffer
    IS_GL = False
    MORPH_SCHEMA = {}
    TIME_BASED = False
    CONTINUOUS_QUALITY = False

    def __init__(self, renderer=None, *, options: EffectOptions, rng=None, frame: FrameContext | None = None, **kwargs):
        """Construct with explicit physical size and per-instance randomness."""
        self.options = options
        self.rng = rng if rng is not None else np.random.default_rng(options.seed)
        self._random = random.Random(options.seed)
        self._frame = frame if frame is not None else FrameContext(
            options.width, options.height,
            waveform=np.zeros(options.audio.block_size, dtype=np.float32),
            fft=np.zeros(options.audio.block_size // 2, dtype=np.float32))
        self.renderer = renderer

    @property
    def frame(self):
        return self._frame

    def set_frame_context(self, frame: FrameContext):
        """Bind supplemental frame inputs for the legacy draw adapters."""
        self._frame = frame

    def render(self, surface, frame: FrameContext):
        """Retain frame on success; restore only the binding on hook failure.

        Pixels, simulation state and randomness are not rolled back. Instances
        require serialized host access; this method is not a transaction.
        """
        previous = self._frame
        self._frame = frame
        try:
            self._draw(surface, frame.waveform, frame.fft, frame.beat, frame.tick)
        except BaseException:
            self._frame = previous
            raise

    def draw(self, surf, waveform, fft, beat, tick):
        """Legacy arguments with render() success/error binding semantics."""
        width, height = surf.get_size() if surf is not None else (self.frame.width, self.frame.height)
        self.render(surf, replace(self.frame, width=width, height=height,
                                 waveform=waveform, fft=fft, beat=beat, tick=tick))

    def detach_renderer(self):
        """Release context-owned resources without discarding CPU simulation state."""
        field = getattr(self, "_gl_field", None)
        if field is not None:
            field.release()
        for name in ("_prog", "_vao"):
            resource = getattr(self, name, None)
            if resource is not None:
                resource.release()
                setattr(self, name, None)
        if hasattr(self, "_feedback_fbo"):
            self._feedback_fbo = None
        self.renderer = None

    def release(self):
        """Release inherited context resources; safe to call repeatedly.

        Overrides own any additional buffers and simulation state. They should
        call this method when they also use the inherited context resources.
        Display changes use detach_renderer() to retain CPU simulation state.
        """
        self.detach_renderer()

    @staticmethod
    def _opaque_canvas(surface):
        """A completed CPU frame is opaque, including RGB-only blend operations."""
        if surface.get_flags() & pygame.SRCALPHA:
            alpha = pygame.surfarray.pixels_alpha(surface)
            alpha[:] = 255
            del alpha

    @staticmethod
    def _fit_viewport(points, width, height, margin=0.04):
        points = np.asarray(points, dtype=float)
        center = np.array([width / 2., height / 2.])
        offsets = points - center
        extent = np.max(np.abs(offsets), axis=tuple(range(offsets.ndim - 1)))
        available = np.maximum(1., np.array([width, height]) * (.5 - margin))
        fit = min(1., float(np.min(available / np.maximum(extent, 1.))))
        return center + offsets * fit

    @staticmethod
    def _resize_field(field, width, height):
        """Bilinearly resample floating point simulation data, preserving its range."""
        old_h, old_w = field.shape[:2]
        x, y = np.linspace(0, old_w - 1, width), np.linspace(0, old_h - 1, height)
        x0, y0 = x.astype(int), y.astype(int)
        x1, y1 = np.minimum(x0 + 1, old_w - 1), np.minimum(y0 + 1, old_h - 1)
        wx, wy = (x-x0).astype(np.float32), (y-y0).astype(np.float32)
        extra = (None,) * (field.ndim - 2)
        wx = wx[(None, slice(None)) + extra]
        wy = wy[(slice(None), None) + extra]
        top = field[y0[:, None], x0] * (1-wx) + field[y0[:, None], x1] * wx
        bottom = field[y1[:, None], x0] * (1-wx) + field[y1[:, None], x1] * wx
        return (top * (1-wy) + bottom * wy).astype(field.dtype)

    def _frame_step(self):
        """Elapsed time in 60 Hz units; cap stalls to avoid simulation jumps."""
        dt = float(self.frame.dt)
        return max(0.0, min(0.1, dt)) * 60.0 if math.isfinite(dt) else 1.0

    def _smooth(self, current, target, rate):
        return current + (target - current) * (1.0 - (1.0 - rate) ** self._frame_step())

    def _audio_response(self, beat):
        """Separate quick light accents from slowly changing geometry/motion."""
        target = np.nan_to_num(np.clip([beat, self.frame.mid, self.frame.treble], 0, 1.5))
        self._motion_audio = self._smooth(getattr(self, "_motion_audio", np.zeros(3)), target, 0.035)
        self._light_audio = self._smooth(getattr(self, "_light_audio", np.zeros(3)), target, 0.25)
        return self._motion_audio, self._light_audio

    def _visual_profile(self):
        profile = self.frame.profile
        if profile == "auto":
            profile = "tv" if self.frame.width >= 2560 else "laptop"
        return profile

    def _profile_response(self):
        # Motion stays gentle on both displays; TV gets stronger light accents.
        return (0.65, 1.0) if self._visual_profile() == "laptop" else (0.85, 1.3)

    def _auto_res_div(self) -> int:
        """Pick a display-aware internal scale bucket.

        Higher resolutions get finer internal rendering so the effect does
        not look blocky on HiDPI or TV-sized screens.
        """
        w = max(1, self.frame.width)
        h = max(1, self.frame.height)

        area = w * h
        if w >= 3200 or h >= 1800 or area >= 5_500_000:
            return 1
        if w >= 2200 or h >= 1300 or area >= 3_000_000:
            return 2
        return 3

    def _render_div(self) -> int:
        """Return the effect's effective internal render divisor."""
        base = max(1, min(int(getattr(self, "RES_DIV", 1)), self._auto_res_div()))
        quality_scale = max(0.5, min(1.0, float(self.frame.quality_scale)))
        return max(base, min(8, int(math.ceil(base / quality_scale))))

    def _render_size(self) -> tuple[int, int, float]:
        """Return (internal_width, internal_height, divisor)."""
        div = self._render_div()
        if self.CONTINUOUS_QUALITY:
            base = max(1, min(self.RES_DIV, self._auto_res_div()))
            scale = max(0.5, min(1.0, float(self.frame.quality_scale)))
            width, height = max(1, round(self.frame.width * scale / base)), max(1, round(self.frame.height * scale / base))
            return width, height, self.frame.width / width
        return max(1, self.frame.width // div), max(1, self.frame.height // div), div

    def _display_motion_scale(self) -> float:
        """Return gentle camera motion appropriate for the chosen display profile."""
        return self._profile_response()[0]
    
    def _draw(self, surf: pygame.Surface | None, waveform: np.ndarray,
             fft: np.ndarray, beat: float, tick: int) -> None:
        """Called once per frame. Draw directly onto surf."""
        raise NotImplementedError("Each effect must implement the _draw hook.")

    def draw_frame(self, width: int, height: int,
                   waveform: np.ndarray, fft: np.ndarray,
                   beat: float, tick: int,
                   renderer=None) -> np.ndarray:
        """Render a CPU effect to an RGBA framebuffer-sized array.

        This is the Android/headless adapter for normal Pygame effects.
        The requested output dimensions become the effect's runtime display
        dimensions for this frame, so reduced-resolution effects do not use
        stale desktop or logical-display dimensions.

        Direct-GL effects implement _draw_frame() for GPU output. A supplied
        renderer becomes this instance's active renderer until detached.
        Frame binding is restored on either outcome. A hook failure does not
        undo renderer replacement, resource release or simulation advancement.
        """
        width = int(width)
        height = int(height)
        if width <= 0 or height <= 0:
            raise ValueError("draw_frame() requires positive dimensions")

        previous = self._frame
        self._frame = replace(previous, width=width, height=height,
                              waveform=waveform, fft=fft, beat=beat, tick=tick)
        try:
            if renderer is not None and renderer is not self.renderer:
                self.detach_renderer()
                self.renderer = renderer
            return self._draw_frame(width, height, waveform, fft, beat, tick, renderer)
        finally:
            self._frame = previous

    def _draw_frame(self, width, height, waveform, fft, beat, tick, renderer=None):
        surface = pygame.Surface((width, height), pygame.SRCALPHA)
        surface.fill((0, 0, 0, 0))
        self.render(surface, self.frame)
        self._opaque_canvas(surface)
        rgb = pygame.surfarray.array3d(surface).transpose(1, 0, 2)
        alpha = pygame.surfarray.array_alpha(surface).transpose(1, 0)
        return np.dstack((rgb, alpha)).copy()

    def get_motion_field(self):
        """Return an optional read-only ``(vx, vy)`` field for a consumer."""
        return None

    def set_motion_field(self, field) -> None:
        """Accept an optional producer field; effects may safely ignore it."""
        self._motion_field = field

    def get_morph_values(self):
        return {
            name: float(getattr(self, name))
            for name in self.MORPH_SCHEMA
            if hasattr(self, name)
        }

    def set_morph_values(self, values) -> None:
        for name, bounds in self.MORPH_SCHEMA.items():
            if name not in values:
                continue
            try:
                value = float(values[name])
            except (TypeError, ValueError):
                continue
            if np.isfinite(value):
                setattr(self, name, max(bounds[0], min(bounds[1], value)))
