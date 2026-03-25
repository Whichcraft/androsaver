# Changelog

All notable changes to AndroSaver are documented here.

## [Unreleased] — dev

### Fixed
- `WeatherFetcher`: check HTTP success status before reading response body; close response with `.use`; add 10 s connect / 15 s read timeouts
- `AudioEngine`: release `Visualizer` resource if setup throws after construction (prevented resource leak)
- `ScreensaverEngine`: break infinite retry loop on image load failure — track consecutive failures, give up after exhausting the list, add 300 ms delay between retries
- `GoogleDriveSource`: null-safe access on `id`/`name` JSON fields (prevented crash on malformed API response); close responses; add timeouts
- `SynologySource`: close HTTP responses with `.use`; add 10 s / 30 s timeouts to trust-all client
- `LocalStorageSource`: log silenced `MediaStore` exceptions instead of swallowing them

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
