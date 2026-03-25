# Changelog

All notable changes to AndroSaver are documented here.

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
