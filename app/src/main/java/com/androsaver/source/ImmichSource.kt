package com.androsaver.source

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import com.androsaver.BuildConfig
import com.androsaver.Prefs
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/** Fetches images from a self-hosted Immich instance via its REST API. */
class ImmichSource(private val context: Context) : ImageSource {

    override val name = "Immich"

    private val client = buildTrustAllClient()

    override fun isConfigured(): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return !prefs.getString(Prefs.IMMICH_HOST, null).isNullOrEmpty() &&
               !prefs.getString(Prefs.IMMICH_API_KEY, null).isNullOrEmpty()
    }

    override suspend fun getImageUrls(): List<ImageItem> =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val prefs    = PreferenceManager.getDefaultSharedPreferences(context)
            val host     = prefs.getString(Prefs.IMMICH_HOST, null) ?: return@withContext emptyList()
            val port     = prefs.getString(Prefs.IMMICH_PORT, "2283") ?: "2283"
            val useHttps = prefs.getBoolean(Prefs.IMMICH_USE_HTTPS, false)
            val apiKey   = prefs.getString(Prefs.IMMICH_API_KEY, null) ?: return@withContext emptyList()
            val albumId  = prefs.getString(Prefs.IMMICH_ALBUM_ID, "")?.trim() ?: ""

            val scheme  = if (useHttps) "https" else "http"
            val baseUrl = "$scheme://$host:$port"

            try {
                if (albumId.isNotEmpty()) {
                    fetchAlbumAssets(baseUrl, apiKey, albumId)
                } else {
                    fetchAllAssets(baseUrl, apiKey)
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG_LOGGING) Log.e(TAG, "Immich fetch error", e)
                emptyList()
            }
        }

    private fun fetchAllAssets(baseUrl: String, apiKey: String): List<ImageItem> {
        val items = mutableListOf<ImageItem>()
        var page = 1
        val pageSize = 500
        while (true) {
            val url = "$baseUrl/api/assets?page=$page&size=$pageSize"
            val json = get(url, apiKey) ?: break
            val array = JSONArray(json)
            if (array.length() == 0) break
            parseAssets(array, baseUrl, apiKey, items)
            if (array.length() < pageSize) break
            page++
        }
        return items
    }

    private fun fetchAlbumAssets(baseUrl: String, apiKey: String, albumId: String): List<ImageItem> {
        val encodedId = URLEncoder.encode(albumId, "UTF-8")
        val url  = "$baseUrl/api/albums/$encodedId"
        val json = get(url, apiKey) ?: return emptyList()
        val obj  = JSONObject(json)
        val array = obj.optJSONArray("assets") ?: return emptyList()
        val items = mutableListOf<ImageItem>()
        parseAssets(array, baseUrl, apiKey, items)
        return items
    }

    private fun parseAssets(array: JSONArray, baseUrl: String, apiKey: String, out: MutableList<ImageItem>) {
        for (i in 0 until array.length()) {
            val asset = array.getJSONObject(i)
            val type  = asset.optString("type", "")
            if (!type.equals("IMAGE", ignoreCase = true)) continue
            val id   = asset.optString("id") ?: continue
            val name = asset.optString("originalFileName", id)
            // Use preview thumbnail for faster loading; Glide handles the auth header
            val url  = "$baseUrl/api/assets/$id/thumbnail?size=preview"
            out.add(ImageItem(url = url, name = name, headers = mapOf("x-api-key" to apiKey)))
        }
    }

    private fun get(url: String, apiKey: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("x-api-key", apiKey)
            .get()
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    if (BuildConfig.DEBUG_LOGGING) Log.w(TAG, "HTTP ${response.code} for $url")
                    null
                } else {
                    response.body?.string()
                }
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG_LOGGING) Log.e(TAG, "Request failed: $url", e)
            null
        }
    }

    private fun buildTrustAllClient(): OkHttpClient {
        val trustManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }
        val sslContext = SSLContext.getInstance("SSL").apply {
            init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
        }
        return OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    companion object {
        private const val TAG = "ImmichSource"
    }
}
