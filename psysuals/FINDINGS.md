# psysuals — Read-only Bug Hunt (2026-10-30)

Scope: confirmed findings only, one package at a time.
Repo state at start: clean `dev` in sync with `origin/dev`, no uncommitted changes.
No code was modified during this review.

---

## Package 0: Inventory and execution surface

Scope: `README.md`, `requirements.txt`, `requirements-gl.txt`, `smoke_test.py`, `config.py`, `settings.py`, `psysualizer.py`, `gl_renderer.py`, `beat_tracking.py`, `benchmarks.py`.

Verifications performed (all resolved as not-bugs):

- `config.py`: `_INITIALIZED` gate (L14, L17) is a global init flag; `assert_initialized()` guards real config use. `config.BPM` defaults to `0.0` (L41).
- `smoke_test.py`: sets `config._INITIALIZED = True` (L25) and wires the regression phase (L56-59); entrypoint L63.
- `benchmarks.py`: sets `_INITIALIZED` (L28, L115); entrypoint L144.
- `psysualizer.py`: entrypoint L978. Carry-forwards verified and resolved:
  - `FRAME_DT` clamped at L452/L732 (init L71) — no drift.
  - `energy_hist`/`energy_sum` subtract-before-append (L806-809) — no accumulator drift.
  - `_start_preset_morph` (L269) reads BPM via `float(getattr(self, "bpm", 0.0) or config.BPM or 0.0)` — safe with `config.BPM == 0.0`.
  - `_save_settings` (L345) intentionally skips `self.args.span_child` (L346-347).

**Findings: 0**
