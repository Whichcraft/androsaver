# TODO — AndroSaver Bug & Code Review Tracker

All identified bugs, memory leaks, concurrency issues, and performance bottlenecks from the deep code review have been fully resolved.

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
