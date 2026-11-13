"""ChromaWeave — eightfold iridescent ribbons woven through breathing petals."""
import math

import numpy as np
import pygame

from .base import Effect
from .gl_field import FullscreenField


_FRAGMENT_SHADER = """
#version 330 core
out vec4 fragColor;
uniform vec2 u_resolution;
uniform float u_time, u_weave, u_hue, u_turn, u_bass, u_mid, u_high, u_pulse, u_accent;

vec3 weaveColour(float hue) {
    return 0.5 + 0.5*cos(6.2831853*(hue + vec3(0.0,0.3333,0.6667)));
}
void main() {
    vec2 p = vec2(2.0*gl_FragCoord.x-u_resolution.x,
                  2.0*gl_FragCoord.y-u_resolution.y)/u_resolution.y;
    float ca=cos(u_turn), sa=sin(u_turn);
    p=mat2(ca,sa,-sa,ca)*p;
    p/=1.0+u_bass*0.06+u_pulse*0.10;
    float radius=length(p);
    float angle=atan(p.y,p.x+0.000001);
    float wedge=0.7853981634;
    float folded=abs(mod(angle+wedge*0.5,wedge)-wedge*0.5);
    vec2 q=radius*vec2(cos(folded),sin(folded));
    float centre=smoothstep(0.07,0.25,radius);
    vec3 colour=vec3(0.006,0.004,0.025);
    for(int i=0;i<3;++i) {
        float layer=float(i);
        vec2 z=q*(1.0+layer*0.38);
        z+=vec2(0.15*sin(u_time*0.19+layer),0.12*cos(u_time*0.13+layer));
        z.x+=0.16*sin(z.y*5.0+u_time*0.23+layer);
        z.y+=0.12*sin(z.x*4.0-u_time*0.19);
        float weave=sin(z.x*6.0-u_weave+sin(z.y*8.0+u_time*0.31))
                    *cos(z.y*10.0+u_time*0.4);
        float thread=exp(-90.0*weave*weave);
        float halo=exp(-8.0*abs(weave));
        float over=0.5+0.5*sin(z.x*7.0+z.y*5.0-u_time*0.42);
        float distance=radius-(0.42+layer*0.46+0.12*cos(angle*8.0+u_time*0.25+layer));
        float petal=exp(-65.0*distance*distance);
        float hue=u_hue+layer*0.19+radius*0.15+folded*0.45+weave*0.08;
        float light=0.32*petal+(0.65+u_pulse*0.25*u_accent)*thread*(0.42+over*0.58)+halo*0.16;
        colour+=weaveColour(hue)*light*centre/(1.0+layer*0.6);
        float sparkle=pow(max(0.0,sin(z.x*12.0+u_time*0.5)*cos(z.y*13.0-u_time*0.2)),18.0);
        colour+=vec3(0.25,0.8,1.0)*sparkle*thread*u_high*0.16*u_accent*centre;
    }
    colour/=1.0+radius*radius*0.3;
    fragColor=vec4(1.0-exp(-colour*2.0),1.0);
}
"""


def _weave_rgb(x, y, uniforms):
    """Same analytic field as GLSL, evaluated on one bounded CPU row tile."""
    time, turn = uniforms['u_time'], uniforms['u_turn']
    ca, sa = math.cos(turn), math.sin(turn)
    zoom = 1.+uniforms['u_bass']*.06+uniforms['u_pulse']*.10
    px, py = (ca*x-sa*y)/zoom, (sa*x+ca*y)/zoom
    radius = np.hypot(px, py)
    angle = np.arctan2(py, px+.000001)
    wedge = math.pi/4
    folded = np.abs((angle+wedge*.5) % wedge-wedge*.5)
    qx, qy = radius*np.cos(folded), radius*np.sin(folded)
    centre = np.clip((radius-.07)/.18, 0., 1.)
    centre = centre*centre*(3.-2.*centre)
    colour = np.empty(radius.shape+(3,), dtype=np.float32)
    colour[:] = (.006, .004, .025)
    for layer in range(3):
        zx = qx*(1.+layer*.38)+.15*math.sin(time*.19+layer)
        zy = qy*(1.+layer*.38)+.12*math.cos(time*.13+layer)
        zx += .16*np.sin(zy*5.+time*.23+layer)
        zy += .12*np.sin(zx*4.-time*.19)
        weave = np.sin(zx*6.-uniforms['u_weave']+np.sin(zy*8.+time*.31))*np.cos(zy*10.+time*.4)
        thread = np.exp(-90.*weave*weave)
        halo = np.exp(-8.*np.abs(weave))
        over = .5+.5*np.sin(zx*7.+zy*5.-time*.42)
        distance = radius-(.42+layer*.46+.12*np.cos(angle*8.+time*.25+layer))
        petal = np.exp(-65.*distance*distance)
        hue = uniforms['u_hue']+layer*.19+radius*.15+folded*.45+weave*.08
        light = .32*petal+(.65+uniforms['u_pulse']*.25*uniforms['u_accent'])*thread*(.42+over*.58)+halo*.16
        light *= centre/(1.+layer*.6)
        for channel, offset in enumerate((0., .3333, .6667)):
            colour[..., channel] += (.5+.5*np.cos(math.tau*(hue+offset)))*light
        sparkle = np.maximum(0., np.sin(zx*12.+time*.5)*np.cos(zy*13.-time*.2))**18
        sparkle *= thread*uniforms['u_high']*.16*uniforms['u_accent']*centre
        for channel, gain in enumerate((.25, .8, 1.)):
            colour[..., channel] += sparkle*gain
    colour /= (1.+radius*radius*.3)[..., None]
    return np.clip((1.-np.exp(-colour*2.0))*255., 0., 255.).astype(np.uint8)


class ChromaWeave(Effect):
    IS_GL = True
    TIME_BASED = True
    CONTINUOUS_QUALITY = True
    RES_DIV = 1
    MAX_CPU_PIXELS = 640 * 360

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self._time = 0.
        # Weave phase, accumulated and wrapped. It used to be total time times
        # the live mid level, so every change in mid moved the pattern by an
        # amount that grew with uptime and it flickered after a few minutes.
        self._weave = 0.
        self._hue = .54
        self._turn = 0.
        self._gl_field = FullscreenField(_FRAGMENT_SHADER)
        self._surface = self._scaled = None
        self._x = self._y = None

    def _advance(self, beat):
        motion, light = self._audio_response(beat)
        dt = self._frame_step()/60.
        motion_scale, accent = self._profile_response()
        advance = dt*(1.+float(motion[1])*.18)
        self._time += advance
        self._weave = (self._weave + advance*(.75+float(motion[1])*.12)) % math.tau
        self._turn += dt*.09*motion_scale
        self._hue = (self._hue+dt*.018) % 1.
        return dict(u_time=self._time, u_weave=self._weave, u_hue=self._hue, u_turn=self._turn,
                    u_bass=float(motion[0]), u_mid=float(motion[1]),
                    u_high=float(light[2]), u_pulse=float(light[0]), u_accent=accent)

    def _draw(self, surf, waveform, fft, beat, tick):
        uniforms = self._advance(beat)
        uniforms['u_resolution'] = tuple(map(float, surf.get_size()))
        if self._gl_field.available(self.renderer):
            self._gl_field.render(self.renderer, uniforms)
            return
        width, height, _ = self._render_size()
        # CPU-only safety budget; the analytic GPU field stays full resolution.
        # Apply quality below this cap too, so the governor can recover FPS.
        budget_scale = min(1., math.sqrt(self.MAX_CPU_PIXELS /
                                        (self.frame.width*self.frame.height)))
        width, height = max(1, int(width*budget_scale)), max(1, int(height*budget_scale))
        if self._surface is None or self._surface.get_size() != (width, height):
            self._surface = pygame.Surface((width, height))
            self._x = (2.*(np.arange(width, dtype=np.float32)+.5)-width)/height
            self._y = (height-2.*(np.arange(height, dtype=np.float32)+.5))/height
        pixels = pygame.surfarray.pixels3d(self._surface)
        try:
            for first in range(0, height, 32):
                last = min(first+32, height)
                colours = _weave_rgb(self._x[None, :], self._y[first:last, None], uniforms)
                pixels[:, first:last] = colours.transpose(1, 0, 2)
        finally:
            del pixels
        if (width, height) == surf.get_size():
            surf.blit(self._surface, (0, 0))
        else:
            if self._scaled is None or self._scaled.get_size() != surf.get_size():
                self._scaled = pygame.Surface(surf.get_size())
            pygame.transform.smoothscale(self._surface, surf.get_size(), self._scaled)
            surf.blit(self._scaled, (0, 0))

    def _draw_frame(self, width, height, waveform, fft, beat, tick, renderer=None):
        renderer = renderer or self.renderer
        if not self._gl_field.available(renderer):
            return super()._draw_frame(width, height, waveform, fft, beat, tick, renderer)
        uniforms = self._advance(beat)
        uniforms['u_resolution'] = (float(width), float(height))
        return self._gl_field.draw_frame(renderer, width, height, uniforms)

    def release(self):
        super().release()
        self._surface = self._scaled = self._x = self._y = None
