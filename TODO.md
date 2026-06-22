# TODO — AndroSaver Bug & Code Review Tracker

This document tracks bugs, memory leaks, concurrency issues, and performance bottlenecks discovered during a deep code review of the Kotlin codebase.

---

## 1. High Priority (Thread Safety & Concurrency)

### ImageCache Concurrent Manifest Write Corruption (Unsynchronized Read-Modify-Write)
- **File:** [ImageCache.kt](file:///home/tom/github.com/androsaver/app/src/main/java/com/androsaver/ImageCache.kt)
- **Description:** Although `readManifest()` and `writeManifest()` are thread-synchronized internally, the actual `saveImages()` function performs a non-atomic read-modify-write cycle. It reads the manifest, executes network requests to download up to 200 images, updates the in-memory list, and writes the manifest back. If multiple download or sync tasks execute concurrently (e.g., background prefetch worker runs while the foreground engine is caching photos), they will read the same stale manifest, and the later writer will overwrite and delete the cache entries recorded by the earlier writer.
- **Fix:** Synchronize the entire download and manifest update sequence in `saveImages()` using a lock or mutex.
- **Status:** PENDING.

### Coroutine Scope Silent Failure / Cancellation Bug in ScreensaverService
- **File:** [ScreensaverService.kt](file:///home/tom/github.com/androsaver/app/src/main/java/com/androsaver/ScreensaverService.kt)
- **Description:** The `CoroutineScope` used by `ScreensaverEngine` is defined as a class property of `ScreensaverService` and cancelled in `onDetachedFromWindow()`. If the service is not destroyed by the OS but the screensaver is previewed, dismissed, and restarted (or detached/attached multiple times), the `CoroutineScope` remains cancelled permanently. As a result, subsequent slideshow download and weather fetch coroutines fail to run silently.
- **Fix:** Re-initialize the `CoroutineScope` inside `onAttachedToWindow()`, or cancel it only in `onDestroy()`.
- **Status:** PENDING.

---

## 2. Medium Priority (Performance & Compatibility)

### Plaintext Storage of Sensitive Credentials & Enabled Backup
- **File:** [Prefs.kt](file:///home/tom/github.com/androsaver/app/src/main/java/com/androsaver/Prefs.kt), [GoogleAuthManager.kt](file:///home/tom/github.com/androsaver/app/src/main/java/com/androsaver/auth/GoogleAuthManager.kt), [DropboxAuthManager.kt](file:///home/tom/github.com/androsaver/app/src/main/java/com/androsaver/auth/DropboxAuthManager.kt), [OneDriveAuthManager.kt](file:///home/tom/github.com/androsaver/auth/OneDriveAuthManager.kt), [AndroidManifest.xml](file:///home/tom/github.com/androsaver/app/src/main/AndroidManifest.xml)
- **Description:** Sensitive user keys and configuration values (e.g. OAuth access/refresh tokens, client secrets, passwords for Nextcloud/Synology, OWM API keys) are stored in standard plaintext `SharedPreferences` instead of `EncryptedSharedPreferences`. Since `android:allowBackup="true"` is enabled in the manifest, this exposes sensitive user credentials to backups and unauthorized recovery.
- **Fix:** Migrate credential keys to `EncryptedSharedPreferences` and set `android:allowBackup="false"` in the manifest.
- **Status:** PENDING.

### Unbounded Concurrency in Dropbox Temporary Link Fetching (Connection Pool Exhaustion)
- **File:** [DropboxSource.kt](file:///home/tom/github.com/androsaver/app/src/main/java/com/androsaver/source/DropboxSource.kt)
- **Description:** In `fetchTempLinks`, the Dropbox source launches asynchronous coroutines to fetch a temporary download link for every single image file concurrently. If a folder contains thousands of images (e.g. 2,000+ files), this launches thousands of simultaneous OkHttp connections. This exhausts the device's connection pool, leads to socket failures/timeouts, triggers API rate-limiting, and causes visualizer/slideshow load failures or crashes.
- **Fix:** Throttle the concurrent temporary link fetch requests using a `Semaphore` (e.g., maximum 10 concurrent requests).
- **Status:** PENDING.

### Unbounded Pagination in REST/WebDAV Sources (OutOfMemory Risk)
- **File:** [ImmichSource.kt](file:///home/tom/github.com/androsaver/app/src/main/java/com/androsaver/source/ImmichSource.kt), [GoogleDriveSource.kt](file:///home/tom/github.com/androsaver/app/src/main/java/com/androsaver/source/GoogleDriveSource.kt), [OneDriveSource.kt](file:///home/tom/github.com/androsaver/app/src/main/java/com/androsaver/source/OneDriveSource.kt)
- **Description:** Cloud/self-hosted source providers loop page-by-page fetching all remote assets recursively without any limit. For libraries containing tens of thousands of pictures, retrieving page tokens/data and mapping them into memory as `ImageItem` instances triggers high network traffic, API rate-limiting, and `OutOfMemoryError` crashes on low-end Android TV devices.
- **Fix:** Cap the maximum number of items requested (e.g., maximum 2,000 items) and exit the page-fetching loop once the limit is reached.
- **Status:** PENDING.

### Sequential Blocking Network Pre-Fetching in ImagePrefetchWorker
- **File:** [ImagePrefetchWorker.kt](file:///home/tom/github.com/androsaver/app/src/main/java/com/androsaver/ImagePrefetchWorker.kt)
- **Description:** Unlike `ScreensaverEngine.loadImages()` which was optimized to parallelize queries across configured image sources, `ImagePrefetchWorker.doWork()` queries all configured sources sequentially. If a user has multiple sources or if one of the NAS/cloud servers is offline or slow, each sequential query can block for up to 60 seconds before timing out, delaying the entire sync process and potentially exceeding WorkManager's execution limits or draining TV resources.
- **Fix:** Query configured image sources concurrently in `ImagePrefetchWorker` using `async` and `awaitAll`.
- **Status:** PENDING.

---

## 3. Low Priority (Maintenance & Robustness)

### [PARTIALLY RESOLVED] High GC Pressure in GLDraw.polygon() and Mode Rendering Loops
- **File:** [GLDraw.kt](file:///home/tom/github.com/androsaver/app/src/main/java/com/androsaver/visualizer/GLDraw.kt), [CubeMode.kt](file:///home/tom/github.com/androsaver/app/src/main/java/com/androsaver/visualizer/modes/CubeMode.kt), [SlimeMoldMode.kt](file:///home/tom/github.com/androsaver/app/src/main/java/com/androsaver/visualizer/modes/SlimeMoldMode.kt), [CliffordMode.kt](file:///home/tom/github.com/androsaver/app/src/main/java/com/androsaver/visualizer/modes/CliffordMode.kt), [FlowFieldMode.kt](file:///home/tom/github.com/androsaver/app/src/main/java/com/androsaver/visualizer/modes/FlowFieldMode.kt), [TriFluxMode.kt](file:///home/tom/github.com/androsaver/app/src/main/java/com/androsaver/visualizer/modes/TriFluxMode.kt), [MyceliumMode.kt](file:///home/tom/github.com/androsaver/app/src/main/java/com/androsaver/visualizer/modes/MyceliumMode.kt), [ButterfliesMode.kt](file:///home/tom/github.com/androsaver/app/src/main/java/com/androsaver/visualizer/modes/ButterfliesMode.kt)
- **Description:** Hot rendering path methods execute at 60 FPS but perform frequent allocations:
  1. `GLDraw.polygon()` filters the coordinates array and computes the average via Kotlin list extension methods `pts.filterIndexed { i, _ -> i % 2 == 0 }.average()`.
  2. `GLDraw.hsl()` is called thousands of times inside nested rendering loops, returning a new `FloatArray(4)` on every call.
  3. `TriFluxMode.screenVerts()` allocates a new `FloatArray(6)` for each of the 500+ tiles every frame. It also performs multiple `ArrayList` allocations (`visibleIndices`, `interiorIndices`, `finished` index list) and filtering operations per frame or beat swap.
  4. `CubeMode.rotateVertex()`, `project()`, and `projectSat()` allocate new `FloatArray` and `Pair` instances for every vertex projection/rotation in the main and satellite loops (hundreds of allocations/frame).
  5. `MyceliumMode.draw()` allocates temporary `ArrayList` collections and instantiates short-lived data classes (`Tip`, `Spore`, `Seg`) in grow loops.
  6. `ButterfliesMode` allocates coordinate float arrays and `Pair` coordinate objects inside the update/draw loops.
  This triggers massive garbage collection overhead and micro-stuttering/jank on low-end Android TV sticks.
- **Fix:** Optimize loops to be allocation-free: replace collection operations with simple index loops, reuse float arrays, pass pre-allocated output array references to `hsl` and rotation helpers, pre-allocate/reuse lists, and use back-buffer reference swapping.
- **Status:** PARTIALLY RESOLVED. Replaced polygon average calculations with index-based loops to eliminate Kotlin list/iterator allocations. Implemented an overloaded non-allocating companion helper `GLDraw.hsl(h,s,l,a,out)` and optimized the default helper to bypass `Triple` allocation. Mode-specific rendering loop allocations remain pending.

### Incomplete Image Source Setup UX Summaries
- **File:** [SettingsActivity.kt](file:///home/tom/github.com/androsaver/app/src/main/java/com/androsaver/SettingsActivity.kt)
- **Description:** `SourcesFragment.onResume()` updates authorized/configured status summaries for Google Drive, OneDrive, Dropbox, and Immich, but completely ignores Nextcloud and Synology NAS sources. As a result, Nextcloud and Synology setup summaries permanently display static fallback texts ("Configure host, credentials, and folder") even after the user has successfully configured them.
- **Fix:** Add dynamic status checks `updateNextcloudStatus()` and `updateSynologyStatus()` to `SourcesFragment` and invoke them in `onResume()`.
- **Status:** PENDING.
