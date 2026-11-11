"""Dispatch desktop events against explicit application state/actions."""
import time as _time

import numpy as np
import pygame

import settings as sett
from core.postprocess import PostProcessChain
from effects import MODES


def handle_events(app, events, entry_script, background_modes=9):
    """Apply an event batch in order, preserving modal picker precedence."""
    for event in events:
        if event.type == pygame.QUIT:
            app._quit()

        elif event.type == pygame.KEYDOWN:
            if app.ui.picking:
                if event.key == pygame.K_UP:
                    app.ui.pick_sel = (app.ui.pick_sel - 1) % max(1, len(app.audio.input_devices()))
                elif event.key == pygame.K_DOWN:
                    app.ui.pick_sel = (app.ui.pick_sel + 1) % max(1, len(app.audio.input_devices()))
                elif event.key in (pygame.K_ESCAPE, pygame.K_RETURN):
                    if event.key == pygame.K_RETURN:
                        devs = app.audio.input_devices()
                        if 0 <= app.ui.pick_sel < len(devs):
                            app.audio.start_input_stream(devs[app.ui.pick_sel][0])
                    app.ui.picking = False
                continue

            if event.key in (pygame.K_ESCAPE, pygame.K_q):
                app._quit()
            elif event.key == pygame.K_f:
                app._release_for_display_change()
                app.display.toggle_fullscreen()
                app._rebuild_effects(force=True)
            elif event.key == pygame.K_SPACE:
                app._switch_mode(app.mode_idx + 1)
            elif event.key == pygame.K_h:
                if event.mod & pygame.KMOD_SHIFT:
                    app.hud_level = (app.hud_level + 1) % 3
                    app.show_hud = app.hud_level > 0
                else:
                    app.show_hud = not app.show_hud
            elif event.key == pygame.K_TAB:
                app.ui.pane_open = not app.ui.pane_open
            elif event.key == pygame.K_v:
                profiles = ("auto", "laptop", "tv")
                app.visual_profile = profiles[(profiles.index(app.visual_profile) + 1) % len(profiles)]
                app.args.visual_profile = app.visual_profile
            elif event.key == pygame.K_m:
                if event.mod & pygame.KMOD_SHIFT:
                    if app.span_mode:
                        app.display.kill_children()
                        app.span_mode = False
                    elif not app.args.span_child:
                        app.display.spawn_span_children(app.span_vis2_idx, entry_script)
                        app.span_mode = True
                else:
                    t = _time.monotonic()
                    app.tap_times.append(t)
                    if len(app.tap_times) >= 2:
                        app.tap_bpm = 60.0 / np.median(np.diff(app.tap_times))
                        app.tap_bpm_expiry = t + 8.0
                        app.tap_flash_end = t + 0.5
                        app._phase_anchor_tick = app.tick
            elif event.key == pygame.K_a:
                if app.span_mode:
                    app.span_vis2_idx = (app.span_vis2_idx - 1) % len(MODES)
                    app.display.spawn_span_children(app.span_vis2_idx, entry_script)
                else:
                    app.auto_gain = not app.auto_gain
            elif event.key == pygame.K_d:
                if app.span_mode:
                    app.span_vis2_idx = (app.span_vis2_idx + 1) % len(MODES)
                    app.display.spawn_span_children(app.span_vis2_idx, entry_script)
                else:
                    app.ui.picking = True
                    devs = app.audio.input_devices()
                    app.ui.pick_sel = next((i for i, d in enumerate(devs) if d[0] == app.audio.active_dev), 0)
            elif event.key == pygame.K_b:
                if event.mod & pygame.KMOD_SHIFT:
                    app._set_background_mode((app.bg_mode_i + 1) % background_modes)
                else:
                    app.bg_on = not app.bg_on
            elif event.key == pygame.K_r and event.mod & pygame.KMOD_SHIFT:
                app._apply_recipe(app.recipe_idx + 1)
            elif event.key == pygame.K_x and event.mod & pygame.KMOD_SHIFT:
                app.postfx_mode = (app.postfx_mode + 1) % len(PostProcessChain.MODES)
            elif event.key == pygame.K_p:
                if event.mod & pygame.KMOD_SHIFT:
                    app.active_preset = (app.active_preset + 1) % max(1, len(app.presets))
                    if app.presets:
                        p = app.presets[app.active_preset]
                        app._start_preset_morph(p)
                else:
                    preset_name = f"Preset {len(app.presets) + 1}"
                    entry = {"mode_idx": app.mode_idx, "intensity": app.effect_gain,
                             "bg_on": app.bg_on, "bg_mode_i": app.bg_mode_i,
                             "bg_alpha": app.bg_alpha, "cf_frames": app.cf_frames,
                             "postfx_mode": app.postfx_mode}
                    app.presets.append({"name": preset_name, **entry})
                    sett.save_preset(preset_name, entry)
                    app.active_preset = len(app.presets) - 1
            elif event.key == pygame.K_RIGHT:
                if app.ui.pane_open:
                    app._pane_adjust(1)
                else:
                    app._switch_mode(app.mode_idx + 1)
            elif event.key == pygame.K_LEFT:
                if app.ui.pane_open:
                    app._pane_adjust(-1)
                else:
                    app._switch_mode(app.mode_idx - 1)
            elif event.key == pygame.K_UP:
                if app.ui.pane_open:
                    app.ui.pane_sel = (app.ui.pane_sel - 1) % 3
                elif app.name == "FlowField" and hasattr(app.vis, "adjust_particles"):
                    app.vis.adjust_particles(2000)
                else:
                    app.effect_gain = min(2.0, round(app.effect_gain + 0.1, 1))
            elif event.key == pygame.K_DOWN:
                if app.ui.pane_open:
                    app.ui.pane_sel = (app.ui.pane_sel + 1) % 3
                elif app.name == "FlowField" and hasattr(app.vis, "adjust_particles"):
                    app.vis.adjust_particles(-2000)
                else:
                    app.effect_gain = max(0.0, round(app.effect_gain - 0.1, 1))
            elif pygame.K_1 <= event.key <= pygame.K_9:
                app._switch_mode(event.key - pygame.K_1)

        elif event.type == pygame.MOUSEBUTTONDOWN and not app.ui.picking:
            app._switch_mode(app.mode_idx + 1)
