# Image Sources Reference

All sources implement `ImageSource` (`com.androsaver.source`):
```kotlin
interface ImageSource {
    suspend fun getImageUrls(): List<ImageItem>
}
```

`ImageItem` carries: `url: String`, `headers: Map<String, String>`, `exifOrientation: Int`

Sources are queried concurrently by `ScreensaverEngine`; results are merged and shuffled. All active sources run simultaneously.

---

## GoogleDriveSource

- **File:** `com.androsaver.source.GoogleDriveSource`
- **API:** Google Drive REST API v3 (`files.list` with `mimeType = image/*`)
- **Auth:** OAuth 2.0 device-auth flow (no Google Play Services); tokens stored encrypted via `GoogleAuthManager`
- **Setup:** Client ID + Client Secret + Folder ID → `GoogleDriveSetupActivity` → `GoogleAuthActivity`
- **Token refresh:** Automatic via refresh token on 401
- **Prefs keys:** `Prefs.GOOGLE_ACCESS_TOKEN`, `Prefs.GOOGLE_REFRESH_TOKEN`, `Prefs.GOOGLE_FOLDER_ID`

## OneDriveSource

- **File:** `com.androsaver.source.OneDriveSource`
- **API:** Microsoft Graph API (`/me/drive/items/{id}/children`)
- **Auth:** Azure device-auth flow; `OneDriveAuthManager` handles refresh
- **Setup:** Client ID + Folder path → `OneDriveSetupActivity` → `OneDriveAuthActivity`
- **Prefs keys:** `Prefs.ONEDRIVE_ACCESS_TOKEN`, `Prefs.ONEDRIVE_REFRESH_TOKEN`, `Prefs.ONEDRIVE_FOLDER_ID`

## DropboxSource

- **File:** `com.androsaver.source.DropboxSource`
- **API:** Dropbox API v2 (`/files/list_folder` + download URLs)
- **Auth:** OAuth code flow; App Key + App Secret required; auto-refresh via `DropboxAuthManager`
- **Setup:** App Key + App Secret + Folder path → `DropboxSetupActivity` → `DropboxAuthActivity`
- **Prefs keys:** `Prefs.DROPBOX_ACCESS_TOKEN`, `Prefs.DROPBOX_REFRESH_TOKEN`, `Prefs.DROPBOX_APP_KEY`, `Prefs.DROPBOX_APP_SECRET`, `Prefs.DROPBOX_FOLDER_PATH`

## ImmichSource

- **File:** `com.androsaver.source.ImmichSource`
- **API:** Immich REST API (`/api/assets` with optional album filter)
- **Auth:** API key in `x-api-key` header (no OAuth)
- **Setup:** Host + Port + HTTPS toggle + API key + optional Album ID → `ImmichSetupActivity`
- **Prefs keys:** `Prefs.IMMICH_HOST`, `Prefs.IMMICH_PORT`, `Prefs.IMMICH_HTTPS`, `Prefs.IMMICH_API_KEY`, `Prefs.IMMICH_ALBUM_ID`

## NextcloudSource

- **File:** `com.androsaver.source.NextcloudSource`
- **API:** WebDAV PROPFIND on configured folder path
- **Auth:** Basic auth with app password; accepts self-signed TLS certs (custom TrustManager in OkHttp)
- **Setup:** Host + Port + HTTPS toggle + Username + App Password + Folder path → `NextcloudSetupActivity`
- **Prefs keys:** `Prefs.NEXTCLOUD_HOST`, `Prefs.NEXTCLOUD_USERNAME`, `Prefs.NEXTCLOUD_PASSWORD`, `Prefs.NEXTCLOUD_FOLDER`

## SynologySource

- **File:** `com.androsaver.source.SynologySource`
- **API:** Synology DSM FileStation REST API
- **Auth:** Username/password → session SID cookie; **re-login every 25 minutes** (DSM sessions expire)
- **Setup:** Host + Port + HTTPS + Username + Password + Folder → `SynologySetupActivity`
- **Prefs keys:** `Prefs.SYNOLOGY_HOST`, `Prefs.SYNOLOGY_PORT`, `Prefs.SYNOLOGY_HTTPS`, `Prefs.SYNOLOGY_USERNAME`, `Prefs.SYNOLOGY_PASSWORD`, `Prefs.SYNOLOGY_FOLDER`

## DefaultImagesSource

- **File:** `com.androsaver.source.DefaultImagesSource`
- **Directory:** `app/src/main/assets/default_images/` (bundled in APK)
- **Auth:** None
- **Activation:** Automatic — added to the source list only when no other source is enabled
- **Supported formats:** JPEG, PNG, WebP, GIF, BMP
- **To add images:** drop files into `app/src/main/assets/default_images/` and commit; they are included in the next build

## LocalStorageSource

- **File:** `com.androsaver.source.LocalStorageSource`
- **API:** Android `MediaStore.Images` (up to 500 most recent photos)
- **Auth:** `READ_MEDIA_IMAGES` (API 33+) or `READ_EXTERNAL_STORAGE` (API < 33); permission prompted on enable
- **No setup activity** — enabled via toggle in Sources screen
- **Prefs keys:** `Prefs.LOCAL_STORAGE_ENABLED`

---

## ImageCache

- **File:** `com.androsaver.ImageCache`
- Stores downloaded images on-disk: ≤ 200 images / ≤ 300 MB
- Used automatically as fallback when all sources fail (network unavailable)
- EXIF orientation preserved in cache metadata

---

## Adding a New Source

1. Create `com.androsaver.source.MySource.kt` implementing `ImageSource`
2. Add credential/config constants to `Prefs.kt`
3. Create a setup activity (e.g. `MySourceSetupActivity.kt`) for credential entry
4. Register the source in `ScreensaverEngine` (add to the sources list)
5. Add an enable toggle to `res/xml/sources_preferences.xml` (key = `Prefs.ENABLE_MY_SOURCE`)
6. Add a setup entry Preference that launches `MySourceSetupActivity`
7. Add all UI strings to `res/values/strings.xml`
8. Add source name to `res/values/arrays.xml` if needed
9. Declare `MySourceSetupActivity` in `AndroidManifest.xml`
10. Update `docs/image-sources.md` with auth pattern and Prefs keys
