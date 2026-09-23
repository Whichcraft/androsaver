# AndroSaver documentation

This directory contains the maintained technical and operational references.
The root [README](../README.md) is the user-facing quick start; these pages
are the deeper source of truth for behavior that is easy to misread from the
settings UI.

| Document | Use it for |
|---|---|
| [Architecture](architecture.md) | Package responsibilities, data flow, lifecycle, security boundaries, and build flavors |
| [Module reference](module-reference.md) | Complete Android module, resource, test, and upstream-module inventory |
| [Settings reference](settings-reference.md) | Preference keys, defaults, migrations, schedule semantics, and provider configuration |
| [Image sources](image-sources.md) | Provider APIs, authentication, limits, cache guarantees, and adding a source |
| [Visualizer modes](visualizer-modes.md) | The 34-mode registry, rotation order, effect contracts, and adding a mode |
| [Visualizer reactivity](visualizer-music-reactivity.md) | Exact audio bands, intensity behavior, thresholds, and silence behavior |
| [psysuals port notes](psysuals-port-notes.md) | Upstream parity, GLES 2.0 substitutions, and intentional Android differences |
| [Play Store checklist](play-store-checklist.md) | Console declarations and release-time privacy/data-safety checks |
| [Privacy policy](privacy-policy.md) | User-facing data handling and network destinations |

## Documentation maintenance rules

- Treat `VisualizerRenderer.modes`, `arrays.xml`, `Prefs.kt`, and
  `screensaver_preferences.xml` as the authoritative runtime registries.
- When a setting changes, update both the user-facing table in `README.md` and
  the key/default table in `settings-reference.md`.
- When an effect changes, update `visualizer-modes.md` and
  `visualizer-music-reactivity.md`; if the change comes from upstream, update
  `psysuals-port-notes.md` as well.
- Keep the root `visualizer-music-reactivity.md` as a compatibility pointer;
  do not create a second maintained copy.
- Do not claim Android verification from a local build. Android builds and
  release verification run in GitHub CI.
