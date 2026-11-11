"""Phason Bloom — bounded quasiperiodic wave interference."""
import math

import numpy as np
import pygame
import pygame.surfarray as surfarray

from .base import Effect
from .gl_field import FullscreenField


_FRAGMENT_SHADER = """
#version 330 core
out vec4 fragColor;
uniform vec2 u_resolution;
uniform float u_time, u_hue, u_bass, u_mid, u_high, u_pulse;
uniform int u_waves;
vec3 palette(float h, float v) {
    return clamp(0.5 + 0.5*cos(6.2831853*(h + vec3(0.0,0.3333,0.6667))),0.0,1.0)*v;
}
void main() {
    vec2 p = (gl_FragCoord.xy / u_resolution - 0.5) * vec2(12.56637,12.56637);
    float v = 0.0;
    for (int i=0; i<11; ++i) {
        if (i >= u_waves) break;
        float a = 6.2831853*float(i)/float(u_waves) + sin(u_time*0.13+float(i))*0.16;
        vec2 k = vec2(cos(a),sin(a));
        v += cos(dot(p,k)*(0.82+u_mid*0.035) + u_time*(0.30+float(i)*0.047));
    }
    v /= float(u_waves);
    v += cos((p.x+p.y)*0.5-u_time*0.7)*(0.20+u_pulse*0.25);
    float bands = 0.5+0.5*cos(v*11.0 + u_time*0.42);
    float hue = u_hue + v*0.21 + bands*0.22 + u_high*0.025;
    float light = clamp(0.12 + bands*0.62 + u_bass*0.10 + u_pulse*0.13,0.0,0.95);
    fragColor = vec4(palette(hue, light),1.0);
}
"""


class Phason(Effect):
    IS_GL = True
    TIME_BASED = True
    TRAIL_ALPHA = 38
    # Preserve full-size pixels instead of enlarging a coarse internal field.
    CONTINUOUS_QUALITY = True
    RES_DIV = 1
    MAX_WAVES = 11
    MAX_FIELD = 1.0

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self._W = self._H = 1
        self._x = self._y = None
        self._field = None
        self._field_work = None
        self._projection = None
        self._wave_work = None
        self._phases = np.zeros(self.MAX_WAVES, dtype=np.float32)
        self._angles = np.zeros(self.MAX_WAVES, dtype=np.float32)
        self._surface = pygame.Surface((1, 1))
        self._scaled = pygame.Surface((1, 1))
        self._hue = 0.0
        self._beat_prev = 0.0
        self._pulse = 0.0
        self._time = 0.0
        self._gl_field = FullscreenField(_FRAGMENT_SHADER)
        self._reset_state()

    def _reset_state(self):
        W, H, _ = self._render_size()
        self._W, self._H = W, H
        xx = np.linspace(-math.tau, math.tau, W, dtype=np.float32)
        yy = np.linspace(-math.tau, math.tau, H, dtype=np.float32)
        self._x, self._y = np.meshgrid(xx, yy)
        self._field = np.zeros((H, W), dtype=np.float32)
        self._field_work = np.zeros_like(self._field)
        self._projection = np.empty_like(self._field)
        self._wave_work = np.empty_like(self._field)
        self._surface = pygame.Surface((W, H))
        self._scaled = pygame.Surface((max(1, self.frame.width), max(1, self.frame.height)))

    def _cpu_colours(self, region, high):
        field = np.clip(self._field[region], -self.MAX_FIELD, self.MAX_FIELD)
        intensity = np.clip((field + 1.0) * 0.5, 0.0, 1.0)
        hue = (self._hue + intensity * 0.72 + high * 0.03) % 1.0
        red = np.clip((np.sin(hue * math.tau) * 127 + 128) * intensity, 0, 255)
        green = np.clip((np.sin((hue + 0.333) * math.tau) * 127 + 128) * intensity, 0, 255)
        blue = np.clip((np.sin((hue + 0.667) * math.tau) * 127 + 128) * intensity, 0, 255)
        colors = np.stack((red, green, blue), axis=-1).astype(np.uint8)
        return colors

    def _render(self, surf, high):
        pixels = surfarray.pixels3d(self._surface)
        try:
            for first in range(0, self._H, 64):
                last = min(first+64, self._H)
                colors = self._cpu_colours(np.s_[first:last], high)
                pixels[:, first:last] = colors.transpose(1, 0, 2)
        finally:
            del pixels
        if self._surface.get_size() != surf.get_size():
            if self._scaled.get_size() != surf.get_size():
                self._scaled = pygame.Surface(surf.get_size())
            pygame.transform.smoothscale(self._surface, surf.get_size(), self._scaled)
            surf.blit(self._scaled, (0, 0), special_flags=pygame.BLEND_RGB_MAX)
        else:
            surf.blit(self._surface, (0, 0), special_flags=pygame.BLEND_RGB_MAX)

    def _draw(self, surf, waveform, fft, beat, tick):
        W, H, _ = self._render_size()
        if (W, H) != (self._W, self._H):
            self._reset_state()
        bass, mid, high = self._advance_state(beat)
        if self._gl_field.available(self.renderer):
            self._gl_field.render(self.renderer, self._gl_uniforms(surf.get_size(), bass, mid, high, tick))
            return
        wave_count = 7
        indices = np.arange(self.MAX_WAVES)
        self._angles[:] = indices * (math.tau / wave_count) + np.sin(self._time*.13 + indices)*.16
        self._phases[:] = self._time*(.30 + indices*.047)
        directions = [(math.cos(float(angle)), math.sin(float(angle)))
                      for angle in self._angles[:wave_count]]
        for first in range(0, self._H, 64):
            self._compute_cpu_rows(np.s_[first:min(first+64, self._H)], mid, directions)
        self._render(surf, high)

    def _compute_cpu_rows(self, region, mid, directions):
        # Keep all seven waves for each pixel together in cache. Each pixel
        # retains the same float32 operations and summation order.
        field = self._field_work[region]
        wave = self._wave_work[region]
        projection = self._projection[region]
        x, y = self._x[region], self._y[region]
        field.fill(0.0)
        for index, (cos_angle, sin_angle) in enumerate(directions):
            np.multiply(x, cos_angle, out=wave)
            np.multiply(y, sin_angle, out=projection)
            np.add(wave, projection, out=wave)
            wave *= .82 + mid*.035
            wave += self._phases[index]
            np.cos(wave, out=wave)
            np.add(field, wave, out=field)
        field /= float(len(directions))
        np.multiply(x, 0.5, out=wave)
        np.multiply(y, 0.5, out=projection)
        np.add(wave, projection, out=wave)
        wave -= self._time * .7
        np.cos(wave, out=wave)
        wave *= .20 + self._pulse*.25
        np.add(field, wave, out=field)
        np.clip(field, -1.0, 1.0, out=self._field[region])

    def _advance_state(self, beat):
        motion, light = self._audio_response(beat)
        bass,mid,high = motion
        step = self._frame_step()
        self._time += (.004 + mid*.001) * step
        self._hue = (self._hue + (.00025 + mid*.0002) * step) % 1
        self._pulse = self._smooth(self._pulse, min(.12, bass*.08), .025)
        return bass,mid,high

    def _gl_uniforms(self, size, bass, mid, high, tick):
        return {
            "u_resolution": tuple(map(float, size)),
            "u_time": self._time,
            "u_hue": self._hue,
            "u_bass": bass,
            "u_mid": mid,
            "u_high": high,
            "u_pulse": self._pulse,
            "u_waves": 7,
        }

    def _draw_frame(self, width, height, waveform, fft, beat, tick, renderer=None):
        if not self._gl_field.available(renderer or self.renderer):
            return super()._draw_frame(width, height, waveform, fft, beat, tick, renderer)
        bass, mid, high = self._advance_state(beat)
        uniforms = self._gl_uniforms((width, height), bass, mid, high, tick)
        return self._gl_field.draw_frame(renderer or self.renderer, int(width), int(height), uniforms)

    def release(self):
        self._gl_field.release()
        self._surface = None
        self._scaled = None
        self._x = self._y = self._field = None
        self._field_work = self._projection = self._wave_work = None
