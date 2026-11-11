"""Explicit construction options and immutable per-frame render inputs."""
from dataclasses import dataclass, field
import sys
import math
from numbers import Integral, Real

import numpy as np


def _integer(name, value, minimum=1, maximum=None):
    if (isinstance(value, (bool, np.bool_)) or not isinstance(value, Integral)
            or value < minimum or (maximum is not None and value > maximum)):
        bounds = f" >= {minimum}" if maximum is None else f" in {minimum}..{maximum}"
        raise ValueError(f"{name} must be an integer{bounds}")


def _finite(name, value, minimum=0., maximum=None, positive=False):
    if (isinstance(value, (bool, np.bool_)) or not isinstance(value, Real)
            or not math.isfinite(value) or value < minimum
            or (positive and value == 0) or (maximum is not None and value > maximum)):
        raise ValueError(f"{name} must be finite and within its supported range")


@dataclass(frozen=True)
class AudioConfig:
    sample_rate: int = 44100
    block_size: int = 1024
    channels: int = 1
    envelope_attack_seconds: float = .035
    envelope_release_seconds: float = .18

    def __post_init__(self):
        for name in ("sample_rate", "channels"):
            _integer(name, getattr(self, name))
        _integer("block_size", self.block_size, minimum=2)
        for name in ("envelope_attack_seconds", "envelope_release_seconds"):
            _finite(name, getattr(self, name), positive=True)


@dataclass(frozen=True)
class EffectOptions:
    width: int
    height: int
    low_spec: bool = False
    seed: int = 0
    audio: AudioConfig = field(default_factory=AudioConfig)

    def __post_init__(self):
        for name in ("width", "height"):
            _integer(name, getattr(self, name))
        _integer("seed", self.seed, minimum=0, maximum=2 ** 32 - 1)
        object.__setattr__(self, "seed", int(self.seed))
        if not isinstance(self.low_spec, bool):
            raise ValueError("low_spec must be a bool")
        if not isinstance(self.audio, AudioConfig):
            raise ValueError("audio must be an AudioConfig")


@dataclass(frozen=True)
class FrameContext:
    width: int
    height: int
    dt: float = 1 / 60
    fps: int = 60
    tick: int = 0
    profile: str = "auto"
    quality_tier: int = 2
    quality_scale: float = 1.
    waveform: np.ndarray = field(default_factory=lambda: np.zeros(1024, dtype=np.float32))
    fft: np.ndarray = field(default_factory=lambda: np.zeros(512, dtype=np.float32))
    beat: float = 0.
    mid: float = 0.
    treble: float = 0.
    envelopes: tuple[float, float, float] = (0., 0., 0.)
    bpm: float = 0.
    phase: float = 0.
    gain: float = .7
    is_silent: bool = True

    def __post_init__(self):
        for name in ("width", "height", "fps"):
            _integer(name, getattr(self, name))
        _integer("tick", self.tick, minimum=0)
        _integer("quality_tier", self.quality_tier, minimum=0, maximum=2)
        _finite("quality_scale", self.quality_scale, maximum=1., positive=True)
        for name in ("dt", "beat", "mid", "treble", "bpm", "gain"):
            _finite(name, getattr(self, name))
        _finite("phase", self.phase, maximum=1.)
        if not isinstance(self.is_silent, bool):
            raise ValueError("is_silent must be a bool")
        if self.profile not in ("auto", "laptop", "tv"):
            raise ValueError("unknown visual profile")
        try:
            envelopes = tuple(self.envelopes)
        except TypeError as exc:
            raise ValueError("envelopes must contain three finite nonnegative values") from exc
        if len(envelopes) != 3:
            raise ValueError("envelopes must contain three finite nonnegative values")
        for value in envelopes:
            _finite("envelopes", value)
        object.__setattr__(self, "envelopes", envelopes)
        for name in ("waveform", "fft"):
            try:
                array = np.array(getattr(self, name), copy=True)
            except (TypeError, ValueError) as exc:
                raise ValueError(f"{name} must be a nonempty 1D real numeric array") from exc
            if (array.ndim != 1 or (name == "waveform" and not array.size) or array.dtype.kind not in "iuf"
                    or not np.isfinite(array).all()):
                raise ValueError(f"{name} must be a finite 1D real numeric array (waveform must be nonempty)")
            array.setflags(write=False)
            object.__setattr__(self, name, array)


def parse_seed(value: str | None) -> int:
    """Parse an explicitly supplied environment value without seeding globals."""
    try:
        return int(value or 0) % (2 ** 32)
    except ValueError:
        print("Startup: invalid PSYSUALS_SEED; using 0", file=sys.stderr)
        return 0
