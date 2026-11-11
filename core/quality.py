from __future__ import annotations

import math
from collections import deque

from dataclasses import dataclass


@dataclass(frozen=True)
class QualityDecision:
    tier: int
    scale: float


class QualityGovernor:
    """Small hysteretic frame-time controller for shared effect resolution."""

    _SCALES = (0.60, 0.80, 1.00)
    _WINDOW = 30
    _COOLDOWN_FRAMES = 120

    def __init__(self, fps=60, tier=2):
        self.fps = max(1.0, float(fps))
        self.tier = max(0, min(2, int(tier)))
        self.samples = deque(maxlen=self._WINDOW)
        self.cooldown = 0

    @property
    def decision(self):
        return QualityDecision(self.tier, self._SCALES[self.tier])

    def observe(self, frame_ms, enabled=True):
        if not enabled:
            return self.decision
        try:
            value = float(frame_ms)
        except (TypeError, ValueError):
            return self.decision
        if not math.isfinite(value) or value < 0.0:
            return self.decision
        self.samples.append(value)
        if self.cooldown:
            self.cooldown -= 1
            return self.decision
        if len(self.samples) < self._WINDOW:
            return self.decision
        ordered = sorted(self.samples)
        p90 = ordered[int(0.90 * (len(ordered) - 1))]
        target = 1000.0 / self.fps
        if p90 > target * 1.20 and self.tier > 0:
            self.tier -= 1
            self.cooldown = self._COOLDOWN_FRAMES
        elif p90 < target * 0.82 and self.tier < 2:
            self.tier += 1
            self.cooldown = self._COOLDOWN_FRAMES
        return self.decision
