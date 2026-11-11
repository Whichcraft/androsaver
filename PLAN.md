# Maintainability plan for bounded model runs

Revisited: 2026-11-11, branch `dev`, HEAD `b8c6de0`, version 3.20.0.
This plan describes the current uncommitted worktree, not just HEAD.
M01–M14 and the later explicit-context migration C01–C05 have recorded
completion evidence in [TODO.md](TODO.md). Do not execute those tasks again.
[ARCHITECTURE.md](ARCHITECTURE.md) describes the current implementation.

This revision challenges the original plan and defines bounded follow-up cards.
The user subsequently authorized R01–R05; execution results are in TODO.md.
Do not restart completed cards. Keep execution status exclusively in TODO.md.
The user's pre-existing FINDINGS.md must remain untouched.

## What changed in this plan

- Retired the original extraction sequence: its modules now exist. More modules
  or fewer lines are not evidence that further extraction would help.
- Removed instructions to initialize/restore config globals: config.py is gone.
- Replaced the historical 127-test starting point with the recorded 190-test
  migration result, while keeping the distinction between recorded and rerun.
- Separated preserved drawing signatures from compatibility of host construction.
  Android construction deliberately changed and remains externally unverified.
- Added explicit evidence requirements for inputs, failure behaviour, performance
  and host integration. Passing visual snapshots does not establish these.

## Challenge of the original items

Each row gives the original purpose and the refined disposition. Historical
results and detailed M13 sub-scope outcomes remain in TODO.md.

| Item | Purpose | Review / refinement |
| --- | --- | --- |
| M01 | Validate saved post-process selection | Keep the fix and loading tests. Validation belongs in settings; no persistence rewrite. |
| M02 | Make recipes select their declared background | Keep full-registry restoration and first-nine manual cycling as distinct policies. Test actual selected classes, not labels alone. |
| M03 | Extract CLI parsing | Complete. Keep explicit argv/version inputs; a parser framework would add no demonstrated value. |
| M04 | Read coherent audio snapshots | Keep one generation per update. Array ownership matters as much as locking; assess extra copies under R04. |
| M05 | Own silence hysteresis separately | Complete. Counters advance on fresh audio generations; avoid changing silence tuning during maintenance. |
| M06 | Name transition state/interpolation | Complete. Tick-based duration remains intentional compatibility, not elapsed-time equivalence. A timing redesign needs a separate request. |
| M07 | Own audio-to-visual response | Complete. The app now builds FrameContext and owns its palette; the old instruction to publish config fields is obsolete. |
| M08 | Extract input dispatch | Complete. Passing the app is still coupling; add an action interface only if a concrete change proves the existing boundary difficult to test. |
| M09 | Define effect cleanup | Keep detach versus final release distinct. Failure recovery and renderer rebinding require a precise contract; see R03. |
| M10 | Extract frame composition | Complete as structural work. EGL framebuffer evidence does not close desktop presentation acceptance; see R05. |
| M11 | Make startup/shutdown ownership explicit | Keep failure-injection tests. They demonstrate attempted cleanup, not recovery from every backend/driver failure. |
| M12 | Clarify Butterflies stages | Complete. Retain seeded/state comparisons; do not generalize the state machine into a shared framework. |
| M13 | Review remaining modules/effects/tests | Completed inventory, not a recurring cleanup mandate. Reopen one module only for new evidence; no second sweep. |
| M14 | Reconcile docs | Completed documentation pass. This revision supersedes its stale plan guidance; hardware gates remain open. |
| C01 | Introduce explicit context/options/audio config | Implemented. Frozen fields and read-only NumPy arrays are not absolute immutability; clarify and validate under R02. |
| C02 | Move mutable app/display/UI/quality state to owners | Implemented. Distinguish independently owned Python state from process-wide SDL/audio/GL facilities; no claim of concurrent desktop app support. |
| C03 | Migrate effects and retain drawing adapters | Implemented. Retaining draw arguments does not preserve old constructors, subclass draw overrides or every output condition; see R03/R05. |
| C04 | Update hosts/tests and remove config.py | Implemented locally. External Android host adoption is a separate integration gate. Avoid recreating a config facade in tests. |
| C05 | Verify isolation and rendering | Recorded checks are useful but bounded: three interleaved effect classes, 32 CPU effects and seven EGL effects. Preserve reproducible checks under R01. |

## Follow-up cards and acceptance criteria

R01–R04 cover durable evidence, contracts and measured overhead. R05 records
separate pass/fail/unavailable integration outcomes; completing the investigation
does not turn unavailable hardware or failed sizing checks into passes. The
cards below retain their scopes and done criteria; current results live in
TODO.md. A no-change result is acceptable when supported by evidence.

### R01 — Make the rendering evidence reproducible

**Problem:** The detailed old-versus-new renderer script and saved source used
for the migration live under /tmp. The new source is uncommitted, so comparing
against HEAD alone also includes changes preceding the context migration.
Existing tests are durable; this particular migration comparison is not.

**Scope:** tests/test_context.py, tests/test_visual_regression.py, README.md.
Read existing visual baselines before deciding whether another artifact is useful.
Do not silently copy /tmp source trees into the repository.

**Work:** Separate permanent automated checks from one-off historical evidence.
Document an exact runnable command for permanent context/adapter checks. If a
migration comparison must remain repeatable, define the exact reference revision
or reviewed source artifact, input sequence, seed, dimensions, backend and
comparison rule before adding a small fixture. Record any missing reference
as unavailable, rather than presenting an approximate reconstruction as proof.

**Done when:** A fresh checkout with the existing dependencies can run the
permanent checks without /tmp or chat history. The documentation states what
can and cannot reproduce the historical comparison. Existing visual baseline
values/tolerances remain unchanged. Any extra GPU check skips with a reason
when unavailable; it must not silently run CPU and claim GPU success.

**Focused checks:** tests.test_context, tests.test_visual_regression.
**Not included:** snapshot regeneration, source archives or a new test framework.

### R02 — Define and enforce the public input contract

**Problem:** Current constructors accept fractional dimensions/block sizes,
NaN dt and negative quality scale. Read-only owned arrays can explicitly have
write access re-enabled. These were confirmed with small constructor probes;
they do not show a failure in the normal desktop path.

**Scope:** core/context.py, tests/test_context.py, ARCHITECTURE.md.
Read the audio/effect consumers before choosing validation bounds.

**Work:** Specify which inputs hosts may supply and whether invalid values raise
or are normalized. Cover positive integer sizes/rates/channels, finite timing
and quality values, supported profiles, envelope structure and audio-array
shape/dtype. Decide supported non-default audio sizes from actual consumers;
do not invent a promise that every effect supports arbitrary FFT lengths.
Reject bool where an integer count is required. Preserve valid existing inputs.
Document arrays as defensive read-only copies unless a stronger guarantee is
necessary; do not build a security boundary around NumPy's write flag.

**Done when:** Table-driven tests cover valid defaults, supported alternative
inputs and explicitly rejected cases. Errors occur at the host boundary and
explain the invalid field. Mutating the caller's original array cannot change
a frame. The stated immutability guarantee matches actual behaviour.

**Focused checks:** tests.test_context, tests.test_audio_engine.
**Not included:** new tuning values, audio algorithm changes or blanket clamps.
If consumer fixes require more files, stop and propose a separate bounded card.

### R03 — Specify rendering and failure semantics

**Problem:** render retains the new frame on success and restores the old frame
on error; draw_frame restores its frame on either outcome, but retains a newly
supplied renderer. Neither promises to undo partially advanced simulation state.
The API is currently easy to misread as a transaction.

**Scope:** effects/base.py, tests/test_context.py, ARCHITECTURE.md.
Read specialized _draw_frame/detach hooks only as direct dependencies.

**Work:** Document those distinctions and the sequence callers should use to
recover or discard an effect after failure. Test success/failure binding for
render, draw and draw_frame, repeated use after a hook error, renderer changes
and detach failure. Distinguish restoring the frame pointer from restoring
resources, pixels, RNG or simulation state. State that a single effect instance
is not guaranteed safe for concurrent rendering.

**Done when:** Tests directly construct effects without RenderHost, verify that
another instance is unaffected, and assert the documented renderer/frame state
after each outcome. No accidental full-state rollback promise is introduced.
Subclass authors are explicitly told to implement _draw/_draw_frame; preserving
public call arguments does not preserve old subclass override behaviour.

**Focused checks:** tests.test_context, tests.test_graphics_lifecycle.
**Not included:** simulation rollback, thread-safe SDL or a new renderer API.
Changes to specialized resource recovery need a separate demonstrated task.

### R04 — Measure context copying before optimizing it

**Problem:** Every dataclasses.replace(FrameContext, ...) currently copies both
audio arrays, including metadata-only updates. The main loop replaces the frame
for dt, audio and quality; adapters can add copies. The copies are confirmed;
a material frame-time regression has not been measured.

**Scope:** benchmarks.py, tests/test_benchmarks.py, core/context.py,
tests/test_context.py. Inspect main-loop call sites without refactoring them.

**Work:** Measure context construction and metadata replacement separately from
simulation/render time with representative array sizes. Report repeatable input,
run count, median/tail timings and bytes copied; compare with the frame budget.
Existing effect-only throughput is insufficient evidence of whole-app cost.
If negligible, record no change. If material, propose a minimal ownership-safe
change with evidence before implementing it.

**Done when:** Results justify either no change or one bounded optimization.
Any optimization preserves caller-array isolation, read-only consumers, RNG
order and visuals. Do not introduce shared writable buffers to win a benchmark.
Performance claims use a named environment; unit tests avoid timing thresholds.

**Focused checks:** tests.test_benchmarks, tests.test_context; a documented
measurement command with results in TODO.md. Hardware is not needed to measure
context construction; whole-app/GPU claims need separate evidence.
**Not included:** shader tuning, an ECS, generic caching or FPS promises.

### R05 — Close each external integration gate separately

**Problem:** Headless tests and offscreen EGL cannot prove desktop presentation,
live audio behaviour, physical multi-monitor operation or Android integration.
Drawing argument compatibility alone does not establish external host compatibility.
R05 inspection established that AndroSaver is a native Kotlin/GLES backport,
not a Python-embedding host; its reference subtree is not a runtime dependency.

**Scope:** One target per run: desktop presentation, live audio, multi-monitor,
or the external Android host. Record results in TODO.md; update README.md only
when a compatibility statement changes. This is a verification card, not
permission to edit an external repository or personal settings.

**Work:** Record environment/backend and a concrete scenario:
- Desktop: CPU and GL startup, resize/fullscreen, mode changes, layers and exit;
  inspect opaque frames, cleared overlays, trails and presentation size.
- Audio: no-input startup and an available-device switch; verify continued
  rendering, error handling and stream cleanup using temporary settings.
- Multi-monitor: span startup, monitor/window change, child teardown and
  suppression of child settings writes, where the physical setup permits.
- Android: inspect the actual architecture first. For a Python-embedding host,
  adopt explicit EffectOptions in its own authorized change and exercise repeated
  draw_frame, resize/context replacement and RGBA ownership. For AndroSaver's
  native backport, inspect runtime/build inputs and native rendering tests;
  verify applicable CI evidence against the exact current app/build source.
  Do not introduce a Python bridge or claim a subtree sync occurred.

**Done when:** Each scenario has its own observed pass/fail/unavailable result.
A Python-embedding host requires confirmed adoption or its gate stays pending.
For a native backport, establish whether any runtime adaptation is applicable
and distinguish existing verified native code from a new upstream import. Preserve
user settings, request any required device/external-edit authorization, and
turn any demonstrated code bug into its own bounded fix proposal.

**Not included:** pretending mocked tests close a hardware gate, an Android build
system in this repo, or running four integration targets in one small-model run.

## Rules for a smaller model

1. Read project instructions, this introduction and the selected card. Inspect
   branch/status/relevant diffs; the worktree contains earlier user changes.
2. Run one requested card only. Inspect its named files and necessary direct
   dependencies. Do not rescan all effects or reopen completed M/C items.
3. Keep to at most four implementation/test files plus task bookkeeping. A
   broader fix needs a concrete follow-up proposal; do not silently expand.
4. Keep behavioural fixes separate from mechanical refactors. Preserve registry
   order, saved-file semantics, formulas, timing, RNG and render order unless a
   requested task explicitly changes them. Never weaken baselines to pass.
5. Use TODO.md for status/results and a compact continuation note. No second
   task database, generated AGENTS.md edits, commits, version/dependency changes
   or delegation without authorization. Keep secrets outside the checkout.
6. For code, run focused checks and the shared gate below. For documentation-only
   changes, verify claims/paths/commands and whitespace; no full suite required.
7. Report changed files, actual checks, unavailable evidence and the next step
   in at most 150 words. No change is a valid outcome for an investigation.

## Compatibility and acceptance limits

Keep the 32-entry MODES order/names and numeric preset interpretation, with
Spectrum/Waterfall last. Preserve draw(surf, waveform, fft, beat, tick) and
draw_frame(width, height, waveform, fft, beat, tick, renderer=None) arguments;
RGBA output stays a fresh uint8 array shaped (height, width, 4).
Construction requires explicit EffectOptions; config globals stay removed.
Preserve CPU fallback, silent startup, optional librosa and span-child rules.
Python instance isolation does not promise simultaneous independent SDL windows,
audio backends or thread-safe shared GL contexts.

Completion means the selected card's evidence meets its acceptance criteria.
It does not mean all effects are maintainable or all platforms are verified.
Keep structural completion, regression checks and hardware acceptance separate.

## Shared verification gate

Use the existing environment; do not install packages automatically.

```bash
SDL_VIDEODRIVER=dummy SDL_AUDIODRIVER=dummy PYTHONDONTWRITEBYTECODE=1 .venv/bin/python -m unittest discover -s tests -q
SDL_VIDEODRIVER=dummy SDL_AUDIODRIVER=dummy PYTHONDONTWRITEBYTECODE=1 .venv/bin/python smoke_test.py
git diff --check
```

Recorded after C05: 190 tests passed in normal and reversed order; 32 smoke
checks passed. These results were not rerun during this documentation review.
Some tests intentionally log simulated backend failures. Check actual test
results and skips; dummy SDL does not validate an interactive GL window.
Inspect new files explicitly because git diff omits untracked files.
Focused example, with the same environment prefix:
`.venv/bin/python -m unittest tests.test_context -v`.

Recorded migration comparisons: all 32 CPU effects matched pre-migration pixels
exactly over the sampled sequences; seven AMD EGL effects differed by at most
one 8-bit channel step. This is bounded historical evidence, not a universal
visual guarantee or a portable GPU tolerance. R01 addresses reproducibility.

## Reusable prompt

```text
Work on only item <R-ID> from PLAN.md. Read instructions and inspect current
branch/status/relevant diffs first. Follow its file boundary, non-goals and done
criteria. Start with the evidence it calls for; a no-change result is acceptable.
Do not implement adjacent items or reopen completed M/C items. Preserve existing
behaviour except for this item's explicit corrections. Run applicable checks,
record actual results/limits in TODO.md and stop after this item. No commit,
dependency change or delegation. If this is review-only, do not change code.
```
