import unittest
from copy import deepcopy

import numpy as np
import pygame

from tests.render_host import RenderHost
from effects.aurora import Aurora
from effects.bubbles import Bubbles
from effects.plasma_gl import PlasmaGL
from effects.mycelium import Mycelium
from effects.slimemold import SlimeMold
from effects.synapse import Synapse
from effects.hyperbolic import Hyperbolic
from effects.liquidlight import LiquidLight
from effects.cymatica import Cymatica
from effects.phason import Phason
from effects.tesseract import Tesseract
from effects.ferrofluid import Ferrofluid
from effects.heartbeat import Heartbeat
from effects.mobius import Mobius
from effects.chromatic import Chromatic
from effects.persistence import Persistence
from effects.lattice import Lattice
from effects.magnetar import Magnetar
from effects.fireworks import Fireworks


class EffectRenderRegressionTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        pygame.init()
        pygame.display.set_mode((320, 240))

    @classmethod
    def tearDownClass(cls):
        pygame.quit()

    def setUp(self):
        self.host = RenderHost()
        self.old = (self.host.width, self.host.height, self.host.initialized,
                    self.host.mid, self.host.treble)
        self.host.width, self.host.height = 320, 240
        self.host.initialized = True
        self.host.mid = 0.4
        self.host.treble = 0.6
        self.surface = pygame.Surface((320, 240))
        self.waveform = np.zeros(self.host.audio.block_size, dtype=np.float32)
        self.fft = np.zeros(self.host.audio.block_size // 2, dtype=np.float32)

    def tearDown(self):
        self.host.width, self.host.height, self.host.initialized, self.host.mid, self.host.treble = self.old

    def test_bubble_surface_cache_is_bounded_and_releases_owned_state(self):
        effect = self.host.create(Bubbles)
        oldest = effect._get_bsurf(10)
        recent = effect._get_bsurf(11)
        for size in range(12, 10 + effect.MAX_CACHED_SURFACES):
            effect._get_bsurf(size)
        self.assertIs(effect._get_bsurf(10), oldest)
        effect._get_bsurf(100)
        self.assertNotIn(11, effect._surf_cache)
        self.assertIs(effect._surf_cache[10], oldest)
        self.assertEqual(len(effect._surf_cache), effect.MAX_CACHED_SURFACES)
        self.assertIsNot(effect._get_bsurf(11), recent)
        effect.release()
        effect.release()
        self.assertFalse(effect._surf_cache)
        self.assertFalse(effect.pool)

    def test_mycelium_colony_presentation_does_not_advance_simulation(self):
        effect = self.host.create(Mycelium)
        effect.draw(self.surface, self.waveform, self.fft, .8, 0)
        before = deepcopy((effect._cores, effect._tips, effect._segs, effect._spores,
                           effect._phase, effect._pulse, effect._rng.bit_generator.state))
        self.surface.fill((0, 0, 0))
        effect._draw_colony(self.surface, .8, .6)
        after = (effect._cores, effect._tips, effect._segs, effect._spores,
                 effect._phase, effect._pulse, effect._rng.bit_generator.state)
        self.assertEqual(before, after)
        self.assertGreater(int(pygame.surfarray.array3d(self.surface).sum()), 0)

    def test_slimemold_clamps_deposit_indices(self):
        effect = self.host.create(SlimeMold)
        effect._px[-1] = np.float32(effect._W)
        effect._py[-1] = np.float32(effect._H)
        effect.draw(self.surface, self.waveform, self.fft, 0.0, 0)

    def test_aurora_allocates_wave_buffer_on_first_frame(self):
        self.host.create(Aurora).draw(self.surface, self.waveform, self.fft, 0.0, 0)

    def test_mycelium_constructs_on_small_display(self):
        old_size = (self.host.width, self.host.height)
        try:
            self.host.width, self.host.height = 100, 80
            effect = self.host.create(Mycelium)
            effect.draw(pygame.Surface((100, 80)), self.waveform, self.fft, 0.0, 0)
        finally:
            self.host.width, self.host.height = old_size

    def test_synapse_grows_sheds_and_wanders_nodes(self):
        effect = self.host.create(Synapse)
        effect.draw(self.surface, self.waveform, self.fft, 0.0, 0)
        initial = effect.n_nodes
        before = [node.copy() for node in effect._nodes]
        effect._mutate_topology(320, 240, add=True)
        self.assertEqual(effect.n_nodes, initial + 1)
        effect.draw(self.surface, self.waveform, self.fft, 0.0, 1)
        self.assertTrue(any(a != b for a, b in zip(before, effect._nodes[:initial])))
        effect._mutate_topology(320, 240, add=False)
        self.assertEqual(effect.n_nodes, initial)

    def test_bubbles_accepts_high_gain_beat_values(self):
        effect = self.host.create(Bubbles)
        for beat in (0.0, 3.0, 6.0):
            effect.draw(self.surface, self.waveform, self.fft, beat, 0)

    def test_plasma_cpu_fallback_reduces_large_internal_grid(self):
        old_size = (self.host.width, self.host.height)
        try:
            self.host.width, self.host.height = 1920, 1080
            effect = self.host.create(PlasmaGL)
            effect._ensure_fallback(1920, 1080)
            self.assertLess(effect._X.shape[1], 1080)
            self.assertLess(effect._X.shape[0], 1920)
        finally:
            self.host.width, self.host.height = old_size

    def test_plasma_domain_warp_stays_bounded_at_high_gain(self):
        effect = self.host.create(PlasmaGL)
        for tick, beat in enumerate((0.0, 3.0, 6.0) * 6):
            effect.draw(self.surface, self.waveform, self.fft, beat, tick)
            self.assertGreaterEqual(effect._warp, 0.0)
            self.assertLessEqual(effect._warp, 0.42)
            self.assertTrue(np.isfinite(effect._X).all())

    def test_heartbeat_leaves_background_untouched_until_a_ring_is_spawned(self):
        effect = self.host.create(Heartbeat)
        self.surface.fill((13, 21, 34))
        before = pygame.surfarray.array3d(self.surface)
        # FFT activity alone must not paint a standing-wave background.
        effect.draw(self.surface, self.waveform, np.ones(512), 0., 0)
        np.testing.assert_array_equal(pygame.surfarray.array3d(self.surface), before)
        self.surface.fill((0, 0, 0))
        effect.draw(self.surface, self.waveform, self.fft, 1.2, 1)
        self.assertEqual(len(effect._rings), 2)
        pixels = pygame.surfarray.array3d(self.surface)
        self.assertTrue(pixels.any())
        self.assertFalse(pixels[:8, :8].any())
        self.assertFalse(pixels[-8:, -8:].any())

    def test_heartbeat_rings_are_bounded_and_support_small_targets_without_fft(self):
        effect = self.host.create(Heartbeat)
        for tick, beat in enumerate((0.0, 3.0, 6.0) * 8):
            effect.draw(self.surface, self.waveform, self.fft, beat, tick)
            self.assertLessEqual(len(effect._rings), 16)
            for ring in effect._rings:
                self.assertTrue(np.isfinite(ring).all())
                self.assertGreater(ring.r, 0.)
                self.assertLessEqual(ring.r, ring.max_r)
        target = pygame.Surface((64, 48))
        effect.draw(target, self.waveform, np.zeros(0, dtype=np.float32), 0., 100)
        rgba = effect.draw_frame(64, 48, self.waveform, np.zeros(0), 0., 101)
        self.assertEqual(rgba.shape, (48, 64, 4))
        self.assertEqual(rgba.dtype, np.uint8)
        self.assertTrue((rgba[:, :, 3] == 255).all())

    def test_mycelium_reaction_halo_stays_bounded(self):
        effect = self.host.create(Mycelium)
        initial_tips = effect.max_tips
        for tick, beat in enumerate((0.0, 3.0, 6.0) * 8):
            effect.draw(self.surface, self.waveform, self.fft, beat, tick)
            self.assertTrue(np.isfinite(effect._halo).all())
            self.assertGreaterEqual(float(effect._halo.min()), 0.0)
            self.assertLessEqual(float(effect._halo.max()), 1.0)
            self.assertLessEqual(len(effect._tips), initial_tips)
        self.assertNotEqual(self.surface.get_bounding_rect().width, 0)

    def test_mycelium_cores_wander_around_seeded_anchors(self):
        effect = self.host.create(Mycelium)
        initial = [(core[0], core[1]) for core in effect._cores]
        for tick in range(60):
            effect.draw(self.surface, self.waveform, self.fft, 0.0, tick)
        self.assertTrue(any(
            np.hypot(core[0] - x, core[1] - y) > 1.0
            for core, (x, y) in zip(effect._cores, initial)
        ))

    def test_fourth_dimension_projection_accents_remain_finite(self):
        mobius = self.host.create(Mobius)
        persistence = self.host.create(Persistence)
        for tick, beat in enumerate((0.0, 3.0, 6.0) * 5):
            mobius.draw(self.surface, self.waveform, self.fft, beat, tick)
            persistence.draw(self.surface, self.waveform, self.fft, beat, tick)
        self.assertTrue(np.isfinite(mobius._rw))
        self.assertTrue(np.isfinite(np.asarray(persistence._rot_w)).all())
        self.assertNotEqual(self.surface.get_bounding_rect().width, 0)

    def test_lattice_euclidean_scale_and_shockwave_stay_bounded(self):
        effect = self.host.create(Lattice)
        self.assertFalse(effect.MORPH_SCHEMA)
        for tick, beat in enumerate((0.0, 3.0, 6.0) * 6):
            effect.draw(self.surface, self.waveform, self.fft, beat, tick)
            self.assertTrue(np.isfinite(effect._shock_r))
            self.assertGreaterEqual(effect._scale, 0.90)
            self.assertLessEqual(effect._scale, 1.12)
        self.assertNotEqual(self.surface.get_bounding_rect().width, 0)

    def test_magnetar_contours_stay_finite_with_particles(self):
        effect = self.host.create(Magnetar)
        for tick, beat in enumerate((0.0, 3.0, 6.0) * 6):
            effect.draw(self.surface, self.waveform, self.fft, beat, tick)
            self.assertTrue(np.isfinite(effect._contour).all())
            self.assertLessEqual(float(effect._contour.max()), 1.0)
        self.assertNotEqual(self.surface.get_bounding_rect().width, 0)

    def test_hyperbolic_tiles_are_bounded_on_small_and_wide_targets(self):
        effect = self.host.create(Hyperbolic)
        for size in ((64, 48), (320, 240), (1920, 1080)):
            target = pygame.Surface(size)
            for tick, beat in enumerate((0.0, 3.0, 6.0) * 4):
                effect.draw(target, self.waveform, self.fft, beat, tick)
                self.assertLessEqual(len(effect._tiles), effect.MAX_TILES)
            self.assertEqual(effect._size, size)
            self.assertNotEqual(target.get_bounding_rect().width, 0)

    def test_liquid_light_reuses_bounded_fluids(self):
        effect = self.host.create(LiquidLight)
        dye = effect._dye
        velocity = effect._vx
        for tick, beat in enumerate((0.0, 3.0, 6.0) * 20):
            effect.draw(self.surface, self.waveform, self.fft, beat, tick)
            self.assertTrue(np.isfinite(effect._dye).all())
            self.assertTrue(np.isfinite(effect._vx).all())
            self.assertLessEqual(float(effect._dye.max()), effect.MAX_DENSITY)
            self.assertLessEqual(float(np.abs(effect._vx).max()), effect.MAX_VELOCITY)
        self.assertIs(effect._dye, dye)
        self.assertIs(effect._vx, velocity)
        self.assertNotEqual(self.surface.get_bounding_rect().width, 0)

    def test_motion_field_consumers_stay_finite_and_bounded(self):
        producer = self.host.create(LiquidLight)
        fireworks = self.host.create(Fireworks)
        mycelium = self.host.create(Mycelium)
        for tick, beat in enumerate((0.0, 3.0, 6.0) * 6):
            producer.draw(self.surface, self.waveform, self.fft, beat, tick)
            field = producer.get_motion_field()
            fireworks.set_motion_field(field)
            mycelium.set_motion_field(field)
            fireworks.draw(self.surface, self.waveform, self.fft, beat, tick)
            mycelium.draw(self.surface, self.waveform, self.fft, beat, tick)
            self.assertTrue(np.isfinite(field[0]).all())
            self.assertTrue(np.isfinite(field[1]).all())
            self.assertLessEqual(len(fireworks._embers), fireworks._MAX_EMBERS)
            self.assertLessEqual(len(mycelium._tips), mycelium.max_tips)

    def test_declared_effect_schemas_clamp_and_expose_values(self):
        for effect in (self.host.create(Hyperbolic), self.host.create(Tesseract), self.host.create(Persistence)):
            self.assertTrue(effect.MORPH_SCHEMA)
            values = effect.get_morph_values()
            self.assertEqual(set(values), set(effect.MORPH_SCHEMA))
            effect.set_morph_values({name: 99.0 for name in values})
            for name, bounds in effect.MORPH_SCHEMA.items():
                self.assertGreaterEqual(getattr(effect, name), bounds[0])
                self.assertLessEqual(getattr(effect, name), bounds[1])

    def test_cymatica_handles_fft_edges_and_bounded_sand(self):
        effect = self.host.create(Cymatica)
        for tick, beat in enumerate((0.0, 3.0, 6.0) * 10):
            fft = np.zeros(0, dtype=np.float32) if tick % 2 else self.fft
            effect.draw(self.surface, self.waveform, fft, beat, tick)
            self.assertTrue(np.isfinite(effect._field).all())
            self.assertLessEqual(len(effect._px), effect.MAX_PARTICLES)
            self.assertLessEqual(len(effect._py), effect.MAX_PARTICLES)
        self.assertNotEqual(self.surface.get_bounding_rect().width, 0)

    def test_phason_reuses_bounded_quasiperiodic_field(self):
        effect = self.host.create(Phason)
        field = effect._field
        for tick, beat in enumerate((0.0, 3.0, 6.0) * 12):
            effect.draw(self.surface, self.waveform, self.fft, beat, tick)
            self.assertTrue(np.isfinite(effect._field).all())
            self.assertLessEqual(float(np.abs(effect._field).max()), effect.MAX_FIELD)
            self.assertLessEqual(effect.MAX_WAVES, 11)
        self.assertIs(effect._field, field)
        self.assertNotEqual(self.surface.get_bounding_rect().width, 0)

    def test_tesseract_cycles_finite_bounded_projections(self):
        effect = self.host.create(Tesseract)
        for tick, beat in enumerate((0.0, 3.0, 0.0, 6.0) * 4):
            effect.draw(self.surface, self.waveform, self.fft, beat, tick)
            self.assertLessEqual(len(effect._vertices), effect.MAX_VERTICES)
            self.assertLessEqual(len(effect._edges), effect.MAX_EDGES)
            self.assertTrue(np.isfinite(effect._projected).all())
        self.assertEqual(effect._preset, 2)
        self.assertNotEqual(self.surface.get_bounding_rect().width, 0)

    def test_tesseract_rotation_responds_to_audio_bands(self):
        baseline = self.host.create(Tesseract)
        baseline.draw(self.surface, self.waveform, self.fft, 0.0, 0)
        baseline_xw = baseline._rotation_xw
        baseline_yz = baseline._rotation_yz

        effect = self.host.create(Tesseract)
        self.host.mid = 3.0
        self.host.treble = 2.0
        effect.draw(self.surface, self.waveform, self.fft, 1.2, 0)
        self.assertGreater(effect._rotation_xw, baseline_xw)
        self.assertGreater(effect._rotation_yz, baseline_yz)

    def test_ferrofluid_field_handles_poles_and_strong_beats(self):
        effect = self.host.create(Ferrofluid)
        for tick, beat in enumerate((0.0, 3.0, 6.0) * 12):
            effect.draw(self.surface, self.waveform, self.fft, beat, tick)
            self.assertTrue(np.isfinite(effect._field).all())
            self.assertLessEqual(float(np.abs(effect._field).max()), effect.MAX_FIELD)
            self.assertEqual(len(effect._poles), effect.MAX_POLES)
        effect._poles[0] = effect._poles[1]
        effect.draw(self.surface, self.waveform, self.fft, 0.0, 100)
        self.assertTrue(np.isfinite(effect._field).all())
        self.assertNotEqual(self.surface.get_bounding_rect().width, 0)

    def test_mobius_whole_strip_stays_inside_margin(self):
        effect = self.host.create(Mobius)
        for size in ((320, 240), (1200, 400), (400, 1200)):
            target = pygame.Surface(size)
            for tick in range(50):
                effect.draw(target, self.waveform, self.fft, 6.0, tick)
                points = effect._projected
                self.assertGreaterEqual(points[..., 0].min(), size[0] * 0.04 - 1)
                self.assertLessEqual(points[..., 0].max(), size[0] * 0.96 + 1)
                self.assertGreaterEqual(points[..., 1].min(), size[1] * 0.04 - 1)
                self.assertLessEqual(points[..., 1].max(), size[1] * 0.96 + 1)

    def test_lattice_laptop_density_and_resize_with_empty_fft(self):
        effect = self.host.create(Lattice)
        for size, count in (((1280, 720), 22 * 14), ((3840, 2160), 14 * 9)):
            self.host.width, self.host.height = size
            effect.draw(pygame.Surface(size), self.waveform, np.zeros(0), 0.0, 0)
            self.assertEqual(len(effect._nodes), count)

    def test_synapse_retires_oldest_and_grows_without_beats(self):
        effect = self.host.create(Synapse)
        effect._ensure_nodes(320, 240)
        initial = effect.n_nodes
        oldest = effect._anchors[0]
        for tick in range(450):
            effect.draw(self.surface, self.waveform, self.fft, 0.0, tick)
        self.assertGreater(effect.n_nodes, initial)
        self.assertFalse(any(anchor is oldest for anchor in effect._anchors))
        for ei, *_ in effect._signals:
            self.assertLess(ei, len(effect._edges))
        self.assertEqual(len(effect._glow), effect.n_nodes)

    def test_cymatica_alternating_fft_has_no_mode_jumps(self):
        effect = self.host.create(Cymatica)
        previous = None
        for tick in range(30):
            fft = np.zeros(128, dtype=np.float32)
            fft[1 if tick % 2 else 120] = 1.0
            effect.draw(self.surface, self.waveform, fft, 1.5, tick)
            if previous is not None:
                self.assertLess(float(np.abs(effect._field - previous).max()), 0.012)
            previous = effect._field.copy()

    def test_chromatic_expansion_scales_with_tv_resolution(self):
        self.host.profile = "tv"  # Compare physical sizes under the same response profile.
        normalized_steps = []
        for width, height in ((1920, 1080), (3840, 2160)):
            effect = self.host.create(Chromatic)
            effect._spawn(0.8, width, height)
            effect._beat_prev = 0.8
            effect.draw(pygame.Surface((width, height)), self.waveform, self.fft, 0.8, 0)
            normalized_steps.append(effect._rings[0][2] / height)
        self.assertAlmostEqual(*normalized_steps)

    def test_hyperbolic_projection_uses_rotating_unit_sphere(self):
        effect = self.host.create(Hyperbolic)
        for angle in (0, 0.8, 2.5):
            effect._angle = angle
            for point in (0j, 0.5j, complex(0.5, 0.5)):
                self.assertAlmostEqual(np.linalg.norm(effect._sphere_point(point)), 1.0)
        effect._angle = 0
        first = effect._project(0.5j, 320, 240)
        effect._angle = 0.8
        self.assertNotEqual(first, effect._project(0.5j, 320, 240))

    def test_ferrofluid_smooth_state_advances_for_gl_adapter(self):
        effect = self.host.create(Ferrofluid)
        bass, _, _ = effect._advance_state(1.5)
        self.assertLess(bass, 0.1)
        uniforms = effect._gl_uniforms((1920, 1080), bass, 0.1, 0)
        self.assertGreater(uniforms["u_time"], 0)
        previous = effect._time
        effect._advance_state(0.0)
        self.assertGreater(effect._time, previous)

    def test_fireworks_flow_field_preserves_caps_and_trail(self):
        old_size = (self.host.width, self.host.height)
        old_low_spec = getattr(self.host, "low_spec", False)
        self.host.width, self.host.height = 96, 72
        self.host.low_spec = True
        target = pygame.Surface((96, 72))
        effect = self.host.create(Fireworks)
        effect._EXPLODE_EMBERS = (5, 6)
        # A representative sustained-beat window; the fixed-size buffers are
        # also exercised by the longer smoke/benchmark runs.
        for tick, beat in enumerate((0.0, 3.0, 6.0) * 10):
            effect.draw(target, self.waveform, self.fft, beat, tick)
            self.assertTrue(np.isfinite(effect._flow_x).all())
            self.assertTrue(np.isfinite(effect._flow_y).all())
            self.assertLessEqual(len(effect._rockets), effect._MAX_ROCKETS)
            self.assertLessEqual(len(effect._embers), effect._MAX_EMBERS)
        self.assertNotEqual(effect._trail.get_bounding_rect().width, 0)
        self.host.width, self.host.height = old_size
        self.host.low_spec = old_low_spec


if __name__ == "__main__":
    unittest.main()
