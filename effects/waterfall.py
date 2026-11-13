from collections import deque

import numpy as np
import pygame

from .base import Effect
from .utils import padded

def _row_colours(hues, lightness):
    """Vectorized colorsys HLS(s=1), retaining its arithmetic/evaluation order."""
    m2 = np.where(lightness <= .5, lightness * 2., lightness + 1. - lightness)
    m1 = 2. * lightness - m2
    channels = []
    for offset in (1/3, 0., -1/3):
        hue = (hues + offset) % 1.
        value = np.where(hue < 1/6, m1+(m2-m1)*hue*6.,
                        np.where(hue < .5, m2,
                                 np.where(hue < 2/3, m1+(m2-m1)*(2/3-hue)*6., m1)))
        channels.append((value * 255.).astype(np.uint8))
    return np.stack(channels, axis=-1)


class GlowSquares(Effect):
    """Waterfall spectrogram — scrolling time-frequency display.

    Each new frame adds a row at the top; older slices scroll downward.
    Log-spaced frequency bins; hue = frequency position, brightness = energy.
    """

    def _grid_dims(self):
        return max(40, self.frame.height // 12), max(30, self.frame.width // 16)

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self._rng = self.rng
        self.hue   = 0.0
        n_bins     = self.options.audio.block_size // 2
        self._rows, self._cols = self._grid_dims()
        raw        = np.geomspace(2, int(n_bins * 0.85), self._cols + 1).astype(int)
        self.edges = np.unique(np.clip(raw, 1, n_bins - 1))
        self.cols  = len(self.edges) - 1
        self._bin_counts = np.maximum(np.diff(self.edges), 1).astype(float)
        self._column_hues = np.arange(self.cols, dtype=float) / self.cols * .75
        self.buf   = deque(maxlen=self._rows)

    def _draw(self, surf, waveform, fft, beat, tick):
        bass = beat
        mid  = self.frame.mid
        high = self.frame.treble

        self.hue += 0.003 + bass * 0.015 + mid * 0.008
        fft  = padded(fft, int(self.edges[-1]))
        sums = np.add.reduceat(fft[:self.edges[-1]], self.edges[:-1])
        row  = sums / self._bin_counts
        row  = row[:self.cols]
        row /= (row.max() + 1e-6)
        self.buf.appendleft(row)

        W, H   = surf.get_size()
        bw     = max(1, W // self.cols)
        bh     = max(4, H // self._rows)
        flash = bass * 0.35

        count = min(len(self.buf), H // bh + 1)
        ages = np.arange(count, dtype=float) / max(len(self.buf), 1)
        levels = np.asarray(list(self.buf)[:count])
        boosts = flash*np.maximum(0., 1.-ages*6) + mid*.12*np.maximum(0., 1.-ages*2)
        lightness = (1.-ages[:, None]*.7)*(.15+levels*.85) + boosts[:, None]
        np.clip(lightness, .04, .95, out=lightness)
        colours = _row_colours((self.hue+self._column_hues) % 1., lightness)
        for ri in range(count):
            age = ri / max(len(self.buf), 1)
            # Preserve the exact number/order of row jitter draws.
            jitter = self._rng.uniform(-.5, .5) if age < .4 else 0.
            x_offset = int(high*6.*(1.-age)*jitter)
            y_offset = int(high*4.*(1.-age)*jitter)
            for ci in range(self.cols):
                pygame.draw.rect(surf, colours[ri, ci],
                                 (ci*bw+x_offset, ri*bh+y_offset, bw-1, bh-1))
