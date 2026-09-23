# TODO

Preserve the current effect registry order and public `Effect.draw(...)` contract.
Every implementation task should include focused tests, documentation updates
where behavior is user-visible, the full unit suite, the headless smoke test,
and `git diff --check`.

## Major-release work (outside the current improvement queue)

This work is intentionally separate from the current improvement queue. Do not
bump the major version until the prerequisite v4 implementation and review work
is complete.

- [ ] **V4-003 — Prepare and write the v4 major-release announcement**
  - **Scope:** `CHANGELOG.md`, a release section in `README.md`, version badges/help text, and a release checklist; do not publish or tag automatically.
  - **Change:** Draft an energetic but factual announcement explaining why v4 is awesome: the new courtship-and-dance Butterflies system, audio-phase/envelope responsiveness, palette continuity, adaptive quality tiers, deterministic visual regression coverage, curated recipes, optional psychedelic post-processing, and the other verified improvements actually present in the release.
  - **Versioning:** Identify every version string and update location, document migration notes from v3, list compatibility/setup changes, and specify the exact verification commands. Bump to `4.0.0` only after the prerequisite v4 work is complete and the release checklist is approved.
  - **Acceptance:** The announcement contains no unimplemented claims, all listed highlights map to code/tests, version strings are consistent when the bump is performed, and the full unit suite, smoke test, compile check, and `git diff --check` are recorded.
