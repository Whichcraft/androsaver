package com.androsaver.source

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import com.androsaver.BuildConfig
import com.androsaver.HttpClients
import com.androsaver.Prefs
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/** Fetches images from a self-hosted Immich instance via its REST API. */
class ImmichSource(private val context: Context) : ImageSource {

    override val name = "Immich"

    private val client = HttpClients.trustAll

    override fun isConfigured(): Boolean {
        val prefs = com.androsaver.Prefs.get(context)
        return !prefs.getString(Prefs.IMMICH_HOST, null).isNullOrEmpty() &&
               !prefs.getString(Prefs.IMMICH_API_KEY, null).isNullOrEmpty()
    }

    override suspend fun getImageUrls(): List<ImageItem> =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val prefs    = com.androsaver.Prefs.get(context)
            val host     = prefs.getString(Prefs.IMMICH_HOST, null) ?: return@withContext emptyList()
            val port     = prefs.getString(Prefs.IMMICH_PORT, "2283") ?: "2283"
            val useHttps = prefs.getBoolean(Prefs.IMMICH_USE_HTTPS, false)
            val apiKey   = prefs.getString(Prefs.IMMICH_API_KEY, null) ?: return@withContext emptyList()
            val albumId  = prefs.getString(Prefs.IMMICH_ALBUM_ID, "")?.trim() ?: ""

            val scheme  = if (useHttps) "https" else "http"
            val baseUrl = "$scheme://$host:$port"

            if (albumId.isNotEmpty()) {
                fetchAlbumAssets(baseUrl, apiKey, albumId)
            } else {
                fetchAllAssets(baseUrl, apiKey)
            }
        }

    private fun fetchAllAssets(baseUrl: String, apiKey: String): List<ImageItem> {
        val items = mutableListOf<ImageItem>()
        var page = 1
        val pageSize = 500
        val maxFetch = 2000
        while (items.size < maxFetch) {
            val url = "$baseUrl/api/assets?page=$page&size=$pageSize"
            val json = get(url, apiKey)
            val array = JSONArray(json)
            if (array.length() == 0) break
            parseAssets(array, baseUrl, apiKey, items, maxFetch)
            if (array.length() < pageSize) break
            page++
        }
        return items
    }

    private fun fetchAlbumAssets(baseUrl: String, apiKey: String, albumId: String): List<ImageItem> {
        val encodedId = URLEncoder.encode(albumId, "UTF-8")
        val url  = "$baseUrl/api/albums/$encodedId"
        val json = get(url, apiKey)
        val obj  = JSONObject(json)
        val array = obj.optJSONArray("assets") ?: return emptyList()
        val items = mutableListOf<ImageItem>()
        parseAssets(array, baseUrl, apiKey, items, 2000)
        return items
    }

    private fun parseAssets(array: JSONArray, baseUrl: String, apiKey: String, out: MutableList<ImageItem>, maxLimit: Int) {
        for (i in 0 until array.length()) {
            if (out.size >= maxLimit) break
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

    private fun get(url: String, apiKey: String): String {
        val request = Request.Builder()
            .url(url)
            .header("x-api-key", apiKey)
            .get()
            .build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            val code = response.code
            response.close()
            throw java.io.IOException("HTTP error code $code")
        }
        return response.use { it.body?.string() } ?: throw java.io.IOException("Empty response body")
    }

    companion object {
        private const val TAG = "ImmichSource"
    }
}
