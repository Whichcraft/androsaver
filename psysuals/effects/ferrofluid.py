"""Ferrofluid Oracle — bounded magnetic potential contour field."""
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
uniform vec2 u_resolution, u_p0, u_p1, u_p2, u_p3, u_p4;
uniform float u_time, u_hue, u_bass, u_high, u_sign;
vec3 palette(float h, float v) {
    return clamp(0.5+0.5*cos(6.2831853*(h+vec3(0.0,0.3333,0.6667))),0.0,1.0)*v;
}
void main() {
    vec2 p = (gl_FragCoord.xy/u_resolution-0.5)*vec2(2.0,2.0);
    vec2 poles[5] = vec2[5](u_p0,u_p1,u_p2,u_p3,u_p4);
    float field=0.0;
    for (int i=0;i<5;++i) {
        float strength = 0.78 + 0.18*sin(float(i)*2.4+u_time*0.32);
        float charge = (i==0 ? u_sign : 1.0);
        field += charge*strength/(length(p-poles[i])+0.035);
    }
    float contour = 0.5+0.5*cos(field*3.7-u_time*0.18);
    float edge = exp(-abs(field-5.2)*0.36);
    float light = clamp(0.12+contour*0.47+edge*0.35+u_bass*0.10,0.0,0.96);
    float hue=u_hue+field*0.035+contour*0.22+u_high*0.025;
    fragColor=vec4(palette(hue,light),1.0);
}
"""


class Ferrofluid(Effect):
    IS_GL = True
    TRAIL_ALPHA = 40
    # Preserve full-size pixels instead of enlarging a coarse internal field.
    RES_DIV = 1
    MAX_POLES = 5
    MAX_CONTOURS = 6
    MAX_FIELD = 8.0

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self._rng = np.random.default_rng(config.RNG_SEED)
        self._W = self._H = 1
        self._x = self._y = None
        self._field = None
        self._field_dx = self._field_dy = None
        self._poles = None
        self._strength = None
        self._surface = pygame.Surface((1, 1))
        self._scaled = pygame.Surface((1, 1))
        self._hue = float(self._rng.random())
        self._beat_prev = 0.0
        self._invert_timer = 0
        self._gl_field = FullscreenField(_FRAGMENT_SHADER)
        self._reset_state()

    def _reset_state(self):
        W, H, _ = self._render_size()
        self._W, self._H = W, H
        xx = np.linspace(-1.0, 1.0, W, dtype=np.float32)
        yy = np.linspace(-1.0, 1.0, H, dtype=np.float32)
        self._x, self._y = np.meshgrid(xx, yy)
        self._field = np.zeros((H, W), dtype=np.float32)
        self._field_dx = np.zeros_like(self._field)
        self._field_dy = np.zeros_like(self._field)
        self._dx_work = np.empty_like(self._field)
        self._dy_work = np.empty_like(self._field)
        self._distance_work = np.empty_like(self._field)
        self._poles = self._rng.uniform(-0.65, 0.65, (self.MAX_POLES, 2)).astype(np.float32)
        self._strength = self._rng.uniform(0.6, 1.2, self.MAX_POLES).astype(np.float32)
        self._surface = pygame.Surface((W, H))
        self._scaled = pygame.Surface((max(1, config.WIDTH), max(1, config.HEIGHT)))
        self._hue = float(self._rng.random())
        self._beat_prev = 0.0
        self._invert_timer = 0

    def _render(self, surf, high):
        field = np.clip(self._field, -self.MAX_FIELD, self.MAX_FIELD)
        bands = np.abs(np.sin(field * self.MAX_CONTOURS * 0.45))
        intensity = np.clip(0.18 + bands * 0.75 + np.maximum(field, 0.0) * 0.04, 0.0, 1.0)
        hue = (self._hue + field * 0.035 + high * 0.04) % 1.0
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
            self._advance_gl_state(bass, mid, high, tick)
            self._gl_field.render(self.renderer, self._gl_uniforms(surf.get_size(), bass, high, tick))
            return
        if bass > 0.7 and self._beat_prev <= 0.7:
            self._invert_timer = 12
        self._beat_prev = bass
        self._invert_timer = max(0, self._invert_timer - 1)
        angle = tick * (0.006 + mid * 0.001)
        cos_a, sin_a = math.cos(angle), math.sin(angle)
        px = self._poles[:, 0] * cos_a - self._poles[:, 1] * sin_a
        py = self._poles[:, 0] * sin_a + self._poles[:, 1] * cos_a
        field = self._field
        field.fill(0.0)
        for index in range(self.MAX_POLES):
            np.subtract(self._x, px[index], out=self._dx_work)
            np.subtract(self._y, py[index], out=self._dy_work)
            np.square(self._dx_work, out=self._dx_work)
            np.square(self._dy_work, out=self._dy_work)
            np.add(self._dx_work, self._dy_work, out=self._distance_work)
            self._distance_work += 0.0125
            np.sqrt(self._distance_work, out=self._distance_work)
            np.divide(self._strength[index], self._distance_work, out=self._distance_work)
            sign = -1.0 if self._invert_timer and index == 0 else 1.0
            if sign < 0.0:
                field -= self._distance_work
            else:
                field += self._distance_work
        np.clip(field, -self.MAX_FIELD, self.MAX_FIELD, out=self._field)
        self._field_dx[:, 1:-1] = (self._field[:, 2:] - self._field[:, :-2]) * 0.5
        self._field_dy[1:-1, :] = (self._field[2:, :] - self._field[:-2, :]) * 0.5
        self._field_dx[:, 0] = self._field[:, 1] - self._field[:, 0]
        self._field_dx[:, -1] = self._field[:, -1] - self._field[:, -2]
        self._field_dy[0, :] = self._field[1, :] - self._field[0, :]
        self._field_dy[-1, :] = self._field[-1, :] - self._field[-2, :]
        self._hue = (self._hue + 0.001 + high * 0.0008) % 1.0
        self._render(surf, high)

    def _advance_gl_state(self, bass, mid, high, tick):
        if bass > 0.7 and self._beat_prev <= 0.7:
            self._invert_timer = 12
        self._beat_prev = bass
        self._invert_timer = max(0, self._invert_timer - 1)
        self._hue = (self._hue + 0.001 + high * 0.0008) % 1.0

    def _gl_uniforms(self, size, bass, high, tick):
        angle = tick * (0.006 + min(max(float(config.MID_ENERGY), 0.0), 4.0) * 0.001)
        c, s = math.cos(angle), math.sin(angle)
        result = {
            "u_resolution": tuple(map(float, size)), "u_time": tick * 0.035,
            "u_hue": self._hue, "u_bass": bass, "u_high": high,
            "u_sign": -1.0 if self._invert_timer else 1.0,
        }
        for index, (x, y) in enumerate(self._poles):
            result[f"u_p{index}"] = (float(x*c-y*s), float(x*s+y*c))
        return result

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
            self._advance_gl_state(bass, mid, high, tick)
            return self._gl_field.draw_frame(renderer, int(width), int(height), self._gl_uniforms((width, height), bass, high, tick))
        finally:
            config.WIDTH, config.HEIGHT = old_size

    def get_motion_field(self):
        return self._field_dx, self._field_dy

    def release(self):
        self._gl_field.release()
        self._surface = None
        self._scaled = None
        self._x = self._y = self._field = self._field_dx = self._field_dy = None
        self._dx_work = self._dy_work = self._distance_work = None
        self._poles = self._strength = None
