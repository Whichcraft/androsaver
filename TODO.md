# TODO

## Release identity, ordering and resumption — 2026-11-11

- [x] Own version `2.12.9` / code `390` explicitly in app/build.gradle;
  remove Git-derived counters and silent fallback versions.
- [x] Generate the update manifest from the built APK; verify its embedded
  package/version, SHA-256, size and pinned URL before publication and reuse.
- [x] Reject obsolete/divergent runs and non-increasing codes before rolling
  asset updates; retain workflow-wide serialization and use a leased tag push.
- [x] Reuse validated published assets for the same commit, including AAB
  integrity evidence; recover interrupted drafts/uploads/tag pushes.
- [x] Pass 21 publication regression tests, including real-Git annotated-tag
  checks, ShellCheck, Bash syntax, workflow YAML/shell validation and diff checks.
- [x] GitHub builder: build the release, confirm APK/manifest version equality,
  and complete actual dev publication with the updated release script; dev
  2.12.11 (code 392) published in run 43314405018 with manifest and APK
  checksums matching.

## Subtree and repeated interruption notice — 2026-11-11

- [x] Fetch psysuals upstream and verify the vendored `2080070` tree exactly
  matches main/dev merge `4be8a7c`; no content changes require Android backports.
- [x] Gate the startup interruption report on the immediately preceding session's
  marker, retaining saved history and fatal/recoverable diagnostics.
- [x] Add regression coverage for repeated clean restarts after an interruption
  and preserved diagnostic history/fatal/recoverable reports.
- [x] Run the new JVM regression in the GitHub builder: verification and API
  29/35 integration passed in run 43046887556; its later release-tag check failed.

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
  - Verify that Phason, Cymatica, and LiquidLight no
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
- [x] Verify JVM tests across all variants, all 11 integration tests on each API 29/35 emulator, signed APK publication and downloaded-asset integrity: [run 40396730355](https://github.com/Whichcraft/androsaver/actions/runs/40396730355), app commit `bae3f6db757ef2ac09f0c448a35f75a7ce432039`; `dev-channel` target confirmed.
- [ ] Check account switching, source management and authorization recreation against real providers on the TV.

## 2.12 cache integrity, diagnostics and recovery (2026-10-26)

- [x] Include account/stable identity/revision in Glide signatures, disable its duplicate persistent photo cache, and remove legacy Glide disk data.
- [x] Persist complete source-list authority, exclude deleted images after restart, and reject late cache writers without invalidating active leases.
- [x] Stage downloads before eviction, use actual sizes, and preserve existing images after failed downloads.
- [x] Track OneDrive, Dropbox, Nextcloud and Synology revisions.
- [x] Preserve typed provider failure causes/HTTP status and distinguish expired, future and untrusted TLS certificates without storing request secrets.
- [x] Bound Google/OneDrive device-auth responses and validate codes/tokens before display/publication.
- [x] Collect Android process-exit metadata and available ANR stack frames; retain the journal fallback on older Android versions.
- [x] Add persistent source status, Retry now, active-playback notification, connectivity recovery, explicit report-read errors and cache-clearing progress.
- [x] Bump the minor version to 2.12.0 and add cache/auth/diagnostic/provider regression coverage.
- [x] Verify the JVM suite across all eight variants, all 14 integration tests on each API 29/35 emulator, signed APK publication and downloaded-asset integrity: [run 40402500712](https://github.com/Whichcraft/androsaver/actions/runs/40402500712), app commit `479075d8c094866818cb5e37e84febe9ee012d5e`. Published manifest confirms version `2.12.0`, code `377`; `dev-channel` target confirmed.
- [ ] Physical TV: verify real certificate failures, connectivity restoration, cache cleanup and system exit evidence with real providers.

## psysuals 3.20.0 source sync (2026-10-26)

- [x] Update the squashed subtree from `4dc8095` to upstream main `0579390` and verify exact tree identity.
- [x] Review upstream registry removals; the later Android backport below supersedes the initial source-only boundary.
- [x] Pass 127 Python unit tests, headless smoke rendering of all 32 upstream effects, and whitespace checks without changing dependencies.
- [x] Verify the source-only sync on GitHub: JVM/device tests, signed APK publication and downloaded-asset verification passed in [run 40410593752](https://github.com/Whichcraft/androsaver/actions/runs/40410593752) for `a93b4cc`; native-backport verification is tracked below.

## psysuals 3.20 Android backport

- [x] Remove Morphogenesis/Mandelbox implementations and settings entries; match upstream 32-mode registry and prune stored selections safely.
- [x] Port elapsed-time motion, visual profiles, separate response envelopes, latest effect behaviour and resize-state preservation with documented mobile limits.
- [x] Clear scene, bloom and display buffers on effect changes; present black switch frames and add EGL regression coverage.
- [x] Update current visualizer, architecture, music-reactivity, store and release documentation.
- [x] Verify the native backport: JVM tests across all eight variants, all 16 device tests on each API 29/35 emulator, signed APK build/publication and downloaded-asset integrity passed in [run 40414215709](https://github.com/Whichcraft/androsaver/actions/runs/40414215709) for `18661a1d4153d4c9ceec0591e98f31afa7fe8e8c`. Published version `2.12.5`, code `384`; `dev-channel` target and immutable APK manifest confirmed.
- [ ] Physically inspect all changed effects on the TV, including high-gain audio, profiles and mode switching.


## Magnetar magnetic-field contour backport

- [x] Confirm Android-only omission against the vendored upstream contour renderer.
- [x] Restore coloured dipole bands using the existing GLES2 full-screen helper;
  preserve 4,000 particles, motion and beat shockwave, with coloured trail decay.
- [x] Add device regression coverage for field visibility, resize, sustained audio
  and EGL context recreation; document native composition differences.
- [ ] Verify JVM/device/build checks on GitHub for the exact fix commit. No local
  Android build. Physical TV acceptance remains separate.
- User requested combined verification/publication at the end of all changes:
  leave this fix uncommitted; do not commit, push, trigger CI or publish yet.
- Local source review confirmed particle motion/drawing blocks are unchanged;
  git diff --check passed. Android JVM/device/build checks have not been run.


## Slowest-four performance and SlimeMold detail — pending combined verification

- [x] Compare the four slowest desktop-GL effects with native counterparts:
  SlimeMold, Lattice, Hyperbolic and LiquidLight. Desktop timings are in the
  psysuals worktree TODO; Android's bottom-four ranking is not yet measured.
- [x] Prepare native finer SlimeMold texture/detail/colour parity, merged Lattice
  beams, cached Hyperbolic transforms/hinges/sort depths and LiquidLight waves.
- [x] Add JVM work/detail invariants, SlimeMold GLES palette/filter/resize/context
  checks, and an opt-in all-mode 1080p GPU-completion timing survey.
- [x] Document intentional native SlimeMold quantization, population/RNG and
  1080p field cap in docs/psysuals-port-notes.md.
- [ ] Run Android JVM/device checks and the opt-in fpsSurvey on the GitHub
  builder or target device at the final batch; no local Android build.
- [ ] Verify SlimeMold's fine veins and smooth colour on the actual Android TV;
  compare with desktop GL and record physical-device frame-time results.
- User instruction: keep all changes uncommitted; do not push, trigger CI or
  publish before the combined batch. Upstream subtree sync is also deferred.


Performance pass local audit: native source/whitespace review passed; all Android
JVM/device/build/FPS checks remain unrun pending the combined batch. Desktop's
corresponding four effects passed 207 Python tests, all 32 smoke checks and
sampled exact CPU/EGL output comparisons. Desktop GL render rates improved from
12.1 to 22.3 FPS (SlimeMold), 21.8 to 42.9 (Lattice), 23.0 to 40.1 (Hyperbolic),
and 29.0 to 39.9 (LiquidLight). These are not Android FPS claims. SlimeMold's
higher native simulation detail/population must be measured on the actual TV;
draw-call reduction alone does not prove a net speedup. Nothing was committed,
pushed, built or published. Pending Magnetar changes remain intact.


## Goal 1, 3, 4 — all remaining effects

- [x] Prepare stronger native Hyperbolic pulse/lift/lighting and common viewport
  fit; add short-kick recovery and loud-input viewport tests.
- [x] Review all native counterparts alongside the upstream all-32 CPU/EGL
  survey; preserve effect detail and existing bounded work budgets.
- [x] Prepare bounded shared circle-direction caching with exact-vertex JVM
  coverage, Cymatica static-basis caching/energy reuse, and particle/field HSL
  scratch reuse in FlowField, Magnetar and PsysualsFieldMode.
- [x] Cache Branches per-arm/depth colours and per-frame depth sway without
  reducing recursive geometry; prepare a full-tree line-count JVM test.
- [ ] Run the prepared JVM/device checks and all-mode opt-in FPS survey in the
  combined GitHub batch, then compare SlimeMold/Hyperbolic/Magnetar on the TV.
- Native changes are uncompiled/unverified locally under AGENTS.md. Source
  import, commits, pushes, CI and publication remain deferred by the user.
- Backups/patches for this continuation are outside the checkout under
  /tmp/androsaver-goal134-remaining and /tmp/androsaver-goal134-final.


Final survey-readiness review (source-only): fixed the opt-in instrumentation
survey's incorrectly labelled p95 (previously the maximum), using the same
linear percentile convention as desktop; log maximum separately. Record actual
bloom status rather than hard-coded true, profile/dt/audio fixture metadata,
and require 32 unique registry modes with finite positive samples. Optional
fpsProfile=tv selects the physical-TV fixture; default laptop preserves the
existing desktop-comparison fixture. Documentation includes the runner flag.
No workflow changed or triggered. These Android test edits remain uncompiled
and unrun under the local-build prohibition and final-batch instruction.


## ChromaWeave — new psychedelic mode

- [x] Prepare full-resolution GLES2 weave shader and bounded audio/phase state,
  matching upstream's new ChromaWeave equations without per-frame scalar arrays.
- [x] Register mode and settings entries before Spectrum/Waterfall (33 total);
  update all-mode survey and registry integration expectation.
- [x] Prepare pulse/release/profile JVM coverage and GLES colour/motion/resize/
  context-recreation checks; update README and backport notes.
- [ ] Verify native compilation, JVM/device rendering and target-TV appearance/
  FPS in the combined GitHub batch. These native checks have not run locally.
- Source subtree import, commit/push, CI and publication remain deferred.


ChromaWeave local desktop checks: all 221 Python tests (eight new focused weave
checks), all 33 smoke effects, real AMD EGL versus CPU field comparisons,
resize/context rebind, normal/TV pulse-release and bounded CPU sampling passed.
Existing visual-baseline entries/tolerances were preserved; only the new mode's
baseline was added. Native registry/label/value arrays match all 33 upstream
names, and the native fragment source matches the GLES2-adapted desktop field.
Those source checks do not replace the pending native compile/device checks.
The native test/README/backport changes remain uncommitted and unbuilt.


## Authorized combined commit/push/dev batch — 2026-11-11

The user has authorized commit, push and merge to dev, superseding earlier
hold notes. Upstream 2080070 is pushed and merged to dev; Python 3.10/3.12 CI
passed (run 43042966999). Imported psysuals/ tree exactly equals that commit;
restored all 21 native files byte-for-byte from the reviewed external backup.

- [x] Upstream committed/pushed, Python CI passed and merged to dev.
- [x] Import reviewed upstream source as an exact psysuals subtree.
- [ ] Commit/push native backports and fast-forward dev; observe JVM/device CI
  and dev APK build/publication for the exact source commit.
- [ ] Optional 33-mode FPS survey and actual TV visual/FPS acceptance remain
  separate; existing default CI skips the optional survey.
- No local Android build. Recovery stash and pre-import files are retained
  outside the source batch; no main/master changes are authorized.

## Cube, Spiral and Magnetar look rework — 2026-11-13

- [x] Restore the cube's slow line afterglow with the upstream 18/255 trail
  fade, opting out of the shared 48/255 floor per effect.
- [x] Shorten the spiral's trails and enlarge its periodic ring bubbles.
- [x] Composite Magnetar on one level: black trail fade, additive contours and
  particles in the same buffer, slower band drift, weaker bass response.
- [x] Add a JVM regression for the cube and spiral trail fades, including the
  frame-rate correction; keep the magnetar covered by the device suites.
- [x] Push the matching upstream psysuals changes: Lattice assigns its FFT bins
  centre-out so the bass no longer drives the leftmost column, and Magnetar's
  contour layer drifts slowly with a weaker bass response.
- [ ] Run the Android JVM/device checks on GitHub for the exact commit and
  confirm Cube, Spiral and Magnetar on the target TV.
- [ ] Re-import the psysuals subtree once upstream `main` carries the Lattice
  and Magnetar changes; the vendored snapshot is still upstream 3.21.0.

## Visualizer rework batch — 2026-11-13

- [x] LiquidLight: replace the analytic field with a bounded dye simulation
  (velocity grid, semi-Lagrangian advection, beat vortices, textured shading).
- [x] Cymatica: evaluate the Chladni plate per pixel in a GLES2 shader so the
  nodal lines are smooth instead of blocky field cells.
- [x] Tunnel: make the ride speed, tube width and winding follow the music.
- [x] Persistence: music-driven size pulse and faster colour cycling; the
  upstream psysuals counterpart is pushed on its `dev` branch.
- [x] Ferrofluid: springy beat-driven peaks and a rainbow skin.
- [x] Chromatic: spread the drops over the frame and soften the treble warp.
- [x] SlimeMold: seed across the whole screen and vary the colour faster.
- [x] Phason: more music-driven movement; Tesseract: bounded geometry trail.
- [x] Remove the now-unused shared field painter; keep the device suite
  covering all 33 registry modes and add Cymatica and LiquidLight visibility.
- [ ] Run the Android JVM/device checks on GitHub for the exact commit and
  confirm the reworked effects on the target TV.

## Mycelium 3.21 port — 2026-11-13

- [x] Port the psysuals 3.21 colony model: bounded node, edge, tip, packet,
  spore and body pools with a free-list allocator.
- [x] Grow the colony from three fruiting bodies with a warm-up, tapered
  filaments, junction loops, packet handover, spore germination and a
  keep-alive heartbeat in silence.
- [x] Render it natively: opaque indigo ground, body glows, tapered filament
  glow and core lines, packet tails, spores, sparks and rings.
- [x] Keep the colony model free of GL state and cover it with JVM checks for
  bounded pools, packet routing, germination, silence and resize.
- [ ] Run the Android JVM/device checks on GitHub for the exact commit and
  confirm the colony on the target TV.
