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
