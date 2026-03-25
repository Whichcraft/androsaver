package com.androsaver

object Prefs {
    // Google Drive
    const val ENABLE_GOOGLE_DRIVE = "source_google_drive"
    const val GOOGLE_CLIENT_ID = "google_client_id"
    const val GOOGLE_CLIENT_SECRET = "google_client_secret"
    const val GOOGLE_ACCESS_TOKEN = "google_access_token"
    const val GOOGLE_REFRESH_TOKEN = "google_refresh_token"
    const val GOOGLE_FOLDER_ID = "google_folder_id"

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
    const val VISUALIZER_MODE      = "visualizer_mode"       // mode name or "auto"
    const val VISUALIZER_INTENSITY = "visualizer_intensity"  // beat gain: 0.0–2.0, default 1.0
}
