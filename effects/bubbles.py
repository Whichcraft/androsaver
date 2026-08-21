import math

import numpy as np
import pygame

import config
from .utils import hsl


from .base import Effect

class Bubbles(Effect):
    """Translucent rising bubbles — fills the full screen; size and spawn rate driven by bass."""

    MAX = 700
    MAX_RENDER_DIAMETER = 512

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self._rng = np.random.default_rng(config.RNG_SEED)
        self.hue        = 0.0
        self.pulse      = 0.0
        self.pvel       = 0.0
        self._bass_flash = 0.0   # spikes on strong bass, inflates rendered bubble size
        
        self.max_bubbles = self.MAX
        init_size = 200
        if getattr(config, "LOW_SPEC", False):
            self.max_bubbles = self.MAX // 2
            init_size = 100
            
        self._W = config.WIDTH
        self._H = config.HEIGHT
        self.pool = [self._make(y=float(self._rng.uniform(0, self._H))) for _ in range(init_size)]
        self._surf_cache: dict = {}

    def _make(self, y=None):
        r = float(self._rng.uniform(8, 45))
        return {
            "x":      float(self._rng.uniform(self._W * 0.02, self._W * 0.98)),
            "y":      float(self._H + r) if y is None else y,
            "r":      r,
            "vx":     float(self._rng.normal(0, 0.4)),
            "vy":    -float(self._rng.uniform(1.0, 3.2)),
            "hue":    float(self._rng.uniform(0, 1.0)),
            "wobble": float(self._rng.uniform(0.025, 0.07)),
            "phase":  float(self._rng.uniform(0, math.tau)),
        }

    def _get_bsurf(self, size):
        # Quantize size to nearest multiple of 8 to avoid infinite cache growth
        size = min(self.MAX_RENDER_DIAMETER, max(8, ((size + 7) // 8) * 8))
        s = self._surf_cache.get(size)
        if s is None:
            if len(self._surf_cache) >= 128:
                # Evict oldest entries
                keys = list(self._surf_cache.keys())
                for k in keys[:64]:
                    del self._surf_cache[k]
            s = pygame.Surface((size, size), pygame.SRCALPHA)
            self._surf_cache[size] = s
        s.fill((0, 0, 0, 0))
        return s

    def release(self) -> None:
        self._surf_cache.clear()

    def _spawn(self, beat, bass, mid, high):
        beat_sel = min(beat, 1.0)
        hue_spread = 0.35 + beat_sel * 0.30
        
        # Spawn more bubbles on bass beats and treble transients
        n_spawn = int(2 + beat_sel * 12 + bass * 6 + high * 8)
        if getattr(config, "LOW_SPEC", False):
            n_spawn = max(1, n_spawn // 2)
            
        for _ in range(n_spawn):
            if len(self.pool) < self.max_bubbles:
                b = self._make()
                b["vy"]  *= (1 + beat_sel * 0.8 + mid * 0.6)
                b["r"]   *= (1 + bass * 1.2)
                b["hue"]  = (self.hue + float(self._rng.uniform(0, hue_spread))) % 1.0
                self.pool.append(b)

        # Mega-bubbles on strong bass hits: 1-3 extra large luminous bubbles
        if beat > 0.7 and len(self.pool) < self.max_bubbles:
            n_mega = 1 + int((beat - 0.7) * 4)
            if getattr(config, "LOW_SPEC", False):
                n_mega = max(1, n_mega // 2)
            for _ in range(n_mega):
                if len(self.pool) < self.max_bubbles:
                    b = self._make()
                    b["r"]  *= (2.2 + bass * 2.0)
                    b["vy"] *= (1.4 + mid * 0.5)
                    b["hue"] = (self.hue + float(self._rng.uniform(0, 0.5))) % 1.0
                    self.pool.append(b)

    def draw(self, surf, waveform, fft, beat, tick):
        self.hue += 0.005
        bass = min(max(float(beat), 0.0), 1.5)
        mid  = config.MID_ENERGY
        high = config.TREBLE_ENERGY
        W, H = surf.get_size()
        if W != self._W or H != self._H:
            self._W, self._H = W, H

        self.pvel += bass * 0.75
        self.pvel += -self.pulse * 0.35
        self.pvel *= 0.52
        self.pulse += self.pvel

        # Bass flash: spike on strong hits, inflate all rendered bubbles for ~10 frames
        if bass > 0.65:
            self._bass_flash = max(self._bass_flash, bass * 2.8)
        self._bass_flash = max(0.0, self._bass_flash - 0.18)

        self._spawn(beat, bass, mid, high)

        alive = []
        for b in self.pool:
            # Treble adds horizontal high-frequency wobble/jitter to the bubble paths
            b["x"]   += b["vx"] + math.sin(tick * b["wobble"] + b["phase"]) * 0.9 * (1.0 + high * 2.5)
            # Mids accelerate the bubble rising speed
            b["y"]   += b["vy"] * (1.0 + mid * 0.8)
            b["hue"]  = (b["hue"] + 0.004) % 1.0
            if b["y"] + b["r"] < 0:
                continue

            life  = max(0.0, min(1.0, b["y"] / H))
            r     = max(2, int(b["r"] * (1 + self.pulse * 0.90 + mid * 0.35 + high * 0.20 + self._bass_flash * 0.45)))
            r     = min(r, max(8, self.MAX_RENDER_DIAMETER // 2 - 14))
            pad   = r + 14
            alpha = int(life * 160)
            size = max(8, ((pad * 2 + 7) // 8) * 8)
            bsurf = self._get_bsurf(size)
            cc = size // 2

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
            if bass > 1.0:
                excess = bass - 1.0
                er1 = max(1, int(r * (1.7 + excess * 0.4)))
                ec1 = hsl((b["hue"] + 0.33) % 1.0, l=0.90)
                er1_alpha = max(0, min(255, int(alpha * excess * 0.55)))
                pygame.draw.circle(bsurf, (*ec1, er1_alpha), (cc, cc), er1, 2)
                if excess > 0.5:
                    er2 = max(1, int(r * (2.0 + excess * 0.3)))
                    ec2 = hsl((b["hue"] + 0.66) % 1.0, l=0.85)
                    er2_alpha = max(0, min(255, int(alpha * (excess - 0.5) * 0.45)))
                    pygame.draw.circle(bsurf, (*ec2, er2_alpha), (cc, cc), er2, 2)

            surf.blit(bsurf, (int(b["x"]) - cc, int(b["y"]) - cc))
            alive.append(b)

        self.pool = alive[-self.MAX:]
