# AndroSaver Architecture Reference

## Package: `com.androsaver`

| File | Role |
|------|------|
| `ScreensaverService.kt` | DreamService entry point; system integration |
| `ScreensaverEngine.kt` | Orchestrates slideshow + visualizer; manages transitions, overlays, remote control; genre-driven mode switching when audio genre detection is active |
| `SettingsActivity.kt` | Settings UI host; contains `SettingsFragment` and `SourcesFragment` |
| `PreviewActivity.kt` | In-app preview without activating system screensaver |
| `Prefs.kt` | **All SharedPreferences key constants** — always use these |
| `ImageCache.kt` | Disk cache (≤200 images / 300 MB); offline fallback; EXIF-aware |
| `ExifRotationTransformation.kt` | Glide transform for EXIF orientation correction |
| `UpdateChecker.kt` | Polls GitHub Releases; supports Stable/Dev channels |
| `UpdateInstaller.kt` | Downloads APK and installs via FileProvider |
| `HttpClients.kt` | Shared OkHttp client instances |
| `WeatherFetcher.kt` | OpenWeatherMap current conditions fetcher |
| `BootReceiver.kt` | Receives BOOT_COMPLETED; pre-refreshes OAuth tokens for Google Drive, OneDrive, and Dropbox on device boot |

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
| `ImageSource.kt` | Interface: `suspend fun getImageUrls(): List<ImageItem>`; also defines `ImageItem` data class (url, headers, exifOrientation) | — |
| `GoogleDriveSource.kt` | Google Drive REST API v3 | OAuth token (auto-refresh) |
| `OneDriveSource.kt` | Microsoft Graph API | OAuth token (auto-refresh) |
| `DropboxSource.kt` | Dropbox API v2 | OAuth token (auto-refresh) |
| `ImmichSource.kt` | Immich REST API | API key header |
| `NextcloudSource.kt` | WebDAV PROPFIND | Basic auth (app password) |
| `SynologySource.kt` | Synology DSM FileStation REST | Session cookie (re-login every 25 min) |
| `LocalStorageSource.kt` | Android MediaStore | `READ_MEDIA_IMAGES` permission |
| `DefaultImagesSource.kt` | Bundled assets (`assets/default_images/`) — auto-used when no source is enabled | None |

See `docs/image-sources.md` for detailed auth patterns.

## Package: `com.androsaver.visualizer`

| File | Role |
|------|------|
| `VisualizerView.kt` | GLSurfaceView wrapper; manages `AudioEngine` + `VisualizerRenderer` lifecycle |
| `AudioEngine.kt` | Android `Visualizer` API → FFT (512 bins) → bass/mid/high bands + beat detection; applies genre weighting |
| `AudioData.kt` | Snapshot: bass, mid, high (0–1), beat (0–2), gain (current beatGain multiplier), waveform[], fft[] |
| `GLDraw.kt` | GL ES 2.0 utilities: shader compilation, matrix math, line/quad/circle/glyph drawing; bloom post-processing pipeline (scene FBO → luminance threshold → half-res 2-pass Gaussian blur → additive composite); pre-allocated `FloatBuffer` fields for zero GC pressure |
| `VisualizerRenderer.kt` | `GLSurfaceView.Renderer`; owns mode list; calls `mode.draw(gl, audio, tick)` each frame; exposes `frameTimeMs` (EMA-smoothed render time in ms) |
| `BaseMode.kt` | Abstract base: `abstract fun draw(gl: GLDraw, audio: AudioData, tick: Long)` |

### 16 Visualizer Modes (`com.androsaver.visualizer.modes`)

| Class | Display name | Visual concept |
|-------|-------------|----------------|
| `YantraMode.kt` | Yantra | 7 concentric rotating polygons + web/spoke connections |
| `CubeMode.kt` | Cube | Nested wireframe cubes + 2 orbiting satellite cubes (additive-blend trails) |
| `TriFluxMode.kt` | TriFlux | Triangle mosaic wall — tiles eject on beat |
| `LissajousMode.kt` | Lissajous | 3D trefoil knot with neon glow; treble brightens glow |
| `TunnelMode.kt` | Tunnel | First-person tunnel; triangle bursts spawn only in far third, cap 50 |
| `CorridorMode.kt` | Corridor | First-person neon rainbow corridor + spark particles |
| `NovaMode.kt` | Nova | 7-fold mirror kaleidoscope waveform |
| `SpiralMode.kt` | Spiral | 6-arm neon helix vortex |
| `BubblesMode.kt` | Bubbles | Rising translucent bubbles; bass-flash inflation + mega-bubble spawns |
| `PlasmaMode.kt` | Plasma | Full-screen sine interference pattern |
| `BranchesMode.kt` | Branches | Psychedelic fractal lightning tree, depth 7, neon glow |
| `ButterfliesMode.kt` | Butterflies | Neon butterfly pairs in mutual pursuit spiral; orbit tightens over lifetime |
| `FlowFieldMode.kt` | FlowField | 4 000 particles on sine/cosine noise field; bass gravity + treble scatter |
| `VortexMode.kt` | Vortex | Firework rockets arcing under gravity, exploding into embers; gain-aware interval |
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
            └─ VisualizerRenderer.onDrawFrame() → BaseMode.draw()  [16 modes]

Remote control (D-pad events in ScreensaverEngine):
  Visualizer: ←/→ = prev/next mode | ↑/↓ = intensity | other = finish()
  Slideshow:  → = next image | ← = previous image | other = finish()
```

## Permissions

| Permission | Purpose |
|-----------|---------|
| `INTERNET` | All cloud sources + weather + update checker |
| `ACCESS_NETWORK_STATE` | Network availability checks |
| `RECORD_AUDIO` | Music visualizer (Visualizer API captures global mix) |
| `MODIFY_AUDIO_SETTINGS` | Required alongside `RECORD_AUDIO` for Visualizer API |
| `READ_MEDIA_IMAGES` | Device storage image source (API 33+) |
| `READ_EXTERNAL_STORAGE` | Device storage image source (API < 33, `maxSdkVersion=32`) |
| `REQUEST_INSTALL_PACKAGES` | Self-update: install downloaded APK |
| `RECEIVE_BOOT_COMPLETED` | `BootReceiver` pre-refreshes OAuth tokens on device boot |

## Build Variants

| Variant | DEBUG_LOGGING | Use |
|---------|-------------|-----|
| `devRelease` | true | Dev APK (Downloader code 9149021) |
| `prodRelease` | false | Stable APK (Downloader code 7582483) |
