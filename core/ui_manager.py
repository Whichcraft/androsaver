from __future__ import annotations
import pygame
import config

class UIManager:
    def __init__(self):
        self._last_width = 0
        self.recalculate_fonts()
        self.pane_open = False
        self.pane_sel = 0
        self.picking = False
        self.pick_sel = 0
        
        # Surface caches
        self._picker_overlay: pygame.Surface | None = None
        self._bar_surf: pygame.Surface | None = None
        self._flash_surf: pygame.Surface | None = None
        self._hud_bg: pygame.Surface | None = None

    def recalculate_fonts(self):
        self._last_width = config.WIDTH
        fs = max(20, min(48, int(config.WIDTH / 80)))
        fs_s = max(16, min(36, int(config.WIDTH / 100)))
        
        families = ["monospace", "Courier New", "DejaVu Sans Mono", "Liberation Mono", "Consolas"]
        self.font = pygame.font.SysFont(families, fs)
        self.font_s = pygame.font.SysFont(families, fs_s)
        self.font_big = pygame.font.SysFont(families, max(72, int(config.WIDTH / 25)), bold=True)

    def _check_resize(self):
        if config.WIDTH != self._last_width:
            self.recalculate_fonts()

    def draw_device_picker(self, target: pygame.Surface, devices: list[tuple[int, str]], active_idx: int | None) -> None:
        self._check_resize()
        if self._picker_overlay is None or self._picker_overlay.get_size() != target.get_size():
            self._picker_overlay = pygame.Surface(target.get_size(), pygame.SRCALPHA)
            self._picker_overlay.fill((0, 0, 0, 210))
            
        target.blit(self._picker_overlay, (0, 0))

        title = self.font.render(
            "SELECT INPUT DEVICE   Up/Down navigate   Enter confirm   Esc cancel",
            True,
            (200, 200, 200),
        )
        target.blit(title, (40, 30))

        if not devices:
            empty = self.font.render("No input devices available", True, (180, 180, 180))
            target.blit(empty, (40, 80))
            return

        row_h = 28
        y0 = 80
        visible = max(0, min(len(devices), (target.get_height() - y0 - 20) // row_h))
        start = max(0, min(self.pick_sel - visible // 2, len(devices) - visible))

        for i, (dev_idx, name) in enumerate(devices[start : start + visible]):
            row = start + i
            y = y0 + i * row_h
            is_selected = row == self.pick_sel
            is_active = dev_idx == active_idx
            if is_selected:
                pygame.draw.rect(target, (40, 80, 140), (30, y - 2, max(1, target.get_width() - 60), row_h - 2))
            marker = ">> " if is_active else "   "
            label = f"{marker}{dev_idx:3d}  {name}"
            color = (255, 255, 100) if is_selected else (140, 140, 140)
            target.blit(self.font.render(label, True, color), (40, y))

    def draw_pane(self, target: pygame.Surface, effect_gain: float, bg_alpha: int, cf_frames: float) -> None:
        self._check_resize()
        panel_w, panel_h = 240, 160
        px = target.get_width() - panel_w - 12
        py = 50
        # Pane background (could be cached too, but it's small)
        panel = pygame.Surface((panel_w, panel_h), pygame.SRCALPHA)
        panel.fill((0, 0, 0, 185))
        target.blit(panel, (px, py))
        pygame.draw.rect(target, (80, 80, 80), (px, py, panel_w, panel_h), 1)
        title = self.font_s.render("Settings  (Tab: close)", True, (120, 120, 120))
        target.blit(title, (px + 8, py + 6))
        items = [
            ("effect_gain", effect_gain, 2.0, f"{effect_gain:.1f}"),
            ("bg_alpha", bg_alpha, 255, f"{bg_alpha}"),
            ("crossfade", cf_frames, 90, f"{int(cf_frames)} fr"),
        ]
        for i, (label, value, max_value, value_str) in enumerate(items):
            y = py + 30 + i * 42
            selected = i == self.pane_sel
            color = (255, 255, 100) if selected else (150, 150, 150)
            marker = ">> " if selected else "   "
            target.blit(
                self.font_s.render(f"{marker}{label}: {value_str}", True, color),
                (px + 10, y),
            )
            bar_total = panel_w - 24
            bar_w = int(bar_total * max(0.0, min(value / max_value, 1.0)))
            pygame.draw.rect(target, (50, 50, 50), (px + 10, y + 16, bar_total, 6))
            if bar_w > 0:
                pygame.draw.rect(target, color, (px + 10, y + 16, bar_w, 6))

    def draw_multiband_bars(self, target: pygame.Surface, beat_val: float, mid_val: float, treble_val: float) -> None:
        self._check_resize()
        bar_w = 8
        bar_max = 120
        gap = 3
        total_w = 3 * bar_w + 2 * gap
        
        pad_x = 6
        pad_y = 6
        surf_w = total_w + 2 * pad_x
        surf_h = bar_max + 2 * pad_y
        
        if self._bar_surf is None or self._bar_surf.get_size() != (surf_w, surf_h):
            self._bar_surf = pygame.Surface((surf_w, surf_h), pygame.SRCALPHA)
            
        self._bar_surf.fill((0, 0, 0, 110)) # semi-transparent background
        
        defs = [
            (beat_val, (180, 50, 50, 200)),
            (mid_val, (50, 180, 50, 200)),
            (treble_val, (130, 60, 200, 200)),
        ]
        for i, (value, color) in enumerate(defs):
            h = int(bar_max * max(0.0, min(value / 3.0, 1.0)))
            x = pad_x + i * (bar_w + gap)
            if h > 0:
                pygame.draw.rect(self._bar_surf, color, (x, pad_y + bar_max - h, bar_w, h))
        target.blit(self._bar_surf, (6, target.get_height() - surf_h - 6))

    def draw_hud_background(self, target: pygame.Surface, lines: list[pygame.Surface]) -> None:
        """Draw semi-transparent panel behind HUD text."""
        if not lines:
            return
        max_w = max(line.get_width() for line in lines)
        spacing = 3
        total_h = sum(line.get_height() for line in lines) + spacing * (len(lines) - 1)
        bg_size = (max_w + 16, total_h + 12)
        if self._hud_bg is None or self._hud_bg.get_size() != bg_size:
            self._hud_bg = pygame.Surface(bg_size, pygame.SRCALPHA)
        self._hud_bg.fill((0, 0, 0, 110))
        target.blit(self._hud_bg, (4, 4))

    def draw_tap_flash(self, target: pygame.Surface, tap_bpm: float, alpha: int) -> None:
        self._check_resize()
        if self._flash_surf is None or self._flash_surf.get_size() != target.get_size():
            self._flash_surf = pygame.Surface(target.get_size(), pygame.SRCALPHA)
            
        self._flash_surf.fill((255, 255, 255, alpha // 6))
        target.blit(self._flash_surf, (0, 0))
        label = self.font_big.render(f"{tap_bpm:.0f} BPM", True, (255, 255, 255, alpha))
        target.blit(
            label,
            (
                target.get_width() // 2 - label.get_width() // 2,
                target.get_height() // 2 - label.get_height() // 2,
            ),
        )
