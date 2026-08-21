# Image Sources Reference

All sources implement `ImageSource` (`com.androsaver.source`):
```kotlin
interface ImageSource {
    suspend fun getImageUrls(): List<ImageItem>
}
```

`ImageItem` carries: `url: String`, `name: String`, `headers: Map<String, String>`, a stable non-secret `stableId` used for cache/slideshow identity, and optional `insecureEndpoint` transport metadata. The latter is only emitted for an explicitly opted-in self-hosted provider and is matched against the exact scheme, host, and port before the trust-all client can be used. Temporary fetch URLs and headers remain in memory only.
Glide reads and applies embedded EXIF orientation while decoding.

Enabled providers are constructed by `ImageSourceRegistry`, shared by the
slideshow, Static Image browser, and prefetch worker. The slideshow may use
bundled defaults when no provider is enabled; the browser instead shows an
actionable empty state. Provider enumeration uses the shared cancellable
OkHttp bridge and a 60-second caller timeout.

Sources are queried concurrently by `ScreensaverEngine`; results are merged and shuffled. All active sources run simultaneously.

---

## GoogleDriveSource

- **File:** `com.androsaver.source.GoogleDriveSource`
- **API:** Google Drive REST API v3 (`files.list` with `mimeType = image/*`)
- **Auth:** OAuth 2.0 device-auth flow (no Google Play Services); tokens stored encrypted via `GoogleAuthManager`
- **Setup:** Client ID + Client Secret + Folder ID → `GoogleDriveSetupActivity` → `GoogleAuthActivity`
- **Token refresh:** Refresh token is exchanged before each source listing; concurrent refreshes are serialized.
- **Prefs keys:** `Prefs.GOOGLE_ACCESS_TOKEN`, `Prefs.GOOGLE_REFRESH_TOKEN`, `Prefs.GOOGLE_FOLDER_ID`
- **Pagination Limit:** Maximum 2,000 files returned, 40 pages, or 20,000 entries scanned (whichever comes first); response bodies are bounded.

## OneDriveSource

- **File:** `com.androsaver.source.OneDriveSource`
- **API:** Microsoft Graph API (`/me/drive/items/{id}/children`)
- **Auth:** Azure device-auth flow; `OneDriveAuthManager` handles refresh
- **Setup:** Client ID + Folder path → `OneDriveSetupActivity` → `OneDriveAuthActivity`
- **Prefs keys:** `Prefs.ONEDRIVE_ACCESS_TOKEN`, `Prefs.ONEDRIVE_REFRESH_TOKEN`, `Prefs.ONEDRIVE_FOLDER`
- **Pagination Limit:** Maximum 2,000 files returned, 40 pages, or 20,000 entries scanned; response bodies are bounded.

## DropboxSource

- **File:** `com.androsaver.source.DropboxSource`
- **API:** Dropbox API v2 (`/files/list_folder` + download URLs)
- **Auth:** OAuth code flow; App Key + App Secret required; auto-refresh via `DropboxAuthManager`
- **Setup:** App Key + App Secret + Folder path → `DropboxSetupActivity` → `DropboxAuthActivity`
- **Prefs keys:** `Prefs.DROPBOX_ACCESS_TOKEN`, `Prefs.DROPBOX_REFRESH_TOKEN`, `Prefs.DROPBOX_APP_KEY`, `Prefs.DROPBOX_APP_SECRET`, `Prefs.DROPBOX_FOLDER`
- **Concurrency Throttle:** Temporary link fetches throttled via Semaphore to maximum 10 concurrent requests to prevent connection pool exhaustion.
- **Pagination Limit:** Maximum 2,000 files returned, 100 pages, or 20,000 entries scanned; repeated cursors stop enumeration.

## ImmichSource

- **File:** `com.androsaver.source.ImmichSource`
- **API:** Immich REST API (`/api/assets` with optional album filter)
- **Auth:** API key in `x-api-key` header (no OAuth)
- **Setup:** Host + Port + HTTPS toggle + API key + optional Album ID → `ImmichSetupActivity`
- **Prefs keys:** `Prefs.IMMICH_HOST`, `Prefs.IMMICH_PORT`, `Prefs.IMMICH_USE_HTTPS`, `Prefs.IMMICH_API_KEY`, `Prefs.IMMICH_ALBUM_ID`
- **Pagination Limit:** Maximum 2,000 files returned, 40 pages, or 20,000 entries scanned; response bodies are bounded.

## NextcloudSource

- **File:** `com.androsaver.source.NextcloudSource`
- **API:** WebDAV PROPFIND on configured folder path
- **Auth:** Basic auth with app password. HTTPS and certificate/hostname validation are enabled by default; HTTP or self-signed certificates require the provider's explicit unsafe option.
- **Setup:** Host + Port + HTTPS toggle + Username + App Password + Folder path → `NextcloudSetupActivity`
- **Prefs keys:** `Prefs.NEXTCLOUD_HOST`, `Prefs.NEXTCLOUD_USERNAME`, `Prefs.NEXTCLOUD_PASSWORD`, `Prefs.NEXTCLOUD_FOLDER`
- **Safety:** WebDAV response URLs must resolve to the configured origin before credentials are attached; folder and username components are encoded as path segments. The response body is bounded to prevent oversized XML exhaustion.

## SynologySource

- **File:** `com.androsaver.source.SynologySource`
- **API:** Synology DSM FileStation REST API
- **Auth:** Username/password POST → session SID; the SID is used only in memory for the active listing/download URLs
- **Setup:** Host + Port + HTTPS + Username + Password + Folder → `SynologySetupActivity`
- **Prefs keys:** `Prefs.SYNOLOGY_HOST`, `Prefs.SYNOLOGY_PORT`, `Prefs.SYNOLOGY_USE_HTTPS`, `Prefs.SYNOLOGY_USERNAME`, `Prefs.SYNOLOGY_PASSWORD`, `Prefs.SYNOLOGY_FOLDER`
- **Pagination Limit:** Maximum 2,000 files returned, 100 pages, or 20,000 entries scanned; response bodies are bounded.

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
- **Prefs keys:** `Prefs.ENABLE_LOCAL_STORAGE`

---

## ImageCache

- **File:** `com.androsaver.ImageCache`
- Stores downloaded images on-disk: ≤ 200 images / ≤ 150 MB
- Used automatically as fallback when all sources fail (network unavailable)
- Preserves original image bytes, including embedded EXIF metadata
- Serializes manifest updates and writes the manifest through a temporary file to avoid partial-cache corruption.
- The manifest stores only the stable item key, local filename, source name,
  timestamp, and byte size; it never stores remote fetch URLs or auth query
  parameters. Downloads are bounded, image-validated, cancellation-aware, and
  published atomically.

---

## Adding a New Source

1. Create `com.androsaver.source.MySource.kt` implementing `ImageSource`
2. Add credential/config constants to `Prefs.kt`
3. Create a setup activity (e.g. `MySourceSetupActivity.kt`) for credential entry
4. Register the source in `ImageSourceRegistry` (the shared factory for slideshow, browser, and prefetch)
5. Add an enable toggle to `res/xml/sources_preferences.xml` (key = `Prefs.ENABLE_MY_SOURCE`)
6. Add a setup entry Preference that launches `MySourceSetupActivity`
7. Add all UI strings to `res/values/strings.xml`
8. Add source name to `res/values/arrays.xml` if needed
9. Declare `MySourceSetupActivity` in `AndroidManifest.xml`
10. Update `docs/image-sources.md` with auth pattern and Prefs keys
