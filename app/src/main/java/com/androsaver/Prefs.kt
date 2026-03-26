package com.androsaver

object Prefs {
    // Google Drive
    const val ENABLE_GOOGLE_DRIVE = "source_google_drive"
    const val GOOGLE_CLIENT_ID = "google_client_id"
    const val GOOGLE_CLIENT_SECRET = "google_client_secret"
    const val GOOGLE_ACCESS_TOKEN = "google_access_token"
    const val GOOGLE_REFRESH_TOKEN = "google_refresh_token"
    const val GOOGLE_FOLDER_ID = "google_folder_id"

    // OneDrive
    const val ENABLE_ONEDRIVE        = "source_onedrive"
    const val ONEDRIVE_CLIENT_ID     = "onedrive_client_id"
    const val ONEDRIVE_FOLDER        = "onedrive_folder"
    const val ONEDRIVE_ACCESS_TOKEN  = "onedrive_access_token"
    const val ONEDRIVE_REFRESH_TOKEN = "onedrive_refresh_token"

    // Dropbox
    const val ENABLE_DROPBOX        = "source_dropbox"
    const val DROPBOX_APP_KEY       = "dropbox_app_key"
    const val DROPBOX_APP_SECRET    = "dropbox_app_secret"
    const val DROPBOX_ACCESS_TOKEN  = "dropbox_access_token"
    const val DROPBOX_REFRESH_TOKEN = "dropbox_refresh_token"
    const val DROPBOX_FOLDER        = "dropbox_folder"

    // Immich
    const val ENABLE_IMMICH    = "source_immich"
    const val IMMICH_HOST      = "immich_host"
    const val IMMICH_PORT      = "immich_port"
    const val IMMICH_API_KEY   = "immich_api_key"
    const val IMMICH_ALBUM_ID  = "immich_album_id"
    const val IMMICH_USE_HTTPS = "immich_use_https"

    // Nextcloud
    const val ENABLE_NEXTCLOUD    = "source_nextcloud"
    const val NEXTCLOUD_HOST      = "nextcloud_host"
    const val NEXTCLOUD_PORT      = "nextcloud_port"
    const val NEXTCLOUD_USERNAME  = "nextcloud_username"
    const val NEXTCLOUD_PASSWORD  = "nextcloud_password"
    const val NEXTCLOUD_FOLDER    = "nextcloud_folder"
    const val NEXTCLOUD_USE_HTTPS = "nextcloud_use_https"

    // Synology NAS
    const val ENABLE_SYNOLOGY = "source_synology"
    const val SYNOLOGY_HOST = "synology_host"
    const val SYNOLOGY_PORT = "synology_port"
    const val SYNOLOGY_USERNAME = "synology_username"
    const val SYNOLOGY_PASSWORD = "synology_password"
    const val SYNOLOGY_FOLDER = "synology_folder"
    const val SYNOLOGY_USE_HTTPS = "synology_use_https"

    // Slideshow
    const val SLIDE_DURATION = "transition_duration"
    const val TRANSITION_EFFECT = "transition_effect"
    const val TRANSITION_SPEED = "transition_speed"

    // Screensaver mode
    const val SCREENSAVER_MODE = "screensaver_mode"
    const val MODE_SLIDESHOW   = "slideshow"
    const val MODE_VISUALIZER  = "visualizer"

    // Visualizer
    const val VISUALIZER_MODE      = "visualizer_mode"
    const val VISUALIZER_INTENSITY = "visualizer_intensity"
    const val VIZ_CYCLE_INTERVAL   = "viz_cycle_interval"
    const val AUDIO_GENRE          = "audio_genre"

    // Slideshow extras
    const val SHOW_CLOCK           = "show_clock"
    const val KEN_BURNS_ENABLED    = "ken_burns_enabled"
    const val VIZ_OVERLAY_ENABLED  = "viz_overlay_on_slideshow"
    const val VIZ_OVERLAY_OPACITY  = "viz_overlay_opacity"
    const val ENABLE_LOCAL_STORAGE = "source_local_storage"

    // Schedule
    const val SCHEDULE_ENABLED  = "schedule_enabled"
    const val SCHEDULE_START_HR = "schedule_start_hr"
    const val SCHEDULE_END_HR   = "schedule_end_hr"

    // Weather
    const val WEATHER_ENABLED = "weather_enabled"
    const val WEATHER_CITY    = "weather_city"
    const val WEATHER_API_KEY = "weather_api_key"

    // Updates
    const val UPDATE_CHANNEL        = "update_channel"
    const val UPDATE_CHANNEL_STABLE = "stable"
    const val UPDATE_CHANNEL_DEV    = "dev"
}
