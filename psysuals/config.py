"""Shared mutable configuration — WIDTH/HEIGHT are set by the host at display
init time (psysualizer.py reads the actual monitor geometry from xrandr).
These defaults are placeholders only; effects must not be instantiated before
the display is opened."""

import os
import random
import sys

import numpy as np

WIDTH  = 0
HEIGHT = 0
_INITIALIZED = False


def assert_initialized():
    if not _INITIALIZED:
        raise RuntimeError(
            "config.WIDTH/HEIGHT accessed before display init. "
            "Ensure DisplayManager.open_display() is called before any effect __init__."
        )
FPS    = 60
LOW_SPEC = False

SAMPLE_RATE = 44100
BLOCK_SIZE  = 1024
CHANNELS    = 1

# Multi-band energy (normalised, updated each frame by psysualizer)
# Effects can read these directly for mid/treble reactivity.
MID_ENERGY    = 0.0   # bins 20-100  (~860 Hz – 4.3 kHz)
TREBLE_ENERGY = 0.0   # bins 100-256 (~4.3 kHz – 11 kHz)
BPM           = 0.0   # detected tempo (60–200), 0 until enough beats seen
DEFAULT_EFFECT_GAIN = 0.7
EFFECT_GAIN         = DEFAULT_EFFECT_GAIN  # current effect intensity
IS_SILENT           = True

# Silence handling: keep a faint idle motion floor, but suppress false beat
# spikes from noise-floor normalization before/after tracks.
SILENCE_RMS_ENTER = 0.0035
SILENCE_RMS_EXIT  = 0.0060
SILENCE_FFT_ENTER = 0.0015
SILENCE_FFT_EXIT  = 0.0030
SILENCE_FRAMES_ENTER = 6
SILENCE_BEAT_FLOOR   = 0.015
SILENCE_MID_FLOOR    = 0.020
SILENCE_TREBLE_FLOOR = 0.018

_seed_str = os.environ.get("PSYSUALS_SEED")
if _seed_str is not None:
    if not _seed_str:
        RNG_SEED = 0
    else:
        try:
            RNG_SEED = int(_seed_str) % (2 ** 32)
        except ValueError:
            print(
                f"config: invalid PSYSUALS_SEED={_seed_str!r}; using 0",
                file=sys.stderr,
            )
            RNG_SEED = 0
    random.seed(RNG_SEED)
    np.random.seed(RNG_SEED)
else:
    RNG_SEED = 0
