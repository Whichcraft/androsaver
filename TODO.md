# TODO

These items were reviewed and implemented in the current change. Android builds
and device verification must run in GitHub CI; do not build the Android project
locally.

- [x] Deliver slideshow remote-control events to the dream and cover the four-mode interaction policy with a JVM test.
- [x] Restrict Nextcloud WebDAV image URLs to the configured origin and encode configured path components safely.
- [x] Bind self-hosted insecure TLS to exact source endpoint metadata through slideshow, cache, and static selection.
- [x] Make self-hosted connection probes use immutable form arguments without mutating shared preferences.
- [x] Bound provider enumeration by accepted images, pages, scanned entries, response bytes, and cursor progress.
- [x] Handle static import failures at the picker/browser boundary, preserve the previous image, and restore UI state.
- [x] Guard encrypted-preference reads/writes in OAuth and self-hosted setup screens; require checked saves before auth.
- [x] Report revoked Device Photos permission separately from an empty library.
- [x] Keep one static-browser image-count footer during out-of-order source completion.
- [x] Reconcile missing, truncated, corrupt, and stale image-cache manifest entries before reuse.
- [x] Use orientation-independent bounded Glide downsampling and resolve landscape/portrait display behavior from the decoded image.
- [x] Validate updater package version/signers and make download streaming cancellation-aware.
- [x] Use both projected ring centers in TunnelMode connectors.
- [x] Remove the largest identified hot-loop allocations in TunnelMode, NovaMode, and LatticeMode.
- [x] Pin all GitHub Actions to reviewed commit SHAs, use setup-java v5, and configure Dependabot.
- [x] Publish rolling releases before moving their tags, then verify assets, checksums, sizes, and tag targets.

## Release content

- [ ] Replace the bundled slideshow example images with a curated set of public-domain photos before release. This is intentionally manual.

## Android visualizer improvements

These goals are intentionally self-contained. Do not build the Android project
locally; use GitHub Actions for Android verification. Preserve the existing
OpenGL ES 2.0 renderer and keep each goal in a separate commit.

- [x] **Add multi-viewport JVM coverage for visualizer modes.**
  - Test every newly split psysuals mode at 1920×1080, 3840×2160, portrait, and
    tiny surfaces.
  - Include empty and short audio/FFT inputs where the mode reads audio data.
  - Assert that drawing does not throw and that reset/surface recreation is safe.
  - Do not require an Android emulator or local Android build.

- [x] **Make scalar-field effects sharper on large TVs.**
  - Replace the fixed 24×16 field resolution with a bounded adaptive grid based
    on the viewport short edge and aspect ratio.
  - Keep a safe lower bound for small devices and a strict upper bound for 4K
    TV performance.
  - Preserve the current aspect correction and black trail behavior.
  - Verify that Morphogenesis, Phason, Cymatica, LiquidLight, and Mandelbox no
    longer appear as undifferentiated color blocks at 1920×1080 and 3840×2160.

- [x] **Improve Android Tesseract detail and upstream parity.**
  - Keep the current scale breathing and beat bounce.
  - Add bounded higher-detail geometry or presets without exceeding the GLDraw
    vertex budget.
  - Make preset/reset state deterministic across mode changes and surface
    recreation.
  - Compare the result with upstream `psysuals/effects/tesseract.py` and record
    intentional GLES differences in `docs/psysuals-port-notes.md`.

- [x] **Reconcile trail documentation with the shared fade policy.**
  - Update stale per-effect comments and port notes that still describe lower
    effective fade values such as 5/255 or 8/255.
  - Document that `GLDraw.fadeBlack()` applies a minimum 48/255 black overlay.
  - Confirm that no effect relies on a deliberately longer trail before
    changing its visual behavior.

- [x] **Audit all visualizer modes for large-TV bounds and performance.**
  - Review every mode for off-screen geometry, oversized radii, unbounded
    particle counts, and GLDraw batch overflow at 1920×1080 and 3840×2160.
  - Prefer viewport-relative geometry and bounded allocations.
  - Add targeted fixes only where the audit finds a concrete issue; do not
    rewrite stable effects unnecessarily.

- [x] **Add a developer-only visualizer performance overlay.**
  - Show active mode, viewport size, frame-time/FPS estimate, submitted vertex
    counts, and bloom enabled/disabled state.
  - Keep it disabled in release builds and avoid allocations in the render loop.
  - Use it to identify effects that overload low-end Android TV hardware.

- [x] **Separate shared visualizer utilities from psysuals-specific helpers.**
  - Move generic trail and viewport helpers out of `PsysualsFieldMode.kt` into
    an appropriately named visualizer utility/base class.
  - Keep psysuals effect files focused on effect behavior.
  - Update architecture documentation and verify there are no duplicate helper
    implementations.
