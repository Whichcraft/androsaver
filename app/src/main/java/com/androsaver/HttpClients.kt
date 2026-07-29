package com.androsaver

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
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
        val prefs = Prefs.get(context.applicationContext)
        val configuredHosts = sequenceOf(
            prefs.getString(Prefs.NEXTCLOUD_HOST, null),
            prefs.getString(Prefs.SYNOLOGY_HOST, null),
            prefs.getString(Prefs.IMMICH_HOST, null)
        ).mapNotNull(::normalizeHost).toSet()
        return if (host.lowercase() in configuredHosts) trustAll else standard
    }

    private fun normalizeHost(value: String?): String? {
        val raw = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val candidate = if ("://" in raw) raw else "https://$raw"
        return candidate.toHttpUrlOrNull()?.host?.lowercase()
    }
}
