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
        fun add(provider: String?, enabled: Boolean, factory: () -> ImageSource) {
            if (!enabled) return
            try {
                val delegate = factory()
                sources += if (provider == null) delegate else ScopedSource(delegate, provider, prefs, com.androsaver.ImageCache(appContext))
            } catch (t: Throwable) {
                Log.w("ImageSourceRegistry", "Provider unavailable", t)
            }
        }
        add("google", prefs.getBoolean(Prefs.ENABLE_GOOGLE_DRIVE, false)) { GoogleDriveSource(appContext) }
        add("onedrive", prefs.getBoolean(Prefs.ENABLE_ONEDRIVE, false)) { OneDriveSource(appContext) }
        add("dropbox", prefs.getBoolean(Prefs.ENABLE_DROPBOX, false)) { DropboxSource(appContext) }
        add("immich", prefs.getBoolean(Prefs.ENABLE_IMMICH, false)) { ImmichSource(appContext) }
        add("nextcloud", prefs.getBoolean(Prefs.ENABLE_NEXTCLOUD, false)) { NextcloudSource(appContext) }
        add("synology", prefs.getBoolean(Prefs.ENABLE_SYNOLOGY, false)) { SynologySource(appContext) }
        add(null, prefs.getBoolean(Prefs.ENABLE_LOCAL_STORAGE, false)) { LocalStorageSource(appContext) }
        return if (sources.isEmpty() && includeBundledFallback) listOf(DefaultImagesSource(appContext)) else sources
    }
    fun eligibleScopes(context: Context): Set<String> = configured(context).mapNotNull { source ->
        try { source.cacheScope?.takeIf { source.isConfigured() } } catch (_: Exception) { null }
    }.toSet()

    private class ScopedSource(
        private val delegate: ImageSource, private val provider: String,
        private val prefs: android.content.SharedPreferences, private val cache: com.androsaver.ImageCache
    ) : ImageSource {
        override val name get() = delegate.name
        override val cacheScope = SourceIdentity.scope(prefs, provider)
        override fun isConfigured() = delegate.isConfigured()
        override suspend fun getImageUrls() = delegate.getImageUrls().map { SourceScope.bind(it, cacheScope) }
        override suspend fun enumerate(): ImageSourceResult {
            val result = delegate.enumerate()
            if (!prefs.getBoolean(SourceIdentity.enabledKey(provider), false) || SourceIdentity.scope(prefs, provider) != cacheScope) return ImageSourceResult.Failure(ImageSourceResult.FailureKind.UNKNOWN)
            val bound = if (result is ImageSourceResult.Success) result.copy(items = result.items.map { SourceScope.bind(it, cacheScope) }) else result
            cache.recordListing(cacheScope, bound, SourceIdentity.providers.keys.map { SourceIdentity.scope(prefs, it) }.toSet())
            return bound
        }
    }

}
