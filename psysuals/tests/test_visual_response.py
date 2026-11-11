"""Visual contracts: elapsed time, physical-display profiles and stable geometry."""
import math
import types
import unittest
from collections import Counter
from unittest import mock

import numpy as np
import pygame

from tests.render_host import RenderHost
from effects.base import Effect
from effects.aurora import Aurora
from effects.butterflies import Butterflies
from effects.fireworks import Fireworks
from effects.liquidlight import LiquidLight
from effects.persistence import Persistence
from effects.phason import Phason
from effects.plasma_gl import PlasmaGL
from effects.slimemold import SlimeMold
from effects.chromatic import Chromatic
from effects.cube import Cube
from effects.ferrofluid import Ferrofluid
from effects.gl_field import FullscreenField
from effects.hyperbolic import Hyperbolic
from effects.lattice import Lattice
from effects.lissajous import Lissajous
from effects.mobius import Mobius
from effects.synapse import Synapse
from effects.triflux import Attractor
from effects.tunnel import Tunnel
from psysualizer import VisualizerApp
from core.display_manager import DisplayManager
import settings


class VisualResponseTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        pygame.init()
        pygame.display.set_mode((320, 240))

    @classmethod
    def tearDownClass(cls):
        pygame.quit()

    def setUp(self):
        self.host = RenderHost()
        keys = ('width', 'height', 'initialized', 'dt', 'profile',
                'mid', 'treble', 'quality_scale', 'low_spec', 'bpm')
        self.saved = {key: getattr(self.host, key) for key in keys}
        self.host.width, self.host.height = 320, 240
        self.host.initialized = True
        self.host.dt = 1 / 60
        self.host.profile = 'laptop'
        self.host.mid, self.host.treble = .4, .6
        self.host.quality_scale, self.host.low_spec, self.host.bpm = 1., False, 0.
        self.surface = pygame.Surface((320, 240))
        self.waveform, self.fft = np.zeros(1024), np.zeros(512)

    def tearDown(self):
        for key, value in self.saved.items():
            setattr(self.host, key, value)

    def draw(self, effect, beat=0., tick=0, surface=None):
        effect.draw(surface if surface is not None else self.surface,
                    self.waveform, self.fft, beat, tick)

    def test_smoothing_has_equal_response_after_one_second(self):
        outcomes = []
        for fps in (30, 60, 120):
            self.host.dt = 1 / fps
            effect = self.host.create(Effect)
            for _ in range(fps):
                motion, light = effect._audio_response(1.)
            outcomes.append((motion.copy(), light.copy()))
        for motion, light in outcomes[1:]:
            np.testing.assert_allclose(motion, outcomes[0][0], atol=1e-12)
            np.testing.assert_allclose(light, outcomes[0][1], atol=1e-12)
        self.assertGreater(outcomes[0][1][0], outcomes[0][0][0])

    def test_motion_is_consistent_at_30_60_and_120_fps(self):
        for cls, attribute in ((Tunnel, 'time'), (Mobius, '_ry'), (Ferrofluid, '_time')):
            values = []
            for fps in (30, 60, 120):
                self.host.dt = 1 / fps
                effect = self.host.create(cls)
                for tick in range(fps):
                    self.draw(effect, .6, tick)
                values.append(getattr(effect, attribute))
            self.assertLess((max(values) - min(values)) / max(values), .015, cls.__name__)

    def test_lissajous_trail_duration_is_independent_of_fps(self):
        for fps in (30, 60, 120):
            self.host.dt = 1 / fps
            effect = self.host.create(Lissajous)
            for tick in range(fps):
                self.draw(effect, tick=tick)
            self.assertEqual(len(effect.hist), 60)

    def test_stalls_and_invalid_time_cannot_explode_motion(self):
        import types
        effect = self.host.create(Effect)
        for dt in (float('nan'), float('inf'), -1., 20.):
            if not math.isfinite(dt) or dt < 0:
                with self.assertRaisesRegex(ValueError, 'dt'):
                    self.host.dt = dt
            # The private helper also defends against a legacy/non-context caller.
            effect.set_frame_context(types.SimpleNamespace(dt=dt))
            self.assertTrue(math.isfinite(effect._frame_step()))
            self.assertGreaterEqual(effect._frame_step(), 0.)
            self.assertLessEqual(effect._frame_step(), 6.)

    def test_profiles_work_at_same_4k_resolution_and_change_live(self):
        self.host.width, self.host.height = 3840, 2160
        effect = self.host.create(Lattice)
        self.assertEqual(len(effect._nodes), 22 * 14)
        self.host.profile = 'tv'
        self.draw(effect)
        self.assertEqual(len(effect._nodes), 14 * 9)
        self.host.profile = 'laptop'
        self.draw(effect)
        self.assertEqual(len(effect._nodes), 22 * 14)

    def test_gl_canvas_matches_display_even_with_quality_reduction(self):
        app = self.host.app()
        app.args = types.SimpleNamespace(gl=True, low_spec=False)
        app.vis = self.host.create(Lattice)
        app._fade_surf = None
        app.fade_alpha = 48
        app.display = types.SimpleNamespace(screen=pygame.Surface((1280, 800)), target=None)
        for quality in (1., .5):
            self.host.quality_scale = quality
            app._update_target_res()
            self.assertEqual(app.display.target.get_size(), (1280, 800))
            self.assertIsNot(app.display.target, app.display.screen)

    def test_shader_resolution_tracks_live_framebuffer_and_offscreen_size(self):
        resolution = types.SimpleNamespace(value=None)
        program = mock.MagicMock()
        program.__getitem__.return_value = resolution
        renderer = types.SimpleNamespace(
            ctx=types.SimpleNamespace(screen=types.SimpleNamespace(size=(2560, 1600))),
            program=mock.Mock(return_value=(program, mock.Mock())),
            render=mock.Mock(),
        )
        field = FullscreenField('unused test shader')
        uniforms = {'u_resolution': (1280., 800.)}
        field.render(renderer, uniforms)
        self.assertEqual(resolution.value, (2560., 1600.))
        self.assertEqual(uniforms['u_resolution'], (1280., 800.))
        renderer.ctx.screen.size = (1920, 1080)
        field.render(renderer, uniforms)
        self.assertEqual(resolution.value, (1920., 1080.))
        field.render(renderer, uniforms, types.SimpleNamespace(width=400, height=300))
        self.assertEqual(resolution.value, (400., 300.))
        field.release()

    def test_display_uses_actual_sdl_size_instead_of_monitor_request(self):
        manager = DisplayManager.__new__(DisplayManager)
        manager.args = types.SimpleNamespace(gl=False)
        manager.renderer, manager._libX11 = None, None
        manager.xmonitors, manager.num_displays = [(0, 0, 2560, 1600)], 1
        with mock.patch('core.display_manager.pygame.display.set_mode',
                        return_value=pygame.Surface((1280, 800))):
            manager.open_display(0, True)
        self.assertEqual((manager.width, manager.height), (1280, 800))

    def test_cube_projection_has_same_relative_size_on_laptop_and_tv(self):
        relative = []
        for size in ((1280, 720), (3840, 2160)):
            effect = self.host.create(Cube)
            effect._canvas_size = size
            x, y = effect._project(np.array([1., 1., 0.]))
            relative.append(((x - size[0]/2)/size[1], (y - size[1]/2)/size[1]))
        np.testing.assert_allclose(relative[0], relative[1], atol=.002)

    def test_triflux_edges_have_partial_pixel_coverage(self):
        for width in (1, 5):
            surface = pygame.Surface((80, 80))
            effect = self.host.create(Attractor)
            effect._rainbow_edges(surface, [(10, 10), (67, 29), (20, 69)], width)
            pixels = pygame.surfarray.array3d(surface)
            # An AA edge has many coverage levels, rather than three flat edge colours.
            colors = np.unique(pixels.reshape(-1, 3), axis=0)
            self.assertGreater(len(colors), 30)

    def test_triflux_resize_replaces_old_grid(self):
        effect = self.host.create(Attractor)
        self.draw(effect)
        first = len(effect.tiles)
        self.draw(effect, surface=pygame.Surface((640, 480)))
        self.draw(effect)
        self.assertEqual(len(effect.tiles), first)
        self.assertTrue(all(i < len(effect.tiles) for i in effect.active_ids + effect.filled_ids))

    def test_synapse_fades_old_node_and_grows_near_parent(self):
        effect = self.host.create(Synapse)
        self.draw(effect)
        oldest = effect._anchors[0]
        initial = effect.n_nodes
        effect._topology_cd = 0
        self.draw(effect)
        self.assertTrue(effect._retiring)
        self.draw(effect)
        self.assertGreater(effect._visibility[0], 0.)
        self.assertLess(effect._visibility[0], 1.)
        for tick in range(65):
            self.draw(effect, tick=tick)
        self.assertEqual(effect.n_nodes, initial + 1)
        self.assertFalse(any(anchor is oldest for anchor in effect._anchors))
        self.assertTrue(all(0 < v < 1 for v in effect._visibility[-2:]))
        for anchor, origin in zip(effect._anchors[-2:], effect._birth_origins[-2:]):
            self.assertLess(np.linalg.norm(np.array(anchor) - origin), 240 * .12)

    def test_hyperbolic_tiles_share_edges_and_hide_back_faces(self):
        effect = self.host.create(Hyperbolic)
        edges = Counter()
        for tile in effect._tiles:
            points = [tuple(np.round(p, 8)) for p in tile]
            for a, b in zip(points, points[1:] + points[:1]):
                edges[tuple(sorted((a, b)))] += 1
        self.assertEqual(set(edges.values()), {2})
        self.draw(effect)
        self.assertGreater(len(effect._visible_tiles), 0)
        self.assertLess(len(effect._visible_tiles), len(effect._tiles))
        for tile in effect._visible_tiles:
            self.assertGreaterEqual(tile[:, 2].min(), 1 / 3.2 - 1e-12)

    def test_ferrofluid_resize_preserves_poles_and_has_finite_spikes(self):
        effect = self.host.create(Ferrofluid)
        poles = effect._poles.copy()
        self.draw(effect, 1.)
        self.host.width, self.host.height = 640, 360
        self.draw(effect, 1., surface=pygame.Surface((640, 360)))
        np.testing.assert_array_equal(poles, effect._poles)
        for field in (effect._field, effect._field_dx, effect._field_dy):
            self.assertTrue(np.isfinite(field).all())
        self.assertGreater(np.abs(effect._field_dx).max(), 1.)
        self.assertLessEqual(effect._mask.max(), 1.)

    def _composition_app(self, effect):
        app = self.host.app()
        app.args = types.SimpleNamespace(gl=True, low_spec=False)
        app.vis = effect
        app.display = types.SimpleNamespace(screen=self.surface, target=pygame.Surface((320,240), pygame.SRCALPHA))
        app.current_genre = 'detecting...'
        app._fade_surf = None
        app.fade_alpha = 48
        app.fade = app._make_fade(48)
        app.bg_on,app.prev_surf,app.postfx_mode = False,None,0
        app.bg_vis, app.bg_alpha = None, 102
        app.postfx = types.SimpleNamespace(release=lambda: None, renderer=None)
        app.effect_gain, app.cf_frames = .7, 45
        app.waveform,app.fft,app.draw_beat,app.tick = self.waveform,self.fft,0.,0
        app._ui_surface = None
        app.tap_bpm,app.show_hud = 0.,False
        app.ui = types.SimpleNamespace(pane_open=False,picking=False)
        return app

    def test_reported_cpu_effects_remain_visible_on_gl_composition(self):
        for cls in (Butterflies,Fireworks,Aurora,LiquidLight,SlimeMold):
            effect = self.host.create(cls)
            app = self._composition_app(effect)
            for tick in range(30):
                app.tick,app.draw_beat = tick, .9 if tick % 15 == 0 else .1
                app._render()
            pixels = pygame.surfarray.array3d(app._present_surface)
            alpha = pygame.surfarray.array_alpha(app._present_surface)
            self.assertGreater(int(pixels.sum()), 0, cls.__name__)
            self.assertEqual(int(alpha.min()), 255, cls.__name__)

    def test_fireworks_does_not_readback_gpu_during_cpu_composition(self):
        renderer = mock.Mock()
        effect = self.host.create(Fireworks, renderer=renderer)
        self.draw(effect,1.)
        renderer.offscreen.assert_not_called()
        renderer.feedback_transform.assert_not_called()
        renderer.read_pixels.assert_not_called()

    def test_display_change_preserves_grown_graph_instance_and_trails(self):
        effect = self.host.create(Synapse)
        self.draw(effect)
        effect._mutate_topology(320,240,True)
        app = self._composition_app(effect)
        app.bg_vis,app.bg_mode_i,app.VisCls = self.host.create(Synapse),0,Synapse
        app.display.renderer = None
        app.display.target.fill((20,40,80,255))
        anchors = np.array(effect._anchors)
        count = effect.n_nodes
        app._release_for_display_change()
        self.assertIs(app.vis,effect)
        self.host.width,self.host.height = 640,480
        app.display.screen = pygame.Surface((640,480))
        app._rebuild_effects(force=True)
        self.assertIs(app.vis,effect)
        effect._ensure_nodes(640,480)
        self.assertEqual(effect.n_nodes,count)
        np.testing.assert_allclose(effect._anchors,anchors*2)
        self.assertEqual(app.display.target.get_at((100,100))[:3],(20,40,80))

    def test_field_resize_preserves_dye(self):
        effect=self.host.create(LiquidLight)
        effect._dye.fill(.35)
        expected=effect._dye.copy()
        self.host.width,self.host.height=640,480
        self.surface = pygame.Surface((640,480))
        with mock.patch.object(effect,'_render'),mock.patch.object(effect,'_inject_vortex'),mock.patch.object(effect,'_advect'):
            self.draw(effect)
        self.assertGreater(float(effect._dye.mean()),.2)
        self.assertGreater(effect._dye.shape[0],expected.shape[0])

    def test_effect_change_drops_previous_frame_and_clears_gl_framebuffer(self):
        app=self._composition_app(self.host.create(Butterflies))
        app.display.target.fill((255,100,50,255))
        app._ui_surface=app.display.target.copy()
        app._present_surface=app.display.target
        app.bg_surf=app.display.target.copy()
        app.tick,app.cf_frames=0,45
        renderer=types.SimpleNamespace(ctx=mock.Mock())
        app.display.renderer=renderer
        app.postfx=mock.Mock()
        with mock.patch('psysualizer.MODES',[('Butterflies',Butterflies),('Tunnel',Tunnel)]):
            app._switch_mode(1)
        self.assertIsNone(app.prev_surf)
        self.assertIsNone(app._present_surface)
        self.assertEqual(int(pygame.surfarray.array3d(app.display.target).sum()),0)
        self.assertEqual(int(pygame.surfarray.array3d(app._ui_surface).sum()),0)
        renderer.ctx.screen.use.assert_called_once()
        renderer.ctx.clear.assert_called_once_with(0.,0.,0.,1.)
        app.postfx.release.assert_called_once()

    def test_phason_geometry_is_slow_and_wave_count_does_not_jump(self):
        effect=self.host.create(Phason)
        previous=None
        for tick in range(40):
            self.host.mid = 0. if tick%2 else 4.
            self.draw(effect,6. if tick%2 else 0.,tick)
            if previous is not None:
                self.assertLess(float(np.abs(effect._field-previous).max()),.02)
            previous=effect._field.copy()
            self.assertEqual(effect._gl_uniforms((320,240),0.,0.,0.,tick)['u_waves'],7)
        self.assertLess(effect._time,.3)

    def test_tunnel_limited_speed_and_no_audio_camera_wobble(self):
        effect=self.host.create(Tunnel)
        self.assertEqual(effect._path(1.,0.),effect._path(1.,6.))
        for tick in range(100):
            before=effect.time
            self.draw(effect,6.,tick)
            self.assertLess(effect.time-before,.013)
        self.assertEqual(len({ring['id'] for ring in effect.rings}),effect.N_RINGS)

    def test_persistence_audio_changes_direction_colour_and_speed(self):
        quiet=self.host.create(Persistence); reactive=self.host.create(Persistence)
        initial=np.array(reactive._rot_y)
        for tick in range(80):
            self.draw(quiet,0.,tick)
            self.draw(reactive,1.,tick)
        self.assertLess(reactive._direction[0],0.)
        self.assertGreater(abs(reactive._hue-quiet._hue),.02)
        self.assertGreater(np.linalg.norm(np.array(reactive._rot_y)-initial),np.linalg.norm(np.array(quiet._rot_y)-initial))
        self.assertEqual(len(reactive._models),5)

    def test_triflux_sustained_bass_activates_only_one_triangle(self):
        effect=self.host.create(Attractor)
        effect._auto_cd=10000
        for tick in range(25): self.draw(effect,1.,tick)
        self.assertEqual(len(effect.active_ids),1)
        self.draw(effect,0.,26)
        self.draw(effect,1.,27)
        self.assertEqual(len(effect.active_ids),2)

    def test_triflux_lifetime_and_rotation_follow_time(self):
        results=[]
        for fps in (30,60,120):
            self.host.dt=1/fps
            effect=self.host.create(Attractor); effect._auto_cd=10000
            self.draw(effect,1.)
            tile=effect.tiles[effect.active_ids[0]]
            life=tile['life']
            for tick in range(fps): self.draw(effect,0.,tick)
            results.append(life-tile['life'])
        np.testing.assert_allclose(results,[60.,60.,60.],atol=1e-8)

    def test_slime_tv_quality_keeps_fine_resolution_and_resize_keeps_agents(self):
        self.host.width,self.host.height=3840,2160
        self.host.quality_scale=.6
        size=self.host.bare(SlimeMold)._render_size()
        self.assertGreaterEqual(size[0],3264)
        self.assertGreaterEqual(size[1],1836)
        self.host.width,self.host.height,self.host.quality_scale=320,240,1.
        effect=self.host.create(SlimeMold)
        effect._trail.fill(.4)
        positions=effect._px.copy()
        angles=effect._ang.copy()
        old_w=effect._W
        self.host.width,self.host.height=640,480
        effect._reset_or_resize_sim()
        np.testing.assert_allclose(effect._px,positions*effect._W/old_w,rtol=1e-6)
        np.testing.assert_array_equal(effect._ang,angles)
        self.assertAlmostEqual(float(effect._trail.mean()),.4,places=5)

    def test_butterflies_are_visible_inside_canvas_and_keep_moving(self):
        effect=self.host.create(Butterflies)
        for tick in range(60): self.draw(effect,.1,tick)
        before=[(b.x,b.y) for b in effect._butterflies]
        for tick in range(60,180): self.draw(effect,.1,tick)
        w,h=effect._trail.get_size()
        self.assertTrue(effect._butterflies)
        for b in effect._butterflies:
            self.assertGreater(b.x,w*.03)
            self.assertLess(b.x,w*.97)
            self.assertGreater(b.y,h*.03)
            self.assertLess(b.y,h*.97)
            self.assertLess(b.scale*22,h*.20)
        distances=[math.hypot(b.x-x,b.y-y) for b,(x,y) in zip(effect._butterflies,before)]
        self.assertGreater(max(distances),h*.05)

    def test_plasma_audio_cannot_trigger_large_phase_or_warp_jumps(self):
        effect=self.host.create(PlasmaGL)
        for tick in range(40):
            previous_time,previous_warp=effect._time,effect._warp
            self.host.mid=6. if tick%2 else 0.
            self.host.treble=6. if tick%2 else 0.
            self.draw(effect,6. if tick%2 else 0.,tick)
            self.assertLess(effect._time-previous_time,.012)
            self.assertLess(abs(effect._warp-previous_warp),.004)

    def test_owned_feedback_does_not_accumulate_stale_maximum_colours(self):
        effect=self.host.create(LiquidLight)
        app=self._composition_app(effect)
        app.display.target.fill((255,0,0,255))
        with mock.patch.object(effect,'render'):
            app._render()
        self.assertEqual(app.display.target.get_at((10,10))[:3],(0,0,0))

    def test_hyperbolic_short_bass_pulse_has_visible_lift_and_recovers(self):
        from dataclasses import replace
        from core.context import EffectOptions, FrameContext
        frame = FrameContext(320, 240, profile='laptop')
        quiet, pulsed = [Hyperbolic(options=EffectOptions(320, 240, seed=7), frame=frame)
                         for _ in range(2)]
        quiet_surface, pulse_surface = pygame.Surface((320, 240)), pygame.Surface((320, 240))
        try:
            peak_delta = None
            for tick in range(90):
                quiet.render(quiet_surface, replace(frame, tick=tick))
                pulsed.render(pulse_surface, replace(frame, tick=tick, beat=1.2 if tick < 6 else 0.))
                delta = float(np.mean(pulsed._tile_heights - quiet._tile_heights))
                if tick == 3:
                    # A four-frame kick should move facets, not just animate an
                    # already-moving idle sphere. The former response was <.001.
                    self.assertGreater(delta, .015)
                if tick == 5:
                    peak_delta = delta
                    quiet_pixels = pygame.surfarray.array3d(quiet_surface).astype(float)
                    pulse_pixels = pygame.surfarray.array3d(pulse_surface).astype(float)
                    self.assertGreater(np.mean(np.abs(pulse_pixels - quiet_pixels)), 15.)
            self.assertLess(abs(delta), peak_delta * .1)
            self.assertAlmostEqual(pulsed._angle, quiet._angle)
        finally:
            quiet.release()
            pulsed.release()

    def test_hyperbolic_strong_audio_keeps_caps_inside_both_profile_viewports(self):
        from dataclasses import replace
        from core.context import EffectOptions, FrameContext
        for profile in ('laptop', 'tv'):
            with self.subTest(profile=profile):
                frame = FrameContext(320, 240, profile=profile, mid=6., treble=6.)
                effect = Hyperbolic(options=EffectOptions(320, 240, seed=7), frame=frame)
                try:
                    for tick in range(120):
                        effect.render(self.surface, replace(frame, tick=tick, beat=6.))
                        self.assertTrue(np.isfinite(effect._tile_heights).all())
                        self.assertGreaterEqual(effect._tile_heights.min(), -.10)
                        self.assertLessEqual(effect._tile_heights.max(), .18)
                        for cap in effect._projected_caps:
                            self.assertTrue(np.isfinite(cap).all())
                            self.assertGreaterEqual(cap[:, 0].min(), 4)
                            self.assertLessEqual(cap[:, 0].max(), 316)
                            self.assertGreaterEqual(cap[:, 1].min(), 4)
                            self.assertLessEqual(cap[:, 1].max(), 236)
                finally:
                    effect.release()

    def test_hyperbolic_segments_rise_sink_and_remain_inside_viewport(self):
        effect=self.host.create(Hyperbolic)
        first=None
        for tick in range(100):
            self.draw(effect,.8 if tick%30<3 else .05,tick)
            self.assertGreaterEqual(float(effect._tile_heights.min()),-.10)
            self.assertLessEqual(float(effect._tile_heights.max()),.18)
            if tick==20: first=effect._tile_heights.copy()
        self.assertGreater(np.ptp(effect._tile_heights),.06)
        self.assertGreater(np.max(np.abs(effect._tile_heights-first)),.025)
        self.assertGreater(effect._angle,.4)
        for cap in effect._projected_caps:
            self.assertGreaterEqual(cap[:,0].min(),4)
            self.assertLessEqual(cap[:,0].max(),316)
            self.assertGreaterEqual(cap[:,1].min(),4)
            self.assertLessEqual(cap[:,1].max(),236)

    def test_cube_motion_stays_bounded_under_loud_alternating_audio(self):
        effect=self.host.create(Cube)
        for tick in range(100):
            previous=np.array([effect.rx,effect.ry,effect.rz,effect.orb_angle])
            self.host.mid=self.host.treble=6. if tick%2 else 0.
            self.draw(effect,6. if tick%2 else 0.,tick)
            current=np.array([effect.rx,effect.ry,effect.rz,effect.orb_angle])
            self.assertLess(np.max(np.abs(current-previous)),.012)
            self.assertLessEqual(effect.scale,1.10)
            self.assertGreaterEqual(effect.scale,1.)

    def test_profile_settings_validate_and_cli_can_override(self):
        self.assertEqual(settings._normalise({'visual_profile': 'tv'})['visual_profile'], 'tv')
        self.assertEqual(settings._normalise({'visual_profile': []})['visual_profile'], 'auto')
        with mock.patch('sys.argv', ['psysualizer', '--visual-profile', 'laptop']):
            args = self.host.app()._parse_args()
        self.assertEqual(args.visual_profile, 'laptop')


if __name__ == '__main__':
    unittest.main()
