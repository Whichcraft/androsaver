# Play Store Listing — AndroSaver

## App title (≤30 chars)
```
AndroSaver
```

## Short description (≤80 chars)
```
Photo slideshow & music visualizer screensaver for Android TV.
```

## Full description (≤4000 chars)
```
Turn your Android TV into a beautiful photo frame or a live music visualizer.

AndroSaver is a screensaver with four modes — photo slideshow, static image, music visualizer, and blank screen — that activates automatically whenever your TV goes idle.

PHOTO SLIDESHOW
Stream your personal photos from any combination of cloud services and local storage, displayed as a fullscreen slideshow with cinematic transitions.

• Google Drive — secure OAuth 2.0 device flow; no Google Play Services required, works on Huawei and GMS-free devices.
• OneDrive — Microsoft OAuth 2.0 device flow; personal and work/school accounts.
• Dropbox — OAuth 2.0 authorization; tokens refresh automatically.
• Immich — self-hosted Immich server via REST API; optional album filter.
• Nextcloud — any folder via WebDAV; app passwords and self-signed certs supported.
• Synology NAS — DSM FileStation API; session re-authenticated automatically.
• Device Photos — photos stored on the TV itself via MediaStore.
• All sources can be active at once — images are merged and shuffled.
• Offline cache — up to 200 images stored locally as a fallback.
• Ken Burns effect — slow pan and zoom on each photo.
• Six transition effects: Crossfade, Fade to Black, Slide Left/Right, Zoom In/Out, Random.
• Remote control: press ← / → to jump to the previous or next photo.

MUSIC VISUALIZER
Thirty-four real-time OpenGL ES 2.0 effects react to whatever is playing on the TV — music, games, or movies.

• Yantra — sacred-geometry mandala with beat-driven ring pulses
• Cube — dual wireframe cubes with spectrum colour cycling
• Plasma — full-screen GPU sine-interference field
• Tunnel — first-person neon tube ride with beat-spawned triangles
• Lissajous — 3D trefoil knot with neon trail glow
• Nova — waveform kaleidoscope with 7-fold symmetry
• Spiral — neon helix vortex with audio-reactive arms
• Bubbles — translucent rising bubbles driven by bass energy
• Spectrum — log-spaced equalizer bars with peak markers
• Waterfall — scrolling time-frequency spectrogram
• Aurora, Lattice, Mycelium, Magnetar, SlimeMold, Mobius, Chromatic, Persistence, Synapse, Heartbeat, Morphogenesis, Hyperbolic, LiquidLight, Cymatica, Phason, Tesseract, Ferrofluid, Mandelbox, and more

Remote control while visualizer is running:
• ← / → — switch between effects
• ↑ / ↓ — adjust how strongly visuals react to the beat (5 levels)

Music Genre hint — tune beat detection to Auto-detect, Electronic, Rock, Classical, or Any. Auto-detect analyzes the spectrum every 30 seconds and can choose a matching visualizer family when effect cycling is On.

DISPLAY OVERLAYS (all non-blank modes)
• Clock — time and date shown in the corner
• Weather — current temperature from OpenWeatherMap

OTHER FEATURES
• Schedule — restrict the screensaver to an active time window (e.g. 08:00–22:00)
• Preview mode — test the screensaver instantly from Settings

PRIVACY FIRST
Credentials are stored on-device with Android encrypted preferences. Photos may be cached locally for offline fallback; nothing is uploaded to the developer. Network traffic goes only to configured providers and OpenWeatherMap if enabled. Standard APK builds also use GitHub Releases for update checks; the Play Store build receives updates through Google Play.

SETUP
AndroSaver registers as a system Dream Service and appears directly in your Android TV screensaver settings (Settings → Device Preferences → Screen saver).

Supports Android 5.0+ and any Android TV device.
```

## Content rating
- No violence, no user-generated content, no ads, no purchases
- Expected rating: **Everyone**

## Data safety disclosures
| Data type                  | Collected | Shared | Notes                                                      |
|----------------------------|-----------|--------|------------------------------------------------------------|
| Google OAuth token         | Yes       | No     | Stored on-device, sent only to Google                      |
| Microsoft OAuth token      | Yes       | No     | Stored on-device, sent only to Microsoft                   |
| Dropbox OAuth token        | Yes       | No     | Stored on-device, sent only to Dropbox                     |
| Immich API key             | Yes       | No     | Stored on-device, sent only to your Immich server          |
| Nextcloud credentials      | Yes       | No     | Stored on-device, sent only to your Nextcloud server       |
| Synology NAS credentials   | Yes       | No     | Stored on-device, sent only to your Synology NAS           |
| Photos/images              | No        | No     | Displayed and optionally cached locally; never uploaded or stored off-device |

## Category
- Primary: **Personalization**
- Secondary tag: Android TV / Screensaver

## Privacy policy
Needs a public URL. Suggested text in `privacy-policy.md`.
