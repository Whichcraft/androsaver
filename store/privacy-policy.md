# Privacy Policy — AndroSaver

*Last updated: September 23, 2026*

## Overview

AndroSaver is an Android TV screensaver app that displays local or configured cloud/NAS photos, static images, and audio-reactive visualizations. This policy explains what data the app handles and how.

## Data We Collect

AndroSaver does not collect, transmit, or store personal data on developer-controlled servers. Data remains on your device or travels directly between your device and the services you configure (Google, Microsoft, Dropbox, Immich, Nextcloud, Synology, or OpenWeatherMap). Standard APK builds also contact GitHub Releases for update checks; the Play Store build receives updates through Google Play.

### Google Drive
- You may optionally connect a Google account using OAuth 2.0.
- Your OAuth credentials (client ID, client secret, and access token) are stored locally using Android's EncryptedSharedPreferences; sensitive operations fail closed if encryption is unavailable.
- These credentials are used solely to fetch photos from your Google Drive and are never sent to any server other than Google's.

### Synology NAS
- You may optionally enter your NAS address, username, and password.
- These credentials are stored locally using Android's EncryptedSharedPreferences.
- They are used solely to fetch photos from your NAS and are never sent anywhere else.

### Photos
- Photos are streamed from your configured sources and displayed on screen.
- Up to 200 images / 150 MiB may be cached locally for offline fallback, and a selected static image is copied into app-private storage.
- Photos are never uploaded to the developer or shared by AndroSaver.

### Visualizer audio
- `RECORD_AUDIO` is used by Android's `Visualizer` API while visualizer mode runs.
- FFT and waveform data are processed in memory on-device and are never recorded or transmitted.

### Weather and updates
- If enabled, the entered city and weather API key are sent to OpenWeatherMap for current conditions.
- Standard APK builds check GitHub Releases for a version manifest and download an APK only after the user accepts an update. The Play Store build receives updates through Google Play.

## Data We Do Not Collect

- We do not collect analytics or usage data.
- We do not serve ads.
- We do not share any data with the developer, advertisers, or analytics providers. The app does make direct service requests to providers you configure.
- We do not use any third-party SDKs that collect data.

## Contact

If you have questions about this privacy policy, please open an issue at:
https://github.com/Whichcraft/androsaver/issues
