from collections import deque

import numpy as np
import pygame

import config
from .utils import hsl


from .base import Effect

class GlowSquares(Effect):
    """Waterfall spectrogram — scrolling time-frequency display.

    Each new frame adds a row at the top; older slices scroll downward.
    Log-spaced frequency bins; hue = frequency position, brightness = energy.
    """

    @classmethod
    def _grid_dims(cls):
        return max(40, config.HEIGHT // 12), max(30, config.WIDTH // 16)

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self._rng = np.random.default_rng(config.RNG_SEED)
        self.hue   = 0.0
        n_bins     = config.BLOCK_SIZE // 2
        self._rows, self._cols = self._grid_dims()
        raw        = np.geomspace(2, int(n_bins * 0.85), self._cols + 1).astype(int)
        self.edges = np.unique(np.clip(raw, 1, n_bins - 1))
        self.cols  = len(self.edges) - 1
        self.buf   = deque(maxlen=self._rows)

    def draw(self, surf, waveform, fft, beat, tick):
        bass = beat
        mid  = config.MID_ENERGY
        high = config.TREBLE_ENERGY

        self.hue += 0.003 + bass * 0.015 + mid * 0.008
        sums = np.add.reduceat(fft[:self.edges[-1]], self.edges[:-1])
        row  = sums / np.maximum(np.diff(self.edges), 1).astype(float)
        row  = row[:self.cols]
        row /= (row.max() + 1e-6)
        self.buf.appendleft(row)

        W, H   = surf.get_size()
        bw     = max(1, W // self.cols)
        bh     = max(4, H // self._rows)
        flash = bass * 0.35

        for ri, r in enumerate(self.buf):
            y = ri * bh
            if y > H:
                break
            age     = ri / max(len(self.buf), 1)
            row_boost = flash * max(0.0, 1.0 - age * 6) + mid * 0.12 * max(0.0, 1.0 - age * 2)
            
            # High frequency treble transients add a heat-shimmer jitter/offset on newer rows
            jitter = self._rng.uniform(-0.5, 0.5) if age < 0.4 else 0.0
            x_offset = int(high * 6.0 * (1.0 - age) * jitter)
            y_offset = int(high * 4.0 * (1.0 - age) * jitter)

            for ci, e in enumerate(r):
                x   = ci * bw
                hue = (self.hue + ci / self.cols * 0.75) % 1.0
                lit = (1 - age * 0.7) * (0.15 + e * 0.85) + row_boost
                lit = max(0.04, min(lit, 0.95))
                pygame.draw.rect(surf, hsl(hue, l=lit),
                                 (x + x_offset, y + y_offset, bw - 1, bh - 1))
