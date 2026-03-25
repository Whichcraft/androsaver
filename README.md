# AndroSaver

An Android TV screensaver app for the Huawei TV Stick (and any Android TV device). Choose between a **photo slideshow** (Google Drive / Synology NAS / device storage) or a fullscreen **music visualizer** that reacts to whatever is playing on the TV.

## Features

### Photo Slideshow
- **Google Drive source** — streams photos from a Drive folder using OAuth 2.0 device flow (no Google Play Services required)
- **Synology NAS source** — streams photos from any FileStation folder via the Synology DSM REST API
- **Device storage source** — uses photos from the TV's local storage via MediaStore
- All sources can be active simultaneously; images are merged and shuffled
- **Offline cache** — up to 200 images / 300 MB stored locally; used automatically as a fallback when sources are unreachable
- Six transition effects: **Crossfade**, **Fade to Black**, **Slide Left**, **Slide Right**, **Zoom In**, **Zoom Out**, plus a **Random** mode
- Configurable time per image (5 s – 30 min) and transition speed (1 – 5 seconds)
- **Ken Burns effect** — slow pan and zoom animation applied to each photo
- **Visualizer overlay** — music visualizer rendered semi-transparently on top of the slideshow

### Music Visualizer
- Ten audio-reactive OpenGL ES 2.0 effects: **Yantra**, **Cube**, **Plasma**, **Tunnel**, **Lissajous**, **Nova**, **Spiral**, **Bubbles**, **Spectrum**, **Waterfall**
- Reacts to system audio — works with any app playing music or video on the TV
- **Remote control** — use the TV remote while the visualizer is running:
  - **←** / **→** — previous / next visual effect
  - **↑** / **↓** — increase / decrease beat-response intensity (5 steps: Off → Subtle → Normal → High → Intense)
  - Any other key — dismiss the screensaver
- **Music Genre hint** — tunes the beat-detection frequency weighting to the music style (Any / Electronic / Rock / Classical); see [genre hint details](#music-genre-hint) below
- Auto-cycle mode rotates through all effects on a configurable interval (off by default)
- Configurable effect and intensity via Settings

### Photo Slideshow
- **Remote control** while slideshow is running:
  - **→** — skip to next image immediately
  - **←** — go back to previous image
  - Any other key — dismiss the screensaver

### General
- **Clock overlay** — date and time shown in the corner (available in both Slideshow and Visualizer mode)
- **Weather widget** — current temperature and conditions from OpenWeatherMap (available in both modes)
- **Schedule** — restrict the screensaver to a configurable active time window (e.g. 08:00–22:00)
- **Preview mode** — test the screensaver from the Settings app without activating the system screensaver
- Registered as a system Dream Service — appears in Android TV's screensaver settings

## Installation

The easiest way to install on an Amazon Fire TV or Android TV device is via the [Downloader app](https://www.aftvnews.com/downloader/):

| Release | Downloader code | Short URL |
|---------|----------------|-----------|
| Stable  | `7582483`      | [aftv.news/7582483](https://aftv.news/7582483) |
| Dev     | `9149021`      | [aftv.news/9149021](https://aftv.news/9149021) *(includes debug info)* |

1. Install **Downloader** from the Amazon Appstore or Google Play.
2. Open Downloader and enter the code (or short URL) for the version you want.
3. Follow the on-screen prompts to install the APK.

## Requirements

- Android 5.0+ (API 21)
- Android TV device (tested on Huawei TV Stick)
- Android Studio Hedgehog or later (to build from source)
- `RECORD_AUDIO` permission required for the Music Visualizer (prompted automatically when you select Visualizer mode in Settings)
- `READ_MEDIA_IMAGES` / `READ_EXTERNAL_STORAGE` permission required for the Device Photos source (prompted when you enable the toggle)

## Building from Source

1. Clone the repo and open it in Android Studio.
2. Android Studio will download the Gradle wrapper automatically on first sync.
3. Build and install via `Run > Run 'app'` or `adb install`.

## Setup

### Google Drive

Google's device-auth flow works without Google Play Services, making it ideal for Huawei devices.

1. Go to [Google Cloud Console](https://console.cloud.google.com/) and create a project.
2. Enable the **Google Drive API**.
3. Create OAuth 2.0 credentials — choose application type **"TV and Limited Input Devices"**.
4. Open the **AndroSaver Settings** app on your TV.
5. Tap **Google Drive Setup** and enter:
   - **OAuth Client ID** — from your Google Cloud project
   - **OAuth Client Secret** — from your Google Cloud project
   - **Folder ID** *(optional)* — leave blank to use the root of My Drive
6. Tap **Authorize with Google** — the screen displays a short code and a URL.
7. On any other device, visit the URL and enter the code.
8. Return to Settings and enable the **Google Drive** toggle.

> **Getting a Folder ID:** Open Google Drive in a browser, navigate to the folder, and copy the ID from the URL (`/drive/folders/<FOLDER_ID>`).

### Synology NAS

1. Open **AndroSaver Settings** on your TV.
2. Tap **Synology NAS Setup** and fill in:

   | Field | Description |
   |-------|-------------|
   | **Host / IP** | e.g. `192.168.1.100` or `nas.local` |
   | **Port** | `5000` (HTTP) or `5001` (HTTPS) |
   | **Use HTTPS** | Enable if your DSM uses HTTPS (self-signed certs are accepted for local use) |
   | **Username** | A DSM account with FileStation access |
   | **Password** | DSM account password |
   | **Image Folder Path** | e.g. `/photos` or `/homes/alice/Pictures` |

3. Tap **Test Connection** to verify — a success message shows how many images were found.
4. Tap **Save**, then enable the **Synology NAS** toggle in Settings.

### Supported Image Formats

| Source | Formats |
|--------|---------|
| Google Drive | Any format with an `image/` MIME type |
| Synology NAS | `jpg`, `jpeg`, `png`, `gif`, `webp`, `bmp`, `heic`, `heif` |

### Local Storage

Enable the **Device Photos** toggle in Settings. No additional setup is required — AndroSaver reads images from the device's external storage via MediaStore (up to 500 photos, sorted by most recent).

### Weather

1. Get a free API key from [openweathermap.org](https://openweathermap.org/api).
2. Open **AndroSaver Settings** and enter the **Weather City** and **API Key**.
3. Enable the **Weather** toggle.

### Preview

Tap **Preview** in the Settings app to test the screensaver immediately without waiting for the system idle timeout.

### Activating the Screensaver

- **Android TV:** Settings → Device Preferences → Screen saver → Select **AndroSaver**
- The screensaver activates automatically when the TV is idle.

## Settings Reference

All options are configured in the **AndroSaver Settings** app. The top-level **Screensaver Mode** picker switches between Photo Slideshow and Music Visualizer — only the relevant settings are shown.

### Photo Slideshow

#### Image Sources

| Setting | Description |
|---------|-------------|
| **Google Drive** toggle | Enable/disable fetching images from Google Drive |
| **Google Drive Setup** | Configure OAuth credentials and folder |
| **Synology NAS** toggle | Enable/disable fetching images from a Synology NAS |
| **Synology NAS Setup** | Configure host, credentials, and folder path |
| **Device Photos** toggle | Enable/disable fetching images from local device storage |

All sources can be enabled at the same time — images from all sources are merged and shuffled. When no source is reachable, the last-fetched images are loaded from the local cache.

#### Slideshow

| Setting | Options | Default | Description |
|---------|---------|---------|-------------|
| **Time per Image** | 5 s, 10 s, 15 s, 30 s, 1 min, 2 min, 5 min, 10 min, 15 min, 20 min, 30 min | 10 s | How long each image is displayed |
| **Transition Speed** | 1 s, 2 s, 3 s, 4 s, 5 s | 1.5 s | Duration of the animation between images |
| **Transition Effect** | Crossfade, Fade to Black, Slide Left, Slide Right, Zoom In, Zoom Out, Random | Crossfade | Animation style used between images |
| **Ken Burns Effect** | On / Off | On | Slow pan and zoom applied to each photo |
| **Visualizer Overlay** | On / Off | Off | Render the music visualizer semi-transparently over photos |
| **Overlay Opacity** | 0.1 – 1.0 | 0.3 | Opacity of the visualizer overlay |

| Effect | Description |
|--------|-------------|
| **Crossfade** | Old image fades out while new image fades in simultaneously |
| **Fade to Black** | Old image fades to black, then new image fades in |
| **Slide Left** | New image slides in from the right, old slides out to the left |
| **Slide Right** | New image slides in from the left, old slides out to the right |
| **Zoom In** | New image scales up from slightly smaller while fading in |
| **Zoom Out** | Old image scales up and fades out as new image fades in |
| **Random** | A different effect is picked at random for each transition |

### Music Visualizer

| Setting | Options | Default | Description |
|---------|---------|---------|-------------|
| **Visual Effect** | Auto, Yantra, Cube, Plasma, Tunnel, Lissajous, Nova, Spiral, Bubbles, Spectrum, Waterfall | Auto | Which visualizer to show; Auto cycles through all effects |
| **Effect Intensity** | Off, Subtle, Normal, High, Intense | Normal | How strongly the visuals react to the beat |
| **Auto-cycle Interval** | Off, 1 min, 2 min, 5 min | Off | How often Auto mode switches to the next effect |
| **Music Genre** | Any, Electronic, Rock, Classical | Any | Tunes beat-detection frequency weighting to the music style |

#### Remote Control (while visualizer is running)

| Key | Action |
|-----|--------|
| **←** | Previous effect |
| **→** | Next effect |
| **↑** | Increase intensity (one step) |
| **↓** | Decrease intensity (one step) |
| Any other key | Dismiss the screensaver |

Intensity changes made with the remote are saved and reflected in Settings.

#### Music Genre Hint

The genre setting adjusts how the beat-detection algorithm weights bass frequency bins. It does not change the visual style directly — it changes what the algorithm considers a *beat*, making visuals more or less reactive to different frequency ranges:

| Genre | Effect |
|-------|--------|
| **Any** | Flat weighting across all bass frequencies (default) |
| **Electronic** | Boosts sub-bass (~0–170 Hz) by 50%, reduces upper bass/low-mids (~430–860 Hz) — more sensitive to kick drums and synth bass |
| **Rock** | Boosts mid-bass range (~85–345 Hz) by 30% — better response to electric guitar and driven kick |
| **Classical** | Reduces overall bass sensitivity, boosts upper harmonics — suited to orchestral content with little sub-bass |

If the visuals feel sluggish or over-reactive, try switching the genre hint to match what is playing.

#### Remote Control (while slideshow is running)

| Key | Action |
|-----|--------|
| **→** | Skip to next image |
| **←** | Go back to previous image |
| Any other key | Dismiss the screensaver |

Manually skipping resets the auto-advance timer so the new image gets a full display duration.

### Display Overlays

These settings apply in **both** Slideshow and Visualizer mode.

| Setting | Options | Default | Description |
|---------|---------|---------|-------------|
| **Show Clock** | On / Off | Off | Display time and date in the corner |
| **Show Weather** | On / Off | Off | Show current weather in the corner |
| **Weather City** | text | — | City name passed to OpenWeatherMap |
| **OpenWeatherMap API Key** | text | — | Free API key from openweathermap.org |

### General Settings

| Setting | Options | Default | Description |
|---------|---------|---------|-------------|
| **Schedule** | On / Off | Off | Restrict the screensaver to an active time window |
| **Active from / until** | 0–23 h | 8 / 22 | Hour range during which the screensaver is allowed to run |

#### Visual Effects

| Effect | Description |
|--------|-------------|
| **Yantra** | Psychedelic sacred-geometry mandala with 6 concentric polygon rings, web connections, neon spokes, and beat-driven spring pulses |
| **Cube** | Dual wireframe cubes with slow rotation and spectrum colour-fade |
| **Plasma** | Full-screen sine-interference plasma with four overlapping wave fields |
| **Tunnel** | First-person ride through a neon tube; beat spawns triangles that fly toward the camera |
| **Lissajous** | 3D trefoil Lissajous knot with two-pass neon glow and hard beat spring burst |
| **Nova** | Waveform kaleidoscope with 7-fold mirror symmetry across 4 spinning layers |
| **Spiral** | Neon helix vortex with 6 arms, audio-reactive radius breathing, cross-ring connections |
| **Bubbles** | Translucent full-screen rising bubbles driven by bass energy; synchronised beat pulse |
| **Spectrum** | Log-spaced spectrum analyser with peak markers and waveform overlay |
| **Waterfall** | Scrolling time-frequency spectrogram; beat flashes the leading edge |

## Architecture

```
ScreensaverService (DreamService)
  └── ScreensaverEngine
        ├── Photo Slideshow mode
        │   ├── GoogleDriveSource    ← Drive REST API v3 + token refresh
        │   ├── SynologySource       ← Synology DSM FileStation API
        │   ├── LocalStorageSource   ← MediaStore device photos
        │   ├── ImageCache           ← offline fallback (200 images / 300 MB)
        │   ├── Ken Burns animator   ← pan/zoom per photo
        │   └── VisualizerView       ← optional overlay (semi-transparent)
        ├── Music Visualizer mode
        │   ├── AudioEngine          ← Android Visualizer API, FFT + beat detection
        │   └── VisualizerView (GLSurfaceView)
        │       └── VisualizerRenderer (OpenGL ES 2.0)
        │           └── 10 × BaseMode  ← Yantra, Cube, Plasma, Tunnel, Lissajous,
        │                                 Nova, Spiral, Bubbles, Spectrum, Waterfall
        ├── Clock overlay            ← date/time updated every minute
        ├── WeatherFetcher           ← OpenWeatherMap, cached 30 min
        └── Schedule check          ← restricts active hours

SettingsActivity (PreferenceFragment)
  ├── GoogleDriveSetupActivity → GoogleAuthActivity (device flow)
  ├── SynologySetupActivity
  └── PreviewActivity              ← in-app screensaver preview
```

Images are loaded with [Glide](https://github.com/bumptech/glide) (OkHttp3 backend), scaled to display resolution, and rendered across two alternating `ImageView`s with configurable transition effects. The visualizer is a Kotlin/OpenGL port of [psysuals](https://github.com/Whichcraft/psysuals).

## Privacy

Credentials are stored in Android SharedPreferences (on-device only). No data is sent anywhere except to Google and/or your local NAS.

## License

MIT
