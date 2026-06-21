# TODO — AndroSaver Bug & Code Review Tracker

This document tracks bugs, memory leaks, concurrency issues, and performance bottlenecks discovered during a deep code review of the Kotlin codebase.

---

## 1. High Priority (Thread Safety & Concurrency)

### [RESOLVED] Concurrency Race Conditions in AudioEngine
- **File:** [AudioEngine.kt](file:///home/tom/github.com/androsaver/app/src/main/java/com/androsaver/visualizer/AudioEngine.kt)
- **Description:** Android Visualizer capture listener callbacks (`onWaveFormDataCapture` and `onFftDataCapture`) run on a background audio processing thread. They modify shared fields (`energySum`, `energyHistory`, `smoothFft`, `genreWeights`, `detectAccum`, `detectFrames`, `midAvg`, `trebleAvg`) inside `publish()` without synchronization. Concurrently, the main thread calls `applyGenreHint()`, `detectGenre()`, and `resetDetection()`, which read and reset these fields. This causes race conditions, corrupted buffers, and data race bugs.
- **Fix:** Synchronize all updates and reads of shared audio state fields using a private lock or `synchronized(this)` inside `publish()`, `applyGenreHint()`, `detectGenre()`, and `resetDetection()`.
- **Status:** FIXED. Added proper `synchronized(this)` locks to all four methods.

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
