import colorsys
import numpy as np


def hsl(h, s=1.0, l=0.5):
    r, g, b = colorsys.hls_to_rgb(h % 1.0, max(0.0, min(l, 1.0)), s)
    return (int(r * 255), int(g * 255), int(b * 255))


def _hsl_batch(h, s=np.float32(1.0), l=0.5):
    """Vectorised HSL→RGB for numpy arrays; returns uint8 array of shape (..., 3)."""
    h  = np.asarray(h, dtype=float) % 1.0
    l  = np.clip(np.asarray(l, dtype=float), 0.0, 1.0)
    c  = (1.0 - np.abs(2.0 * l - 1.0)) * s
    h6 = h * 6.0
    x  = c * (1.0 - np.abs(h6 % 2.0 - 1.0))
    m  = l - c / 2.0
    i  = h6.astype(np.int32) % 6
    z  = np.zeros_like(h)
    r  = np.select([i==0,i==1,i==2,i==3,i==4,i==5], [c,x,z,z,x,c]) + m
    g  = np.select([i==0,i==1,i==2,i==3,i==4,i==5], [x,c,c,x,z,z]) + m
    b  = np.select([i==0,i==1,i==2,i==3,i==4,i==5], [z,z,x,c,c,x]) + m
    return np.stack([
        np.clip(r * 255, 0, 255).astype(np.uint8),
        np.clip(g * 255, 0, 255).astype(np.uint8),
        np.clip(b * 255, 0, 255).astype(np.uint8),
    ], axis=-1)


def spectrum_bands(fft, count):
    """Mean FFT magnitude in ``count`` bands with squared edge spacing.

    The squared spacing gives the low end more bands. Non-finite input reads
    and infinite input reads as silence, and a spectrum too short to split
    returns zeros.
    """
    bands = np.zeros(count)
    n = len(fft)
    if n >= 8:
        edges = np.unique((np.linspace(0.0, 1.0, count + 1) ** 2 * n * 0.6).astype(int))
        values = np.abs(np.asarray(fft[:edges[-1]], dtype=np.float64))
        values[~np.isfinite(values)] = 0.0
        means = np.add.reduceat(values, edges[:-1]) / np.diff(edges)
        bands[:] = np.interp(np.linspace(0, len(means) - 1, count), np.arange(len(means)), means)
    return bands


def track_levels(levels, peak, fft, smooth):
    """Advance peak-normalised spectrum levels; returns ``(levels, peak)``.

    The peak follows loud passages at once and decays slowly, so the levels
    stay in 0..1 whatever the input gain. ``smooth`` is ``Effect._smooth``.
    """
    bands = spectrum_bands(fft, len(levels))
    loudest = float(bands.max())
    peak = max(1e-3, smooth(peak, loudest, 0.01), loudest)
    return smooth(levels, np.sqrt(bands / peak), 0.3), peak


def padded(fft, length):
    """Return ``fft`` with at least ``length`` bins, zero-padding the top.

    Effects that index fixed bins stay safe when a host passes a short or
    empty spectrum; full-length input is returned unchanged.
    """
    if len(fft) >= length:
        return fft
    out = np.zeros(length, dtype=np.float32)
    out[:len(fft)] = fft
    return out
