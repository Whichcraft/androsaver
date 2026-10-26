"""Mandelbox Temple — bounded CPU fractal escape-field architecture."""
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
uniform float u_time,u_hue,u_fold,u_angle,u_bass,u_high;
vec3 palette(float h,float v) {
    return clamp(0.5+0.5*cos(6.2831853*(h+vec3(0.0,0.3333,0.6667))),0.0,1.0)*v;
}
void main() {
    vec2 uv=gl_FragCoord.xy/u_resolution;
    vec2 p=(uv-0.5)*vec2(2.8,2.0);
    float c=cos(u_angle),s=sin(u_angle);
    p=mat2(c,-s,s,c)*p;
    vec2 z=p;
    float orbit=1.0, escaped=0.0, shade=0.0;
    for(int i=0;i<18;++i) {
        z=abs(z)*u_fold-vec2(0.85,0.10);
        float r2=dot(z,z);
        orbit=min(orbit,min(abs(z.x)+abs(z.y),1.0));
        if(r2>4.0 && escaped==0.0) { escaped=1.0; shade=float(i)/18.0; }
        z=z*0.94+p*0.04;
    }
    float bands=0.5+0.5*cos(orbit*50.0-u_time*0.8);
    float light=clamp(0.08+escaped*(0.25+shade*0.45)+bands*0.35+u_bass*0.08,0.0,0.94);
    float hue=u_hue+orbit*0.48+shade*0.22+u_high*0.025;
    fragColor=vec4(palette(hue,light),1.0);
}
"""


class Mandelbox(Effect):
    IS_GL = True
    TRAIL_ALPHA = 42
    # Preserve full-size pixels instead of enlarging a coarse internal field.
    RES_DIV = 1
    MAX_ITERATIONS = 18
    MAX_FIELD = 1.0

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self._W = self._H = 1
        self._x = self._y = None
        self._field = None
        self._orbit = None
        self._surface = pygame.Surface((1, 1))
        self._scaled = pygame.Surface((1, 1))
        self._hue = 0.0
        self._gl_field = FullscreenField(_FRAGMENT_SHADER)
        self._reset_state()

    def _reset_state(self):
        W, H, _ = self._render_size()
        self._W, self._H = W, H
        xx = np.linspace(-1.4, 1.4, W, dtype=np.float32)
        yy = np.linspace(-1.0, 1.0, H, dtype=np.float32)
        self._x, self._y = np.meshgrid(xx, yy)
        self._field = np.zeros((H, W), dtype=np.float32)
        self._orbit = np.ones((H, W), dtype=np.float32)
        self._x_work = np.empty_like(self._field)
        self._y_work = np.empty_like(self._field)
        self._radius_work = np.empty_like(self._field)
        self._temp_work = np.empty_like(self._field)
        self._escaped = np.empty((H, W), dtype=bool)
        self._newly_escaped = np.empty_like(self._escaped)
        self._mask_work = np.empty_like(self._escaped)
        self._surface = pygame.Surface((W, H))
        self._scaled = pygame.Surface((max(1, config.WIDTH), max(1, config.HEIGHT)))
        self._hue = 0.0

    def _render(self, surf, high):
        intensity = np.clip(self._field, 0.0, self.MAX_FIELD)
        hue = (self._hue + self._orbit * 0.22 + high * 0.04) % 1.0
        glow = np.clip(intensity * 1.7, 0.0, 1.0)
        red = np.clip((np.sin(hue * math.tau) * 127 + 128) * glow, 0, 255)
        green = np.clip((np.sin((hue + 0.333) * math.tau) * 127 + 128) * glow, 0, 255)
        blue = np.clip((np.sin((hue + 0.667) * math.tau) * 127 + 128) * glow, 0, 255)
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
            self._hue = (self._hue + 0.001 + mid * 0.0008) % 1.0
            uniforms = self._gl_uniforms(surf.get_size(), bass, mid, high, tick)
            self._gl_field.render(self.renderer, uniforms)
            return
        fold = 1.72 + bass * 0.14 + mid * 0.025
        angle = tick * (0.004 + high * 0.001)
        c, s = math.cos(angle), math.sin(angle)
        x, y = self._x_work, self._y_work
        np.multiply(self._x, c, out=x)
        np.multiply(self._y, -s, out=self._radius_work)
        np.add(x, self._radius_work, out=x)
        np.multiply(self._x, s, out=y)
        np.multiply(self._y, c, out=self._radius_work)
        np.add(y, self._radius_work, out=y)
        orbit = self._orbit
        orbit.fill(1.0)
        escaped = self._escaped
        escaped.fill(False)
        self._field.fill(1.0)
        for iteration in range(self.MAX_ITERATIONS):
            np.multiply(x, fold, out=x)
            x -= 0.5
            np.abs(x, out=x)
            x -= 0.35
            np.clip(x, -2.0, 2.0, out=x)
            np.multiply(y, fold, out=y)
            y += 0.25
            np.abs(y, out=y)
            y -= 0.35
            np.clip(y, -2.0, 2.0, out=y)
            np.square(x, out=self._radius_work)
            np.square(y, out=self._temp_work)
            np.add(self._radius_work, self._temp_work, out=self._radius_work)
            np.greater(self._radius_work, 4.0, out=self._newly_escaped)
            np.logical_not(escaped, out=self._mask_work)
            np.logical_and(self._newly_escaped, self._mask_work, out=self._newly_escaped)
            self._field[self._newly_escaped] = iteration / float(self.MAX_ITERATIONS)
            np.logical_or(escaped, self._newly_escaped, out=escaped)
            np.abs(x, out=self._radius_work)
            np.abs(y, out=self._temp_work)
            np.add(self._radius_work, self._temp_work, out=self._radius_work)
            np.minimum(orbit, self._radius_work, out=orbit)
            np.multiply(x, 0.94, out=x)
            np.multiply(self._x, 0.04, out=self._radius_work)
            np.add(x, self._radius_work, out=x)
            np.multiply(y, 0.94, out=y)
            np.multiply(self._y, 0.04, out=self._radius_work)
            np.add(y, self._radius_work, out=y)
        np.clip(orbit, 0.0, 1.0, out=self._orbit)
        self._hue = (self._hue + 0.001 + mid * 0.0008) % 1.0
        self._render(surf, high)

    def _gl_uniforms(self, size, bass, mid, high, tick):
        return {
            "u_resolution": tuple(map(float, size)), "u_time": tick * 0.035,
            "u_hue": self._hue, "u_fold": 1.72 + bass * 0.14 + mid * 0.025,
            "u_angle": tick * (0.004 + high * 0.001), "u_bass": bass, "u_high": high,
        }

    def draw_frame(self, width, height, waveform, fft, beat, tick, renderer=None):
        renderer = renderer or self.renderer
        if not self._gl_field.available(renderer):
            return super().draw_frame(width, height, waveform, fft, beat, tick, renderer)
        old_size = (config.WIDTH, config.HEIGHT)
        config.WIDTH, config.HEIGHT = int(width), int(height)
        try:
            bass = min(max(float(beat), 0.0), 1.5)
            mid = min(max(float(config.MID_ENERGY), 0.0), 4.0)
            high = min(max(float(config.TREBLE_ENERGY), 0.0), 4.0)
            self._hue = (self._hue + 0.001 + mid * 0.0008) % 1.0
            return self._gl_field.draw_frame(renderer, int(width), int(height), self._gl_uniforms((width, height), bass, mid, high, tick))
        finally:
            config.WIDTH, config.HEIGHT = old_size

    def release(self):
        self._gl_field.release()
        self._surface = None
        self._scaled = None
        self._x = self._y = self._field = self._orbit = None
        self._x_work = self._y_work = self._radius_work = self._temp_work = None
        self._escaped = self._newly_escaped = self._mask_work = None
