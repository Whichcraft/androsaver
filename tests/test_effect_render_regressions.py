import unittest

import numpy as np
import pygame

import config
from effects.aurora import Aurora
from effects.bubbles import Bubbles
from effects.plasma_gl import PlasmaGL
from effects.mycelium import Mycelium
from effects.slimemold import SlimeMold
from effects.synapse import Synapse
from effects.morphogenesis import Morphogenesis
from effects.hyperbolic import Hyperbolic
from effects.liquidlight import LiquidLight
from effects.cymatica import Cymatica
from effects.phason import Phason
from effects.tesseract import Tesseract
from effects.ferrofluid import Ferrofluid
from effects.mandelbox import Mandelbox
from effects.heartbeat import Heartbeat
from effects.mobius import Mobius
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
        self.old = (config.WIDTH, config.HEIGHT, config._INITIALIZED,
                    config.MID_ENERGY, config.TREBLE_ENERGY)
        config.WIDTH, config.HEIGHT = 320, 240
        config._INITIALIZED = True
        config.MID_ENERGY = 0.4
        config.TREBLE_ENERGY = 0.6
        self.surface = pygame.Surface((320, 240))
        self.waveform = np.zeros(config.BLOCK_SIZE, dtype=np.float32)
        self.fft = np.zeros(config.BLOCK_SIZE // 2, dtype=np.float32)

    def tearDown(self):
        config.WIDTH, config.HEIGHT, config._INITIALIZED, config.MID_ENERGY, config.TREBLE_ENERGY = self.old

    def test_slimemold_clamps_deposit_indices(self):
        effect = SlimeMold()
        effect._px[-1] = np.float32(effect._W)
        effect._py[-1] = np.float32(effect._H)
        effect.draw(self.surface, self.waveform, self.fft, 0.0, 0)

    def test_aurora_allocates_wave_buffer_on_first_frame(self):
        Aurora().draw(self.surface, self.waveform, self.fft, 0.0, 0)

    def test_mycelium_constructs_on_small_display(self):
        old_size = (config.WIDTH, config.HEIGHT)
        try:
            config.WIDTH, config.HEIGHT = 100, 80
            effect = Mycelium()
            effect.draw(pygame.Surface((100, 80)), self.waveform, self.fft, 0.0, 0)
        finally:
            config.WIDTH, config.HEIGHT = old_size

    def test_synapse_grows_sheds_and_wanders_nodes(self):
        effect = Synapse()
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
        effect = Bubbles()
        for beat in (0.0, 3.0, 6.0):
            effect.draw(self.surface, self.waveform, self.fft, beat, 0)

    def test_plasma_cpu_fallback_reduces_large_internal_grid(self):
        old_size = (config.WIDTH, config.HEIGHT)
        try:
            config.WIDTH, config.HEIGHT = 1920, 1080
            effect = PlasmaGL()
            effect._ensure_fallback(1920, 1080)
            self.assertLess(effect._X.shape[1], 1080)
            self.assertLess(effect._X.shape[0], 1920)
        finally:
            config.WIDTH, config.HEIGHT = old_size

    def test_plasma_domain_warp_stays_bounded_at_high_gain(self):
        effect = PlasmaGL()
        for tick, beat in enumerate((0.0, 3.0, 6.0) * 6):
            effect.draw(self.surface, self.waveform, self.fft, beat, tick)
            self.assertGreaterEqual(effect._warp, 0.0)
            self.assertLessEqual(effect._warp, 0.42)
            self.assertTrue(np.isfinite(effect._X).all())

    def test_heartbeat_cymatics_overlay_is_bounded_and_resizes(self):
        effect = Heartbeat()
        for tick, beat in enumerate((0.0, 3.0, 6.0) * 8):
            effect.draw(self.surface, self.waveform, self.fft, beat, tick)
            self.assertTrue(np.isfinite(effect._plate_field).all())
            self.assertLessEqual(float(np.abs(effect._plate_field).max()), 1.5)
        target = pygame.Surface((64, 48))
        effect.draw(target, self.waveform, np.zeros(0, dtype=np.float32), 0.0, 100)
        self.assertEqual(effect._plate_scaled.get_size(), target.get_size())

    def test_mycelium_reaction_halo_stays_bounded(self):
        effect = Mycelium()
        initial_tips = effect.max_tips
        for tick, beat in enumerate((0.0, 3.0, 6.0) * 8):
            effect.draw(self.surface, self.waveform, self.fft, beat, tick)
            self.assertTrue(np.isfinite(effect._halo).all())
            self.assertGreaterEqual(float(effect._halo.min()), 0.0)
            self.assertLessEqual(float(effect._halo.max()), 1.0)
            self.assertLessEqual(len(effect._tips), initial_tips)
        self.assertNotEqual(self.surface.get_bounding_rect().width, 0)

    def test_fourth_dimension_projection_accents_remain_finite(self):
        mobius = Mobius()
        persistence = Persistence()
        for tick, beat in enumerate((0.0, 3.0, 6.0) * 5):
            mobius.draw(self.surface, self.waveform, self.fft, beat, tick)
            persistence.draw(self.surface, self.waveform, self.fft, beat, tick)
        self.assertTrue(np.isfinite(mobius._rw))
        self.assertTrue(np.isfinite(np.asarray(persistence._rot_w)).all())
        self.assertNotEqual(self.surface.get_bounding_rect().width, 0)

    def test_lattice_hyperbolic_metric_stays_bounded(self):
        effect = Lattice()
        for tick, beat in enumerate((0.0, 3.0, 6.0) * 6):
            effect.draw(self.surface, self.waveform, self.fft, beat, tick)
            self.assertGreaterEqual(effect._hyperbolic_strength, 0.0)
            self.assertLessEqual(effect._hyperbolic_strength, 0.35)
        self.assertNotEqual(self.surface.get_bounding_rect().width, 0)

    def test_magnetar_contours_stay_finite_with_particles(self):
        effect = Magnetar()
        for tick, beat in enumerate((0.0, 3.0, 6.0) * 6):
            effect.draw(self.surface, self.waveform, self.fft, beat, tick)
            self.assertTrue(np.isfinite(effect._contour).all())
            self.assertLessEqual(float(effect._contour.max()), 1.0)
        self.assertNotEqual(self.surface.get_bounding_rect().width, 0)

    def test_morphogenesis_bounds_reaction_diffusion_state(self):
        effect = Morphogenesis()
        first_u = effect._u
        first_v = effect._v
        for tick, beat in enumerate((0.0, 3.0, 6.0) * 40):
            effect.draw(self.surface, self.waveform, self.fft, beat, tick)
            self.assertTrue(np.isfinite(effect._u).all())
            self.assertTrue(np.isfinite(effect._v).all())
            self.assertGreaterEqual(float(effect._u.min()), 0.0)
            self.assertLessEqual(float(effect._u.max()), effect.MAX_FIELD_VALUE)
            self.assertGreaterEqual(float(effect._v.min()), 0.0)
            self.assertLessEqual(float(effect._v.max()), effect.MAX_FIELD_VALUE)
        self.assertIs(effect._u, first_u)
        self.assertIs(effect._v, first_v)
        self.assertNotEqual(self.surface.get_bounding_rect().width, 0)

    def test_hyperbolic_tiles_are_bounded_on_small_and_wide_targets(self):
        effect = Hyperbolic()
        for size in ((64, 48), (320, 240), (1920, 1080)):
            target = pygame.Surface(size)
            for tick, beat in enumerate((0.0, 3.0, 6.0) * 4):
                effect.draw(target, self.waveform, self.fft, beat, tick)
                self.assertLessEqual(len(effect._tiles), effect.MAX_TILES)
            self.assertEqual(effect._size, size)
            self.assertNotEqual(target.get_bounding_rect().width, 0)

    def test_liquid_light_reuses_bounded_fluids(self):
        effect = LiquidLight()
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
        producer = LiquidLight()
        fireworks = Fireworks()
        mycelium = Mycelium()
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

    def test_compatible_effect_schemas_clamp_and_expose_values(self):
        for effect in (Lattice(), Hyperbolic(), Tesseract(), Persistence()):
            self.assertTrue(effect.MORPH_SCHEMA)
            values = effect.get_morph_values()
            self.assertEqual(set(values), set(effect.MORPH_SCHEMA))
            effect.set_morph_values({name: 99.0 for name in values})
            for name, bounds in effect.MORPH_SCHEMA.items():
                self.assertGreaterEqual(getattr(effect, name), bounds[0])
                self.assertLessEqual(getattr(effect, name), bounds[1])

    def test_cymatica_handles_fft_edges_and_bounded_sand(self):
        effect = Cymatica()
        for tick, beat in enumerate((0.0, 3.0, 6.0) * 10):
            fft = np.zeros(0, dtype=np.float32) if tick % 2 else self.fft
            effect.draw(self.surface, self.waveform, fft, beat, tick)
            self.assertTrue(np.isfinite(effect._field).all())
            self.assertLessEqual(len(effect._px), effect.MAX_PARTICLES)
            self.assertLessEqual(len(effect._py), effect.MAX_PARTICLES)
        self.assertNotEqual(self.surface.get_bounding_rect().width, 0)

    def test_phason_reuses_bounded_quasiperiodic_field(self):
        effect = Phason()
        field = effect._field
        for tick, beat in enumerate((0.0, 3.0, 6.0) * 12):
            effect.draw(self.surface, self.waveform, self.fft, beat, tick)
            self.assertTrue(np.isfinite(effect._field).all())
            self.assertLessEqual(float(np.abs(effect._field).max()), effect.MAX_FIELD)
            self.assertLessEqual(effect.MAX_WAVES, 11)
        self.assertIs(effect._field, field)
        self.assertNotEqual(self.surface.get_bounding_rect().width, 0)

    def test_tesseract_cycles_finite_bounded_projections(self):
        effect = Tesseract()
        for tick, beat in enumerate((0.0, 3.0, 0.0, 6.0) * 4):
            effect.draw(self.surface, self.waveform, self.fft, beat, tick)
            self.assertLessEqual(len(effect._vertices), effect.MAX_VERTICES)
            self.assertLessEqual(len(effect._edges), effect.MAX_EDGES)
            self.assertTrue(np.isfinite(effect._projected).all())
        self.assertEqual(effect._preset, 2)
        self.assertNotEqual(self.surface.get_bounding_rect().width, 0)

    def test_ferrofluid_field_handles_poles_and_strong_beats(self):
        effect = Ferrofluid()
        for tick, beat in enumerate((0.0, 3.0, 6.0) * 12):
            effect.draw(self.surface, self.waveform, self.fft, beat, tick)
            self.assertTrue(np.isfinite(effect._field).all())
            self.assertLessEqual(float(np.abs(effect._field).max()), effect.MAX_FIELD)
            self.assertEqual(len(effect._poles), effect.MAX_POLES)
        effect._poles[0] = effect._poles[1]
        effect.draw(self.surface, self.waveform, self.fft, 0.0, 100)
        self.assertTrue(np.isfinite(effect._field).all())
        self.assertNotEqual(self.surface.get_bounding_rect().width, 0)

    def test_mandelbox_escape_field_is_finite_and_bounded(self):
        effect = Mandelbox()
        for tick, beat in enumerate((0.0, 3.0, 6.0) * 8):
            effect.draw(self.surface, self.waveform, self.fft, beat, tick)
            self.assertTrue(np.isfinite(effect._field).all())
            self.assertTrue(np.isfinite(effect._orbit).all())
            self.assertLessEqual(float(effect._field.max()), effect.MAX_FIELD)
            self.assertLessEqual(effect.MAX_ITERATIONS, 18)
        self.assertNotEqual(self.surface.get_bounding_rect().width, 0)

    def test_fireworks_flow_field_preserves_caps_and_trail(self):
        old_size = (config.WIDTH, config.HEIGHT)
        old_low_spec = getattr(config, "LOW_SPEC", False)
        config.WIDTH, config.HEIGHT = 96, 72
        config.LOW_SPEC = True
        target = pygame.Surface((96, 72))
        effect = Fireworks()
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
        config.WIDTH, config.HEIGHT = old_size
        config.LOW_SPEC = old_low_spec


if __name__ == "__main__":
    unittest.main()
