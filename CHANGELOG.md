# Changelog

All notable changes to AndroSaver are documented here.

## 2026-03-26 (session 15 cont10)

### Changed
- **TunnelMode rework** — bass-punch triangles on the center line; constant speed always; beat circles removed
- **LissajousMode** — less nervous (frequency/phase band influence cut 4×); trail fades faster (0.08→0.18)
- **SpiralMode** — tighter spiral (SPIN 1.6→3.0); extremes reduced (beat speed, scale, radius mod, flash all pulled back)

---

## 2026-03-26 (session 15 cont9)

### Fixed
- **Update checker version mismatch** — CI was hardcoding `1.0.x` in `version.json`; now mirrors `build.gradle` formula (`1.1.$(versionCode - 101)`)

---

## 2026-03-26 (session 15 cont8)

### Changed
- **YantraMode intensity +30%** — all beat multipliers ×1.3
- **CubeMode intensity +30%** — all beat multipliers ×1.3
- **CubeMode satellite cubes no longer deform** — removed vertex clamping from projectOffset; orbit radius clamping already keeps them on-screen
- **TunnelMode constant speed** — dt is now a fixed constant (no beat/bass fluctuation); always a smooth ride
- **TunnelMode triangles removed** — triangle data class, spawn logic, bass spring, and draw loop fully removed
- **LissajousMode size +30%** — projection scale raised from 0.40 to 0.52 of screen dimension

---

## 2026-03-26 (session 15 cont7)

### Changed
- **CubeMode satellite smoothness** — fixed angular slot divisor to N_MAX=6 so existing satellites never jump when beat changes active count
- **TunnelMode inner circles** — removed constant per-ring rotating polygons; replaced with beat-spawned circles that travel through the tunnel independently
- **LissajousMode intensity** — all beat multipliers divided by 3 (scale, hue, rotation, knot speed, head dot size)

---

## 2026-03-26 (session 15 cont6)

### Changed
- **Patch version resets on minor bump** — introduced `versionPatchBase` so 1.1.x patch count starts from 0 at the 1.1 bump; future minor bumps just update the base constant
- **Auto-cycle timer resets on manual effect change** — left/right navigation now restarts the full cycle interval

---

## 2026-03-26 (session 15 cont5)

### Removed
- **Check for Updates button** — removed separate preference row; update check still runs automatically on resume and result shown in the AndroSaver about row

---

## 2026-03-26 (session 15 cont4)

### Changed
- **Version bumped to 1.1** — versionName is now `1.1.${gitVersionCode}`
- **Visualizer intensity default lowered to Low (0.5)** — effects start at lowest level instead of Medium
- **Auto-cycle interval default changed to 2 minutes** — was Off; added 10-minute and 15-minute options

---

## 2026-03-26 (session 15 cont3)

### Added
- **CubeMode echo trail** — last 8 frames of main and inner cube positions are replayed each frame with decreasing alpha, giving a motion-trail/echo effect; implemented via ring buffer (renderer clears the screen every frame, so framebuffer persistence cannot be relied on)
- **CubeMode satellite bounds clamping** — orbit radius is clamped per-frame so satellite cubes never leave the screen, accounting for screen size and current cube scale

### Changed
- **Removed separate Update Channel row** — channel is now shown inline in the version string (e.g. "Version 1.0.42 · dev channel"); the read-only channel preference row is gone

### Fixed
- **Version always 1.0.1 / update check never finding updates** — CI checkout was a shallow clone (`fetch-depth` defaulting to 1), so `git rev-list --count HEAD` always returned 1; added `fetch-depth: 0` to the build job checkout

### Changed
- **Version shows update channel** — About entry now displays e.g. "Version 1.0.42 · dev channel" instead of just the version number

---

## 2026-03-26 (session 14)

### Added
- **Satellite cubes in CubeMode** — port of psysuals v1.3 orbiting satellite cubes; 2–6 small cubes (28% of main cube scale) orbit at radius 2.6 using the same rotation matrix as the main cube; count scales with beat intensity (`2 + int(beat * 2)`), orbital speed increases on stronger beats
- **Check for Updates button** — new "Check for Updates" entry in the About settings section; shows "Checking…" while in progress, then "Up to date" or "Update available: vX — press to install"

### Changed
- **Update channel locked to build flavor** — dev builds always check the dev channel, stable builds always check the stable channel; channel selector is now read-only with an "automatic" label; manual channel switching removed

---

## 2026-03-26 (session 13)

### Fixed
- **Resource leaks** — HTTP responses in `GoogleAuthManager` (two call sites) and `UpdateChecker` were not closed after use; wrapped with `.use {}`; `UpdateInstaller` response body was consumed without closing the response itself
- **Dead code** — removed unused `logout()` method in `SynologySource` (intentionally not called; SID expires naturally)

### Changed
- **Shared HTTP clients** — replaced 9 separate `OkHttpClient` instances across sources and auth managers with two singletons in `HttpClients`: `standard` (cloud APIs) and `trustAll` (self-hosted LAN servers with self-signed certs); reduces thread/connection pool overhead
- **Per-source fetch timeout** — `ScreensaverEngine` now wraps each source's `getImageUrls()` call with `withTimeoutOrNull(60s)` in both the initial load and the periodic refresh loop; a hung source can no longer block the entire slideshow

---

## 2026-03-26 (session 12)

### Changed
- **psysuals submodule** — updated from v1.2.0 → v1.3.0; picks up backported AndroSaver tuning improvements, orbiting satellite cubes in CubeMode (count 2–6 scales with beat intensity), Lissajous `raw` variable bug fix, and 5 additional fixes from code audit

---

## 2026-03-26 (session 11)

### Fixed
- **CI build** — missing `import com.androsaver.auth.DropboxAuthManager` in `SettingsActivity` caused `Unresolved reference: auth` compile error; spurious `auth.` prefix removed from call site

---

## 2026-03-26 (session 10)

### Added
- **In-app updater** — app silently checks GitHub Releases for a newer `version.json` on each Settings open; if a newer build is available the About row shows "Update available: vX — press to install"; pressing it downloads the APK and hands it to the system installer (`REQUEST_INSTALL_PACKAGES`)
- **Update channel selector** — `ListPreference` in the About section lets users switch between Stable and Dev update channels; defaults to the build's own flavor; re-checks for updates immediately on change
- **About preference** — new About category at the bottom of Settings always shows the current installed version
- **Image Sources sub-screen** — all source toggles and setup entries moved to a dedicated sub-page (press Back to return); the main Settings screen shows a single "Image Sources" entry with a live summary of active source names (e.g. "Google Drive, Dropbox")
- **EXIF orientation** — images are now displayed in their correct orientation; `LocalStorageSource` reads `MediaStore.Images.Media.ORIENTATION` at list time (free — already in the cursor); `ImageCache` reads `ExifInterface` from each cached file; `ExifRotationTransformation` (full 8-case EXIF including flips and transposes) is applied via Glide for local and cached images; remote HTTP images rely on Glide's built-in `Downsampler` EXIF handling
- **Synology session auto-refresh** — image URLs (which embed the DSM session ID) are automatically re-fetched every 25 minutes, re-logging in to Synology before the ~30-minute DSM session expiry; the slideshow continues uninterrupted and all other sources also refresh at the same time

### Changed
- `versionCode` is now derived from `git rev-list --count HEAD` at build time (auto-incrementing); `versionName` is `1.0.<versionCode>`
- CI now generates and publishes `version.json` alongside every APK release (dev and stable)
- **Ken Burns** — reduced oversize from 5–14 % to 4–8 %; all four presets now end at translation (0, 0) so the image is always centered at rest; two presets zoom in (pan from offset to center), two zoom out (pan from offset to center at reduced scale)
- **Proprietary licence** — replaced placeholder MIT licence with All Rights Reserved; unauthorised copying, modification, distribution, or sale is prohibited

### Fixed
- CHANGELOG session 9 entry had literal `\n` sequences instead of real newlines (same `sed` corruption bug as AndroidManifest)

---

## 2026-03-25 (session 9)

### Added
- **Dropbox source** — fetch photos from Dropbox via API v2; OAuth 2.0 authorization code flow (user visits auth URL on another device, pastes code back); access token refreshed automatically using App Key + App Secret; images fetched as temporary links (4-hour pre-signed URLs) retrieved in parallel; configurable folder path

---

## 2026-03-25 (session 8)

### Added
- **Immich source** — fetch photos from a self-hosted [Immich](https://immich.app) server via its REST API; authenticates with an API key (generate in Account Settings → API Keys); optional Album ID to restrict to one album, or leave blank to show all photos; supports self-signed certificates; default port 2283

---

## 2026-03-25 (session 7)

### Added
- **OneDrive source** — fetch photos from Microsoft OneDrive (personal or work) via Microsoft Graph API; uses OAuth 2.0 device code flow (same TV-friendly pattern as Google Drive); downloads use pre-authenticated URLs so no auth header is needed for image loading

---

## 2026-03-25 (session 6)

### Added
- **Nextcloud source** — fetch photos from any Nextcloud instance via WebDAV (PROPFIND + Basic Auth); supports app passwords and self-signed certificates; setup UI mirrors the Synology setup

---

## 2026-03-25 (session 5)

### Changed
- **Dev vs Stable builds**: added `dev` and `prod` product flavors. `BuildConfig.DEBUG_LOGGING` is `true` in dev, `false` in prod — all `Log.*` calls are gated behind this flag so no debug output ships in Stable or Play Store builds
- CI updated: dev branch builds `assembleDevRelease`; master builds `assembleProdRelease` / `bundleProdRelease`

---

## 2026-03-25 (session 4)

### Fixed
- CI: release steps now force-push the `dev`, `stable`, and `playstore-stable` tags to the current commit before publishing, so GitHub's auto-generated source archives (`/archive/refs/tags/*.zip`) always match the released build
- CI: `bundle-playstore` job now merges `master` into the `playstore` branch before building the AAB, keeping the branch and its source archive in sync with master

---

## 2026-03-25 (session 3)

### Changed
- **Effect cycling** now always starts each effect from its default state — `reset()` is dispatched to the GL thread via a `@Volatile` flag so no mode ever draws an uninitialised first frame
- `YantraMode`: beat reactivity increased by ~1.5× (ring pulse, brightness, spoke reach, outer radius)
- `CubeMode`: beat reactivity doubled; base spin ~65% faster; scale pulse on beat substantially increased; trail persistence increased (fadeBlack 0.13→0.07)
- `TunnelMode`: default speed and reactivity reduced (−2 intensity steps) so tunnel is calm at Normal intensity and scales up with the slider; path swing reduced and tube radius increased so viewer stays inside the tube most of the time; triangles burst larger on bass hits (spring physics); triangle spawn count is now continuously driven by both bass and beat (more sound → more triangles); triangle cap increased (80→120)
- `BubblesMode`: initial bubble pool halved (400→200); spawn rate and beat-spring pulsation reduced for a calmer default start

### Fixed
- **Slideshow — black screen with Synology source**: `SynologySource.getImageUrls()` was calling `logout()` immediately after listing files, invalidating the session ID that was embedded in every image URL before Glide could load any images. The logout is now omitted; the DSM session expires naturally after ~30 minutes
- **Slideshow — black edge on right side**: Ken Burns `startScale` raised from 1.0 to 1.05 so the image always overflows the view bounds on all sides, eliminating the gap that appeared when a translation shifted the image at 1:1 scale
- `SettingsActivity`: replaced deprecated `requestPermissions` / `onRequestPermissionsResult` API with `ActivityResultLauncher` for both storage and audio permissions

---

## 2026-03-25 (session 2)

### Added
- Slideshow remote control: ← / → now navigate to previous / next image; skipping resets the auto-advance timer
- Display Overlays settings section — clock and weather are now configurable in both Slideshow and Visualizer mode (previously clock was slideshow-only in the UI)
- Music Genre hint documented: explains how each genre adjusts bass-frequency weighting for beat detection (Electronic boosts sub-bass, Rock boosts mid-bass, Classical reduces bass / boosts harmonics)

### Changed
- `AudioEngine`: doubled capture rate (removed `/2` on `getMaxCaptureRate`), reduced FFT smoothing (0.75→0.50), halved energy-history window (30→15 frames) — faster overall music reaction
- `BarsMode` (Spectrum): added `display[]` smoothed height buffer; lerp speed scales from 0.25 at silence to 1.0 at full beat — calmer bars at low intensity, snappy at high
- `BubblesMode`: capped `beatSel` for spawn count and bubble size so high intensity doesn't flood the screen; wider hue spread at high intensity; extra neon rings appear only when beat > 1.0 (i.e. intensity > Normal)
- `CubeMode`: reduced base rotation and band multipliers further; scale pulse now driven by both beat and direct bass for a sharper size pop
- `LissajousMode`: substantially reduced trace speed, phase-selector drift, frequency-selector reactivity, scale burst, rotation inertia, and hue jump — much calmer at default intensity
- `NovaMode`: replaced oversized centre circle with two counter-rotating layers of 3 triangles; redistributed beat energy to rings, spokes, and ring brightness
- `PlasmaMode`: reduced idle time increment ~60% and hue cycle rate for slower default movement
- `TunnelMode`: tunnel radius is now dynamic (small at silence, expands with beat/intensity); triangle spawn threshold raised and count/size reduced — scales cleanly with intensity
- `WaterfallMode`: removed `e < 0.02` skip; minimum lightness floor (`coerceIn 0.04`) ensures no black tiles
- `YantraMode`: beat impulse on ring pulsation increased; ring displacement amplitude increased; ring brightness now includes a beat term; spokes reach further on beat; centre dot substantially reduced
- Auto-cycle interval default changed to Off (was 90 s)
- Show Clock default changed to Off
- Auto-cycle options simplified to Off / 1 min / 2 min / 5 min (removed 30 s option)

### Fixed
- CI: upgraded `gradle/actions/setup-gradle` from v3 to v4 for Node.js 24 compatibility

---

## 2026-03-25

### Added
- Preview mode — test the screensaver without activating the system screensaver
- Clock overlay — date and time shown in the corner during slideshow
- Ken Burns effect — slow pan/zoom animation on photos
- Visualizer overlay — music visualizer rendered on top of the photo slideshow
- Image cache — offline fallback; up to 200 images / 300 MB stored locally
- Weather widget — current temperature and conditions from OpenWeatherMap
- Schedule — restrict screensaver to a configurable time window
- Music visualizer mode — 10 OpenGL ES 2.0 audio-reactive effects (Yantra, Cube, Plasma, Tunnel, Lissajous, Nova, Spiral, Bubbles, Spectrum, Waterfall)
- TV remote control while visualizer is running (← / → cycle effects; ↑ / ↓ adjust intensity)
- Auto-cycle visualizer mode — rotate through effects on a configurable interval
- Local storage source — use photos from device storage alongside Drive/NAS

### Changed
- Visualizer performance improvements ported from psysuals v1.1/v1.2 algorithm (FFT smoothing, beat energy normalisation)

### Fixed
- `WeatherFetcher`: check HTTP success status before reading response body; close responses; add 10 s / 15 s timeouts
- `AudioEngine`: release `Visualizer` resource if setup throws after construction (resource leak)
- `ScreensaverEngine`: prevent infinite retry loop on image load failure — track consecutive failures, give up after exhausting the list, add 300 ms delay between retries
- `GoogleDriveSource`: null-safe access on `id`/`name` JSON fields; close responses; add timeouts; follow `nextPageToken` so folders with >1000 images are fully fetched
- `SynologySource`: close HTTP responses; add timeouts; log out DSM session after image listing to prevent session accumulation on the NAS
- `SynologySetupActivity`: validate port is a number between 1–65535 before saving or testing
- `LocalStorageSource`: log silenced `MediaStore` exceptions instead of swallowing them
- `SettingsActivity`: request `RECORD_AUDIO` permission when switching to Visualizer mode; show explanatory message if denied

---

## 2026-03-24

### Added
- GitHub Actions CI/CD — dev APK builds on push to `dev`; Play Store AAB published on merge to `master`
- Play Store listing, privacy policy, and generated screenshots

---

## 2026-03-23

### Added
- Initial release: Android TV screensaver with Google Drive and Synology NAS photo sources
- Six transition effects: Crossfade, Fade to Black, Slide Left, Slide Right, Zoom In, Zoom Out (+ Random)
- Configurable slide duration (5 s – 30 min) and transition speed (1 – 5 s)
- Adaptive launcher icon and TV banner
- Google OAuth 2.0 device flow (no Google Play Services required)
- Synology DSM FileStation API integration with self-signed certificate support
