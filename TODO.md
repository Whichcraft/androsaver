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
