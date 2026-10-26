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

## Screensaver reliability review fixes

- [x] Prevent hardware-bitmap sampling failures in static/slideshow automatic backgrounds and retain a manual-colour fallback.
- [x] Serialise cache writers, commit each image separately, reserve download space, and preserve active downloads when reading an empty manifest.
- [x] Close HTTP responses when cancellation wins the race with coroutine dispatch.
- [x] Preserve automatic genre detection across audio pause/resume.
- [x] Restore the postmortem overlay on every preview start.
- [x] Share one process-wide postmortem handler across sessions and write reports atomically.
- [x] Copy audio under a short lock and perform visualizer drawing outside the capture lock.
- [x] Add JVM regression tests for cache transactions, cancellation races, postmortem ownership/storage, and audio snapshot/lifecycle behaviour.
- [x] Run the new JVM tests in the GitHub builder (successful run linked below).
- [ ] Perform the device checks in `docs/module-reference.md`; no local Android build.

## Second reliability review fixes

- [x] Select APK signing-query flags correctly on API 21–27 and API 28+.
- [x] Disable automatic redirects for insecure TLS and permit manually checked same-origin redirects only.
- [x] Publish uniquely named static images before committing their selection, serialise imports, retain the previous image, and recover legacy interrupted replacements.
- [x] Reconcile cache contents once per batch while retaining individual download commits.
- [x] Return actual cache/download/failure counts and retry incomplete prefetch work.
- [x] Stage commit-specific releases, download and verify remote APK/AAB assets, and move existing channel tags only after verification.
- [x] Prefer a validated commit-specific APK URL while retaining compatibility with older rolling manifests and clients.
- [x] Keep CI signing material in the runner temporary directory outside the checkout.
- [x] Run four mocked release publication regression tests and check script syntax and workflow YAML.
- [x] Run the new Android JVM tests in the GitHub builder.
- [ ] Device-check updates on API 21–27, replacing an already displayed static image, and self-hosted redirects.

## Third reliability review and startup UX

- [x] Pin every CI checkout to the triggering SHA and assert `HEAD` before building.
- [x] Snapshot diagnostic history before startup and store recoverable failures separately from fatal postmortems.
- [x] Give each engine run a cancellable child job; cancel fetch/cache/weather work on stop and reject stale visualizer diagnostics.
- [x] Clear slideshow items/source state on stop and route remote input by the active mode.
- [x] Start manual visualizer mode on an enabled effect.
- [x] Bound Google Drive response bytes, pages, scanned entries, accepted images and repeated page tokens.
- [x] Start the slideshow with the first useful source; append later images without restarting the current image or timer.
- [x] Add focused JVM coverage for session cancellation, progressive source delivery, fatal-report preservation, visualizer selection, response byte limits and Drive pagination budgets.
- [x] Verify workflow SHA pinning and rerun the four mocked release tests.
- [x] GitHub builder: compile/run Android JVM tests.
- [ ] Device: stop/restart preview across all modes, check old report plus a new startup error, and confirm fast-source startup while another source is offline.

## Playback recovery, HTTP lifetime and freshness

- [x] Track slideshow loading/waiting/stalled state; resume with later source results and attempt offline fallback once per session when every image fails.
- [x] Keep HTTP cancellation attached until response-body closure so cancellation interrupts active socket reads, and retire the watcher on normal close.
- [x] Route diagnostic remote controls before mode navigation: Up/Down scroll, Left/Right page, Center/Back dismiss; consume dismissal key-up and preserve volume controls.
- [x] Separate permanent/authentication/parse/unsupported-image failures from transient HTTP/network failures and cap prefetch retries at three per periodic run.
- [x] Preserve source HTTP status classification and token-refresh network failures through provider enumeration.
- [x] Refresh cached photos on revision changes or after 24 hours; retain the old file if replacement fails and use distinct replacement filenames/signatures.
- [x] Add regression cases for late healthy sources, broken fallback-cache loops, stalled body cancellation, normal HTTP close, remote policy, source retry classification and cache revision/age/failure behaviour.
- [x] Check diff whitespace and parse the updated string resources.
- [x] GitHub builder: compile and execute the new JVM tests.
- [ ] Device: exercise actual Glide failures followed by a healthy source; cancel stalled downloads; scroll/dismiss long reports in dream and preview; confirm edited photos refresh offline.

## Settings fallback, schedule and updater safety

- [x] Make the settings startup error Fragment public with a public no-argument constructor so AndroidX can add and restore it.
- [x] Apply the schedule-enabled flag to both admission and the scheduled shutdown timer; cover disabled daytime/overnight/all-day windows.
- [x] Ignore repeated update clicks while busy, serialise update preparation, and use per-attempt temporary files plus reusable verified APKs keyed by version/checksum.
- [x] Revalidate byte count, checksum, package version and signers before reusing a cached update.
- [x] Share lazily allocated triangle/line vertex staging, reducing its full budget from 24 MiB to 12 MiB without changing the vertex cap or primitive order.
- [x] Add JVM regression tests for fallback-Fragment visibility, schedule gating, cancelled update attempts, verified artifact reuse and vertex storage allocation.
- [x] GitHub builder: compile/run JVM tests.
- [ ] Device: force settings startup failure and recreate the Activity; confirm disabled scheduling stays active past its former end time; repeat update clicks/cancel/retry; profile actual low-memory-TV renderer usage.

## Confirmed GitHub verification — 2026-10-26

- [x] Commit `b6266b0`: Android JVM tests, signed dev APK build, release publication and downloaded-asset verification passed in [run 40330812147](https://github.com/Whichcraft/androsaver/actions/runs/40330812147).
- [x] Preserve concurrent remote dev visualizer/versioning updates through merge `d50e4d6`.
- [x] Correct cancellation-triggered body-read IOExceptions after the first CI run exposed the regression.

Device-only checks above remain open; CI success does not mark them complete.

## Retention, clock changes, performance display and Graph pagination

- [x] Bound new update staging to three APKs / 200 MiB; prune obsolete installed versions and old unprotected stages, protect recent stages for 24 hours, and never prune the current temporary download.
- [x] Refuse a new download when only protected stages occupy the budget and explain the cache-clearing option in settings.
- [x] Monitor system time/timezone changes for the active engine session, cancel/recalculate the shutdown timer, and refresh clock formatting; recheck wall time when a deadline fires.
- [x] Connect dev visualizer performance samples to a dedicated overlay with one-second UI throttling and session guards, keeping crash diagnostics above it.
- [x] Display effect, viewport, frame time/FPS, vertices, bloom and allocated vertex staging; keep release builds hidden.
- [x] Validate every bearer-authenticated OneDrive page against HTTPS Graph origin, reject repeated URLs, preserve opaque cursors and disable listing redirects.
- [x] Add regression cases for retention quotas/protection, timezone and wall-clock shifts, performance formatting/throttling, and untrusted/repeated Graph links.
- [x] Validate changed XML resources and diff whitespace.
- [x] GitHub builder: new JVM tests, signed APK, publication and downloaded-asset verification passed for `aeec139` in [run 40335301941](https://github.com/Whichcraft/androsaver/actions/runs/40335301941).
- [ ] Device: change time/timezone during a running schedule, observe dev performance samples and diagnostic priority, exercise update-storage-full UI, and enumerate a multi-page OneDrive folder.

## Deep review follow-up (2026-10-26)

- [x] Route cached Java file URIs by scheme through a shared Glide model boundary; import asset and local URIs correctly.
- [x] Reject stale Google, OneDrive and Dropbox sign-in/refresh responses after disconnect, configuration changes or account replacement.
- [x] Extract source refresh coordination and per-source snapshots; preserve last good images on failure and deliver healthy refreshes progressively.
- [x] Preserve Dropbox partial/transient failures for prefetch retries and stop queued link requests after HTTP 429.
- [x] Record metadata-only session markers in every build/mode and distinguish unclean shutdowns from captured fatal exceptions.
- [x] Show persistent source health, recovery guidance and offline-cache playback status.
- [x] Add JVM regressions for account races, snapshots, refresh ordering, partial links and session journals.
- [x] Add Android integration tests for real cache/Glide loading, static imports, all-mode preview lifecycle/recreation, GL samples and APK FileProvider grants.
- [x] Fix the static-image resume blank exposed by the emulator suite: engine-owned requests use the application RequestManager, avoiding competing Activity lifecycle ownership.
- [x] Gate APK publication on GitHub emulator tests on API 29 and 35.
- [x] Verify this follow-up's JVM tests, device tests and signed APK build/publication on GitHub: [run 40344834235](https://github.com/Whichcraft/androsaver/actions/runs/40344834235), app commit `e553363bfbb5e85ebafcdb64f69b8e8bb89d7477`. All four Android integration tests passed on both API 29 and 35; downloaded release assets and `dev-channel` target were verified.
- [x] Verify automatic screensaver activation on the physical TV: user confirmed it works flawlessly (2026-10-26).
- [ ] Run physical-TV checks for audio capture and external installer completion (see docs/module-reference.md).

## Provider security and lifecycle follow-up (2026-10-26)

- [x] Honour Synology transport/security consent without automatic certificate bypass or HTTP downgrade; show the actual connection transport.
- [x] Confine authenticated self-hosted API/image redirects to the configured origin with validated TLS as well as explicit insecure TLS.
- [x] Replace obsolete Immich library/album listing with authenticated metadata search, bounded page/cursor pagination, revisions and versioned contract fixtures.
- [x] Lease offline-playlist files across cache instances, protect old generations during refresh, and account for leased files within storage/count limits.
- [x] Preserve authoritative encrypted credentials when migration retries after failed plaintext cleanup.
- [x] Keep MediaStore null/failed/partial queries distinct from successful empty libraries.
- [x] Attempt every shutdown action, retain cleanup failure causes, and preserve unclean-session evidence across overlapping sessions.
- [x] Show audio permission/backend/signal status; require an explicit Enable audio response action rather than prompting on mode changes.
- [x] Split emulator lifecycle tests into independent mode cases and add provider/security/migration/cache/cleanup/audio regressions.
- [x] Verify this follow-up on GitHub: JVM suite, API 29/35 device cases, signed APK build/publication and downloaded-asset verification passed in [run 40356857225](https://github.com/Whichcraft/androsaver/actions/runs/40356857225) for `861f9eef687f8762e07327a728ba8cafa8c61596`; `dev-channel` target confirmed.

## Account-scoped cache, playback and settings follow-up (2026-10-26)

- [x] Namespace remote images by account and source configuration; exclude disabled, disconnected, replaced and legacy unscoped accounts from offline playback.
- [x] Separate account replacement from routine token refresh so concurrent sign-in remains valid.
- [x] Clear slideshow views and pending requests on successful empty refresh; pass both media key actions through blank mode.
- [x] Share a stable fair cache plan across providers, reserve unavailable-provider quotas, and bound warming to 200 images, 100 MiB transferred and 90 seconds per round.
- [x] Retain Google/OneDrive device authorization across Activity recreation without persisting device codes.
- [x] Validate and canonicalize host-only fields for setup, probes and stored configurations.
- [x] Add settings to view/copy/clear diagnostic reports and inspect/clear saved images by provider; preserve active-session markers and leased images.
- [x] Show last successful source refresh and next scheduled check.
- [x] Add JVM and Android regressions for these transitions.
- [ ] Verify JVM tests, API 29/35 emulator tests and signed APK publication on GitHub.
- [ ] Check account switching, source management and authorization recreation against real providers on the TV.
