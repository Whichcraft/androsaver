"""
Shared color palette — qwen2.5:14b concept, fixed by Claude.

qwen's get() referenced beat/treble which weren't in scope; fixed by
storing them in update() and reading instance vars in get().

Usage:
    from effects.palette import palette
    palette.update(beat, mid, treble, tick)   # once per frame in main loop
    r, g, b = palette.get(offset=0.0)         # any time in any effect
"""

from effects.utils import hsl


# Genre → (target_hue, drift_speed, sat_boost, trail_alpha)
# Index: 0=target_hue, 1=drift_speed, 2=sat_boost, 3=trail_alpha
_GENRE_PRESETS = {
    "electronic": (0.55, 0.004, 0.20, 16),
    "rock":       (0.05, 0.003, 0.15, 22),
    "classical":  (0.75, 0.001, 0.00, 35),
    "any":        (None, 0.002, 0.10, 28),
}


class ColorPalette:
    def __init__(self):
        self.base_hue    = 0.0
        self.trail_alpha = 28        # exported for main loop to read
        self._beat       = 0.0
        self._treble     = 0.0
        self._drift      = 0.002
        self._sat_boost  = 0.10
        self._target_hue = None      # None = free drift

    def set_genre(self, genre: str) -> None:
        target, drift, sat_boost, trail = _GENRE_PRESETS.get(
            genre, _GENRE_PRESETS["any"])
        self._target_hue = target
        self._drift      = drift
        self._sat_boost  = sat_boost
        self.trail_alpha = trail

    def update(self, beat: float, mid: float, treble: float, tick: int) -> None:
        if self._target_hue is not None:
            # Nudge base_hue toward target
            diff = (self._target_hue - self.base_hue + 0.5) % 1.0 - 0.5
            self.base_hue = (self.base_hue + diff * 0.01 + mid * self._drift) % 1.0
        else:
            self.base_hue = (self.base_hue + 0.001 + mid * self._drift) % 1.0
        self._beat   = beat
        self._treble = treble

    def get(self, offset: float = 0.0):
        hue = (self.base_hue + offset) % 1.0
        sat = min(0.5 + self._sat_boost + self._beat * 0.25, 1.0)
        lit = max(0.30, min(0.35 + self._treble * 0.15, 0.65))
        return hsl(hue, sat, lit)


palette = ColorPalette()
