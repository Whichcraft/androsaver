package com.androsaver.source

import android.content.Context
import com.androsaver.Prefs
import android.util.Log

/** Single source of truth for enabled image providers. */
object ImageSourceRegistry {
    fun configured(context: Context, includeBundledFallback: Boolean = false): List<ImageSource> {
        val appContext = context.applicationContext
        val prefs = Prefs.get(appContext)
        val sources = mutableListOf<ImageSource>()
        fun add(enabled: Boolean, factory: () -> ImageSource) {
            if (!enabled) return
            try { sources += factory() } catch (t: Throwable) {
                Log.w("ImageSourceRegistry", "Provider unavailable", t)
            }
        }
        add(prefs.getBoolean(Prefs.ENABLE_GOOGLE_DRIVE, false)) { GoogleDriveSource(appContext) }
        add(prefs.getBoolean(Prefs.ENABLE_ONEDRIVE, false)) { OneDriveSource(appContext) }
        add(prefs.getBoolean(Prefs.ENABLE_DROPBOX, false)) { DropboxSource(appContext) }
        add(prefs.getBoolean(Prefs.ENABLE_IMMICH, false)) { ImmichSource(appContext) }
        add(prefs.getBoolean(Prefs.ENABLE_NEXTCLOUD, false)) { NextcloudSource(appContext) }
        add(prefs.getBoolean(Prefs.ENABLE_SYNOLOGY, false)) { SynologySource(appContext) }
        add(prefs.getBoolean(Prefs.ENABLE_LOCAL_STORAGE, false)) { LocalStorageSource(appContext) }
        return if (sources.isEmpty() && includeBundledFallback) listOf(DefaultImagesSource(appContext)) else sources
    }
}
