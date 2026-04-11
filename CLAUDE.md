# AndroSaver — Claude Instructions

## Project

Android TV screensaver app (Kotlin, Android 5+). Two modes: **Photo Slideshow** and **Music Visualizer** (10 OpenGL ES 2.0 effects). Targets Android TV (Huawei TV Stick, Fire TV Stick).

## Build

```sh
./gradlew assembleDevRelease    # debug logging build
./gradlew assembleProdRelease   # production build
./gradlew installDevRelease     # install via ADB
```

- Build flavors: `dev` (DEBUG_LOGGING=true) / `prod` (DEBUG_LOGGING=false)
- Build type: `release` only (no debug APK); signing via env vars KEYSTORE_PATH / KEYSTORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD
- versionName: `1.2.<patch>` where patch = `git rev-list --count HEAD` − 116
- versionCode: total git commit count

## Key Conventions

- All Kotlin, no Java
- `Prefs.kt` — all SharedPreferences key constants live here; always use these constants, never raw strings
- Encrypted credentials: AndroidX `security-crypto` EncryptedSharedPreferences
- HTTP: OkHttp only (no Retrofit, no Volley)
- Image loading: Glide + okhttp3 integration
- Async: Kotlin coroutines (no RxJava, no callbacks unless Android API forces it)
- No Hilt/Dagger; no MVVM/ViewModel; straightforward activity/service architecture

## Architecture

See `docs/architecture.md` for full class map.

- Entry point: `ScreensaverService` (DreamService) → `ScreensaverEngine`
- Settings UI: `SettingsActivity` → `SettingsFragment` + `SourcesFragment`
- Visualizer: `VisualizerView` → `AudioEngine` + `VisualizerRenderer` → 10 mode classes
- Image sources: implement `ImageSource` interface, registered in `ScreensaverEngine`

## Git / Release Workflow

- Work on `dev` branch; merge to `master` to ship
- Always update `CHANGELOG.md` before pushing
- Any new dev-only file (docs, notes, store copy, Claude instructions) must be added to `.gitattributes` with `export-ignore` — never ship dev files in release archives
- See `docs/architecture.md` for full class/file reference

### Release Cycle (follow in order)

1. **Update docs** — update `CHANGELOG.md` and any affected `docs/` reference files
2. **Update qmd index** — run `qmd update && qmd embed` so the index and embeddings reflect all changes
3. **Push to dev** — commit and push to the `dev` branch
4. **Merge to master** — merge `dev` → `master` to ship the release

## Visualizer Tuning Notes

- **CubeMode** — the inner cube (scale × 0.45) must not spin visibly faster than the outer cube at default intensity (beatGain=1.0). Both cubes share the same rotation state (`rx/ry/rz`), so this is maintained automatically. If rotation velocity constants are ever changed, verify at default intensity that the inner cube does not appear to spin erratically.

## psysuals Submodule

`psysuals/` is the upstream Python reference implementation of the visualizer (`psysualizer.py`, pygame + sounddevice). It is **not part of the Android app** — it runs on desktop as a development/preview tool.

- When visualizer modes are tuned in AndroSaver, port equivalent changes back to `psysuals/psysualizer.py`
- The two codebases share the same 10 mode names and audio reactivity logic, but use different rendering stacks (OpenGL ES 2.0 vs pygame)
- Backport status: all known changes ported as of 2026-03-30 (see memory)

## Reference Docs

| File | Contents |
|------|----------|
| `docs/architecture.md` | Full class map, package structure, key files |
| `docs/image-sources.md` | All 7 image source classes and their auth patterns |
| `docs/visualizer-modes.md` | All 13 effect classes and their audio reactivity |
| `docs/settings-reference.md` | All Prefs keys, UI types, and defaults |
| `docs/psysuals-port-notes.md` | **Always read when porting psysuals updates** — Android adaptations, per-effect standing deltas, import checklist |
| `visualizer-music-reactivity.md` | Detailed per-effect audio reactivity tables |
| `CHANGELOG.md` | Development history |
