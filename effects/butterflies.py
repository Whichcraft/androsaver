"""Bounded, deterministic butterflies that emerge, pair, dance, and dissolve."""
import math
import numpy as np
import pygame
import config
from .base import Effect
from .utils import hsl

def _delta(a, b): return (b - a + math.pi) % math.tau - math.pi

def _wing(x, y, heading, side, upper, flap, scale):
    ca, sa = math.cos(heading), math.sin(heading)
    ax, ay = x + ca * 5 * scale * upper, y + sa * 5 * scale * upper
    width = scale * (22 if upper > 0 else 13)
    ang = heading + side * (.22 + flap * .68) * math.pi / 2 + upper * .1
    def pt(a, r): return (int(ax + math.cos(a) * width * r), int(ay + math.sin(a) * width * r))
    return [pt(ang, 0), pt(ang-side*.88, .58), pt(ang, 1), pt(ang+side*1.01, .52)]

class _Butterfly:
    def __init__(self, x, y, hue, rng, scale=4.8, state='cocoon'):
        self.x, self.y = float(x), float(y); self.vx, self.vy = rng.uniform(-.4,.4,2)
        self.heading = float(rng.uniform(0, math.tau)); self.wing_phase = float(rng.uniform(0, math.tau))
        self.hue=hue%1; self.scale=scale; self.state=state; self.age=0; self.partner=None; self._rng=rng
        self._wander=self.heading; self._wander_cd=int(rng.integers(30,120)); self._seed=float(rng.random()*math.tau)
    def off_screen(self,w,h):
        m=70*self.scale; return self.x < -m or self.x > w+m or self.y < -m or self.y > h+m
    def update(self,bass,mids,treble,beat,w,h,target=None):
        self.age += 1
        if self.state=='cocoon': self.wing_phase += .035; self.x += self.vx*.35; self.y += self.vy*.35; return
        self.wing_phase += .10+bass*.20+beat*.06; self._wander_cd -= 1
        if target is None:
            if self._wander_cd<=0: self._wander += float(self._rng.uniform(-.8,.8)); self._wander_cd=int(self._rng.integers(35,130))
            desired=self._wander+math.sin(self.age*.013+self._seed)*(.15+treble*.4)
        else: desired=math.atan2(target[1]-self.y,target[0]-self.x)
        if self.x<50: desired=0
        elif self.x>w-50: desired=math.pi
        if self.y<50: desired=math.pi/2
        elif self.y>h-50: desired=-math.pi/2
        self.heading += max(-.18,min(.18,_delta(self.heading,desired)*(.12 if target is None else .22)))
        speed=(1.1+bass*.9+mids*.35+beat*.5)*self.scale/4.8
        self.vx=self.vx*.88+math.cos(self.heading)*speed*.12; self.vy=self.vy*.88+math.sin(self.heading)*speed*.12
        self.x+=self.vx; self.y+=self.vy
    def draw(self,surf,treble=0):
        flap=math.sin(self.wing_phase)*.5+.5; body=hsl(self.hue,l=.28); c1=hsl(self.hue,l=.58+treble*.12); c2=hsl((self.hue+.1)%1,l=.45)
        for upper,col in ((-1,c2),(1,c1)):
            for side in (-1,1):
                pts=_wing(self.x,self.y,self.heading,side,upper,flap,self.scale); pygame.draw.polygon(surf,col,pts)
                if self.scale>3: pygame.draw.polygon(surf,(18,12,24),pts,1)
        ca,sa=math.cos(self.heading),math.sin(self.heading); n=int(8*self.scale)
        pygame.draw.line(surf,body,(int(self.x-ca*n),int(self.y-sa*n)),(int(self.x+ca*n),int(self.y+sa*n)),max(1,int(self.scale*1.5)))

class _Pair:
    def __init__(self,a,b,rng):
        self.a,self.b,self._rng=a,b,rng; self.age=0; self.orbit=float(rng.uniform(0,math.tau)); self.radius=float(rng.uniform(35,80)); self.break_at=int(rng.integers(360,900)); a.partner=b; b.partner=a; a.state=b.state='paired'
    def update(self,bass,mids,treble,beat,w,h):
        self.age+=1; self.orbit += .012+beat*.025+mids*.008; self.radius=max(28,self.radius-.015-beat*.03)
        if self.age>self.break_at and self._rng.random()<.012+treble*.01: self.a.partner=self.b.partner=None; self.a.state=self.b.state='free'; return False
        ax,ay,bx,by=self.a.x,self.a.y,self.b.x,self.b.y; c,s=math.cos(self.orbit),math.sin(self.orbit)
        self.a.update(bass,mids,treble,beat,w,h,(bx+c*self.radius,by+s*self.radius)); self.b.update(bass,mids,treble,beat,w,h,(ax-c*self.radius,ay-s*self.radius))
        phase=(self.a.wing_phase+self.b.wing_phase)*.5; self.a.wing_phase+=(phase-self.a.wing_phase)*.12; self.b.wing_phase+=(phase-self.b.wing_phase)*.12; return True

class Butterflies(Effect):
    TRAIL_ALPHA=0; RES_DIV=2; MAX_POPULATION=12; MAX_PAIRS=6
    def __init__(self,**kwargs):
        super().__init__(**kwargs); self._rng=np.random.default_rng(config.RNG_SEED); self._tick=0; self._hue=float(self._rng.random()); self._butterflies=[]; self._pairs=[]; self._trail=None; self._scaled=None
    @staticmethod
    def _spawn(w,h,rng):
        e=int(rng.integers(4)); m=min(35,max(4,min(w,h)//4)); return ((-m,float(rng.uniform(0,h))) if e==0 else (w+m,float(rng.uniform(0,h))) if e==1 else (float(rng.uniform(0,w)),-m) if e==2 else (float(rng.uniform(0,w)),h+m))
    def _surfaces(self,w,h,dw,dh):
        if self._trail is None or self._trail.get_size()!=(w,h): self._trail=pygame.Surface((w,h)); self._trail.fill((0,0,0))
        if self._scaled is None or self._scaled.get_size()!=(dw,dh): self._scaled=pygame.Surface((dw,dh))
    def draw(self,surf,waveform,fft,beat,tick):
        dw,dh=surf.get_size(); w,h=self._render_size()[:2]; self._surfaces(w,h,dw,dh); self._tick+=1; self._hue=(self._hue+.0012+getattr(config,'TREBLE_ENERGY',0)*.0005)%1
        bass=float(np.mean(fft[:min(6,len(fft))])) if len(fft) else 0.; mids=float(getattr(config,'MID_ENERGY',0)); treble=float(getattr(config,'TREBLE_ENERGY',0)); self._trail.fill((239,239,239),special_flags=pygame.BLEND_RGB_MULT)
        if len(self._butterflies)<self.MAX_POPULATION and (self._tick<20 or self._tick%24==0):
            x,y=self._spawn(w,h,self._rng); self._butterflies.append(_Butterfly(x,y,self._hue+self._rng.random()*.25,self._rng,state='cocoon'))
        for b in self._butterflies:
            if b.state=='cocoon' and b.age>18: b.state='free'
        free=[b for b in self._butterflies if b.state=='free' and b.partner is None]
        for a in free:
            if len(self._pairs)>=self.MAX_PAIRS: break
            c=[b for b in free if b is not a and b.partner is None and math.hypot(a.x-b.x,a.y-b.y)<180]
            if c: self._pairs.append(_Pair(a,min(c,key=lambda q:math.hypot(a.x-q.x,a.y-q.y)),self._rng))
        for p in self._pairs[:]:
            if not p.update(bass,mids,treble,beat,w,h): self._pairs.remove(p)
        alive=[]
        for b in self._butterflies:
            if b.partner is None: b.update(bass,mids,treble,beat,w,h)
            b.hue=(self._hue+(0.5 if b.partner else 0))%1
            if b.state=='cocoon': pygame.draw.circle(self._trail,hsl(b.hue,l=.55),(int(b.x),int(b.y)),max(1,int(2+b.age*.08)))
            elif not b.off_screen(w,h): b.draw(self._trail,treble)
            if not b.off_screen(w,h) or b.partner is not None: alive.append(b)
        self._butterflies=alive[:self.MAX_POPULATION]
        if (w,h)!=(dw,dh): pygame.transform.scale(self._trail,(dw,dh),self._scaled); surf.blit(self._scaled,(0,0),special_flags=pygame.BLEND_RGB_MAX)
        else: surf.blit(self._trail,(0,0),special_flags=pygame.BLEND_RGB_MAX)
    def release(self): self._trail=None; self._scaled=None; self._butterflies.clear(); self._pairs.clear()
