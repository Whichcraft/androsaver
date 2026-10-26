package com.androsaver

import android.content.Context
import okhttp3.OkHttpClient
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import com.androsaver.source.ImageItem
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
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

    /** Validated TLS with credential-bearing redirects confined to the initial origin. */
    val selfHosted: OkHttpClient by lazy {
        standard.newBuilder().followRedirects(false).followSslRedirects(false)
            .addInterceptor(SameOriginRedirectInterceptor()).build()
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
            .followRedirects(false)
            .followSslRedirects(false)
            .addInterceptor(SameOriginRedirectInterceptor())
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    fun forHost(context: Context, host: String, allowInsecure: Boolean): OkHttpClient =
        if (allowInsecure) trustAll else selfHosted

    fun forImageItem(context: Context, item: ImageItem): OkHttpClient {
        return imageClient(item.url.toHttpUrlOrNull(), item.credentialEndpoint, item.insecureEndpoint)
    }

    fun forGlideRequest(context: Context, request: okhttp3.Request): OkHttpClient {
        return imageClient(request.url, request.header(CREDENTIAL_ENDPOINT_HEADER), request.header(INSECURE_ENDPOINT_HEADER))
    }

    internal fun imageClient(target: HttpUrl?, credentialOrigin: String?, insecureOrigin: String?): OkHttpClient {
        val credential = credentialOrigin?.toHttpUrlOrNull()
        if (credentialOrigin != null && (credential == null || target == null || !sameEndpoint(credential, target))) {
            throw java.io.IOException("Authenticated image outside configured origin refused")
        }
        val insecure = insecureOrigin?.toHttpUrlOrNull()
        if (insecure != null && target != null && sameEndpoint(insecure, target)) return trustAll
        return if (credential != null) selfHosted else standard
    }

    fun insecureEndpointHeader(endpoint: String): String = endpoint

    const val CREDENTIAL_ENDPOINT_HEADER = "X-AndroSaver-Credential-Endpoint"
    const val INSECURE_ENDPOINT_HEADER = "X-AndroSaver-Insecure-Endpoint"

    private fun sameEndpoint(a: HttpUrl, b: HttpUrl): Boolean =
        a.scheme == b.scheme && a.host == b.host && a.port == b.port

}
