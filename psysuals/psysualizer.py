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

__version__ = "3.17.0"

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
from core.quality import QualityGovernor
from core.postprocess import PostProcessChain

_CROSSFADE_FRAMES = 45
_BG_MODES = 9
_PRESET_MORPH_BEATS = 8
RECIPES = (
    {"name": "Hyperbolic Liquid Cathedral", "foreground": "Hyperbolic", "background": "LiquidLight", "bg_alpha": 112},
    {"name": "Tesseract Persistence", "foreground": "Tesseract", "background": "Persistence", "bg_alpha": 96},
    {"name": "Cymatic Ferrofluid", "foreground": "Cymatica", "background": "Ferrofluid", "bg_alpha": 104},
    {"name": "Morphogenic Plasma", "foreground": "Morphogenesis", "background": "Plasma", "bg_alpha": 108},
    {"name": "Aurora Fireworks", "foreground": "Fireworks", "background": "Aurora", "bg_alpha": 118},
)

class VisualizerApp:
    def __init__(self):
        self._quit_requested = False
        self.args = self._parse_args()
        config.LOW_SPEC = self.args.low_spec
        if config.LOW_SPEC:
            config.FPS = 30
        self.settings = sett.load()
        # Faster global decay prevents stale grey trails accumulating between
        # frames; effect-owned trails use the same shorter-persistence policy.
        self.fade_alpha = 48
        
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
        self.recipe_idx = -1
        self.postfx_mode = self.settings.get("postfx_mode", 0) % len(PostProcessChain.MODES)
        self._preset_morph = None
        self._parameter_morph = None
        
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
        self.silence_frames = 0  # retained as a compatibility diagnostic
        self._silence_last_generation = None
        self._silence_last_audio_time = None
        self._silence_quiet_seconds = 0.0
        self._silence_loud_blocks = 0
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
        self._phase_anchor_tick = 0
        self._phase_last_onset = 0.0
        self._phase_anchor_time = 0.0
        self._phase_was_silent = True
        
        self.span_vis2_idx = (self.mode_idx + 1) % len(MODES)
        self.span_mode = len(self.display.xmonitors) >= 2 and not self.args.span_child
        if self.span_mode:
            self.display.spawn_span_children(self.span_vis2_idx, os.path.abspath(__file__))
            
        self.presets = sett.load_presets()
        self.active_preset = -1
        self.dev_name_cache = {}
        
        self.clock = pygame.time.Clock()
        self.quality = QualityGovernor(config.FPS)
        self.postfx = PostProcessChain(self.display.renderer)
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
        desc += "  Shift+R         Curated foreground/background recipe\n"
        desc += "  Shift+X         Cycle psychedelic post-process\n"
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

    def _apply_recipe(self, recipe_idx: int) -> None:
        """Select one curated foreground/background pairing explicitly."""
        recipe = RECIPES[recipe_idx % len(RECIPES)]
        mode_by_name = {name: index for index, (name, _) in enumerate(MODES)}
        foreground = mode_by_name.get(recipe["foreground"])
        background = mode_by_name.get(recipe["background"])
        if foreground is None or background is None:
            return
        if foreground != self.mode_idx:
            self._switch_mode(foreground)
        self._set_background_mode(background)
        self.bg_alpha = int(max(0, min(255, recipe["bg_alpha"])))
        self.bg_on = True
        self.recipe_idx = recipe_idx % len(RECIPES)

    def _start_preset_morph(self, preset: dict) -> None:
        """Begin a bounded preset blend; discrete mode changes occur midway."""
        bpm = float(getattr(self, "bpm", 0.0) or config.BPM or 0.0)
        if 60.0 <= bpm <= 200.0:
            duration = 60.0 / bpm * _PRESET_MORPH_BEATS * config.FPS
        else:
            duration = 4.0 * config.FPS
        self._preset_morph = {
            "start": self.tick, "duration": max(1, int(round(duration))), "switched": False,
            "mode_idx": int(preset.get("mode_idx", self.mode_idx)),
            "bg_mode_i": int(preset.get("bg_mode_i", self.bg_mode_i)),
            "bg_on": bool(preset.get("bg_on", self.bg_on)),
            "from_gain": float(self.effect_gain), "to_gain": float(preset.get("intensity", self.effect_gain)),
            "from_alpha": float(self.bg_alpha), "to_alpha": float(preset.get("bg_alpha", self.bg_alpha)),
            "from_cf": float(self.cf_frames), "to_cf": float(preset.get("cf_frames", self.cf_frames)),
        }

    def _advance_preset_morph(self) -> None:
        morph = self._preset_morph
        if morph is None:
            return
        progress = min(1.0, max(0.0, (self.tick - morph["start"]) / morph["duration"]))
        if not morph["switched"] and progress >= 0.5:
            self._switch_mode(morph["mode_idx"])
            self._set_background_mode(morph["bg_mode_i"])
            self.bg_on = morph["bg_on"]
            morph["switched"] = True
        self.effect_gain = morph["from_gain"] + (morph["to_gain"] - morph["from_gain"]) * progress
        self.bg_alpha = int(round(morph["from_alpha"] + (morph["to_alpha"] - morph["from_alpha"]) * progress))
        self.cf_frames = morph["from_cf"] + (morph["to_cf"] - morph["from_cf"]) * progress
        if progress >= 1.0:
            self._preset_morph = None

    def _advance_parameter_morph(self) -> None:
        morph = self._parameter_morph
        if morph is None:
            return
        progress = min(1.0, max(0.0, (self.tick - morph["start"]) / morph["duration"]))
        values = {
            name: source + (morph["target"][name] - source) * progress
            for name, source in morph["source"].items()
            if name in morph["target"]
        }
        self.vis.set_morph_values(values)
        if progress >= 1.0:
            self._parameter_morph = None

    def _quit(self):
        self._quit_requested = True

    def _cleanup(self):
        if getattr(self, "_cleanup_done", False):
            return
        self._cleanup_done = True
        if hasattr(self, "postfx"):
            self.postfx.release()
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
            "postfx_mode": self.postfx_mode,
        })

    def _switch_mode(self, new_idx: int):
        old_values = self.vis.get_morph_values() if hasattr(self.vis, "get_morph_values") else {}
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
        new_values = self.vis.get_morph_values() if hasattr(self.vis, "get_morph_values") else {}
        common = old_values.keys() & new_values.keys()
        if common:
            self._parameter_morph = {
                "start": self.tick,
                "duration": max(1, int(self.cf_frames)),
                "source": {name: old_values[name] for name in common},
                "target": {name: new_values[name] for name in common},
            }
        else:
            self._parameter_morph = None
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
        if hasattr(self, "postfx"):
            self.postfx.renderer = self.display.renderer
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
        if hasattr(self, "postfx"):
            self.postfx.renderer = None

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
                    if self.postfx_mode and self.vis.IS_GL:
                        self.display.renderer.postprocess_screen(
                            self.postfx, self.postfx_mode, min(1.0, self.effect_gain), self.tick
                        )
                    self.display.target.fill((0, 0, 0, 0))

                pygame.display.flip()
                self.quality.observe(self.clock.get_rawtime(), enabled=True)
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
                            self._phase_anchor_tick = self.tick
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
                elif event.key == pygame.K_r and event.mod & pygame.KMOD_SHIFT:
                    self._apply_recipe(self.recipe_idx + 1)
                elif event.key == pygame.K_x and event.mod & pygame.KMOD_SHIFT:
                    self.postfx_mode = (self.postfx_mode + 1) % len(PostProcessChain.MODES)
                elif event.key == pygame.K_p:
                    if event.mod & pygame.KMOD_SHIFT:
                        self.active_preset = (self.active_preset + 1) % max(1, len(self.presets))
                        if self.presets:
                            p = self.presets[self.active_preset]
                            self._start_preset_morph(p)
                    else:
                        preset_name = f"Preset {len(self.presets) + 1}"
                        entry = {"mode_idx": self.mode_idx, "intensity": self.effect_gain,
                                 "bg_on": self.bg_on, "bg_mode_i": self.bg_mode_i,
                                 "bg_alpha": self.bg_alpha, "cf_frames": self.cf_frames,
                                 "postfx_mode": self.postfx_mode}
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

    def _update_beat_phase(self, audio_time, bpm, onset_time):
        """Publish a bounded phase that reaches zero at predicted beat times."""
        try:
            now = float(audio_time)
            tempo = float(bpm)
            onset = float(onset_time)
        except (TypeError, ValueError):
            now = tempo = onset = float("nan")

        if self.is_silent:
            phase = (self.tick / max(1.0, config.FPS) * 0.25) % 1.0
        elif self.using_tap and np.isfinite(tempo) and tempo > 0:
            phase = ((self.tick - self._phase_anchor_tick) * tempo /
                     (60.0 * max(1.0, config.FPS))) % 1.0
        elif (
            np.isfinite(now) and np.isfinite(tempo) and np.isfinite(onset)
            and 60.0 <= tempo <= 200.0 and onset > 0.0
        ):
            if onset != self._phase_last_onset or self._phase_anchor_time <= 0.0:
                self._phase_last_onset = onset
                self._phase_anchor_time = onset
            if now < self._phase_anchor_time:
                self._phase_anchor_time = now
            phase = ((now - self._phase_anchor_time) * tempo / 60.0) % 1.0
        else:
            phase = (self.tick / max(1.0, config.FPS) * 0.25) % 1.0
        config.BEAT_PHASE = float(phase if np.isfinite(phase) else 0.0)

    def _update_silence_state(self, rms, fft_mean, audio_time, generation):
        """Update the silence gate once per newly published audio block."""
        if generation == self._silence_last_generation:
            return
        self._silence_last_generation = generation

        block_seconds = config.BLOCK_SIZE / config.SAMPLE_RATE
        try:
            current_audio_time = float(audio_time)
        except (TypeError, ValueError):
            current_audio_time = float("nan")
        if (
            self._silence_last_audio_time is not None
            and np.isfinite(current_audio_time)
            and np.isfinite(self._silence_last_audio_time)
            and current_audio_time > self._silence_last_audio_time
        ):
            elapsed = min(current_audio_time - self._silence_last_audio_time, 1.0)
        else:
            elapsed = block_seconds
        self._silence_last_audio_time = current_audio_time

        finite = np.isfinite(rms) and np.isfinite(fft_mean)
        if not finite:
            self._silence_quiet_seconds = 0.0
            self._silence_loud_blocks = 0
            return

        if self.is_silent:
            loud_now = rms >= config.SILENCE_RMS_EXIT or fft_mean >= config.SILENCE_FFT_EXIT
            if loud_now:
                self._silence_loud_blocks += 1
                self._silence_quiet_seconds = 0.0
                if self._silence_loud_blocks >= config.SILENCE_EXIT_BLOCKS:
                    self.is_silent = False
                    self._silence_loud_blocks = 0
            else:
                self._silence_loud_blocks = 0
            return

        quiet_now = rms < config.SILENCE_RMS_ENTER and fft_mean < config.SILENCE_FFT_ENTER
        if quiet_now:
            self._silence_quiet_seconds += elapsed
            if self._silence_quiet_seconds >= config.SILENCE_ENTER_SECONDS:
                self.is_silent = True
                self._silence_loud_blocks = 0
        else:
            self._silence_quiet_seconds = 0.0

    def _update(self):
        self._advance_preset_morph()
        self._advance_parameter_morph()
        self._update_genre()
            
        self.waveform, self.fft, raw_beat, mid_e, treble_e, bpm, audio_time = self.audio.get_audio()
        generation = getattr(self.audio, "get_audio_generation", lambda: audio_time)()
        envelopes = getattr(self.audio, "get_envelopes", lambda: (0.0, 0.0, 0.0))()
        if self.audio.beat_tracker.enabled:
            bpm = self.audio.beat_tracker.analyze(fallback_bpm=bpm)
            raw_beat = self.audio.beat_tracker.refine_beat(raw_beat, audio_time)

        rms = float(np.sqrt(np.mean(self.waveform ** 2)))
        fft_mean = float(np.mean(self.fft)) if len(self.fft) else 0.0

        was_silent = self.is_silent
        self._update_silence_state(rms, fft_mean, audio_time, generation)
        if was_silent and not self.is_silent:
            self._phase_last_onset = 0.0
            self._phase_anchor_time = 0.0

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
        if self.is_silent:
            config.BASS_ENVELOPE = config.MID_ENVELOPE = config.TREBLE_ENVELOPE = 0.0
        else:
            config.BASS_ENVELOPE = float(np.clip(envelopes[0], 0.0, 6.0))
            config.MID_ENVELOPE = float(np.clip(envelopes[1], 0.0, 6.0))
            config.TREBLE_ENVELOPE = float(np.clip(envelopes[2], 0.0, 6.0))
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

        timing = getattr(self.audio, "get_beat_timing", lambda: (bpm, 0.0))()
        self._update_beat_phase(audio_time, self.bpm, timing[1])
            
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
        new_alpha = genre_alpha if genre_alpha is not None else getattr(self.vis, "TRAIL_ALPHA", 48)
        new_alpha = int(round(new_alpha))
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

            field = getattr(self.bg_vis, "get_motion_field", lambda: None)()
            setter = getattr(self.vis, "set_motion_field", None)
            if callable(setter):
                setter(field)
        elif callable(getattr(self.vis, "set_motion_field", None)):
            self.vis.set_motion_field(None)
            
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

        if not is_gl_fg and self.postfx_mode:
            self.postfx.apply(target, self.postfx_mode, min(1.0, self.effect_gain), self.tick)

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
