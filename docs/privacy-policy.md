# Privacy Policy — AndroSaver

_Last updated: 2026-07-29_

## 1. Overview

AndroSaver is an Android screensaver app that displays photo slideshows and music visualizations on Android TV and Fire TV devices. This policy explains what data the app uses, where it is stored, and what is shared with whom.

**Short version:** AndroSaver stores your credentials locally on your device and uses them only to connect to the services you configure. The developer never receives, stores, or processes any of your data.

---

## 2. Data Collected and Processed

### 2.1 Cloud Storage Credentials (Google Drive, OneDrive, Dropbox)

When Android encrypted storage is available, AndroSaver stores OAuth 2.0 access and refresh tokens locally using Android's `EncryptedSharedPreferences` (AES-256 encryption). If Android's encrypted storage cannot be initialized, the app falls back to its local preferences so configured sources remain usable. These tokens are:

- Used solely to fetch image files from your own account via the respective provider's API.
- Never transmitted to the app developer or any third party.
- Changeable or removable from the relevant source settings.

### 2.2 Self-Hosted Server Credentials (Immich, Nextcloud, Synology NAS)

Host address, port, username, password or API key are stored locally and, when Android encrypted storage is available, protected with `EncryptedSharedPreferences`. They are used solely to authenticate with the server you have configured and are never sent anywhere else.

### 2.3 Photos and Images

Photos are fetched from your configured sources, displayed on screen, and cached locally on the device (up to 200 images / 150 MB) as an offline fallback. Public cloud and update traffic use HTTPS. Self-hosted sources offer a user-controlled HTTP/HTTPS setting; choose HTTPS whenever your server supports it. The app developer never receives copies of your photos. Photos are never uploaded, analysed, or shared.

### 2.4 Microphone / Audio (RECORD_AUDIO Permission)

The music visualizer uses Android's `Visualizer` API to capture a real-time FFT spectrum of the audio currently playing on the device. This data is:

- Processed entirely on-device to drive the visual animations.
- Never recorded, stored, or transmitted.
- Only active while the screensaver is running in Visualizer mode.

The `RECORD_AUDIO` permission is required by Android to access the system `Visualizer` API. No voice data or audio content is captured.

### 2.5 Weather (Optional)

If you enable the weather widget, the city name you enter and your OpenWeatherMap API key are sent to the OpenWeatherMap API (`api.openweathermap.org`) to retrieve current conditions. The response (temperature and description) is cached on-device for 30 minutes. No location data (GPS) is used. OpenWeatherMap's privacy policy applies: https://openweathermap.org/privacy-policy

### 2.6 Software Update Check

Each time the Settings screen opens, the app downloads a small version manifest from the selected GitHub Release channel. No credentials, photos, or device-identifying application data are deliberately included in that request.

---

## 3. Data Sharing

AndroSaver does **not** share any user data with the developer or any advertising network. The only outbound network calls made by the app are:

| Destination | Purpose | User-controlled |
|-------------|---------|-----------------|
| Google Drive API | Fetch images from your Drive | Requires explicit setup + toggle |
| Microsoft Graph API (OneDrive) | Fetch images from your OneDrive | Requires explicit setup + toggle |
| Dropbox API | Fetch images from your Dropbox | Requires explicit setup + toggle |
| Your Immich server | Fetch images | Requires explicit setup + toggle |
| Your Nextcloud server | Fetch images | Requires explicit setup + toggle |
| Your Synology NAS | Fetch images | Requires explicit setup + toggle |
| OpenWeatherMap API | Current weather for entered city | Requires explicit setup + toggle |
| GitHub Releases | Check for app updates | Always, on Settings open |

---

## 4. Local Storage

All configuration data (credentials, settings, image cache) is stored locally on the device in app-private storage. It is not backed up to Google's cloud backup by default. It is deleted when the app is uninstalled.

---

## 5. Advertising

AndroSaver contains **no advertising** of any kind. No advertising SDK is included.

---

## 6. Analytics and Crash Reporting

AndroSaver collects **no analytics** and sends **no crash reports**. No analytics SDK is included.

---

## 7. Children's Privacy

AndroSaver is not directed at children under 13. It does not knowingly collect any information from children.

---

## 8. Changes to This Policy

If this policy changes materially, the updated version will be published in the app repository and the "Last updated" date above will be revised.

---

## 9. Contact

Questions about this privacy policy can be directed to the developer via the GitHub repository:
https://github.com/Whichcraft/androsaver
