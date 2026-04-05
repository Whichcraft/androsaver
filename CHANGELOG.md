# Changelog

All notable changes to AndroSaver are documented here.

---

## 2026-04-05

### Fixed
- **Corridor spark trailing** — sparks now trail ~25 frames (matching psysuals `_SPARK_FADE=10`) via a 25-frame screen-position ring buffer replayed with additive blend, instead of fading with the same 0.11f rate as the corridor frames

---

## 2026-04-03

### Added
- **Spaceflight** — psychedelic hyperspace trip: 340 rainbow warp streaks burst outward from the ship's focal point; beat fires expanding neon rings; planets grow from tiny dots to enormous spheres; ship banks through jinks, climbs and barrel rolls with twin engine blooms. Port of psysuals v2.1.0.
- **Butterflies** — up to 3 pairs of neon butterflies: solo enters first, partner joins and orbits lovingly; wing-flap syncs when close; sparkles on strong beats; pairs drift off-screen and new ones enter continuously. Port of psysuals v2.1.0.

### Changed
- **Yantra** — port psysuals v2.0.3: web lines brighter (base 0.18→0.30), ring energy coefficient 0.25→0.32, spoke base lightness 0.18→0.30 and beat term 0.975→0.50; Branches/Cube/Tunnel already in sync

### Added
- **Lissajous head dots** — restored 3 filled dots at the tip of each arm; pulse with beat; ring halo on strong kicks
- **Auto-detect music genre** — new "Auto-detect" option in Music Genre setting; analyzes the FFT spectrum every 30 seconds (after ~20 s of audio) and automatically applies the best genre hint (Electronic / Rock / Classical / Any)

### Fixed
- **Update checker version** — CI was generating `1.4.x` instead of `2.0.x`; now matches `build.gradle` formula (base 144)

### Changed
- **Visual Effect Cycle** — renamed from "Visual Effect"; list now shows Off / On / Random instead of individual effect names (effect selection moved to Active Effects)

---

## 2026-04-02

### Changed
- **Target SDK** — bumped `compileSdk` and `targetSdk` from 34 → 35 (Android 15); `minSdk` remains 21
- **Release cycle** — documented release steps in `CLAUDE.md`

---

## 2026-04-01 (cont 18)

### Fixed
- **Weather widget** — moved from top-left to top-right corner; text right-aligned
- **Weather settings warning** — Show Weather summary now shows ⚠ when enabled but city or API key is missing; updates live as you type; shows "Showing weather for {city}" when fully configured
- **Weather API key input** — Return key now confirms like the city field (was inserting newlines)

---

## 2026-04-01 (cont 17)

### Added
- **Default images** — `app/src/main/assets/default_images/` directory; images placed there are bundled in the APK and shown automatically in slideshow mode when no source is configured; ignored as soon as any source is enabled
- **Weather city Return key** — pressing Return/Done on the city text field now confirms the input instead of inserting a newline

### Fixed / Documentation
- **Music Genre Hint** — README now documents the exact FFT bin ranges and weight multipliers for each genre setting

---

## v2.0.1 — 2026-04-01

### Added
- **Random visual effect mode** — new option in the Visual Effect picker; picks a random enabled effect at startup and on each auto-cycle interval
- **Active Effects multi-select** — uncheck effects to exclude them from Auto/Random cycling and remote ← → navigation; defaults to all active
- **Autostart hint** — info line at top of Settings with exact path to activate AndroSaver on Android TV and Fire TV

---

## 2026-04-01 (cont 16)

### Added
- **Random visual effect mode** — new option in the Visual Effect picker; picks a random enabled effect at startup and on each auto-cycle interval (vs Auto which cycles in order)
- **Active Effects multi-select** — new setting under Visual Effect; uncheck effects to exclude them from Auto and Random cycling (and from ← → remote navigation); unchecked effects can still be selected directly by name; defaults to all active
- **Autostart hint** — non-interactive info line at top of Settings explains the path to activate AndroSaver as the system screensaver (Android TV and Fire TV paths)

---

## v2.0.0 — 2026-04-01

### Three new visualizer effects

This is the big one. Three spectacular new effects join the lineup — the most ambitious visual update since the app launched.

**TriFlux** — A wall of triangles covers the entire screen, every edge pulsing with a rolling rainbow. On beats, tiles hurl themselves to the foreground, bounce off screen edges, and snap back into the mosaic. Nothing sits still.

**Branches** — A psychedelic fractal lightning tree erupts from screen centre. Nine neon arms split recursively to depth 7 — each segment drawn as a glowing halo and a white-hot core. Mid frequencies twist every branch angle live; beats fire extra arms and flood the screen with colour.

**Corridor** — A first-person ride through an infinite neon rainbow corridor. Twenty-eight luminous frames rush toward you, hues sweeping the full spectrum. Bass drives speed; beats launch glowing spark particles that streak outward from centre.

### Visualizer tune-up
- **YantraMode** — 7th ring added; per-ring graduated rotation speed; tighter ring spacing; higher base brightness
- **CubeMode** — always 2 satellite cubes fixed 180° apart with independent rotation and additive-blend trails; scale capped at 1.25× (bumps on beat, never grows huge); rotation velocity clamped to prevent runaway at heavy bass; satellite trail halved for snappier fade
- **TunnelMode** — triangle spawn rate raised 4× (bass×4 + beat×6); interior star polygon halved for cleaner geometry; tube radius restored to original
- **BranchesMode** — trunk stub halved for denser ball-like centre shape
- **CorridorMode** — sparks always render above frames via additive blend pass; spawn rate raised for Android (bass×5)

### Auto-cycle default
- Auto-cycle interval now defaults to **2 minutes** (pre-selected in settings)

---

## 2026-04-01 (cont 15)

### Changed — psysuals v2.0.3 + v2.0.4
- **BranchesMode** — trunk stub halved `0.03→0.015` for denser ball-like shape at centre
- **CubeMode** — scale capped at 1.25× (spring physics restored from v2.0.3; `max(0.5,scale)` → `coerceIn(0.5,1.25)`) so cube bumps on beat without growing large
- **TunnelMode** — triangle spawn rate increased `bass×1.5+beat×3.0 → bass×4.0+beat×6.0`; interior star polygon halved `sR×0.52 → ×0.24`
- **YantraMode** — 7th ring added (`N_RINGS 6→7`, signs/bands extended); per-ring base rotation speed `0.0004 → 0.010+i×0.003` (2.5× faster, gradient across rings); tighter ring spacing `base_r 0.13+i×0.83 → 0.28+i×0.62`; poff scale `0.70→0.38`
- **psysuals submodule** — updated to v2.0.4

### Changed
- **Auto-cycle interval** — "2 minutes" now labelled "(default)" in the settings list; "Off" entry now shows "(switch with arrow buttons)"

---

## 2026-04-01 (cont 14)

### Changed — psysuals v2.0.2
- **CubeMode** — rotation velocity clamped after damping (`rvx/rvy` → ±0.08, `rvz` → ±0.05 rad/frame) to prevent runaway spinning on heavy beats; satellite trail ring buffer halved 30→15 frames (matches psysuals `_SAT_FADE` 8→16)
- **psysuals submodule** — updated to v2.0.2

---

## 2026-04-01 (cont 13)

### Fixed
- **CubeMode** — base idle rotation constants reverted to v1.4.x values (`0.00025/0.00035/0.00018`); psysuals v2.0.0 values were ~6× higher, causing the inner cube to spin erratically at default intensity on full-screen TV. Audio-reactive multipliers and 0.94 damping kept from v2.0.0. Delta documented in `docs/psysuals-port-notes.md`.

---

## 2026-04-01 (cont 12)

### Changed — psysuals v2.0.1
- **BranchesMode** — psychedelic edition: MAX_DEPTH 6→7; BASE_ARMS 6→9; triple-fork extended to trunk+first-split (was trunk only); neon glow: each segment drawn twice (wide dim halo + bright core); jitter adds third sine term; hue sweep 0.45→0.80 across depth; trunk 0.03× stub (was 0.15×) with cap at 0.27×min(W,H); `spread` π/2.6 (was π/2.8); trail fade 16→10; `hue += 0.012`, faster time; arm hue spread 0.35→0.75; extra arms on beat ×2.2 (was ×1.5)
- **TriFluxMode** — grid extended one tile past all screen edges so no clipped triangles appear at borders (x0=−tw, y0=−th; N_COLS+2 cols, N_ROWS+3 rows)
- **YantraMode** — higher base brightness, smaller intensity steps: ring bright 0.42+e×0.35+beat×0.585 → 0.52+e×0.25+beat×0.50 (rings visible even at silence)
- **psysuals submodule** — updated to v2.0.1

---

## 2026-04-01 (cont 11)

### Fixed
- **TunnelMode** — restore psysuals v2.0.0 (original) parameters: TUBE_R 2.0→2.8; dt formula bass×0.09+beat×0.18; spawn rate bass×1.5+beat×3.0 when beat>0.3 (was 0.6/1.5, rarely spawned at low intensity); triangle size pre-computed at spawn; cap raised to 120. Added interior rotating star polygon at each ring centre (n_star=3+(i%4), complementary hue). Removed v1.4.3-specific spark trail ring buffer.
- **docs/psysuals-port-notes.md** — new standing reference: Android adaptation rules (surfaces→fadeBlack/ring-buffers, blend modes), per-effect deltas, import checklist. Referenced from CLAUDE.md so it is always consulted when porting future psysuals updates.

---

## 2026-04-01 (cont 10)

### Added
- **TriFluxMode** — new effect (psysuals v2.0.0): triangle mosaic wall. All triangles are rainbow-edge wireframes; N_FILLED=5 tiles filled at any time. Bass beats pop interior tiles to the foreground at 4.5–8.5× scale (up to 3 active at once), bouncing off screen edges and spring-returning to grid. Two independent rainbow sweep bands traverse the grid.
- **BranchesMode** — new effect (psysuals v2.0.0): recursive fractal lightning tree. 6 neon arms radiate from screen centre, each branching to depth 6. Mid frequencies jitter branch angles; bass drives trunk length; beat fires extra arms and a brightness burst.

### Changed
- **CubeMode** — port of psysuals v2.0.0: always 2 satellites fixed 180° apart (no variable beat-count); independent satellite rotation (`satRx`, `satRy`); `satScale` capped at 0.55; updated rotation damping (×0.94) and svel physics; spinDir flip removed
- **CorridorMode** — port of psysuals v2.0.0: sparks now drawn after (on top of) corridor frames with additive blend, replacing the unified sorted draw list
- **Mode list** — Plasma moved from slot 3 to slot 10; TriFlux inserted at slot 3; Branches added at slot 11; order now matches psysuals v2.0.0 `MODES` list
- **psysuals submodule** — updated to v2.0.0

---

## 2026-04-01 (cont 9)

### Changed
- **Version bumped to 1.4** — `versionPatchBase` reset to 133 in `build.gradle` and `build.yml`; patch counter restarts from 0

---

## 2026-04-01 (cont 8)

### Changed
- **Mode order** — Lissajous and Tunnel swapped to match psysuals v1.4.3 (Lissajous=4, Tunnel=5)
- **TunnelMode** — port of psysuals v1.4.3: variable dt (bass×0.14 + beat×0.22); bass expands tube radius (+0.6×bass); ring brightness and line weight scale with bass; continuous spark spawning (bass×0.6 + beat×1.5); spark size strongly bass-reactive (1+bass×3+beat×1.5); sparks drawn with additive blending above tunnel rings via 40-frame trail ring buffer; spark cap 60
- **CubeMode satellites** — port of psysuals v1.4.3: satellite trail ring buffer (30 frames) drawn with additive blend; satellites start dim and brighten with energy (lightness 0.38+svel×0.20 → 0.18+svel×0.28)
- **psysuals submodule** — updated to v1.5.0

---

## 2026-04-01 (cont 7)

### Fixed
- **CubeMode satellites** — port of psysuals v1.4.2: replaced per-vertex perspective projection (`projectOffset`) with centre-based uniform 2D scale (`projectSat`); satellites no longer appear skewed/distorted; orbit centre clamped so satellites never leave the screen

---

## 2026-04-01 (cont 6)

### Fixed
- **CorridorMode** — port of psysuals v1.4.1 fixes: sparks now bounded to ±85% of corridor half-extents (no longer escape tunnel walls); per-spark `pt` dropped, path uses `time` so sparks align with frames at same depth; frames and sparks merged into a single back-to-front draw list so near frames can no longer overwrite nearer sparks

---

## 2026-04-01 (cont 5)

### Fixed
- **CorridorMode sparks** — bass coefficient raised 1.2→5 and beat threshold removed; sparks now spawn visibly from bass alone at all intensity levels (old formula gave 0 sparks at typical bass values below 0.84)

---

## 2026-04-01 (cont 4)

### Added
- **CorridorMode** — new visualizer effect ported from psysuals v1.4.0; concentric neon rounded-rectangle frames fly toward the camera with full rainbow sweep across depth; beat flares nearest frames; bass + beat spawn glowing spark particles; gentle curving path. 11th mode, inserted between Lissajous and Nova.

---

## 2026-04-01 (cont 3)

### Changed
- **CubeMode satellite count** — scales with beat intensity: 2 at baseline, up to 6 at max beat (`2 + int(beat.coerceAtMost(2f) * 2)`); satellite scale raised from 0.16→0.28 to match psysuals; orbit spacing uses nSats instead of fixed N_MAX=2. Port of psysuals commit a3acb0c.

### Fixed
- **BubblesMode bass band** — was `fft.meanSlice(0, 8)`, now `fft.meanSlice(0, 6)`; bins 6–7 overlap with the mid band (`fft[6:30]`). Port of psysuals bug fix (commit 8dbf0db).

---

## 2026-04-01 (cont 2)

### Fixed
- **.gitattributes** — added `export-ignore` for `psysuals/` and `.claude/`; both were missing and would have been included in GitHub release source archives

---

## 2026-04-01 (cont)

### Fixed
- **build.yml** — delete ALL existing `dev` releases (not just one) before publishing; two releases existed for the tag causing the conflict

---

## 2026-04-01

### Changed
- **build.yml** — version bump to 1.3, patch base updated to 124
- **docs/architecture.md** — merged `ImageItem` row into `ImageSource.kt` row; expanded permissions table with `INTERNET`, `ACCESS_NETWORK_STATE`, `MODIFY_AUDIO_SETTINGS`, `REQUEST_INSTALL_PACKAGES`, `RECEIVE_BOOT_COMPLETED`

---

## 2026-03-30 — version 1.3.0

### Changed
- Version bumped to 1.3 (patch counter reset to 0)

---

## 2026-03-30 (cont)

### Changed
- **security-crypto** — upgraded from `1.1.0-alpha06` → `1.1.0` (stable)
- **BootReceiver** — now refreshes OAuth tokens for Google Drive, OneDrive, and Dropbox on device boot (was Google Drive only)

### Fixed
- **docs/architecture.md** — `BootReceiver` was incorrectly described as unused; it pre-refreshes the Google Drive OAuth token on boot
- **docs/settings-reference.md** — `UPDATE_CHANNEL` clarified as internal/auto-set, not a user-facing preference

---

## 2026-03-30

### Added
- `CLAUDE.md` — project conventions, build commands, key patterns for Claude Code sessions; includes `.gitattributes` export-ignore rule
- `docs/architecture.md` — full class map, data flow, build variants, permissions reference
- `docs/image-sources.md` — all 7 image source classes with auth patterns and Prefs key names
- `docs/visualizer-modes.md` — all 10 effect classes with audio reactivity summaries and guide for adding new modes
- `docs/settings-reference.md` — all Prefs keys mapped to UI preference types and defaults
- `docs/image-sources.md` — added "Adding a New Source" checklist
- `CLAUDE.md` — document psysuals submodule purpose and backport workflow

---

## 2026-03-26 — version 1.2.0

### Changed
- Version bumped to 1.2 (patch counter reset to 0)

---

## 2026-03-26 (session 15 cont18)

### Fixed
- **TunnelMode triangles** — projected at world origin so they drifted outside tunnel walls; now use `path(tri.pt)` to stay centered inside tunnel; also lowered BASS_THRESH 0.35→0.20 so bursts trigger more reliably

### Changed
- **SpiralMode** — reverted all session tuning back to pre-session state (RADIUS=1f, SPIN=1.6f, N_SYM=3, original speed/rMod/flash)

---

## 2026-03-26 (session 15 cont17)

### Changed
- **YantraMode** — rings near-static without audio (base rvel 10× reduced); rotation driven by energy/beat; fadeBlack dynamic (long trails on loud music, short trails on quiet)

---

## 2026-03-26 (session 15 cont16)

### Fixed
- **ScreensaverEngine** — switching effects now resets intensity to the saved preference default (not carried over from previous effect)

### Changed
- **LissajousMode** — removed all circles (bass bump rings, high-freq glow, head dots); dot trails only
- **SpiralMode** — removed 3-fold rotational symmetry (N_SYM 3→1); single spiral only

---

## 2026-03-26 (session 15 cont15)

### Fixed
- **TunnelMode triangles** — were projected off-screen due to path offset magnification; now travel straight down world origin (center axis)

### Changed
- **SpiralMode** — center black hole shrunk (RADIUS 1.0→0.05)
- **YantraMode** — added fadeBlack(0.12f) for color motion trails on rotating rings

---

## 2026-03-26 (session 15 cont14)

### Changed
- **TunnelMode** — pure geometry tunnel; bass-punch triangle bursts scale with intensity (2–15 triangles, wilder spin/size at elevated bass)
- **LissajousMode** — removed center dot bubbles

---

## 2026-03-26 (session 15 cont13)

### Changed
- **CubeMode** — spin direction flips on each bass punch (rising edge at 0.4); fixed 2 satellites (opposite each other, bouncy)

---

## 2026-03-26 (session 15 cont12)

### Changed
- **LissajousMode** — 50% less music interaction across all multipliers
- **LissajousMode** — faster reactivity restored (beat speed/scale/rotation ×3–4); center bubbles always present (3-fold, scale with bass/beat); high-freq glow halo at center; bass bump rings via spring system

---

## 2026-03-26 (session 15 cont11)

### Changed
- **CubeMode satellite cubes** — smaller (0.28→0.16 scale), bouncy radial oscillation per slot, no deformation
- **CubeMode trails** — longer (8→14 frames) and brighter (alpha 0.55→0.75) for all cubes

---

## 2026-03-26 (session 15 cont10)

### Changed
- **TunnelMode rework** — bass-punch triangles on the center line; constant speed always; beat circles removed
- **LissajousMode** — less nervous (frequency/phase band influence cut 4×); trail fades faster (0.08→0.18)
- **SpiralMode** — tighter spiral (SPIN 1.6→3.0); extremes reduced (beat speed, scale, radius mod, flash all pulled back)

---

## 2026-03-26 (session 15 cont9)

### Fixed
- **Update checker version mismatch** — CI was hardcoding `1.0.x` in `version.json`; now mirrors `build.gradle` formula (`1.1.$(versionCode - 101)`)

---

## 2026-03-26 (session 15 cont8)

### Changed
- **YantraMode intensity +30%** — all beat multipliers ×1.3
- **CubeMode intensity +30%** — all beat multipliers ×1.3
- **CubeMode satellite cubes no longer deform** — removed vertex clamping from projectOffset; orbit radius clamping already keeps them on-screen
- **TunnelMode constant speed** — dt is now a fixed constant (no beat/bass fluctuation); always a smooth ride
- **TunnelMode triangles removed** — triangle data class, spawn logic, bass spring, and draw loop fully removed
- **LissajousMode size +30%** — projection scale raised from 0.40 to 0.52 of screen dimension

---

## 2026-03-26 (session 15 cont7)

### Changed
- **CubeMode satellite smoothness** — fixed angular slot divisor to N_MAX=6 so existing satellites never jump when beat changes active count
- **TunnelMode inner circles** — removed constant per-ring rotating polygons; replaced with beat-spawned circles that travel through the tunnel independently
- **LissajousMode intensity** — all beat multipliers divided by 3 (scale, hue, rotation, knot speed, head dot size)

---

## 2026-03-26 (session 15 cont6)

### Changed
- **Patch version resets on minor bump** — introduced `versionPatchBase` so 1.1.x patch count starts from 0 at the 1.1 bump; future minor bumps just update the base constant
- **Auto-cycle timer resets on manual effect change** — left/right navigation now restarts the full cycle interval

---

## 2026-03-26 (session 15 cont5)

### Removed
- **Check for Updates button** — removed separate preference row; update check still runs automatically on resume and result shown in the AndroSaver about row

---

## 2026-03-26 (session 15 cont4)

### Changed
- **Version bumped to 1.1** — versionName is now `1.1.${gitVersionCode}`
- **Visualizer intensity default lowered to Low (0.5)** — effects start at lowest level instead of Medium
- **Auto-cycle interval default changed to 2 minutes** — was Off; added 10-minute and 15-minute options

---

## 2026-03-26 (session 15 cont3)

### Added
- **CubeMode echo trail** — last 8 frames of main and inner cube positions are replayed each frame with decreasing alpha, giving a motion-trail/echo effect; implemented via ring buffer (renderer clears the screen every frame, so framebuffer persistence cannot be relied on)
- **CubeMode satellite bounds clamping** — orbit radius is clamped per-frame so satellite cubes never leave the screen, accounting for screen size and current cube scale

### Changed
- **Removed separate Update Channel row** — channel is now shown inline in the version string (e.g. "Version 1.0.42 · dev channel"); the read-only channel preference row is gone

### Fixed
- **Version always 1.0.1 / update check never finding updates** — CI checkout was a shallow clone (`fetch-depth` defaulting to 1), so `git rev-list --count HEAD` always returned 1; added `fetch-depth: 0` to the build job checkout

### Changed
- **Version shows update channel** — About entry now displays e.g. "Version 1.0.42 · dev channel" instead of just the version number

---

## 2026-03-26 (session 14)

### Added
- **Satellite cubes in CubeMode** — port of psysuals v1.3 orbiting satellite cubes; 2–6 small cubes (28% of main cube scale) orbit at radius 2.6 using the same rotation matrix as the main cube; count scales with beat intensity (`2 + int(beat * 2)`), orbital speed increases on stronger beats
- **Check for Updates button** — new "Check for Updates" entry in the About settings section; shows "Checking…" while in progress, then "Up to date" or "Update available: vX — press to install"

### Changed
- **Update channel locked to build flavor** — dev builds always check the dev channel, stable builds always check the stable channel; channel selector is now read-only with an "automatic" label; manual channel switching removed

---

## 2026-03-26 (session 13)

### Fixed
- **Resource leaks** — HTTP responses in `GoogleAuthManager` (two call sites) and `UpdateChecker` were not closed after use; wrapped with `.use {}`; `UpdateInstaller` response body was consumed without closing the response itself
- **Dead code** — removed unused `logout()` method in `SynologySource` (intentionally not called; SID expires naturally)

### Changed
- **Shared HTTP clients** — replaced 9 separate `OkHttpClient` instances across sources and auth managers with two singletons in `HttpClients`: `standard` (cloud APIs) and `trustAll` (self-hosted LAN servers with self-signed certs); reduces thread/connection pool overhead
- **Per-source fetch timeout** — `ScreensaverEngine` now wraps each source's `getImageUrls()` call with `withTimeoutOrNull(60s)` in both the initial load and the periodic refresh loop; a hung source can no longer block the entire slideshow

---

## 2026-03-26 (session 12)

### Changed
- **psysuals submodule** — updated from v1.2.0 → v1.3.0; picks up backported AndroSaver tuning improvements, orbiting satellite cubes in CubeMode (count 2–6 scales with beat intensity), Lissajous `raw` variable bug fix, and 5 additional fixes from code audit

---

## 2026-03-26 (session 11)

### Fixed
- **CI build** — missing `import com.androsaver.auth.DropboxAuthManager` in `SettingsActivity` caused `Unresolved reference: auth` compile error; spurious `auth.` prefix removed from call site

---

## 2026-03-26 (session 10)

### Added
- **In-app updater** — app silently checks GitHub Releases for a newer `version.json` on each Settings open; if a newer build is available the About row shows "Update available: vX — press to install"; pressing it downloads the APK and hands it to the system installer (`REQUEST_INSTALL_PACKAGES`)
- **Update channel selector** — `ListPreference` in the About section lets users switch between Stable and Dev update channels; defaults to the build's own flavor; re-checks for updates immediately on change
- **About preference** — new About category at the bottom of Settings always shows the current installed version
- **Image Sources sub-screen** — all source toggles and setup entries moved to a dedicated sub-page (press Back to return); the main Settings screen shows a single "Image Sources" entry with a live summary of active source names (e.g. "Google Drive, Dropbox")
- **EXIF orientation** — images are now displayed in their correct orientation; `LocalStorageSource` reads `MediaStore.Images.Media.ORIENTATION` at list time (free — already in the cursor); `ImageCache` reads `ExifInterface` from each cached file; `ExifRotationTransformation` (full 8-case EXIF including flips and transposes) is applied via Glide for local and cached images; remote HTTP images rely on Glide's built-in `Downsampler` EXIF handling
- **Synology session auto-refresh** — image URLs (which embed the DSM session ID) are automatically re-fetched every 25 minutes, re-logging in to Synology before the ~30-minute DSM session expiry; the slideshow continues uninterrupted and all other sources also refresh at the same time

### Changed
- `versionCode` is now derived from `git rev-list --count HEAD` at build time (auto-incrementing); `versionName` is `1.0.<versionCode>`
- CI now generates and publishes `version.json` alongside every APK release (dev and stable)
- **Ken Burns** — reduced oversize from 5–14 % to 4–8 %; all four presets now end at translation (0, 0) so the image is always centered at rest; two presets zoom in (pan from offset to center), two zoom out (pan from offset to center at reduced scale)
- **Proprietary licence** — replaced placeholder MIT licence with All Rights Reserved; unauthorised copying, modification, distribution, or sale is prohibited

### Fixed
- CHANGELOG session 9 entry had literal `\n` sequences instead of real newlines (same `sed` corruption bug as AndroidManifest)

---

## 2026-03-25 (session 9)

### Added
- **Dropbox source** — fetch photos from Dropbox via API v2; OAuth 2.0 authorization code flow (user visits auth URL on another device, pastes code back); access token refreshed automatically using App Key + App Secret; images fetched as temporary links (4-hour pre-signed URLs) retrieved in parallel; configurable folder path

---

## 2026-03-25 (session 8)

### Added
- **Immich source** — fetch photos from a self-hosted [Immich](https://immich.app) server via its REST API; authenticates with an API key (generate in Account Settings → API Keys); optional Album ID to restrict to one album, or leave blank to show all photos; supports self-signed certificates; default port 2283

---

## 2026-03-25 (session 7)

### Added
- **OneDrive source** — fetch photos from Microsoft OneDrive (personal or work) via Microsoft Graph API; uses OAuth 2.0 device code flow (same TV-friendly pattern as Google Drive); downloads use pre-authenticated URLs so no auth header is needed for image loading

---

## 2026-03-25 (session 6)

### Added
- **Nextcloud source** — fetch photos from any Nextcloud instance via WebDAV (PROPFIND + Basic Auth); supports app passwords and self-signed certificates; setup UI mirrors the Synology setup

---

## 2026-03-25 (session 5)

### Changed
- **Dev vs Stable builds**: added `dev` and `prod` product flavors. `BuildConfig.DEBUG_LOGGING` is `true` in dev, `false` in prod — all `Log.*` calls are gated behind this flag so no debug output ships in Stable or Play Store builds
- CI updated: dev branch builds `assembleDevRelease`; master builds `assembleProdRelease` / `bundleProdRelease`

---

## 2026-03-25 (session 4)

### Fixed
- CI: release steps now force-push the `dev`, `stable`, and `playstore-stable` tags to the current commit before publishing, so GitHub's auto-generated source archives (`/archive/refs/tags/*.zip`) always match the released build
- CI: `bundle-playstore` job now merges `master` into the `playstore` branch before building the AAB, keeping the branch and its source archive in sync with master

---

## 2026-03-25 (session 3)

### Changed
- **Effect cycling** now always starts each effect from its default state — `reset()` is dispatched to the GL thread via a `@Volatile` flag so no mode ever draws an uninitialised first frame
- `YantraMode`: beat reactivity increased by ~1.5× (ring pulse, brightness, spoke reach, outer radius)
- `CubeMode`: beat reactivity doubled; base spin ~65% faster; scale pulse on beat substantially increased; trail persistence increased (fadeBlack 0.13→0.07)
- `TunnelMode`: default speed and reactivity reduced (−2 intensity steps) so tunnel is calm at Normal intensity and scales up with the slider; path swing reduced and tube radius increased so viewer stays inside the tube most of the time; triangles burst larger on bass hits (spring physics); triangle spawn count is now continuously driven by both bass and beat (more sound → more triangles); triangle cap increased (80→120)
- `BubblesMode`: initial bubble pool halved (400→200); spawn rate and beat-spring pulsation reduced for a calmer default start

### Fixed
- **Slideshow — black screen with Synology source**: `SynologySource.getImageUrls()` was calling `logout()` immediately after listing files, invalidating the session ID that was embedded in every image URL before Glide could load any images. The logout is now omitted; the DSM session expires naturally after ~30 minutes
- **Slideshow — black edge on right side**: Ken Burns `startScale` raised from 1.0 to 1.05 so the image always overflows the view bounds on all sides, eliminating the gap that appeared when a translation shifted the image at 1:1 scale
- `SettingsActivity`: replaced deprecated `requestPermissions` / `onRequestPermissionsResult` API with `ActivityResultLauncher` for both storage and audio permissions

---

## 2026-03-25 (session 2)

### Added
- Slideshow remote control: ← / → now navigate to previous / next image; skipping resets the auto-advance timer
- Display Overlays settings section — clock and weather are now configurable in both Slideshow and Visualizer mode (previously clock was slideshow-only in the UI)
- Music Genre hint documented: explains how each genre adjusts bass-frequency weighting for beat detection (Electronic boosts sub-bass, Rock boosts mid-bass, Classical reduces bass / boosts harmonics)

### Changed
- `AudioEngine`: doubled capture rate (removed `/2` on `getMaxCaptureRate`), reduced FFT smoothing (0.75→0.50), halved energy-history window (30→15 frames) — faster overall music reaction
- `BarsMode` (Spectrum): added `display[]` smoothed height buffer; lerp speed scales from 0.25 at silence to 1.0 at full beat — calmer bars at low intensity, snappy at high
- `BubblesMode`: capped `beatSel` for spawn count and bubble size so high intensity doesn't flood the screen; wider hue spread at high intensity; extra neon rings appear only when beat > 1.0 (i.e. intensity > Normal)
- `CubeMode`: reduced base rotation and band multipliers further; scale pulse now driven by both beat and direct bass for a sharper size pop
- `LissajousMode`: substantially reduced trace speed, phase-selector drift, frequency-selector reactivity, scale burst, rotation inertia, and hue jump — much calmer at default intensity
- `NovaMode`: replaced oversized centre circle with two counter-rotating layers of 3 triangles; redistributed beat energy to rings, spokes, and ring brightness
- `PlasmaMode`: reduced idle time increment ~60% and hue cycle rate for slower default movement
- `TunnelMode`: tunnel radius is now dynamic (small at silence, expands with beat/intensity); triangle spawn threshold raised and count/size reduced — scales cleanly with intensity
- `WaterfallMode`: removed `e < 0.02` skip; minimum lightness floor (`coerceIn 0.04`) ensures no black tiles
- `YantraMode`: beat impulse on ring pulsation increased; ring displacement amplitude increased; ring brightness now includes a beat term; spokes reach further on beat; centre dot substantially reduced
- Auto-cycle interval default changed to Off (was 90 s)
- Show Clock default changed to Off
- Auto-cycle options simplified to Off / 1 min / 2 min / 5 min (removed 30 s option)

### Fixed
- CI: upgraded `gradle/actions/setup-gradle` from v3 to v4 for Node.js 24 compatibility

---

## 2026-03-25

### Added
- Preview mode — test the screensaver without activating the system screensaver
- Clock overlay — date and time shown in the corner during slideshow
- Ken Burns effect — slow pan/zoom animation on photos
- Visualizer overlay — music visualizer rendered on top of the photo slideshow
- Image cache — offline fallback; up to 200 images / 300 MB stored locally
- Weather widget — current temperature and conditions from OpenWeatherMap
- Schedule — restrict screensaver to a configurable time window
- Music visualizer mode — 10 OpenGL ES 2.0 audio-reactive effects (Yantra, Cube, Plasma, Tunnel, Lissajous, Nova, Spiral, Bubbles, Spectrum, Waterfall)
- TV remote control while visualizer is running (← / → cycle effects; ↑ / ↓ adjust intensity)
- Auto-cycle visualizer mode — rotate through effects on a configurable interval
- Local storage source — use photos from device storage alongside Drive/NAS

### Changed
- Visualizer performance improvements ported from psysuals v1.1/v1.2 algorithm (FFT smoothing, beat energy normalisation)

### Fixed
- `WeatherFetcher`: check HTTP success status before reading response body; close responses; add 10 s / 15 s timeouts
- `AudioEngine`: release `Visualizer` resource if setup throws after construction (resource leak)
- `ScreensaverEngine`: prevent infinite retry loop on image load failure — track consecutive failures, give up after exhausting the list, add 300 ms delay between retries
- `GoogleDriveSource`: null-safe access on `id`/`name` JSON fields; close responses; add timeouts; follow `nextPageToken` so folders with >1000 images are fully fetched
- `SynologySource`: close HTTP responses; add timeouts; log out DSM session after image listing to prevent session accumulation on the NAS
- `SynologySetupActivity`: validate port is a number between 1–65535 before saving or testing
- `LocalStorageSource`: log silenced `MediaStore` exceptions instead of swallowing them
- `SettingsActivity`: request `RECORD_AUDIO` permission when switching to Visualizer mode; show explanatory message if denied

---

## 2026-03-24

### Added
- GitHub Actions CI/CD — dev APK builds on push to `dev`; Play Store AAB published on merge to `master`
- Play Store listing, privacy policy, and generated screenshots

---

## 2026-03-23

### Added
- Initial release: Android TV screensaver with Google Drive and Synology NAS photo sources
- Six transition effects: Crossfade, Fade to Black, Slide Left, Slide Right, Zoom In, Zoom Out (+ Random)
- Configurable slide duration (5 s – 30 min) and transition speed (1 – 5 s)
- Adaptive launcher icon and TV banner
- Google OAuth 2.0 device flow (no Google Play Services required)
- Synology DSM FileStation API integration with self-signed certificate support
