package com.androsaver.source

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import com.androsaver.BuildConfig
import com.androsaver.awaitResponse
import com.androsaver.stringLimited
import com.androsaver.HttpClients
import com.androsaver.Prefs
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/** Fetches images from a self-hosted Immich instance via its REST API. */
class ImmichSource(private val context: Context) : ImageSource {

    override val name = "Immich"

    internal data class ConnectionConfig(
        val host: String,
        val port: String,
        val apiKey: String,
        val useHttps: Boolean,
        val allowInsecure: Boolean
    )

    override fun isConfigured(): Boolean {
        val prefs = com.androsaver.Prefs.get(context)
        return !prefs.getString(Prefs.IMMICH_HOST, null).isNullOrEmpty() &&
               !prefs.getString(Prefs.IMMICH_API_KEY, null).isNullOrEmpty()
    }

    suspend fun probeConnection(config: ConnectionConfig): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val host = config.host
        val port = config.port
        val key = config.apiKey
        val https = config.useHttps
        val insecure = config.allowInsecure
        if (!https && !insecure) throw java.io.IOException("HTTP is disabled")
        val base = "${if (https) "https" else "http"}://$host:$port".toHttpUrlOrNull()
            ?.takeIf { it.encodedPath == "/" && it.query == null }?.toString()?.removeSuffix("/")
            ?: throw java.io.IOException("Invalid Immich host or port")
        HttpClients.forHost(context, host, insecure).newCall(
            Request.Builder().url("$base/api/server/ping").header("x-api-key", key).build()
        ).awaitResponse().use { it.isSuccessful }
    }

    override suspend fun getImageUrls(): List<ImageItem> =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val prefs    = com.androsaver.Prefs.get(context)
            val host     = prefs.getString(Prefs.IMMICH_HOST, null) ?: return@withContext emptyList()
            val port     = prefs.getString(Prefs.IMMICH_PORT, "2283") ?: "2283"
            val useHttps = prefs.getBoolean(Prefs.IMMICH_USE_HTTPS, true)
            val allowInsecure = prefs.getBoolean(Prefs.IMMICH_ALLOW_INSECURE, false)
            val apiKey   = prefs.getString(Prefs.IMMICH_API_KEY, null) ?: return@withContext emptyList()
            val albumId  = prefs.getString(Prefs.IMMICH_ALBUM_ID, "")?.trim() ?: ""

            val scheme  = if (useHttps) "https" else "http"
            if (!useHttps && !allowInsecure) throw java.io.IOException("HTTP is disabled; enable insecure connections explicitly")
            val baseUrl = "$scheme://$host:$port".toHttpUrlOrNull()
                ?.takeIf { it.encodedPath == "/" && it.query == null }
                ?.toString()?.removeSuffix("/")
                ?: throw java.io.IOException("Invalid Immich host or port")
            val client = HttpClients.forHost(context, host, allowInsecure)

            if (albumId.isNotEmpty()) {
                fetchAlbumAssets(client, baseUrl, apiKey, albumId, if (allowInsecure) baseUrl else null)
            } else {
                fetchAllAssets(client, baseUrl, apiKey, if (allowInsecure) baseUrl else null)
            }
        }

    private suspend fun fetchAllAssets(client: okhttp3.OkHttpClient, baseUrl: String, apiKey: String, insecureEndpoint: String?): List<ImageItem> {
        val items = mutableListOf<ImageItem>()
        var page = 1
        val pageSize = 500
        val maxFetch = 2000
        var scanned = 0
        var pages = 0
        while (items.size < maxFetch && scanned < MAX_SCANNED_ENTRIES && pages < MAX_PAGES) {
            val url = "$baseUrl/api/assets?page=$page&size=$pageSize"
            val json = get(client, url, apiKey)
            val array = JSONArray(json)
            if (array.length() == 0) break
            scanned += array.length()
            pages++
            parseAssets(array, baseUrl, apiKey, items, maxFetch, insecureEndpoint)
            if (array.length() < pageSize) break
            page++
        }
        return items
    }

    private suspend fun fetchAlbumAssets(client: okhttp3.OkHttpClient, baseUrl: String, apiKey: String, albumId: String, insecureEndpoint: String?): List<ImageItem> {
        val encodedId = URLEncoder.encode(albumId, "UTF-8")
        val url  = "$baseUrl/api/albums/$encodedId"
        val json = get(client, url, apiKey)
        val obj  = JSONObject(json)
        val array = obj.optJSONArray("assets") ?: return emptyList()
        val items = mutableListOf<ImageItem>()
        parseAssets(array, baseUrl, apiKey, items, 2000, insecureEndpoint)
        return items
    }

    private fun parseAssets(array: JSONArray, baseUrl: String, apiKey: String, out: MutableList<ImageItem>, maxLimit: Int, insecureEndpoint: String?) {
        for (i in 0 until array.length()) {
            if (out.size >= maxLimit) break
            val asset = array.getJSONObject(i)
            val type  = asset.optString("type", "")
            if (!type.equals("IMAGE", ignoreCase = true)) continue
            val id   = asset.optString("id") ?: continue
            val name = asset.optString("originalFileName", id)
            // Use preview thumbnail for faster loading; Glide handles the auth header
            val url  = "$baseUrl/api/assets/$id/thumbnail?size=preview"
            out.add(ImageItem(url = url, name = name, headers = mapOf("x-api-key" to apiKey), stableId = "immich:$id", insecureEndpoint = insecureEndpoint))
        }
    }

    private suspend fun get(client: okhttp3.OkHttpClient, url: String, apiKey: String): String {
        val request = Request.Builder()
            .url(url)
            .header("x-api-key", apiKey)
            .get()
            .build()
        val response = client.newCall(request).awaitResponse()
        if (!response.isSuccessful) {
            val code = response.code
            response.close()
            throw java.io.IOException("HTTP error code $code")
        }
        return response.use { it.body?.stringLimited(MAX_RESPONSE_BYTES) } ?: throw java.io.IOException("Empty response body")
    }

    companion object {
        private const val TAG = "ImmichSource"
        private const val MAX_PAGES = 40
        private const val MAX_SCANNED_ENTRIES = 20_000
        private const val MAX_RESPONSE_BYTES = 8L * 1024 * 1024
    }
}
