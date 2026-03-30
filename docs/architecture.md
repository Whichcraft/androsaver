# AndroSaver Architecture Reference

## Package: `com.androsaver`

| File | Role |
|------|------|
| `ScreensaverService.kt` | DreamService entry point; system integration |
| `ScreensaverEngine.kt` | Orchestrates slideshow + visualizer; manages transitions, overlays, remote control |
| `SettingsActivity.kt` | Settings UI host; contains `SettingsFragment` and `SourcesFragment` |
| `PreviewActivity.kt` | In-app preview without activating system screensaver |
| `Prefs.kt` | **All SharedPreferences key constants** — always use these |
| `ImageCache.kt` | Disk cache (≤200 images / 300 MB); offline fallback; EXIF-aware |
| `ExifRotationTransformation.kt` | Glide transform for EXIF orientation correction |
| `UpdateChecker.kt` | Polls GitHub Releases; supports Stable/Dev channels |
| `UpdateInstaller.kt` | Downloads APK and installs via FileProvider |
| `HttpClients.kt` | Shared OkHttp client instances |
| `WeatherFetcher.kt` | OpenWeatherMap current conditions fetcher |
| `BootReceiver.kt` | Receives BOOT_COMPLETED; pre-refreshes Google Drive OAuth token on device boot (only Google Drive — OneDrive/Dropbox not refreshed) |

## Package: `com.androsaver.auth`

OAuth managers (all use coroutines):

| File | Auth pattern |
|------|-------------|
| `GoogleAuthManager.kt` | Device-auth flow; no Google Play Services needed |
| `OneDriveAuthManager.kt` | Microsoft Azure device-auth flow |
| `DropboxAuthManager.kt` | Authorization code flow + token auto-refresh |

Setup activities: `GoogleDriveSetupActivity`, `GoogleAuthActivity`, `OneDriveSetupActivity`, `OneDriveAuthActivity`, `DropboxSetupActivity`, `DropboxAuthActivity`, `ImmichSetupActivity`, `NextcloudSetupActivity`, `SynologySetupActivity`

## Package: `com.androsaver.source`

| File | Source | Auth |
|------|--------|------|
| `ImageSource.kt` | Interface: `suspend fun getImageUrls(): List<ImageItem>` | — |
| `ImageItem.kt` | Data class: url, headers, exifOrientation | — |
| `GoogleDriveSource.kt` | Google Drive REST API v3 | OAuth token (auto-refresh) |
| `OneDriveSource.kt` | Microsoft Graph API | OAuth token (auto-refresh) |
| `DropboxSource.kt` | Dropbox API v2 | OAuth token (auto-refresh) |
| `ImmichSource.kt` | Immich REST API | API key header |
| `NextcloudSource.kt` | WebDAV PROPFIND | Basic auth (app password) |
| `SynologySource.kt` | Synology DSM FileStation REST | Session cookie (re-login every 25 min) |
| `LocalStorageSource.kt` | Android MediaStore | `READ_MEDIA_IMAGES` permission |

See `docs/image-sources.md` for detailed auth patterns.

## Package: `com.androsaver.visualizer`

| File | Role |
|------|------|
| `VisualizerView.kt` | GLSurfaceView wrapper; manages `AudioEngine` + `VisualizerRenderer` lifecycle |
| `AudioEngine.kt` | Android `Visualizer` API → FFT (512 bins) → bass/mid/high bands + beat detection; applies genre weighting |
| `AudioData.kt` | Snapshot: bass, mid, high (0–1), beat (0–2), waveform[], fft[] |
| `GLDraw.kt` | GL ES 2.0 utilities: shader compilation, matrix math, line/quad/circle/glyph drawing |
| `VisualizerRenderer.kt` | `GLSurfaceView.Renderer`; owns mode list; calls `mode.draw(gl, audio, tick)` each frame |
| `BaseMode.kt` | Abstract base: `abstract fun draw(gl: GLDraw, audio: AudioData, tick: Long)` |

### 10 Visualizer Modes (`com.androsaver.visualizer.modes`)

| Class | Display name | Visual concept |
|-------|-------------|----------------|
| `YantraMode.kt` | Yantra | 6 concentric rotating polygons + web/spoke connections |
| `CubeMode.kt` | Cube | Nested wireframe cubes with satellite points |
| `PlasmaMode.kt` | Plasma | Full-screen sine interference pattern |
| `TunnelMode.kt` | Tunnel | First-person tunnel with triangle bursts |
| `LissajousMode.kt` | Lissajous | 3D trefoil knot with neon glow |
| `NovaMode.kt` | Nova | 7-fold mirror kaleidoscope waveform |
| `SpiralMode.kt` | Spiral | 6-arm neon helix vortex |
| `BubblesMode.kt` | Bubbles | Rising translucent bubbles |
| `BarsMode.kt` | Spectrum | Log-spaced spectrum bars + waveform overlay |
| `WaterfallMode.kt` | Waterfall | Scrolling time-frequency spectrogram |

See `docs/visualizer-modes.md` for audio reactivity details.

## Key Resources

| File | Contents |
|------|----------|
| `res/xml/screensaver_preferences.xml` | All settings preference screen definitions |
| `res/values/arrays.xml` | Dropdown option arrays (durations, effects, intensities…) |
| `res/values/strings.xml` | All UI strings (~150+) |
| `res/layout/dream_layout.xml` | Main screensaver layout (2 ImageViews + VisualizerView) |
| `res/xml/dream_info.xml` | DreamService metadata (icon, label, settings activity) |
| `res/xml/file_paths.xml` | FileProvider paths for APK install |

## Data Flow

```
DreamService.onDreamingStarted()
  └─ ScreensaverEngine.start()
       ├─ [Slideshow] load images from N sources → ImageCache → Glide → ImageView transitions
       │    └─ optional VisualizerView overlay (semi-transparent, 10–70% opacity)
       └─ [Visualizer] VisualizerView.start()
            ├─ AudioEngine: Visualizer API → FFT → AudioData (60 fps)
            └─ VisualizerRenderer.onDrawFrame() → BaseMode.draw()

Remote control (D-pad events in ScreensaverEngine):
  Visualizer: ←/→ = prev/next mode | ↑/↓ = intensity | other = finish()
  Slideshow:  → = next image | ← = previous image | other = finish()
```

## Permissions

| Permission | Purpose |
|-----------|---------|
| `RECORD_AUDIO` | Music visualizer (Visualizer API captures global mix) |
| `READ_MEDIA_IMAGES` | Device storage image source (API 33+) |
| `READ_EXTERNAL_STORAGE` | Device storage image source (API < 33) |
| `INTERNET` | All cloud sources + weather + update checker |

## Build Variants

| Variant | DEBUG_LOGGING | Use |
|---------|-------------|-----|
| `devRelease` | true | Dev APK (Downloader code 9149021) |
| `prodRelease` | false | Stable APK (Downloader code 7582483) |
