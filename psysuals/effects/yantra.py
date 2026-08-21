import math

import numpy as np
import pygame

import config
from .utils import hsl


from .base import Effect

class Yantra(Effect):
    """Psychedelic sacred-geometry mandala for psytrance.

    Six concentric polygon rings (triangle → octagon) alternate rotation
    direction, each driven by its own frequency band.  Web lines connect
    adjacent ring vertices. Neon spokes radiate from the centre. Beat
    triggers spring-physics pulses that push every ring outward then snap
    it back — timed to the kick drum of fast BPM tracks.
    """

    N_RINGS  = 7
    N_SPOKES = 24

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.hue  = 0.0
        self.time = 0.0
        signs = [1, -1, 1, -1, 1, -1, 1]
        self.rot  = [i / self.N_RINGS * math.tau for i in range(self.N_RINGS)]
        self.rvel = [signs[i] * (0.010 + i * 0.0030) for i in range(self.N_RINGS)]
        self.poff = [0.0] * self.N_RINGS
        self.pvel = [0.0] * self.N_RINGS

    def _ring_verts(self, i, r, cx, cy):
        n   = 3 + i
        rot = self.rot[i]
        return [(cx + math.cos(v / n * math.tau + rot) * r,
                 cy + math.sin(v / n * math.tau + rot) * r)
                for v in range(n)]

    def draw(self, surf, waveform, fft, beat, tick):
        self.hue  += 0.005
        W, H = surf.get_size()
        bass = beat
        mid  = config.MID_ENERGY
        high = config.TREBLE_ENERGY
        
        self.time += 0.02 + mid * 0.05 + high * 0.03

        cx, cy  = W // 2, H // 2
        max_r   = min(W, H) * 0.46

        # Construct distinct bands combining different proportions of the 3 detections
        bands = [
            bass,
            (bass + mid) * 0.5,
            mid,
            (mid + high) * 0.5,
            high,
            (bass + high) * 0.5,
            (mid + high) * 0.5
        ]

        for i in range(self.N_RINGS):
            e = min(bands[i], 1.0)
            self.pvel[i] += bass * (0.24 + e * 0.12)
            self.pvel[i] += -self.poff[i] * 0.22
            self.pvel[i] *= 0.65
            self.poff[i] += self.pvel[i]
            self.rot[i]  += self.rvel[i] * (1.0 + e * 2.8 + bass * 1.2 + mid * 1.5)

        all_verts = []
        for i in range(self.N_RINGS):
            base_r = max_r * (0.28 + i / (self.N_RINGS - 1) * 0.62)
            r      = base_r * (1.0 + self.poff[i] * 0.38)
            # Add high-frequency jitter to vertex positions
            jitter = high * 6.0 * (i / self.N_RINGS)
            all_verts.append(self._ring_verts(i, r + jitter, cx, cy))

        for i in range(self.N_RINGS - 1):
            v_out = all_verts[i + 1]
            v_in  = all_verts[i]
            n_out = len(v_out)
            n_in  = len(v_in)
            e     = min(bands[i], 1.0)
            h     = (self.hue + i / self.N_RINGS * 0.5) % 1.0
            for k, (ox, oy) in enumerate(v_out):
                nearest = round(k / n_out * n_in) % n_in
                ix, iy  = v_in[nearest]
                pygame.draw.line(surf,
                                 hsl(h, l=0.30 + e * 0.20 + mid * 0.10),
                                 (int(ox), int(oy)), (int(ix), int(iy)), 1)

        for i in range(self.N_RINGS - 1, -1, -1):  # outermost first so inner rings layer on top
            e     = min(bands[i], 1.0)
            h     = (self.hue + i / self.N_RINGS * 0.55) % 1.0
            bright = 0.52 + e * 0.20 + mid * 0.15
            lw    = max(1, int(1 + e * 1.2 + high * 1.8))
            ipts  = [(int(x), int(y)) for x, y in all_verts[i]]
            pygame.draw.polygon(surf, hsl(h, l=bright), ipts, lw)
            n = len(ipts)
            if n >= 5:
                step = 2
                for k in range(n):
                    pygame.draw.line(surf, hsl(h, l=bright * 0.6),
                                     ipts[k], ipts[(k + step) % n], 1)

        outer_r = max_r * (1.02 + bass * 0.20 + mid * 0.10)
        for s in range(self.N_SPOKES):
            a   = s / self.N_SPOKES * math.tau + self.time * 0.22
            a  += math.sin(self.time * 2.4 + s * 0.85) * (0.05 + mid * 0.15)
            # Treble adds a jittery radial spoke distortion
            spoke_r = outer_r + (math.sin(self.time * 10.0 + s) * (high * 20.0))
            x2  = int(cx + math.cos(a) * spoke_r)
            y2  = int(cy + math.sin(a) * spoke_r)
            h   = (self.hue + s / self.N_SPOKES * 0.35 + high * 0.2) % 1.0
            lw  = max(1, int(1 + bass * 1.5 + high * 2.0))
            pygame.draw.line(surf,
                             hsl(h, l=0.30 + bass * 0.30 + mid * 0.15 + high * 0.20),
                             (cx, cy), (x2, y2), lw)

        cr = max(2, int(4 + bass * 15 + mid * 5))
        pygame.draw.circle(surf, hsl(self.hue, l=0.55 + bass * 0.25 + mid * 0.15), (cx, cy), cr)
        inner_cr = max(1, int((cr // 3) * (1.0 + high * 0.5)))
        pygame.draw.circle(surf, hsl((self.hue + 0.5) % 1.0, l=0.75),
                           (cx, cy), inner_cr)
