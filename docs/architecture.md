# AndroSaver Architecture Reference

## Package: `com.androsaver`

| File | Role |
|------|------|
| `ScreensaverService.kt` | DreamService entry point; system integration |
| `ScreensaverEngine.kt` | Orchestrates slideshow + visualizer; manages transitions, overlays, remote control, cancellable weather/cache work, and per-slot Glide targets with session/sequence guards |
| `SettingsActivity.kt` | Settings UI host; contains `SettingsFragment` and `SourcesFragment` |
| `PreviewActivity.kt` | In-app preview without activating system screensaver |
| `Prefs.kt` | **All SharedPreferences key constants** — always use these |
| `ImageCache.kt` | Disk cache (≤200 images / 150 MB); offline fallback; serialized, atomic manifest updates |
| `UpdateChecker.kt` | Polls GitHub Releases; supports Stable/Dev channels |
| `UpdateInstaller.kt` | HTTPS-only APK download via temporary file, then FileProvider install |
| `HttpClients.kt` | Shared OkHttp clients; normal TLS by default and opt-in trust-all only for an explicitly marked self-hosted provider |
| `WeatherFetcher.kt` | OpenWeatherMap current conditions fetcher |
| `BootReceiver.kt` | Receives BOOT_COMPLETED and delegates constrained prefetch work to WorkManager |
| `PrefetchScheduler.kt` | Schedules periodic background prefetching of images using WorkManager |
| `ImagePrefetchWorker.kt` | Background worker that queries remote image sources concurrently to warm cache |
| `SecurePreferences.kt` | SharedPreferences wrapper that uses EncryptedSharedPreferences for credentials and fails closed if encrypted storage is unavailable |
| `SecurePreferenceDataStore.kt` | PreferenceDataStore interface bridge for Settings screen integration |

### Upstream visualizer source

`psysuals/` is a Git subtree imported from the upstream psysuals repository.
The current Android port tracks upstream v3.14.0 (`e539626`) and applies the
OpenGL ES 2.0 and Android lifecycle adaptations documented in
`docs/psysuals-port-notes.md`. To update it, add/fetch the upstream remote with
`git remote add psysuals-upstream https://github.com/Whichcraft/psysuals.git`
when it is not already configured, then run
`git subtree pull --prefix=psysuals psysuals-upstream main --squash`, compare
the upstream diff with every affected Kotlin mode, backport applicable changes,
and rely on GitHub CI for Android verification.

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
| `ImageSource.kt` | Interface: `suspend fun getImageUrls()`; `ImageItem` also carries a stable non-secret provider identity | — |
| `GoogleDriveSource.kt` | Google Drive REST API v3 | OAuth token (auto-refresh) |
| `OneDriveSource.kt` | Microsoft Graph API | OAuth token (auto-refresh) |
| `DropboxSource.kt` | Dropbox API v2 | OAuth token (auto-refresh) |
| `ImmichSource.kt` | Immich REST API | API key header |
| `NextcloudSource.kt` | WebDAV PROPFIND | Basic auth (app password) |
| `SynologySource.kt` | Synology DSM FileStation REST | POST login; in-memory session SID |
| `LocalStorageSource.kt` | Android MediaStore | `READ_MEDIA_IMAGES` permission |
| `DefaultImagesSource.kt` | Bundled assets (`assets/default_images/`) — auto-used when no source is enabled | None |

See `docs/image-sources.md` for detailed auth patterns.

## Package: `com.androsaver.visualizer`

| File | Role |
|------|------|
| `VisualizerView.kt` | GLSurfaceView wrapper; manages `AudioEngine` + `VisualizerRenderer` lifecycle |
| `AudioEngine.kt` | Android `Visualizer` API → FFT (512 bins) → bass/mid/high bands + beat detection; applies genre weighting; warm-starts smoothing and suppresses phantom first-frame beats |
| `AudioData.kt` | Snapshot: bass, mid, high (0–1), beat (0–2), gain (current beatGain multiplier), waveform[], fft[] |
| `GLDraw.kt` | GL ES 2.0 utilities: shader compilation, matrix math, line/quad/circle/glyph drawing; bloom post-processing pipeline (scene FBO → luminance threshold → half-res 2-pass Gaussian blur → additive composite); pre-allocated `FloatBuffer` fields for zero GC pressure |
| `VisualizerRenderer.kt` | `GLSurfaceView.Renderer`; applies UI mode requests atomically on the GL thread, resets the selected mode, and exposes EMA-smoothed `frameTimeMs` |
| `BaseMode.kt` | Abstract base: `draw(gl, audio, tick)` plus reset and EGL-context recreation hooks |

### 27 Visualizer Modes (`com.androsaver.visualizer.modes`)

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
| `FlowFieldMode.kt` | FlowField | 8 000–40 000 particles on sine/cosine noise field; bass gravity + treble scatter |
| `FireworksMode.kt` | Fireworks | Firework rockets arcing under gravity, exploding into embers; gain-aware interval |
| `AuroraMode.kt` | Aurora | Five horizontally undulating Northern Lights curtains |
| `LatticeMode.kt` | Lattice | 14x9 crystal grid with peak normalization and shockwave ring |
| `MyceliumMode.kt` | Mycelium | Swirling fungal network with multi-colony cores |
| `MagnetarMode.kt` | Magnetar | 4 000 particles riding a rotating magnetic dipole field |
| `SlimeMoldMode.kt` | SlimeMold | Physarum 2 500-agent trail simulation with adaptive-grid diffusion |
| `CliffordMode.kt` | Clifford | Strange attractor: 7 000 walkers × 6 passes, dynamic framing, presets |
| `MobiusMode.kt` | Mobius | 3-D wireframe Möbius strip (latitude lines only) |
| `ChromaticMode.kt` | Chromatic | Prismatic raindrop ripples with RGB-separated outlines |
| `PersistenceMode.kt` | Persistence | Nested rotating 3D Platonic solids wireframes with perspective and depth-fading |
| `SynapseMode.kt` | Synapse | Living 28–90-node neural graph with safe nearest-neighbour rewiring |
| `HeartbeatMode.kt` | Heartbeat | Expanding polygon rings with beat-driven morphing |
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
DreamService.onAttachedToWindow()
  └─ ScreensaverEngine.start()
       ├─ [Slideshow/Static Image] load configured sources through ImageSourceRegistry → ImageCache/Private Store → Glide
       │    ├─ one Glide CustomTarget tracked per slot; old slot target cleared before reuse
       │    ├─ late image callbacks ignored via transition-sequence guard
       │    ├─ slideshow start/refresh/fallback work fenced by slideshow-session token
       │    └─ next slide scheduled only after the current image is displayed; transition speed is added on top of image dwell time
       └─ [Visualizer] VisualizerView.start()
            ├─ AudioEngine: Visualizer API → FFT → AudioData (60 fps)
            └─ VisualizerRenderer.onDrawFrame() → BaseMode.draw()  [27 modes]

DreamService.onDreamingStarted() → resume visualizer
DreamService.onDreamingStopped() → pause visualizer

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
| `REQUEST_INSTALL_PACKAGES` | Self-update in standard dev/prod variants; removed from the Play Store variant |
| `RECEIVE_BOOT_COMPLETED` | `BootReceiver` restores the WorkManager prefetch schedule after boot |

## Build Variants

| Variant | DEBUG_LOGGING | Use |
|---------|-------------|-----|
| `devStandardRelease` | true | Dev APK with GitHub updater |
| `prodStandardRelease` | false | Stable APK with GitHub updater |
| `prodPlaystoreRelease` | false | Play Store AAB; updater entry point hidden and install permission removed |
