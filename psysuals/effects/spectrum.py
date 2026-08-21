import numpy as np
import pygame

import config
from .utils import hsl


from .base import Effect

class Bars(Effect):
    """Classic spectrum bars with peak markers and a waveform overlay."""

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.hue = 0.0
        n_bins     = config.BLOCK_SIZE // 2
        raw        = np.geomspace(2, int(n_bins * 0.85), 81).astype(int)
        self.edges = np.unique(np.clip(raw, 1, n_bins - 1))
        self.n     = len(self.edges) - 1
        self.peaks   = np.zeros(self.n)
        self.display = np.zeros(self.n)

    def draw(self, surf, waveform, fft, beat, tick):
        W, H = surf.get_size()
        bass = beat
        mid  = config.MID_ENERGY
        high = config.TREBLE_ENERGY

        self.hue += 0.003 + mid * 0.005
        bar_w   = W // self.n
        sums    = np.add.reduceat(fft[:self.edges[-1]], self.edges[:-1])
        heights = sums / np.maximum(np.diff(self.edges), 1).astype(float)
        heights = heights[:self.n]
        heights /= (heights.max() + 1e-6)
        lerp = max(0.05, min(0.25 + bass * 0.55, 1.0))
        self.display = self.display * (1.0 - lerp) + heights * lerp
        self.peaks = np.maximum(self.peaks * 0.94, self.display)

        for i, h in enumerate(self.display):
            bar_h = int(h * H * 0.82)
            peak  = int(self.peaks[i] * H * 0.82)
            x     = i * bar_w
            hue   = (self.hue + i / self.n) % 1.0
            bw = max(1, bar_w - 2)
            pygame.draw.rect(surf, hsl(hue, l=0.38 + h * 0.42),
                             (x, H - bar_h, bw, bar_h))
            if bar_h > 0:
                pygame.draw.rect(surf, hsl(hue, l=0.9),
                                 (x, H - peak - 3, bw, 3))

        # Waveform amplitude and thickness react to treble transients
        step = max(1, len(waveform) // W)
        amp  = 120 + high * 120
        pts  = [(int(i * W / (len(waveform) // step)),
                 int(H // 2 + waveform[i * step] * amp))
                for i in range(len(waveform) // step)]
        if len(pts) > 1:
            lw = max(1, int(2 + high * 3))
            pygame.draw.lines(surf, hsl(self.hue, l=0.75 + high * 0.15), False, pts, lw)
