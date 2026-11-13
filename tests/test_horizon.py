import unittest

import numpy as np
import pygame

from effects import MODES
from effects.horizon import Horizon
from effects.utils import spectrum_bands, track_levels
from tests.render_host import RenderHost


class HorizonTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        pygame.init()
        pygame.display.set_mode((320, 240))

    @classmethod
    def tearDownClass(cls):
        pygame.quit()

    def setUp(self):
        self.host = RenderHost()
        self.host.mid, self.host.treble = 0.4, 0.6
        self.surface = pygame.Surface((320, 240))
        self.waveform = np.zeros(self.host.audio.block_size, dtype=np.float32)
        self.silence = np.zeros(self.host.audio.block_size // 2, dtype=np.float32)
        self.loud = np.linspace(1.0, 0.2, len(self.silence), dtype=np.float32)

    def test_registry_places_horizon_before_the_diagnostic_pair(self):
        names = [name for name, _ in MODES]
        self.assertEqual(names[-5:], ['Horizon', 'Caustics', 'Lanterns', 'Spectrum', 'Waterfall'])
        self.assertEqual(names.index('Horizon'), 31)

    def test_silence_still_shows_a_travelling_landscape(self):
        effect = self.host.create(Horizon)
        start = effect.time
        for tick in range(30):
            effect.draw(self.surface, self.waveform, self.silence, 0.0, tick)
        self.assertGreater(effect.time, start)
        pixels = pygame.surfarray.array3d(self.surface).max(axis=2)
        self.assertGreater((pixels > 3).mean(), 0.9)
        self.assertGreater(float(effect._rows.max()), 0.1)

    def test_valley_stays_flat_and_music_raises_new_ridges(self):
        quiet, loud = self.host.create(Horizon), self.host.create(Horizon)
        for tick in range(600):
            quiet.draw(self.surface, self.waveform, self.silence, 0.0, tick)
            loud.draw(self.surface, self.waveform, self.loud, 0.0, tick)
        centre = len(loud._t) // 2
        self.assertEqual(float(np.abs(loud._rows[:, centre - 1:centre + 1]).max()), 0.0)
        self.assertGreater(float(loud._rows.mean()), float(quiet._rows.mean()) * 1.5)

    def test_a_row_keeps_its_shape_while_it_travels(self):
        effect = self.host.create(Horizon)
        effect.draw(self.surface, self.waveform, self.loud, 0.0, 0)
        newest = effect._newest
        shape = effect._rows[newest % len(effect._rows)].copy()
        for tick in range(1, 120):
            effect.draw(self.surface, self.waveform, self.silence, 0.0, tick)
        self.assertGreater(effect._newest, newest)
        self.assertLess(effect._newest - newest, effect.N_ROWS)
        np.testing.assert_array_equal(effect._rows[newest % len(effect._rows)], shape)

    def test_ride_follows_bass_and_rows_stay_bounded_after_a_jump(self):
        quiet, loud = self.host.create(Horizon), self.host.create(Horizon)
        for tick in range(30):
            quiet.draw(self.surface, self.waveform, self.silence, 0.0, tick)
            loud.draw(self.surface, self.waveform, self.silence, 1.5, tick)
        self.assertGreater(loud.time, quiet.time)
        loud.time += 10_000.0
        loud.draw(self.surface, self.waveform, self.loud, 6.0, 31)
        self.assertEqual(loud._rows.shape, (Horizon.N_ROWS + 2, 2 * (Horizon.N_SIDE + 1)))
        self.assertTrue(np.isfinite(loud._rows).all())

    def test_infinite_fft_reads_as_silence(self):
        bad = self.loud.copy()
        bad[3] = np.inf
        bad[40] = -np.inf
        self.assertTrue(np.isfinite(spectrum_bands(bad.astype(np.float64), 22)).all())
        self.assertEqual(float(spectrum_bands(np.full(64, np.inf), 6).max()), 0.0)
        # FrameContext already rejects such input; the helper stays safe for
        # direct callers too.
        effect = self.host.create(Horizon)
        levels, peak = track_levels(np.zeros(22), 1e-3, bad, effect._smooth)
        self.assertLess(peak, 10.0)
        self.assertTrue(np.isfinite(levels).all())

    def test_wide_frames_show_terrain_to_the_edges(self):
        effect = self.host.create(Horizon)
        target = pygame.Surface((1200, 300))
        for tick in range(5):
            effect.draw(target, self.waveform, self.loud, 0.0, tick)
        pixels = pygame.surfarray.array3d(target)
        horizon = int(300 * Horizon.HORIZON)
        for x in (2, 1197):
            column = pixels[x, horizon + 3:horizon + 40]
            self.assertGreater(len({tuple(p) for p in column}), 3)

    def test_odd_viewports_and_empty_spectrum_render(self):
        effect = self.host.create(Horizon)
        for size in ((400, 1200), (1200, 400), (16, 16)):
            target = pygame.Surface(size)
            effect.draw(target, self.waveform, np.zeros(0, dtype=np.float32), 6.0, 0)
            self.assertNotEqual(target.get_bounding_rect().width, 0)
        self.assertTrue(np.isfinite(effect._spectrum).all())


if __name__ == "__main__":
    unittest.main()
