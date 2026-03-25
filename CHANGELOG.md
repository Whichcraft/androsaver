# Changelog

All notable changes to AndroSaver are documented here.

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
