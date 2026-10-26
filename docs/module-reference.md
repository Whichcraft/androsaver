# AndroSaver module reference

This is the complete source inventory for the repository. It is intentionally
more granular than the architecture overview: each Android source module is
listed so a change has an obvious documentation home. Paths are relative to
the repository root.

## Android application modules

### Dream lifecycle and orchestration

| Module | Responsibility | Important boundary |
|---|---|---|
| `ScreensaverService.kt` | Android `DreamService` entry point; creates the view binding, engine, coroutine scope, and input policy. | Attaches/detaches the engine with the Dream lifecycle; never owns provider or visualizer details. |
| `PreviewActivity.kt` | Launches the same engine from Settings without waiting for the system dream timeout. | Stops audio/GL when paused or stopped; Back finishes preview. |
| `ScreensaverEngine.kt` | Coordinates mode selection, slideshow/static image/blank/visualizer rendering, overlays, transitions, cache refresh, remote input, and cancellation guards. | One engine session at a time; session and sequence tokens reject late async results. |
| `DreamInteractionPolicy.kt` | Decides whether the Dream window receives input. | Visualizer, slideshow, and blank modes are interactive; Static Image is intentionally not marked interactive. |
| `SettingsActivity.kt` | Main settings host, mode-dependent preference visibility, source browser, permissions, colors, update status, and source status summaries. | Uses `SecurePreferenceDataStore`; Android build variants hide the standard updater UI in Play Store builds. |
| `Prefs.kt` | Central constants for every preference key and mode value. | Add keys here before using them anywhere else. |
| `ScheduleWindow.kt` | Pure active-hours and time-until-end calculations. | Same start/end means always active; overnight ranges cross midnight. |

### Storage, images, and rendering policy

| Module | Responsibility | Contract |
|---|---|---|
| `ImageSource.kt` | `ImageItem`, source result/error taxonomy, and the provider interface. | `enumerate()` converts expected provider failures into non-secret UI/worker outcomes and rethrows cancellation. |
| `ImageSourceRegistry.kt` | Builds enabled providers in fixed order and optionally supplies bundled defaults. | Shared by slideshow, static browser, and prefetch; default assets are only a slideshow fallback when no provider is enabled. |
| `ImageCache.kt` | Downloads, validates, indexes, evicts, and reconstructs offline images. | Maximum 200 entries / 150 MiB; individual downloads max 50 MiB; manifest writes are atomic and contain stable IDs, not URLs or credentials. |
| `StaticImageStore.kt` | Copies a selected local/content/remote image into app-private storage. | Uses a temporary file and previous-copy backup; validates decodability before replacing the current image; max 50 MiB. |
| `ImageBackground.kt` | Paints manual or sampled-gradient unused-space backgrounds. | Auto mode samples three image points and darkens them; manual mode uses the configured color. |
| `ImageBehavior.kt` | Normalizes crop/fit/center/stretch and resolves portrait vs landscape settings. | Fit is the default for both orientations and both image modes. |
| `AndroSaverGlideModule.kt` | Connects Glide to the shared OkHttp image transport. | Per-request endpoint metadata can select the explicitly authorized self-hosted insecure client. |
| `HttpClients.kt` | Shared standard and opt-in trust-all OkHttp clients. | Standard TLS validates normally; trust-all is selected only when the exact configured scheme/host/port matches. |
| `HttpCallExtensions.kt` | Coroutine-friendly OkHttp response awaiting. | Callers own response-body closing and cancellation behavior. |

### Background work, security, weather, and updates

| Module | Responsibility | Runtime behavior |
|---|---|---|
| `BootReceiver.kt` | Receives `BOOT_COMPLETED`. | Enqueues WorkManager; does not perform network work inside the short broadcast window. |
| `PrefetchScheduler.kt` | Owns unique periodic prefetch registration. | Requires network, runs every 12 hours, and uses WorkManager backoff. |
| `ImagePrefetchWorker.kt` | Queries configured remote sources concurrently and warms the cache. | Excludes MediaStore, gives each source 60 seconds, round-robins up to 200 items, retries when every source fails. |
| `SecurePreferences.kt` | SharedPreferences facade that routes sensitive keys to `EncryptedSharedPreferences`. | AES-256-backed key/value encryption; migration writes encrypted values before removing plaintext; secure writes fail closed. |
| `SecurePreferenceDataStore.kt` | Bridges AndroidX Preference UI reads/writes to the secure facade. | Keeps settings XML decoupled from storage implementation. |
| `SourceSetupValidation.kt` | Validates self-hosted host syntax and required fields. | Rejects whitespace, slashes, and embedded schemes in host fields; port validation belongs to setup activities. |
| `WeatherFetcher.kt` | Fetches current OpenWeatherMap conditions and parses temperature/description. | HTTPS only, 10-second connect / 15-second read timeout, one-city 30-minute local cache; no GPS. |
| `UpdateChecker.kt` | Reads the build-selected GitHub `version.json` manifest. | Requires HTTPS, GitHub host, exact channel APK path, valid SHA-256 and size, and a higher version code. |
| `UpdateInstaller.kt` | Downloads, hashes, validates, and hands an APK to Android Package Installer. | HTTPS, 100 MiB maximum, declared-size and SHA-256 checks, package/version/signature checks, non-exported FileProvider. |

### Provider setup and authentication activities

| Module group | Components | Flow |
|---|---|---|
| Google Drive | `GoogleDriveSetupActivity.kt`, `GoogleAuthActivity.kt`, `GoogleAuthManager.kt` | Device authorization; client ID/secret and optional folder ID; access/refresh tokens auto-refreshed. |
| OneDrive | `OneDriveSetupActivity.kt`, `OneDriveAuthActivity.kt`, `OneDriveAuthManager.kt` | Microsoft device authorization; client ID and optional folder path; token refresh. |
| Dropbox | `DropboxSetupActivity.kt`, `DropboxAuthActivity.kt`, `DropboxAuthManager.kt` | App Key/Secret plus authorization code; refresh-token flow. |
| Immich | `ImmichSetupActivity.kt` | Host/port/HTTPS, API key, optional album; connection test before save. |
| Nextcloud | `NextcloudSetupActivity.kt` | Host/port/HTTPS, username/app password, folder; WebDAV connection test. |
| Synology | `SynologySetupActivity.kt` | DSM host/port/HTTPS, optional insecure transport, credentials, folder; FileStation connection test. |

Setup activities load saved values, validate required input, save through the
secure preference facade, and report provider-specific status. Revoke/clear
actions remove OAuth tokens where the provider supports them; disabling a
source does not delete its credentials or cache entries.

## Image provider modules

| Module | API and selection behavior | Limits/notes |
|---|---|---|
| `GoogleDriveSource.kt` | Drive v3 image MIME listing, optional folder. | Up to 2,000 returned, 40 pages, or 20,000 scanned entries. |
| `OneDriveSource.kt` | Microsoft Graph children listing, optional path. | Up to 2,000 returned, 40 pages, or 20,000 scanned entries. |
| `DropboxSource.kt` | Dropbox v2 folder listing and temporary download links. | Up to 2,000 returned, 100 pages, or 20,000 scanned entries; link work is capped at 10 concurrent requests. |
| `ImmichSource.kt` | Immich asset listing, optional album UUID. | Up to 2,000 returned, 40 pages, or 20,000 scanned entries. |
| `NextcloudSource.kt` | WebDAV `PROPFIND` with same-origin response filtering. | Encodes path segments, bounds XML, and never attaches credentials to an off-origin response URL. |
| `SynologySource.kt` | DSM FileStation login/list/download. | SID lives only in memory; re-authentication is bounded and endpoint transport is explicit. |
| `LocalStorageSource.kt` | Android MediaStore images. | Requires the API-appropriate read permission and returns up to 500 recent images. |
| `DefaultImagesSource.kt` | APK assets under `app/src/main/assets/default_images/`. | No credentials; slideshow fallback only when no provider is enabled. |

All providers produce stable, non-secret cache identities. Temporary URLs,
headers, bearer tokens, and session IDs remain in memory and are not written
to the cache manifest.

## Visualizer modules

| Module | Responsibility |
|---|---|
| `AudioData.kt` | Reusable snapshot containing beat, mid, treble, gain, waveform, and smoothed FFT arrays. |
| `AudioEngine.kt` | Captures global mix through Android `Visualizer`, converts waveform/FFT bytes, smooths spectrum, computes beat and deviation bands, and performs optional genre detection. |
| `GLDraw.kt` | GLES 2.0 shader, matrix, primitive, batching, blend, trail, and bloom/FBO utilities. |
| `VisualizerRenderTuning.kt` | Shared viewport scale, bounded field dimensions, and minimum trail fade policy. |
| `VisualizerRenderer.kt` | GL-thread mode registry, atomic mode requests, reset/context lifecycle, frame timing, and dark fallback after a render error. |
| `VisualizerView.kt` | `GLSurfaceView` lifecycle plus enabled-mode filtering, next/previous/random selection, and audio start/stop. |
| `modes/BaseMode.kt` | Effect contract: `draw`, reset, and EGL context recreation hook. |
| `modes/PsysualsFieldMode.kt` | Shared bounded 64×36 scalar-field storage, viewport mapping, adaptive interpolation, trail clearing, and field painting for six field effects. |
| `modes/*Mode.kt` | 34 registered effects plus dormant `CliffordMode.kt`; registry order is authoritative in `VisualizerRenderer`. |

The active registry is also mirrored in `arrays.xml` for the settings
multi-select. A mode must be added to both lists in the same change. The
mode-level behavior and upstream deltas are documented in
[`visualizer-modes.md`](visualizer-modes.md),
[`visualizer-music-reactivity.md`](visualizer-music-reactivity.md), and
[`psysuals-port-notes.md`](psysuals-port-notes.md).

## Resources and build modules

| Path | Role |
|---|---|
| `res/xml/screensaver_preferences.xml` | Main settings tree and defaults. |
| `res/xml/sources_preferences.xml` | Provider toggles/setup entries. |
| `res/values/arrays.xml` | Display/value pairs for durations, modes, effects, intensity, hours, scaling, backgrounds, and cycles. |
| `res/values/strings.xml` | Setup, status, error, permission, and update copy. |
| `res/layout/dream_layout.xml` | Two image slots, visualizer container, status, clock, weather, and dev diagnostics overlay. |
| `res/layout/activity_*` | Settings/auth/provider setup screens. |
| `res/xml/dream_info.xml` | Registers SettingsActivity as the Dream Service settings entry point. |
| `res/xml/file_paths.xml` | FileProvider cache path for update installation. |
| `app/src/playstore/AndroidManifest.xml` | Removes `REQUEST_INSTALL_PACKAGES` from the Play Store flavor. |
| `app/build.gradle` | API 21 minimum, API 35 compile/target, Java/Kotlin 21, dev/prod × standard/playstore flavors. |

## Tests and verification modules

| Test module | Scope |
|---|---|
| `ScheduleWindowTest.kt` | Same-day, overnight, always-active, and end-boundary schedule behavior. |
| `ImageBehaviorTest.kt` | Image scale normalization and orientation-specific defaults. |
| `DreamInteractionPolicyTest.kt` | Which Dream modes receive input. |
| `VisualizerRenderTuningTest.kt` | Viewport scaling, field sizing, and trail tuning invariants. |
| `psysuals/tests/test_*.py` | Upstream audio, settings, config, lifecycle, rendering regression, determinism, palette, recipe, quality, benchmark, and post-processing behavior. |

Android compilation/device verification is intentionally delegated to GitHub
CI. Documentation checks, source/resource inventory checks, and upstream
Python tests are separate from an Android build.

## Vendored psysuals modules

`psysuals/` is a Git subtree, not an Android runtime dependency. Its core
modules provide the reference audio engine, renderer, display/UI lifecycle,
post-processing, quality selection, regression harness, and benchmarks. Its
`effects/` directory is the upstream effect registry and shader/helper source;
its `tests/` directory is the upstream verification suite. Android ports may
replace pygame/numpy surfaces with bounded GLES geometry or scalar fields, but
those differences must be recorded in `docs/psysuals-port-notes.md`.

### Screensaver postmortem diagnostics

Dev builds (`DEBUG_LOGGING`) retain fatal/legacy screensaver evidence locally in
`noBackupFilesDir/screensaver-postmortem.txt` and the latest recoverable failure
in `screensaver-postmortem.txt.recoverable` (each bounded to 64,000 characters).
The report is shown again in the diagnostic overlay on the next screensaver or
preview start, including blank, static image, slideshow and visualizer modes.
Each report remains until replaced by another failure of the same category or
app data is cleared. Startup snapshots saved evidence before starting a mode,
so a new startup error cannot hide or overwrite the prior fatal report. Nothing is
uploaded. HTTP(S) URLs are redacted from the saved report.

An uncaught JVM exception on any thread while an engine is active records its
active modes, time, thread and stack trace before delegating to Android's existing crash
handler. One process-wide recorder tracks overlapping dream/preview sessions
without retaining their views or contexts, and serialises atomic report writes.
The saved overlay is restored on every preview start, including after returning
from the background. Static-image Glide failures also retain their decode/fetch causes;
image/background application failures include file existence, byte count, scale
and background settings. Existing slideshow and visualizer reports are retained
through the same mechanism. Release builds keep their existing behaviour.
Native crashes, system process kills and severe out-of-memory failures are not
guaranteed to leave a report.

GitHub builder/device verification: in a dev build, select an invalid static
image and confirm the decode cause appears; restart into each of the four modes
and confirm the saved overlay reappears. Exercise an uncaught background-thread
exception while each mode is active and confirm normal process termination plus
report recovery on restart. Also verify normal stop restores the prior handler
after the last active session ends, and that returning to preview restores the
saved overlay.

Additional GitHub/device checks: automatic image backgrounds must work on a
hardware-bitmap-capable device and fall back to the manual colour if sampling
is unavailable. Start prefetch and slideshow caching together; interrupt a
download and verify already completed images remain available offline. Pause
and resume the visualizer with automatic genre detection enabled and verify
that classification resumes without blocking capture during rendering.

### Static-image, network and cache reliability

`StaticImageFiles` serialises imports and gives each validated image a unique
filename, so Glide cannot reuse an older selection's cache key. It syncs the new
file before committing the preference pointer and retains the previous selected
file for in-flight readers. Failed imports leave the selected image intact;
orphaned older imports are pruned after successful publication. Selection reads
also recover an old `background_image.previous` left by the former two-rename
replacement scheme. `StaticImageStore` now commits selection metadata on the IO
thread; callers must not separately republish the returned path.

The insecure self-hosted HTTP client disables OkHttp automatic redirects.
`SameOriginRedirectInterceptor` permits bounded redirects only within the
original scheme, host and port, rejecting cross-origin and credential-bearing
locations before another request. A self-hosted server redirecting to another
origin must be configured with its final endpoint instead.

`ImageCache.SaveResult` reports already-cached, downloaded and failed counts.
A writer validates the cache once per batch, commits each image separately and
reserves download space before opening its temporary file. Prefetch logs actual
outcomes, retries transient network/server failures up to three times, and
leaves authentication, permission, permanent HTTP, parsing and unsupported-image
failures for the next periodic run or user correction. JVM regression coverage exercises the cache validation count,
failed-download results, origin boundaries, image publication failure, legacy
recovery, update URL validation and Android-version signing flags.

### Release publication verification

`.github/scripts/publish-release.sh` stages a draft commit-specific release,
downloads its assets and checks exact bytes (plus APK checksum/size metadata),
then publishes it. Published commit-specific assets are never overwritten by
this script. Rolling channel assets are also downloaded and verified before
an existing channel tag moves. The same checks cover the Play Store AAB.
`.github/scripts/test_publish_release.py` exercises publication ordering and
corrupt downloaded assets using mocked `gh`/`git`, without touching GitHub.

New manifests retain rolling `apkUrl` for older installed clients and supply
`immutableApkUrl` for new clients. The updater accepts only HTTPS GitHub URLs in
the expected repository/channel with a full commit-specific tag, and bounds
manifest responses to 64 KiB. Old manifests remain supported. Older installed
clients may need to retry if they download during a rolling asset replacement;
new clients obtain their APK from the commit-specific URL instead.
APK signature queries use `GET_SIGNATURES` below API 28 and
`GET_SIGNING_CERTIFICATES` from API 28 onward. CI signing material is kept under
`RUNNER_TEMP`, outside the checkout, and removed after the job.

### Engine sessions and progressive startup

`ScreensaverSession` owns a child supervisor job for one engine run. Starting a
new mode cancels the old run; stopping cancels source enumeration, queued or
active image-cache work and weather requests without cancelling the host scope.
Short non-cancellable cache/file commits may finish to preserve file integrity.
Stop clears image/source state, and key handling checks the active mode rather
than interpreting leftover slideshow items. Visualizer diagnostic dispatches
are checked against the current session generation.

`ProgressiveSources` delivers useful source results as they arrive. The first
nonempty result starts the slideshow immediately. Later images are deduplicated
by stable identity and appended, leaving an active image, transition and timer
intact. If playback has exhausted its available images and stalled, later usable
source results resume loading. Empty or failed sources do not trigger a restart.
Offline cache fallback runs when every source finishes without usable images or
when an image round is exhausted; exhaustion-triggered fallback is attempted at
most once per session so a broken cache cannot recursively restart itself.

Google Drive enumeration accepts at most 2,000 unique images over 20 pages and
20,000 scanned entries, stops repeated cursors, bounds listing responses to
4 MiB and token responses to 64 KiB, and checks cancellation between pages and
entries. All CI checkouts explicitly select the event SHA and verify `HEAD`
before the Android build, including the separate Play Store job.

### Recovery and freshness integration

`SlideshowPlayback` records loading, waiting and stalled states shared by source
arrival, Glide callbacks and the engine's timers. A late source resumes stalled
playback at its first new image. Cache fallback is dispatched after Glide's
callback returns, and an outstanding fallback read is ignored if new source
images have already resumed playback. A periodic source refresh can also resume
a stalled slideshow.

`awaitResponse()` keeps a cancellable child watcher for the entire response-body
lifetime, including blocking reads after headers are delivered. Cancelling the
consumer cancels the OkHttp call; closing its response/body retires the watcher
without cancelling a normally completed call. All callers must close responses.
Tests cover both header-dispatch cancellation and cancellation of a body read
already in progress.

The dev error overlay owns remote controls while visible: Up/Down scroll,
Left/Right page, Center/Enter/Back close. Its dismissal key-up is consumed so
Back does not immediately exit preview. Volume keys remain available to Android.
The report files remain available on subsequent starts after dismissal.

Cache entries store an optional non-secret content revision and download time.
Google Drive supplies its [modifiedTime](https://developers.google.com/workspace/drive/api/reference/rest/v3/files#File)
as a revision marker; providers without markers are re-downloaded after 24 hours.
Cache replacements use distinct filenames, preserve the old entry during
fetch/validation, and commit new manifest references before deleting superseded
files. Failure leaves the previous cached image usable. Glide also uses a known
revision as a request signature to invalidate a changed remote image.

`PrefetchRetryPolicy` treats HTTP 408/425/429 and 5xx responses as transient.
Provider HTTP errors retain their status classification; token-refresh network
errors are not misreported as revoked credentials. TLS validation failures,
authentication/permission errors, non-transient HTTP errors, oversized or
undecodable images do not cause an immediate retry loop. A new periodic run can
try them again after credentials or provider contents change.

### Schedule, settings fallback and update staging

`ScheduleWindow` accepts the schedule-enabled flag for both admission and the
end-of-window delay. A disabled schedule always admits startup and returns no
shutdown timer, including daytime, overnight and equal-hour windows.
`SettingsActivity.StartupErrorFragment` is a public nested class with a public
empty constructor, meeting AndroidX Fragment transaction/restoration rules.

Settings ignore update clicks while their existing update job is active.
`UpdateDownloadFiles` serialises preparation across callers, gives each attempt
its own temporary file and publishes a verified APK under `cacheDir/updates`
with a version/checksum-specific filename. Cancelling one attempt removes only
its temporary file. A later request for the same artifact reuses it after
checking bytes, checksum, package version and signing identity again; it does
not overwrite an APK that the package installer may still be reading.

`VertexBatchStorage` lazily allocates a single heap vertex array and a single
direct upload buffer. GLDraw flushes before switching triangle/line primitives,
so the two primitive types share this storage. The unchanged 262,144-vertex,
six-float budget requires 12 MiB of staging instead of 24 MiB. This excludes GPU
VBOs, framebuffers, textures and effect state; actual device memory/performance
still needs profiling. The pure storage tests check lazy allocation, reuse and
the full capacity budget without invoking Android GL APIs.

### Update staging retention

`UpdateRetentionPolicy` limits new staged updates to three APKs and 200 MiB.
Before downloading, it removes stages whose version is already installed and
then evicts the oldest other APKs only when they have not been used for 24 hours.
Recent stages and the current destination are protected because an external
package installer may still need them. Reusing a verified APK refreshes its
protection timestamp. If protected files leave insufficient budget, no new
network download starts; settings explains how to finish an installation or use
Android's Clear cache action. Previously oversized protected caches may remain
above the limit until their files become obsolete or age out, but cannot grow
through new stages. Abandoned temporary files older than 24 hours are cleaned,
with the current attempt explicitly excluded. A post-download quota check also
covers callers without a declared size.

### Clock corrections and dev performance samples

The engine registers a non-exported time/timezone-change receiver only while a
mode is active and unregisters it on stop. A change cancels the previous schedule
timer and evaluates the window against the new local clock. Moving outside the
window finishes the screensaver; otherwise its deadline is recalculated. Timer
callbacks also recheck wall time. Clock formatting is refreshed on time changes
and when restarting the clock overlay.

Dev visualizers now attach `onPerformanceUpdate` to `devPerformanceOverlay`.
Samples are accepted at most once per second and posted to Main only for the
same active visualizer session. The display includes mode, viewport, frame time,
FPS, vertices, bloom and allocated vertex staging. It does not measure total
process or GPU memory. Stop removes the callback and hides the view; release
builds never attach the callback. The persistent error overlay stays above the
performance display.

### OneDrive authenticated pagination boundary

`GraphPages` validates the starting URL and each next link before the caller can
attach a bearer header. Only HTTPS `graph.microsoft.com` on port 443 is accepted,
without userinfo or fragments; repeated canonical URLs are rejected. The full
opaque query is passed through, rather than extracting/rebuilding skip tokens.
Listing requests disable automatic redirects, so a validated request cannot
silently turn into a request to an unvalidated origin. Page, scanned-entry and
image limits remain enforced, and pre-authenticated image download URLs receive
no Graph Authorization header.

### Deep review follow-up: source refresh and integration coverage

`ImageModels` routes URI schemes before HTTP models. `SourceRefreshCoordinator`
owns enumeration deadlines and progressive refresh; `SourceSnapshots` keeps the
last successful result per provider. Temporary failures retain those images,
explicit empty results clear them, and removed providers are pruned. Partial
Dropbox results replace refreshed URLs while retaining missing previous items;
the prefetch worker retries transient failures within its existing attempt limit.
HTTP 429 stops queued temporary-link requests rather than continuing the burst.

OAuth publication and disconnect/configuration changes share `AccountTransactions`.
In-memory generation tickets and configuration comparisons reject stale responses;
tickets and credential snapshots are never logged or persisted.

Every build writes a metadata-only `screensaver-postmortem.txt.active` marker while
an engine session is active. A leftover marker is recorded separately as
`.interrupted`, with an explicitly unknown cause. This is evidence of an unclean
shutdown, not proof of a crash, ANR, native fault or low-memory kill. Dev builds
continue to show captured fatal and recoverable exception reports separately.
Overlapping preview/dream sessions share marker ownership.

Source health remains visible during playback with reconnect/permission/folder
and connection guidance. Cache playback is identified independently of diagnostics.

GitHub's API 29/35 emulator matrix runs `connectedDevStandardDebugAndroidTest`:
real cache manifest to Glide decode, static import validation, all four Preview
modes across stop/restart and recreation (including GL sample production), and
APK sharing through the actual FileProvider. Publication depends on both jobs.
No Android build is performed locally. These tests do not prove physical TV
system-dream activation, system audio capture or completion of the external
package installer; those remain explicit device checks.

Image requests use an application-scoped Glide RequestManager because the engine
owns their complete lifecycle and clears all targets at stop. An Activity-scoped
manager also pauses/resumes through Glide's hidden Fragment; that competing
lifecycle could discard target ownership and leave static Preview blank after
stop/resume. The emulator lifecycle regression verifies visible images before
and after stop/resume and recreation, rather than merely checking for crashes.

Verification: [GitHub run 40344834235](https://github.com/Whichcraft/androsaver/actions/runs/40344834235)
passed the JVM suite, all four Android integration tests on each of API 29/35,
and signed dev APK build/publication with downloaded-asset verification for
`e553363bfbb5e85ebafcdb64f69b8e8bb89d7477`. The rolling `dev-channel` tag was
confirmed to target that commit. The user confirmed on 2026-10-26 that automatic
screensaver activation on the physical TV works flawlessly. System audio capture
and external installer completion remain manual checks.

### Provider transport, cache leases and audio status

Self-hosted API requests use bounded same-origin redirects even with validated
TLS. Immich, Nextcloud and Synology image items also carry credential-origin
metadata through Glide, direct imports and the offline writer. Internal metadata
headers are stripped before network transmission. Cloud transports retain their
normal CDN redirects. Synology uses exactly the configured protocol/port and TLS
policy: self-signed/expired certificates require explicit insecure consent, and
HTTP requires both selecting HTTP and insecure consent. There is no automatic
certificate bypass, protocol downgrade or port fallback.

Immich uses `POST /api/search/metadata` for both library and album enumeration;
connection probes verify this authenticated API rather than the public ping.
The supported contract is metadata search in the Immich v2/v3 API family, with
legacy numeric `nextPage` and v3.2 `nextCursor` pagination. Removed `GET /assets`
and album-embedded asset arrays are not used. Requests are capped at 40 pages,
20,000 scanned rows and 2,000 unique images; repeated cursors/pages stop progress.
Synthetic v2/v3 response fixtures exercise the consumed API fields. Actual server
compatibility remains a deployment check; no live personal server was accessed.

Offline fallback acquires a process-wide lease under the cache manifest lock.
Leased files, including retired generations, cannot be evicted by another cache
instance. They count toward byte/entry limits; optional downloads are skipped
when a full leased playlist leaves no reservation space. Leases are released on
stop or after a fresh replacement image has loaded, including cancellation.

Credential migration only fills missing encrypted keys before its encrypted
migration marker is recorded. Later encrypted values and removals remain
authoritative after plaintext cleanup failure; stale plaintext cannot resurrect
a disconnected account. Sensitive writes/removals and clear operations preserve
that marker, which is hidden from normal preference enumeration. A sensitive
edit retries an incomplete initial migration before recording that marker, so
a new account write cannot discard another provider’s unmigrated credentials. Failed or null MediaStore
queries produce source failures rather than an authoritative empty snapshot.
Cleanup attempts every release and preserves failed phases/causes. A failed
cleanup keeps a session marker even when an overlapping session stops cleanly.

Audio response is enabled through an explicit Settings preference. It explains
Android's saved “While using the app” grant and links to app settings when
needed. Switching visualizer modes never opens a permission dialog. The live
visualizer reports permission required, backend unavailable, no signal or active
signal separately, in all builds; diagnostic reports include capture status.
Permission alone is not proof of capture capability on a particular TV. Effects
continue without audio. No microphone recording or new audio transport is added.

The emulator suite now reports static, offline slideshow, blank and visualizer
lifecycle/recreation independently so one failure cannot hide the other modes.

Verification: [GitHub run 40356857225](https://github.com/Whichcraft/androsaver/actions/runs/40356857225)
passed the full JVM suite, device integration on API 29 and 35 (independent
four-mode lifecycle cases plus cache/import/installer checks), and signed dev
APK build/publication with downloaded-asset verification for
`861f9eef687f8762e07327a728ba8cafa8c61596`. The rolling `dev-channel` tag was
confirmed to target that commit. Real-server certificate/API compatibility and
physical-TV system audio capture remain deployment checks.

### Account-scoped offline images and management (2026-10-26)

Remote stable IDs now include an account namespace and nonsecret source configuration fingerprint. Random namespace IDs are encrypted alongside credentials and rotated atomically on account replacement; normal OAuth refresh does not replace the namespace. Legacy unscoped cache entries are excluded from fallback until images are fetched again. Disabling/disconnecting a source or changing its account/origin/folder excludes its previous cache immediately at fallback/refresh boundaries.

After progressive enumeration finishes, live playback and background prefetch each use one stable fair plan of at most 200 remote images. Unavailable providers retain quota rather than letting healthy providers repeatedly displace their images. Warming is bounded to 100 MiB transferred and 90 seconds per round; file leases and storage budgets still apply. A successful empty listing clears the displayed slideshow and cancels pending transitions instead of treating old cache as an outage fallback.

Settings offers saved diagnostic viewing/copying/clearing without resetting preferences; clearing never removes active-session markers. Saved images shows provider counts/storage and permits per-provider clearing when files are not leased. Enabled sources may refill their cache. Source health includes the last successful refresh and next scheduled check.

Google/OneDrive authorization uses an in-memory ViewModel session across Activity recreation. Process death starts a new session; device codes and tokens are not persisted in saved Activity state. Host fields reject URLs, credentials, embedded ports, query/fragment/path syntax and normalize DNS/IP literals before use. No visualizer effects or vendored upstream code changed in this follow-up.

Verified app commit `bae3f6db757ef2ac09f0c448a35f75a7ce432039` in [GitHub run 40396730355](https://github.com/Whichcraft/androsaver/actions/runs/40396730355): JVM tests across all variants, all 11 integration tests on each API 29/35 emulator, signed dev APK publication and downloaded-asset integrity passed. The `dev-channel` tag was confirmed against that app commit. Resource XML parsing, diff whitespace checks and four release-script tests also passed locally; no Android build ran locally. Physical TV/cloud/server integration remains a manual check.

### 2.12 cache integrity and recovery

ImageCache is the sole persistent remote-photo cache. Glide disk caching is disabled globally, its old disk cache is purged once on startup, and memory request signatures include source namespace, stable identity and revision. Source cleanup clears released Glide memory too. Complete listings persist an allowed-ID set per namespace; empty listings persist an empty set. Partial/failed listings never overwrite that authority. Readers and late download commits check it; leases continue to protect active readers.

Downloads stage up to 50 MiB before any committed image is evicted. Published images and leased generations remain bounded to 150 MiB / 200 files; temporary staging may use an additional 50 MiB. Actual-size capacity checks replace the previous fixed 50 MiB reservation. Failed requests leave the working manifest/files intact. OneDrive ETags, Dropbox revisions, Nextcloud ETags/modified dates and Synology modification time/size invalidate stale copies.

Provider failures carry exception class chains and HTTP status only, with dedicated TLS categories. Saved source-failure metadata is separate from fatal/recoverable reports and never activates the fatal overlay. Device-auth bodies are limited to 64 KiB; codes, expiry, verification URL and credentials are checked before use.

On API 30+, startup reads available Android historical exit records for Java/native crashes, ANRs, memory pressure and excessive resources. Reports include reason, status/signal, memory metrics and bounded ANR stack frames. Raw descriptions, arbitrary trace messages and native binary protobufs are not copied. Android may have no retained trace; API 21–29 keep the session-journal fallback. Fatal Java capture is registered in every build.

Settings Source status retains healthy and failed refresh timestamps and typed failure details, offers Retry now, and notifies active slideshow engines. Slideshow retries after connectivity returns and unregisters network callbacks on stop. Report read errors are distinct from empty reports; cache clearing displays progress. Verification pending GitHub builder; no effects or vendored psysuals code changed.
