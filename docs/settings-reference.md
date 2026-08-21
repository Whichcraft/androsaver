# Settings Reference

All SharedPreferences keys, their `Prefs.kt` constants, UI type, and defaults.
Use `Prefs.<CONSTANT>` everywhere — never raw strings.

---

## Screensaver Mode

| Prefs Constant | Key | Type | Default | Values |
|---|---|---|---|---|
| `SCREENSAVER_MODE` | `screensaver_mode` | ListPreference | `slideshow` | `slideshow`, `static`, `visualizer`, `blank` |

---

## Slideshow Settings

| Prefs Constant | Key | Type | Default |
|---|---|---|---|
| `SLIDE_DURATION` | `transition_duration` | ListPreference | `10000` (ms) |
| `TRANSITION_EFFECT` | `transition_effect` | ListPreference | `crossfade` |
| `TRANSITION_SPEED` | `transition_speed` | ListPreference | `2000` (ms) |
| `KEN_BURNS_ENABLED` | `ken_burns_enabled` | SwitchPreference | `true` |
| `SLIDESHOW_IMAGE_SCALE` | `slideshow_image_scale` | ListPreference | `fit` (landscape) |
| `SLIDESHOW_IMAGE_SCALE_PORTRAIT` | `slideshow_image_scale_portrait` | ListPreference | `fit` (portrait) |
| `SLIDESHOW_BACKGROUND_COLOR` | `slideshow_background_color` | EditTextPreference | `#000000` |

Image rendering behavior is configurable independently for landscape and portrait
images in both Static Image and Slideshow modes. Slideshow also has its own
unused-space/background color setting.

Image behavior values are `crop` (Fill/Crop), `fit` (Fit/Letterbox), `center`
(Original Size/Center), and `stretch` (advanced; may distort). Static defaults
are crop for landscape or square images and fit for portrait images. Slideshow
defaults to fit for every orientation. Background colors accept `#RRGGBB` or
`#AARRGGBB`.

Static Image stores a private app-local copy after selection. Remote source URLs,
session IDs, and temporary bearer links are not used as the restart fallback.

### Static Image Settings

| Prefs Constant | Key | Type | Default |
|---|---|---|---|
| `STATIC_IMAGE_URI` | `static_image_uri` | Internal legacy/document key | no remote URL retained |
| `STATIC_IMAGE_LOCAL_PATH` | `static_image_local_path` | Internal path | — |
| `STATIC_IMAGE_DISPLAY_NAME` | `static_image_display_name` | Internal label | — |
| `STATIC_IMAGE_SCALE` | `static_image_scale` | ListPreference | `crop` (landscape/square) |
| `STATIC_IMAGE_SCALE_PORTRAIT` | `static_image_scale_portrait` | ListPreference | `fit` |
| `STATIC_BACKGROUND_COLOR` | `static_background_color` | EditTextPreference | `#000000` |

Transition effects: `crossfade`, `fade_black`, `slide_left`, `slide_right`, `zoom_in`, `zoom_out`, `random`

---

## Visualizer Settings

| Prefs Constant | Key | Type | Default | Notes |
|---|---|---|---|---|
| `VISUALIZER_MODE` | `visualizer_mode` | ListPreference | `auto` | `off` (no cycling), `auto` (cycle in order), `random` (cycle randomly) |
| `VISUALIZER_INTENSITY` | `visualizer_intensity` | ListPreference | `0.5` | beat multiplier: Off=0×, Low=0.5×, Med=1×, High=1.5×, Max=2× |
| `VIZ_CYCLE_INTERVAL` | `viz_cycle_interval` | ListPreference | `120000` (ms) | `0` = off; applies to both `auto` and `random` modes |
| `VIZ_ENABLED_MODES` | `viz_enabled_modes` | MultiSelectListPreference | _(all)_ | Set of mode names included in the cycle; at least one must remain selected |
| `AUDIO_GENRE` | `audio_genre` | ListPreference | `any` | `auto` (detect from FFT spectrum every 30 s), `any`, `electronic`, `rock`, `classical` |

---

## Display Overlays

| Prefs Constant | Key | Type | Default |
|---|---|---|---|
| `SHOW_CLOCK` | `show_clock` | SwitchPreference | `false` |
| `WEATHER_ENABLED` | `weather_enabled` | SwitchPreference | `false` | Summary shows ⚠ when city or API key is missing |
| `WEATHER_CITY` | `weather_city` | EditTextPreference | — | Return key confirms (no newline) |
| `WEATHER_API_KEY` | `weather_api_key` | EditTextPreference | — | Free key from openweathermap.org/api; Return key confirms |

---

## Schedule (Active Hours)

| Prefs Constant | Key | Type | Default |
|---|---|---|---|
| `SCHEDULE_ENABLED` | `schedule_enabled` | SwitchPreference | `false` |
| `SCHEDULE_START_HR` | `schedule_start_hr` | ListPreference | `8` |
| `SCHEDULE_END_HR` | `schedule_end_hr` | ListPreference | `22` |

---

## Image Sources (all SwitchPreference, default off)

The same configured sources are available from Slideshow and Static Image mode.
The Static Image browser copies a selected item into private app storage; it
does not retain remote fetch URLs, authentication headers, session IDs, or
temporary bearer links. The browser is cancellable, bounded to 60 seconds per
source, and virtualized for large result sets.

| Prefs Constant | Key |
|---|---|
| `ENABLE_GOOGLE_DRIVE` | `source_google_drive` |
| `ENABLE_ONEDRIVE` | `source_onedrive` |
| `ENABLE_DROPBOX` | `source_dropbox` |
| `ENABLE_IMMICH` | `source_immich` |
| `ENABLE_NEXTCLOUD` | `source_nextcloud` |
| `ENABLE_SYNOLOGY` | `source_synology` |
| `ENABLE_LOCAL_STORAGE` | `source_local_storage` |

---

## Credentials & Source Config

Sensitive user keys, tokens, and passwords listed below are routed to `EncryptedSharedPreferences` via `SecurePreferences`. If encrypted storage is unavailable, sensitive writes fail closed rather than falling back to plaintext. Non-sensitive settings remain in default SharedPreferences. Migration writes the encrypted copy before plaintext cleanup.

Self-hosted providers default to HTTPS with normal certificate and hostname
validation. HTTP or self-signed certificates require that provider's explicit
“Allow insecure” option; this choice is independent for Immich, Nextcloud, and
Synology.

### Google Drive
| Prefs Constant | Key |
|---|---|
| `GOOGLE_CLIENT_ID` | `google_client_id` |
| `GOOGLE_CLIENT_SECRET` | `google_client_secret` |
| `GOOGLE_ACCESS_TOKEN` | `google_access_token` |
| `GOOGLE_REFRESH_TOKEN` | `google_refresh_token` |
| `GOOGLE_FOLDER_ID` | `google_folder_id` |

### OneDrive
| Prefs Constant | Key |
|---|---|
| `ONEDRIVE_CLIENT_ID` | `onedrive_client_id` |
| `ONEDRIVE_FOLDER` | `onedrive_folder` |
| `ONEDRIVE_ACCESS_TOKEN` | `onedrive_access_token` |
| `ONEDRIVE_REFRESH_TOKEN` | `onedrive_refresh_token` |

### Dropbox
| Prefs Constant | Key |
|---|---|
| `DROPBOX_APP_KEY` | `dropbox_app_key` |
| `DROPBOX_APP_SECRET` | `dropbox_app_secret` |
| `DROPBOX_ACCESS_TOKEN` | `dropbox_access_token` |
| `DROPBOX_REFRESH_TOKEN` | `dropbox_refresh_token` |
| `DROPBOX_FOLDER` | `dropbox_folder` |

### Immich
| Prefs Constant | Key |
|---|---|
| `IMMICH_HOST` | `immich_host` |
| `IMMICH_PORT` | `immich_port` |
| `IMMICH_USE_HTTPS` | `immich_use_https` |
| `IMMICH_ALLOW_INSECURE` | `immich_allow_insecure` |
| `IMMICH_API_KEY` | `immich_api_key` |
| `IMMICH_ALBUM_ID` | `immich_album_id` |

### Nextcloud
| Prefs Constant | Key |
|---|---|
| `NEXTCLOUD_HOST` | `nextcloud_host` |
| `NEXTCLOUD_PORT` | `nextcloud_port` |
| `NEXTCLOUD_USE_HTTPS` | `nextcloud_use_https` |
| `NEXTCLOUD_ALLOW_INSECURE` | `nextcloud_allow_insecure` |
| `NEXTCLOUD_USERNAME` | `nextcloud_username` |
| `NEXTCLOUD_PASSWORD` | `nextcloud_password` |
| `NEXTCLOUD_FOLDER` | `nextcloud_folder` |

### Synology
| Prefs Constant | Key |
|---|---|
| `SYNOLOGY_HOST` | `synology_host` |
| `SYNOLOGY_PORT` | `synology_port` |
| `SYNOLOGY_USE_HTTPS` | `synology_use_https` |
| `SYNOLOGY_ALLOW_INSECURE` | `synology_allow_insecure` |
| `SYNOLOGY_USERNAME` | `synology_username` |
| `SYNOLOGY_PASSWORD` | `synology_password` |
| `SYNOLOGY_FOLDER` | `synology_folder` |

---

## App / Update

`UPDATE_CHANNEL` / `update_channel` is set automatically by build flavor (`dev` builds → dev channel, `prod` builds → stable channel). It is **not a user-facing preference** — there is no UI for it. The Prefs constant still exists for internal use by `UpdateChecker`.
Standard dev/prod APKs use the GitHub release updater. The Play Store variant
does not expose the updater and removes `REQUEST_INSTALL_PACKAGES`; Play users
receive updates through Google Play.
