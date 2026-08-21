package com.androsaver

import android.content.Context
import okhttp3.OkHttpClient
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Shared OkHttpClient singletons. OkHttpClient is designed to be shared — it manages
 * its own connection pool and thread pool internally.
 */
internal object HttpClients {

    /** Standard HTTPS client for public cloud APIs (Google, Microsoft, Dropbox, etc.). */
    val standard: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /** Trust-all client for self-hosted LAN servers with self-signed certificates. */
    val trustAll: OkHttpClient by lazy {
        val trustManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
        }
        OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    fun forHost(context: Context, host: String): OkHttpClient {
        val normalized = host.trim().lowercase()
        return try {
            val prefs = Prefs.get(context)
            val allowed = setOf(
                Prefs.IMMICH_HOST to Prefs.IMMICH_ALLOW_INSECURE,
                Prefs.NEXTCLOUD_HOST to Prefs.NEXTCLOUD_ALLOW_INSECURE,
                Prefs.SYNOLOGY_HOST to Prefs.SYNOLOGY_ALLOW_INSECURE
            ).any { (hostKey, allowKey) ->
                prefs.getBoolean(allowKey, false) &&
                    prefs.getString(hostKey, null)?.trim()?.lowercase() == normalized
            }
            if (allowed) trustAll else standard
        } catch (_: Throwable) {
            standard
        }
    }

    fun forHost(context: Context, host: String, allowInsecure: Boolean): OkHttpClient =
        if (allowInsecure) trustAll else standard

}
