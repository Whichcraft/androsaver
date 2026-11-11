import unittest
import contextlib
import io
import itertools
from unittest import mock

import numpy as np

import benchmarks


class BenchmarkCliTests(unittest.TestCase):
    def test_nonfinite_durations_are_rejected(self):
        for duration in ('nan', 'inf', '-inf'):
            with self.subTest(duration=duration), contextlib.redirect_stderr(io.StringIO()):
                with self.assertRaises(SystemExit):
                    benchmarks.parse_args(['--duration=' + duration])
                with self.assertRaises(ValueError):
                    benchmarks.run_benchmark(float(duration))

    def test_gl_draw_and_renderer_release_failures_always_cleanup(self):
        for failure in ('draw', 'release'):
            with self.subTest(failure=failure), contextlib.ExitStack() as stack:
                cpu, gpu, renderer = mock.Mock(), mock.Mock(), mock.Mock()
                gpu.IS_GL = True
                if failure == 'draw':
                    gpu.render.side_effect = RuntimeError('draw failed')
                else:
                    renderer.release.side_effect = RuntimeError('release failed')
                factory = mock.Mock(side_effect=[cpu, gpu])
                stack.enter_context(mock.patch.object(benchmarks, 'MODES', [('Fake', factory)]))
                stack.enter_context(mock.patch.object(benchmarks, 'HAS_MODERNGL', True))
                stack.enter_context(mock.patch.object(benchmarks, 'GLRenderer', return_value=renderer))
                stack.enter_context(mock.patch.object(benchmarks.time, 'perf_counter',
                                                      side_effect=itertools.count(0., .1)))
                stack.enter_context(mock.patch.object(benchmarks.pygame, 'init'))
                quit_sdl = stack.enter_context(mock.patch.object(benchmarks.pygame, 'quit'))
                stack.enter_context(mock.patch.object(benchmarks.pygame.display, 'set_mode'))
                stack.enter_context(mock.patch.object(benchmarks.pygame.display, 'flip'))
                with contextlib.redirect_stdout(io.StringIO()), self.assertRaises(RuntimeError):
                    benchmarks.run_benchmark(.25, enable_gl=True, headless=False, resolution=(32, 24))
                cpu.release.assert_called_once_with()
                gpu.release.assert_called_once_with()
                renderer.release.assert_called_once_with()
                quit_sdl.assert_called_once_with()


    def test_benchmark_fixture_is_seeded_without_global_rng_side_effects(self):
        captured = []
        state = np.random.get_state()
        for _ in range(2):
            effect = mock.Mock()
            effect.render.side_effect = lambda surf, frame: captured.append(frame.waveform.copy())
            with mock.patch.object(benchmarks, 'MODES', [('Fake', mock.Mock(return_value=effect))]), \
                 mock.patch.object(benchmarks.time, 'perf_counter', side_effect=itertools.count(0., .1)), \
                 mock.patch.object(benchmarks.pygame, 'init'), \
                 mock.patch.object(benchmarks.pygame, 'quit'), contextlib.redirect_stdout(io.StringIO()):
                benchmarks.run_benchmark(.15, headless=False, resolution=(32, 24))
        np.testing.assert_array_equal(captured[0], captured[1])
        after = np.random.get_state()
        self.assertEqual(state[0], after[0])
        np.testing.assert_array_equal(state[1], after[1])
        self.assertEqual(state[2:], after[2:])

    def test_cpu_only_defaults_and_duration(self):
        args = benchmarks.parse_args(["--cpu-only", "--duration", "0.25"])
        self.assertTrue(args.cpu_only)
        self.assertFalse(args.gl)
        self.assertEqual(args.duration, 0.25)

    def test_gl_mode_is_mutually_exclusive_and_selectable(self):
        args = benchmarks.parse_args(["--gl"])
        self.assertTrue(args.gl)
        self.assertFalse(args.cpu_only)

    def test_non_positive_duration_is_rejected(self):
        with self.assertRaises(SystemExit):
            benchmarks.parse_args(["--duration", "0"])

    def test_resolution_and_effect_selection(self):
        args = benchmarks.parse_args(["--resolution", "4k", "--effects", "Phason", "Cymatica"])
        self.assertEqual(args.resolution, "4k")
        self.assertEqual(args.effects, ["Phason", "Cymatica"])

    def test_unknown_effect_is_rejected(self):
        with self.assertRaises(SystemExit):
            benchmarks.parse_args(["--effects", "not-an-effect"])


class ContextMeasurementTests(unittest.TestCase):
    def test_context_measurements_work_without_sdl_or_effects(self):
        with mock.patch.object(benchmarks.pygame, 'init', side_effect=AssertionError('SDL not needed')), \
             mock.patch.object(benchmarks, 'MODES', []):
            rows = benchmarks.measure_contexts(samples=3, block_sizes=(256, 1024))
        self.assertEqual(len(rows), 4)
        self.assertEqual({r['operation'] for r in rows}, {'construct', 'metadata_replace'})
        for row in rows:
            self.assertEqual(row['samples'], 3)
            self.assertEqual(row['bytes_copied'], (row['block_size'] + row['block_size']//2) * 4)
            self.assertGreaterEqual(row['median_us'], 0.)
            self.assertGreaterEqual(row['p95_us'], row['median_us'])

    def test_context_cli_and_invalid_measurement_inputs(self):
        args = benchmarks.parse_args(['--context-only', '--samples', '20', '--audio-blocks', '256', '2048'])
        self.assertTrue(args.context_only)
        self.assertEqual(args.audio_blocks, [256, 2048])
        self.assertEqual(args.samples, 20)
        for argv in (['--samples', '0'], ['--audio-blocks', '1'],
                     ['--context-only', '--gl'], ['--context-only', '--effects', 'Cube']):
            with self.subTest(argv=argv), contextlib.redirect_stderr(io.StringIO()):
                with self.assertRaises(SystemExit):
                    benchmarks.parse_args(argv)
        for samples in (True, 0, 1.5):
            with self.assertRaisesRegex(ValueError, 'samples'):
                benchmarks.measure_contexts(samples=samples)
        with self.assertRaisesRegex(ValueError, 'block_sizes'):
            benchmarks.measure_contexts(block_sizes=())


if __name__ == "__main__":
    unittest.main()
