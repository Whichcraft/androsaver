# Settings Reference

All SharedPreferences keys, their `Prefs.kt` constants, UI type, and defaults.
Use `Prefs.<CONSTANT>` everywhere — never raw strings.

---

## Screensaver Mode

| Prefs Constant | Key | Type | Default | Values |
|---|---|---|---|---|
| `SCREENSAVER_MODE` | `screensaver_mode` | ListPreference | `slideshow` | `slideshow`, `visualizer` |

---

## Slideshow Settings

| Prefs Constant | Key | Type | Default |
|---|---|---|---|
| `SLIDE_DURATION` | `transition_duration` | ListPreference | `10000` (ms) |
| `TRANSITION_EFFECT` | `transition_effect` | ListPreference | `crossfade` |
| `TRANSITION_SPEED` | `transition_speed` | ListPreference | `1500` (ms) |
| `KEN_BURNS_ENABLED` | `ken_burns_enabled` | SwitchPreference | `true` |
| `VIZ_OVERLAY_ENABLED` | `viz_overlay_on_slideshow` | SwitchPreference | `false` |
| `VIZ_OVERLAY_OPACITY` | `viz_overlay_opacity` | ListPreference | `0.3` |

Transition effects: `crossfade`, `fade_black`, `slide_left`, `slide_right`, `zoom_in`, `zoom_out`, `random`

---

## Visualizer Settings

| Prefs Constant | Key | Type | Default | Notes |
|---|---|---|---|---|
| `VISUALIZER_MODE` | `visualizer_mode` | ListPreference | `auto` | `auto` + one per mode class |
| `VISUALIZER_INTENSITY` | `visualizer_intensity` | ListPreference | `0.5` | beat multiplier: Off=0×, Low=0.5×, Med=1×, High=1.5×, Max=2× |
| `VIZ_CYCLE_INTERVAL` | `viz_cycle_interval` | ListPreference | `120000` (ms) | `0` = off |
| `AUDIO_GENRE` | `audio_genre` | ListPreference | `any` | `any`, `electronic`, `rock`, `classical` |

---

## Display Overlays

| Prefs Constant | Key | Type | Default |
|---|---|---|---|
| `SHOW_CLOCK` | `show_clock` | SwitchPreference | `false` |
| `WEATHER_ENABLED` | `weather_enabled` | SwitchPreference | `false` |
| `WEATHER_CITY` | `weather_city` | EditTextPreference | — |
| `WEATHER_API_KEY` | `weather_api_key` | EditTextPreference | — |

---

## Schedule (Active Hours)

| Prefs Constant | Key | Type | Default |
|---|---|---|---|
| `SCHEDULE_ENABLED` | `schedule_enabled` | SwitchPreference | `false` |
| `SCHEDULE_START_HR` | `schedule_start_hr` | ListPreference | `8` |
| `SCHEDULE_END_HR` | `schedule_end_hr` | ListPreference | `22` |

---

## Image Sources (all SwitchPreference, default off)

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

## Credentials & Source Config (all String, stored in EncryptedSharedPreferences)

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
| `IMMICH_API_KEY` | `immich_api_key` |
| `IMMICH_ALBUM_ID` | `immich_album_id` |

### Nextcloud
| Prefs Constant | Key |
|---|---|
| `NEXTCLOUD_HOST` | `nextcloud_host` |
| `NEXTCLOUD_PORT` | `nextcloud_port` |
| `NEXTCLOUD_USE_HTTPS` | `nextcloud_use_https` |
| `NEXTCLOUD_USERNAME` | `nextcloud_username` |
| `NEXTCLOUD_PASSWORD` | `nextcloud_password` |
| `NEXTCLOUD_FOLDER` | `nextcloud_folder` |

### Synology
| Prefs Constant | Key |
|---|---|
| `SYNOLOGY_HOST` | `synology_host` |
| `SYNOLOGY_PORT` | `synology_port` |
| `SYNOLOGY_USE_HTTPS` | `synology_use_https` |
| `SYNOLOGY_USERNAME` | `synology_username` |
| `SYNOLOGY_PASSWORD` | `synology_password` |
| `SYNOLOGY_FOLDER` | `synology_folder` |

---

## App / Update

`UPDATE_CHANNEL` / `update_channel` is set automatically by build flavor (`dev` builds → dev channel, `prod` builds → stable channel). It is **not a user-facing preference** — there is no UI for it. The Prefs constant still exists for internal use by `UpdateChecker`.
