# AndroSaver

An Android TV screensaver app for the Huawei TV Stick, Amazon Fire TV Stick, and any Android TV device. Choose between a **photo slideshow** (Google Drive, OneDrive, Dropbox, Immich, Nextcloud, Synology NAS, or device storage) or a fullscreen **music visualizer** — the perfect companion for listening to music on your TV. Put on some music, let the screen go idle, and AndroSaver turns your TV into an audio-reactive light show that pulses and morphs in real time.

## What's New in v2.0

**Three spectacular new visualizer effects** — the biggest visual update yet:

- 🔺 **TriFlux** — A wall of triangles covering the entire screen, every edge pulsing with a rolling rainbow. On every beat, tiles hurl themselves to the foreground, bounce wildly off the screen edges, then snap back into the mosaic. Nothing sits still.
- ⚡ **Branches** — A psychedelic fractal lightning tree erupts from screen centre: nine neon arms split recursively to depth 7, each segment drawn twice as a glowing halo and a white-hot core. Mid frequencies twist every branch angle in real time; beats fire extra arms and flood the screen with colour.
- 🌈 **Corridor** — A first-person ride through an infinite neon rainbow corridor. Twenty-eight luminous frames rush toward you, their hues sweeping the full spectrum from far to near. Bass drives speed; every beat flares the nearest frames and launches glowing spark particles that streak from the centre outward.

Plus a full tune-up of every existing effect — smoother physics, denser geometry, and much more satisfying beat response across the board.

## Features

### Photo Slideshow
- **Google Drive source** — streams photos from a Drive folder using OAuth 2.0 device flow (no Google Play Services required)
- **OneDrive source** — streams photos from Microsoft OneDrive using OAuth 2.0 device flow; works with personal and work/school accounts
- **Dropbox source** — streams photos from a Dropbox folder via OAuth 2.0 authorization code flow; access token auto-refreshed using App Key + App Secret
- **Immich source** — streams photos from a self-hosted [Immich](https://immich.app) server via its REST API; API key auth; optional album filter
- **Nextcloud source** — streams photos from any folder via WebDAV; works with app passwords and self-signed certificates
- **Synology NAS source** — streams photos from any FileStation folder via the Synology DSM REST API; session re-authenticated automatically every 25 minutes
- **Device storage source** — uses photos from the TV's local storage via MediaStore
- All sources can be active simultaneously; images are merged and shuffled
- **Offline cache** — up to 200 images / 300 MB stored locally; used automatically as a fallback when sources are unreachable
- Six transition effects: **Crossfade**, **Fade to Black**, **Slide Left**, **Slide Right**, **Zoom In**, **Zoom Out**, plus a **Random** mode
- Configurable time per image (5 s – 30 min) and transition speed (1 – 5 seconds)
- **Ken Burns effect** — slow pan and zoom animation; each photo always comes to rest centered
- **EXIF orientation** — portrait photos from phones are displayed upright regardless of source
- **Visualizer overlay** — music visualizer rendered semi-transparently on top of the slideshow

### Music Visualizer
Designed for listening sessions: start playing music in any app, let the screen idle, and AndroSaver takes over with a fullscreen light show that reacts to every beat.

- Thirteen audio-reactive OpenGL ES 2.0 effects: **Yantra**, **Cube**, **TriFlux**, **Lissajous**, **Tunnel**, **Corridor**, **Nova**, **Spiral**, **Bubbles**, **Plasma**, **Branches**, **Spectrum**, **Waterfall**
- Reacts to system audio — works with any music or streaming app on the TV
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
- **In-app updater** — checks GitHub Releases on each Settings open; prompts to install if a newer build is available; update channel (Stable / Dev) selectable in Settings
- Registered as a system Dream Service — appears in Android TV's screensaver settings

## Installation

The easiest way to install on an Amazon Fire TV or Android TV device is via the [Downloader app](https://www.aftvnews.com/downloader/):

| Release | Downloader code | Short URL | Notes |
|---------|----------------|-----------|-------|
| Stable  | `7582483`      | [aftv.news/7582483](https://aftv.news/7582483) | No debug logging |
| Dev     | `9149021`      | [aftv.news/9149021](https://aftv.news/9149021) | Debug logging enabled |

1. Install **Downloader** from the Amazon Appstore or Google Play.
2. Open Downloader and enter the code (or short URL) for the version you want.
3. Follow the on-screen prompts to install the APK.

## Requirements

- Android 5.0+ (API 21)
- Any Android device — optimised for Android TV (tested on Huawei TV Stick and Amazon Fire TV Stick), also works on tablets and phones
- Android Studio Hedgehog or later (to build from source)
- `RECORD_AUDIO` permission required for the Music Visualizer (prompted automatically when you select Visualizer mode in Settings)
- `READ_MEDIA_IMAGES` / `READ_EXTERNAL_STORAGE` permission required for the Device Photos source (prompted when you enable the toggle)

## Building from Source

1. Clone the repo and open it in Android Studio.
2. Android Studio will download the Gradle wrapper automatically on first sync.
3. Select the build variant in **Build > Select Build Variant**:
   - `devRelease` — debug logging enabled (mirrors the Dev APK)
   - `prodRelease` — no debug logging (mirrors the Stable APK)
4. Build and install via `Run > Run 'app'` or `adb install`.

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

### OneDrive

Microsoft's device auth flow works without a browser redirect, making it ideal for Android TV.

1. Go to [portal.azure.com](https://portal.azure.com) and sign in.
2. Open **Azure Active Directory → App registrations → New registration**.
3. Give it any name; under **Supported account types** select *Accounts in any organizational directory and personal Microsoft accounts*.
4. Under **Authentication → Platform configurations**, add **Mobile and desktop applications**.
5. Still under Authentication, enable **Allow public client flows**.
6. Copy the **Application (client) ID**.
7. Open **AndroSaver Settings** on your TV.
8. Tap **OneDrive Setup** and enter:
   - **Client ID** — the Application (client) ID from step 6
   - **Folder Path** *(optional)* — leave blank for root, or enter e.g. `/Photos`
9. Tap **Authorize with Microsoft** — the screen shows a URL and a short code.
10. On any other device, visit the URL and enter the code.
11. Return to Settings and enable the **OneDrive** toggle.

### Dropbox

1. Go to [dropbox.com/developers](https://www.dropbox.com/developers) → **App Console → Create app**.
2. Set access to **Full Dropbox** (or **App folder** if you prefer).
3. From the app's **Settings** tab, copy the **App Key** and **App Secret**.
4. Open **AndroSaver Settings** on your TV.
5. Tap **Image Sources → Dropbox Setup** and enter:
   - **App Key** and **App Secret** from step 3
   - **Folder Path** *(optional)* — leave blank for root, or enter e.g. `/Photos`
6. Tap **Authorize with Dropbox** — the screen displays a URL.
7. On any other device, visit the URL and sign in to Dropbox.
8. Dropbox shows a short code — paste it back into the TV and tap **Submit Code**.
9. Return to Settings and enable the **Dropbox** toggle.

> Access tokens are refreshed automatically using the App Key + App Secret; no re-authorization is needed unless you revoke the app.

### Immich

1. In Immich web UI, go to **Account Settings → API Keys** and create a new key.
2. Open **AndroSaver Settings** on your TV.
3. Tap **Immich Setup** and fill in:

   | Field | Description |
   |-------|-------------|
   | **Host / IP** | e.g. `192.168.1.50` or `photos.example.com` |
   | **Port** | `2283` (default) or `443` (HTTPS) |
   | **Use HTTPS** | Enable if your Immich instance uses HTTPS (self-signed certs are accepted) |
   | **API Key** | The key generated in Immich Account Settings |
   | **Album ID** | Optional — paste an album UUID to show only that album; leave blank for all photos |

4. Tap **Test Connection** to verify, then **Save** and enable the **Immich** toggle in Settings.

> To get an album's UUID: open the album in Immich and copy the UUID from the URL (`/albums/<uuid>`).

### Nextcloud

1. Open **AndroSaver Settings** on your TV.
2. Tap **Nextcloud Setup** and fill in:

   | Field | Description |
   |-------|-------------|
   | **Host / IP** | e.g. `cloud.example.com` or `192.168.1.50` |
   | **Port** | `443` (HTTPS) or `80` (HTTP) |
   | **Use HTTPS** | Enable for HTTPS (self-signed certs are accepted) |
   | **Username** | Your Nextcloud username |
   | **Password / App Password** | Your Nextcloud password, or an app password from Nextcloud's Security settings |
   | **Image Folder Path** | e.g. `/Photos` or `/family/Pictures` |

3. Tap **Test Connection** to verify — a success message shows how many images were found.
4. Tap **Save**, then enable the **Nextcloud** toggle in Settings.

> **App passwords** are recommended: in Nextcloud, go to Settings → Security → Devices & Sessions → Create new app password.

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
| OneDrive | Any format with an `image/` MIME type |
| Dropbox | Any format supported by Glide (JPEG, PNG, WebP, GIF, HEIC, BMP) |
| Immich | Any `IMAGE` type asset (as classified by Immich) |
| Nextcloud | `jpg`, `jpeg`, `png`, `gif`, `webp`, `bmp`, `heic`, `heif` (or any `image/` MIME type) |
| Synology NAS | `jpg`, `jpeg`, `png`, `gif`, `webp`, `bmp`, `heic`, `heif` |

### Local Storage

Enable the **Device Photos** toggle in Settings. No additional setup is required — AndroSaver reads images from the device's external storage via MediaStore (up to 500 photos, sorted by most recent).

### Default Images

When **no image source is enabled**, AndroSaver automatically falls back to a set of bundled images stored in `app/src/main/assets/default_images/` in this repository. Drop any JPEG, PNG, WebP, GIF, or BMP files into that folder and commit — they will be included in the next build and shown on any device where no source has been configured.

This lets you ship the app with a ready-to-use set of demo or placeholder photos out of the box. As soon as any source (Drive, Dropbox, Device Photos, etc.) is enabled, the bundled images are ignored.

### Weather

1. Get a free API key from [openweathermap.org](https://openweathermap.org/api).
2. Open **AndroSaver Settings** and enter the **Weather City** and **API Key**.
3. Enable the **Weather** toggle.

### Preview

Tap **Preview** in the Settings app to test the screensaver immediately without waiting for the system idle timeout.

### Activating the Screensaver

- **Android TV:** Settings → Device Preferences → Screen saver → Select **AndroSaver**
- **Tablet / Phone:** Settings → Display → Screen saver → Select **AndroSaver** *(exact path varies by Android version and OEM)*
- The screensaver activates automatically when the device is idle (TV) or charging and idle (tablet/phone).

### Using on a Tablet or Phone

AndroSaver works on any Android device — not just Android TV. All features (photo slideshow, music visualizer, clock, weather) work as-is.

A few differences compared to TV:

- **Remote navigation** (← → to switch effects or skip photos, ↑ ↓ to adjust intensity) requires a connected keyboard or D-pad. Without one, the screensaver still runs; you just can't switch effects mid-session.
- **Touch dismisses the screensaver** in slideshow mode — this is standard Android behaviour.
- The screensaver activates while the device is **charging and idle**, rather than just idle.

## Settings Reference

All options are configured in the **AndroSaver Settings** app. The top-level **Screensaver Mode** picker switches between Photo Slideshow and Music Visualizer — only the relevant settings are shown.

### Photo Slideshow

#### Image Sources

Tap **Image Sources** on the main Settings screen to open the sources sub-page. A summary of currently-active sources is shown on the entry (e.g. "Google Drive, Dropbox"). Press Back to return to main Settings.

| Setting | Description |
|---------|-------------|
| **Google Drive** toggle | Enable/disable fetching images from Google Drive |
| **Google Drive Setup** | Configure OAuth credentials and folder |
| **OneDrive** toggle | Enable/disable fetching images from Microsoft OneDrive |
| **OneDrive Setup** | Authorize with Microsoft account and set folder path |
| **Dropbox** toggle | Enable/disable fetching images from Dropbox |
| **Dropbox Setup** | Configure App Key/Secret and authorize |
| **Immich** toggle | Enable/disable fetching images from a self-hosted Immich server |
| **Immich Setup** | Configure host, API key, and optional album ID |
| **Nextcloud** toggle | Enable/disable fetching images from a Nextcloud instance |
| **Nextcloud Setup** | Configure host, credentials, and folder path |
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
| **Visual Effect** | Auto, Yantra, Cube, TriFlux, Lissajous, Tunnel, Corridor, Nova, Spiral, Bubbles, Plasma, Branches, Spectrum, Waterfall | Auto | Which visualizer to show; Auto cycles through all effects |
| **Effect Intensity** | Off, Low, Medium, High, Max | Low | How strongly the visuals react to the beat |
| **Auto-cycle Interval** | Off, 1 min, 2 min, 5 min, 10 min, 15 min | 2 min | How often the screensaver switches to the next effect |
| **Music Genre** | Any, Electronic, Rock, Classical | Any | Tunes beat-detection frequency weighting to the music style |

#### Remote Control (while visualizer is running)

| Key | Action |
|-----|--------|
| **←** | Previous effect |
| **→** | Next effect |
| **↑** | Increase intensity (one step) |
| **↓** | Decrease intensity (one step) |
| Any other key | Dismiss the screensaver |

Intensity changes made with the remote are saved and reflected in Settings. Switching effects (← / →) resets intensity to the last saved value.

#### Music Genre Hint

The genre setting adjusts how the beat-detection algorithm weights bass frequency bins. It does not change the visual style directly — it changes what the algorithm considers a *beat*, making visuals more or less reactive to different frequency ranges.

Beat detection works on the lowest 20 FFT bins (roughly 0–860 Hz at 44100 Hz / 512-bin FFT). Each bin gets a weight multiplier; the weighted average of those 20 bins is compared against a short-term running average to detect transients. Genre changes only the per-bin weights:

| Genre | Bins boosted | Bins reduced | Net effect |
|-------|-------------|--------------|------------|
| **Any** | — (all ×1.0) | — | Flat — treats all bass frequencies equally |
| **Electronic** | 0–4 (~0–172 Hz) ×1.5 | 10–19 (~430–860 Hz) ×0.7 | Sub-bass boost + upper-bass cut — highly sensitive to 4-on-the-floor kick and synth bass; ignores mid-bass mud |
| **Rock** | 2–8 (~86–344 Hz) ×1.3 | — | Punchy mid-bass boost — better response to kick/snare hits and overdriven bass guitar; leaves sub-bass untouched |
| **Classical** | 10–19 (~430–860 Hz) ×1.4 | 0–9 (~0–387 Hz) ×0.6 | Strong sub-bass cut + upper-bass/low-mid boost — avoids false beats from bass-heavy mastering; responds to orchestral transients in the 400–800 Hz range |

If the visuals feel sluggish or fire on every bass rumble, try switching genre to match what is playing.

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

### About

| Setting | Options | Default | Description |
|---------|---------|---------|-------------|
| **Update Channel** | Stable, Dev | Stable (prod) / Dev (dev build) | Which GitHub Release to check for updates against |
| **AndroSaver** (version row) | — | — | Shows installed version; tapping installs a newer build when one is available (checked automatically each time Settings opens) |

#### Visual Effects

See [visualizer-music-reactivity.md](visualizer-music-reactivity.md) for a detailed breakdown of how each effect reacts to bass, mid, high, and beat signals.

| Effect | Description |
|--------|-------------|
| **Yantra** | Psychedelic sacred-geometry mandala with 7 concentric polygon rings, web connections, and neon spokes; rings spin at graduated speeds and kick outward on every beat |
| **Cube** | Dual wireframe cubes with slow rotation, motion trails, and spectrum colour-fade; two orbiting satellites leave additive-blend trails; size pulses on each beat |
| **TriFlux** ✨ | A living mosaic of triangles covering the full screen — every edge pulses with a rolling rainbow sweep; on beats, tiles eject to the foreground, bounce off screen edges, and spring back |
| **Lissajous** | 3D trefoil Lissajous knot with two-pass neon glow and hard beat spring burst |
| **Tunnel** | Smooth first-person ride through a curving neon tube; bass punches spawn dense bursts of triangles with rotating star polygons at each ring centre |
| **Corridor** ✨ | Infinite neon rainbow corridor — 28 luminous frames rush toward you; hue sweeps full spectrum far-to-near; bass drives speed; beats fire glowing spark particles that streak outward |
| **Nova** | Waveform kaleidoscope with 7-fold mirror symmetry across 4 spinning layers |
| **Spiral** | Neon helix vortex with 6 arms, audio-reactive radius breathing, cross-ring connections |
| **Bubbles** | Translucent full-screen rising bubbles driven by bass energy; synchronised beat pulse |
| **Plasma** | Full-screen sine-interference plasma with four overlapping wave fields |
| **Branches** ✨ | Psychedelic fractal lightning tree — nine neon arms split recursively to depth 7 with glowing halos and bright cores; mid frequencies twist branch angles live; beats fire extra arms |
| **Spectrum** | Log-spaced spectrum analyser with peak markers and waveform overlay |
| **Waterfall** | Scrolling time-frequency spectrogram; beat flashes the leading edge |

## Architecture

```
ScreensaverService (DreamService)
  └── ScreensaverEngine
        ├── Photo Slideshow mode
        │   ├── GoogleDriveSource    ← Drive REST API v3 + OAuth token refresh
        │   ├── OneDriveSource       ← Microsoft Graph API + OAuth token refresh
        │   ├── DropboxSource        ← Dropbox API v2 + OAuth token refresh
        │   ├── ImmichSource         ← Immich REST API + API key auth
        │   ├── NextcloudSource      ← WebDAV PROPFIND + Basic Auth
        │   ├── SynologySource       ← Synology DSM FileStation API; re-login every 25 min
        │   ├── LocalStorageSource   ← MediaStore device photos + EXIF orientation
        │   ├── ImageCache           ← offline fallback (200 images / 300 MB); EXIF preserved
        │   ├── ExifRotationTransformation ← corrects orientation for local/cached images
        │   ├── Ken Burns animator   ← pan/zoom per photo; always ends centered
        │   └── VisualizerView       ← optional overlay (semi-transparent)
        ├── Music Visualizer mode
        │   ├── AudioEngine          ← Android Visualizer API, FFT + beat detection
        │   └── VisualizerView (GLSurfaceView)
        │       └── VisualizerRenderer (OpenGL ES 2.0)
        │           └── 13 × BaseMode  ← Yantra, Cube, TriFlux, Lissajous, Tunnel,
        │                                 Corridor, Nova, Spiral, Bubbles, Plasma,
        │                                 Branches, Spectrum, Waterfall
        ├── Clock overlay            ← date/time updated every minute
        ├── WeatherFetcher           ← OpenWeatherMap, cached 30 min
        └── Schedule check          ← restricts active hours

SettingsActivity
  ├── SettingsFragment             ← main screen
  │   └── SourcesFragment          ← Image Sources sub-screen (back-stack navigation)
  ├── GoogleDriveSetupActivity → GoogleAuthActivity (device flow)
  ├── OneDriveSetupActivity → OneDriveAuthActivity (device flow)
  ├── DropboxSetupActivity → DropboxAuthActivity (auth code flow)
  ├── ImmichSetupActivity
  ├── NextcloudSetupActivity
  ├── SynologySetupActivity
  └── PreviewActivity              ← in-app screensaver preview

UpdateChecker                      ← checks version.json on GitHub Releases (Stable/Dev)
UpdateInstaller                    ← downloads APK via OkHttp, installs via FileProvider
```

Images are loaded with [Glide](https://github.com/bumptech/glide) (OkHttp3 backend), scaled to display resolution, and rendered across two alternating `ImageView`s with configurable transition effects. EXIF orientation is applied for local and cached images via a custom `BitmapTransformation`; remote JPEG images are handled by Glide's built-in `Downsampler`. The visualizer is a Kotlin/OpenGL port of [psysuals](https://github.com/Whichcraft/psysuals).

## Privacy

Credentials (OAuth tokens, API keys, passwords) are stored in Android SharedPreferences (on-device only). No data is sent to any third party except:
- The cloud service you configure (Google, Microsoft, Dropbox, your Immich/Nextcloud/Synology server)
- OpenWeatherMap (if the weather widget is enabled)
- GitHub Releases (to check for updates — only a version manifest is fetched; no identifying information is sent)

## License

All Rights Reserved — see [LICENSE](LICENSE).
