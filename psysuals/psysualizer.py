#!/usr/bin/env python3
"""
Music Visualizer — real-time audio -> visuals.

Controls:
  SPACE / click   Switch to next mode
  1-9             Jump to modes 1-9
  Left/Right      Cycle modes (or adjust pane slider when pane is open)
  Up/Down         Adjust intensity (or navigate pane sliders when pane is open)
  Tab             Toggle real-time settings pane
  P               Save current state as a preset
  Shift+P         Cycle through saved presets
  A               Toggle auto-gain (or cycle child mode backward in span mode)
  B               Toggle background layer
  Shift+B         Cycle background effect
  M               Tap tempo (tap 2+ times to lock BPM for 8s)
  Shift+M         Toggle span mode (multi-monitor extension)
  D               Open device picker (or cycle child mode forward in span mode)
  F               Toggle fullscreen
  H               Toggle HUD visibility
  Shift+H         Cycle HUD detail level
  Q / ESC         Quit
"""

from __future__ import annotations

__version__ = "3.14.0"

import argparse
import atexit
import math
import os
import sys
import time as _time
import signal
from collections import deque

import numpy as np
import pygame

import config
import settings as sett
from core.audio_engine import AudioEngine
from core.display_manager import DisplayManager
from core.ui_manager import UIManager
from effects import MODES
from effects.palette import palette

_CROSSFADE_FRAMES = 45
_BG_MODES = 9

class VisualizerApp:
    def __init__(self):
        self._quit_requested = False
        self.args = self._parse_args()
        config.LOW_SPEC = self.args.low_spec
        if config.LOW_SPEC:
            config.FPS = 30
        self.settings = sett.load()
        self.fade_alpha = 28
        
        self.display = DisplayManager(self.args)
        self.audio = AudioEngine()
        
        self._init_display()
        self._setup_signals()
        atexit.register(self.display.kill_children)
        
        self.ui = UIManager()
        self._init_audio()
        
        self.mode_idx = max(0, min(self.settings.get("mode_idx", 0), len(MODES) - 1))
        if self.args.mode is not None:
            self.mode_idx = self.args.mode % len(MODES)
        
        self.name, self.VisCls = MODES[self.mode_idx]
        self.vis = self.VisCls(renderer=self.display.renderer)
        
        self.using_tap = False
        self._fade_surf = None
        self._ui_surface = None
        self._present_surface = None
        # Initialize target resolution based on effect
        self._update_target_res()
        
        self.bg_on = self.settings.get("bg_on", False)
        self.bg_mode_i = self.settings.get("bg_mode_i", 0) % _BG_MODES
        self.bg_name, self.bg_cls = MODES[self.bg_mode_i]
        self.bg_vis = self.bg_cls(renderer=self.display.renderer)
        self.bg_surf = pygame.Surface((config.WIDTH, config.HEIGHT))
        self.bg_alpha = self.settings.get("bg_alpha", 102)
        
        self.prev_surf = None
        self.prev_surf_scaled = None
        self.crossfade_frame = 0
        self.cf_frames = self.settings.get("cf_frames", _CROSSFADE_FRAMES)
        
        self.tick = 0
        self.energy_hist = deque(maxlen=40)
        self.energy_sum = 0.0
        self.beat_decay = 0.0
        self.effect_gain = self.settings.get("effect_gain", config.DEFAULT_EFFECT_GAIN)
        self.current_genre = "detecting..."
        self._genre_check_cd = 0
        self.silence_frames = 0
        self.is_silent = True
        
        self.hud_level = self.settings.get("hud_level", 2)
        self.show_hud = self.settings.get("show_hud", self.hud_level > 0)
        
        self.auto_gain = self.settings.get("auto_gain", False)
        self.target_rms = 0.05
        self.rms_buf = deque([self.target_rms], maxlen=30)
        
        self.tap_times = deque(maxlen=4)
        self.tap_bpm = 0.0
        self.tap_bpm_expiry = 0.0
        self.tap_flash_end = 0.0
        
        self.span_vis2_idx = (self.mode_idx + 1) % len(MODES)
        self.span_mode = len(self.display.xmonitors) >= 2 and not self.args.span_child
        if self.span_mode:
            self.display.spawn_span_children(self.span_vis2_idx, os.path.abspath(__file__))
            
        self.presets = sett.load_presets()
        self.active_preset = -1
        self.dev_name_cache = {}
        
        self.clock = pygame.time.Clock()
        self.fade = self._make_fade(self.fade_alpha)

    def _setup_signals(self):
        def _sig_handler(sig, frame):
            # Do not tear down pygame from inside a draw callback. Request
            # shutdown and let the main loop clean up after the frame returns.
            self._quit_requested = True
        signal.signal(signal.SIGINT, _sig_handler)
        signal.signal(signal.SIGTERM, _sig_handler)

    def _parse_args(self):
        desc = "psysuals — The Ultimate Psychedelic Music Visualizer v" + __version__ + "\n\n"
        desc += "Controls:\n"
        desc += "  Space / Click   Cycle modes\n"
        desc += "  1-9             Jump to mode\n"
        desc += "  Arrows          Intensity / Settings / Modes\n"
        desc += "  Tab             Toggle settings pane\n"
        desc += "  F               Toggle fullscreen\n"
        desc += "  H / Shift+H     HUD visibility / Detail\n"
        desc += "  M / Shift+M     Tap Tempo / Span Mode\n"
        desc += "  D               Device picker / Span cycle\n"
        desc += "  Q / Esc         Quit"
        
        parser = argparse.ArgumentParser(
            description=desc,
            formatter_class=argparse.RawDescriptionHelpFormatter
        )
        parser.add_argument("-d", "--display", type=int, default=None, help="Target display index (e.g. 0, 1)")
        parser.add_argument("-m", "--mode", type=int, default=None, help="Starting mode index (0-26)")
        parser.add_argument("-g", "--gl", action="store_true", help="Enable ModernGL hardware acceleration")
        parser.add_argument("--low-spec", action="store_true", help="Optimize performance for low-end systems (lowers FPS and particle counts)")
        parser.add_argument("--span-child", action="store_true", help=argparse.SUPPRESS)
        return parser.parse_args()

    def _init_display(self):
        requested_display = (self.settings.get("display_idx", 0) 
                           if self.args.display is None else self.args.display)
        display_idx = max(0, min(requested_display, self.display.num_displays - 1))
        pygame.init()
        self.display.open_display(display_idx, True)
        pygame.display.set_caption(f"psysuals v{__version__}")

    def _init_audio(self):
        active_dev = self.settings.get("active_dev")
        devices = self.audio.input_devices()
        if active_dev is not None and active_dev not in [d[0] for d in devices]:
            active_dev = None
        self.audio.open_input_stream(active_dev, None)

    def _update_target_res(self):
        div = 1
        if hasattr(self, "vis") and self.vis is not None:
            if hasattr(self.vis, "_render_div"):
                div = self.vis._render_div()
            else:
                div = getattr(self.vis, "RES_DIV", 1)
        
        if not self.args.gl:
            self.display.target = self.display.screen
            return

        tw, th = config.WIDTH // div, config.HEIGHT // div
        if self.display.target is None or self.display.target.get_size() != (tw, th):
            self.display.target = pygame.Surface((tw, th), pygame.SRCALPHA)
            self.fade = self._make_fade(self.fade_alpha)

    def _make_fade(self, alpha: int):
        size = self.display.target.get_size()
        if self._fade_surf is None or self._fade_surf.get_size() != size:
            self._fade_surf = pygame.Surface(size, pygame.SRCALPHA)
        self._fade_surf.fill((0, 0, 0, alpha))
        return self._fade_surf

    def _set_background_mode(self, mode_i: int) -> None:
        old_bg = getattr(self, "bg_vis", None)
        if old_bg is not None and hasattr(old_bg, "release"):
            old_bg.release()
        self.bg_mode_i = mode_i % _BG_MODES
        self.bg_name, self.bg_cls = MODES[self.bg_mode_i]
        self.bg_vis = self.bg_cls(renderer=self.display.renderer)

    def _quit(self):
        self._quit_requested = True

    def _cleanup(self):
        if getattr(self, "_cleanup_done", False):
            return
        self._cleanup_done = True
        self._save_settings()
        for effect in (getattr(self, "vis", None), getattr(self, "bg_vis", None)):
            release = getattr(effect, "release", None)
            if callable(release):
                try:
                    release()
                except Exception:
                    pass
        if hasattr(self, "display") and self.display is not None:
            self.display.kill_children()
            if self.display.renderer:
                self.display.renderer.release()
        if hasattr(self, "audio"):
            self.audio.release()
        try:
            pygame.display.set_mode((1, 1))
        except Exception:
            pass
        pygame.quit()

    def _save_settings(self):
        if self.args.span_child:
            return
        sett.save({
            "active_dev": self.audio.active_dev,
            "mode_idx": self.mode_idx,
            "show_hud": self.show_hud,
            "auto_gain": self.auto_gain,
            "bg_on": self.bg_on,
            "bg_mode_i": self.bg_mode_i,
            "display_idx": self.display.display_idx,
            "bg_alpha": self.bg_alpha,
            "cf_frames": self.cf_frames,
            "hud_level": self.hud_level,
            "effect_gain": self.effect_gain,
        })

    def _switch_mode(self, new_idx: int):
        if hasattr(self.vis, "release") and callable(self.vis.release):
            self.vis.release()
        if not (self.args.gl and self.vis.IS_GL):
            self.prev_surf = self.display.target.copy()
        else:
            self.prev_surf = None
        self.display.target.fill((0, 0, 0))
        self.prev_surf_scaled = None
        self.crossfade_frame = 0
        self.mode_idx = new_idx % len(MODES)
        self.name, self.VisCls = MODES[self.mode_idx]
        self.vis = self.VisCls(renderer=self.display.renderer)
        self.effect_gain = config.DEFAULT_EFFECT_GAIN
        self._update_target_res()

    def _rebuild_effects(self, force: bool = False):
        prev_size = (config.WIDTH, config.HEIGHT)
        if not force and getattr(self, "_last_rebuild_size", None) == prev_size:
            return
        self._last_rebuild_size = prev_size
        if hasattr(self.vis, "release") and callable(self.vis.release):
            self.vis.release()
        if hasattr(self.bg_vis, "release") and callable(self.bg_vis.release):
            self.bg_vis.release()
        self.vis = self.VisCls(renderer=self.display.renderer)
        self.bg_name, self.bg_cls = MODES[self.bg_mode_i]
        self.bg_vis = self.bg_cls(renderer=self.display.renderer)
        self.bg_surf = pygame.Surface((config.WIDTH, config.HEIGHT))
        self.prev_surf_scaled = None
        self._update_target_res()

    def _release_for_display_change(self):
        for name in ("vis", "bg_vis"):
            obj = getattr(self, name, None)
            if hasattr(obj, "release") and callable(obj.release):
                obj.release()
            setattr(self, name, None)
        renderer = getattr(self.display, "renderer", None)
        if renderer is not None and hasattr(renderer, "release"):
            renderer.release()
        self.display.renderer = None

    def run(self):
        try:
            while not self._quit_requested:
                self._handle_events()
                if self._quit_requested:
                    break
                self._update()
                if self._quit_requested:
                    break
            
                if self.args.gl and self.display.renderer:
                    self.display.renderer.ctx.screen.use()
                    self.display.renderer.ctx.clear(0.0, 0.0, 0.0, 1.0)
                    self.display.renderer.ctx.disable(self.display.renderer.ctx.BLEND)

                self._render()

                if self.args.gl and self.display.renderer:
                    present = self._present_surface if self._present_surface is not None else self.display.target
                    self.display.renderer.blit(present)
                    self.display.target.fill((0, 0, 0, 0))

                pygame.display.flip()
                self.clock.tick(config.FPS)
                self.tick += 1
        finally:
            self._cleanup()

    def _handle_events(self):
        for event in pygame.event.get():
            if event.type == pygame.QUIT:
                self._quit()
            
            elif event.type == pygame.KEYDOWN:
                if self.ui.picking:
                    if event.key == pygame.K_UP:
                        self.ui.pick_sel = (self.ui.pick_sel - 1) % max(1, len(self.audio.input_devices()))
                    elif event.key == pygame.K_DOWN:
                        self.ui.pick_sel = (self.ui.pick_sel + 1) % max(1, len(self.audio.input_devices()))
                    elif event.key in (pygame.K_ESCAPE, pygame.K_RETURN):
                        if event.key == pygame.K_RETURN:
                            devs = self.audio.input_devices()
                            if 0 <= self.ui.pick_sel < len(devs):
                                self.audio.start_input_stream(devs[self.ui.pick_sel][0])
                        self.ui.picking = False
                    continue

                if event.key in (pygame.K_ESCAPE, pygame.K_q):
                    self._quit()
                elif event.key == pygame.K_f:
                    self._release_for_display_change()
                    self.display.toggle_fullscreen()
                    self._rebuild_effects(force=True)
                elif event.key == pygame.K_SPACE:
                    self._switch_mode(self.mode_idx + 1)
                elif event.key == pygame.K_h:
                    if event.mod & pygame.KMOD_SHIFT:
                        self.hud_level = (self.hud_level + 1) % 3
                        self.show_hud = self.hud_level > 0
                    else:
                        self.show_hud = not self.show_hud
                elif event.key == pygame.K_TAB:
                    self.ui.pane_open = not self.ui.pane_open
                elif event.key == pygame.K_m:
                    if event.mod & pygame.KMOD_SHIFT:
                        if self.span_mode:
                            self.display.kill_children()
                            self.span_mode = False
                        elif not self.args.span_child:
                            self.display.spawn_span_children(self.span_vis2_idx, os.path.abspath(__file__))
                            self.span_mode = True
                    else:
                        t = _time.monotonic()
                        self.tap_times.append(t)
                        if len(self.tap_times) >= 2:
                            self.tap_bpm = 60.0 / np.median(np.diff(self.tap_times))
                            self.tap_bpm_expiry = t + 8.0
                            self.tap_flash_end = t + 0.5
                elif event.key == pygame.K_a:
                    if self.span_mode:
                        self.span_vis2_idx = (self.span_vis2_idx - 1) % len(MODES)
                        self.display.spawn_span_children(self.span_vis2_idx, os.path.abspath(__file__))
                    else:
                        self.auto_gain = not self.auto_gain
                elif event.key == pygame.K_d:
                    if self.span_mode:
                        self.span_vis2_idx = (self.span_vis2_idx + 1) % len(MODES)
                        self.display.spawn_span_children(self.span_vis2_idx, os.path.abspath(__file__))
                    else:
                        self.ui.picking = True
                        devs = self.audio.input_devices()
                        self.ui.pick_sel = next((i for i, d in enumerate(devs) if d[0] == self.audio.active_dev), 0)
                elif event.key == pygame.K_b:
                    if event.mod & pygame.KMOD_SHIFT:
                        self._set_background_mode(self.bg_mode_i + 1)
                    else:
                        self.bg_on = not self.bg_on
                elif event.key == pygame.K_p:
                    if event.mod & pygame.KMOD_SHIFT:
                        self.active_preset = (self.active_preset + 1) % max(1, len(self.presets))
                        if self.presets:
                            p = self.presets[self.active_preset]
                            self._switch_mode(p["mode_idx"])
                            self.effect_gain = p.get("intensity", 0.7)
                            self.bg_on = p.get("bg_on", False)
                            self._set_background_mode(p.get("bg_mode_i", 0))
                            self._update_target_res()
                    else:
                        preset_name = f"Preset {len(self.presets) + 1}"
                        entry = {"mode_idx": self.mode_idx, "intensity": self.effect_gain,
                                 "bg_on": self.bg_on, "bg_mode_i": self.bg_mode_i}
                        self.presets.append({"name": preset_name, **entry})
                        sett.save_preset(preset_name, entry)
                        self.active_preset = len(self.presets) - 1
                elif event.key == pygame.K_RIGHT:
                    if self.ui.pane_open:
                        self._pane_adjust(1)
                    else:
                        self._switch_mode(self.mode_idx + 1)
                elif event.key == pygame.K_LEFT:
                    if self.ui.pane_open:
                        self._pane_adjust(-1)
                    else:
                        self._switch_mode(self.mode_idx - 1)
                elif event.key == pygame.K_UP:
                    if self.ui.pane_open:
                        self.ui.pane_sel = (self.ui.pane_sel - 1) % 3
                    elif self.name == "FlowField" and hasattr(self.vis, "adjust_particles"):
                        self.vis.adjust_particles(2000)
                    else:
                        self.effect_gain = min(2.0, round(self.effect_gain + 0.1, 1))
                elif event.key == pygame.K_DOWN:
                    if self.ui.pane_open:
                        self.ui.pane_sel = (self.ui.pane_sel + 1) % 3
                    elif self.name == "FlowField" and hasattr(self.vis, "adjust_particles"):
                        self.vis.adjust_particles(-2000)
                    else:
                        self.effect_gain = max(0.0, round(self.effect_gain - 0.1, 1))
                elif pygame.K_1 <= event.key <= pygame.K_9:
                    self._switch_mode(event.key - pygame.K_1)
            
            elif event.type == pygame.MOUSEBUTTONDOWN and not self.ui.picking:
                self._switch_mode(self.mode_idx + 1)

    def _pane_adjust(self, delta: int):
        if self.ui.pane_sel == 0:
            self.effect_gain = min(2.0, max(0.0, round(self.effect_gain + delta * 0.1, 1)))
        elif self.ui.pane_sel == 1:
            self.bg_alpha = min(255, max(0, self.bg_alpha + delta * 5))
        elif self.ui.pane_sel == 2:
            self.cf_frames = min(90, max(0, self.cf_frames + delta * 5))

    def _update_genre(self) -> None:
        """Poll genre detection at a cadence appropriate to silence state."""
        self._genre_check_cd -= 1
        if self._genre_check_cd > 0:
            return
        detected = self.audio.detect_genre()
        if detected:
            self.current_genre = detected
            self.audio.apply_genre_weights(detected)
            palette.set_genre(detected)
        self._genre_check_cd = 60 if self.is_silent else 1

    def _compute_draw_beat(self) -> float:
        """Apply optional RMS normalization and the user-selected gain."""
        if self.auto_gain and self.rms_buf:
            cur_rms = float(np.mean(self.rms_buf)) + 1e-9
            auto_scale = max(0.5, min(self.target_rms / cur_rms, 2.0))
            return self.beat * auto_scale * self.effect_gain
        return self.beat * self.effect_gain

    def _update(self):
        self._update_genre()
            
        self.waveform, self.fft, raw_beat, mid_e, treble_e, bpm, audio_time = self.audio.get_audio()
        if self.audio.beat_tracker.enabled:
            bpm = self.audio.beat_tracker.analyze(fallback_bpm=bpm)
            raw_beat = self.audio.beat_tracker.refine_beat(raw_beat, audio_time)

        rms = float(np.sqrt(np.mean(self.waveform ** 2)))
        fft_mean = float(np.mean(self.fft)) if len(self.fft) else 0.0

        if self.is_silent:
            silent_now = (
                rms < config.SILENCE_RMS_EXIT
                and fft_mean < config.SILENCE_FFT_EXIT
            )
        else:
            silent_now = (
                rms < config.SILENCE_RMS_ENTER
                and fft_mean < config.SILENCE_FFT_ENTER
            )

        if silent_now:
            self.silence_frames += 1
        else:
            self.silence_frames = 0
            self.is_silent = False

        if self.silence_frames >= config.SILENCE_FRAMES_ENTER:
            self.is_silent = True

        if self.is_silent:
            raw_beat = 0.0
            # Gentle breathing/LFO modulation to keep the visuals alive and dynamic in no-input/silent mode
            t_sec = self.tick / 60.0
            lfo_mid = 0.5 + 0.5 * math.sin(t_sec * 1.2)
            lfo_treble = 0.5 + 0.5 * math.cos(t_sec * 0.9)
            lfo_beat = 0.5 + 0.5 * math.sin(t_sec * 2.0)
            
            mid_e = config.SILENCE_MID_FLOOR * (0.8 + 0.4 * lfo_mid)
            treble_e = config.SILENCE_TREBLE_FLOOR * (0.8 + 0.4 * lfo_treble)
            
            # Simulate a very gentle ambient pulse at config.BPM (or fallback 120)
            bpm_rate = (config.BPM or 120.0) / 60.0
            pulse = max(0.0, math.sin(t_sec * math.pi * bpm_rate)) ** 4
            silence_beat_floor = config.SILENCE_BEAT_FLOOR * (0.7 + 0.6 * pulse + 0.2 * lfo_beat)
            
            self.energy_hist.clear()
            self.energy_sum = 0.0
        else:
            silence_beat_floor = 0.0
            mid_e = max(mid_e, config.SILENCE_MID_FLOOR * 0.5)
            treble_e = max(treble_e, config.SILENCE_TREBLE_FLOOR * 0.5)
            
        config.MID_ENERGY = mid_e
        config.TREBLE_ENERGY = treble_e
        config.EFFECT_GAIN = self.effect_gain
        config.IS_SILENT = self.is_silent
        
        if self.tap_bpm > 0 and _time.monotonic() < self.tap_bpm_expiry:
            config.BPM = self.tap_bpm
            self.bpm = self.tap_bpm
            self.using_tap = True
        else:
            config.BPM = bpm
            self.bpm = bpm
            self.using_tap = False
            
        if len(self.energy_hist) == self.energy_hist.maxlen:
            self.energy_sum -= self.energy_hist[0]
        self.energy_hist.append(raw_beat)
        self.energy_sum += raw_beat
        avg = self.energy_sum / len(self.energy_hist) if self.energy_hist else 1e-6
        impulse = max(0.0, min(raw_beat / (avg + 1e-6) - 1.0, 3.0))
        self.beat_decay = max(impulse, self.beat_decay * (0.82 if self.is_silent else 0.90))
        self.beat = max(self.beat_decay, silence_beat_floor if self.is_silent else 0.0)
        
        self.rms_buf.append(rms)
        self.draw_beat = self._compute_draw_beat()
            
        palette.update(self.beat, mid_e, treble_e, self.tick)
        
        self.display.reposition_window_fix(self.tick)

        if self.span_mode:
            geometry_changed = self.tick % 60 == 0 and self.display.requery_xmonitors()
            if geometry_changed:
                print("  Monitor geometry changed, rebuilding span children...")
                self._release_for_display_change()
                self.display.open_display(self.display.display_idx, self.display.fullscreen)
                self._rebuild_effects(force=True)
                self.display.spawn_span_children(self.span_vis2_idx, os.path.abspath(__file__))
            else:
                for child_idx, child in list(self.display.span_children.items()):
                    if child.poll() is not None:
                        print(f"  Span child {child_idx} died, respawning...")
                        try:
                            child.wait(timeout=1)
                        except Exception:
                            pass
                        self.display.spawn_child(child_idx, self.span_vis2_idx, os.path.abspath(__file__))

        # sounddevice is imported in a daemon thread so a broken PortAudio
        # installation cannot block startup. Retry the initial stream once the
        # backend has finished loading.
        if self.audio.stream is None and self.audio.initial_stream_pending and self.tick % 30 == 0:
            self.audio.open_input_stream(self.settings.get("active_dev"), None)

    def _prepare_present_surface(self, target):
        """Upscale reduced GL effect targets before drawing display-resolution UI."""
        if self.args.gl and target.get_size() != self.display.screen.get_size():
            if self._ui_surface is None or self._ui_surface.get_size() != self.display.screen.get_size():
                self._ui_surface = pygame.Surface(self.display.screen.get_size(), pygame.SRCALPHA)
            self._ui_surface.fill((0, 0, 0, 0))
            pygame.transform.smoothscale(target, self.display.screen.get_size(), self._ui_surface)
            return self._ui_surface
        return target

    def _render(self):
        target = self.display.target
        
        genre_alpha = palette.trail_alpha if self.current_genre != "detecting..." else None
        new_alpha = genre_alpha if genre_alpha is not None else getattr(self.vis, "TRAIL_ALPHA", 28)
        if new_alpha != self.fade_alpha:
            self.fade_alpha = new_alpha
            self._make_fade(self.fade_alpha)
            
        is_gl_fg = self.args.gl and self.vis.IS_GL
        
        if not is_gl_fg:
            target.blit(self.fade, (0, 0))
        
        if self.bg_on and not is_gl_fg:
            self.bg_surf.fill((0, 0, 0))
            self.bg_vis.draw(self.bg_surf, self.waveform, self.fft, self.draw_beat, self.tick)
            self.bg_surf.set_alpha(self.bg_alpha)
            if target.get_size() != self.bg_surf.get_size():
                bg_scaled = pygame.transform.smoothscale(self.bg_surf, target.get_size())
                target.blit(bg_scaled, (0, 0))
            else:
                target.blit(self.bg_surf, (0, 0))
            
        self.vis.draw(target, self.waveform, self.fft, self.draw_beat, self.tick)
        
        if self.prev_surf and not is_gl_fg:
            tw, th = target.get_size()
            if self.prev_surf_scaled is None or self.prev_surf_scaled.get_size() != (tw, th):
                self.prev_surf_scaled = pygame.transform.scale(self.prev_surf, (tw, th))
            scaled_prev = self.prev_surf_scaled
            frames = max(1, int(self.cf_frames))
            t = self.crossfade_frame / frames
            ease = t * t * (3.0 - 2.0 * t)
            scaled_prev.set_alpha(int(255 * (1.0 - ease)))
            target.blit(scaled_prev, (0, 0))
            self.crossfade_frame += 1
            if self.crossfade_frame >= frames:
                scaled_prev.set_alpha(0)
                self.prev_surf = None
                self.prev_surf_scaled = None

        ui_target = self._prepare_present_surface(target)
        self._present_surface = ui_target

        now = _time.monotonic()
        if self.tap_bpm > 0 and now < self.tap_flash_end:
            self.ui.draw_tap_flash(ui_target, self.tap_bpm, int(180 * min(self.tap_flash_end - now, 1.0)))
            
        if self.show_hud:
            self._render_hud(ui_target)
            
        if self.ui.pane_open:
            self.ui.draw_pane(ui_target, self.effect_gain, self.bg_alpha, self.cf_frames)
            
        if self.ui.picking:
            self.ui.draw_device_picker(ui_target, self.audio.input_devices(), self.audio.active_dev)

    def _render_hud(self, target):
        self.ui.draw_multiband_bars(target, self.beat, config.MID_ENERGY, config.TREBLE_ENERGY)
        
        if self.audio.stream is None:
            dev_name = "no input"
        elif self.audio.active_dev is None:
            dev_name = "default"
        else:
            if self.audio.active_dev not in self.dev_name_cache:
                devs = self.audio.input_devices()
                self.dev_name_cache[self.audio.active_dev] = next((d[1] for d in devs if d[0] == self.audio.active_dev), "unknown")
            dev_name = self.dev_name_cache[self.audio.active_dev]
            
        fps = self.clock.get_fps()
        gl_tag = " | GL" if self.args.gl else ""
        title = f"psysuals v{__version__}  [{fps:.0f} fps{gl_tag}]"
        
        # Build text lines
        lines = []
        lines.append(self.ui.font.render(title, True, (220, 220, 220)))
        
        mode_text = f"Mode {self.mode_idx+1}: {self.name} ({self.effect_gain:.1f})"
        if self.bg_on:
            mode_text += f" + BG: {self.bg_name}"
        lines.append(self.ui.font.render(mode_text, True, (200, 200, 100)))
        
        if self.hud_level > 1:
            info = f"BPM: {self.bpm:.1f} ({self.current_genre})"
            if self.using_tap: info += " [TAP]"
            if self.auto_gain: info += " [AUTO]"
            lines.append(self.ui.font_s.render(info, True, (160, 160, 160)))
            lines.append(self.ui.font_s.render(f"Input: {dev_name}", True, (130, 130, 130)))
            
            if self.active_preset >= 0 and self.presets:
                pname = self.presets[self.active_preset]["name"]
                lines.append(self.ui.font_s.render(f"Preset: {pname}", True, (100, 200, 255)))
                
        self.ui.draw_hud_background(target, lines)
        
        # Render text lines onto target
        y = 10
        spacing = 3
        for line in lines:
            target.blit(line, (12, y))
            y += line.get_height() + spacing

if __name__ == "__main__":
    app = VisualizerApp()
    app.run()
