from typing import ClassVar
import math

import numpy as np
import pygame
import config

class Effect:
    """Base class for all visualization effects."""
    
    # Optional — controls trail fade speed (lower = longer trails, default 48)
    TRAIL_ALPHA = 48
    
    # Resolution divisor for performance-heavy effects
    RES_DIV = 1
    
    # Set to True if the effect renders directly to the GL backbuffer
    IS_GL = False
    MORPH_SCHEMA = {}

    # Cache for display info, invalidated when config dimensions change
    _cached_info: ClassVar[tuple[int, int, pygame.Surface | None]] = (0, 0, None)

    def __init__(self, renderer=None, **kwargs):
        """One-time setup. Stores optional GLRenderer."""
        config.assert_initialized()
        self.renderer = renderer

    def _auto_res_div(self) -> int:
        """Pick a display-aware internal scale bucket.

        Higher resolutions get finer internal rendering so the effect does
        not look blocky on HiDPI or TV-sized screens.
        """
        w = max(1, config.WIDTH)
        h = max(1, config.HEIGHT)
        cached_w, cached_h, cached_info = self._cached_info
        if w != cached_w or h != cached_h or cached_info is None:
            try:
                cached_info = pygame.display.Info()
            except Exception:
                cached_info = None
            self._cached_info = (w, h, cached_info)
        if cached_info is not None:
            w = max(w, int(getattr(cached_info, "current_w", 0) or 0))
            h = max(h, int(getattr(cached_info, "current_h", 0) or 0))

        area = w * h
        if w >= 3200 or h >= 1800 or area >= 5_500_000:
            return 1
        if w >= 2200 or h >= 1300 or area >= 3_000_000:
            return 2
        return 3

    def _render_div(self) -> int:
        """Return the effect's effective internal render divisor."""
        base = max(1, min(int(getattr(self, "RES_DIV", 1)), self._auto_res_div()))
        quality_scale = max(0.5, min(1.0, float(getattr(config, "QUALITY_SCALE", 1.0))))
        return max(base, min(8, int(math.ceil(base / quality_scale))))

    def _render_size(self) -> tuple[int, int, int]:
        """Return (internal_width, internal_height, divisor)."""
        div = self._render_div()
        return max(1, config.WIDTH // div), max(1, config.HEIGHT // div), div

    def _display_motion_scale(self) -> float:
        """Scale high-energy motion for smaller laptop-sized displays.

        Large TVs retain the original intensity. Smaller displays get a
        gentler response so rapid perspective effects do not become visually
        overwhelming at close viewing distance.
        """
        shortest = min(max(1, config.WIDTH), max(1, config.HEIGHT))
        return max(0.55, min(1.0, shortest / 1080.0))
    
    def draw(self, surf: pygame.Surface | None, waveform: np.ndarray,
             fft: np.ndarray, beat: float, tick: int) -> None:
        """Called once per frame. Draw directly onto surf."""
        raise NotImplementedError("Each effect must implement the draw method.")

    def draw_frame(self, width: int, height: int,
                   waveform: np.ndarray, fft: np.ndarray,
                   beat: float, tick: int,
                   renderer=None) -> np.ndarray:
        """Render a CPU effect to an RGBA framebuffer-sized array.

        This is the Android/headless adapter for normal Pygame effects.
        The requested output dimensions become the effect's runtime display
        dimensions for this frame, so reduced-resolution effects do not use
        stale desktop or logical-display dimensions.

        Direct-GL effects may override this method with a GPU implementation.
        """
        width = int(width)
        height = int(height)
        if width <= 0 or height <= 0:
            raise ValueError("draw_frame() requires positive dimensions")

        old_width, old_height = config.WIDTH, config.HEIGHT
        old_initialized = config._INITIALIZED
        config.WIDTH, config.HEIGHT = width, height
        config._INITIALIZED = True
        try:
            surface = pygame.Surface((width, height), pygame.SRCALPHA)
            surface.fill((0, 0, 0, 0))
            self.draw(surface, waveform, fft, beat, tick)

            rgb = pygame.surfarray.array3d(surface).transpose(1, 0, 2)
            alpha = pygame.surfarray.array_alpha(surface).transpose(1, 0)
            return np.dstack((rgb, alpha)).copy()
        finally:
            config.WIDTH, config.HEIGHT = old_width, old_height
            config._INITIALIZED = old_initialized

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
