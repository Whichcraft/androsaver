#!/usr/bin/env python3
import argparse
import os
import time
import numpy as np
import pygame
import config
from effects import MODES
from gl_renderer import GLRenderer, HAS_MODERNGL

def run_benchmark(duration_s=2.0, *, enable_gl=False, headless=True,
                  resolution=(1920, 1080), effect_names=None):
    W, h = resolution
    print(f"Running benchmarks at {W}x{h} ({duration_s}s per mode)...")
    
    # Mock audio data
    waveform = np.random.uniform(-1, 1, 1024).astype(np.float32)
    fft = np.random.uniform(0, 1, 512).astype(np.float32)
    beat = 1.0
    tick = 0
    
    if headless:
        os.environ.setdefault('SDL_VIDEODRIVER', 'dummy')
    pygame.init()
    _old_w, _old_h = config.WIDTH, config.HEIGHT
    _old_initialized = config._INITIALIZED
    config.WIDTH, config.HEIGHT = W, h
    config._INITIALIZED = True
    renderer = None
    gl_available = bool(enable_gl and HAS_MODERNGL)
    if enable_gl and not HAS_MODERNGL:
        print("  ModernGL unavailable; skipping GL measurements.")
    
    try:
        # CPU Target
        cpu_surf = pygame.Surface((W, h))

        gl_target = None
        if gl_available:
            try:
                pygame.display.set_mode((W, h), pygame.OPENGL | pygame.DOUBLEBUF)
                renderer = GLRenderer(W, h)
                gl_target = pygame.Surface((W, h), pygame.SRCALPHA)
            except Exception as e:
                print(f"  ⚠️ ModernGL init failed for benchmark: {e}")
                gl_available = False

        results = []

        selected_modes = MODES
        if effect_names:
            requested = set(effect_names)
            available = {name for name, _ in MODES}
            unknown = requested - available
            if unknown:
                raise ValueError(f"Unknown effect(s): {', '.join(sorted(unknown))}")
            selected_modes = [(name, cls) for name, cls in MODES if name in requested]

        for name, VisCls in selected_modes:
            print(f"  Benchmarking {name:12}...", end="", flush=True)

            # CPU Test
            vis_cpu = VisCls()
            try:
                tick = 0
                start = time.perf_counter()
                frames = 0
                end_time = start + duration_s
                while time.perf_counter() < end_time:
                    vis_cpu.draw(cpu_surf, waveform, fft, beat, tick)
                    frames += 1
                    tick += 1
                cpu_fps = frames / (time.perf_counter() - start)
            finally:
                if hasattr(vis_cpu, "release"):
                    vis_cpu.release()

            # GL Test
            gl_fps = None
            vis_gl = None
            if renderer:
                vis_gl = VisCls(renderer=renderer)
                tick = 0
                start = time.perf_counter()
                frames = 0
                end_time = start + duration_s
                while time.perf_counter() < end_time:
                    vis_gl.draw(gl_target, waveform, fft, beat, tick)
                    if not getattr(vis_gl, "IS_GL", False):
                        renderer.blit(gl_target)
                        gl_target.fill((0, 0, 0, 0))
                    pygame.display.flip()
                    finish = getattr(getattr(renderer, "ctx", None), "finish", None)
                    if callable(finish):
                        finish()
                    frames += 1
                    tick += 1
                gl_fps = frames / (time.perf_counter() - start)
            if vis_gl is not None and hasattr(vis_gl, "release"):
                vis_gl.release()

            results.append((name, cpu_fps, gl_fps))
            print(f" Done. (CPU: {cpu_fps:6.1f} fps" + (f" | GL: {gl_fps:6.1f} fps" if gl_fps else "") + ")")

        print("\n" + "="*60)
        print(f"{'Effect':15} | {'CPU FPS':10} | {'GL FPS':10} | {'Speedup':8}")
        print("-" * 60)
        for name, cpu, gl in results:
            speedup = f"{gl/cpu:7.2f}x" if gl else "N/A"
            gl_str = f"{gl:10.1f}" if gl else "N/A"
            print(f"{name:15} | {cpu:10.1f} | {gl_str:10} | {speedup}")
        print("="*60)
    finally:
        config.WIDTH, config.HEIGHT = _old_w, _old_h
        config._INITIALIZED = _old_initialized
        if renderer is not None:
            renderer.release()
        pygame.quit()


def parse_args(argv=None):
    parser = argparse.ArgumentParser(description="Benchmark CPU effects and optional ModernGL rendering")
    parser.add_argument("--duration", type=float, default=2.0,
                        help="seconds per effect (default: 2.0)")
    parser.add_argument("--resolution", choices=("1080p", "4k"), default="1080p",
                        help="CPU benchmark output resolution (default: 1080p)")
    parser.add_argument("--effects", nargs="+", metavar="NAME",
                        help="benchmark selected effects only")
    group = parser.add_mutually_exclusive_group()
    group.add_argument("--cpu-only", action="store_true",
                       help="run headless CPU measurements only (default)")
    group.add_argument("--gl", action="store_true",
                       help="run CPU measurements and attempt a real ModernGL window")
    args = parser.parse_args(argv)
    if args.duration <= 0:
        parser.error("--duration must be greater than zero")
    available = {name for name, _ in MODES}
    unknown = set(args.effects or ()) - available
    if unknown:
        parser.error(f"unknown effect(s): {', '.join(sorted(unknown))}")
    return args


if __name__ == "__main__":
    args = parse_args()
    run_benchmark(
        args.duration,
        enable_gl=args.gl,
        headless=not args.gl,
        resolution=(3840, 2160) if args.resolution == "4k" else (1920, 1080),
        effect_names=args.effects,
    )
