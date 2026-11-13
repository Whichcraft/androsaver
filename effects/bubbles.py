import math
from collections import OrderedDict

import numpy as np
import pygame

from .utils import hsl, padded


from .base import Effect

class Bubbles(Effect):
    """Translucent rising bubbles — fills the full screen; size and spawn rate driven by bass."""

    MAX = 700
    MAX_CACHED_SURFACES = 32

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.hue        = 0.0
        self.pulse      = 0.0
        self.pvel       = 0.0
        self._bass_flash = 0.0   # spikes on strong bass, inflates rendered bubble size
        self.pool = [self._make(y=self._random.uniform(0, self.frame.height)) for _ in range(200)]
        self._surf_cache = OrderedDict()

    def _make(self, y=None):
        r = self._random.uniform(8, 45)
        return {
            "x":      self._random.uniform(self.frame.width * 0.02, self.frame.width * 0.98),
            "y":      float(self.frame.height + r) if y is None else y,
            "r":      r,
            "vx":     self._random.gauss(0, 0.4),
            "vy":    -self._random.uniform(1.0, 3.2),
            "hue":    self._random.uniform(0, 1.0),
            "wobble": self._random.uniform(0.025, 0.07),
            "phase":  self._random.uniform(0, math.tau),
        }

    def _get_bsurf(self, size):
        s = self._surf_cache.get(size)
        if s is None:
            s = pygame.Surface((size, size), pygame.SRCALPHA)
            if len(self._surf_cache) >= self.MAX_CACHED_SURFACES:
                self._surf_cache.popitem(last=False)
            self._surf_cache[size] = s
        else:
            self._surf_cache.move_to_end(size)
        s.fill((0, 0, 0, 0))
        return s

    def _spawn(self, beat, bass):
        beat_sel = min(beat, 1.0)
        hue_spread = 0.35 + beat_sel * 0.30
        for _ in range(int(2 + beat_sel * 12 + bass * 6)):
            b = self._make()
            b["vy"]  *= (1 + beat_sel * 0.8)
            b["r"]   *= (1 + bass * 1.2)
            b["hue"]  = (self.hue + self._random.uniform(0, hue_spread)) % 1.0
            self.pool.append(b)

        # Mega-bubbles on strong bass hits: 1-3 extra large luminous bubbles
        if beat > 0.7 and len(self.pool) < self.MAX:
            for _ in range(1 + int((beat - 0.7) * 4)):
                if len(self.pool) < self.MAX:
                    b = self._make()
                    b["r"]  *= (2.2 + bass * 2.0)
                    b["vy"] *= 1.4
                    b["hue"] = (self.hue + self._random.uniform(0, 0.5)) % 1.0
                    self.pool.append(b)

    def _draw(self, surf, waveform, fft, beat, tick):
        fft = padded(fft, 30)
        self.hue += 0.005
        bass = float(np.mean(fft[:6]))
        mid  = float(np.mean(fft[6:30]))

        self.pvel += beat * 0.75
        self.pvel += -self.pulse * 0.35
        self.pvel *= 0.52
        self.pulse += self.pvel

        # Bass flash: spike on strong hits, inflate all rendered bubbles for ~10 frames
        if bass > 0.65:
            self._bass_flash = max(self._bass_flash, bass * 2.8)
        self._bass_flash = max(0.0, self._bass_flash - 0.18)

        self._spawn(beat, bass)

        max_r = max(2, min(self.frame.width, self.frame.height) // 2)
        alive = []
        for b in self.pool:
            b["x"]   += b["vx"] + math.sin(tick * b["wobble"] + b["phase"]) * 0.9
            b["y"]   += b["vy"]
            b["hue"]  = (b["hue"] + 0.004) % 1.0
            if b["y"] + b["r"] < 0:
                continue

            life  = max(0.0, min(1.0, b["y"] / self.frame.height))
            # Cap the swell: loud beats at high gain drove the pulse so far that
            # every bubble needed a huge glow surface and frames stalled.
            swell = min(3.0, 1 + self.pulse * 0.90 + mid * 0.15 + self._bass_flash * 0.45)
            r     = max(2, min(int(b["r"] * swell), max_r))
            pad   = r + 14
            alpha = int(life * 160)
            bsurf = self._get_bsurf(pad * 2)
            cc = pad

            for g, (g_exp, g_l, g_a_mul) in enumerate(
                    [(1.55, 0.40, 0.18), (1.28, 0.52, 0.35), (1.10, 0.65, 0.60)]):
                gr    = max(1, int(r * g_exp))
                galpha = int(alpha * g_a_mul)
                pygame.draw.circle(bsurf, (*hsl(b["hue"], l=g_l), galpha),
                                   (cc, cc), gr, max(2, gr // 5))

            pygame.draw.circle(bsurf, (*hsl(b["hue"], l=0.78), alpha),
                               (cc, cc), r, 2)
            pygame.draw.circle(bsurf,
                               (*hsl((b["hue"] + 0.5) % 1.0, l=0.55),
                                int(alpha * 0.22)),
                               (cc, cc), max(1, r - 2))
            hr = max(1, r // 3)
            pygame.draw.circle(bsurf, (255, 255, 255, int(alpha * 0.55)),
                               (cc - r // 3, cc - r // 3), hr)
            if beat > 1.0:
                excess = beat - 1.0
                er1 = max(1, int(r * (1.7 + excess * 0.4)))
                ec1 = hsl((b["hue"] + 0.33) % 1.0, l=0.90)
                glow_alpha = min(255, int(alpha * excess * 0.55))
                pygame.draw.circle(bsurf, (*ec1, glow_alpha), (cc, cc), er1, 2)
                if excess > 0.5:
                    er2 = max(1, int(r * (2.0 + excess * 0.3)))
                    ec2 = hsl((b["hue"] + 0.66) % 1.0, l=0.85)
                    glow_alpha = min(255, int(alpha * (excess - 0.5) * 0.45))
                    pygame.draw.circle(bsurf, (*ec2, glow_alpha), (cc, cc), er2, 2)

            surf.blit(bsurf, (int(b["x"]) - cc, int(b["y"]) - cc))
            alive.append(b)

        self.pool = alive[-self.MAX:]

    def release(self):
        self._surf_cache.clear()
        self.pool.clear()
        super().release()
