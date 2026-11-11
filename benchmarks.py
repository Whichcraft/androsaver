#!/usr/bin/env python3
from dataclasses import replace
import argparse
import math
import os
import platform
import time
import numpy as np
import pygame
from core.context import AudioConfig, EffectOptions, FrameContext, parse_seed
from effects import MODES
from gl_renderer import GLRenderer, HAS_MODERNGL

def run_benchmark(duration_s=2.0, *, enable_gl=False, headless=True,
                  resolution=(1920, 1080), effect_names=None):
    if not math.isfinite(duration_s) or duration_s <= 0:
        raise ValueError("duration_s must be finite and greater than zero")
    W, h = resolution
    print(f"Running benchmarks at {W}x{h} ({duration_s}s per mode)...")
    
    # Mock audio data
    rng = np.random.default_rng(parse_seed(os.environ.get("PSYSUALS_SEED")))
    waveform = rng.uniform(-1, 1, 1024).astype(np.float32)
    fft = rng.uniform(0, 1, 512).astype(np.float32)
    beat = 1.0
    tick = 0
    
    if headless:
        os.environ.setdefault('SDL_VIDEODRIVER', 'dummy')
    pygame.init()
    options = EffectOptions(W, h, seed=parse_seed(os.environ.get("PSYSUALS_SEED")))
    frame = FrameContext(W, h, waveform=waveform, fft=fft, beat=beat)
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
            vis_cpu = VisCls(options=options)
            try:
                tick = 0
                start = time.perf_counter()
                frames = 0
                end_time = start + duration_s
                while time.perf_counter() < end_time:
                    vis_cpu.render(cpu_surf, replace(frame, tick=tick))
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
                vis_gl = VisCls(renderer=renderer, options=options)
                try:
                    tick = 0
                    start = time.perf_counter()
                    frames = 0
                    end_time = start + duration_s
                    while time.perf_counter() < end_time:
                        vis_gl.render(gl_target, replace(frame, tick=tick))
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
                finally:
                    if hasattr(vis_gl, "release"):
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
        try:
            if renderer is not None:
                renderer.release()
        finally:
            pygame.quit()


def measure_contexts(samples=1000, block_sizes=(256, 1024, 8192)):
    """Measure host-boundary validation/copies, without SDL or effect rendering.

    Per-call timings include Python construction and array ownership/validation.
    bytes_copied counts owned array payload, not allocator/validation traffic.
    """
    if isinstance(samples, bool) or not isinstance(samples, int) or samples <= 0:
        raise ValueError("samples must be a positive integer")
    block_sizes = tuple(block_sizes)
    if not block_sizes:
        raise ValueError("block_sizes must not be empty")
    for size in block_sizes:
        AudioConfig(block_size=size)
    rows = []
    for size in block_sizes:
        waveform = np.zeros(size, dtype=np.float32)
        fft = np.ones(size // 2, dtype=np.float32)
        frame = FrameContext(1920, 1080, waveform=waveform, fft=fft)
        operations = {
            "construct": lambda: FrameContext(1920, 1080, waveform=waveform, fft=fft),
            "metadata_replace": lambda: replace(frame, tick=1),
        }
        for name, operation in operations.items():
            for _ in range(100):
                operation()
            timings = np.empty(samples, dtype=np.float64)
            for index in range(samples):
                start = time.perf_counter_ns()
                result = operation()
                timings[index] = (time.perf_counter_ns() - start) / 1000.
            rows.append({"block_size": size, "operation": name, "samples": samples,
                         "median_us": float(np.median(timings)),
                         "p95_us": float(np.percentile(timings, 95)),
                         "bytes_copied": result.waveform.nbytes + result.fft.nbytes})
    return rows


def print_context_measurements(samples=1000, block_sizes=(256, 1024, 8192)):
    print(f"Context-only: Python {platform.python_version()}, NumPy {np.__version__}, "
          f"{platform.system()} {platform.release()} {platform.machine()}")
    print("float32 arrays; 100 warmups per operation; no rendering/SDL initialization")
    rows = measure_contexts(samples, block_sizes)
    for row in rows:
        budget_share = row["p95_us"] * 3 / (1e6 / 60) * 100
        print(f"block={row['block_size']} {row['operation']}: n={row['samples']} "
              f"median={row['median_us']:.3f}us p95={row['p95_us']:.3f}us "
              f"array_bytes/call={row['bytes_copied']} "
              f"3x_p95/60Hz_budget={budget_share:.3f}%")
    print("3x is a host-boundary cost estimate, not measured whole-app latency or FPS.")
    return rows


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
    parser.add_argument("--context-only", action="store_true",
                        help="measure context construction/replacement without rendering")
    parser.add_argument("--samples", type=int, default=1000,
                        help="samples per context operation (default: 1000)")
    parser.add_argument("--audio-blocks", type=int, nargs="+", default=[256, 1024, 8192],
                        help="context benchmark audio block sizes")
    args = parser.parse_args(argv)
    if args.samples <= 0:
        parser.error("--samples must be positive")
    if any(size < 2 for size in args.audio_blocks):
        parser.error("--audio-blocks must be integers of at least two")
    if args.context_only and (args.gl or args.effects):
        parser.error("--context-only cannot be combined with --gl or --effects")
    if not math.isfinite(args.duration) or args.duration <= 0:
        parser.error("--duration must be greater than zero")
    available = {name for name, _ in MODES}
    unknown = set(args.effects or ()) - available
    if unknown:
        parser.error(f"unknown effect(s): {', '.join(sorted(unknown))}")
    return args


if __name__ == "__main__":
    args = parse_args()
    if args.context_only:
        print_context_measurements(args.samples, args.audio_blocks)
        raise SystemExit(0)
    run_benchmark(
        args.duration,
        enable_gl=args.gl,
        headless=not args.gl,
        resolution=(3840, 2160) if args.resolution == "4k" else (1920, 1080),
        effect_names=args.effects,
    )
