"""Persist user settings and presets to ~/.config/psysuals/."""

import json
import os
import sys

_CONFIG_DIR    = os.path.join(os.path.expanduser("~"), ".config", "psysuals")
_SETTINGS_FILE = os.path.join(_CONFIG_DIR, "settings.json")
_PRESETS_FILE  = os.path.join(_CONFIG_DIR, "presets.json")

_DEFAULTS = {
    "active_dev": None,
    "mode_idx": 0,
    "display_idx": 0,
    "show_hud": True,
    "hud_level": 2,
    "auto_gain": False,
    "bg_on": False,
    "bg_mode_i": 0,
    "bg_alpha": 102,
    "cf_frames": 45,
    "effect_gain": 0.7,
}


def _ensure_config_dir() -> bool:
    try:
        os.makedirs(_CONFIG_DIR, exist_ok=True)
        return True
    except OSError as exc:
        print(f"Settings: cannot create config directory: {exc}", file=sys.stderr)
        return False


def _normalise(data: object) -> dict:
    if not isinstance(data, dict):
        return dict(_DEFAULTS)
    result = {**_DEFAULTS, **data}
    int_ranges = {
        "mode_idx": (0, 10_000), "display_idx": (0, 10_000),
        "hud_level": (0, 2), "bg_mode_i": (0, 10_000),
        "bg_alpha": (0, 255), "cf_frames": (0, 300),
    }
    for key, (low, high) in int_ranges.items():
        value = result[key]
        if isinstance(value, bool) or not isinstance(value, int):
            result[key] = _DEFAULTS[key]
        else:
            result[key] = max(low, min(high, value))
    value = result["effect_gain"]
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        result["effect_gain"] = _DEFAULTS["effect_gain"]
    else:
        result["effect_gain"] = max(0.0, min(2.0, float(value)))
    for key in ("show_hud", "auto_gain", "bg_on"):
        if not isinstance(result[key], bool):
            result[key] = _DEFAULTS[key]
    return result


def load() -> dict:
    if not _ensure_config_dir():
        return dict(_DEFAULTS)
    try:
        with open(_SETTINGS_FILE) as f:
            data = json.load(f)
        return _normalise(data)
    except (FileNotFoundError, json.JSONDecodeError, UnicodeDecodeError, OSError):
        return dict(_DEFAULTS)


def save(d: dict) -> None:
    if not _ensure_config_dir():
        return
    tmp = _SETTINGS_FILE + ".tmp"
    try:
        with open(tmp, "w") as f:
            json.dump(d, f, indent=2)
        os.replace(tmp, _SETTINGS_FILE)
    except (PermissionError, OSError) as exc:
        print(f"Settings: cannot save settings: {exc}", file=sys.stderr)
        try:
            os.unlink(tmp)
        except OSError:
            pass


# ── Presets ───────────────────────────────────────────────────────────────────

def load_presets() -> list:
    """Return list of saved preset dicts (each has at least a 'name' key)."""
    try:
        with open(_PRESETS_FILE) as f:
            data = json.load(f)
        if isinstance(data, dict):
            data = [
                {"name": k, **value} for k, value in data.items()
                if isinstance(k, str) and isinstance(value, dict)
            ]
        if not isinstance(data, list):
            return []
        valid = []
        for preset in data:
            if not isinstance(preset, dict) or not isinstance(preset.get("name"), str):
                continue
            mode_idx = preset.get("mode_idx")
            if not isinstance(mode_idx, int) or isinstance(mode_idx, bool):
                continue
            item = dict(preset)
            item["mode_idx"] = max(0, mode_idx)
            gain = item.get("intensity", _DEFAULTS["effect_gain"])
            if isinstance(gain, (int, float)) and not isinstance(gain, bool):
                item["intensity"] = max(0.0, min(2.0, float(gain)))
            else:
                item["intensity"] = _DEFAULTS["effect_gain"]
            item["bg_on"] = item.get("bg_on", False) if isinstance(item.get("bg_on", False), bool) else False
            bg_mode = item.get("bg_mode_i", 0)
            item["bg_mode_i"] = max(0, bg_mode) if isinstance(bg_mode, int) and not isinstance(bg_mode, bool) else 0
            valid.append(item)
        return valid
    except (FileNotFoundError, json.JSONDecodeError, UnicodeDecodeError, OSError, TypeError):
        return []


def _write_presets(presets: list) -> None:
    """Atomically write presets list to disk."""
    if not _ensure_config_dir():
        return
    tmp = _PRESETS_FILE + ".tmp"
    try:
        with open(tmp, "w") as f:
            json.dump(presets, f, indent=2)
        os.replace(tmp, _PRESETS_FILE)
    except (PermissionError, OSError) as exc:
        print(f"Settings: cannot save presets: {exc}", file=sys.stderr)
        try:
            os.unlink(tmp)
        except OSError:
            pass


def save_preset(name: str, data: dict) -> None:
    """Append a preset entry. Always adds a new entry (append-only)."""
    presets = load_presets()
    presets.append({"name": name, **data})
    _write_presets(presets)


def delete_preset(name: str) -> None:
    """Remove a preset by name (no-op if not found)."""
    presets = [p for p in load_presets() if p.get("name") != name]
    _write_presets(presets)
