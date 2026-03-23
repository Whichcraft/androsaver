# AndroSaver

An Android TV screensaver app for the Huawei TV Stick (and any Android TV device) that fetches photos from **Google Drive** or a **Synology NAS** and displays them as a fullscreen slideshow.

## Features

- **Google Drive source** — streams photos from a Drive folder using OAuth 2.0 device flow (no Google Play Services required)
- **Synology NAS source** — streams photos from any FileStation folder via the Synology DSM REST API
- Both sources can be active simultaneously; images are shuffled together
- Six transition effects: **Crossfade**, **Fade to Black**, **Slide Left**, **Slide Right**, **Zoom In**, **Zoom Out**, plus a **Random** mode
- Configurable time per image (5 s – 30 min)
- Configurable transition speed (1 – 5 seconds)
- Registered as a system Dream Service — appears in Android TV's screensaver settings

## Installation

The easiest way to install on an Amazon Fire TV or Android TV device is via the [Downloader app](https://www.aftvnews.com/downloader/):

| Release | Downloader code | Short URL |
|---------|----------------|-----------|
| v1.0    | `4414059`      | [aftv.news/4414059](https://aftv.news/4414059) |
| Beta    | `6643418`      | [aftv.news/6643418](https://aftv.news/6643418) |

1. Install **Downloader** from the Amazon Appstore or Google Play.
2. Open Downloader and enter the code (or short URL) for the version you want.
3. Follow the on-screen prompts to install the APK.

## Requirements

- Android 5.0+ (API 21)
- Android TV device (tested on Huawei TV Stick)
- Android Studio Hedgehog or later (to build from source)

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

### Activating the Screensaver

- **Android TV:** Settings → Device Preferences → Screen saver → Select **AndroSaver**
- The screensaver activates automatically when the TV is idle.

## Settings Reference

All options are configured in the **AndroSaver Settings** app.

### Image Sources

| Setting | Description |
|---------|-------------|
| **Google Drive** toggle | Enable/disable fetching images from Google Drive |
| **Google Drive Setup** | Configure OAuth credentials and folder |
| **Synology NAS** toggle | Enable/disable fetching images from a Synology NAS |
| **Synology NAS Setup** | Configure host, credentials, and folder path |

Both sources can be enabled at the same time — images from all sources are merged and shuffled.

### Slideshow

| Setting | Options | Default | Description |
|---------|---------|---------|-------------|
| **Time per Image** | 5 s, 10 s, 15 s, 30 s, 1 min, 2 min, 5 min, 10 min, 15 min, 20 min, 30 min | 10 s | How long each image is displayed |
| **Transition Speed** | 1 s, 2 s, 3 s, 4 s, 5 s | 1.5 s | Duration of the animation between images |
| **Transition Effect** | Crossfade, Fade to Black, Slide Left, Slide Right, Zoom In, Zoom Out, Random | Crossfade | Animation style used between images |

#### Transition Effects

| Effect | Description |
|--------|-------------|
| **Crossfade** | Old image fades out while new image fades in simultaneously |
| **Fade to Black** | Old image fades to black, then new image fades in |
| **Slide Left** | New image slides in from the right, old slides out to the left |
| **Slide Right** | New image slides in from the left, old slides out to the right |
| **Zoom In** | New image scales up from slightly smaller while fading in |
| **Zoom Out** | Old image scales up and fades out as new image fades in |
| **Random** | A different effect is picked at random for each transition |

## Architecture

```
ScreensaverService (DreamService)
  ├── GoogleDriveSource    ← Drive REST API v3 + token refresh
  └── SynologySource       ← Synology DSM FileStation API

SettingsActivity (PreferenceFragment)
  ├── GoogleDriveSetupActivity → GoogleAuthActivity (device flow)
  └── SynologySetupActivity
```

Images are loaded with [Glide](https://github.com/bumptech/glide) (OkHttp3 backend), scaled to display resolution, and rendered across two alternating `ImageView`s with configurable transition effects.

## Privacy

Credentials are stored in Android SharedPreferences (on-device only). No data is sent anywhere except to Google and/or your local NAS.

## License

MIT
