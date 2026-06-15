package com.androsaver

import android.content.Context
import com.bumptech.glide.Glide
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.module.AppGlideModule
import java.io.InputStream

/**
 * Registers a custom OkHttpUrlLoader that uses [HttpClients.trustAll] so that Glide can load
 * images from self-hosted NAS devices (Synology, Nextcloud, Immich) that present self-signed
 * TLS certificates.  Without this, Glide's default OkHttp client rejects self-signed certs and
 * all HTTPS image loads fail silently.
 */
@GlideModule
class AndroSaverGlideModule : AppGlideModule() {
    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        registry.replace(
            GlideUrl::class.java,
            InputStream::class.java,
            OkHttpUrlLoader.Factory(HttpClients.trustAll)
        )
    }
}
