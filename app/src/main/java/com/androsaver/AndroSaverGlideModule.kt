package com.androsaver

import android.content.Context
import com.bumptech.glide.Glide
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.module.AppGlideModule
import okhttp3.Call
import okhttp3.Request
import java.io.InputStream

/**
 * Registers a custom OkHttpUrlLoader that dynamically chooses between a trust-all OkHttpClient
 * (for self-hosted local NAS servers with self-signed certificates) and a standard validating
 * OkHttpClient (for public cloud storage and API endpoints) to prevent MITM vulnerabilities.
 */
@GlideModule
class AndroSaverGlideModule : AppGlideModule() {
    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        val dynamicCallFactory = object : Call.Factory {
            override fun newCall(request: Request): Call {
                return HttpClients.forHost(context, request.url.host).newCall(request)
            }
        }

        registry.replace(
            GlideUrl::class.java,
            InputStream::class.java,
            OkHttpUrlLoader.Factory(dynamicCallFactory)
        )
    }
}
