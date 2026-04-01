# Google Play Store — Console Checklist

All sections required to publish AndroSaver on Google Play, with the correct answers and copy-paste text.

---

## 1. Datenschutzerklärung (Privacy Policy)

**What to enter:** URL of the privacy policy.

Host the rendered version of `docs/privacy-policy.md` at a stable public URL, for example via GitHub Pages or a raw GitHub link:

```
https://github.com/Whichcraft/androsaver/blob/master/docs/privacy-policy.md
```

or, if GitHub Pages is set up:

```
https://whichcraft.github.io/androsaver/privacy-policy
```

The privacy policy covers: local-only credential storage, RECORD_AUDIO (on-device FFT only), photo caching, OpenWeatherMap weather call, GitHub update check, no ads, no analytics.

---

## 2. App-Zugriff (App Access)

> Explain how Google reviewers can access all features of your app.

**Select:** "All functionality is available without special access"

**Notes for review instructions (paste into the field):**

```
AndroSaver is an Android TV screensaver. To test it:

1. Install the APK on an Android TV or Fire TV device (or emulator with API 21+).
2. Open the "AndroSaver Settings" app that appears in the launcher.
3. Tap "Preview" on the main screen to start the screensaver immediately — no idle wait required.
4. To activate as the system screensaver: Settings → Device Preferences → Screen Saver → select AndroSaver.

Photo Slideshow (optional — not required for review):
- Enable any image source toggle (Google Drive, OneDrive, Dropbox, Immich, Nextcloud, Synology, or Device Photos).
- Each source requires credentials from the respective service (reviewer's own account).

Music Visualizer:
- Set Screensaver Mode to "Music Visualizer" in Settings.
- Play audio on the device; the visualizer reacts to system audio.
- No account or login required for the visualizer.

No special test account is needed for core functionality.
```

---

## 3. Anzeigen (Ads)

**Does your app contain ads?** → **No**

AndroSaver contains no advertising of any kind. No ad SDK is included in the build.

---

## 4. Einstufung des Inhalts (Content Rating — IARC Questionnaire)

Answer the IARC questionnaire as follows:

| Question category | Answer |
|-------------------|--------|
| Violence | None |
| Sexual content | None |
| Profanity or crude humour | None |
| Controlled substances | None |
| Gambling or simulated gambling | None |
| User-generated content (UGC) | None — users configure private cloud accounts; no public sharing |
| Location sharing | No |
| User data sharing | No |

**Expected rating: Everyone (E) / USK 0 / PEGI 3**

The content rating is the same in all territories: this is a screensaver displaying user-selected photos and audio-reactive visualizations with no objectionable content.

---

## 5. Zielgruppe (Target Audience)

| Field | Value |
|-------|-------|
| Target age group | **18 and over** (primary) — the app is designed for Android TV home entertainment setups |
| Also appropriate for | 13–17 (no content concerns, but primary use case is adult home users) |
| Is this app primarily directed at children? | **No** |
| Does the app comply with Google Play's Families Policy? | No (not applicable — not targeted at children) |

> **Note:** Do not select any age group under 13. The app processes no children's data and is not designed for children.

---

## 6. Datensicherheit (Data Safety)

Complete the Data Safety form with the following answers.

### Does your app collect or share any of the required user data types?

**Yes** — but only on behalf of the user to connect to their own configured services. See details below.

### Data types — Collection

| Data type | Collected? | Notes |
|-----------|-----------|-------|
| Precise location | **No** | Weather city is a text string entered by user, not GPS |
| Approximate location | **No** | |
| Name | **No** | |
| Email address | **No** | |
| User IDs | **No** | |
| Phone number | **No** | |
| Race and ethnicity | **No** | |
| Political or religious beliefs | **No** | |
| Sexual orientation | **No** | |
| Other personal info | **No** | |
| Financial info | **No** | |
| Health and fitness | **No** | |
| Messages | **No** | |
| Photos / videos | **No** — displayed and locally cached only; never uploaded | |
| Audio files | **No** — FFT processed on-device only; never recorded | |
| Files / docs | **No** | |
| Calendar events | **No** | |
| Contacts | **No** | |
| App interactions | **No** | |
| In-app search history | **No** | |
| Web browsing history | **No** | |
| Crash logs | **No** | |
| Diagnostics | **No** | |
| Device IDs | **No** | |

### Data types — Sharing

No user data is shared with the developer or any third party. The app makes API calls to services the user has explicitly configured (Google Drive, OneDrive, Dropbox, Immich, Nextcloud, Synology, OpenWeatherMap) using only the credentials and settings the user has entered. Those are standard user-to-service calls, not developer data collection.

### Is all data encrypted in transit?

**Yes** — all API calls use HTTPS. Credentials at rest are stored in AES-256 `EncryptedSharedPreferences`.

### Can users request data deletion?

**Yes** — uninstalling the app deletes all locally stored data (credentials, cache, settings). Users can also clear individual credentials in Settings.

### Summary statement (paste into the "Privacy practices" free-text field if available):

```
AndroSaver stores your cloud credentials locally on your device using AES-256 encryption.
It does not collect, transmit, or share any personal data with the developer.
Audio is processed on-device only and is never recorded.
Photos are fetched from your own accounts and cached locally.
No ads. No analytics. No tracking.
```

---

## 7. Behörden-Apps (Government Apps)

**Is this a government app?** → **No**

AndroSaver is an independent open-source screensaver app developed by a private individual. It has no government affiliation.

---

## 8. Finanzfunktionen (Financial Features)

**Does your app offer any of the following?**

| Feature | Answer |
|---------|--------|
| In-app purchases | No |
| Subscriptions | No |
| Paid app | No (free) |
| Cryptocurrency features | No |
| Financial product promotion | No |
| Lending or credit features | No |

**Select:** "This app does not offer financial features."

No financial disclosures are required.

---

## Quick Reference

| Play Console section | Status | Action needed |
|---------------------|--------|---------------|
| Privacy Policy | ✅ Ready | Publish `docs/privacy-policy.md` at a public URL and paste it |
| App Access | ✅ Ready | "All functionality available"; paste test instructions above |
| Ads | ✅ Ready | Select "No ads" |
| Content Rating | ✅ Ready | Complete IARC questionnaire → Everyone / PEGI 3 |
| Target Audience | ✅ Ready | 18+, not for children |
| Data Safety | ✅ Ready | No data collected or shared; HTTPS + encryption; deletion via uninstall |
| Government Apps | ✅ Ready | Select "No" |
| Financial Features | ✅ Ready | Select "No financial features" |
