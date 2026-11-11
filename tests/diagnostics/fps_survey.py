#!/usr/bin/env python3
"""Bounded, uncapped CPU/EGL effect survey; never substitutes CPU for EGL."""
import argparse
from contextlib import ExitStack
from dataclasses import replace
import hashlib
import inspect
import json
import os
from pathlib import Path
import platform
import sys
import time

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))
import numpy as np
import pygame
from core.context import EffectOptions, FrameContext
from effects import MODES


def parse_args():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--backend', choices=('cpu', 'egl', 'both'), default='both')
    parser.add_argument('--width', type=int, default=1920)
    parser.add_argument('--height', type=int, default=1080)
    parser.add_argument('--warmup', type=int, default=32)
    parser.add_argument('--frames', type=int, default=48, help='total frames including warmup')
    parser.add_argument('--effects', nargs='+', choices=[name for name, _ in MODES])
    parser.add_argument('--json-output', type=Path)
    args = parser.parse_args()
    if min(args.width, args.height) <= 0 or args.warmup < 0 or args.frames <= args.warmup:
        parser.error('positive dimensions and frames > warmup >= 0 are required')
    return args


def survey(args):
    os.environ.setdefault('SDL_VIDEODRIVER', 'dummy')
    os.environ.setdefault('SDL_AUDIODRIVER', 'dummy')
    pygame.init()
    ctx = fbo = renderer = None
    result = {'environment': {'python': platform.python_version(), 'numpy': np.__version__,
                             'pygame': pygame.version.ver, 'machine': platform.machine()},
              'fixture': {'size': [args.width, args.height], 'seed': 7, 'profile': 'laptop',
                          'dt': 1/60, 'warmup': args.warmup, 'frames': args.frames,
                          'mid': .4, 'treble': .6, 'beat': '.9 for tick%12<3, else .08',
                          'audio': '1024-sample eight-pi sine; 512-bin linear FFT 0..1'},
              'results': []}
    with ExitStack() as cleanup:
        cleanup.callback(pygame.quit)
        if args.backend != 'cpu':
            import moderngl  # Missing module/context is an error, never a CPU fallback.
            from gl_renderer import GLRenderer
            ctx = moderngl.create_standalone_context(backend='egl', require=330)
            cleanup.callback(ctx.release)
            fbo = ctx.simple_framebuffer((args.width, args.height), components=4)
            cleanup.callback(fbo.release)
            class OffscreenContext:
                screen = fbo
                def __getattr__(self, name):
                    return getattr(ctx, name)
            renderer = GLRenderer(args.width, args.height, ctx=OffscreenContext())
            cleanup.callback(renderer.release)
            result['environment']['gpu'] = ctx.info['GL_RENDERER']
        frame = FrameContext(args.width, args.height, profile='laptop', dt=1/60,
                             mid=.4, treble=.6, bpm=120., is_silent=False,
                             waveform=np.sin(np.linspace(0, 8*np.pi, 1024)).astype(np.float32),
                             fft=np.linspace(0, 1, 512, dtype=np.float32))
        backends = ('cpu', 'egl') if args.backend == 'both' else (args.backend,)
        for backend in backends:
            for name, cls in MODES:
                if args.effects and name not in args.effects:
                    continue
                effect = cls(options=EffectOptions(args.width, args.height, seed=7), frame=frame,
                             renderer=renderer if backend == 'egl' else None)
                target = pygame.Surface((args.width, args.height), pygame.SRCALPHA)
                timings = []
                try:
                    for tick in range(args.frames):
                        current = replace(frame, tick=tick, beat=.9 if tick % 12 < 3 else .08)
                        target.fill((0, 0, 0, 0 if backend == 'egl' and effect.IS_GL else 255))
                        if backend == 'egl':
                            fbo.use(); ctx.disable(moderngl.BLEND); ctx.clear(0, 0, 0, 1); ctx.finish()
                        start = time.perf_counter_ns()
                        effect.render(target, current)
                        if backend == 'egl':
                            if not effect.IS_GL:
                                renderer.blit(target)
                            ctx.finish()
                        elapsed = (time.perf_counter_ns()-start)/1e6
                        if tick >= args.warmup:
                            timings.append(elapsed)
                    median = float(np.median(timings))
                    row = {'name': name, 'backend': backend, 'median_ms': median,
                           'p95_ms': float(np.percentile(timings, 95)), 'fps': 1000/median,
                           'source_sha256': hashlib.sha256(Path(inspect.getfile(cls)).read_bytes()).hexdigest()}
                    result['results'].append(row)
                    print(json.dumps(row), flush=True)
                finally:
                    effect.release()
        return result


if __name__ == '__main__':
    args = parse_args()
    result = survey(args)
    if args.json_output:
        args.json_output.write_text(json.dumps(result, indent=2)+'\n')
    for backend in sorted({row['backend'] for row in result['results']}):
        slowest = sorted((row for row in result['results'] if row['backend'] == backend),
                         key=lambda row: row['fps'])[:4]
        print(backend, 'slowest:', ', '.join(f"{row['name']} {row['fps']:.1f} FPS" for row in slowest))
