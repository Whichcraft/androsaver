"""Regression tests for the 2026-11-13 full-code bug sweep."""
import os
import tempfile
import unittest
from unittest import mock

import numpy as np
import pygame

import settings as sett
from types import SimpleNamespace

from core.audio_engine import AudioEngine, BandLevel
from core.frame_composer import FrameComposer
from effects import MODES, MODE_NAMES, resolve_mode
from effects.aurora import Aurora
from effects.bubbles import Bubbles
from effects.horizon import Horizon
from effects.spectrum import Bars
from effects.spiral import Spiral
from effects.waterfall import GlowSquares
from tests.render_host import RenderHost
from psysualizer import VisualizerApp
from effects.butterflies import Butterflies
from effects.chromaweave import ChromaWeave
from effects.flowfield import FlowField
from effects.magnetar import Magnetar


class BugSweepTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        pygame.init()
        pygame.display.set_mode((64, 64))

    @classmethod
    def tearDownClass(cls):
        pygame.quit()

    def setUp(self):
        self.host = RenderHost(160, 120)
        self.surface = pygame.Surface((160, 120))
        self.waveform = np.zeros(1024, dtype=np.float32)

    def test_short_and_empty_spectra_render(self):
        for cls in (Bars, GlowSquares, Spiral, Bubbles):
            for length in (0, 1, 5, 256):
                effect = self.host.create(cls)
                effect.draw(self.surface, self.waveform, np.full(length, .3, np.float32), 1.0, 0)

    def test_aurora_renders_on_very_narrow_surfaces(self):
        for size in ((1, 1), (4, 100), (5, 50)):
            effect = self.host.create(Aurora)
            effect.draw(pygame.Surface(size), self.waveform, np.zeros(512, np.float32), 1.0, 0)

    def test_bubble_size_stays_bounded_on_huge_beats(self):
        effect = self.host.create(Bubbles)
        fft = np.full(512, .6, np.float32)
        for tick in range(20):
            effect.draw(self.surface, self.waveform, fft, 12.0, tick)
        # Radius is capped at half the frame plus the glow pad.
        self.assertLessEqual(max(effect._surf_cache), 2 * (60 + 14) + 2)

    def test_horizon_twinkle_follows_elapsed_time_not_frames(self):
        clocks = []
        for fps in (30, 60):
            self.host.dt = 1 / fps
            effect = self.host.create(Horizon)
            for tick in range(fps):
                effect.draw(self.surface, self.waveform, np.zeros(512, np.float32), 0.0, tick)
            clocks.append(effect._clock)
        self.host.dt = 1 / 60
        self.assertAlmostEqual(clocks[0], clocks[1], delta=0.5)

    BLOCKS_PER_SECOND = 44100 / 1024

    def band_run(self, level, parts, rng):
        result = {}
        for label, low, high, seconds, noise in parts:
            blocks = int(seconds * self.BLOCKS_PER_SECOND)
            if noise:   # room noise through a laptop microphone: log-normal
                values = np.exp(rng.normal(np.log(0.002), np.log(1.8), blocks))
            else:
                values = rng.uniform(low, high, blocks)
            result[label] = np.array([level.update(float(v)) for v in values])
        return result

    def test_quiet_music_pause_holds_and_resumes_cleanly(self):
        # Measured live: quiet music at 0.010-0.021, room noise 0.002-0.005.
        rng = np.random.default_rng(1)
        level = BandLevel(self.BLOCKS_PER_SECOND)
        out = self.band_run(level, [("music", .010, .021, 15, False), ("pause", 0, 0, 120, True),
                                    ("resume", .010, .021, 10, False)], rng)
        self.assertLess(float(np.median(out["pause"][-200:])), 0.3)   # was ~1.0: room noise as music
        self.assertLess(float(out["resume"].max()), 3.0)
        self.assertAlmostEqual(float(np.median(out["resume"])), 1.0, delta=0.15)

    def test_quieter_passage_is_followed_once_the_room_was_heard(self):
        rng = np.random.default_rng(2)
        level = BandLevel(self.BLOCKS_PER_SECOND)
        out = self.band_run(level, [("room", 0, 0, 10, True), ("music", .015, .025, 15, False),
                                    ("quiet", .004, .006, 30, False)], rng)
        self.assertGreater(float(np.median(out["quiet"][-200:])), 0.8)

    def test_band_average_catches_up_after_a_silent_start(self):
        rng = np.random.default_rng(3)
        level = BandLevel(self.BLOCKS_PER_SECOND)
        out = self.band_run(level, [("silence", 0, 0, 10, True), ("music", .010, .021, 10, False)], rng)
        self.assertLess(float(out["music"][43:].max()), 3.0)
        self.assertLessEqual(BandLevel(1.0).update(1e6), 6.0)

    def test_start_up_transient_does_not_seed_the_average(self):
        rng = np.random.default_rng(4)
        level = BandLevel(self.BLOCKS_PER_SECOND)
        level.update(0.07)                                   # stream start-up pop
        self.band_run(level, [("room", 0, 0, 20, True)], rng)
        out = self.band_run(level, [("music", .010, .021, 3, False)], rng)
        self.assertAlmostEqual(float(np.median(out["music"][43:])), 1.0, delta=0.3)

    def test_one_bad_sample_does_not_poison_the_analysis(self):
        engine = AudioEngine(self.host.audio)
        info = mock.Mock(inputBufferAdcTime=None, currentTime=0.0)
        rng = np.random.default_rng(3)
        block = (rng.standard_normal((self.host.audio.block_size, 1)) * .1).astype(np.float32)
        for value in (np.nan, np.inf):
            bad = block.copy()
            bad[10, 0] = value
            engine._audio_cb(bad, len(bad), info, None)
            for _ in range(20):
                engine._audio_cb(block, len(block), info, None)
            snapshot = engine.get_snapshot()
            self.assertTrue(np.isfinite(snapshot.fft).all())
            self.assertTrue(np.isfinite([snapshot.mid_energy, snapshot.treble_energy,
                                         snapshot.raw_beat, *snapshot.envelopes]).all())
        engine.beat_tracker.release()

    def test_switching_device_forgets_the_old_input(self):
        engine = AudioEngine(self.host.audio)
        info = mock.Mock(inputBufferAdcTime=None, currentTime=0.0)
        loud = np.full((self.host.audio.block_size, 1), 0.2, np.float32)
        loud[::2] += 0.3
        for _ in range(50):
            engine._audio_cb(loud, len(loud), info, None)
        fake = mock.Mock(InputStream=mock.Mock(return_value=mock.Mock()))
        with mock.patch("core.audio_engine._get_sounddevice", return_value=fake):
            engine.start_input_stream(4)
            engine.start_input_stream(5)
        self.assertIsNone(engine._mid_level.average)
        self.assertIsNone(engine._dc_offset)
        self.assertEqual(len(engine._beat_times), 0)
        self.assertEqual(len(engine.beat_tracker._blocks), 0)
        engine.beat_tracker.release()

    def test_a_stream_that_stops_delivering_is_reopened(self):
        app = SimpleNamespace(audio=mock.Mock(), _STREAM_TIMEOUT=2.0)
        app.audio.stream = SimpleNamespace(active=True)
        watch = lambda generation, now: VisualizerApp._watch_audio_stream(app, generation, now)
        watch(1, 0.0)
        watch(2, 0.5)
        watch(2, 1.5)
        app.audio.mark_stream_lost.assert_not_called()
        watch(2, 3.0)
        app.audio.mark_stream_lost.assert_called_once()
        app.audio.mark_stream_lost.reset_mock()
        app._audio_seen = (7, 10.0)
        app.audio.stream = SimpleNamespace(active=False)
        watch(7, 10.1)
        app.audio.mark_stream_lost.assert_called_once()

    def test_lost_stream_publishes_silence_and_retries(self):
        engine = AudioEngine(self.host.audio)
        info = mock.Mock(inputBufferAdcTime=None, currentTime=0.0)
        engine._audio_cb(np.full((self.host.audio.block_size, 1), .5, np.float32),
                         self.host.audio.block_size, info, None)
        before = engine.get_snapshot().generation
        engine.stream = mock.Mock()
        engine._initial_stream_pending = False
        engine.mark_stream_lost()
        snapshot = engine.get_snapshot()
        self.assertIsNone(engine.stream)
        self.assertTrue(engine.initial_stream_pending)
        self.assertGreater(snapshot.generation, before)
        self.assertFalse(snapshot.waveform.any())
        engine.beat_tracker.release()

    def test_input_dc_offset_is_removed_before_analysis(self):
        engine = AudioEngine(self.host.audio)
        info = mock.Mock(inputBufferAdcTime=None, currentTime=0.0)
        block = np.full((self.host.audio.block_size, 1), 0.2, dtype=np.float32)
        for i in range(40):
            info.currentTime = i * 0.023
            engine._audio_cb(block, len(block), info, None)
        waveform = engine._waveform
        self.assertLess(float(np.sqrt(np.mean(waveform ** 2))), 0.01)
        engine.beat_tracker.release()

    def test_cpu_fade_follows_a_resized_screen(self):
        composer = FrameComposer()
        display = mock.Mock()
        display.screen = pygame.Surface((80, 60))
        composer.update_target(display, False)
        display.screen = pygame.Surface((200, 100))
        composer.update_target(display, False)
        self.assertEqual(composer.fade.get_size(), (200, 100))

    def test_saved_modes_resolve_by_name_and_old_indices_shift(self):
        horizon = MODE_NAMES.index("Horizon")
        self.assertEqual(MODES[resolve_mode(horizon, None)][0], "Spectrum")
        self.assertEqual(MODES[resolve_mode(horizon + 1, None)][0], "Waterfall")
        self.assertEqual(resolve_mode(3, None), 3)
        self.assertEqual(resolve_mode(0, "Waterfall"), MODE_NAMES.index("Waterfall"))
        self.assertEqual(resolve_mode(7, "NoSuchEffect"), 7)

    def test_settings_keep_mode_names_and_reject_bad_ones(self):
        with tempfile.TemporaryDirectory() as tmp, \
                mock.patch.object(sett, "_CONFIG_DIR", tmp), \
                mock.patch.object(sett, "_SETTINGS_FILE", os.path.join(tmp, "settings.json")):
            sett.save({"mode_idx": 32, "mode_name": "Spectrum", "bg_mode_name": 5, "cf_frames": 80})
            loaded = sett.load()
        self.assertEqual(loaded["mode_name"], "Spectrum")
        self.assertIsNone(loaded["bg_mode_name"])
        self.assertEqual(loaded["cf_frames"], 80)

    def run_particles(self, cls, frames, size=(320, 180)):
        host = RenderHost(*size)
        host.mid = host.treble = .5
        effect = host.create(cls)
        surface = pygame.Surface(size)
        rng = np.random.default_rng(4)
        for tick in range(frames):
            fft = (np.exp(-np.arange(512) / 60.) * .5 * (.5 + rng.random(512))).astype(np.float32)
            surface.fill((0, 0, 0))
            effect.draw(surface, self.waveform, fft, 1.2 if tick % 26 < 3 else .1, tick)
        return effect

    def test_magnetar_and_flowfield_particles_stay_spread_out(self):
        for cls in (Magnetar, FlowField):
            effect = self.run_particles(cls, 1200)
            cells = set(zip((effect._px // 4).astype(int).tolist(), (effect._py // 4).astype(int).tolist()))
            # Before the fix: Magnetar 22 and FlowField a few dozen cells.
            self.assertGreater(len(cells), 150, cls.__name__)

    def test_flowfield_trail_fades_to_black(self):
        effect = self.run_particles(FlowField, 5)
        effect._px[:] = -1e6        # park every particle off screen
        effect._RESEED = 0.0
        effect._trail.fill((200, 200, 200))
        for tick in range(120):
            effect._px[:] = 0; effect._py[:] = 0
            effect.draw(pygame.Surface((320, 180)), self.waveform, np.zeros(512, np.float32), 0., tick)
        pixels = pygame.surfarray.array3d(effect._trail)
        self.assertEqual(int(np.median(pixels)), 0)

    def test_butterflies_keep_their_size_over_resize_round_trips(self):
        host = RenderHost(1280, 720)
        effect = host.create(Butterflies)
        for tick in range(30):
            effect.draw(pygame.Surface((1280, 720)), self.waveform, np.zeros(512, np.float32), .3, tick)
        before = [b.scale for b in effect._butterflies]
        for round_trip in range(3):
            for size in ((800, 600), (1280, 720)):
                host.width, host.height = size
                effect.draw(pygame.Surface(size), self.waveform, np.zeros(512, np.float32), .3, 100 + round_trip)
        after = [b.scale for b in effect._butterflies[:len(before)]]
        np.testing.assert_allclose(after, before, rtol=1e-6)

    def test_chromaweave_phase_stays_smooth_after_long_uptime(self):
        steps = []
        for start in (0.0, 3600.0):
            host = RenderHost(160, 90)
            effect = host.create(ChromaWeave)
            effect._time = start
            phases = []
            for tick in range(240):
                host.mid = .75 + .75 * np.sin(tick / 40)
                phases.append(effect._advance(0.)["u_weave"])
            step = np.angle(np.exp(1j * np.diff(phases)))
            steps.append(float(np.abs(step).max()))
        self.assertLess(steps[1], 0.05)            # was several radians per frame after an hour
        self.assertAlmostEqual(steps[0], steps[1], delta=0.01)

    def test_preset_morph_resolves_modes_by_name_and_keeps_whole_frames(self):
        app = self.host.app()
        app.tick, app.bpm = 0, 120.
        app.mode_idx, app.bg_mode_i, app.bg_on = 0, 0, False
        app.effect_gain, app.bg_alpha, app.cf_frames = .5, 80, 40
        app._switch_mode = mock.Mock()
        app._set_background_mode = mock.Mock()
        horizon = MODE_NAMES.index("Horizon")
        app._start_preset_morph({"mode_idx": horizon, "cf_frames": 81})   # saved before names
        self.assertEqual(MODE_NAMES[app._preset_morph.mode_idx], "Spectrum")
        app._start_preset_morph({"mode_idx": 0, "mode_name": "Waterfall", "bg_mode_name": "Cube"})
        self.assertEqual(MODE_NAMES[app._preset_morph.mode_idx], "Waterfall")
        self.assertEqual(MODE_NAMES[app._preset_morph.bg_mode_i], "Cube")
        app._start_preset_morph({"mode_idx": 1, "cf_frames": 81})
        app.tick = app._preset_morph.duration // 3
        app._advance_preset_morph()
        self.assertIsInstance(app.cf_frames, int)

    def test_gl_feedback_shader_samples_uploaded_frames_upright(self):
        import gl_renderer
        renderer = object.__new__(gl_renderer.GLRenderer)
        renderer.program = mock.Mock(return_value=(None, None))
        renderer.feedback_program()
        vertex = renderer.program.call_args[0][0]
        self.assertIn("v_uv.y = 1.0 - v_uv.y", vertex)

    def test_gl_feedback_output_is_upright_on_a_real_gpu(self):
        try:
            import moderngl
            context = moderngl.create_standalone_context(backend="egl", require=330)
        except Exception as exc:
            self.skipTest(f"EGL unavailable: {exc}")
        import gl_renderer
        try:
            renderer = gl_renderer.GLRenderer(64, 48, ctx=context)
            source = pygame.Surface((64, 48))
            source.fill((0, 0, 255))
            source.fill((255, 0, 0), (0, 0, 64, 24))        # red top half
            fbo = renderer.offscreen(64, 48)
            renderer.feedback_transform(source, fbo, 1.0, 0.0)
            pixels = renderer.read_pixels(fbo)
            self.assertEqual(pixels[2, 32, :3].tolist(), [255, 0, 0])
            self.assertEqual(pixels[45, 32, :3].tolist(), [0, 0, 255])
        finally:
            context.release()


if __name__ == "__main__":
    unittest.main()
