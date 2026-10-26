package com.androsaver.source

import android.content.SharedPreferences
import com.androsaver.Prefs
import java.util.UUID

/** Identity tokens live beside credentials so account replacement commits them together. */
internal object SourceIdentity {
    val providers = linkedMapOf("google" to "Google Drive", "onedrive" to "OneDrive", "dropbox" to "Dropbox",
        "immich" to "Immich", "nextcloud" to "Nextcloud", "synology" to "Synology NAS")
    fun enabledKey(provider: String) = when (provider) {
        "google" -> Prefs.ENABLE_GOOGLE_DRIVE
        "onedrive" -> Prefs.ENABLE_ONEDRIVE
        "dropbox" -> Prefs.ENABLE_DROPBOX
        "immich" -> Prefs.ENABLE_IMMICH
        "nextcloud" -> Prefs.ENABLE_NEXTCLOUD
        "synology" -> Prefs.ENABLE_SYNOLOGY
        else -> error("Unknown source identity")
    }
    fun eligible(prefs: SharedPreferences, candidate: String?): Boolean = try {
        val provider = SourceScope.provider(candidate)
        if (provider == null || provider !in providers || !prefs.getBoolean(enabledKey(provider), false)) false
        else {
            val required = when (provider) {
                "google" -> listOf(Prefs.GOOGLE_REFRESH_TOKEN)
                "onedrive" -> listOf(Prefs.ONEDRIVE_REFRESH_TOKEN)
                "dropbox" -> listOf(Prefs.DROPBOX_REFRESH_TOKEN)
                "immich" -> listOf(Prefs.IMMICH_HOST, Prefs.IMMICH_API_KEY)
                "nextcloud" -> listOf(Prefs.NEXTCLOUD_HOST, Prefs.NEXTCLOUD_USERNAME, Prefs.NEXTCLOUD_PASSWORD)
                else -> listOf(Prefs.SYNOLOGY_HOST, Prefs.SYNOLOGY_USERNAME, Prefs.SYNOLOGY_PASSWORD)
            }
            required.all { !prefs.getString(it, null).isNullOrBlank() } && scope(prefs, provider) == candidate
        }
    } catch (_: Exception) { false }

    fun key(provider: String) = "source_identity_$provider"
    val keys get() = providers.keys.map(::key).toSet()
    fun rotate(editor: SharedPreferences.Editor, provider: String): SharedPreferences.Editor =
        editor.putString(key(provider), UUID.randomUUID().toString())

    @Synchronized fun scope(prefs: SharedPreferences, provider: String): String {
        val identity = prefs.getString(key(provider), null) ?: UUID.randomUUID().toString().also {
            check(prefs.edit().putString(key(provider), it).commit()) { "Could not save source identity" }
        }
        val config = when (provider) {
            "google" -> listOf(Prefs.GOOGLE_CLIENT_ID, Prefs.GOOGLE_FOLDER_ID)
            "onedrive" -> listOf(Prefs.ONEDRIVE_CLIENT_ID, Prefs.ONEDRIVE_FOLDER)
            "dropbox" -> listOf(Prefs.DROPBOX_APP_KEY, Prefs.DROPBOX_FOLDER)
            "immich" -> listOf(Prefs.IMMICH_HOST, Prefs.IMMICH_PORT, Prefs.IMMICH_ALBUM_ID)
            "nextcloud" -> listOf(Prefs.NEXTCLOUD_HOST, Prefs.NEXTCLOUD_PORT, Prefs.NEXTCLOUD_USERNAME, Prefs.NEXTCLOUD_FOLDER)
            "synology" -> listOf(Prefs.SYNOLOGY_HOST, Prefs.SYNOLOGY_PORT, Prefs.SYNOLOGY_USERNAME, Prefs.SYNOLOGY_FOLDER)
            else -> error("Unknown source identity")
        }.map { key ->
            val value = prefs.getString(key, null)?.trim()
            if (key.endsWith("_host")) value?.let(com.androsaver.SourceSetupValidation::host) ?: "invalid-host" else value
        }
        val https = when (provider) {
            "immich" -> prefs.getBoolean(Prefs.IMMICH_USE_HTTPS, true).toString()
            "nextcloud" -> prefs.getBoolean(Prefs.NEXTCLOUD_USE_HTTPS, true).toString()
            "synology" -> prefs.getBoolean(Prefs.SYNOLOGY_USE_HTTPS, true).toString()
            else -> null
        }
        return SourceScope.create(provider, identity, config + https)
    }
}
