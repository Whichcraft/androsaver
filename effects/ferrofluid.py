"""Ferrofluid — smooth magnetic droplets with metallic surface lighting."""
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
uniform float u_time, u_hue, u_bass, u_high;
void main() {
    vec2 p = (gl_FragCoord.xy/u_resolution-0.5)*2.0;
    p.y = -p.y;
    p.x *= u_resolution.x/u_resolution.y;
    vec2 poles[5] = vec2[5](u_p0,u_p1,u_p2,u_p3,u_p4);
    float height=0.0;
    vec2 gradient=vec2(0.0);
    for (int i=0;i<5;++i) {
        vec2 d=p-poles[i];
        float width=0.12+u_bass*0.018;
        float peak=exp(-dot(d,d)/width);
        height+=peak;
        gradient+=-2.0*d/width*peak;
    }
    // Connected liquid envelope supporting a triangular lattice of sharp peaks.
    float envelope=1.0-exp(-height*1.4);
    vec2 envelopeGradient=1.4*exp(-height*1.4)*gradient;
    float ca=cos(u_time*0.12), sa=sin(u_time*0.12);
    vec2 q=mat2(ca,sa,-sa,ca)*p;
    float k=20.0;
    float a=k*q.x, b=k*(0.5*q.x+0.8660254*q.y), c=k*(0.5*q.x-0.8660254*q.y);
    float lattice=(cos(a)+cos(b)+cos(c))/3.0;
    vec2 dg=vec2(-k*(sin(a)+0.5*sin(b)+0.5*sin(c))/3.0,
                 -k*0.8660254*(sin(b)-sin(c))/3.0);
    dg=mat2(ca,-sa,sa,ca)*dg;
    float peak=exp((lattice-1.0)*8.0);
    float amplitude=0.18+u_bass*0.04;
    gradient=gradient*0.15+amplitude*(envelopeGradient*peak+envelope*peak*8.0*dg);
    float mask=smoothstep(0.06,0.4,height);
    height=height*0.15+amplitude*envelope*peak;
    vec3 normal=normalize(vec3(-gradient,1.0));
    vec3 light=normalize(vec3(-0.5,-0.7,1.0));
    float diffuse=max(dot(normal,light),0.0);
    float spec=pow(max(dot(reflect(-light,normal),vec3(0,0,1)),0.0),40.0);
    float rim=pow(1.0-normal.z,3.0);
    vec3 tint=0.5+0.5*cos(6.2831853*(u_hue+height*0.08+vec3(0,0.3333,0.6667)));
    vec3 color=vec3(0.008,0.01,0.018)+mask*(tint*(0.035+diffuse*0.18+rim*0.4)+vec3(spec)*(0.8+u_high*0.08));
    fragColor=vec4(clamp(color,0.0,1.0),1.0);
}
"""


class Ferrofluid(Effect):
    TIME_BASED = True
    IS_GL = True
    TRAIL_ALPHA = 40
    # Preserve full-size pixels instead of enlarging a coarse internal field.
    CONTINUOUS_QUALITY = True
    RES_DIV = 1
    MAX_POLES = 5
    MAX_FIELD = 8.0

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self._rng = np.random.default_rng(config.RNG_SEED)
        self._W = self._H = 1
        self._x = self._y = None
        self._field = None
        self._field_dx = self._field_dy = None
        self._poles = None
        self._surface = pygame.Surface((1, 1))
        self._scaled = pygame.Surface((1, 1))
        self._hue = float(self._rng.random())
        self._gl_field = FullscreenField(_FRAGMENT_SHADER)
        self._audio = np.zeros(3)
        self._time = 0.0
        self._reset_state()

    def _reset_state(self):
        W, H, _ = self._render_size()
        self._W, self._H = W, H
        xx = np.linspace(-W / H, W / H, W, dtype=np.float32)
        yy = np.linspace(-1.0, 1.0, H, dtype=np.float32)
        self._x, self._y = np.meshgrid(xx, yy)
        self._field = np.zeros((H, W), dtype=np.float32)
        self._field_dx = np.zeros_like(self._field)
        self._field_dy = np.zeros_like(self._field)
        if self._poles is None:
            self._poles = self._rng.uniform(-0.65, 0.65, (self.MAX_POLES, 2)).astype(np.float32)
        self._surface = pygame.Surface((W, H))
        self._scaled = pygame.Surface((max(1, config.WIDTH), max(1, config.HEIGHT)))

    def _render(self, surf, high):
        nx, ny = -self._field_dx, -self._field_dy
        nz = 1.0 / np.sqrt(nx * nx + ny * ny + 1.0)
        nx, ny = nx * nz, ny * nz
        light = np.array([-0.5, -0.7, 1.0], dtype=np.float32)
        light /= np.linalg.norm(light)
        diffuse = np.maximum(nx * light[0] + ny * light[1] + nz * light[2], 0.0)
        spec = np.maximum(2 * diffuse * nz - light[2], 0.0) ** 40
        rim = (1 - nz) ** 3
        hue = self._hue + self._field * 0.08
        tint = np.stack([0.5 + 0.5 * np.cos(math.tau * (hue + offset))
                         for offset in (0, 0.3333, 0.6667)], axis=-1)
        colors = (np.array([0.008, 0.01, 0.018]) + self._mask[..., None] *
                  (tint * (0.035 + diffuse * 0.18 + rim * 0.4)[..., None] +
                   spec[..., None] * (0.8 + high * 0.08)))
        colors = (np.clip(colors, 0, 1) * 255).astype(np.uint8)
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
        bass, mid, high = self._advance_state(beat)
        if self._gl_field.available(self.renderer):
            self._gl_field.render(self.renderer, self._gl_uniforms(surf.get_size(), bass, high, tick))
            return
        angle = self._time * 0.35
        cos_a, sin_a = math.cos(angle), math.sin(angle)
        px = self._poles[:, 0] * cos_a - self._poles[:, 1] * sin_a
        py = self._poles[:, 0] * sin_a + self._poles[:, 1] * cos_a
        self._field.fill(0)
        self._field_dx.fill(0)
        self._field_dy.fill(0)
        width = 0.12 + bass * 0.018
        for x, y in zip(px, py):
            dx, dy = self._x - x, self._y - y
            peak = np.exp(-(dx * dx + dy * dy) / width)
            self._field += peak
            self._field_dx += -2 * dx / width * peak
            self._field_dy += -2 * dy / width * peak
        pool = self._field.copy()
        exp_pool = np.exp(-pool * 1.4)
        envelope = 1.0 - exp_pool
        ex = 1.4 * exp_pool * self._field_dx
        ey = 1.4 * exp_pool * self._field_dy
        ca, sa = math.cos(self._time * .12), math.sin(self._time * .12)
        x, y = ca * self._x - sa * self._y, sa * self._x + ca * self._y
        k = 20.0
        a, b, c = k*x, k*(.5*x + .8660254*y), k*(.5*x - .8660254*y)
        lattice = (np.cos(a) + np.cos(b) + np.cos(c)) / 3
        gx = -k * (np.sin(a) + .5*np.sin(b) + .5*np.sin(c)) / 3
        gy = -k * .8660254 * (np.sin(b) - np.sin(c)) / 3
        gx, gy = ca*gx + sa*gy, -sa*gx + ca*gy
        peak = np.exp((lattice - 1) * 8)
        amplitude = .18 + bass * .04
        self._field_dx[:] = self._field_dx*.15 + amplitude*(ex*peak + envelope*peak*8*gx)
        self._field_dy[:] = self._field_dy*.15 + amplitude*(ey*peak + envelope*peak*8*gy)
        self._field[:] = pool*.15 + amplitude*envelope*peak
        mask = np.clip((pool - .06) / .34, 0, 1)
        self._mask = mask*mask*(3 - 2*mask)
        self._render(surf, high)

    def _advance_state(self, beat):
        self._audio, light = self._audio_response(beat)
        bass, mid, high = self._audio
        step = self._frame_step()
        self._time += (0.008 + mid * 0.002) * step * self._profile_response()[0]
        self._hue = (self._hue + (0.001 + high * 0.0008) * step) % 1.0
        high = float(light[2]) * self._profile_response()[1]
        return bass, mid, high

    def _gl_uniforms(self, size, bass, high, tick):
        angle = self._time * 0.35
        c, s = math.cos(angle), math.sin(angle)
        result = {
            "u_resolution": tuple(map(float, size)), "u_time": self._time,
            "u_hue": self._hue, "u_bass": bass, "u_high": high,
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
            bass, mid, high = self._advance_state(beat)
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
        self._poles = self._mask = None
