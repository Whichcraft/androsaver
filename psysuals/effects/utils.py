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
