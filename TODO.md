# TODO — AndroSaver Bug & Code Review Tracker

This document tracks bugs, memory leaks, concurrency issues, and performance bottlenecks discovered during a deep code review of the Kotlin codebase.

---

## 1. High Priority (Thread Safety & Concurrency)

### [RESOLVED] Concurrency Race Conditions in AudioEngine
- **File:** [AudioEngine.kt](file:///home/tom/github.com/androsaver/app/src/main/java/com/androsaver/visualizer/AudioEngine.kt)
- **Description:** Android Visualizer capture listener callbacks (`onWaveFormDataCapture` and `onFftDataCapture`) run on a background audio processing thread. They modify shared fields (`energySum`, `energyHistory`, `smoothFft`, `genreWeights`, `detectAccum`, `detectFrames`, `midAvg`, `trebleAvg`) inside `publish()` without synchronization. Concurrently, the main thread calls `applyGenreHint()`, `detectGenre()`, and `resetDetection()`, which read and reset these fields. This causes race conditions, corrupted buffers, and data race bugs.
- **Fix:** Synchronize all updates and reads of shared audio state fields using a private lock or `synchronized(this)` inside `publish()`, `applyGenreHint()`, `detectGenre()`, and `resetDetection()`.
- **Status:** FIXED. Added proper `synchronized(this)` locks to all four methods.

### [RESOLVED] DropboxSource Dispatcher Blocking (NetworkOnMainThreadException)
- **File:** [DropboxSource.kt](file:///home/tom/github.com/androsaver/app/src/main/java/com/androsaver/source/DropboxSource.kt)
- **Description:** `DropboxSource.getImageUrls()` makes blocking synchronous OkHttp network requests (`client.newCall(request).execute()`) without switching to `Dispatchers.IO`. Since `ScreensaverEngine` calls `getImageUrls()` from a coroutine launched on `Dispatchers.Main`, this blocks the main thread, resulting in a `NetworkOnMainThreadException` crash or rendering freeze.
- **Fix:** Wrap the implementation of `getImageUrls()` inside `withContext(Dispatchers.IO)`.
- **Status:** FIXED. Wrapped body of `getImageUrls` in `withContext(Dispatchers.IO)`.

### [RESOLVED] ScreensaverService missing onDreamingStarted/onDreamingStopped Lifecycle Handling
- **File:** [ScreensaverService.kt](file:///home/tom/github.com/androsaver/app/src/main/java/com/androsaver/ScreensaverService.kt)
- **Description:** `ScreensaverService` starts visualizer rendering and audio capture in `onAttachedToWindow()`, and stops them in `onDetachedFromWindow()`. However, it fails to handle `onDreamingStarted()` and `onDreamingStopped()`. On many Android TV and Fire TV devices, the screensaver's window remains attached even when the TV screen is turned off or system enters sleep. Because the rendering loop and audio engine are not paused, they continue to run in the background, wasting CPU/GPU resources and power.
- **Fix:** Move starting/resuming to `onDreamingStarted()` and pausing/stopping to `onDreamingStopped()`.
- **Status:** FIXED. Overrode onDreamingStarted() and onDreamingStopped() to resume/pause the visualizer engine.

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

### [RESOLVED] ImageCache Memory Exhaustion (OOM Crashes)
- **File:** [ImageCache.kt](file:///home/tom/github.com/androsaver/app/src/main/java/com/androsaver/ImageCache.kt)
- **Description:** `saveImages()` reads the entire file response into JVM memory using `response.body?.bytes()`. On memory-constrained Android TV hardware (such as Fire TV or Huawei TV sticks), downloading multiple large, high-resolution photographs directly as byte arrays can easily cause heap exhaustion and `OutOfMemoryError` crashes.
- **Fix:** Stream the response body directly to the local cache storage path rather than loading the entire byte array in memory:
  ```kotlin
  response.body?.byteStream()?.use { input ->
      File(dir, fname).outputStream().use { output ->
          input.copyTo(output)
      }
  }
  ```
- **Status:** FIXED. Refactored `saveImages` to stream body to cache file using input stream copying.

### [RESOLVED] Waveform and FFT Packet Desynchronization (Stale Data)
- **File:** [AudioEngine.kt](file:///home/tom/github.com/androsaver/app/src/main/java/com/androsaver/visualizer/AudioEngine.kt)
- **Description:** In `publish()`, `lastWave` and `lastFft` are consumed but never cleared (set back to `null`). As a result, subsequent callbacks pair new waveforms with stale/old FFT magnitudes, or vice versa, causing analysis desynchronization on every capture packet.
- **Fix:** Atomically retrieve and clear/nullify both variables upon consumption so that each wave and FFT data block is processed only once as a synchronized pair.
- **Status:** FIXED. `lastWave` and `lastFft` are nullified inside the synchronized block upon consumption.

### [RESOLVED] UpdateInstaller Deprecated Intent Compatibility
- **File:** [UpdateInstaller.kt](file:///home/tom/github.com/androsaver/app/src/main/java/com/androsaver/UpdateInstaller.kt)
- **Description:** Launching the APK installer uses `Intent(Intent.ACTION_INSTALL_PACKAGE)`. This action was deprecated in Android 8.0 (API 26) and fails or acts unreliably on newer Android TV boxes (Android 10+).
- **Fix:** Replace with the modern standard pattern:
  ```kotlin
  val intent = Intent(Intent.ACTION_VIEW).apply {
      setDataAndType(uri, "application/vnd.android.package-archive")
      flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
  }
  ```
- **Status:** FIXED. Updated to `Intent.ACTION_VIEW` with explicit package-archive type.

### [RESOLVED] Glide Permanent Pausing via Application/Service Context onStop()
- **File:** [ScreensaverService.kt](file:///home/tom/github.com/androsaver/app/src/main/java/com/androsaver/ScreensaverService.kt), [ScreensaverEngine.kt](file:///home/tom/github.com/androsaver/app/src/main/java/com/androsaver/ScreensaverEngine.kt)
- **Description:** When the screensaver is stopped/detached, it calls `Glide.with(applicationContext).onStop()` or `Glide.with(context).onStop()` (using the service context). Since Glide treats service/application contexts as application-wide singletons, this pauses Glide's global RequestManager. Because `onStart()` is never called, Glide remains permanently paused. As a result, if the user returns to Settings or Setup activities, no images or previews will load.
- **Fix:** Remove the global `.onStop()` calls. Instead, cancel and clear individual view requests using `Glide.with(context).clear(imageView)` in `stop()` or when changing images.
- **Status:** FIXED. Removed global `onStop()` calls and replaced them with targeted `clear` requests on individual image views in `stopSlideshow()`.

### [RESOLVED] Unchecked Response Success in UpdateInstaller
- **File:** [UpdateInstaller.kt](file:///home/tom/github.com/androsaver/app/src/main/java/com/androsaver/UpdateInstaller.kt)
- **Description:** `UpdateInstaller.downloadAndInstall()` executes the network request to download the APK and streams it to the cache file without checking `response.isSuccessful`. If the server returns a 404, 500, or other error, it writes the error response (HTML or empty) to the file and attempts to install it, causing a package parse error or crash.
- **Fix:** Throw an `IOException` or return early if `!response.isSuccessful`.
- **Status:** FIXED. Checked `response.isSuccessful` and throw `IOException` if downloading fails.

### [RESOLVED] Fragile Update Download Lifecycle Binding
- **File:** [SettingsActivity.kt](file:///home/tom/github.com/androsaver/app/src/main/java/com/androsaver/SettingsActivity.kt)
- **Description:** The coroutine calling `UpdateInstaller.downloadAndInstall()` is launched on `viewLifecycleOwner.lifecycleScope` in `SettingsFragment`. If the device is rotated or if the activity configuration changes, the fragment view lifecycle is destroyed, immediately cancelling the download coroutine and aborting the APK download, leaving a corrupted or half-downloaded file.
- **Fix:** Use a context scope that survives fragment destruction, or perform the download/install outside the view's lifecycle (e.g. using the activity's `lifecycleScope` or a background task/worker).
- **Status:** FIXED. Switched view-bound lifecycle scope to an un-cancelled custom CoroutineScope utilizing the `applicationContext`.

### Plaintext Storage of Sensitive Credentials & Enabled Backup
- **File:** [Prefs.kt](file:///home/tom/github.com/androsaver/app/src/main/java/com/androsaver/Prefs.kt), [GoogleAuthManager.kt](file:///home/tom/github.com/androsaver/app/src/main/java/com/androsaver/auth/GoogleAuthManager.kt), [DropboxAuthManager.kt](file:///home/tom/github.com/androsaver/app/src/main/java/com/androsaver/auth/DropboxAuthManager.kt), [OneDriveAuthManager.kt](file:///home/tom/github.com/androsaver/app/src/main/java/com/androsaver/auth/OneDriveAuthManager.kt), [AndroidManifest.xml](file:///home/tom/github.com/androsaver/app/src/main/AndroidManifest.xml)
- **Description:** Sensitive user keys and configuration values (e.g. OAuth access/refresh tokens, client secrets, passwords for Nextcloud/Synology, OWM API keys) are stored in standard plaintext `SharedPreferences` instead of `EncryptedSharedPreferences`. Since `android:allowBackup="true"` is enabled in the manifest, this exposes sensitive user credentials to backups and unauthorized recovery.
- **Fix:** Migrate credential keys to `EncryptedSharedPreferences` and set `android:allowBackup="false"` in the manifest.
- **Status:** PENDING.

### [RESOLVED] ImageCache SSL Handshake Failures for Self-Signed Certificates
- **File:** [ImageCache.kt](file:///home/tom/github.com/androsaver/app/src/main/java/com/androsaver/ImageCache.kt)
- **Description:** `ImageCache` instantiates its own `OkHttpClient()` instead of sharing the `HttpClients.trustAll` instance. When background sync runs to cache images from self-hosted services (Synology, Nextcloud, Immich) that use self-signed certificates, the default `OkHttpClient` rejects the self-signed certificates, throwing an `SSLHandshakeException` and rendering the cache-fallback mechanism non-functional for those sources.
- **Fix:** Use `HttpClients.trustAll` in `ImageCache` to download and cache images.
- **Status:** FIXED. Configured `ImageCache` to use the shared `HttpClients.trustAll` client.

### [RESOLVED] SettingsActivity Indefinite "Downloading update..." UI Freeze on Failure
- **File:** [SettingsActivity.kt](file:///home/tom/github.com/androsaver/app/src/main/java/com/androsaver/SettingsActivity.kt)
- **Description:** When the update APK download fails in `SettingsFragment.onPreferenceTreeClick()`, the thrown exception is caught and logged, but the preference summary is never reset. The UI remains frozen displaying "Downloading update..." indefinitely, giving the user no feedback about the failure.
- **Fix:** In the `catch` block of the coroutine, update the preference summary back to a failed state, display an informative Toast to the user, and reset state variables to allow retry.
- **Status:** FIXED. Added try-catch handler to update preference summary, reset `pendingUpdateUrl`, and display a failure toast.

### [RESOLVED] Global SSL/TLS Validation Bypass Security Vulnerability in Glide
- **File:** [AndroSaverGlideModule.kt](file:///home/tom/github.com/androsaver/app/src/main/java/com/androsaver/AndroSaverGlideModule.kt)
- **Description:** `AndroSaverGlideModule` registers `HttpClients.trustAll` globally for Glide image loading. Bypassing certificate validation globally allows Glide to load images from self-signed local NAS servers, but it also silently disables certificate verification for public cloud providers (Google Drive, OneDrive, Dropbox). This leaves the application vulnerable to Man-in-the-Middle (MITM) attacks when loading cloud photos over public networks.
- **Fix:** Configure the OkHttp factory in Glide or a custom HostnameVerifier to verify certificates normally for public domain suffix APIs (e.g. googleapis.com, live.com, dropboxapi.com) and only fallback to trusting self-signed certs for local IP addresses or user-configured hosts.
- **Status:** FIXED. Implemented a dynamic `Call.Factory` in `AndroSaverGlideModule` that only uses `trustAll` for domains/IPs of configured self-hosted servers, using standard validating client otherwise.

### [RESOLVED] Screensaver Image Loading Sequential Bottleneck
- **File:** [ScreensaverEngine.kt](file:///home/tom/github.com/androsaver/app/src/main/java/com/androsaver/ScreensaverEngine.kt)
- **Description:** In `loadImages()` and `scheduleImageRefresh()`, the screensaver engine queries each configured `ImageSource` sequentially. If a user has multiple sources configured, or if one of the servers is slow or offline (taking up to 60 seconds to time out), the entire image-loading process is delayed. This causes the screensaver to remain stuck on the "Loading images..." screen for a long time.
- **Fix:** Query the configured image sources concurrently using Kotlin coroutines `async` and `awaitAll` to retrieve image lists in parallel.
- **Status:** FIXED. Utilized `async` and `awaitAll` to parallelize queries across all configured image sources.

### [RESOLVED] AudioEngine Cached Data Leak on Stop
- **File:** [AudioEngine.kt](file:///home/tom/github.com/androsaver/app/src/main/java/com/androsaver/visualizer/AudioEngine.kt)
- **Description:** When `AudioEngine.stop()` is called, it releases the system `Visualizer` instance but fails to clear/reset the active `_data` buffer (`AtomicReference(AudioData())`) and the volatile `lastWave`/`lastFft` references. When the visualizer is restarted or paused, the renderer can read the stale audio data, causing the rendering effects to start with a frozen snapshot of the last played audio frame instead of transitioning smoothly from silence.
- **Fix:** Clear the cached audio buffers and reset `_data.set(AudioData())` inside `stop()`.
- **Status:** FIXED. Cleared wave/fft buffers, reset averages, and reset `_data` snapshot to default silent state in `stop()`.

### [RESOLVED] Stale Location-Independent Weather Cache
- **File:** [WeatherFetcher.kt](file:///home/tom/github.com/androsaver/app/src/main/java/com/androsaver/WeatherFetcher.kt)
- **Description:** `WeatherFetcher.loadCached()` retrieves cached weather data based solely on the timestamp. It does not verify the city name. If the user changes the location in settings, the fetcher will serve the cached weather of the old city until the 30-minute cache lifetime expires.
- **Fix:** Store the city name alongside the cached JSON in `saveCached()`, and verify that the requested `city` matches the cached `city` in `loadCached()`.
- **Status:** FIXED. Added city name to the cache keys, validating that the requested city matches the cached city case-insensitively.

### [RESOLVED] Missing RECORD_AUDIO Permission Prompt on Settings Launch
- **File:** [SettingsActivity.kt](file:///home/tom/github.com/androsaver/app/src/main/java/com/androsaver/SettingsActivity.kt)
- **Description:** `SettingsActivity` only prompts the user for the `RECORD_AUDIO` permission if they manually change the screensaver mode to "Visualizer". If the mode is already set to "Visualizer" but the permission has been revoked or was never granted (e.g. restored from a backup or revoked via system settings), the activity does not prompt the user on startup. The screensaver will start up and silently fail to capture audio.
- **Fix:** Check if the current mode is `Prefs.MODE_VISUALIZER` and `RECORD_AUDIO` is not granted during `onResume()`, and launch the permission request if so.
- **Status:** FIXED. Added check on settings activity resume to request audio recording permission when visualizer mode is active but permission is missing.

### [RESOLVED] Volume Keys Dismiss Screensaver in Interactive Visualizer Mode
- **File:** [ScreensaverEngine.kt](file:///home/tom/github.com/androsaver/app/src/main/java/com/androsaver/ScreensaverEngine.kt)
- **Description:** In `handleKeyEvent()`, any non-DPAD key event (including volume buttons like `KEYCODE_VOLUME_UP`, `KEYCODE_VOLUME_DOWN`, `KEYCODE_VOLUME_MUTE`) triggers `onRequestFinish()` and exits the screensaver. This makes it impossible for users to adjust their TV volume while the music visualizer is running.
- **Fix:** Explicitly ignore volume control keys (`KEYCODE_VOLUME_UP`, `KEYCODE_VOLUME_DOWN`, `KEYCODE_VOLUME_MUTE`) in the `handleKeyEvent()` fallback branch.
- **Status:** FIXED. Volume keys are explicitly bypassed in `handleKeyEvent()` and passed to the system, avoiding screensaver dismissal.

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

### [RESOLVED] Activity Context Memory Leak in WeatherFetcher
- **File:** [WeatherFetcher.kt](file:///home/tom/github.com/androsaver/app/src/main/java/com/androsaver/WeatherFetcher.kt)
- **Description:** The class takes a `Context` parameter and holds it directly. If instantiated with an Activity context, this will prevent the activity from being garbage collected on configuration change or finish, causing a memory leak.
- **Fix:** Store the application context instead: `private val context = context.applicationContext`.
- **Status:** FIXED. Changed to assign `context.applicationContext` to the private context field.

### [RESOLVED] Unsynchronized Manifest File IO in ImageCache
- **File:** [ImageCache.kt](file:///home/tom/github.com/androsaver/app/src/main/java/com/androsaver/ImageCache.kt)
- **Description:** Caching files read/write `manifest.json` on the IO dispatcher without locking. If multiple background sync tasks execute concurrently, the manifest file can be partially written or corrupted, causing cache index corruption.
- **Fix:** Synchronize file read and write calls inside `readManifest()` and `writeManifest()`.
- **Status:** FIXED. Added `synchronized(this)` block to `readManifest` and `writeManifest` file operations.

### [RESOLVED] Glide Custom Transformation Bitmap Recycling Crash
- **File:** [ExifRotationTransformation.kt](file:///home/tom/github.com/androsaver/app/src/main/java/com/androsaver/ExifRotationTransformation.kt)
- **Description:** Custom Glide transformations should not put the source bitmap back to the pool because Glide itself manages pool recycling. Manually calling `pool.put(source)` leads to double-recycling of bitmaps and canvas crashes.
- **Fix:** Remove `pool.put(source)` call and return the created bitmap.
- **Status:** FIXED. Deleted manual pool recycling logic.

### [RESOLVED] Swallowed Exceptions in Setup Connection Testing
- **File:** [NextcloudSource.kt](file:///home/tom/github.com/androsaver/app/src/main/java/com/androsaver/source/NextcloudSource.kt), [SynologySource.kt](file:///home/tom/github.com/androsaver/app/src/main/java/com/androsaver/source/SynologySource.kt), [ImmichSource.kt](file:///home/tom/github.com/androsaver/app/src/main/java/com/androsaver/source/ImmichSource.kt)
- **Description:** The `getImageUrls()` method catches all exceptions internally and returns an empty list. As a result, the connection test in `NextcloudSetupActivity`, `SynologySetupActivity`, and `ImmichSetupActivity` reports "Connection successful: no images found" instead of "Connection failed" when an exception (e.g., DNS error, network timeout, SSL handshake failure, or auth error) occurs.
- **Fix:** Avoid catching all exceptions internally inside `getImageUrls()`, or propagate them to the caller, letting `ScreensaverEngine` handle them at a higher level, which allows setup activities to properly display the connection error.
- **Status:** FIXED. Propagated exceptions properly on connection errors by removing exception swallowing inside `getImageUrls()`.

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

### [RESOLVED] OpenGL ES Shader Handle Leaks in GLDraw
- **File:** [GLDraw.kt](file:///home/tom/github.com/androsaver/app/src/main/java/com/androsaver/visualizer/GLDraw.kt)
- **Description:** Compiled shader objects (`vert` and `frag`) are attached and linked to OpenGL programs but are never detached (`glDetachShader`) or deleted (`glDeleteShader`), leaking GPU resources. Furthermore, there are no checks for compilation or linking success (`GL_COMPILE_STATUS`/`GL_LINK_STATUS`), causing silent rendering failures if compile errors happen.
- **Fix:** Detach and delete shaders after program linking, and add checks to log compilation/link logs on failure.
- **Status:** FIXED. Detached and deleted shader handles after program linking, and added GL compilation/link status verification with logs.

### Incomplete Image Source Setup UX Summaries
- **File:** [SettingsActivity.kt](file:///home/tom/github.com/androsaver/app/src/main/java/com/androsaver/SettingsActivity.kt)
- **Description:** `SourcesFragment.onResume()` updates authorized/configured status summaries for Google Drive, OneDrive, Dropbox, and Immich, but completely ignores Nextcloud and Synology NAS sources. As a result, Nextcloud and Synology setup summaries permanently display static fallback texts ("Configure host, credentials, and folder") even after the user has successfully configured them.
- **Fix:** Add dynamic status checks `updateNextcloudStatus()` and `updateSynologyStatus()` to `SourcesFragment` and invoke them in `onResume()`.
- **Status:** PENDING.

### [RESOLVED] High CPU Trigonometry and Vertex Buffer Overflow in FlowFieldMode / MagnetarMode
- **File:** [FlowFieldMode.kt](file:///home/tom/github.com/androsaver/app/src/main/java/com/androsaver/visualizer/modes/FlowFieldMode.kt), [MagnetarMode.kt](file:///home/tom/github.com/androsaver/app/src/main/java/com/androsaver/visualizer/modes/MagnetarMode.kt), [GLDraw.kt](file:///home/tom/github.com/androsaver/app/src/main/java/com/androsaver/visualizer/GLDraw.kt)
- **Description:** Modes like `FlowFieldMode` (up to 100,000 particles on 4K TVs) and `MagnetarMode` (4,000 particles) draw each particle as a 4-segment filled circle (which translates to 400,000 triangles / 1,200,000 vertices for `FlowFieldMode` on 4K screens).
  1. This easily overflows `GLDraw`'s `MAX_VERTS` buffer limit (262,144 vertices), causing subsequent vertices to be silently dropped and particles to vanish from rendering.
  2. Running `cos`/`sin` calculations for every segment of every particle on the CPU inside the rendering loop (up to 400,000 trigonometry calls per frame) causes high CPU overhead and massive frame drops on low-end Android TV devices.
- **Fix:** Implement point sprite / point rendering using `GLES20.GL_POINTS` in `GLDraw`, or draw them as simple 2x2 quads or pre-calculated offsets, and reduce particle counts on TV screens.
- **Status:** FIXED. Added an optimized `particle()` method to `GLDraw` that draws particles as axis-aligned quads (6 vertices per particle) with zero trig calls. FlowFieldMode's `N_MAX` was also capped at `40000` to prevent buffer overflow on 4K displays.

### [RESOLVED] Transition Speed Preference Default Value Mismatch
- **File:** [screensaver_preferences.xml](file:///home/tom/github.com/androsaver/app/src/main/res/xml/screensaver_preferences.xml), [arrays.xml](file:///home/tom/github.com/androsaver/app/src/main/res/values/arrays.xml)
- **Description:** In `screensaver_preferences.xml`, the `transition_speed` ListPreference has `defaultValue="1500"`. However, the corresponding `transition_speed_values` array in `arrays.xml` only defines the values `1000`, `2000`, `3000`, `4000`, and `5000`. Because `1500` is not an entry in the list of allowed values, the preference UI fails to highlight the selected entry, causing the visual summary to display incorrectly or default to blank.
- **Fix:** Adjust the default value in `screensaver_preferences.xml` to `1000` or `2000`, or add `1500` to `transition_speed_values`/`transition_speed_entries`.
- **Status:** FIXED. Adjusted the default value to `2000` (2 seconds) in preferences and fallback value in ScreensaverEngine.
