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
- See `docs/architecture.md` for full class/file reference

## Reference Docs

| File | Contents |
|------|----------|
| `docs/architecture.md` | Full class map, package structure, key files |
| `docs/image-sources.md` | All 7 image source classes and their auth patterns |
| `docs/visualizer-modes.md` | All 10 effect classes and their audio reactivity |
| `visualizer-music-reactivity.md` | Detailed per-effect audio reactivity tables |
| `CHANGELOG.md` | Development history |
