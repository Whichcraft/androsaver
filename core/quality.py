from __future__ import annotations

import math
from collections import deque

import config


class QualityGovernor:
    """Small hysteretic frame-time controller for shared effect resolution."""

    _SCALES = (0.60, 0.80, 1.00)
    _WINDOW = 30
    _COOLDOWN_FRAMES = 120

    def __init__(self, fps=None):
        self.fps = max(1.0, float(fps or config.FPS))
        self.tier = max(0, min(2, int(getattr(config, "QUALITY_TIER", 2))))
        self.samples = deque(maxlen=self._WINDOW)
        self.cooldown = 0
        self.enabled = True
        self._publish()

    def _publish(self):
        config.QUALITY_TIER = self.tier
        config.QUALITY_SCALE = self._SCALES[self.tier]

    def observe(self, frame_ms, enabled=True):
        if not enabled:
            self.enabled = False
            return self.tier
        self.enabled = True
        try:
            value = float(frame_ms)
        except (TypeError, ValueError):
            return self.tier
        if not math.isfinite(value) or value < 0.0:
            return self.tier
        self.samples.append(value)
        if self.cooldown:
            self.cooldown -= 1
            return self.tier
        if len(self.samples) < self._WINDOW:
            return self.tier
        ordered = sorted(self.samples)
        p90 = ordered[int(0.90 * (len(ordered) - 1))]
        target = 1000.0 / self.fps
        if p90 > target * 1.20 and self.tier > 0:
            self.tier -= 1
            self.cooldown = self._COOLDOWN_FRAMES
            self._publish()
        elif p90 < target * 0.82 and self.tier < 2:
            self.tier += 1
            self.cooldown = self._COOLDOWN_FRAMES
            self._publish()
        return self.tier
