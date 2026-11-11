"""Tick-based transitions; effect selection stays in the application."""
from dataclasses import dataclass


def progress_at(tick, start, duration):
    return min(1.0, max(0.0, (tick - start) / duration))


def interpolate(source, target, progress):
    return source + (target - source) * progress


@dataclass(frozen=True)
class PresetValues:
    gain: float
    alpha: int
    cf_frames: float
    switch_now: bool
    complete: bool


@dataclass
class PresetTransition:
    start: int
    duration: int
    mode_idx: int
    bg_mode_i: int
    bg_on: bool
    from_gain: float
    to_gain: float
    from_alpha: float
    to_alpha: float
    from_cf: float
    to_cf: float
    switched: bool = False

    def sample(self, tick) -> PresetValues:
        progress = progress_at(tick, self.start, self.duration)
        return PresetValues(
            interpolate(self.from_gain, self.to_gain, progress),
            int(round(interpolate(self.from_alpha, self.to_alpha, progress))),
            interpolate(self.from_cf, self.to_cf, progress),
            not self.switched and progress >= .5,
            progress >= 1.,
        )


@dataclass
class ParameterTransition:
    start: int
    duration: int
    source: dict[str, float]
    target: dict[str, float]

    def sample(self, tick) -> tuple[dict[str, float], bool]:
        progress = progress_at(tick, self.start, self.duration)
        return ({name: interpolate(value, self.target[name], progress)
                 for name, value in self.source.items() if name in self.target},
                progress >= 1.)
