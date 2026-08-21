# TODO

These items were reviewed and implemented in the current change. Android builds
and device verification must run in GitHub CI; do not build the Android project
locally.

- [x] Deliver slideshow remote-control events to the dream and cover the four-mode interaction policy with a JVM test.
- [x] Restrict Nextcloud WebDAV image URLs to the configured origin and encode configured path components safely.
- [x] Bind self-hosted insecure TLS to exact source endpoint metadata through slideshow, cache, and static selection.
- [x] Make self-hosted connection probes use immutable form arguments without mutating shared preferences.
- [x] Bound provider enumeration by accepted images, pages, scanned entries, response bytes, and cursor progress.
- [x] Handle static import failures at the picker/browser boundary, preserve the previous image, and restore UI state.
- [x] Guard encrypted-preference reads/writes in OAuth and self-hosted setup screens; require checked saves before auth.
- [x] Report revoked Device Photos permission separately from an empty library.
- [x] Keep one static-browser image-count footer during out-of-order source completion.
- [x] Reconcile missing, truncated, corrupt, and stale image-cache manifest entries before reuse.
- [x] Use orientation-independent bounded Glide downsampling and resolve landscape/portrait display behavior from the decoded image.
- [x] Validate updater package version/signers and make download streaming cancellation-aware.
- [x] Use both projected ring centers in TunnelMode connectors.
- [x] Remove the largest identified hot-loop allocations in TunnelMode, NovaMode, and LatticeMode.
- [x] Pin all GitHub Actions to reviewed commit SHAs, use setup-java v5, and configure Dependabot.
- [x] Publish rolling releases before moving their tags, then verify assets, checksums, sizes, and tag targets.

## Release content

- [ ] Replace the bundled slideshow example images with a curated set of public-domain photos before release. This is intentionally manual.
