"""Hyperbolic — a rotating sphere of independently rising and sinking neon facets."""
import math

import numpy as np
import pygame

import config
from .base import Effect
from .utils import hsl


class Hyperbolic(Effect):
    TIME_BASED = True
    MORPH_SCHEMA = {"_morph_warp": (0.0, 1.0)}
    TRAIL_ALPHA = 42
    MAX_TILES = 180

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self._rng = np.random.default_rng(config.RNG_SEED)
        self._size = (0, 0)
        self._tiles = []
        self._hue = float(self._rng.random())
        self._angle = 0.0
        self._morph_warp = 0.5
        self._time = 0.0
        self._tile_heights = None
        self._reset_geometry(max(1, config.WIDTH), max(1, config.HEIGHT))

    def _reset_geometry(self, W, H):
        self._size = (W, H)
        if not self._tiles:
            # Dual of a subdivided icosahedron: shared hexagon/pentagon boundaries.
            phi = (1 + math.sqrt(5)) / 2
            vertices = np.array([(-1,phi,0),(1,phi,0),(-1,-phi,0),(1,-phi,0),
                                 (0,-1,phi),(0,1,phi),(0,-1,-phi),(0,1,-phi),
                                 (phi,0,-1),(phi,0,1),(-phi,0,-1),(-phi,0,1)], dtype=float)
            vertices /= np.linalg.norm(vertices, axis=1)[:, None]
            faces = [(0,11,5),(0,5,1),(0,1,7),(0,7,10),(0,10,11),
                     (1,5,9),(5,11,4),(11,10,2),(10,7,6),(7,1,8),
                     (3,9,4),(3,4,2),(3,2,6),(3,6,8),(3,8,9),
                     (4,9,5),(2,4,11),(6,2,10),(8,6,7),(9,8,1)]
            vertices = list(vertices)
            for _ in range(2):
                cache, refined = {}, []
                def midpoint(a, b):
                    key = tuple(sorted((a, b)))
                    if key not in cache:
                        point = vertices[a] + vertices[b]
                        cache[key] = len(vertices)
                        vertices.append(point / np.linalg.norm(point))
                    return cache[key]
                for a, b, c in faces:
                    ab, bc, ca = midpoint(a,b), midpoint(b,c), midpoint(c,a)
                    refined.extend(((a,ab,ca),(b,bc,ab),(c,ca,bc),(ab,bc,ca)))
                faces = refined
            vertices = np.array(vertices)
            centers = np.array([vertices[list(face)].mean(axis=0) for face in faces])
            centers /= np.linalg.norm(centers, axis=1)[:, None]
            self._tiles = []
            for i, vertex in enumerate(vertices):
                adjacent = [j for j, face in enumerate(faces) if i in face]
                reference = np.array([0., 0., 1.]) if abs(vertex[2]) < .9 else np.array([0., 1., 0.])
                tangent = np.cross(vertex, reference); tangent /= np.linalg.norm(tangent)
                bitangent = np.cross(vertex, tangent)
                adjacent.sort(key=lambda j: math.atan2(centers[j] @ bitangent, centers[j] @ tangent))
                self._tiles.append(centers[adjacent])
            self._tile_centers = np.array([tile.mean(axis=0) for tile in self._tiles])
            self._tile_heights = np.zeros(len(self._tiles))
        # Cache a faint shaded sphere behind the rotating surface mesh.
        radius = max(1, int(min(W, H) * .36 * 3.2 / math.sqrt(3.2 ** 2 - 1)))
        axis = np.linspace(-1, 1, radius * 2 + 1, dtype=np.float32)
        x, y = np.meshgrid(axis, axis)
        mask = x*x + y*y <= 1
        z = np.sqrt(np.maximum(0, 1 - x*x - y*y))
        light = np.clip(-x * .35 - y * .45 + z * .8, 0, 1)
        rgba = np.zeros((*x.shape, 4), dtype=np.uint8)
        rgba[..., :3] = (np.array([6, 10, 24]) + light[..., None] * np.array([8, 13, 28])).astype(np.uint8)
        rgba[..., 3] = mask * 255
        self._body = pygame.image.frombuffer(rgba.tobytes(), (len(axis), len(axis)), "RGBA").copy()

    def _sphere_point(self, z):
        # Inverse stereographic map gives a real sphere, then rotate in 3-D.
        r2 = abs(z) ** 2
        x, y, depth = 2 * z.real / (1 + r2), 2 * z.imag / (1 + r2), (1 - r2) / (1 + r2)
        a = self._angle
        x, depth = x * math.cos(a) + depth * math.sin(a), -x * math.sin(a) + depth * math.cos(a)
        tilt = 0.35
        return x, y * math.cos(tilt) - depth * math.sin(tilt), y * math.sin(tilt) + depth * math.cos(tilt)

    def _project(self, z, W, H):
        x, y, depth = self._sphere_point(z)
        scale = min(W, H) * 0.40 * 3.2 / (3.2 - depth)
        return int(W * 0.5 + x * scale), int(H * 0.5 + y * scale)

    def _rotate_mesh(self, vertices):
        c, s = math.cos(self._angle), math.sin(self._angle)
        tilt = 0.35
        rotation_y = np.array([[c,0,s],[0,1,0],[-s,0,c]])
        rotation_x = np.array([[1,0,0],[0,math.cos(tilt),-math.sin(tilt)],
                               [0,math.sin(tilt),math.cos(tilt)]])
        return vertices @ (rotation_x @ rotation_y).T

    @staticmethod
    def _clip_front(vertices):
        # The perspective horizon of the unit sphere is z = 1 / camera_distance.
        horizon = 1 / 3.2
        result = []
        for a, b in zip(vertices, np.roll(vertices, -1, axis=0)):
            inside_a, inside_b = a[2] >= horizon, b[2] >= horizon
            if inside_a:
                result.append(a)
            if inside_a != inside_b:
                t = (horizon - a[2]) / (b[2] - a[2])
                result.append(a + (b - a) * t)
        return np.array(result)

    def draw(self, surf, waveform, fft, beat, tick):
        W, H = surf.get_size()
        if (W, H) != self._size:
            self._reset_geometry(W, H)
        step = self._frame_step()
        motion, light = self._audio_response(beat)
        motion_scale, accent = self._profile_response()
        self._time += (.013 + motion[1]*.002) * step
        self._angle += (.008 + motion[1]*.002) * step * motion_scale
        self._hue = (self._hue + (.0025 + motion[1]*.0008)*step) % 1
        indices = np.arange(len(self._tiles))
        longitude = np.arctan2(self._tile_centers[:,1],self._tile_centers[:,0])
        phase = self._time + longitude*3 + self._tile_centers[:,2]*4
        target = .075*np.sin(phase) + .035*np.sin(self._time*1.4+indices*.52)
        target += motion[0]*.035*(.5+.5*np.sin(phase+self._time*.3))
        self._tile_heights = self._smooth(self._tile_heights,np.clip(target,-.10,.18),.08)
        surf.fill((2,1,8))
        surf.blit(self._body,((W-self._body.get_width())//2,(H-self._body.get_height())//2))
        self._visible_tiles=[]
        rotated=[self._rotate_mesh(tile) for tile in self._tiles]
        self._projected_caps=[]
        def project(vertices):
            scale=min(W,H)*.36*3.2/(3.2-vertices[:,2])
            return list(zip((W/2+vertices[:,0]*scale).tolist(),(H/2+vertices[:,1]*scale).tolist()))
        for index in sorted(range(len(rotated)),key=lambda i: rotated[i][:,2].mean()):
            base=rotated[index]
            visible=self._clip_front(base)
            if len(visible)<3:
                continue
            self._visible_tiles.append(visible)
            height=float(self._tile_heights[index])
            tile=self._tiles[index]
            # Tilt around one tile edge, so caps can stand up as well as lift.
            axis=tile[1]-tile[0]; axis/=np.linalg.norm(axis)
            pivot=(tile[0]+tile[1])*.5
            angle=.30*max(0.,math.sin(float(phase[index])))
            relative=tile-pivot
            tilted=(relative*math.cos(angle)+np.cross(axis,relative)*math.sin(angle)
                    + np.outer(relative@axis,axis)*(1-math.cos(angle)))+pivot
            cap=self._rotate_mesh(tilted*(1+height))
            depth=float(visible[:,2].mean())
            hue=(self._hue+index*.033+.20*math.sin(float(phase[index]))+height*.8)%1
            # Exposed walls and dark recesses provide depth cues for each segment.
            for i in range(len(base)):
                j=(i+1)%len(base)
                wall=self._clip_front(np.array([base[i],base[j],cap[j],cap[i]]))
                if len(wall)>=3:
                    shade=.04+max(0.,height)*.5+(i%3)*.018
                    pygame.draw.polygon(surf,hsl((hue+.10)%1,s=.9,l=shade),project(wall))
            clipped_cap=self._clip_front(cap)
            if len(clipped_cap)<3:
                continue
            points=project(clipped_cap)
            self._projected_caps.append(np.array(points))
            lightness=min(.48,.13+depth*.12+max(0.,height)*.65+light[0]*.05*accent)
            pygame.draw.polygon(surf,hsl(hue,s=.95,l=lightness),points)
            edge_brightness=min(.90,.42+depth*.22+light[2]*.10+max(0.,height))
            pygame.draw.aalines(surf,hsl((hue+.08)%1,l=edge_brightness),True,points)

    def release(self):
        self._tiles = []
        self._body = None
