# TODO — AndroSaver Bug & Code Review Tracker

All identified bugs, memory leaks, concurrency issues, and performance bottlenecks from the deep code review have been fully resolved.

## Pending Tasks

- [ ] Replace the bundled slideshow example images with a curated set of public-domain photos before release.
- [x] Add a screensaver menu option to select a static background image and configure its rendering behavior. Offer the useful display modes **Fill/Crop** (preserve aspect ratio and crop overflow), **Fit/Letterbox** (show the complete image with unused space), and **Original Size/Center** (preserve pixels and center smaller images without upscaling); treat **Stretch** (may distort the image) as an optional explicitly labelled advanced mode rather than the default. Include a menu setting for the letterbox/background color, and ensure all modes behave consistently for portrait and landscape images/displays, including rotation and mismatched aspect ratios.

---

## Resolved Tasks (v2.5.27 & prior)

- [x] **`ImageCache` Concurrent Manifest Write Corruption**: Fixed with global companion-level `Mutex` synchronization inside `saveImages()`.
- [x] **ScreensaverService Coroutine Scope silent failure**: Fixed by dynamically creating/nullifying `CoroutineScope` during window attachment/detachment.
- [x] **Dropbox Source Connection Pool Exhaustion**: Fixed using a `Semaphore(10)` to limit simultaneous temporary link HTTP requests.
- [x] **Incomplete Image Source UX summaries**: Configured Nextcloud and Synology NAS sources to correctly display status summaries on settings screen resume.
- [x] **Plaintext Storage of Sensitive Credentials & Enabled Backup**: Migrated OAuth tokens, NAS credentials, and OWM API keys to `EncryptedSharedPreferences` via `SecurePreferences`, and disabled device backup in `AndroidManifest.xml`.
- [x] **Unbounded Pagination in REST/WebDAV Sources**: Capped maximum items fetched to 2,000 in `ImmichSource`, `GoogleDriveSource`, and `OneDriveSource`.
- [x] **Sequential Blocking Network Pre-Fetching**: Configured `ImagePrefetchWorker` to fetch sources concurrently using `async`/`awaitAll`.
- [x] **High GC Pressure in rendering loops**: Optimized GLDraw polygon calculations to be trig-free/iterator-free, and refactored `CubeMode` rendering loop to be completely allocation-free (eliminating `FloatArray` and `Pair` instances creation per frame).
