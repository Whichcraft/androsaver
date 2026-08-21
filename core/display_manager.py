from __future__ import annotations
import importlib
import os
import sys
import subprocess
import re as _re
from typing import TYPE_CHECKING
import pygame
import config

if TYPE_CHECKING:
    from gl_renderer import GLRenderer


def _load_gl_renderer():
    try:
        mod = importlib.import_module("gl_renderer")
    except Exception as exc:
        print(f"  ⚠️ ModernGL renderer import failed: {exc}", file=sys.stderr)
        return None, False
    return mod.GLRenderer, bool(getattr(mod, "HAS_MODERNGL", False))

class DisplayManager:
    def __init__(self, args):
        self.args = args
        self._x11_err_handler_ref = None
        self._libX11 = None
        self._xmove_target = None
        self.renderer: GLRenderer | None = None
        self._gl_renderer_cls: type[GLRenderer] | None = None
        self._has_moderngl = False
        self.screen = None
        self.target = None
        self.fullscreen = True
        self.xmonitors = self._xrandr_monitors() or []
        detected = 0
        try:
            detected = int(pygame.display.get_num_displays())
        except Exception:
            pass
        self.num_displays = max(len(self.xmonitors), detected, 1)
        self.display_idx = 0
        self.span_children: dict[int, subprocess.Popen] = {}
        self._install_x11_error_handler()

    def _install_x11_error_handler(self) -> None:
        """Suppress X11 RandR errors on some multi-monitor X11 setups."""
        try:
            import ctypes

            class _XErrorEvent(ctypes.Structure):
                _fields_ = [
                    ("type", ctypes.c_int),
                    ("display", ctypes.c_void_p),
                    ("resourceid", ctypes.c_ulong),
                    ("serial", ctypes.c_ulong),
                    ("error_code", ctypes.c_ubyte),
                    ("request_code", ctypes.c_ubyte),
                    ("minor_code", ctypes.c_ubyte),
                ]

            handler_type = ctypes.CFUNCTYPE(
                ctypes.c_int,
                ctypes.c_void_p,
                ctypes.POINTER(_XErrorEvent),
            )

            def _handler(display, event):
                return 0

            self._x11_err_handler_ref = handler_type(_handler)
            lib = ctypes.CDLL("libX11.so.6")
            lib.XSetErrorHandler(self._x11_err_handler_ref)
            lib.XMoveWindow.argtypes = [
                ctypes.c_void_p,
                ctypes.c_ulong,
                ctypes.c_int,
                ctypes.c_int,
            ]
            lib.XSync.argtypes = [ctypes.c_void_p, ctypes.c_int]
            self._libX11 = lib
        except Exception:
            pass

    def _xrandr_monitors(self) -> list[tuple[int, int, int, int]] | None:
        """Return list of (x, y, w, h) for each physical monitor."""
        try:
            out = subprocess.check_output(
                ["xrandr", "--listmonitors"],
                stderr=subprocess.DEVNULL,
                text=True,
            )
            monitors = []
            for line in out.splitlines():
                match = _re.search(r"(\d+)/\d+x(\d+)/\d+\+(-?\d+)\+(-?\d+)", line)
                if match:
                    w, h, x, y = (int(match.group(i)) for i in range(1, 5))
                    monitors.append((x, y, w, h))
            monitors.sort(key=lambda mon: mon[0])
            return monitors
        except Exception:
            return None

    def open_display(self, idx: int, fullscreen: bool):
        # Validate index
        if idx < 0 or idx >= self.num_displays:
            print(f"  ⚠️ display_idx {idx} out of range (0-{self.num_displays - 1}), falling back to 0")
            idx = 0
            
        self.display_idx = idx
        self.fullscreen = fullscreen
        self._xmove_target = None
        # The old context is still current here; release renderer resources
        # before SDL recreates the window/context below.
        if self.renderer is not None:
            try:
                self.renderer.release()
            finally:
                self.renderer = None
        flags = 0
        def set_mode(size, mode_flags):
            if self.num_displays <= 1:
                return pygame.display.set_mode(size, mode_flags)
            try:
                return pygame.display.set_mode(size, mode_flags, display=idx)
            except TypeError:
                # Older Pygame builds and simple test doubles lack display=.
                return pygame.display.set_mode(size, mode_flags)
        if self.args.gl:
            flags |= pygame.OPENGL | pygame.DOUBLEBUF
            if self._gl_renderer_cls is None:
                self._gl_renderer_cls, self._has_moderngl = _load_gl_renderer()
            if not self._has_moderngl:
                print("  ⚠️ --gl requested but moderngl is not installed in this environment; running without the ModernGL renderer.")
                # An OpenGL display without a renderer cannot present the
                # Pygame target surface, so use the CPU display path.
                self.args.gl = False
                flags = 0
            if self.args.gl and not getattr(self, "_gl_attrs_set", False):
                try:
                    pygame.display.gl_set_attribute(pygame.GL_CONTEXT_MAJOR_VERSION, 3)
                    pygame.display.gl_set_attribute(pygame.GL_CONTEXT_MINOR_VERSION, 3)
                    pygame.display.gl_set_attribute(pygame.GL_CONTEXT_PROFILE_MASK, pygame.GL_CONTEXT_PROFILE_CORE)
                    self._gl_attrs_set = True
                except Exception as e:
                    print(f"  ⚠️ Warning: Failed to set OpenGL attributes: {e}")
        
        try:
            if fullscreen and idx < len(self.xmonitors):
                mx, my, mw, mh = self.xmonitors[idx]
                # Sanity check geometry
                if mw < 100 or mh < 100:
                     raise ValueError(f"Invalid monitor geometry: {mw}x{mh}")
                     
                os.environ["SDL_VIDEO_WINDOW_POS"] = f"{mx},{my}"
                try:
                    self.screen = pygame.display.set_mode((mw, mh), flags | pygame.NOFRAME)
                finally:
                    os.environ.pop("SDL_VIDEO_WINDOW_POS", None)
                if self._libX11:
                    wm = pygame.display.get_wm_info()
                    dpy = wm.get("display")
                    win = wm.get("window")
                    if isinstance(dpy, int) and dpy and win:
                        self._libX11.XMoveWindow(dpy, win, mx, my)
                        self._libX11.XSync(dpy, 0)
                        self._xmove_target = (dpy, win, mx, my)
                config.WIDTH = mw
                config.HEIGHT = mh
            elif fullscreen:
                self.screen = set_mode((0, 0), flags | pygame.FULLSCREEN)
                config.WIDTH, config.HEIGHT = self.screen.get_size()
            else:
                # Default window size if config was 0
                w = config.WIDTH or 1280
                h = config.HEIGHT or 720
                self.screen = set_mode((w, h), flags)
                config.WIDTH, config.HEIGHT = self.screen.get_size()
        except Exception as _dm_exc:
            print(f"  ⚠️ Display init failed: {_dm_exc}", file=sys.stderr)
            # Last resort fallback: plain CPU/windowed mode. Reusing OPENGL
            # flags here would repeat the failure this branch is recovering.
            self.args.gl = False
            self.renderer = None
            self.screen = pygame.display.set_mode((1280, 720), 0)
            config.WIDTH, config.HEIGHT = 1280, 720
        config._INITIALIZED = True
        
        if self.args.gl and self._has_moderngl and self._gl_renderer_cls is not None:
            try:
                self.renderer = self._gl_renderer_cls(config.WIDTH, config.HEIGHT)
            except Exception as e:
                print(f"  ⚠️ ModernGL renderer initialization failed: {e}")
                self.renderer = None
                # A context can fail even when moderngl imports correctly.
                # Reopen the window without OPENGL so --gl still degrades to
                # the working CPU renderer.
                self.args.gl = False
                return self.open_display(idx, fullscreen)
        
        self.target = self.screen
        return self.screen

    def toggle_fullscreen(self):
        return self.open_display(self.display_idx, not self.fullscreen)

    def reposition_window_fix(self, tick: int):
        if self._libX11 and self._xmove_target and tick == 0:
            dpy, win, mx, my = self._xmove_target
            self._libX11.XMoveWindow(dpy, win, mx, my)
            self._libX11.XSync(dpy, 0)

    def requery_xmonitors(self) -> bool:
        """Re-query xrandr monitor list (handles hotplug changes)."""
        new_mons = self._xrandr_monitors()
        if new_mons is None:
            return False
        if new_mons != self.xmonitors:
            self.xmonitors = new_mons
            self.num_displays = max(len(self.xmonitors), 1)
            self.display_idx = max(0, min(getattr(self, "display_idx", 0), self.num_displays - 1))
            return True
        return False

    def spawn_child(self, child_idx: int, mode_i: int, entry_script: str) -> None:
        cmd = [
            sys.executable,
            entry_script,
            "--display",
            str(child_idx),
            "--mode",
            str(mode_i),
            "--span-child",
        ]
        if self.args.gl:
            cmd.append("--gl")
        try:
            self.span_children[child_idx] = subprocess.Popen(cmd)
        except Exception as e:
            print(f"  \u26a0\ufe0f Failed to spawn span child {child_idx}: {e}")

    def spawn_span_children(self, mode_i: int, entry_script: str):
        self.kill_children()
        for child_idx in range(self.num_displays):
            if child_idx == self.display_idx:
                continue
            self.spawn_child(child_idx, mode_i, entry_script)

    def kill_children(self) -> None:
        """Terminate all span child processes and wait for them to exit."""
        children = getattr(self, "span_children", {})
        # 1. Send SIGTERM to all first (parallel)
        for child in children.values():
            if child.poll() is None:
                try:
                    child.terminate()
                except (ProcessLookupError, OSError):
                    pass
        
        # 2. Wait a bit for all to exit gracefully
        for child in children.values():
            try:
                child.wait(timeout=0.15)
            except subprocess.TimeoutExpired:
                pass
        
        # 3. Kill any survivors and wait to clean up zombies
        for child in children.values():
            if child.poll() is None:
                try:
                    child.kill()
                except (ProcessLookupError, OSError):
                    pass
                try:
                    child.wait()
                except (ChildProcessError, OSError):
                    pass
        
        self.span_children = {}

    def __del__(self):
        """Final cleanup of resources."""
        self.kill_children()
