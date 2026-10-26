"""Phason Bloom — bounded quasiperiodic wave interference."""
import math

import numpy as np
import pygame
import pygame.surfarray as surfarray

import config
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
    TRAIL_ALPHA = 38
    # Preserve full-size pixels instead of enlarging a coarse internal field.
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
        self._scaled = pygame.Surface((max(1, config.WIDTH), max(1, config.HEIGHT)))
        self._angles = np.arange(self.MAX_WAVES, dtype=np.float32) * (math.tau / self.MAX_WAVES)
        self._phases.fill(0.0)
        self._hue = 0.0
        self._beat_prev = 0.0
        self._pulse = 0.0

    def _render(self, surf, high):
        field = np.clip(self._field, -self.MAX_FIELD, self.MAX_FIELD)
        intensity = np.clip((field + 1.0) * 0.5, 0.0, 1.0)
        hue = (self._hue + intensity * 0.72 + high * 0.03) % 1.0
        red = np.clip((np.sin(hue * math.tau) * 127 + 128) * intensity, 0, 255)
        green = np.clip((np.sin((hue + 0.333) * math.tau) * 127 + 128) * intensity, 0, 255)
        blue = np.clip((np.sin((hue + 0.667) * math.tau) * 127 + 128) * intensity, 0, 255)
        colors = np.stack((red, green, blue), axis=-1).astype(np.uint8)
        pixels = surfarray.pixels3d(self._surface)
        try:
            pixels[:] = colors.transpose(1, 0, 2)
        finally:
            del pixels
        if self._surface.get_size() != surf.get_size():
            if self._scaled.get_size() != surf.get_size():
                self._scaled = pygame.Surface(surf.get_size())
            pygame.transform.smoothscale(self._surface, surf.get_size(), self._scaled)
            surf.blit(self._scaled, (0, 0), special_flags=pygame.BLEND_RGB_MAX)
        else:
            surf.blit(self._surface, (0, 0), special_flags=pygame.BLEND_RGB_MAX)

    def draw(self, surf, waveform, fft, beat, tick):
        W, H, _ = self._render_size()
        if (W, H) != (self._W, self._H):
            self._reset_state()
        bass = min(max(float(beat), 0.0), 1.5)
        mid = min(max(float(config.MID_ENERGY), 0.0), 4.0)
        high = min(max(float(config.TREBLE_ENERGY), 0.0), 4.0)
        if self._gl_field.available(self.renderer):
            if bass > 0.7 and self._beat_prev <= 0.7:
                self._pulse = min(1.0, self._pulse + 0.75)
            self._beat_prev = bass
            self._pulse *= 0.94
            self._hue = (self._hue + 0.001 + mid * 0.0007) % 1.0
            self._gl_field.render(self.renderer, self._gl_uniforms(surf.get_size(), bass, mid, high, tick))
            return
        bpm = min(max(float(getattr(config, "BPM", 0.0)), 0.0), 240.0)
        if bass > 0.7 and self._beat_prev <= 0.7:
            self._pulse = min(1.0, self._pulse + 0.75)
        self._beat_prev = bass
        self._pulse *= 0.94
        wave_count = min(self.MAX_WAVES, 5 + 2 * int(mid))
        speed = 0.002 + bpm * 0.000004 + high * 0.001
        self._angles[:wave_count] += 0.0007 + speed
        self._phases[:wave_count] = self._phases[:wave_count] + speed + self._pulse * 0.008
        field = self._field_work
        field.fill(0.0)
        for index in range(wave_count):
            angle = self._angles[index]
            np.multiply(self._x, math.cos(float(angle)), out=self._wave_work)
            np.multiply(self._y, math.sin(float(angle)), out=self._projection)
            np.add(self._wave_work, self._projection, out=self._wave_work)
            self._wave_work += self._phases[index]
            np.cos(self._wave_work, out=self._wave_work)
            np.add(field, self._wave_work, out=field)
        field /= float(wave_count)
        np.multiply(self._x, 0.5, out=self._wave_work)
        np.multiply(self._y, 0.5, out=self._projection)
        np.add(self._wave_work, self._projection, out=self._wave_work)
        np.cos(self._wave_work, out=self._wave_work)
        self._wave_work *= self._pulse
        np.add(field, self._wave_work, out=field)
        np.clip(field, -1.0, 1.0, out=self._field)
        self._hue = (self._hue + 0.001 + mid * 0.0007) % 1.0
        self._render(surf, high)

    def _gl_uniforms(self, size, bass, mid, high, tick):
        return {
            "u_resolution": tuple(map(float, size)),
            "u_time": float(tick) * 0.035,
            "u_hue": self._hue,
            "u_bass": bass,
            "u_mid": mid,
            "u_high": high,
            "u_pulse": self._pulse,
            "u_waves": min(self.MAX_WAVES, 5 + 2 * int(mid)),
        }

    def draw_frame(self, width, height, waveform, fft, beat, tick, renderer=None):
        if not self._gl_field.available(renderer or self.renderer):
            return super().draw_frame(width, height, waveform, fft, beat, tick, renderer)
        old_size = (config.WIDTH, config.HEIGHT)
        config.WIDTH, config.HEIGHT = int(width), int(height)
        try:
            bass = min(max(float(beat), 0.0), 1.5)
            mid = min(max(float(config.MID_ENERGY), 0.0), 4.0)
            high = min(max(float(config.TREBLE_ENERGY), 0.0), 4.0)
            if bass > 0.7 and self._beat_prev <= 0.7:
                self._pulse = min(1.0, self._pulse + 0.75)
            self._beat_prev = bass
            self._pulse *= 0.94
            self._hue = (self._hue + 0.001 + mid * 0.0007) % 1.0
            uniforms = self._gl_uniforms((width, height), bass, mid, high, tick)
            return self._gl_field.draw_frame(renderer or self.renderer, int(width), int(height), uniforms)
        finally:
            config.WIDTH, config.HEIGHT = old_size

    def release(self):
        self._gl_field.release()
        self._surface = None
        self._scaled = None
        self._x = self._y = self._field = None
        self._field_work = self._projection = self._wave_work = None
