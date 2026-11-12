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
  Shift+P         Morph to the next saved preset
  A               Toggle auto-gain (or cycle child mode backward in span mode)
  B               Toggle background layer
  Shift+B         Cycle background effect
  M               Tap tempo (tap 2+ times to lock BPM for 8s)
  Shift+M         Toggle span mode (multi-monitor extension)
  D               Open device picker (or cycle child mode forward in span mode)
  F               Toggle fullscreen
  V               Cycle Auto / Laptop / TV visual profile
  H               Toggle HUD visibility
  Shift+H         Cycle HUD detail level
  Q / ESC         Quit
"""

from __future__ import annotations

__version__ = "3.21.0"

import atexit
import os
import time as _time
import signal
import sys
import threading
from collections import deque
from dataclasses import replace
from core.audio_response import AudioResponse

import pygame

from core.context import AudioConfig, EffectOptions, FrameContext, parse_seed
import settings as sett
from core.cli import parse_args
from core.frame_composer import FrameComposer, FrameInputs, composition_state
from core.input_controller import handle_events
from core.transitions import PresetTransition, ParameterTransition
from core.silence import SilenceGate, SilenceThresholds
from core.audio_engine import AudioEngine
from core.display_manager import DisplayManager
from core.ui_manager import UIManager, HUDState
from effects import MODES
from effects.palette import ColorPalette
from core.quality import QualityGovernor
from core.postprocess import PostProcessChain

_CROSSFADE_FRAMES = 45
_BG_MODES = 9
_PRESET_MORPH_BEATS = 8
RECIPES = (
    {"name": "Hyperbolic Liquid Cathedral", "foreground": "Hyperbolic", "background": "LiquidLight", "bg_alpha": 112},
    {"name": "Tesseract Persistence", "foreground": "Tesseract", "background": "Persistence", "bg_alpha": 96},
    {"name": "Cymatic Ferrofluid", "foreground": "Cymatica", "background": "Ferrofluid", "bg_alpha": 104},
    {"name": "Aurora Fireworks", "foreground": "Fireworks", "background": "Aurora", "bg_alpha": 118},
)

class VisualizerApp:
    @property
    def _frames(self):
        # Lazy construction keeps historical __new__-based integration hosts valid.
        if "_frame_composer" not in self.__dict__:
            self._frame_composer = FrameComposer()
        return self._frame_composer

    fade_alpha = composition_state("fade_alpha")
    fade = composition_state("fade")
    _fade_surf = composition_state("_fade_surf")
    _ui_surface = composition_state("_ui_surface")
    _present_surface = composition_state("_present_surface")
    bg_surf = composition_state("bg_surf")
    prev_surf = composition_state("prev_surf")
    prev_surf_scaled = composition_state("prev_surf_scaled")
    crossfade_frame = composition_state("crossfade_frame")
    _display_snapshot = composition_state("_display_snapshot")

    def __init__(self):
        self._initialized = False
        try:
            self._initialize()
            self._initialized = True
        except BaseException:
            if hasattr(self, "display") or hasattr(self, "audio"):
                self._cleanup()
            raise

    def _initialize(self):
        self._quit_requested = False
        self.args = self._parse_args()
        self.fps = 30 if self.args.low_spec else 60
        self.audio_config = AudioConfig()
        self.seed = parse_seed(os.environ.get("PSYSUALS_SEED"))
        self.palette = ColorPalette()
        self.settings = sett.load()
        self.visual_profile = self.args.visual_profile or self.settings["visual_profile"]
        self.args.visual_profile = self.visual_profile
        # Faster global decay prevents stale grey trails accumulating between
        # frames; effect-owned trails use the same shorter-persistence policy.
        self.fade_alpha = 48
        
        self.display = DisplayManager(self.args)
        self.audio = AudioEngine(self.audio_config)
        
        self._init_display()
        self.frame = FrameContext(*self.display.screen.get_size(), fps=self.fps, profile=self.visual_profile)
        self._setup_signals()
        atexit.register(self.display.kill_children)
        
        self.ui = UIManager(self.frame.width)
        self._init_audio()
        
        self.mode_idx = max(0, min(self.settings.get("mode_idx", 0), len(MODES) - 1))
        if self.args.mode is not None:
            self.mode_idx = self.args.mode % len(MODES)
        
        self.name, self.VisCls = MODES[self.mode_idx]
        self.vis = self._create_effect(self.VisCls)
        
        self.using_tap = False
        self._fade_surf = None
        self._ui_surface = None
        self._present_surface = None
        # Initialize target resolution based on effect
        self._update_target_res()
        
        self.bg_on = self.settings.get("bg_on", False)
        self.bg_mode_i = self.settings.get("bg_mode_i", 0) % len(MODES)
        self.bg_name, self.bg_cls = MODES[self.bg_mode_i]
        self.bg_vis = self._create_effect(self.bg_cls)
        self.bg_surf = pygame.Surface((self.frame.width, self.frame.height))
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
        self.effect_gain = self.settings.get("effect_gain", .7)
        self.current_genre = "detecting..."
        self._genre_check_cd = 0
        self.silence_frames = 0  # retained as a compatibility diagnostic
        self.silence_gate = SilenceGate(
            SilenceThresholds(), self.audio_config.block_size / self.audio_config.sample_rate)
        self.response = AudioResponse(self.silence_gate)
        self.is_silent = True
        
        self.hud_level = self.settings.get("hud_level", 2)
        self.show_hud = self.settings.get("show_hud", self.hud_level > 0)
        
        self.auto_gain = self.settings.get("auto_gain", False)
        
        self.tap_times = deque(maxlen=4)
        self.tap_bpm = 0.0
        self.tap_bpm_expiry = 0.0
        self.tap_flash_end = 0.0
        self._phase_anchor_tick = 0
        
        self.span_vis2_idx = (self.mode_idx + 1) % len(MODES)
        # Multi-monitor span mode is opt-in. Start the primary app on one
        # screen so an extra monitor is never claimed unexpectedly; Shift+M
        # can still enable synchronized child windows explicitly.
        self.span_mode = False
            
        self.presets = sett.load_presets()
        self.active_preset = -1
        self.dev_name_cache = {}
        
        self.clock = pygame.time.Clock()
        self.quality = QualityGovernor(self.fps)
        self.postfx = PostProcessChain(self.display.renderer)
        self.fade = self._make_fade(self.fade_alpha)

    def _create_effect(self, effect_cls):
        effect = effect_cls(
            renderer=self.display.renderer, frame=self.frame,
            options=EffectOptions(self.frame.width, self.frame.height,
                                  low_spec=self.args.low_spec, seed=self.seed,
                                  audio=self.audio_config))
        effect.set_frame_context(self.frame)
        return effect

    def _setup_signals(self):
        self._interrupt_count = 0
        self._interrupt_watchdog = None

        def _sig_handler(sig, frame):
            # The first interrupt is graceful and never tears down pygame from
            # inside a draw callback. If another Ctrl-C arrives while cleanup
            # or audio shutdown is stuck, restore the OS default so the
            # process exits immediately instead of accumulating ``^C`` text.
            self._interrupt_count += 1
            if self._interrupt_count >= 2:
                # Do not depend on Python returning from a blocked PortAudio
                # or SDL call; hard-exit on the second interrupt.
                os._exit(128 + int(sig))
                return
            self._quit_requested = True
            # A first Ctrl-C should normally reach the loop's finally block,
            # but a backend call can hold the main thread. Guarantee escape
            # after a short grace period even if that call never returns.
            self._interrupt_watchdog = threading.Timer(1.5, lambda: os._exit(128 + int(sig)))
            self._interrupt_watchdog.daemon = True
            self._interrupt_watchdog.start()
        signal.signal(signal.SIGINT, _sig_handler)
        signal.signal(signal.SIGTERM, _sig_handler)

    def _parse_args(self):
        return parse_args(__version__)

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
        self._frames.update_target(self.display, self.args.gl)

    def _make_fade(self, alpha: int):
        return self._frames.make_fade(self.display.target, alpha)

    def _set_background_mode(self, mode_i: int) -> None:
        old_bg = getattr(self, "bg_vis", None)
        if old_bg is not None and hasattr(old_bg, "release"):
            old_bg.release()
        self.bg_mode_i = mode_i % len(MODES)
        self.bg_name, self.bg_cls = MODES[self.bg_mode_i]
        self.bg_vis = self._create_effect(self.bg_cls)

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
        bpm = float(getattr(self, "bpm", 0.0) or self.frame.bpm or 0.0)
        if 60.0 <= bpm <= 200.0:
            duration = 60.0 / bpm * _PRESET_MORPH_BEATS * self.fps
        else:
            duration = 4.0 * self.fps
        self._preset_morph = PresetTransition(
            start=self.tick, duration=max(1, int(round(duration))),
            mode_idx=int(preset.get("mode_idx", self.mode_idx)),
            bg_mode_i=int(preset.get("bg_mode_i", self.bg_mode_i)),
            bg_on=bool(preset.get("bg_on", self.bg_on)),
            from_gain=float(self.effect_gain), to_gain=float(preset.get("intensity", self.effect_gain)),
            from_alpha=float(self.bg_alpha), to_alpha=float(preset.get("bg_alpha", self.bg_alpha)),
            from_cf=float(self.cf_frames), to_cf=float(preset.get("cf_frames", self.cf_frames)),
        )

    def _advance_preset_morph(self) -> None:
        morph = self._preset_morph
        if morph is None:
            return
        values = morph.sample(self.tick)
        if values.switch_now:
            self._switch_mode(morph.mode_idx)
            self._set_background_mode(morph.bg_mode_i)
            self.bg_on = morph.bg_on
            morph.switched = True
        # Apply after switching: _switch_mode resets gain to its default.
        self.effect_gain = values.gain
        self.bg_alpha = values.alpha
        self.cf_frames = values.cf_frames
        if values.complete:
            self._preset_morph = None

    def _advance_parameter_morph(self) -> None:
        morph = self._parameter_morph
        if morph is None:
            return
        values, complete = morph.sample(self.tick)
        self.vis.set_morph_values(values)
        if complete:
            self._parameter_morph = None

    def _quit(self):
        self._quit_requested = True

    def _cleanup(self):
        if getattr(self, "_cleanup_done", False):
            return
        self._cleanup_done = True
        def attempt(action):
            try:
                action()
            except Exception as exc:
                print(f"Cleanup: {exc}", file=sys.stderr)

        if getattr(self, "_initialized", False):
            attempt(self._save_settings)
        for resource in (getattr(self, "postfx", None),
                         getattr(self, "vis", None), getattr(self, "bg_vis", None)):
            release = getattr(resource, "release", None)
            if callable(release):
                attempt(release)
        display = getattr(self, "display", None)
        if display is not None:
            attempt(display.kill_children)
            attempt(lambda: atexit.unregister(display.kill_children))
            renderer = getattr(display, "renderer", None)
            if renderer is not None:
                attempt(renderer.release)
                display.renderer = None
        audio = getattr(self, "audio", None)
        if audio is not None:
            attempt(audio.release)
        attempt(lambda: pygame.display.set_mode((1, 1)))
        attempt(pygame.quit)

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
            "visual_profile": self.visual_profile,
        })

    def _switch_mode(self, new_idx: int):
        old_values = self.vis.get_morph_values() if hasattr(self.vis, "get_morph_values") else {}
        self.vis.release()
        # Manual mode changes start clean; never reintroduce the previous frame.
        self.prev_surf = None
        self.display.target.fill((0,0,0,0))
        self.prev_surf_scaled = None
        self.crossfade_frame = 0
        self.mode_idx = new_idx % len(MODES)
        self.name, self.VisCls = MODES[self.mode_idx]
        self.vis = self._create_effect(self.VisCls)
        new_values = self.vis.get_morph_values() if hasattr(self.vis, "get_morph_values") else {}
        common = old_values.keys() & new_values.keys()
        if common:
            self._parameter_morph = ParameterTransition(
                start=self.tick,
                duration=max(1, int(self.cf_frames)),
                source={name: old_values[name] for name in common},
                target={name: new_values[name] for name in common},
            )
        else:
            self._parameter_morph = None
        self.effect_gain = .7
        self._update_target_res()
        self.display.target.fill((0,0,0,0 if self.args.gl and self.vis.IS_GL else 255))
        self._present_surface = None
        self._display_snapshot = None
        for name in ("_ui_surface", "bg_surf"):
            surface = getattr(self,name,None)
            if surface is not None:
                surface.fill((0,0,0,0))
        if hasattr(self,"postfx"):
            self.postfx.release()
        renderer = self.display.renderer
        if self.args.gl and renderer is not None:
            renderer.ctx.screen.use()
            renderer.ctx.clear(0.,0.,0.,1.)

    def _rebuild_effects(self, force: bool = False):
        self.frame = replace(self.frame, width=self.display.screen.get_width(), height=self.display.screen.get_height())
        prev_size = (self.frame.width, self.frame.height)
        if not force and getattr(self, "_last_rebuild_size", None) == prev_size:
            return
        self._last_rebuild_size = prev_size
        for attribute, effect_cls in (("vis", self.VisCls), ("bg_vis", MODES[self.bg_mode_i][1])):
            effect = getattr(self, attribute, None)
            if isinstance(effect, effect_cls) and callable(getattr(effect, "detach_renderer", None)):
                effect.renderer = self.display.renderer
            else:
                if effect is not None and callable(getattr(effect, "release", None)):
                    effect.release()
                setattr(self, attribute, self._create_effect(effect_cls))
        self.bg_name, self.bg_cls = MODES[self.bg_mode_i]
        self.bg_surf = pygame.Surface((self.frame.width, self.frame.height))
        self.prev_surf_scaled = None
        if hasattr(self, "postfx"):
            self.postfx.renderer = self.display.renderer
        self._update_target_res()
        snapshot = getattr(self, "_display_snapshot", None)
        if snapshot is not None and not self.vis.IS_GL:
            self.display.target.blit(pygame.transform.smoothscale(snapshot, self.display.target.get_size()), (0,0))
        self._display_snapshot = None

    def _release_for_display_change(self):
        target = getattr(self.display, "target", None)
        self._display_snapshot = target.copy() if target is not None and not getattr(self.vis, "IS_GL", False) else None
        for name in ("vis", "bg_vis"):
            obj = getattr(self, name, None)
            detach = getattr(obj, "detach_renderer", None)
            if callable(detach):
                detach()
            else:
                if callable(getattr(obj, "release", None)):
                    obj.release()
                setattr(self, name, None)
        renderer = getattr(self.display, "renderer", None)
        if renderer is not None and hasattr(renderer, "release"):
            renderer.release()
        self.display.renderer = None
        if hasattr(self, "postfx"):
            self.postfx.release()
            self.postfx.renderer = None

    def run(self):
        previous_frame = _time.monotonic() - 1.0 / 60.0
        try:
            while not self._quit_requested:
                now = _time.monotonic()
                self.frame = replace(self.frame, dt=max(0.0, min(0.1, now - previous_frame)))
                previous_frame = now
                self._handle_events()
                if self._quit_requested:
                    break
                self._update()
                if self._quit_requested:
                    break
            
                self._frames.begin_gl(self.display, self.args.gl)
                self._render()
                self._frames.present_gl(self.display, self.args.gl, self.vis,
                                        self.postfx, self.postfx_mode,
                                        self.effect_gain, self.tick)

                pygame.display.flip()
                quality = self.quality.observe(self.clock.get_rawtime(), enabled=True)
                self.frame = replace(self.frame, quality_tier=quality.tier, quality_scale=quality.scale)
                self.clock.tick(self.fps)
                self.tick += 1
        finally:
            self._cleanup()

    def _handle_events(self):
        handle_events(self, pygame.event.get(), os.path.abspath(__file__), _BG_MODES)

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
            self.palette.set_genre(detected)
        self._genre_check_cd = 60 if self.is_silent else 1

    def _compute_draw_beat(self) -> float:
        return self.response.compute_draw_beat(self.beat, self.auto_gain, self.effect_gain)

    def _update_beat_phase(self, audio_time, bpm, onset_time):
        phase = self.response.phase(
            audio_time, bpm, onset_time, tick=self.tick, fps=self.fps,
            is_silent=self.is_silent, using_tap=self.using_tap,
            tap_anchor_tick=self._phase_anchor_tick)
        self.frame = replace(self.frame, phase=phase)

    def _update_silence_state(self, rms, fft_mean, audio_time, generation,
                              music_activity=0.0):
        self.silence_gate.update(rms, fft_mean, audio_time, generation, music_activity)
        self.is_silent = self.silence_gate.is_silent

    def _update(self):
        self._advance_preset_morph()
        self._advance_parameter_morph()
        self._update_genre()
            
        snapshot = self.audio.get_snapshot()
        self.waveform, self.fft = snapshot.waveform, snapshot.fft
        if self.audio.beat_tracker.enabled:
            snapshot = replace(
                snapshot,
                bpm=self.audio.beat_tracker.analyze(fallback_bpm=snapshot.bpm),
                raw_beat=self.audio.beat_tracker.refine_beat(snapshot.raw_beat, snapshot.audio_time),
            )
        values = self.response.update(
            snapshot, dt=self.frame.dt, tick=self.tick, fps=self.fps,
            tap_bpm=self.tap_bpm, tap_expiry=self.tap_bpm_expiry,
            tap_anchor_tick=self._phase_anchor_tick, now=_time.monotonic(),
            previous_bpm=self.frame.bpm, gain=self.effect_gain, auto_gain=self.auto_gain)
        self.beat, self.draw_beat = values.beat, values.draw_beat
        self.bpm, self.using_tap, self.is_silent = values.bpm, values.using_tap, values.is_silent
        self.frame = replace(
            self.frame, width=self.display.screen.get_width(), height=self.display.screen.get_height(),
            waveform=self.waveform, fft=self.fft, beat=self.draw_beat, gain=self.effect_gain,
            mid=values.mid, treble=values.treble, envelopes=values.envelopes,
            bpm=values.bpm, phase=values.phase, is_silent=values.is_silent,
            tick=self.tick, profile=self.visual_profile, fps=self.fps)
        self.palette.update(self.beat, values.mid, values.treble, self.tick)

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
        return self._frames.prepare_present_surface(target, self.display.screen, self.args.gl)

    def _render(self):
        target = self._frames.compose(
            self.display.target,
            FrameInputs(self.vis, self.bg_vis, self.bg_on, self.bg_alpha,
                        self.waveform, self.fft, self.draw_beat, self.tick, context=self.frame),
            use_gl=self.args.gl,
            genre_alpha=self.palette.trail_alpha if self.current_genre != "detecting..." else None,
            postfx=self.postfx, postfx_mode=self.postfx_mode,
            gain=self.effect_gain, cf_frames=self.cf_frames)

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
        self.ui.draw_multiband_bars(target, self.beat, self.frame.mid, self.frame.treble)
        
        if self.audio.stream is None:
            dev_name = "no input"
        elif self.audio.active_dev is None:
            dev_name = "default"
        else:
            if self.audio.active_dev not in self.dev_name_cache:
                devs = self.audio.input_devices()
                self.dev_name_cache[self.audio.active_dev] = next((d[1] for d in devs if d[0] == self.audio.active_dev), "unknown")
            dev_name = self.dev_name_cache[self.audio.active_dev]
            
        self.ui.draw_hud(target, HUDState(
            version=__version__, fps=self.clock.get_fps(), use_gl=self.args.gl,
            profile=self.visual_profile, mode_idx=self.mode_idx, name=self.name,
            gain=self.effect_gain, bg_name=self.bg_name if self.bg_on else None,
            level=self.hud_level, bpm=self.bpm, genre=self.current_genre,
            using_tap=self.using_tap, auto_gain=self.auto_gain, device_name=dev_name,
            preset_name=(self.presets[self.active_preset]["name"]
                         if self.active_preset >= 0 and self.presets else None),
        ))

if __name__ == "__main__":
    app = VisualizerApp()
    app.run()
